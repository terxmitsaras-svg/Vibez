package com.vibez.terminal.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibez.terminal.AppContainer
import com.vibez.terminal.BuildConfig
import com.vibez.terminal.data.AppSettings
import com.vibez.terminal.data.BellMode
import com.vibez.terminal.data.CursorStyle
import com.vibez.terminal.data.UiThemeMode
import com.vibez.terminal.terminal.ExtraKeysLayout
import com.vibez.terminal.terminal.LocalShell
import com.vibez.terminal.terminal.TerminalFonts
import com.vibez.terminal.terminal.TerminalTheme
import com.vibez.terminal.terminal.TerminalThemes
import com.vibez.terminal.ui.components.LocalSnackbar
import com.vibez.terminal.ui.components.SectionHeader
import com.vibez.terminal.ui.components.TextFieldDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, onKnownHosts: () -> Unit) {
    val settings by container.settings.settings.collectAsState()
    val knownHosts by container.knownHosts.knownHosts.collectAsState()
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    fun update(transform: (AppSettings) -> AppSettings) = container.settings.update(transform)

    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    var customFont by remember { mutableStateOf(TerminalFonts.hasCustomFont(context)) }

    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { TerminalFonts.import(context, uri) } }
            customFont = TerminalFonts.hasCustomFont(context)
            snackbar.showSnackbar(result.fold({ "Font installed" }, { it.message ?: "Not a valid font file" }))
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        LazyColumn(contentPadding = padding) {
            item { SectionHeader("Terminal") }
            item {
                val theme = TerminalThemes.byName(settings.terminalTheme)
                ListItem(
                    modifier = Modifier.clickable { dialog = SettingsDialog.Theme },
                    headlineContent = { Text("Color theme") },
                    supportingContent = { Text(theme.name) },
                    trailingContent = { ThemeSwatch(theme) },
                )
            }
            item {
                var fontSize by remember(settings.fontSizeSp) { mutableFloatStateOf(settings.fontSizeSp.toFloat()) }
                ListItem(
                    headlineContent = { Text("Font size: ${fontSize.toInt()} sp") },
                    supportingContent = {
                        Slider(
                            value = fontSize,
                            onValueChange = { fontSize = it },
                            onValueChangeFinished = { update { it.copy(fontSizeSp = fontSize.toInt()) } },
                            valueRange = AppSettings.MIN_FONT_SIZE.toFloat()..AppSettings.MAX_FONT_SIZE.toFloat(),
                        )
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Font") },
                    supportingContent = { Text(if (customFont) "Custom font" else "System monospace — import any .ttf/.otf, e.g. a Nerd Font") },
                    trailingContent = {
                        Row {
                            if (customFont) {
                                TextButton(onClick = {
                                    TerminalFonts.reset(context)
                                    customFont = false
                                }) { Text("Reset") }
                            }
                            TextButton(onClick = { fontPicker.launch(arrayOf("font/*", "application/x-font-ttf", "application/octet-stream", "*/*")) }) {
                                Text("Import")
                            }
                        }
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Cursor") },
                    supportingContent = {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            CursorStyle.entries.forEachIndexed { index, style ->
                                SegmentedButton(
                                    selected = settings.cursorStyle == style,
                                    onClick = { update { it.copy(cursorStyle = style) } },
                                    shape = SegmentedButtonDefaults.itemShape(index, CursorStyle.entries.size),
                                ) { Text(style.name.lowercase().replaceFirstChar(Char::uppercase)) }
                            }
                        }
                    },
                )
            }
            item { SwitchRow("Blinking cursor", null, settings.cursorBlink) { value -> update { it.copy(cursorBlink = value) } } }
            item {
                ListItem(
                    modifier = Modifier.clickable { dialog = SettingsDialog.Scrollback },
                    headlineContent = { Text("Scrollback") },
                    supportingContent = { Text("${settings.scrollbackLines} lines (applies to new sessions)") },
                )
            }
            item {
                ListItem(
                    modifier = Modifier.clickable { dialog = SettingsDialog.Bell },
                    headlineContent = { Text("Bell") },
                    supportingContent = { Text(settings.bellMode.name.lowercase().replaceFirstChar(Char::uppercase)) },
                )
            }
            item { SwitchRow("Keep screen on", "While a terminal is visible", settings.keepScreenOn) { value -> update { it.copy(keepScreenOn = value) } } }

            item { SectionHeader("Keyboard") }
            item { SwitchRow("Extra keys row", "Esc, Ctrl, Alt, arrows and more above the keyboard", settings.showExtraKeys) { value -> update { it.copy(showExtraKeys = value) } } }
            item {
                ListItem(
                    modifier = Modifier.clickable { dialog = SettingsDialog.ExtraKeys },
                    headlineContent = { Text("Extra keys layout") },
                    supportingContent = { Text(settings.extraKeysLayout, fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                )
            }
            item {
                SwitchRow("Volume keys as Ctrl / Fn", "Volume down + key = Ctrl+key. Volume up + W/A/S/D = arrows, 1–0 = F1–F10", settings.volumeKeysAsModifiers) { value ->
                    update { it.copy(volumeKeysAsModifiers = value) }
                }
            }
            item { SwitchRow("Back key sends Escape", "Leave the terminal with the ← button instead", settings.backKeyIsEscape) { value -> update { it.copy(backKeyIsEscape = value) } } }
            item {
                SwitchRow("Disable keyboard suggestions", "Recommended. Turn off if your keyboard cannot type in the terminal", settings.enforceCharBasedInput) { value ->
                    update { it.copy(enforceCharBasedInput = value) }
                }
            }

            item { SectionHeader("Local shell") }
            item {
                ListItem(
                    modifier = Modifier.clickable { dialog = SettingsDialog.Shell },
                    headlineContent = { Text("Shell") },
                    supportingContent = { Text(settings.localShell, fontFamily = FontFamily.Monospace) },
                )
            }
            item {
                SwitchRow("Close tab when the shell exits", "Only after a successful exit", settings.closeLocalSessionOnExit) { value ->
                    update { it.copy(closeLocalSessionOnExit = value) }
                }
            }
            item {
                ListItem(
                    headlineContent = { Text("Startup file") },
                    supportingContent = { Text("~/${LocalShell.RC_FILE} — aliases and prompt, edit it from the shell", fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                )
            }

            item { SectionHeader("Security") }
            item {
                SwitchRow("App lock", "Require fingerprint, face or screen lock to open the app", settings.appLock) { enable ->
                    val available = BiometricManager.from(context)
                        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    if (enable && available != BiometricManager.BIOMETRIC_SUCCESS) {
                        scope.launch { snackbar.showSnackbar("Set up a screen lock on this device first") }
                    } else {
                        update { it.copy(appLock = enable) }
                    }
                }
            }
            item {
                SwitchRow("Block screenshots", "Hide the screen from screenshots and the recent apps view", settings.secureScreen) { value ->
                    update { it.copy(secureScreen = value) }
                }
            }
            item {
                ListItem(
                    modifier = Modifier.clickable(onClick = onKnownHosts),
                    headlineContent = { Text("Known hosts") },
                    supportingContent = { Text("${knownHosts.size} trusted host key(s)") },
                )
            }

            item { SectionHeader("Appearance") }
            item {
                ListItem(
                    headlineContent = { Text("App theme") },
                    supportingContent = {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            UiThemeMode.entries.forEachIndexed { index, mode ->
                                SegmentedButton(
                                    selected = settings.uiThemeMode == mode,
                                    onClick = { update { it.copy(uiThemeMode = mode) } },
                                    shape = SegmentedButtonDefaults.itemShape(index, UiThemeMode.entries.size),
                                ) { Text(mode.name.lowercase().replaceFirstChar(Char::uppercase)) }
                            }
                        }
                    },
                )
            }

            item { SectionHeader("About") }
            item {
                ListItem(
                    modifier = Modifier.clickable { dialog = SettingsDialog.About },
                    headlineContent = { Text("Vibez ${BuildConfig.VERSION_NAME}") },
                    supportingContent = { Text("Open source licenses") },
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    when (dialog) {
        SettingsDialog.Theme -> ThemeDialog(
            selected = settings.terminalTheme,
            onSelect = { name -> update { it.copy(terminalTheme = name) } },
            onDismiss = { dialog = null },
        )
        SettingsDialog.Scrollback -> ChoiceDialog(
            title = "Scrollback lines",
            options = listOf(1000, 2000, 5000, 10000, 20000, 50000),
            selected = settings.scrollbackLines,
            label = { "$it lines" },
            onSelect = { lines -> update { it.copy(scrollbackLines = lines) } },
            onDismiss = { dialog = null },
        )
        SettingsDialog.Bell -> ChoiceDialog(
            title = "Bell",
            options = BellMode.entries,
            selected = settings.bellMode,
            label = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
            onSelect = { mode -> update { it.copy(bellMode = mode) } },
            onDismiss = { dialog = null },
        )
        SettingsDialog.ExtraKeys -> TextFieldDialog(
            title = "Extra keys layout",
            label = "One row per line",
            initialValue = settings.extraKeysLayout,
            singleLine = false,
            supportingText = "Special keys: " + ExtraKeysLayout.keyNames.sorted().joinToString(" ") + ". Anything else is typed as text.",
            onConfirm = { layout -> update { it.copy(extraKeysLayout = layout.trim().ifEmpty { AppSettings.DEFAULT_EXTRA_KEYS }) } },
            onDismiss = { dialog = null },
        )
        SettingsDialog.Shell -> TextFieldDialog(
            title = "Shell",
            label = "Absolute path",
            initialValue = settings.localShell,
            supportingText = "Used for new local sessions. Falls back to ${AppSettings.DEFAULT_SHELL} if it cannot be executed.",
            validate = { if (it.startsWith("/")) null else "Must be an absolute path" },
            onConfirm = { path -> update { it.copy(localShell = path.trim()) } },
            onDismiss = { dialog = null },
        )
        SettingsDialog.About -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Open source licenses") },
            text = {
                Text(
                    """
                    Terminal emulator and view — Termux (Apache License 2.0)
                    JSch, mwiede fork — BSD 3-Clause
                    Bouncy Castle — MIT
                    AndroidX, Jetpack Compose, Kotlin — Apache License 2.0
                    """.trimIndent(),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("OK") } },
        )
        null -> Unit
    }
}

private enum class SettingsDialog { Theme, Scrollback, Bell, ExtraKeys, Shell, About }

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.clickable { onChange(!checked) },
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
    )
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = option == selected, role = Role.RadioButton, onClick = {
                                onSelect(option)
                                onDismiss()
                            })
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(label(option))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ThemeDialog(selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Color theme") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(TerminalThemes.all, key = { it.name }) { theme ->
                    ThemePreview(theme, theme.name == selected) {
                        onSelect(theme.name)
                        onDismiss()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun ThemePreview(theme: TerminalTheme, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(border)
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(theme.background))
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Text(
            buildPrompt(theme),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = Color(theme.foreground),
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            theme.ansi.take(8).forEach { color -> Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(Color(color))) }
        }
    }
}

private fun buildPrompt(theme: TerminalTheme) = "${theme.name}  ~/projects $ ls"

@Composable
private fun ThemeSwatch(theme: TerminalTheme) {
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).background(Color(theme.background)).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        listOf(1, 2, 3, 4).forEach { index -> Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(Color(theme.ansi[index]))) }
    }
}
