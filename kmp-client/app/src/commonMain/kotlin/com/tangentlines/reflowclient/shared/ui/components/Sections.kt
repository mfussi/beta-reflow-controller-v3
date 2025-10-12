package com.tangentlines.reflowclient.shared.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// --- API card (collapsed when connected) ---
@Composable
fun ApiCard(
    connected: Boolean,
    host: String,
    discoveredHosts: List<String>,
    onHostChange: (String) -> Unit,
    onScan: () -> Unit,
    onDisconnect: () -> Unit
) {
    val description = when(connected) {
        true -> "You’re connected to the Reflow HTTP API. You can refresh or disconnect at any time."
        else -> "No API connection detected. Select a discovered host to connect and enable device control."
    }

    val actions = listOfNotNull(
        if(!connected) ActionButton("Scan", Icons.Rounded.Refresh, onScan) else null
    )

    SectionCard("API", description = description, actions = actions) {
        Box(Modifier.fillMaxWidth().animateContentSize()) {
            AnimatedCardSwitcher(
                showPrimary = connected,
                primary = {

                    val str = buildAnnotatedString {
                        append("Connected to: ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(host)
                        }
                    }

                    // CONNECTED VIEW
                    Text(str, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    OutlineAccentButton("Disconnect from API", onClick = onDisconnect)
                },
                secondary = {
                    // UNCONNECTED VIEW
                    Spacer(Modifier.height(8.dp))
                    if (discoveredHosts.isNotEmpty()) {
                        ChipRow(items = discoveredHosts, selected = null, onClick = onHostChange)
                    } else {
                        Text("No hosts found.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            )
        }
    }
}

// --- Port connection (collapsed when connected) ---
@Composable
fun ConnectionCard(
    connectedPort: String?,
    ports: List<String>,
    onRefresh: () -> Unit,
    onDisconnect: () -> Unit,
    onConnectPortClick: (String) -> Unit
) {

    val description = when(connectedPort) {
        null -> "No serial port is in use. Select a port to connect to the hardware controller."
        else -> "The controller is connected to the selected serial port. Disconnect or refresh to verify availability."
    }

    val actions = listOfNotNull(
        if((connectedPort == null)) ActionButton("Refresh", Icons.Rounded.Refresh, onRefresh) else null
    )

    SectionCard("Connection", description = description, actions = actions) {
        Box(Modifier.fillMaxWidth().animateContentSize()) {
            AnimatedCardSwitcher(
                showPrimary = (connectedPort != null),
                primary = {
                    // CONNECTED VIEW

                    val str = buildAnnotatedString {
                        append("Connected to serial port: ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(connectedPort)
                        }
                    }

                    Text(str, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    OutlineAccentButton("Disconnect", onClick = onDisconnect)
                },
                secondary = {
                    // DISCONNECTED VIEW
                    Text("Select a port to connect.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    ChipRow(items = ports, selected = null, onClick = onConnectPortClick)
                }
            )
        }
    }
}

// --- Profile selection (start by chip; optional Stop) ---
@Composable
fun ProfileCard(
    availableProfiles: List<String>,   // e.g. listOf("Manual") + server/local profiles
    selected: String?,
    onChipClick: (String) -> Unit,
    onLongChipClick: ((String) -> Unit)?,
    onStop: (() -> Unit)? = null,
    onAdd: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
) {
    val hasActive = selected != null && onStop != null
    val description = when(hasActive) {
        true -> "A profile is currently running. Phase timings and targets come from the selected recipe. Stop the run to switch profiles."
        else -> "Select a reflow profile to start a run. Profiles define the target temperature over time; choose Manual for direct control."
    }

    val actions = listOfNotNull(
        if(!hasActive && onRefresh != null) ActionButton("Refresh", Icons.Default.Refresh, onClick = { onRefresh.invoke() }) else null,
        if(!hasActive && onAdd != null) ActionButton("Add", Icons.Default.Add, onClick = { onAdd.invoke() }) else null
    )

    SectionCard("Profile", description = description, actions = actions) {
        Box(Modifier.fillMaxWidth().animateContentSize()) {
            AnimatedCardSwitcher(
                showPrimary = hasActive,
                primary = {
                    // ACTIVE (show Stop)

                    val str = buildAnnotatedString {
                        append("Active profile: ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("'$selected'")
                        }
                    }

                    Text(str, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    OutlineAccentButton("Stop active profile", onClick = { onStop?.invoke() })
                },
                secondary = {
                    // INACTIVE (show chips)
                    ChipRow(
                        items = availableProfiles,
                        selected = selected,
                        onClick = onChipClick,
                        onLongPress = onLongChipClick,
                    )
                }
            )
        }
    }
}

@Composable
private fun AnimatedCardSwitcher(
    showPrimary: Boolean,
    modifier: Modifier = Modifier,
    primary: @Composable ColumnScope.() -> Unit,
    secondary: @Composable ColumnScope.() -> Unit
) {
    AnimatedContent(
        targetState = showPrimary,
        transitionSpec = {
            // slide up on appear, slide down on disappear (with fades)
            (fadeIn(animationSpec = tween(220)) +
                    slideInVertically(animationSpec = tween(220)) { fullHeight -> fullHeight / 3 }
                    ).togetherWith(
                    fadeOut(animationSpec = tween(200)) +
                            slideOutVertically(animationSpec = tween(200)) { fullHeight -> -fullHeight / 3 }
                ).using(SizeTransform(clip = false))
        },
        modifier = modifier,
        label = "CardSwitcher"
    ) { primaryState ->
        Column {
            if (primaryState) primary() else secondary()
        }
    }
}

@Composable
fun ManualControlCard(
    temp: Float,
    intensity: Float,
    onChange: (Float, Float) -> Unit
) {
    // Local mirrors to provide smooth slider UX; propagate on each change
    var localTemp by remember(temp) { mutableStateOf(temp) }
    var localIntensity by remember(intensity) { mutableStateOf(intensity) }

    SectionCard("Manual Control", description = "Direct, real-time control of target temperature and heater intensity. Useful for preheating, testing, or manual rework—changes are applied immediately.") {
        // Temperature
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Target temperature", style = MaterialTheme.typography.bodyMedium)
            Text("${localTemp.roundToInt()}°C", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
        }

        val ranget = 0f..300f
        Spacer(Modifier.height(8.dp))
        Slider(
            value = localTemp,
            onValueChange = {
                localTemp = it
                onChange(localTemp, localIntensity)
            },
            steps = ((ranget.endInclusive - ranget.start) / 5).toInt(),
            valueRange = ranget,
            colors = SliderDefaults.colors().copy(
                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            )
        )

        Spacer(Modifier.height(16.dp))

        // Intensity
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Command intensity", style = MaterialTheme.typography.bodyMedium)
            Text("${(localIntensity * 100f).roundToInt()}%", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
        }
        Spacer(Modifier.height(8.dp))

        Slider(
            value = localIntensity,
            onValueChange = {
                localIntensity = it
                onChange(localTemp, localIntensity)
            },
            steps = 20,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors().copy(
                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            )
        )
    }
}