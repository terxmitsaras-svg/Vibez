package com.nocap.terminal.ssh

import android.util.Log
import com.jcraft.jsch.ChannelDirectTCPIP
import com.jcraft.jsch.Session
import com.nocap.terminal.data.ForwardType
import com.nocap.terminal.data.PortForward
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** The port forwards running on a connection. */
class ActivePortForwards internal constructor(
    val descriptions: List<String>,
    private val socksServers: List<Socks5Server>,
) {
    fun stop() = socksServers.forEach { it.close() }

    companion object {
        /** Start all enabled [rules]. Failures are reported to [status] and do not abort the connection. */
        fun start(session: Session, rules: List<PortForward>, status: (String) -> Unit): ActivePortForwards {
            val started = mutableListOf<String>()
            val socksServers = mutableListOf<Socks5Server>()
            for (rule in rules.filter { it.enabled }) {
                try {
                    when (rule.type) {
                        ForwardType.LOCAL -> session.setPortForwardingL(
                            rule.bindAddress, rule.bindPort, rule.destinationHost, rule.destinationPort,
                        )
                        ForwardType.REMOTE -> session.setPortForwardingR(
                            rule.bindAddress, rule.bindPort, rule.destinationHost, rule.destinationPort,
                        )
                        ForwardType.DYNAMIC -> socksServers += Socks5Server(session, rule.bindAddress, rule.bindPort).also { it.start() }
                    }
                    started += rule.describe()
                    status("Forwarding ${rule.describe()}")
                } catch (e: Exception) {
                    status("Port forward ${rule.describe()} failed: ${e.message}")
                }
            }
            return ActivePortForwards(started, socksServers)
        }
    }
}

/** A SOCKS5 proxy (CONNECT only, no authentication) that opens its connections through an SSH session. */
internal class Socks5Server(private val session: Session, bindAddress: String, port: Int) : Closeable {

    private val serverSocket = ServerSocket().apply {
        reuseAddress = true
        bind(InetSocketAddress(InetAddress.getByName(bindAddress), port))
    }
    private val executor: ExecutorService = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "socks5-$port").apply { isDaemon = true }
    }
    private val openResources: MutableSet<Closeable> = Collections.synchronizedSet(mutableSetOf())

    @Volatile
    private var closed = false

    fun start() {
        executor.execute {
            while (!closed) {
                val client = try {
                    serverSocket.accept()
                } catch (e: IOException) {
                    if (!closed) Log.w(TAG, "SOCKS accept failed", e)
                    return@execute
                }
                executor.execute { handle(client) }
            }
        }
    }

    private fun handle(client: Socket) {
        openResources += client
        var channel: ChannelDirectTCPIP? = null
        val channelCloser = Closeable { channel?.disconnect() }
        openResources += channelCloser
        try {
            client.soTimeout = HANDSHAKE_TIMEOUT_MS
            val input = DataInputStream(BufferedInputStream(client.getInputStream()))
            val output = client.getOutputStream()

            // Greeting: version, methods. Only "no authentication" is offered.
            if (input.readUnsignedByte() != SOCKS_VERSION) return
            val methods = ByteArray(input.readUnsignedByte()).also { input.readFully(it) }
            if (methods.none { it.toInt() == 0 }) {
                output.write(byteArrayOf(SOCKS_VERSION.toByte(), 0xFF.toByte()))
                return
            }
            output.write(byteArrayOf(SOCKS_VERSION.toByte(), 0))

            // Request: version, command, reserved, address type, address, port.
            if (input.readUnsignedByte() != SOCKS_VERSION) return
            val command = input.readUnsignedByte()
            input.readUnsignedByte()
            val host = when (input.readUnsignedByte()) {
                1 -> InetAddress.getByAddress(ByteArray(4).also { input.readFully(it) }).hostAddress
                3 -> String(ByteArray(input.readUnsignedByte()).also { input.readFully(it) }, Charsets.US_ASCII)
                4 -> InetAddress.getByAddress(ByteArray(16).also { input.readFully(it) }).hostAddress
                else -> return reply(output, REPLY_ADDRESS_TYPE_NOT_SUPPORTED)
            }
            val port = input.readUnsignedShort()
            if (command != COMMAND_CONNECT) return reply(output, REPLY_COMMAND_NOT_SUPPORTED)

            val opened = session.openChannel("direct-tcpip") as ChannelDirectTCPIP
            channel = opened
            opened.setHost(host)
            opened.setPort(port)
            opened.setOrgIPAddress(client.inetAddress.hostAddress)
            opened.setOrgPort(client.port)
            val fromRemote = opened.inputStream
            val toRemote = opened.outputStream
            try {
                opened.connect(CHANNEL_TIMEOUT_MS)
            } catch (e: Exception) {
                return reply(output, REPLY_CONNECTION_REFUSED)
            }
            reply(output, REPLY_SUCCEEDED)
            client.soTimeout = 0

            val downstream = executor.submit(Runnable {
                pump(fromRemote, output)
                runCatching { client.shutdownOutput() }
            })
            pump(input, toRemote)
            // Half-close: tell the remote end we are done sending, but keep reading its response.
            runCatching { toRemote.close() }
            runCatching { downstream.get() }
        } catch (e: IOException) {
            // Client went away or sent garbage.
        } catch (e: Exception) {
            Log.w(TAG, "SOCKS connection failed", e)
        } finally {
            runCatching { client.close() }
            runCatching { channelCloser.close() }
            openResources -= client
            openResources -= channelCloser
        }
    }

    private fun pump(from: InputStream, to: OutputStream) {
        try {
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val read = from.read(buffer)
                if (read < 0) break
                to.write(buffer, 0, read)
                to.flush()
            }
        } catch (ignored: IOException) {
            // Either side closed.
        }
    }

    private fun reply(output: OutputStream, code: Int) {
        output.write(byteArrayOf(SOCKS_VERSION.toByte(), code.toByte(), 0, 1, 0, 0, 0, 0, 0, 0))
        output.flush()
    }

    override fun close() {
        closed = true
        runCatching { serverSocket.close() }
        synchronized(openResources) {
            openResources.forEach { runCatching { it.close() } }
            openResources.clear()
        }
        executor.shutdownNow()
    }

    private companion object {
        const val TAG = "Socks5Server"
        const val SOCKS_VERSION = 5
        const val COMMAND_CONNECT = 1
        const val REPLY_SUCCEEDED = 0
        const val REPLY_CONNECTION_REFUSED = 5
        const val REPLY_COMMAND_NOT_SUPPORTED = 7
        const val REPLY_ADDRESS_TYPE_NOT_SUPPORTED = 8
        const val HANDSHAKE_TIMEOUT_MS = 30_000
        const val CHANNEL_TIMEOUT_MS = 20_000
    }
}
