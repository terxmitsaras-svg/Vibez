package com.nocap.terminal.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickConnectTest {

    private fun parsed(input: String) = QuickConnect.parse(input)?.let { Triple(it.username, it.hostname, it.port) }

    @Test
    fun acceptsCommonForms() {
        assertEquals(Triple("root", "example.com", 22), parsed("root@example.com"))
        assertEquals(Triple("pi", "10.0.0.5", 2222), parsed("pi@10.0.0.5:2222"))
        assertEquals(Triple("me", "::1", 22), parsed("me@[::1]"))
        assertEquals(Triple("me", "fe80::1", 2200), parsed("me@[fe80::1]:2200"))
        assertEquals(Triple("git", "host", 22), parsed("ssh://git@host/"))
        assertEquals(Triple("deploy", "web", 2022), parsed("  ssh deploy@web -p 2022 "))
        assertEquals(Triple("deploy", "web", 2022), parsed("ssh -p2022 deploy@web"))
        assertEquals(Triple("user@corp", "host", 22), parsed("user@corp@host"))
    }

    @Test
    fun rejectsIncompleteOrUnsupportedInput() {
        assertNull(parsed(""))
        assertNull(parsed("example.com"))
        assertNull(parsed("@example.com"))
        assertNull(parsed("root@"))
        assertNull(parsed("root@host:abc"))
        assertNull(parsed("root@host:70000"))
        assertNull(parsed("ssh -i key root@host"))
        assertNull(parsed("root@host extra"))
        assertNull(parsed("root@[::1"))
    }
}
