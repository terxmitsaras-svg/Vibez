package com.nocap.terminal.data

/**
 * Imports hosts from an OpenSSH client configuration file (~/.ssh/config).
 *
 * Follows OpenSSH's rules: Host sections (including wildcard and negated patterns) apply in file
 * order and the first value of an option wins. A host is created for every alias without wildcards.
 *
 * Supported options: HostName, User, Port, ProxyJump (chains of aliases
 * or user@host:port), LocalForward, RemoteForward, DynamicForward, Compression, ForwardAgent,
 * ServerAliveInterval, SetEnv and RemoteCommand are mapped. Keys
 * (IdentityFile) cannot be read from another device's disk and must be imported into the
 * keychain separately.
 */
object SshConfigImporter {

    data class Result(val hosts: List<Host>, val skipped: List<String>)

    private class Section(val patterns: List<String>, val options: MutableList<Pair<String, String>> = mutableListOf())

    fun parse(text: String, existing: List<Host> = emptyList()): Result {
        val sections = mutableListOf(Section(listOf("*")))
        val aliases = linkedSetOf<String>()
        val skipped = mutableListOf<String>()
        var inMatch = false

        for (rawLine in text.lines()) {
            val line = rawLine.substringBefore('#').trim()
            if (line.isEmpty()) continue
            val (keyword, value) = splitOption(line) ?: continue
            when (keyword) {
                "host" -> {
                    inMatch = false
                    val patterns = value.split(Regex("\\s+")).filter { it.isNotEmpty() }
                    sections += Section(patterns)
                    aliases += patterns.filter { pattern -> pattern.none { it in "*?!" } }
                }
                "match" -> {
                    inMatch = true
                    skipped += "Match $value (not supported)"
                }
                else -> if (!inMatch) sections.last().options += keyword to value
            }
        }

        /** OpenSSH semantics: walk matching sections in file order; the first value wins. */
        fun resolve(alias: String): Map<String, List<String>> {
            val resolved = linkedMapOf<String, MutableList<String>>()
            for (section in sections) {
                if (!matches(section.patterns, alias)) continue
                for ((keyword, value) in section.options) {
                    val values = resolved.getOrPut(keyword) { mutableListOf() }
                    if (keyword in REPEATABLE || values.isEmpty()) values += value
                }
            }
            return resolved
        }

        // Reuse the ids of hosts imported before, so a second import updates instead of duplicating.
        val byAlias = aliases.associateWith { alias -> existing.firstOrNull { it.label == alias }?.id ?: newId() }
        val extraHosts = mutableListOf<Host>()

        fun jumpHostId(spec: String): String? {
            byAlias[spec]?.let { return it }
            val parsed = parseUserHostPort(spec) ?: return null
            val existingJump = (existing + extraHosts).firstOrNull {
                it.hostname == parsed.second && it.port == parsed.third && it.username == parsed.first
            }
            existingJump?.let { return it.id }
            return Host(label = spec, hostname = parsed.second, port = parsed.third, username = parsed.first, group = "Imported")
                .also { extraHosts += it }.id
        }

        val hosts = aliases.map { alias ->
            val options = resolve(alias)
            fun option(keyword: String): String? = options[keyword]?.firstOrNull()
            fun all(keyword: String): List<String> = options[keyword].orEmpty()

            val previous = existing.firstOrNull { it.label == alias }
            val jumps = option("proxyjump")?.takeIf { it != "none" }?.split(',')?.map { it.trim() }.orEmpty()
            // A chain "a,b" means: connect to a, then through it to b, then to the host.
            var jumpId: String? = null
            for (jump in jumps) {
                val id = jumpHostId(jump) ?: continue
                if (jumpId != null) {
                    val index = extraHosts.indexOfFirst { it.id == id }
                    if (index >= 0) extraHosts[index] = extraHosts[index].copy(jumpHostId = jumpId)
                }
                jumpId = id
            }
            val forwards = all("localforward").mapNotNull { parseForward(it, ForwardType.LOCAL) } +
                all("remoteforward").mapNotNull { parseForward(it, ForwardType.REMOTE) } +
                all("dynamicforward").mapNotNull { parseDynamic(it) }
            val environment = all("setenv").flatMap { it.split(Regex("\\s+")) }
                .mapNotNull { pair -> pair.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1].trim('"') } }
                .toMap()
            Host(
                id = byAlias.getValue(alias),
                label = alias,
                hostname = option("hostname") ?: alias,
                port = option("port")?.toIntOrNull() ?: 22,
                username = option("user").orEmpty(),
                group = previous?.group ?: "Imported",
                tags = previous?.tags.orEmpty(),
                color = previous?.color ?: HostColors.NONE,
                encryptedPassword = previous?.encryptedPassword,
                keyId = previous?.keyId,
                identityId = previous?.identityId,
                jumpHostId = jumpId,
                compression = option("compression").equals("yes", ignoreCase = true),
                agentForwarding = option("forwardagent").equals("yes", ignoreCase = true),
                keepAliveSeconds = option("serveraliveinterval")?.toIntOrNull() ?: 30,
                startupCommand = option("remotecommand").orEmpty(),
                environment = environment,
                portForwards = forwards,
                createdAt = previous?.createdAt ?: System.currentTimeMillis(),
                lastConnectedAt = previous?.lastConnectedAt ?: 0,
            )
        }
        return Result(extraHosts + hosts, skipped)
    }

    /** OpenSSH Host pattern lists: any positive pattern matches and no negated one does. */
    internal fun matches(patterns: List<String>, alias: String): Boolean {
        var matched = false
        for (pattern in patterns) {
            if (pattern.startsWith("!")) {
                if (glob(pattern.substring(1), alias)) return false
            } else if (glob(pattern, alias)) {
                matched = true
            }
        }
        return matched
    }

    private fun glob(pattern: String, text: String): Boolean {
        val regex = buildString {
            pattern.forEach { char ->
                when (char) {
                    '*' -> append(".*")
                    '?' -> append('.')
                    else -> append(Regex.escape(char.toString()))
                }
            }
        }
        return Regex(regex, RegexOption.IGNORE_CASE).matches(text)
    }

    private val REPEATABLE = setOf("localforward", "remoteforward", "dynamicforward", "setenv")

    /** "Keyword value", "Keyword=value" or "Keyword = value"; the keyword in lower case. */
    private fun splitOption(line: String): Pair<String, String>? {
        val match = Regex("""^(\S+?)\s*(?:=\s*|\s+)(.+)$""").find(line) ?: return null
        return match.groupValues[1].lowercase() to match.groupValues[2].trim().trim('"')
    }

    /** "[user@]host[:port]" → (user, host, port). */
    internal fun parseUserHostPort(spec: String): Triple<String, String, Int>? {
        val user = spec.substringBefore('@', "")
        val rest = spec.substringAfter('@')
        val host: String
        var port = 22
        if (rest.startsWith("[")) {
            host = rest.substringAfter('[').substringBefore(']')
            rest.substringAfter("]", "").removePrefix(":").toIntOrNull()?.let { port = it }
        } else if (rest.count { it == ':' } == 1) {
            host = rest.substringBefore(':')
            port = rest.substringAfter(':').toIntOrNull() ?: return null
        } else {
            host = rest
        }
        return if (host.isBlank()) null else Triple(user, host, port)
    }

    /** "[bind:]port host:hostport" (also "port host hostport"). */
    private fun parseForward(value: String, type: ForwardType): PortForward? {
        val parts = value.split(Regex("\\s+"))
        if (parts.size < 2) return null
        val (bindAddress, bindPort) = parseBind(parts[0]) ?: return null
        val destination = if (parts.size >= 3) "${parts[1]}:${parts[2]}" else parts[1]
        val destinationHost = destination.substringBeforeLast(':').removePrefix("[").removeSuffix("]")
        val destinationPort = destination.substringAfterLast(':').toIntOrNull() ?: return null
        val defaultBind = if (type == ForwardType.REMOTE) "localhost" else "127.0.0.1"
        return PortForward(
            type = type,
            bindAddress = bindAddress ?: defaultBind,
            bindPort = bindPort,
            destinationHost = destinationHost,
            destinationPort = destinationPort,
        )
    }

    private fun parseDynamic(value: String): PortForward? {
        val (bindAddress, bindPort) = parseBind(value.trim()) ?: return null
        return PortForward(type = ForwardType.DYNAMIC, bindAddress = bindAddress ?: "127.0.0.1", bindPort = bindPort)
    }

    private fun parseBind(value: String): Pair<String?, Int>? {
        val port = value.substringAfterLast(':').toIntOrNull() ?: return null
        val address = if (value.contains(':')) value.substringBeforeLast(':').removePrefix("[").removeSuffix("]") else null
        return address?.let { if (it == "*") "0.0.0.0" else it } to port
    }
}
