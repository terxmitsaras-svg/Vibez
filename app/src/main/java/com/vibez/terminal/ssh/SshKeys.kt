package com.vibez.terminal.ssh

import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.KeyPair
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Base64

enum class KeyAlgorithm(val label: String, internal val jschType: Int, internal val bits: Int) {
    ED25519("Ed25519 (recommended)", KeyPair.ED25519, 256),
    ECDSA_256("ECDSA P-256", KeyPair.ECDSA, 256),
    ECDSA_384("ECDSA P-384", KeyPair.ECDSA, 384),
    ECDSA_521("ECDSA P-521", KeyPair.ECDSA, 521),
    RSA_3072("RSA 3072", KeyPair.RSA, 3072),
    RSA_4096("RSA 4096", KeyPair.RSA, 4096),
}

/** Private key material plus the derived public key information. */
class KeyMaterial(
    /** The private key file contents (OpenSSH, PEM or PuTTY format). */
    val privateKey: ByteArray,
    /** SSH key type, e.g. "ssh-ed25519". */
    val type: String,
    /** The public key as a single authorized_keys line. */
    val publicKey: String,
    val fingerprint: String,
)

class PassphraseRequiredException : Exception("This key is protected by a passphrase")

class InvalidKeyException(message: String) : Exception(message)

object SshKeys {

    /** Generate a new key pair. The private key is written in OpenSSH format, encrypted if [passphrase] is set. */
    fun generate(algorithm: KeyAlgorithm, comment: String, passphrase: String?): KeyMaterial {
        val keyPair = KeyPair.genKeyPair(JSch(), algorithm.jschType, algorithm.bits)
        try {
            val privateKey = ByteArrayOutputStream().also { out ->
                keyPair.writeOpenSSHv1PrivateKey(out, passphrase?.takeIf { it.isNotEmpty() }?.encodeToByteArray())
            }.toByteArray()
            val blob = keyPair.publicKeyBlob
            return KeyMaterial(privateKey, keyTypeOf(blob), publicKeyLine(blob, comment), fingerprint(blob))
        } finally {
            keyPair.dispose()
        }
    }

    /**
     * Parse an existing private key and derive its public key.
     *
     * @throws PassphraseRequiredException if the key is encrypted and no passphrase was given.
     * @throws InvalidKeyException if the key cannot be read or the passphrase is wrong.
     */
    fun parse(privateKey: ByteArray, passphrase: String?, comment: String): KeyMaterial {
        val keyPair = try {
            KeyPair.load(JSch(), privateKey, null)
        } catch (e: JSchException) {
            throw InvalidKeyException("Not a supported private key (OpenSSH, PEM or PuTTY): ${e.message}")
        }
        try {
            if (keyPair.isEncrypted) {
                if (passphrase.isNullOrEmpty()) throw PassphraseRequiredException()
                if (!keyPair.decrypt(passphrase.encodeToByteArray())) throw InvalidKeyException("Wrong passphrase")
            }
            val blob = keyPair.publicKeyBlob ?: throw InvalidKeyException("Cannot derive the public key")
            return KeyMaterial(privateKey, keyTypeOf(blob), publicKeyLine(blob, comment), fingerprint(blob))
        } finally {
            keyPair.dispose()
        }
    }

    /** OpenSSH style "SHA256:..." fingerprint of a public key blob. */
    fun fingerprint(blob: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(blob)
        return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest)
    }

    /** The key type name stored as the first string in an SSH public key blob. */
    fun keyTypeOf(blob: ByteArray): String {
        val buffer = ByteBuffer.wrap(blob)
        if (buffer.remaining() < 4) return "unknown"
        val length = buffer.int
        if (length <= 0 || length > buffer.remaining()) return "unknown"
        return String(blob, 4, length, Charsets.US_ASCII)
    }

    fun publicKeyLine(blob: ByteArray, comment: String): String =
        listOf(keyTypeOf(blob), Base64.getEncoder().encodeToString(blob), comment.trim())
            .filter { it.isNotEmpty() }
            .joinToString(" ")
}
