package com.nocap.terminal.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.nocap.terminal.ssh.PromptBroker
import com.nocap.terminal.ssh.PromptRequest
import com.nocap.terminal.ssh.SecretAnswer
import com.nocap.terminal.ui.theme.MonoStyle

/** Shows questions from SSH connections (passwords, host keys, ...) one at a time, on any screen. */
@Composable
fun PromptHost(broker: PromptBroker) {
    val requests by broker.requests.collectAsState()
    when (val request = requests.firstOrNull()) {
        null -> Unit
        is PromptRequest.Secret -> SecretPrompt(request)
        is PromptRequest.Confirm -> ConfirmPrompt(request)
        is PromptRequest.KeyboardInteractive -> KeyboardInteractivePrompt(request)
    }
}

private val promptDialogProperties = DialogProperties(dismissOnClickOutside = false)

@Composable
private fun SecretPrompt(request: PromptRequest.Secret) {
    var value by remember(request) { mutableStateOf("") }
    var remember by remember(request) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { request.respond(null) },
        properties = promptDialogProperties,
        title = { Text(request.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (request.message.isNotBlank()) Text(request.message, style = MaterialTheme.typography.bodyMedium)
                SecretTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = request.label,
                    onImeAction = { request.respond(SecretAnswer(value, remember)) },
                )
                if (request.offerRemember) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = remember, onCheckedChange = { remember = it })
                        Text("Save in this app (encrypted)", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { request.respond(SecretAnswer(value, remember)) }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { request.respond(null) }) { Text("Cancel") } },
    )
}

@Composable
private fun ConfirmPrompt(request: PromptRequest.Confirm) {
    val accent = if (request.isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    AlertDialog(
        onDismissRequest = { request.respond(false) },
        properties = promptDialogProperties,
        icon = if (request.isWarning) {
            { Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
        } else {
            null
        },
        title = { Text(request.title, color = if (request.isWarning) accent else MaterialTheme.colorScheme.onSurface) },
        text = {
            Text(
                request.message,
                style = MonoStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = { TextButton(onClick = { request.respond(true) }) { Text(request.confirmLabel, color = accent) } },
        dismissButton = { TextButton(onClick = { request.respond(false) }) { Text(request.dismissLabel) } },
    )
}

@Composable
private fun KeyboardInteractivePrompt(request: PromptRequest.KeyboardInteractive) {
    val answers = remember(request) { mutableStateListOf(*Array(request.fields.size) { "" }) }
    AlertDialog(
        onDismissRequest = { request.respond(null) },
        properties = promptDialogProperties,
        title = { Text(request.title.ifBlank { "Authentication" }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (request.instruction.isNotBlank()) Text(request.instruction, style = MaterialTheme.typography.bodyMedium)
                request.fields.forEachIndexed { index, field ->
                    OutlinedTextField(
                        value = answers[index],
                        onValueChange = { answers[index] = it },
                        label = { Text(field.label.trimEnd(':', ' ')) },
                        singleLine = true,
                        visualTransformation = if (field.echo) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { request.respond(answers.toList()) }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { request.respond(null) }) { Text("Cancel") } },
    )
}
