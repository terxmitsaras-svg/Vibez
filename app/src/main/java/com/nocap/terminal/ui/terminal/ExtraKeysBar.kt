package com.nocap.terminal.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nocap.terminal.terminal.ExtraKey
import com.nocap.terminal.terminal.ModifierState
import com.nocap.terminal.terminal.TerminalController
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Rows of keys missing from soft keyboards: Esc, Ctrl, Alt, arrows, ... */
@Composable
fun ExtraKeysBar(
    rows: List<List<ExtraKey>>,
    controller: TerminalController,
    foreground: Color,
    accent: Color,
    background: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().background(background)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().height(42.dp)) {
                row.forEach { key ->
                    ExtraKeyButton(key, controller, foreground, accent, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun ExtraKeyButton(
    key: ExtraKey,
    controller: TerminalController,
    foreground: Color,
    accent: Color,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    val modifierState = (key as? ExtraKey.ModifierKey)?.let { controller.modifierState(it.modifier) } ?: ModifierState.OFF

    val gestures = when {
        key is ExtraKey.Code && key.repeatable -> Modifier.pointerInput(key) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = true
                controller.onExtraKey(key)
                val repeat = scope.launch {
                    delay(REPEAT_DELAY_MS)
                    while (isActive) {
                        controller.onExtraKey(key, haptic = false)
                        delay(REPEAT_INTERVAL_MS)
                    }
                }
                waitForUpOrCancellation()
                repeat.cancel()
                pressed = false
            }
        }
        else -> Modifier.pointerInput(key) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    tryAwaitRelease()
                    pressed = false
                },
                onTap = { controller.onExtraKey(key) },
                onLongPress = {
                    if (key is ExtraKey.ModifierKey) controller.toggleModifier(key.modifier, lock = true) else controller.onExtraKey(key)
                },
            )
        }
    }

    Box(
        modifier = modifier
            .padding(horizontal = 2.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    modifierState == ModifierState.LOCKED -> accent.copy(alpha = 0.35f)
                    modifierState == ModifierState.ONCE || pressed -> accent.copy(alpha = 0.18f)
                    else -> Color.Transparent
                }
            )
            .then(gestures)
            .semantics { role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = key.label,
            color = if (modifierState != ModifierState.OFF) accent else foreground,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (modifierState != ModifierState.OFF) FontWeight.Bold else FontWeight.Medium,
            fontSize = if (key.label.length > 3) 11.sp else 14.sp,
            maxLines = 1,
        )
        if (modifierState == ModifierState.LOCKED) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 3.dp)
                    .width(14.dp)
                    .height(2.dp)
                    .background(accent),
            )
        }
    }
}

private const val REPEAT_DELAY_MS = 400L
private const val REPEAT_INTERVAL_MS = 45L
