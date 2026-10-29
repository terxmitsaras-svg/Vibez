package com.nocap.terminal.ssh

import com.nocap.terminal.data.Host
import com.nocap.terminal.data.Protocol
import com.nocap.terminal.ssh.TelnetBackend.Companion.DO
import com.nocap.terminal.ssh.TelnetBackend.Companion.IAC
import com.nocap.terminal.ssh.TelnetBackend.Companion.OPTION_ECHO
import com.nocap.terminal.ssh.TelnetBackend.Companion.OPTION_NAWS
import com.nocap.terminal.ssh.TelnetBackend.Companion.OPTION_TERMINAL_TYPE
import com.nocap.terminal.ssh.TelnetBackend.Companion.SB
import com.nocap.terminal.ssh.TelnetBackend.Companion.SE
import com.nocap.terminal.ssh.TelnetBackend.Companion.TERMINAL_TYPE_IS
import com.nocap.terminal.ssh.TelnetBackend.Companion.TERMINAL_TYPE_SEND
import com.nocap.terminal.ssh.TelnetBackend.Companion.WILL
import com.termux.terminal.TerminalBackend
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import kotlin.concurrent.thread

class TelnetBackendTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private fun ByteArray.containsSequence(sequence: ByteArray): Boolean =
        (0..size - sequence.size).any { start -> sequence.indices.all { this[start + it] == sequence[it] } }

    @Test
    fun negotiatesOptionsAndKeepsDataClean() {
        val server = ServerSocket(0)
        val fromClient = ByteArrayOutputStream()
        val expectedLogin = "admin\r\u0000".encodeToByteArray()
        val serverThread = thread {
            server.accept().use { client ->
                client.soTimeout = 5000
                client.getOutputStream().apply {
                    write(bytes(IAC, DO, OPTION_TERMINAL_TYPE, IAC, DO, OPTION_NAWS, IAC, WILL, OPTION_ECHO))
                    write(bytes(IAC, SB, OPTION_TERMINAL_TYPE, TERMINAL_TYPE_SEND, IAC, SE))
                    write("login: ".encodeToByteArray())
                    write(bytes(IAC, IAC))
                    flush()
                }
                val buffer = ByteArray(1024)
                while (!fromClient.toByteArray().containsSequence(expectedLogin)) {
                    val read = client.getInputStream().read(buffer)
                    if (read < 0) break
                    fromClient.write(buffer, 0, read)
                }
            }
        }

        val backend = TelnetBackend(Host(hostname = "127.0.0.1", port = server.localPort, username = "", protocol = Protocol.TELNET))
        backend.start(100, 30, 10, 20, TerminalBackend.StatusSink { })
        val shown = ByteArrayOutputStream()
        val buffer = ByteArray(64)
        while (shown.size() < 8) {
            val read = backend.inputStream.read(buffer)
            if (read < 0) break
            shown.write(buffer, 0, read)
        }
        backend.outputStream.write("admin\r".encodeToByteArray())
        serverThread.join(5000)
        backend.terminate()
        server.close()

        // Negotiation never reaches the screen; an escaped 0xFF does.
        assertArrayEquals("login: ".encodeToByteArray() + bytes(IAC), shown.toByteArray())
        val sent = fromClient.toByteArray()
        assertTrue(sent.containsSequence(bytes(IAC, WILL, OPTION_TERMINAL_TYPE)))
        assertTrue(sent.containsSequence(bytes(IAC, WILL, OPTION_NAWS)))
        assertTrue(sent.containsSequence(bytes(IAC, SB, OPTION_NAWS, 0, 100, 0, 30, IAC, SE)))
        assertTrue(sent.containsSequence(bytes(IAC, DO, OPTION_ECHO)))
        assertTrue(sent.containsSequence(bytes(IAC, SB, OPTION_TERMINAL_TYPE, TERMINAL_TYPE_IS) + "XTERM-256COLOR".encodeToByteArray() + bytes(IAC, SE)))
        assertTrue("Enter must be sent as CR NUL", sent.containsSequence(expectedLogin))
    }
}
