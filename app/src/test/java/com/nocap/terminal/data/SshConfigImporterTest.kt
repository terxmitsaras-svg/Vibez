package com.nocap.terminal.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SshConfigImporterTest {

    private val config = """
        Host bastion
            HostName 203.0.113.10
            User ops

        Host db prod-db
            HostName=10.0.2.15
            User postgres
            Port 2222
            ProxyJump bastion
            LocalForward 5432 localhost:5432
            LocalForward 127.0.0.1:6380 cache.internal:6379
            DynamicForward 1080
            RemoteForward 9000 localhost:3000
            ForwardAgent yes
            Compression yes
            SetEnv LANG=en_US.UTF-8 TZ=UTC

        Host cache.internal

        Host *.internal !secret.internal
            User nobody

        Host legacy
            HostName legacy.example.com
            ProxyJump admin@gateway.example.com:2200
        Match host foo
            User ignored

        # Defaults last, as usual: the first value found wins.
        Host *
            ServerAliveInterval 15
            User fallback
    """.trimIndent()

    @Test
    fun importsHostsWithOptions() {
        val result = SshConfigImporter.parse(config)
        val byLabel = result.hosts.associateBy { it.label }

        val bastion = byLabel.getValue("bastion")
        assertEquals("203.0.113.10", bastion.hostname)
        assertEquals("ops", bastion.username)
        assertEquals(15, bastion.keepAliveSeconds)

        val db = byLabel.getValue("db")
        assertEquals("10.0.2.15", db.hostname)
        assertEquals(2222, db.port)
        assertEquals("postgres", db.username)
        assertEquals(bastion.id, db.jumpHostId)
        assertTrue(db.agentForwarding)
        assertTrue(db.compression)
        assertEquals(mapOf("LANG" to "en_US.UTF-8", "TZ" to "UTC"), db.environment)
        assertEquals(
            listOf(
                PortForward(id = db.portForwards[0].id, type = ForwardType.LOCAL, bindAddress = "127.0.0.1", bindPort = 5432, destinationHost = "localhost", destinationPort = 5432),
                PortForward(id = db.portForwards[1].id, type = ForwardType.LOCAL, bindAddress = "127.0.0.1", bindPort = 6380, destinationHost = "cache.internal", destinationPort = 6379),
                PortForward(id = db.portForwards[2].id, type = ForwardType.REMOTE, bindAddress = "localhost", bindPort = 9000, destinationHost = "localhost", destinationPort = 3000),
                PortForward(id = db.portForwards[3].id, type = ForwardType.DYNAMIC, bindAddress = "127.0.0.1", bindPort = 1080),
            ),
            db.portForwards,
        )
        // A second alias on the same line gets the same settings.
        assertEquals("10.0.2.15", byLabel.getValue("prod-db").hostname)
        // Host * supplies defaults, wildcard sections apply to matching aliases.
        assertEquals("fallback", byLabel.getValue("legacy").username)
        assertEquals("nobody", byLabel.getValue("cache.internal").username)
    }

    @Test
    fun firstValueWinsLikeOpenSsh() {
        val hosts = SshConfigImporter.parse(
            """
            Host *
                User everyone
            Host web
                User web-admin
                Port 2200
            """.trimIndent()
        ).hosts
        val web = hosts.single()
        assertEquals("everyone", web.username)
        assertEquals(2200, web.port)
    }

    @Test
    fun patternMatching() {
        assertTrue(SshConfigImporter.matches(listOf("*.internal"), "cache.internal"))
        assertTrue(SshConfigImporter.matches(listOf("web?"), "web1"))
        assertTrue(!SshConfigImporter.matches(listOf("*", "!secret"), "secret"))
        assertTrue(!SshConfigImporter.matches(listOf("!secret"), "other"))
    }

    @Test
    fun createsHostsForInlineJumpTargetsAndReportsSkippedEntries() {
        val result = SshConfigImporter.parse(config)
        val legacy = result.hosts.single { it.label == "legacy" }
        val gateway = result.hosts.single { it.id == legacy.jumpHostId }
        assertEquals("gateway.example.com", gateway.hostname)
        assertEquals(2200, gateway.port)
        assertEquals("admin", gateway.username)
        assertTrue(result.skipped.single().startsWith("Match"))
        assertNull(result.hosts.firstOrNull { it.username == "ignored" })
    }

    @Test
    fun reimportUpdatesInsteadOfDuplicating() {
        val first = SshConfigImporter.parse(config).hosts
        val again = SshConfigImporter.parse(config, existing = first).hosts
        val firstAliases = first.filter { it.label in setOf("bastion", "db", "legacy") }.associate { it.label to it.id }
        val againAliases = again.filter { it.label in setOf("bastion", "db", "legacy") }.associate { it.label to it.id }
        assertEquals(firstAliases, againAliases)
        // The inline jump host already exists, so it is reused rather than created again.
        assertTrue(again.all { host -> first.any { it.id == host.id } })
    }
}
