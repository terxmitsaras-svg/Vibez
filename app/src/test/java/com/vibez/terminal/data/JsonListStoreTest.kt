package com.vibez.terminal.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class JsonListStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob())

    @After
    fun tearDown() = scope.cancel()

    private fun awaitFile(file: File, predicate: (String) -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            if (file.exists() && predicate(file.readText())) return
            Thread.sleep(10)
        }
        throw AssertionError("Timed out waiting for ${file.name}")
    }

    @Test
    fun updatesArePersistedAndReloaded() {
        val dir = temporaryFolder.root
        val hosts = HostRepository(dir, scope)
        val host = Host(label = "web", hostname = "example.com", username = "deploy", tags = listOf("prod"))
        hosts.save(host)
        hosts.save(host.copy(port = 2222))
        awaitFile(File(dir, "hosts.json")) { it.contains("2222") }

        val reloaded = HostRepository(dir, scope)
        assertEquals(listOf(host.copy(port = 2222)), reloaded.hosts.value)
    }

    @Test
    fun deletingAJumpHostClearsReferences() {
        val hosts = HostRepository(temporaryFolder.root, scope)
        val jump = Host(hostname = "bastion", username = "a").also(hosts::save)
        val target = Host(hostname = "db", username = "b", jumpHostId = jump.id).also(hosts::save)

        hosts.delete(jump.id)

        assertEquals(listOf(target.copy(jumpHostId = null)), hosts.hosts.value)
    }

    @Test
    fun unreadableFileIsKeptAsideInsteadOfLost() {
        val dir = temporaryFolder.root
        File(dir, "snippets.json").writeText("{ this is not json")

        val snippets = SnippetRepository(dir, scope)

        assertTrue(snippets.snippets.value.isEmpty())
        assertTrue(dir.list()!!.any { it.startsWith("snippets.json.corrupt-") })
    }

    @Test
    fun unknownFieldsFromNewerVersionsAreIgnored() {
        val dir = temporaryFolder.root
        File(dir, "snippets.json").writeText(
            """{"version":1,"items":[{"id":"1","name":"up","command":"uptime","futureField":true}]}"""
        )

        val snippets = SnippetRepository(dir, scope)

        assertEquals("uptime", snippets.snippets.value.single().command)
    }
}
