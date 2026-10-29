package com.nocap.terminal.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SnippetVariablesTest {
    @Test
    fun findsVariablesWithDefaultsOnce() {
        val command = "docker logs -f {{container}} --tail {{ lines:100 }} && echo {{container}}"
        assertEquals(
            listOf(SnippetVariables.Variable("container", ""), SnippetVariables.Variable("lines", "100")),
            SnippetVariables.find(command),
        )
    }

    @Test
    fun fillsValuesAndDefaults() {
        val command = "git checkout {{branch:main}} && git pull {{remote:origin}} {{branch:main}}"
        assertEquals("git checkout dev && git pull origin dev", SnippetVariables.fill(command, mapOf("branch" to "dev")))
        assertEquals("echo {{ not a var", SnippetVariables.fill("echo {{ not a var", emptyMap()))
        assertEquals(emptyList<SnippetVariables.Variable>(), SnippetVariables.find("awk '{print \$1}'"))
    }
}
