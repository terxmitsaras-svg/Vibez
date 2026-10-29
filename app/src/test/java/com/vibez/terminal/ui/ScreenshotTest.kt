package com.vibez.terminal.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.termux.terminal.TerminalBackend
import com.vibez.terminal.AppContainer
import com.vibez.terminal.VibezApp
import com.vibez.terminal.data.ForwardType
import com.vibez.terminal.data.Host
import com.vibez.terminal.data.PortForward
import com.vibez.terminal.data.Snippet
import com.vibez.terminal.data.SshKey
import com.vibez.terminal.data.UiThemeMode
import com.vibez.terminal.terminal.SessionKind
import com.vibez.terminal.terminal.TerminalController
import com.vibez.terminal.ui.hosts.HostEditorScreen
import com.vibez.terminal.ui.hosts.HostsScreen
import com.vibez.terminal.ui.keys.KeychainScreen
import com.vibez.terminal.ui.settings.SettingsScreen
import com.vibez.terminal.ui.terminal.TerminalScreen
import com.vibez.terminal.ui.theme.VibezTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch

/**
 * Renders the main screens with Robolectric's native graphics into build/screenshots, so the UI can
 * be reviewed without a device, and fails if a screen does not lay out or draw.
 */
@RunWith(SandboxRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val container: AppContainer get() = (composeRule.activity.application as VibezApp).container

    private fun seedHosts(): List<Host> {
        val web = Host(label = "web-1", hostname = "web-1.example.com", username = "deploy", group = "Production", color = "red", tags = listOf("nginx"))
        val bastion = Host(label = "bastion", hostname = "203.0.113.10", username = "ops", group = "Production", color = "orange")
        val db = Host(label = "db-primary", hostname = "10.0.2.15", username = "postgres", group = "Production", jumpHostId = bastion.id, color = "purple",
            portForwards = listOf(PortForward(type = ForwardType.LOCAL, bindPort = 5432, destinationHost = "localhost", destinationPort = 5432)))
        val pi = Host(label = "raspberrypi", hostname = "192.168.1.42", username = "pi", group = "Home", color = "green", tags = listOf("iot"))
        val build = Host(hostname = "build.internal", port = 2222, username = "ci", tags = listOf("docker"))
        return listOf(web, bastion, db, pi, build).onEach(container.hosts::save)
    }

    private fun snapshot(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { VibezTheme(UiThemeMode.DARK) { content() } }
        // Let terminal reader threads deliver their output to the main looper.
        repeat(6) {
            Thread.sleep(60)
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.waitForIdle()
        }
        val root = composeRule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val directory = File("build/screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // A screen that failed to draw would be a single flat color.
        val sample = IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
        assertTrue("$name looks blank", sample.distinct().size > 20)
    }

    @Test
    fun hosts() {
        seedHosts()
        snapshot("hosts") { HostsScreen(container, onConnect = {}, onEdit = {}, onSftp = {}, onLocalTerminal = {}) }
    }

    @Test
    fun hostEditor() {
        val db = seedHosts()[2]
        snapshot("host_editor") { HostEditorScreen(container, db.id, onDone = {}) }
    }

    @Test
    fun keychain() {
        container.keys.save(
            SshKey(name = "pixel-key", type = "ssh-ed25519", publicKey = "ssh-ed25519 AAAA… vibez@pixel",
                fingerprint = "SHA256:2Lq0w5a1mDqk7Q7Yk0g9xQ0cJ3kq0f6p3m5r1zT8b4E", encryptedPrivateKey = "unused"),
        )
        container.keys.save(
            SshKey(name = "work-laptop", type = "rsa-sha2-512", publicKey = "ssh-rsa AAAA… me@work",
                fingerprint = "SHA256:Yp3x4kQe7n0b8P1sV6dM2hZ9c5tL0wR3aJ7uF4gK1oE", encryptedPrivateKey = "unused", encryptedPassphrase = "unused"),
        )
        snapshot("keychain") { KeychainScreen(container) }
    }

    @Test
    fun settings() {
        snapshot("settings") { SettingsScreen(container, onKnownHosts = {}) }
    }

    @Test
    fun terminal() {
        val web = seedHosts().first()
        container.snippets.save(Snippet(name = "Tail nginx errors", command = "sudo tail -f /var/log/nginx/error.log"))
        container.sessions.openWithBackend(SessionKind.LOCAL, null, "Local 1", ScriptedBackend(LOCAL_OUTPUT))
        container.sessions.openWithBackend(SessionKind.SSH, web, web.displayName, ScriptedBackend(SSH_OUTPUT))
        val controller = TerminalController(composeRule.activity, container.sessions, container.settings)
        snapshot("terminal") {
            TerminalScreen(container, controller, onBack = {}, onOpenSftp = {}, onManageSnippets = {}, onShowHosts = {})
        }
        container.sessions.closeAll()
    }

    /** Plays back fixed output, then stays connected until terminated. */
    private class ScriptedBackend(output: String) : TerminalBackend {
        private val ended = CountDownLatch(1)
        private val data = ByteArrayInputStream(output.replace("\n", "\r\n").toByteArray())

        override fun start(columns: Int, rows: Int, cellWidthPixels: Int, cellHeightPixels: Int, status: TerminalBackend.StatusSink) = Unit

        override fun getInputStream(): InputStream = object : InputStream() {
            override fun read(): Int = throw UnsupportedOperationException()

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                val read = data.read(buffer, offset, length)
                if (read > 0) return read
                ended.await()
                return -1
            }
        }

        override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()

        override fun resize(columns: Int, rows: Int, cellWidthPixels: Int, cellHeightPixels: Int) = Unit

        override fun waitFor(): Int {
            ended.await()
            return 0
        }

        override fun terminate() = ended.countDown()

        override fun close() = Unit

        override fun describeExit(exitStatus: Int) = ""

        override fun getPid() = -1

        override fun getCwd(): String? = null
    }

    private companion object {
        const val ESC = "\u001b"
        val SSH_OUTPUT = """
            Welcome to Ubuntu 24.04.1 LTS (GNU/Linux 6.8.0-45-generic x86_64)

            ${ESC}[1;32mdeploy@web-1${ESC}[0m:${ESC}[1;34m~${ESC}[0m$ systemctl status nginx
            ${ESC}[1;32m●${ESC}[0m nginx.service - A high performance web server
                 Loaded: loaded (/usr/lib/systemd/system/nginx.service; ${ESC}[1;32menabled${ESC}[0m)
                 Active: ${ESC}[1;32mactive (running)${ESC}[0m since Tue 2026-10-27 09:14:02 UTC; 2 days ago
               Main PID: 1234 (nginx)
                  Tasks: 5 (limit: 4558)
                 Memory: 12.4M (peak: 18.1M)
            ${ESC}[1;32mdeploy@web-1${ESC}[0m:${ESC}[1;34m~${ESC}[0m$ ls --color
            ${ESC}[1;34mapp${ESC}[0m  ${ESC}[1;34mbackups${ESC}[0m  ${ESC}[1;32mdeploy.sh${ESC}[0m  docker-compose.yml  ${ESC}[1;31mrelease.tar.gz${ESC}[0m
            ${ESC}[1;32mdeploy@web-1${ESC}[0m:${ESC}[1;34m~${ESC}[0m$ git log --oneline -3
            ${ESC}[33m6eb9b3c${ESC}[0m (${ESC}[1;36mHEAD -> ${ESC}[1;32mmain${ESC}[0m) Ship release 2.4
            ${ESC}[33m91c2d0a${ESC}[0m Fix cache headers
            ${ESC}[33m4f1e8b7${ESC}[0m Add health check endpoint
            ${ESC}[1;32mdeploy@web-1${ESC}[0m:${ESC}[1;34m~${ESC}[0m$
        """.trimIndent()

        val LOCAL_OUTPUT = "${ESC}[1;32mu0_a210@vibez${ESC}[0m:${ESC}[1;34mhome${ESC}[0m$ "
    }
}
