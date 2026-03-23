package com.vibez.keyboard.subscription

import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

/**
 * SubscriptionManager
 *
 * Manages the free/premium tiers:
 * - Free tier: 5 minutes of voice-to-text per day
 * - Premium: unlimited voice, faster processing, no ads
 *
 * Uses Google Play Billing for subscription management.
 */
class SubscriptionManager(private val context: Context) {

    companion object {
        private const val TAG = "VibezSubscription"

        // Product IDs (configure in Google Play Console)
        const val PRODUCT_PREMIUM_MONTHLY = "vibez_premium_monthly"
        const val PRODUCT_PREMIUM_YEARLY = "vibez_premium_yearly"

        // Free tier limits
        const val FREE_DAILY_VOICE_SECONDS = 5 * 60  // 5 minutes
    }

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------
    private var isPremiumUser = false
    private var billingClient: BillingClient? = null
    private var voiceSessionStartTime = 0L
    private val prefs = context.getSharedPreferences("vibez_subscription", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // -------------------------------------------------------------------------
    // Initialization
    // -------------------------------------------------------------------------
    init {
        setupBillingClient()
        resetDailyUsageIfNeeded()
    }

    private fun setupBillingClient() {
        billingClient = BillingClient.newBuilder(context)
            .setListener { billingResult, purchases ->
                handlePurchaseUpdate(billingResult, purchases)
            }
            .enablePendingPurchases()
            .build()

        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing client connected")
                    queryExistingPurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected")
                // Retry logic handled by billing client internally
            }
        })
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------
    fun isPremium(): Boolean = isPremiumUser

    fun canUseVoice(): Boolean {
        if (isPremiumUser) return true
        return getDailyVoiceSecondsUsed() < FREE_DAILY_VOICE_SECONDS
    }

    fun getRemainingVoiceMinutes(): Int {
        if (isPremiumUser) return Int.MAX_VALUE
        val usedSeconds = getDailyVoiceSecondsUsed()
        val remainingSeconds = (FREE_DAILY_VOICE_SECONDS - usedSeconds).coerceAtLeast(0)
        return remainingSeconds / 60
    }

    fun getDailyVoiceSecondsUsed(): Int {
        return prefs.getInt("daily_voice_seconds_${todayKey()}", 0)
    }

    fun startVoiceSession() {
        voiceSessionStartTime = System.currentTimeMillis()
    }

    fun endVoiceSession() {
        if (voiceSessionStartTime == 0L) return
        val elapsed = System.currentTimeMillis() - voiceSessionStartTime
        val seconds = (elapsed / 1000).toInt()
        if (!isPremiumUser && seconds > 0) {
            val key = "daily_voice_seconds_${todayKey()}"
            val current = prefs.getInt(key, 0)
            prefs.edit().putInt(key, current + seconds).apply()
        }
        voiceSessionStartTime = 0L
    }

    fun getAvailableProducts(callback: (List<ProductDetails>) -> Unit) {
        if (billingClient?.isReady != true) {
            callback(emptyList())
            return
        }
        val products = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_PREMIUM_MONTHLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_PREMIUM_YEARLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
            .build()

        billingClient?.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                callback(details)
            } else {
                callback(emptyList())
            }
        }
    }

    fun launchPurchaseFlow(activity: android.app.Activity, productDetails: ProductDetails) {
        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: return
        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setOfferToken(offerToken)
            .build()

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        billingClient?.launchBillingFlow(activity, billingFlowParams)
    }

    fun disconnect() {
        scope.cancel()
        billingClient?.endConnection()
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------
    private fun queryExistingPurchases() {
        scope.launch {
            billingClient?.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            ) { result, purchases ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    isPremiumUser = purchases.any { purchase ->
                        purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                            (PRODUCT_PREMIUM_MONTHLY in purchase.products ||
                             PRODUCT_PREMIUM_YEARLY in purchase.products)
                    }
                    Log.d(TAG, "Premium status: $isPremiumUser")
                }
            }
        }
    }

    private fun handlePurchaseUpdate(result: BillingResult, purchases: List<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    if (PRODUCT_PREMIUM_MONTHLY in purchase.products ||
                        PRODUCT_PREMIUM_YEARLY in purchase.products) {
                        isPremiumUser = true
                        acknowledgePurchase(purchase)
                    }
                }
            }
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient?.acknowledgePurchase(params) { result ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "Purchase acknowledged")
            }
        }
    }

    private fun todayKey(): String {
        val day = System.currentTimeMillis() / TimeUnit.DAYS.toMillis(1)
        return day.toString()
    }

    private fun resetDailyUsageIfNeeded() {
        val lastResetDay = prefs.getLong("last_reset_day", 0)
        val today = System.currentTimeMillis() / TimeUnit.DAYS.toMillis(1)
        if (lastResetDay < today) {
            prefs.edit()
                .putLong("last_reset_day", today)
                .apply()
        }
    }
}
