package com.vibez.terminal.ssh

import com.jcraft.jsch.ChannelDirectTCPIP
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchChangedHostKeyException
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.JSchUnknownHostKeyException
import com.jcraft.jsch.Proxy
import com.jcraft.jsch.Session
import com.jcraft.jsch.SocketFactory
import com.jcraft.jsch.UIKeyboardInteractive
import com.jcraft.jsch.UserInfo
import com.vibez.terminal.data.Host
import com.vibez.terminal.data.HostRepository
import com.vibez.terminal.data.KeyRepository
import com.vibez.terminal.data.KnownHostRepository
import com.vibez.terminal.data.SecretCipher
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.CopyOnWriteArrayList

/** An established SSH connection, possibly tunnelled through one or more jump hosts. */
class SshConnection internal constructor(val host: Host) {
    private val chain = CopyOnWriteArrayList<Session>()

    @Volatile
    var isCancelled = false
        private set

    /** The session to the target host. Only valid once [SshConnector.connect] has returned. */
    val session: Session get() = chain.last()

    val isConnected: Boolean get() = chain.isNotEmpty() && chain.all { it.isConnected }

    internal fun add(session: Session) {
        chain += session
        // Close a session created after a concurrent disconnect().
        if (isCancelled) disconnect()
    }

    /** Disconnect all sessions, target first. Safe to call from any thread, also while connecting. */
    fun disconnect() {
        isCancelled = true
        chain.asReversed().forEach { runCatching { it.disconnect() } }
    }
}

class ConnectionCancelledException : IOException("Connection cancelled")

/** Opens SSH connections for saved hosts: jump hosts, keys, passwords, host key verification. */
class SshConnector(
    private val hosts: HostRepository,
    private val keys: KeyRepository,
    private val knownHosts: KnownHostRepository,
    private val secrets: SecretCipher,
    private val prompts: PromptBroker,
) {

    /**
     * Connect to [host]. Blocking; must not be called on the main thread. Questions for the user are
     * asked through the [PromptBroker] on behalf of [owner]. Progress is reported to [status].
     *
     * @param connection receives the sessions while they are created, so that a concurrent
     * [SshConnection.disconnect] aborts the attempt.
     */
    fun connect(host: Host, owner: Any, status: (String) -> Unit, connection: SshConnection = SshConnection(host)): SshConnection {
        val chain = resolveJumpChain(host)
        try {
            var previous: Host? = null
            for (hop in chain) {
                if (connection.isCancelled) throw ConnectionCancelledException()
                status(if (previous == null) "Connecting to ${hop.address}…" else "Connecting to ${hop.address} via ${previous.address}…")
                val userInfo = InteractiveUserInfo(hop, owner, status)
                val session = createSession(hop, owner, userInfo)
                if (previous != null) session.setProxy(JumpHostProxy(connection.session))
                connection.add(session)
                session.connect(CONNECT_TIMEOUT_MS)
                userInfo.passwordToRemember?.let { password ->
                    hosts.get(hop.id)?.let { hosts.save(it.copy(encryptedPassword = secrets.encryptString(password))) }
                }
                status("Connected to ${hop.address} (${session.serverVersion})")
                previous = hop
            }
            hosts.markConnected(host.id)
            return connection
        } catch (e: Exception) {
            val cancelled = connection.isCancelled
            connection.disconnect()
            if (cancelled && e !is ConnectionCancelledException) throw ConnectionCancelledException()
            throw translate(e)
        }
    }

    /** The hosts to connect to in order: the outermost jump host first, [host] last. */
    private fun resolveJumpChain(host: Host): List<Host> {
        val chain = ArrayDeque<Host>()
        var current: Host? = host
        while (current != null) {
            if (chain.any { it.id == current.id }) throw IOException("Jump host loop detected at ${current.displayName}")
            if (chain.size > MAX_JUMP_HOSTS) throw IOException("Too many jump hosts")
            chain.addFirst(current)
            val jumpId = current.jumpHostId ?: break
            current = hosts.get(jumpId) ?: throw IOException("Jump host of ${current.displayName} no longer exists")
        }
        return chain
    }

    private fun createSession(host: Host, owner: Any, userInfo: InteractiveUserInfo): Session {
        val jsch = JSch()
        jsch.setHostKeyRepository(KnownHostsVerifier(knownHosts, prompts, owner))
        keys.get(host.keyId)?.let { key ->
            val privateKey = secrets.decrypt(key.encryptedPrivateKey)
            val passphrase = key.encryptedPassphrase?.let { secrets.decrypt(it) }
            jsch.addIdentity(key.name, privateKey, null, passphrase)
        }
        return jsch.getSession(host.username, host.hostname, host.port).apply {
            setUserInfo(userInfo)
            setConfig("StrictHostKeyChecking", "yes")
            setConfig("PreferredAuthentications", "publickey,keyboard-interactive,password")
            if (host.compression) {
                setConfig("compression.s2c", "zlib@openssh.com,zlib,none")
                setConfig("compression.c2s", "zlib@openssh.com,zlib,none")
                setConfig("compression_level", "6")
            }
            if (host.keepAliveSeconds > 0) {
                serverAliveInterval = host.keepAliveSeconds * 1000
                serverAliveCountMax = 3
            }
        }
    }

    private fun translate(e: Exception): Exception {
        val cause = generateSequence<Throwable>(e) { it.cause }.firstOrNull { it !is JSchException } ?: e
        val message = when {
            e is ConnectionCancelledException -> return e
            e is JSchUnknownHostKeyException -> "Host key was not trusted"
            e is JSchChangedHostKeyException -> "Host key changed — connection refused"
            // Thrown by JSch as "Auth cancel for methods '...'" when a password prompt was cancelled.
            e.message?.startsWith("Auth cancel") == true -> "Authentication cancelled"
            cause is UnknownHostException -> "Unknown host: ${cause.message}"
            cause is ConnectException -> "Connection refused: ${cause.message}"
            cause is NoRouteToHostException -> "No route to host"
            cause is SocketTimeoutException -> "Connection timed out"
            e.message == "Auth fail" || e.message?.startsWith("Auth fail") == true -> "Authentication failed"
            e.message == "timeout: socket is not established" -> "Connection timed out"
            else -> e.message ?: e.javaClass.simpleName
        }
        return IOException(message, e)
    }

    /** Bridges JSch's authentication callbacks to the user, using the saved password first. */
    private inner class InteractiveUserInfo(
        private val host: Host,
        private val owner: Any,
        private val status: (String) -> Unit,
    ) : UserInfo, UIKeyboardInteractive {
        private var savedPassword: String? = host.encryptedPassword?.let { runCatching { secrets.decryptString(it) }.getOrNull() }
        private var triedSavedPassword = false
        private var password: String? = null
        private var passphrase: String? = null

        /** A password the user asked to remember; saved once authentication succeeded. */
        var passwordToRemember: String? = null
            private set

        override fun getPassword(): String? = password

        override fun getPassphrase(): String? = passphrase

        override fun promptPassword(message: String): Boolean {
            password = nextPassword(message) ?: return false
            return true
        }

        override fun promptPassphrase(message: String): Boolean {
            val answer = prompts.askSecret(owner, "Key passphrase", message, "Passphrase", offerRemember = false) ?: return false
            passphrase = answer.value
            return true
        }

        override fun promptYesNo(message: String): Boolean =
            prompts.confirm(owner, host.address, message, confirmLabel = "Yes", dismissLabel = "No")

        override fun showMessage(message: String) = status(message.trimEnd())

        override fun promptKeyboardInteractive(
            destination: String,
            name: String,
            instruction: String,
            prompt: Array<String>,
            echo: BooleanArray,
        ): Array<String>? {
            if (prompt.isEmpty()) return emptyArray()
            if (prompt.size == 1 && !echo[0] && prompt[0].contains("password", ignoreCase = true)) {
                return nextPassword(prompt[0].trim())?.let { arrayOf(it) }
            }
            val fields = prompt.indices.map { PromptField(prompt[it].trim(), echo[it]) }
            return prompts.keyboardInteractive(owner, name.ifBlank { destination }, instruction, fields)?.toTypedArray()
        }

        /** The saved password on the first attempt, then whatever the user types. */
        private fun nextPassword(message: String): String? {
            savedPassword?.let {
                savedPassword = null
                triedSavedPassword = true
                return it
            }
            val text = if (triedSavedPassword) "The saved password was not accepted.\n$message" else message
            triedSavedPassword = false
            val answer = prompts.askSecret(owner, host.address, text, "Password", offerRemember = true) ?: return null
            passwordToRemember = if (answer.remember) answer.value else null
            return answer.value
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 20_000
        const val MAX_JUMP_HOSTS = 8
    }
}

/** Tunnels a connection through an already established session, like OpenSSH's ProxyJump. */
private class JumpHostProxy(private val jump: Session) : Proxy {
    private var channel: ChannelDirectTCPIP? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    override fun connect(socketFactory: SocketFactory?, host: String, port: Int, timeout: Int) {
        val channel = jump.openChannel("direct-tcpip") as ChannelDirectTCPIP
        channel.setHost(host)
        channel.setPort(port)
        input = channel.inputStream
        output = channel.outputStream
        channel.connect(timeout)
        this.channel = channel
    }

    override fun getInputStream(): InputStream? = input

    override fun getOutputStream(): OutputStream? = output

    override fun getSocket(): Socket? = null

    override fun close() {
        channel?.disconnect()
    }
}
