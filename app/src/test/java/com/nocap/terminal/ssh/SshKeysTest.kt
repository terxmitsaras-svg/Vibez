package com.nocap.terminal.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SshKeysTest {

    @Test
    fun generatedKeysParseBackToTheSamePublicKey() {
        for (algorithm in listOf(KeyAlgorithm.ED25519, KeyAlgorithm.ECDSA_384, KeyAlgorithm.RSA_3072)) {
            val generated = SshKeys.generate(algorithm, "me@phone", passphrase = null)
            val parsed = SshKeys.parse(generated.privateKey, passphrase = null, comment = "me@phone")
            assertEquals(algorithm.name, generated.fingerprint, parsed.fingerprint)
            assertEquals(algorithm.name, generated.publicKey, parsed.publicKey)
            assertTrue(generated.publicKey.endsWith(" me@phone"))
        }
    }

    @Test
    fun encryptedKeysNeedTheRightPassphrase() {
        val generated = SshKeys.generate(KeyAlgorithm.ED25519, "", passphrase = "open sesame")
        assertTrue(String(generated.privateKey).contains("BEGIN OPENSSH PRIVATE KEY"))

        try {
            SshKeys.parse(generated.privateKey, passphrase = null, comment = "")
            throw AssertionError("Expected PassphraseRequiredException")
        } catch (expected: PassphraseRequiredException) {
        }
        try {
            SshKeys.parse(generated.privateKey, passphrase = "wrong", comment = "")
            throw AssertionError("Expected InvalidKeyException")
        } catch (expected: InvalidKeyException) {
            assertEquals("Wrong passphrase", expected.message)
        }
        assertEquals(generated.fingerprint, SshKeys.parse(generated.privateKey, "open sesame", "").fingerprint)
    }

    @Test
    fun garbageIsRejected() {
        try {
            SshKeys.parse("not a key".encodeToByteArray(), passphrase = null, comment = "")
            throw AssertionError("Expected InvalidKeyException")
        } catch (expected: InvalidKeyException) {
        }
    }

    @Test
    fun keyTypeIsReadFromBlob() {
        val blob = byteArrayOf(0, 0, 0, 11) + "ssh-ed25519".encodeToByteArray() + ByteArray(36)
        assertEquals("ssh-ed25519", SshKeys.keyTypeOf(blob))
        assertEquals("unknown", SshKeys.keyTypeOf(byteArrayOf(0, 0, 9)))
        assertEquals("unknown", SshKeys.keyTypeOf(byteArrayOf(0x7f, 0, 0, 1, 65)))
    }
}
