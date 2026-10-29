package com.nocap.terminal.ssh

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.ChannelShell
import com.nocap.terminal.data.Host
import com.termux.terminal.TerminalBackend
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * A [TerminalBackend] running an interactive shell on an SSH server, or, with [tunnelOnly], just
 * keeping the connection and its port forwards open without a shell.
 */
class SshBackend(
    val host: Host,
    private val connector: SshConnector,
    private val prompts: PromptBroker,
    val tunnelOnly: Boolean = false,
) : TerminalBackend {

    private val connection = SshConnection(host)
    private val resizeExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ssh-resize").apply { isDaemon = true }
    }
    private val ended = CountDownLatch(1)

    @Volatile
    private var channel: ChannelShell? = null

    @Volatile
    private var forwards: ActivePortForwards? = null

    @Volatile
    private var terminated = false

    private var input: InputStream? = null
    private var output: OutputStream? = null

    /** Port forwards that are running, for display. */
    val activeForwards: List<String> get() = forwards?.descriptions.orEmpty()

    val isConnected: Boolean get() = connection.isConnected && (tunnelOnly || channel?.isConnected == true)

    override fun start(columns: Int, rows: Int, cellWidthPixels: Int, cellHeightPixels: Int, status: TerminalBackend.StatusSink) {
        val report = { text: String -> status.onStatus(dim(text)) }
        connector.connect(host, owner = this, status = report, connection = connection)
        if (terminated) throw ConnectionCancelledException()

        val session = connection.session
        if (host.portForwards.isNotEmpty()) forwards = ActivePortForwards.start(session, host.portForwards, report)

        if (tunnelOnly) {
            val running = activeForwards
            val text = if (running.isEmpty()) {
                "\r\nConnected, but no port forward could be started.\r\n"
            } else {
                "\r\n\u001b[1;32mTunnels open:\u001b[0m\r\n" + running.joinToString("") { "  $it\r\n" } +
                    "\r\nClose this tab to stop them.\r\n"
            }
            input = TunnelStatusStream(text.encodeToByteArray(), ended)
            // Typing into a tunnel tab does nothing. (OutputStream.nullOutputStream() needs Android 13.)
            output = object : OutputStream() {
                override fun write(b: Int) = Unit
            }
            return
        }

        val shell = session.openChannel("shell") as ChannelShell
        shell.setPtyType(host.terminalType.ifBlank { "xterm-256color" }, columns, rows, columns * cellWidthPixels, rows * cellHeightPixels)
        shell.setAgentForwarding(host.agentForwarding)
        host.environment.forEach { (name, value) -> shell.setEnv(name, value) }
        input = shell.inputStream
        output = shell.outputStream
        shell.connect(CHANNEL_TIMEOUT_MS)
        channel = shell
        status.onStatus("\r\n")

        if (host.startupCommand.isNotBlank()) {
            output?.apply {
                write((host.startupCommand.trimEnd() + "\r").encodeToByteArray())
                flush()
            }
        }
    }

    override fun getInputStream(): InputStream = input ?: throw IOException("Not connected")

    override fun getOutputStream(): OutputStream = output ?: throw IOException("Not connected")

    override fun resize(columns: Int, rows: Int, cellWidthPixels: Int, cellHeightPixels: Int) {
        val shell = channel ?: return
        resizeExecutor.execute {
            runCatching { shell.setPtySize(columns, rows, columns * cellWidthPixels, rows * cellHeightPixels) }
        }
    }

    override fun waitFor(): Int {
        val shell = channel
        if (!tunnelOnly && shell == null) return EXIT_UNKNOWN
        while (connection.isConnected && !terminated && (shell == null || !shell.isClosed)) {
            try {
                Thread.sleep(POLL_INTERVAL_MS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
        ended.countDown()
        return shell?.exitStatus ?: EXIT_UNKNOWN
    }

    override fun terminate() {
        terminated = true
        ended.countDown()
        prompts.cancelAll(this)
        connection.disconnect()
    }

    override fun close() {
        terminated = true
        ended.countDown()
        prompts.cancelAll(this)
        forwards?.stop()
        resizeExecutor.shutdown()
        connection.disconnect()
    }

    override fun describeExit(exitStatus: Int): String {
        val reason = when {
            terminated -> "Disconnected"
            tunnelOnly -> "Tunnel connection closed"
            exitStatus >= 0 -> "Session ended (exit $exitStatus)"
            else -> "Connection closed"
        }
        return "\r\n\u001b[2m[$reason — press Enter to reconnect]\u001b[0m\r\n"
    }

    override fun getPid(): Int = -1

    override fun getCwd(): String? = null

    /** Open an SFTP channel on this session's existing connection. */
    fun openSftp(): ChannelSftp {
        if (!connection.isConnected) throw IOException("Not connected")
        return (connection.session.openChannel("sftp") as ChannelSftp).apply { connect(CHANNEL_TIMEOUT_MS) }
    }

    private fun dim(text: String): String = "\u001b[2m" + text.replace("\r\n", "\n").replace("\n", "\r\n") + "\u001b[0m\r\n"

    /** Shows a fixed text, then blocks until the tunnel ends. */
    private class TunnelStatusStream(private val text: ByteArray, private val ended: CountDownLatch) : InputStream() {
        private var position = 0

        override fun read(): Int = throw UnsupportedOperationException()

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (position < text.size) {
                val count = minOf(length, text.size - position)
                System.arraycopy(text, position, buffer, offset, count)
                position += count
                return count
            }
            ended.await()
            return -1
        }
    }

    private companion object {
        const val CHANNEL_TIMEOUT_MS = 15_000
        const val POLL_INTERVAL_MS = 100L
        const val EXIT_UNKNOWN = -1
    }
}
