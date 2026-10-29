package com.nocap.terminal.ssh

import com.nocap.terminal.data.Host
import com.termux.terminal.TerminalBackend
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * A [TerminalBackend] speaking Telnet (RFC 854). Negotiates character-at-a-time mode with remote
 * echo, sends the terminal type (RFC 1091) and window size (RFC 1073), and strips all other
 * negotiation from the data stream.
 */
class TelnetBackend(val host: Host) : TerminalBackend {

    private val socket = Socket()

    @Volatile
    private var terminated = false

    @Volatile
    private var columns = 80

    @Volatile
    private var rows = 24

    private lateinit var rawOutput: OutputStream
    private lateinit var input: InputStream
    private lateinit var output: OutputStream
    private val writeLock = Any()

    override fun start(columns: Int, rows: Int, cellWidthPixels: Int, cellHeightPixels: Int, status: TerminalBackend.StatusSink) {
        this.columns = columns
        this.rows = rows
        status.onStatus("\u001b[2mConnecting to ${host.hostname}:${host.port} (telnet, unencrypted)…\u001b[0m\r\n")
        socket.connect(InetSocketAddress(host.hostname, host.port), CONNECT_TIMEOUT_MS)
        socket.tcpNoDelay = true
        if (terminated) throw ConnectionCancelledException()
        rawOutput = socket.getOutputStream()
        input = TelnetInputStream(socket.getInputStream())
        output = TelnetOutputStream()
    }

    override fun getInputStream(): InputStream = input

    override fun getOutputStream(): OutputStream = output

    override fun resize(columns: Int, rows: Int, cellWidthPixels: Int, cellHeightPixels: Int) {
        this.columns = columns
        this.rows = rows
        if (::rawOutput.isInitialized && windowSizeEnabled) {
            Thread { runCatching { sendWindowSize() } }.start()
        }
    }

    override fun waitFor(): Int {
        while (!terminated && !socket.isClosed && !inputEnded) {
            try {
                Thread.sleep(POLL_INTERVAL_MS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
        return 0
    }

    override fun terminate() {
        terminated = true
        runCatching { socket.close() }
    }

    override fun close() = terminate()

    override fun describeExit(exitStatus: Int): String =
        "\r\n\u001b[2m[${if (terminated) "Disconnected" else "Connection closed"} — press Enter to reconnect]\u001b[0m\r\n"

    override fun getPid(): Int = -1

    override fun getCwd(): String? = null

    // region Protocol

    @Volatile
    private var windowSizeEnabled = false

    @Volatile
    private var inputEnded = false

    private fun send(vararg bytes: Int) = synchronized(writeLock) {
        rawOutput.write(ByteArray(bytes.size) { bytes[it].toByte() })
        rawOutput.flush()
    }

    private fun sendWindowSize() {
        val data = ByteArrayOutputStream()
        data.write(IAC)
        data.write(SB)
        data.write(OPTION_NAWS)
        for (value in intArrayOf(columns shr 8, columns and 0xFF, rows shr 8, rows and 0xFF)) {
            data.write(value)
            if (value == IAC) data.write(IAC)
        }
        data.write(IAC)
        data.write(SE)
        synchronized(writeLock) {
            rawOutput.write(data.toByteArray())
            rawOutput.flush()
        }
    }

    private fun handleCommand(command: Int, option: Int) {
        when (command) {
            DO -> when (option) {
                OPTION_TERMINAL_TYPE, OPTION_SUPPRESS_GO_AHEAD -> send(IAC, WILL, option)
                OPTION_NAWS -> {
                    send(IAC, WILL, option)
                    windowSizeEnabled = true
                    sendWindowSize()
                }
                else -> send(IAC, WONT, option)
            }
            WILL -> when (option) {
                OPTION_ECHO, OPTION_SUPPRESS_GO_AHEAD -> send(IAC, DO, option)
                else -> send(IAC, DONT, option)
            }
            DONT -> send(IAC, WONT, option)
            WONT -> send(IAC, DONT, option)
        }
    }

    private fun handleSubnegotiation(data: ByteArray) {
        if (data.size >= 2 && data[0].toInt() == OPTION_TERMINAL_TYPE && data[1].toInt() == TERMINAL_TYPE_SEND) {
            val type = host.terminalType.ifBlank { "xterm-256color" }.uppercase().encodeToByteArray()
            val reply = ByteArrayOutputStream()
            reply.write(byteArrayOf(IAC.toByte(), SB.toByte(), OPTION_TERMINAL_TYPE.toByte(), TERMINAL_TYPE_IS.toByte()))
            reply.write(type)
            reply.write(byteArrayOf(IAC.toByte(), SE.toByte()))
            synchronized(writeLock) {
                rawOutput.write(reply.toByteArray())
                rawOutput.flush()
            }
        }
    }

    /** Server data with Telnet commands removed and answered. */
    private inner class TelnetInputStream(private val source: InputStream) : InputStream() {
        private val buffer = ByteArray(4096)
        private var state = State.DATA
        private var command = 0
        private val subnegotiation = ByteArrayOutputStream()

        override fun read(): Int = throw UnsupportedOperationException()

        override fun read(target: ByteArray, offset: Int, length: Int): Int {
            while (true) {
                val read = try {
                    source.read(buffer, 0, minOf(buffer.size, length))
                } catch (e: IOException) {
                    -1
                }
                if (read < 0) {
                    inputEnded = true
                    return -1
                }
                var count = 0
                for (index in 0 until read) {
                    val byte = buffer[index].toInt() and 0xFF
                    if (process(byte)) target[offset + count++] = byte.toByte()
                }
                if (count > 0) return count
            }
        }

        /** Returns true if [byte] is terminal data. */
        private fun process(byte: Int): Boolean {
            when (state) {
                State.DATA -> if (byte == IAC) state = State.IAC else return true
                State.IAC -> when (byte) {
                    IAC -> {
                        state = State.DATA
                        return true
                    }
                    DO, DONT, WILL, WONT -> {
                        command = byte
                        state = State.OPTION
                    }
                    SB -> {
                        subnegotiation.reset()
                        state = State.SUBNEGOTIATION
                    }
                    else -> state = State.DATA
                }
                State.OPTION -> {
                    handleCommand(command, byte)
                    state = State.DATA
                }
                State.SUBNEGOTIATION -> if (byte == IAC) state = State.SUBNEGOTIATION_IAC else subnegotiation.write(byte)
                State.SUBNEGOTIATION_IAC -> when (byte) {
                    SE -> {
                        handleSubnegotiation(subnegotiation.toByteArray())
                        state = State.DATA
                    }
                    IAC -> {
                        subnegotiation.write(IAC)
                        state = State.SUBNEGOTIATION
                    }
                    else -> state = State.SUBNEGOTIATION
                }
            }
            return false
        }
    }

    /** Escapes IAC bytes and turns a bare carriage return into CR NUL, as Telnet requires. */
    private inner class TelnetOutputStream : OutputStream() {
        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

        override fun write(data: ByteArray, offset: Int, length: Int) {
            val encoded = ByteArrayOutputStream(length + 8)
            for (index in offset until offset + length) {
                val byte = data[index].toInt() and 0xFF
                encoded.write(byte)
                if (byte == IAC) encoded.write(IAC)
                if (byte == '\r'.code) encoded.write(0)
            }
            synchronized(writeLock) {
                rawOutput.write(encoded.toByteArray())
                rawOutput.flush()
            }
        }
    }

    private enum class State { DATA, IAC, OPTION, SUBNEGOTIATION, SUBNEGOTIATION_IAC }

    // endregion

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val POLL_INTERVAL_MS = 100L

        const val IAC = 255
        const val DONT = 254
        const val DO = 253
        const val WONT = 252
        const val WILL = 251
        const val SB = 250
        const val SE = 240
        const val OPTION_ECHO = 1
        const val OPTION_SUPPRESS_GO_AHEAD = 3
        const val OPTION_TERMINAL_TYPE = 24
        const val OPTION_NAWS = 31
        const val TERMINAL_TYPE_IS = 0
        const val TERMINAL_TYPE_SEND = 1
    }
}
