package com.tangentlines.reflowclient.shared.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.tangentlines.reflowclient.shared.model.ReflowProfile
import com.tangentlines.reflowclient.shared.model.State
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


@Composable
fun AnimatedVisibilityScope.TemperatureChartSection(states: List<State>, profile: ReflowProfile?) {

    SectionCard("Temperature", description = "Plots measured temperature against the target/profile plan over time. Phase boundaries help correlate behavior with each stage of the recipe.") {
        DeferredChartsSection(states, profile) {
            TemperatureChart(it)
        }
    }

}

@Composable
fun AnimatedVisibilityScope.IntensityChartSection(states: List<State>, profile: ReflowProfile?) {

    SectionCard("Intensity", description = "Shows commanded vs. active heater power in percent. Use it to diagnose PID behavior, power limits, and responsiveness.") {
        DeferredChartsSection(states, profile) {
            IntensityChart(it)
        }
    }

}

/**
 * Shows a lightweight placeholder first, then (after deferMs) composes the real chart.
 * Also preprocesses the chart data off the main thread before showing the chart.
 */
@Composable
fun AnimatedVisibilityScope.DeferredChartsSection(
    states: List<State>,
    profile: ReflowProfile?,
    modifier: Modifier = Modifier,
    placeholder: @Composable (() -> Unit) = { ChartsPlaceholder() },
    chart: @Composable ((ChartDataBase?) -> Unit),
) {

    val enterSettled by remember {
        derivedStateOf { !transition.isRunning && transition.targetState == transition.currentState }
    }

    // Optional: do heavy preprocessing off the main thread BEFORE composing the chart
    // If your ChartsSection does no heavy work, you can skip this block.
    val processed : ChartDataBase? by produceState(initialValue = null, key1 = states, key2 = profile) {
        value = withContext(Dispatchers.Default) {
            createChartData(states, profile)
        }
    }

    Crossfade(targetState = enterSettled, modifier = modifier, label = "chartCrossfade") { ready ->
        if (!ready) {
            placeholder.invoke()
        } else {
            chart.invoke(processed)
        }
    }

}

@Composable
private fun ChartsPlaceholder() {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant) // a tad brighter than card bg
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.width(4.dp))
            androidx.compose.material3.Text(
                "Preparing chart…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}