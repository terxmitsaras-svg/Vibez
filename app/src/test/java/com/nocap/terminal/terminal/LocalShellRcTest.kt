package com.nocap.terminal.terminal

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

/** Runs the default startup file in mksh (Android's shell) when it is installed on this machine. */
class LocalShellRcTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val mksh = listOf("/usr/bin/mksh", "/bin/mksh").map(::File).firstOrNull { it.canExecute() }

    private fun prompt(promptText: String, showDirectory: Boolean): String {
        assumeTrue("mksh is not installed", mksh != null)
        val home = temporaryFolder.newFolder()
        File(home, "projects").mkdirs()
        val rc = File(home, LocalShell.RC_FILE).apply { writeText(LocalShell.DEFAULT_RC) }
        // Values go through a UTF-8 script: the JVM may encode environment variables as ASCII.
        val script = File(home, "run.sh").apply {
            writeText(
                "export NOCAP_PROMPT='$promptText' NOCAP_PROMPT_DIR=${if (showDirectory) 1 else 0}\n" +
                    ". \"${rc.path}\"\ncd projects\neval \"print -r -- \\\"${'$'}PS1\\\"\"\n"
            )
        }
        val process = ProcessBuilder(mksh!!.path, script.path)
            .directory(home)
            .apply {
                environment().clear()
                environment()["HOME"] = home.path
            }
            .start()
        process.waitFor(10, TimeUnit.SECONDS)
        val output = process.inputStream.readBytes().decodeToString()
        // Strip the line editor markers and colors to get what the user sees.
        return output.replace(Regex("\u0001\r?|\u001b\\[[0-9;]*m"), "").trimEnd('\n')
    }

    @Test
    fun defaultPromptIsCentAtPi() {
        assertEquals("¢@π: ", prompt("¢@π:", showDirectory = false))
    }

    @Test
    fun promptCanShowTheCurrentFolder() {
        assertEquals("¢@π:~/projects$ ", prompt("¢@π:", showDirectory = true))
    }

    @Test
    fun customPromptText() {
        assertEquals("no-cap> ", prompt("no-cap>", showDirectory = false))
    }
}
