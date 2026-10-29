package com.nocap.terminal.ui.snippets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nocap.terminal.data.Snippet
import com.nocap.terminal.data.SnippetVariables
import com.nocap.terminal.ui.theme.MonoStyle

/**
 * Runs snippets, first asking for the values of any {{variables}} in them.
 * Call the returned function to run a snippet; [send] receives the final command.
 */
@Composable
fun rememberSnippetRunner(send: (command: String, pressEnter: Boolean) -> Unit): (Snippet) -> Unit {
    var pending by remember { mutableStateOf<Snippet?>(null) }

    pending?.let { snippet ->
        val variables = remember(snippet) { SnippetVariables.find(snippet.command) }
        val values = remember(snippet) { mutableStateMapOf<String, String>().apply { variables.forEach { put(it.name, it.defaultValue) } } }
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(snippet.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    variables.forEach { variable ->
                        OutlinedTextField(
                            value = values[variable.name].orEmpty(),
                            onValueChange = { values[variable.name] = it },
                            label = { Text(variable.name) },
                            singleLine = true,
                            textStyle = MonoStyle,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text(
                        SnippetVariables.fill(snippet.command, values),
                        style = MonoStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    send(SnippetVariables.fill(snippet.command, values), snippet.runImmediately)
                    pending = null
                }) { Text("Run") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
        )
    }

    return { snippet ->
        if (SnippetVariables.find(snippet.command).isEmpty()) send(snippet.command, snippet.runImmediately) else pending = snippet
    }
}
