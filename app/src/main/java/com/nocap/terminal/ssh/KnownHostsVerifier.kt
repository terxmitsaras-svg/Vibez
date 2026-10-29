package com.nocap.terminal.ssh

import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.UserInfo
import com.nocap.terminal.data.KnownHost
import com.nocap.terminal.data.KnownHostRepository
import java.util.Base64

/**
 * Verifies server host keys against the app's known hosts, asking the user to trust unknown keys and
 * loudly warning about changed ones. Used with StrictHostKeyChecking=yes, so the verdict returned by
 * [check] is final.
 */
class KnownHostsVerifier(
    private val repository: KnownHostRepository,
    private val prompts: PromptBroker,
    private val owner: Any,
) : HostKeyRepository {

    override fun check(host: String, key: ByteArray): Int {
        val type = SshKeys.keyTypeOf(key)
        val encoded = Base64.getEncoder().encodeToString(key)
        val fingerprint = SshKeys.fingerprint(key)
        val known = repository.find(host)
        if (known.any { it.keyType == type && it.key == encoded }) return HostKeyRepository.OK

        val previous = known.firstOrNull { it.keyType == type }
        val accepted = if (previous == null) {
            prompts.confirm(
                owner = owner,
                title = "Unknown host",
                message = buildString {
                    append("The authenticity of $host can't be established.\n\n")
                    append("$type key fingerprint:\n$fingerprint\n\n")
                    if (known.isNotEmpty()) append("A key of another type is already trusted for this host.\n\n")
                    append("Trust this host and continue connecting?")
                },
                confirmLabel = "Trust",
                dismissLabel = "Cancel",
            )
        } else {
            prompts.confirm(
                owner = owner,
                title = "Host key changed!",
                message = "The $type host key of $host is different from the one you trusted before. " +
                    "Someone could be eavesdropping on you (man-in-the-middle attack), or the server was reinstalled.\n\n" +
                    "Trusted: ${previous.fingerprint}\nOffered: $fingerprint\n\n" +
                    "Only accept the new key if you know why it changed.",
                confirmLabel = "Accept new key",
                dismissLabel = "Disconnect",
                isWarning = true,
            )
        }
        if (!accepted) return if (previous == null) HostKeyRepository.NOT_INCLUDED else HostKeyRepository.CHANGED

        repository.trust(KnownHost(host, type, encoded, fingerprint))
        return HostKeyRepository.OK
    }

    override fun add(hostkey: HostKey, ui: UserInfo?) {
        val blob = Base64.getDecoder().decode(hostkey.key)
        repository.trust(KnownHost(hostkey.host, SshKeys.keyTypeOf(blob), hostkey.key, SshKeys.fingerprint(blob)))
    }

    override fun remove(host: String, type: String?) = repository.remove(host, type)

    override fun remove(host: String, type: String?, key: ByteArray?) = repository.remove(host, type)

    override fun getKnownHostsRepositoryID(): String = "nocap-known-hosts"

    override fun getHostKey(): Array<HostKey> = toHostKeys(repository.knownHosts.value)

    override fun getHostKey(host: String?, type: String?): Array<HostKey> = toHostKeys(
        repository.knownHosts.value.filter { (host == null || it.host == host) && (type == null || it.keyType == type) }
    )

    private fun toHostKeys(entries: List<KnownHost>): Array<HostKey> = entries.mapNotNull { entry ->
        runCatching { HostKey(entry.host, Base64.getDecoder().decode(entry.key)) }.getOrNull()
    }.toTypedArray()
}
