package com.nocap.terminal.data

import com.nocap.terminal.ssh.FakeCipher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class BackupTest {

    private val cipher = FakeCipher()

    @Test
    fun roundTripRestoresEverythingIncludingSecrets() {
        val key = SshKey(name = "k", type = "ssh-ed25519", publicKey = "ssh-ed25519 AAA", fingerprint = "SHA256:x",
            encryptedPrivateKey = cipher.encryptString("PRIVATE"), encryptedPassphrase = cipher.encryptString("phrase"))
        val identity = Identity(name = "ops", username = "ops", encryptedPassword = cipher.encryptString("idpw"), keyId = key.id)
        val host = Host(hostname = "h", username = "u", encryptedPassword = cipher.encryptString("hostpw"), identityId = identity.id,
            proxy = ProxyConfig(hostname = "proxy", port = 1080, username = "p", encryptedPassword = cipher.encryptString("proxypw")))
        val snippet = Snippet(name = "s", command = "uptime")
        val known = KnownHost("h", "ssh-ed25519", "AAAA", "SHA256:y")
        val settings = AppSettings(terminalTheme = "Dracula", promptText = "¢@π:")

        val contents = Backup.collect(listOf(host), listOf(identity), listOf(key), listOf(snippet), listOf(known), settings, cipher)
        // Nothing device-encrypted leaks into the backup models.
        assertNull(contents.hosts.single().encryptedPassword)
        assertEquals("", contents.keys.single().encryptedPrivateKey)

        val file = Backup.encrypt(contents, "correct horse".toCharArray())
        assertFalse(String(file, Charsets.ISO_8859_1).contains("PRIVATE"))
        val restored = Backup.restore(Backup.decrypt(file, "correct horse".toCharArray()), cipher)

        assertEquals("hostpw", cipher.decryptString(restored.hosts.single().encryptedPassword!!))
        assertEquals("proxypw", cipher.decryptString(restored.hosts.single().proxy!!.encryptedPassword!!))
        assertEquals("idpw", cipher.decryptString(restored.identities.single().encryptedPassword!!))
        assertEquals("PRIVATE", cipher.decryptString(restored.keys.single().encryptedPrivateKey))
        assertEquals("phrase", cipher.decryptString(restored.keys.single().encryptedPassphrase!!))
        assertEquals(listOf(snippet), restored.snippets)
        assertEquals(listOf(known), restored.knownHosts)
        assertEquals(settings, restored.settings)
        assertEquals(emptyMap<String, String>(), restored.secrets)
    }

    @Test(expected = WrongPasswordException::class)
    fun wrongPasswordIsRejected() {
        val file = Backup.encrypt(BackupContents(), "right".toCharArray())
        Backup.decrypt(file, "wrong".toCharArray())
    }

    @Test(expected = WrongPasswordException::class)
    fun otherFilesAreRejected() {
        Backup.decrypt("{ not a backup }".encodeToByteArray(), "x".toCharArray())
    }
}
