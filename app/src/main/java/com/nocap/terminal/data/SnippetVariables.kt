package com.nocap.terminal.data

/**
 * Placeholders in snippets, written as {{name}} or {{name:default}}. They are asked for when the
 * snippet runs, e.g. `docker logs -f {{container}}` or `git checkout {{branch:main}}`.
 */
object SnippetVariables {

    data class Variable(val name: String, val defaultValue: String)

    private val pattern = Regex("""\{\{\s*([A-Za-z_][A-Za-z0-9_\-]*)\s*(?::([^}]*))?}}""")

    /** The distinct variables in [command], in order of first appearance. */
    fun find(command: String): List<Variable> =
        pattern.findAll(command)
            .map { Variable(it.groupValues[1], it.groupValues[2].trim()) }
            .distinctBy { it.name }
            .toList()

    /** Replace every placeholder with its value; unknown names get their default. */
    fun fill(command: String, values: Map<String, String>): String =
        pattern.replace(command) { match -> values[match.groupValues[1]] ?: match.groupValues[2].trim() }
}
