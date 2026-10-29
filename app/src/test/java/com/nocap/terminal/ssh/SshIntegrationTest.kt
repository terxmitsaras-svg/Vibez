package com.nocap.terminal.ssh

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.nocap.terminal.data.ForwardType
import com.nocap.terminal.data.Host
import com.nocap.terminal.data.HostRepository
import com.nocap.terminal.data.Identity
import com.nocap.terminal.data.IdentityRepository
import com.nocap.terminal.data.KeyRepository
import com.nocap.terminal.data.KnownHost
import com.nocap.terminal.data.KnownHostRepository
import com.nocap.terminal.data.PortForward
import com.nocap.terminal.data.ProxyConfig
import com.nocap.terminal.data.ProxyType
import com.nocap.terminal.data.SshKey
import com.termux.terminal.TerminalBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.apache.sshd.common.config.keys.AuthorizedKeyEntry
import org.apache.sshd.common.config.keys.KeyUtils
import org.apache.sshd.common.config.keys.PublicKeyEntryResolver
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator
import org.apache.sshd.server.forward.AcceptAllForwardingFilter
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.server.shell.ProcessShellCommandFactory
import org.apache.sshd.server.shell.ProcessShellFactory
import org.apache.sshd.sftp.server.SftpSubsystemFactory
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.PublicKey
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

class SshIntegrationTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var server: SshServer
    private lateinit var scope: CoroutineScope
    private lateinit var hosts: HostRepository
    private lateinit var keys: KeyRepository
    private lateinit var identities: IdentityRepository
    private lateinit var knownHosts: KnownHostRepository
    private lateinit var prompts: PromptBroker
    private lateinit var connector: SshConnector
    private val cipher = FakeCipher()
    private val authorizedKeys = CopyOnWriteArrayList<PublicKey>()
    private val closeables = mutableListOf<AutoCloseable>()

    @Before
    fun setUp() {
        val sftpRoot = temporaryFolder.newFolder("sftp-root").toPath()
        server = SshServer.setUpDefaultServer().apply {
            host = "127.0.0.1"
            port = 0
            keyPairProvider = SimpleGeneratorHostKeyProvider(temporaryFolder.root.toPath().resolve("host-key"))
            passwordAuthenticator = PasswordAuthenticator { user, password, _ -> user == USER && password == PASSWORD }
            publickeyAuthenticator = PublickeyAuthenticator { user, key, _ ->
                user == USER && authorizedKeys.any { KeyUtils.compareKeys(it, key) }
            }
            shellFactory = ProcessShellFactory("/bin/sh", "/bin/sh")
            commandFactory = ProcessShellCommandFactory.INSTANCE
            forwardingFilter = AcceptAllForwardingFilter.INSTANCE
            subsystemFactories = listOf(SftpSubsystemFactory())
            fileSystemFactory = VirtualFileSystemFactory(sftpRoot)
            start()
        }
        scope = CoroutineScope(SupervisorJob())
        val dataDir = temporaryFolder.newFolder("data")
        hosts = HostRepository(dataDir, scope)
        keys = KeyRepository(dataDir, scope)
        knownHosts = KnownHostRepository(dataDir, scope)
        prompts = PromptBroker()
        identities = IdentityRepository(dataDir, scope)
        connector = SshConnector(hosts, identities, keys, knownHosts, cipher, prompts)
    }

    @After
    fun tearDown() {
        closeables.asReversed().forEach { runCatching { it.close() } }
        server.stop(true)
        scope.cancel()
    }

    private fun host(label: String = "test", configure: (Host) -> Host = { it }): Host =
        configure(Host(label = label, hostname = "127.0.0.1", port = server.port, username = USER)).also { hosts.save(it) }

    private fun respond(answer: (PromptRequest<*>) -> Any?): PromptResponder =
        PromptResponder(prompts, answer).also { closeables += it }

    private fun connect(host: Host, status: MutableList<String> = mutableListOf()): SshConnection =
        connector.connect(host, owner = this, status = { status += it }).also { connection -> closeables += AutoCloseable { connection.disconnect() } }

    private fun trustAndTypePassword(remember: Boolean = false): (PromptRequest<*>) -> Any? = { request ->
        when (request) {
            is PromptRequest.Confirm -> true
            is PromptRequest.Secret -> SecretAnswer(PASSWORD, remember)
            else -> null
        }
    }

    private fun exec(connection: SshConnection, command: String): String {
        val channel = connection.session.openChannel("exec") as ChannelExec
        channel.setCommand(command)
        val output = channel.inputStream
        channel.connect(5000)
        return output.readBytes().decodeToString().also { channel.disconnect() }
    }

    @Test
    fun passwordLoginTrustsHostKeyAndRemembersPassword() {
        val host = host()
        val responder = respond(trustAndTypePassword(remember = true))

        val connection = connect(host)
        assertTrue(connection.isConnected)
        assertEquals("hello", exec(connection, "echo hello").trim())

        val confirm = responder.asked.filterIsInstance<PromptRequest.Confirm>().single()
        assertEquals("Unknown host", confirm.title)
        assertFalse(confirm.isWarning)
        assertTrue(confirm.message.contains("SHA256:"))
        assertEquals(1, responder.asked.filterIsInstance<PromptRequest.Secret>().size)
        assertEquals("[127.0.0.1]:${server.port}", knownHosts.knownHosts.value.single().host)
        assertEquals(PASSWORD, cipher.decryptString(hosts.get(host.id)!!.encryptedPassword!!))
        connection.disconnect()

        // Second time: trusted host key and saved password, so nothing is asked.
        responder.asked.clear()
        connect(hosts.get(host.id)!!)
        assertTrue(responder.asked.isEmpty())
    }

    @Test
    fun rejectedSavedPasswordFallsBackToPrompt() {
        val host = host { it.copy(encryptedPassword = cipher.encryptString("wrong")) }
        val responder = respond(trustAndTypePassword())

        connect(host)

        val secret = responder.asked.filterIsInstance<PromptRequest.Secret>().single()
        assertTrue(secret.message, secret.message.contains("saved password was not accepted"))
        // Not ticked "remember", so the wrong password stays.
        assertEquals("wrong", cipher.decryptString(hosts.get(host.id)!!.encryptedPassword!!))
    }

    @Test
    fun cancellingThePasswordPromptFailsCleanly() {
        val host = host()
        respond { request -> if (request is PromptRequest.Confirm) true else null }

        val error = assertFailsWith<IOException> { connect(host) }
        assertEquals("Authentication cancelled", error.message)
    }

    @Test
    fun untrustedHostKeyAbortsTheConnection() {
        val host = host()
        respond { request -> if (request is PromptRequest.Confirm) false else SecretAnswer(PASSWORD, false) }

        val error = assertFailsWith<IOException> { connect(host) }
        assertEquals("Host key was not trusted", error.message)
        assertTrue(knownHosts.knownHosts.value.isEmpty())
    }

    @Test
    fun changedHostKeyIsReportedAsWarning() {
        val host = host()
        // Learn the real key type first, then plant a different key of that type.
        respond(trustAndTypePassword()).use { connect(host).disconnect() }
        val real = knownHosts.knownHosts.value.single()
        val fakeBlob = java.util.Base64.getDecoder().decode(real.key).also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        knownHosts.trust(real.copy(key = java.util.Base64.getEncoder().encodeToString(fakeBlob), fingerprint = "SHA256:fake"))

        val responder = respond { request -> if (request is PromptRequest.Confirm) false else SecretAnswer(PASSWORD, false) }
        val error = assertFailsWith<IOException> { connect(host) }
        assertEquals("Host key changed — connection refused", error.message)
        val warning = responder.asked.filterIsInstance<PromptRequest.Confirm>().single()
        assertTrue(warning.isWarning)
        assertTrue(warning.message.contains("SHA256:fake"))
    }

    @Test
    fun acceptingAChangedHostKeyReplacesIt() {
        val host = host()
        respond(trustAndTypePassword()).use { connect(host).disconnect() }
        val real = knownHosts.knownHosts.value.single()
        knownHosts.trust(real.copy(key = "AAAA", fingerprint = "SHA256:fake"))

        val responder = respond(trustAndTypePassword())
        connect(host)

        assertTrue(responder.asked.filterIsInstance<PromptRequest.Confirm>().single().isWarning)
        assertEquals(real.copy(addedAt = 0), knownHosts.knownHosts.value.single().copy(addedAt = 0))
    }

    @Test
    fun publicKeyLoginWithGeneratedKeys() {
        for ((algorithm, passphrase) in listOf(KeyAlgorithm.ED25519 to null, KeyAlgorithm.ECDSA_256 to "pass phrase", KeyAlgorithm.RSA_3072 to null)) {
            val material = SshKeys.generate(algorithm, "test@nocap", passphrase)
            authorizedKeys += AuthorizedKeyEntry.parseAuthorizedKeyEntry(material.publicKey)
                .resolvePublicKey(null, PublicKeyEntryResolver.FAILING)
            val key = SshKey(
                name = algorithm.name,
                type = material.type,
                publicKey = material.publicKey,
                fingerprint = material.fingerprint,
                encryptedPrivateKey = cipher.encrypt(material.privateKey),
                encryptedPassphrase = passphrase?.let { cipher.encryptString(it) },
            )
            keys.save(key)
            val host = host(algorithm.name) { it.copy(keyId = key.id) }
            val responder = respond { request -> request is PromptRequest.Confirm }

            val connection = connect(host)

            assertEquals(algorithm.name, "key-login", exec(connection, "echo key-login").trim())
            assertTrue(responder.asked.none { it is PromptRequest.Secret })
            connection.disconnect()
            responder.close()
            authorizedKeys.clear()
        }
    }

    @Test
    fun shellBackendRunsCommandsAndReportsExitStatus() {
        val host = host { it.copy(startupCommand = "echo started-\$((40+2))") }
        respond(trustAndTypePassword())
        val backend = SshBackend(host, connector, prompts)
        closeables += AutoCloseable { backend.close() }
        val status = mutableListOf<String>()

        backend.start(80, 24, 10, 20, TerminalBackend.StatusSink { status += it })
        val output = ByteArrayOutputStream()
        val reader = thread { runCatching { backend.inputStream.copyTo(output) } }
        backend.outputStream.apply {
            write("echo hello-from-shell\nexit 3\n".encodeToByteArray())
            flush()
        }

        assertEquals(3, backend.waitFor())
        reader.join(5000)
        val text = output.toString(Charsets.UTF_8.name())
        assertTrue(text, text.contains("started-42"))
        assertTrue(text, text.contains("hello-from-shell"))
        assertTrue(status.joinToString(), status.any { it.contains("Connecting to $USER@127.0.0.1") })
        assertTrue(backend.describeExit(3).contains("exit 3"))
    }

    @Test
    fun sftpUploadListDownloadRenameDelete() {
        val host = host()
        respond(trustAndTypePassword())
        val backend = SshBackend(host, connector, prompts)
        closeables += AutoCloseable { backend.close() }
        backend.start(80, 24, 10, 20, TerminalBackend.StatusSink { })
        val sftp = SftpClient(backend.openSftp(), ownedConnection = null)
        closeables += sftp

        val content = ByteArray(300_000) { (it % 251).toByte() }
        val progress = mutableListOf<Long>()
        sftp.mkdir("/docs")
        sftp.upload(ByteArrayInputStream(content), "/docs/data.bin", TransferProgress { done, _ -> progress += done })
        assertEquals(content.size.toLong(), progress.last())

        val listing = sftp.list("/docs")
        assertEquals(listOf("data.bin"), listing.map { it.name })
        assertEquals(content.size.toLong(), listing.single().size)
        assertTrue(sftp.list("/").single { it.name == "docs" }.isDirectory)

        val downloaded = ByteArrayOutputStream()
        sftp.download("/docs/data.bin", downloaded, TransferProgress { _, _ -> })
        assertArrayEquals(content, downloaded.toByteArray())

        sftp.rename("/docs/data.bin", "/docs/renamed.bin")
        assertEquals(listOf("renamed.bin"), sftp.list("/docs").map { it.name })

        sftp.delete(sftp.list("/").single { it.name == "docs" })
        assertTrue(sftp.list("/").none { it.name == "docs" })
    }

    @Test
    fun localAndDynamicPortForwards() {
        val echo = EchoServer().also { closeables += it }
        val localPort = freePort()
        val socksPort = freePort()
        val host = host {
            it.copy(
                portForwards = listOf(
                    PortForward(type = ForwardType.LOCAL, bindPort = localPort, destinationHost = "127.0.0.1", destinationPort = echo.port),
                    PortForward(type = ForwardType.DYNAMIC, bindPort = socksPort),
                ),
            )
        }
        respond(trustAndTypePassword())
        val connection = connect(host)
        val status = mutableListOf<String>()
        val forwards = ActivePortForwards.start(connection.session, host.portForwards, status::add)
        closeables += AutoCloseable { forwards.stop() }
        assertEquals(status.joinToString(), 2, forwards.descriptions.size)

        Socket("127.0.0.1", localPort).use { socket -> assertEquals("via-local", roundTrip(socket, "via-local")) }

        val proxy = java.net.Proxy(java.net.Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort))
        Socket(proxy).use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", echo.port), 5000)
            assertEquals("via-socks", roundTrip(socket, "via-socks"))
        }
    }

    @Test
    fun failingPortForwardDoesNotAbortConnection() {
        val busy = ServerSocket(0).also { closeables += it }
        val host = host {
            it.copy(portForwards = listOf(PortForward(type = ForwardType.DYNAMIC, bindPort = busy.localPort)))
        }
        respond(trustAndTypePassword())
        val connection = connect(host)
        val status = mutableListOf<String>()

        val forwards = ActivePortForwards.start(connection.session, host.portForwards, status::add)

        assertTrue(forwards.descriptions.isEmpty())
        assertTrue(status.single(), status.single().contains("failed"))
        assertTrue(connection.isConnected)
    }

    @Test
    fun connectsThroughJumpHost() {
        val jump = host("jump")
        val target = host("target") { it.copy(jumpHostId = jump.id) }
        val responder = respond(trustAndTypePassword())
        val status = mutableListOf<String>()

        val connection = connect(target, status)

        assertEquals("through-jump", exec(connection, "echo through-jump").trim())
        assertTrue(status.joinToString(), status.any { it.contains(" via ") })
        // Both hops reach the same server, so its key is asked about only once.
        assertEquals(1, responder.asked.count { it is PromptRequest.Confirm })
    }

    @Test
    fun identitySuppliesUsernameAndPassword() {
        val identity = Identity(name = "alice", username = USER, encryptedPassword = cipher.encryptString(PASSWORD)).also(identities::save)
        val host = host { it.copy(username = "", identityId = identity.id) }
        val responder = respond { request -> request is PromptRequest.Confirm }

        val connection = connect(host)

        assertEquals("via-identity", exec(connection, "echo via-identity").trim())
        assertTrue(responder.asked.none { it is PromptRequest.Secret })
    }

    @Test
    fun rememberedPasswordGoesToTheIdentity() {
        val identity = Identity(name = "alice", username = USER).also(identities::save)
        val host = host { it.copy(username = "", identityId = identity.id) }
        respond(trustAndTypePassword(remember = true))

        connect(host)

        assertEquals(PASSWORD, cipher.decryptString(identities.get(identity.id)!!.encryptedPassword!!))
        assertEquals(null, hosts.get(host.id)!!.encryptedPassword)
    }

    @Test
    fun environmentVariablesReachTheShell() {
        val host = host { it.copy(environment = mapOf("NOCAP_GREETING" to "no-cap")) }
        respond(trustAndTypePassword())
        val backend = SshBackend(host, connector, prompts)
        closeables += AutoCloseable { backend.close() }
        backend.start(80, 24, 10, 20, TerminalBackend.StatusSink { })
        val output = ByteArrayOutputStream()
        val reader = thread { runCatching { backend.inputStream.copyTo(output) } }
        backend.outputStream.apply {
            write("echo greeting=\$NOCAP_GREETING\nexit 0\n".encodeToByteArray())
            flush()
        }
        backend.waitFor()
        reader.join(5000)
        assertTrue(output.toString(Charsets.UTF_8.name()), output.toString(Charsets.UTF_8.name()).contains("greeting=no-cap"))
    }

    @Test
    fun tunnelOnlySessionKeepsForwardsWithoutShell() {
        val echo = EchoServer().also { closeables += it }
        val localPort = freePort()
        val host = host {
            it.copy(portForwards = listOf(PortForward(type = ForwardType.LOCAL, bindPort = localPort, destinationHost = "127.0.0.1", destinationPort = echo.port)))
        }
        respond(trustAndTypePassword())
        val backend = SshBackend(host, connector, prompts, tunnelOnly = true)
        closeables += AutoCloseable { backend.close() }

        backend.start(80, 24, 10, 20, TerminalBackend.StatusSink { })
        val banner = ByteArray(512).let { buffer -> String(buffer, 0, backend.inputStream.read(buffer)) }
        assertTrue(banner, banner.contains("Tunnels open") && banner.contains(":$localPort"))
        Socket("127.0.0.1", localPort).use { socket -> assertEquals("through-tunnel", roundTrip(socket, "through-tunnel")) }

        val waiter = thread { backend.waitFor() }
        backend.terminate()
        waiter.join(5000)
        assertFalse(waiter.isAlive)
    }

    @Test
    fun connectsThroughSocksProxy() {
        // A dynamic forward on a first connection serves as the SOCKS5 proxy for the second.
        val socksPort = freePort()
        val proxyHost = host("socks") { it.copy(portForwards = listOf(PortForward(type = ForwardType.DYNAMIC, bindPort = socksPort))) }
        respond(trustAndTypePassword())
        val first = connect(proxyHost)
        val forwards = ActivePortForwards.start(first.session, proxyHost.portForwards) {}
        closeables += AutoCloseable { forwards.stop() }
        val target = host("behind-proxy") {
            it.copy(proxy = ProxyConfig(type = ProxyType.SOCKS5, hostname = "127.0.0.1", port = socksPort))
        }
        val status = mutableListOf<String>()

        val connection = connect(target, status)

        assertEquals("via-proxy", exec(connection, "echo via-proxy").trim())
        assertTrue(status.joinToString(), status.any { it.contains("SOCKS5 proxy") })
    }

    @Test
    fun jumpHostLoopIsDetected() {
        val a = host("a")
        val b = host("b") { it.copy(jumpHostId = a.id) }
        hosts.save(a.copy(jumpHostId = b.id))

        val error = assertFailsWith<IOException> { connect(hosts.get(b.id)!!) }
        assertTrue(error.message!!, error.message!!.contains("loop"))
    }

    @Test
    fun disconnectWhilePromptingCancelsTheAttempt() {
        val host = host()
        val connection = SshConnection(host)
        respond { request ->
            if (request is PromptRequest.Confirm) {
                true
            } else {
                connection.disconnect()
                null
            }
        }

        assertFailsWith<ConnectionCancelledException> {
            connector.connect(host, owner = this, status = {}, connection = connection)
        }
    }

    @Test
    fun fingerprintsMatchOpenSshFormat() {
        val material = SshKeys.generate(KeyAlgorithm.ED25519, "", null)
        val publicKey = AuthorizedKeyEntry.parseAuthorizedKeyEntry(material.publicKey).resolvePublicKey(null, PublicKeyEntryResolver.FAILING)
        assertEquals(KeyUtils.getFingerPrint(publicKey), material.fingerprint)
        assertEquals("ssh-ed25519", material.type)
    }

    private fun roundTrip(socket: Socket, message: String): String {
        socket.soTimeout = 5000
        socket.getOutputStream().apply {
            write((message + "\n").encodeToByteArray())
            flush()
        }
        return socket.getInputStream().bufferedReader().readLine()
    }

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    /** Echoes every line back. */
    private class EchoServer : AutoCloseable {
        private val socket = ServerSocket(0)
        val port: Int = socket.localPort

        init {
            thread(isDaemon = true) {
                while (!socket.isClosed) {
                    val client = runCatching { socket.accept() }.getOrNull() ?: break
                    thread(isDaemon = true) { client.use { it.getInputStream().copyTo(it.getOutputStream()) } }
                }
            }
        }

        override fun close() = socket.close()
    }

    companion object {
        private const val USER = "alice"
        private const val PASSWORD = "correct horse battery staple"

        @BeforeClass
        @JvmStatic
        fun useAndroidCryptoImplementations() {
            // On Android JSch uses BouncyCastle for these; make the JVM tests take the same code paths.
            JSch.setConfig("xdh", "com.jcraft.jsch.bc.XDH")
            JSch.setConfig("keypairgen.eddsa", "com.jcraft.jsch.bc.KeyPairGenEdDSA")
            JSch.setConfig("ssh-ed25519", "com.jcraft.jsch.bc.SignatureEd25519")
            JSch.setConfig("ssh-ed448", "com.jcraft.jsch.bc.SignatureEd448")
            JSch.setConfig("mlkem768", "com.jcraft.jsch.bc.MLKEM768")
            JSch.setConfig("mlkem1024", "com.jcraft.jsch.bc.MLKEM1024")
        }

        inline fun <reified T : Throwable> assertFailsWith(block: () -> Unit): T {
            try {
                block()
            } catch (e: Throwable) {
                if (e is T) return e
                throw AssertionError("Expected ${T::class.simpleName} but got $e", e)
            }
            fail("Expected ${T::class.simpleName}")
            throw IllegalStateException()
        }
    }
}
