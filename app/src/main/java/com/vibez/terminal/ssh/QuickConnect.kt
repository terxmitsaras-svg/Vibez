package com.vibez.terminal.ssh

import com.vibez.terminal.data.Host

/**
 * Parses what people type to connect somewhere: "user@host", "user@host:2222", "user@[::1]:22",
 * "ssh://user@host" or a pasted "ssh user@host -p 2222".
 */
object QuickConnect {

    fun parse(input: String): Host? {
        val tokens = input.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.toMutableList()
        if (tokens.firstOrNull() == "ssh") tokens.removeAt(0)

        var port: Int? = null
        var target: String? = null
        var index = 0
        while (index < tokens.size) {
            val token = tokens[index]
            when {
                token == "-p" -> {
                    port = tokens.getOrNull(index + 1)?.toIntOrNull() ?: return null
                    index++
                }
                token.startsWith("-p") -> port = token.substring(2).toIntOrNull() ?: return null
                token.startsWith("-") -> return null
                target == null -> target = token
                else -> return null
            }
            index++
        }

        val address = target?.removePrefix("ssh://")?.trimEnd('/') ?: return null
        val at = address.lastIndexOf('@')
        if (at <= 0) return null
        val user = address.substring(0, at)
        val hostPart = address.substring(at + 1)

        val hostname: String
        when {
            hostPart.startsWith("[") -> {
                val end = hostPart.indexOf(']')
                if (end < 0) return null
                hostname = hostPart.substring(1, end)
                val rest = hostPart.substring(end + 1)
                if (rest.isNotEmpty()) {
                    if (!rest.startsWith(":")) return null
                    port = port ?: rest.substring(1).toIntOrNull() ?: return null
                }
            }
            hostPart.count { it == ':' } == 1 -> {
                hostname = hostPart.substringBefore(':')
                port = port ?: hostPart.substringAfter(':').toIntOrNull() ?: return null
            }
            else -> hostname = hostPart
        }

        val finalPort = port ?: 22
        if (hostname.isBlank() || finalPort !in 1..65535) return null
        return Host(hostname = hostname, port = finalPort, username = user)
    }
}
