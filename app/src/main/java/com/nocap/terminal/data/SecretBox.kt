package com.nocap.terminal.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts secrets such as passwords and private keys for storage. */
interface SecretCipher {
    fun encrypt(plain: ByteArray): String

    fun decrypt(encoded: String): ByteArray

    fun encryptString(plain: String): String = encrypt(plain.encodeToByteArray())

    fun decryptString(encoded: String): String = decrypt(encoded).decodeToString()
}

/**
 * Encrypts secrets with an AES-256-GCM key that lives in the Android Keystore and never leaves it.
 * Ciphertexts are "v1:<iv>:<ciphertext>" in Base64.
 */
class SecretBox(private val alias: String = "nocap_secrets_v1") : SecretCipher {

    private val key: SecretKey by lazy { loadOrCreateKey() }

    override fun encrypt(plain: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ciphertext = cipher.doFinal(plain)
        return "v1:" + cipher.iv.toBase64() + ":" + ciphertext.toBase64()
    }

    override fun decrypt(encoded: String): ByteArray {
        val parts = encoded.split(':')
        require(parts.size == 3 && parts[0] == "v1") { "Unsupported secret format" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, parts[1].fromBase64()))
        return cipher.doFinal(parts[2].fromBase64())
    }

    private fun loadOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128

        fun ByteArray.toBase64(): String = Base64.encodeToString(this, Base64.NO_WRAP)
        fun String.fromBase64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)
    }
}
