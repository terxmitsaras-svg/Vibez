package com.vibez.keyboard.subscription

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.vibez.keyboard.R

/**
 * SubscriptionActivity — Premium subscription purchase screen.
 *
 * Shows:
 * - Free vs Premium feature comparison
 * - Monthly and yearly subscription options
 * - Purchase flow via Google Play Billing
 */
class SubscriptionActivity : AppCompatActivity() {

    private lateinit var subscriptionManager: SubscriptionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subscription)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.subscription_title)

        subscriptionManager = SubscriptionManager(this)

        setupUI()
        loadProducts()
    }

    private fun setupUI() {
        val btnMonthly = findViewById<Button>(R.id.btnSubscribeMonthly)
        val btnYearly = findViewById<Button>(R.id.btnSubscribeYearly)

        if (subscriptionManager.isPremium()) {
            showPremiumActive()
            return
        }

        btnMonthly.setOnClickListener {
            purchaseProduct(SubscriptionManager.PRODUCT_PREMIUM_MONTHLY)
        }

        btnYearly.setOnClickListener {
            purchaseProduct(SubscriptionManager.PRODUCT_PREMIUM_YEARLY)
        }
    }

    private fun loadProducts() {
        subscriptionManager.getAvailableProducts { products ->
            runOnUiThread {
                val btnMonthly = findViewById<Button>(R.id.btnSubscribeMonthly)
                val btnYearly = findViewById<Button>(R.id.btnSubscribeYearly)

                products.forEach { product ->
                    val offerDetails = product.subscriptionOfferDetails?.firstOrNull()
                    val pricingPhase = offerDetails?.pricingPhases?.pricingPhaseList?.lastOrNull()
                    val price = pricingPhase?.formattedPrice ?: "—"

                    when (product.productId) {
                        SubscriptionManager.PRODUCT_PREMIUM_MONTHLY ->
                            btnMonthly.text = "Monthly – $price/month"
                        SubscriptionManager.PRODUCT_PREMIUM_YEARLY ->
                            btnYearly.text = "Yearly – $price/year (Best Value!)"
                    }
                }
            }
        }
    }

    private fun purchaseProduct(productId: String) {
        subscriptionManager.getAvailableProducts { products ->
            val product = products.find { it.productId == productId } ?: return@getAvailableProducts
            runOnUiThread {
                subscriptionManager.launchPurchaseFlow(this, product)
            }
        }
    }

    private fun showPremiumActive() {
        val premiumBadge = findViewById<View>(R.id.layoutPremiumActive)
        premiumBadge?.visibility = View.VISIBLE
        val freeTierLayout = findViewById<View>(R.id.layoutFreeTier)
        freeTierLayout?.visibility = View.GONE
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()
        subscriptionManager.disconnect()
    }
}
