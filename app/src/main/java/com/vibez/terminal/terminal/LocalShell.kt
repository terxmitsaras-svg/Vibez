package com.vibez.terminal.terminal

import android.content.Context
import android.os.Build
import android.util.Log
import com.termux.terminal.LocalProcessBackend
import java.io.File

/** Sets up the environment for local shell sessions using the device's own userland. */
class LocalShell(private val context: Context) {

    val homeDirectory: File get() = File(context.filesDir, "home")

    fun createBackend(shellPath: String): LocalProcessBackend {
        val home = homeDirectory.apply { mkdirs() }
        val tmp = File(context.cacheDir, "tmp").apply { mkdirs() }
        installDefaultRcFile(home)

        val shell = shellPath.takeIf { File(it).canExecute() } ?: DEFAULT_SHELL
        val environment = buildEnvironment(home, tmp, shell)
        // A leading dash in argv[0] makes the shell a login shell.
        val argv0 = "-" + File(shell).name
        return LocalProcessBackend(shell, home.absolutePath, arrayOf(argv0), environment.map { "${it.key}=${it.value}" }.toTypedArray())
    }

    private fun buildEnvironment(home: File, tmp: File, shell: String): Map<String, String> {
        val environment = linkedMapOf<String, String>()
        // Variables the platform's own tools (am, pm, cmd, dalvikvm...) need to work.
        val inherited = System.getenv()
        for (name in INHERITED_VARIABLES) inherited[name]?.let { environment[name] = it }
        inherited.filterKeys { it.startsWith("ANDROID_") }.forEach { (name, value) -> environment[name] = value }

        environment["HOME"] = home.absolutePath
        environment["TMPDIR"] = tmp.absolutePath
        environment["SHELL"] = shell
        environment["TERM"] = "xterm-256color"
        environment["COLORTERM"] = "truecolor"
        environment["LANG"] = "en_US.UTF-8"
        environment["PATH"] = PATH
        environment["HISTFILE"] = File(home, ".sh_history").absolutePath
        environment["HISTSIZE"] = "5000"
        // mksh, Android's shell, reads $ENV when started interactively.
        environment["ENV"] = File(home, RC_FILE).absolutePath
        environment["PREFIX"] = home.absolutePath
        environment["VIBEZ_VERSION"] = Build.VERSION.RELEASE
        return environment
    }

    private fun installDefaultRcFile(home: File) {
        val rc = File(home, RC_FILE)
        if (rc.exists()) return
        try {
            rc.writeText(DEFAULT_RC)
        } catch (e: Exception) {
            Log.w(TAG, "Cannot write $rc", e)
        }
    }

    companion object {
        private const val TAG = "LocalShell"
        const val DEFAULT_SHELL = "/system/bin/sh"
        const val RC_FILE = ".vibezrc"

        private val INHERITED_VARIABLES = listOf(
            "BOOTCLASSPATH", "DEX2OATBOOTCLASSPATH", "SYSTEMSERVERCLASSPATH", "EXTERNAL_STORAGE",
            "ASEC_MOUNTPOINT", "LOOP_MOUNTPOINT",
        )

        private val PATH = listOf(
            "/product/bin", "/apex/com.android.runtime/bin", "/apex/com.android.art/bin",
            "/system_ext/bin", "/system/bin", "/system/xbin", "/odm/bin", "/vendor/bin", "/vendor/xbin",
        ).joinToString(":")

        /** Written once; users may edit it freely afterwards. */
        private val DEFAULT_RC = """
            |# Vibez local shell startup file, read by interactive shells through ${'$'}ENV.
            |# Edit freely: this file is never overwritten.
            |
            |# Keep the platform defaults (aliases, completion) when they exist.
            |[ -r /system/etc/mkshrc ] && . /system/etc/mkshrc
            |
            |alias ls='ls --color=auto'
            |alias ll='ls -lh'
            |alias la='ls -lAh'
            |alias ..='cd ..'
            |
            |# user@device:dir$ with colors. \001 brackets invisible sequences for the line editor.
            |PS1=${'$'}'\001\r\001\033[1;32m\001'"${'$'}(id -un)@vibez"${'$'}'\001\033[0m\001:\001\033[1;34m\001''${'$'}{PWD##*/}'${'$'}'\001\033[0m\001$ '
        """.trimMargin() + "\n"
    }
}
