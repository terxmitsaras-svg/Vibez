package com.nocap.terminal.data

import kotlinx.serialization.Serializable
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

/** A saved SSH server. Secrets are stored encrypted by [SecretBox]. */
@Serializable
data class Host(
    val id: String = newId(),
    val label: String = "",
    val hostname: String,
    val port: Int = 22,
    val username: String,
    val group: String = "",
    val tags: List<String> = emptyList(),
    /** One of [HostColors], used as a visual marker (e.g. red for production). */
    val color: String = HostColors.NONE,
    val protocol: Protocol = Protocol.SSH,
    val encryptedPassword: String? = null,
    val keyId: String? = null,
    /** Reusable credentials; when set, its username, password and key are used. */
    val identityId: String? = null,
    val jumpHostId: String? = null,
    /** Forward the SSH agent (the host's key) to the server, like ssh -A. */
    val agentForwarding: Boolean = false,
    /** Environment variables sent to the server. Servers only accept names allowed by AcceptEnv. */
    val environment: Map<String, String> = emptyMap(),
    val proxy: ProxyConfig? = null,
    /** Command typed into the shell right after connecting. */
    val startupCommand: String = "",
    val keepAliveSeconds: Int = 30,
    val compression: Boolean = false,
    val terminalType: String = "xterm-256color",
    /** Name of a [com.nocap.terminal.terminal.TerminalTheme] overriding the global theme, or empty. */
    val themeOverride: String = "",
    val portForwards: List<PortForward> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val lastConnectedAt: Long = 0,
) {
    val displayName: String get() = label.ifBlank { if (username.isBlank()) hostname else "$username@$hostname" }

    /** user@host[:port], the way it is typed on a command line. */
    val address: String
        get() {
            val user = if (username.isBlank()) "" else "$username@"
            return if (port == protocol.defaultPort) "$user$hostname" else "$user$hostname:$port"
        }
}

@Serializable
enum class Protocol(val defaultPort: Int) { SSH(22), TELNET(23) }

@Serializable
enum class ProxyType { SOCKS5, HTTP }

/** A proxy the connection to the first hop goes through. */
@Serializable
data class ProxyConfig(
    val type: ProxyType = ProxyType.SOCKS5,
    val hostname: String,
    val port: Int,
    val username: String = "",
    val encryptedPassword: String? = null,
)

/** Reusable login credentials shared by several hosts. */
@Serializable
data class Identity(
    val id: String = newId(),
    val name: String,
    val username: String,
    val encryptedPassword: String? = null,
    val keyId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

object HostColors {
    const val NONE = ""
    val all = listOf(NONE, "red", "orange", "yellow", "green", "teal", "blue", "purple", "pink")
}

@Serializable
enum class ForwardType { LOCAL, REMOTE, DYNAMIC }

/**
 * A port forwarding rule, started together with the SSH connection.
 *
 * - LOCAL (-L): [bindAddress]:[bindPort] on this device → [destinationHost]:[destinationPort] seen from the server.
 * - REMOTE (-R): [bindAddress]:[bindPort] on the server → [destinationHost]:[destinationPort] seen from this device.
 * - DYNAMIC (-D): SOCKS5 proxy on [bindAddress]:[bindPort] on this device, connecting through the server.
 */
@Serializable
data class PortForward(
    val id: String = newId(),
    val type: ForwardType = ForwardType.LOCAL,
    val bindAddress: String = "127.0.0.1",
    val bindPort: Int,
    val destinationHost: String = "",
    val destinationPort: Int = 0,
    val enabled: Boolean = true,
) {
    fun describe(): String = when (type) {
        ForwardType.LOCAL -> "L $bindAddress:$bindPort → $destinationHost:$destinationPort"
        ForwardType.REMOTE -> "R $bindAddress:$bindPort → $destinationHost:$destinationPort"
        ForwardType.DYNAMIC -> "D SOCKS5 $bindAddress:$bindPort"
    }
}

/** A private key in the keychain. The private key is kept exactly as generated or imported. */
@Serializable
data class SshKey(
    val id: String = newId(),
    val name: String,
    /** SSH key type, e.g. "ssh-ed25519". */
    val type: String,
    /** The public key as a single authorized_keys line. */
    val publicKey: String,
    /** "SHA256:..." fingerprint of the public key. */
    val fingerprint: String,
    val encryptedPrivateKey: String,
    /** Passphrase of the private key, if it is encrypted. */
    val encryptedPassphrase: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class Snippet(
    val id: String = newId(),
    val name: String,
    val command: String,
    /** Whether Enter is pressed after pasting the command. */
    val runImmediately: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

/** A trusted server host key. [host] is "hostname" for port 22 and "[hostname]:port" otherwise. */
@Serializable
data class KnownHost(
    val host: String,
    val keyType: String,
    /** Base64 of the public key blob. */
    val key: String,
    val fingerprint: String,
    val addedAt: Long = System.currentTimeMillis(),
)
