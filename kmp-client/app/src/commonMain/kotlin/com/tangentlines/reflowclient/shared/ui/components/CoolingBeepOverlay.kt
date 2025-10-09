package com.tangentlines.reflowclient.shared.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tangentlines.reflowclient.shared.ui.utils.toFixed
import io.ktor.util.date.getTimeMillis
import kotlinx.coroutines.delay

/**
 * Fullscreen overlay that "arms" when [active] becomes true.
 * While armed it beeps and watches temperature to detect "door open" cooling, then dismisses itself.
 *
 * Pass [onBeep] to make sound (platform-specific) and [onDismiss] to react when it disarms.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CoolingBeepOverlay(
    active: Boolean,
    currentTemp: Float?,
    targetTemp: Float?,
    modifier: Modifier = Modifier,
    // --- logic params (same defaults as legacy) ---
    beepIntervalMs: Long = 2000L,
    minDropPerTick: Float = 1.5f,
    dropStreakToOpen: Int = 3,
    baselineDropToOpen: Float = 10.0f,
    targetDeltaToOpen: Float = 15.0f,
    tickMs: Long = 500L,
    // --- hooks ---
    onBeep: (() -> Unit)? = null,          // provide platform beep here (see notes below)
    onDismiss: (() -> Unit)? = null,       // called when overlay auto-disarms
    allowManualDismiss: Boolean = true     // shows a “Silence” button
) {
    // Internal state mirrors the legacy notifier
    var armed by remember { mutableStateOf(false) }
    var baselineTemp by remember { mutableStateOf<Float?>(null) }
    var lastTemp by remember { mutableStateOf<Float?>(null) }
    var lastBeepAt by remember { mutableStateOf(0L) }
    var dropStreak by remember { mutableStateOf(0) }

    // Always read the latest temps inside loops
    val currentTempState by rememberUpdatedState(currentTemp)
    val targetTempState by rememberUpdatedState(targetTemp)

    // Arm on rising edge of 'active'
    LaunchedEffect(active) {
        if (active) {
            armed = true
            baselineTemp = currentTempState
            lastTemp = currentTempState
            dropStreak = 0
            lastBeepAt = 0L
            // Immediate beep on finish, like the Swing version
            onBeep?.invoke()
        } else {
            armed = false
        }
    }

    // Ticker loop while armed
    LaunchedEffect(armed) {
        if (!armed) return@LaunchedEffect
        while (armed) {
            val ct = currentTempState
            val tt = targetTempState

            // slope-based cooling detection window
            val lt = lastTemp
            dropStreak = if (lt != null && ct != null && ct < lt - minDropPerTick) {
                dropStreak + 1
            } else 0

            val baseline = baselineTemp ?: ct
            val cooling = when {
                ct == null -> false
                // 1) below target - delta
                (tt != null && ct <= tt - targetDeltaToOpen) -> true
                // 2) total drop from baseline
                (baseline != null && ct <= baseline - baselineDropToOpen) -> true
                // 3) consecutive downslope samples
                (dropStreak >= dropStreakToOpen) -> true
                else -> false
            }

            if (cooling) {
                armed = false
                onDismiss?.invoke()
                break
            }

            // Beep on interval
            val now = getTimeMillis()
            if (now - lastBeepAt >= beepIntervalMs) {
                onBeep?.invoke()
                lastBeepAt = now
            }

            lastTemp = ct
            delay(tickMs)
        }
    }

    // Fullscreen UI overlay while armed
    AnimatedVisibility(visible = armed) {

        BackHandler(enabled = true) {
            armed = false
            onDismiss?.invoke()
        }

        Box(
            modifier
                .background(MaterialTheme.colorScheme.background)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier
                    .padding(24.dp)
                    .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Cooling step complete", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Open the oven door and remove the board.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(16.dp))

                // Small status line with current/target if available
                val ct = currentTemp
                val tt = targetTemp
                if (ct != null || tt != null) {
                    Text(
                        buildString {
                            append("Temp: ")
                            append(ct?.toFixed(1) ?: "-")
                            if (tt != null) append(" / ${tt.toFixed(1)}") else append(" °C")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(24.dp))

                if (allowManualDismiss) {
                    Button(onClick = {
                        // manual silence
                        armed = false
                        onDismiss?.invoke()
                    }) {
                        Text("Silence")
                    }
                }
            }
        }
    }
}