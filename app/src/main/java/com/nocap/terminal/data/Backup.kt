package com.nocap.terminal.data

import kotlinx.serialization.Serializable
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Everything in the app, with secrets in plain text; only ever stored encrypted by [Backup]. */
@Serializable
data class BackupContents(
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val hosts: List<Host> = emptyList(),
    val identities: List<Identity> = emptyList(),
    val keys: List<SshKey> = emptyList(),
    val snippets: List<Snippet> = emptyList(),
    val knownHosts: List<KnownHost> = emptyList(),
    val settings: AppSettings? = null,
    /** Plain text secrets, keyed like "host/<id>/password". The models' encrypted fields are empty. */
    val secrets: Map<String, String> = emptyMap(),
)

class WrongPasswordException : Exception("Wrong password, or not a No Cap Terminal backup")

/**
 * Password protected backups, the account-free way to move everything to a new phone.
 *
 * Format: "NOCAPBK1" | salt (16) | iv (12) | AES-256-GCM(JSON), with the key derived from the
 * password with PBKDF2-HMAC-SHA256.
 */
object Backup {
    private val MAGIC = "NOCAPBK1".encodeToByteArray()
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val ITERATIONS = 210_000
    private val random = SecureRandom()

    /** Gather everything, decrypting device-bound secrets so they can be re-encrypted with the password. */
    fun collect(
        hosts: List<Host>,
        identities: List<Identity>,
        keys: List<SshKey>,
        snippets: List<Snippet>,
        knownHosts: List<KnownHost>,
        settings: AppSettings,
        cipher: SecretCipher,
    ): BackupContents {
        val secrets = linkedMapOf<String, String>()
        fun keep(name: String, encrypted: String?) {
            encrypted?.let { secrets[name] = cipher.decryptString(it) }
        }
        hosts.forEach { host ->
            keep("host/${host.id}/password", host.encryptedPassword)
            keep("host/${host.id}/proxy-password", host.proxy?.encryptedPassword)
        }
        identities.forEach { keep("identity/${it.id}/password", it.encryptedPassword) }
        keys.forEach { key ->
            keep("key/${key.id}/private", key.encryptedPrivateKey)
            keep("key/${key.id}/passphrase", key.encryptedPassphrase)
        }
        return BackupContents(
            hosts = hosts.map { it.copy(encryptedPassword = null, proxy = it.proxy?.copy(encryptedPassword = null)) },
            identities = identities.map { it.copy(encryptedPassword = null) },
            keys = keys.map { it.copy(encryptedPrivateKey = "", encryptedPassphrase = null) },
            snippets = snippets,
            knownHosts = knownHosts,
            settings = settings,
            secrets = secrets,
        )
    }

    /** The backup's models with their secrets encrypted again for this device. */
    fun restore(contents: BackupContents, cipher: SecretCipher): BackupContents {
        fun secret(name: String): String? = contents.secrets[name]?.let { cipher.encryptString(it) }
        return contents.copy(
            hosts = contents.hosts.map { host ->
                host.copy(
                    encryptedPassword = secret("host/${host.id}/password"),
                    proxy = host.proxy?.copy(encryptedPassword = secret("host/${host.id}/proxy-password")),
                )
            },
            identities = contents.identities.map { it.copy(encryptedPassword = secret("identity/${it.id}/password")) },
            keys = contents.keys.mapNotNull { key ->
                val privateKey = secret("key/${key.id}/private") ?: return@mapNotNull null
                key.copy(encryptedPrivateKey = privateKey, encryptedPassphrase = secret("key/${key.id}/passphrase"))
            },
            secrets = emptyMap(),
        )
    }

    fun encrypt(contents: BackupContents, password: CharArray): ByteArray {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
        cipher.updateAAD(MAGIC)
        val plain = StoreJson.encodeToString(BackupContents.serializer(), contents).encodeToByteArray()
        return MAGIC + salt + iv + cipher.doFinal(plain)
    }

    fun decrypt(data: ByteArray, password: CharArray): BackupContents {
        val header = MAGIC.size + SALT_BYTES + IV_BYTES
        if (data.size <= header || !data.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) throw WrongPasswordException()
        val salt = data.copyOfRange(MAGIC.size, MAGIC.size + SALT_BYTES)
        val iv = data.copyOfRange(MAGIC.size + SALT_BYTES, header)
        val plain = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
            cipher.updateAAD(MAGIC)
            cipher.doFinal(data, header, data.size - header)
        } catch (e: GeneralSecurityException) {
            throw WrongPasswordException()
        }
        return StoreJson.decodeFromString(BackupContents.serializer(), plain.decodeToString())
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
