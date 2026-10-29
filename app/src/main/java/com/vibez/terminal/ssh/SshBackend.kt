package com.vibez.terminal.ssh

import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.ChannelSftp
import com.termux.terminal.TerminalBackend
import com.vibez.terminal.data.Host
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** A [TerminalBackend] running an interactive shell on an SSH server. */
class SshBackend(
    val host: Host,
    private val connector: SshConnector,
    private val prompts: PromptBroker,
) : TerminalBackend {

    private val connection = SshConnection(host)
    private val resizeExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ssh-resize").apply { isDaemon = true }
    }

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

    val isConnected: Boolean get() = channel?.isConnected == true && connection.isConnected

    override fun start(columns: Int, rows: Int, cellWidthPixels: Int, cellHeightPixels: Int, status: TerminalBackend.StatusSink) {
        val report = { text: String -> status.onStatus(dim(text)) }
        connector.connect(host, owner = this, status = report, connection = connection)
        if (terminated) throw ConnectionCancelledException()

        val session = connection.session
        if (host.portForwards.isNotEmpty()) forwards = ActivePortForwards.start(session, host.portForwards, report)

        val shell = session.openChannel("shell") as ChannelShell
        shell.setPtyType(host.terminalType.ifBlank { "xterm-256color" }, columns, rows, columns * cellWidthPixels, rows * cellHeightPixels)
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
        val shell = channel ?: return EXIT_UNKNOWN
        while (!shell.isClosed && connection.isConnected) {
            try {
                Thread.sleep(POLL_INTERVAL_MS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
        return shell.exitStatus
    }

    override fun terminate() {
        terminated = true
        prompts.cancelAll(this)
        connection.disconnect()
    }

    override fun close() {
        terminated = true
        prompts.cancelAll(this)
        forwards?.stop()
        resizeExecutor.shutdown()
        connection.disconnect()
    }

    override fun describeExit(exitStatus: Int): String {
        val reason = when {
            terminated -> "Disconnected"
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

    private companion object {
        const val CHANNEL_TIMEOUT_MS = 15_000
        const val POLL_INTERVAL_MS = 100L
        const val EXIT_UNKNOWN = -1
    }
}
