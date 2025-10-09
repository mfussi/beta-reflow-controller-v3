package com.tangentlines.reflowclient.shared.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.tangentlines.reflowclient.shared.model.StatusDto
import com.tangentlines.reflowclient.shared.ui.utils.duration
import com.tangentlines.reflowclient.shared.ui.utils.durationMs
import com.tangentlines.reflowclient.shared.ui.utils.f1
import com.tangentlines.reflowclient.shared.ui.utils.percent0

enum class Tone { Neutral, Positive, Warning, Critical }

data class StatusRow(
    val label: AnnotatedString,
    val value: AnnotatedString,
    val tone: Tone = Tone.Neutral
)

// --- Status key-value list ---
@Composable
fun StatusCard(rows: List<StatusRow>) {
    SectionCard("Status") {
        rows.forEachIndexed { i, row ->
            val toneColor = when (row.tone) {
                Tone.Positive -> MaterialTheme.colorScheme.tertiary
                Tone.Warning  -> MaterialTheme.colorScheme.error.copy(alpha = 0.80f)
                Tone.Critical -> MaterialTheme.colorScheme.error
                else          -> MaterialTheme.colorScheme.onSurface
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = row.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )
                Text(
                    text = row.value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = toneColor
                )
            }
            if (i != rows.lastIndex) Spacer(Modifier.height(8.dp))
        }
    }
}

private val Bold = SpanStyle(fontWeight = FontWeight.SemiBold)
private val UnitDim = SpanStyle(color = Color.Unspecified.copy(alpha = 0.75f))

private inline fun asLabel(text: String) = buildAnnotatedString { append(text) }
private inline fun bold(text: String) = buildAnnotatedString { withStyle(Bold) { append(text) } }

/** Bold the number, append unit (not bold). Color comes from the Text() color param. */
private fun valueNumberWithUnit(num: String, unit: String?): AnnotatedString =
    buildAnnotatedString {
        withStyle(Bold) { append(num) }
        if (!unit.isNullOrBlank()) {
            append(" ")
            append(unit) // no special color -> same color as number
        }
    }

private fun valueTwoNumbers(
    aNum: String, aUnit: String?,
    bNum: String, bUnit: String?,
    sep: String = " / "
): AnnotatedString = buildAnnotatedString {
    append(valueNumberWithUnit(aNum, aUnit))
    append(sep)
    append(valueNumberWithUnit(bNum, bUnit))
}

fun mapStatusRows(s: StatusDto): List<StatusRow> {
    val rows = mutableListOf<StatusRow>()

    // Mode
    rows += StatusRow(
        label = asLabel("Mode"),
        value = bold(s.mode?.replaceFirstChar { it.uppercase() } ?: "-")
    )

    // Uptime
    rows += StatusRow(
        label = asLabel("Uptime"),
        value = valueNumberWithUnit(duration(s.timeAlive?.let { it / 1000 }), null)
    )

    // Phase (if profile is available)
    s.profile?.let { prof ->
        val phaseName = prof.phases.getOrNull(s.phase ?: -1)?.name ?: "-"
        rows += StatusRow(
            label = asLabel("Phase"),
            value = bold(phaseName)
        )

        rows += StatusRow(
            label = asLabel("Next Phase In"),
            value = valueTwoNumbers(
                aNum = durationMs(s.nextPhaseIn), aUnit = null,
                bNum = durationMs(s.phaseTime),  bUnit = null
            )
        )
    }

    // Temperature (measured / target)
    val tempTone =
        when {
            (s.targetTemperature != null && (s.temperature ?: 0.0f) > (s.targetTemperature + 5f)) -> Tone.Warning
            else -> Tone.Neutral
        }

    rows += StatusRow(
        label = asLabel("Temperature"),
        value = valueTwoNumbers(
            aNum = s.temperature.f1(),        aUnit = "°C",
            bNum = s.targetTemperature.f1(),  bUnit = "°C"
        ),
        tone = tempTone
    )

    // Intensity (active / command) -> percent0() already includes %, so pass null units
    rows += StatusRow(
        label = asLabel("Intensity"),
        value = valueTwoNumbers(
            aNum = s.activeIntensity.percent0(), aUnit = null,
            bNum = s.intensity.percent0(),       bUnit = null
        )
    )

    // Timers
    rows += StatusRow(label = asLabel("Over-Temp"),  value = bold(durationMs(s.timeSinceTempOver)))
    rows += StatusRow(label = asLabel("Last Cmd"),   value = bold(durationMs(s.timeSinceCommand)))

    return rows
}