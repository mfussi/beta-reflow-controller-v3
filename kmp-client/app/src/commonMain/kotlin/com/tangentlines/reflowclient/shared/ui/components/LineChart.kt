package com.tangentlines.reflowclient.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tangentlines.reflowclient.shared.model.PhaseType
import com.tangentlines.reflowclient.shared.model.ReflowProfile
import com.tangentlines.reflowclient.shared.model.State
import io.github.koalaplot.core.line.LinePlot
import io.github.koalaplot.core.style.LineStyle
import io.github.koalaplot.core.util.ExperimentalKoalaPlotApi
import io.github.koalaplot.core.xygraph.AxisStyle
import io.github.koalaplot.core.xygraph.CategoryAxisModel
import io.github.koalaplot.core.xygraph.DefaultPoint
import io.github.koalaplot.core.xygraph.DoubleLinearAxisModel
import io.github.koalaplot.core.xygraph.VerticalLineAnnotation
import io.github.koalaplot.core.xygraph.XYGraph
import io.github.koalaplot.core.xygraph.XYGraphScope
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToLong

private fun buildPhasePlan(profile: ReflowProfile): List<Pair<Long, Double>> {
    val xSec = mutableListOf<Double>()
    val yDeg = mutableListOf<Double>()

    var t = 0.0
    var lastTarget: Double? = null

    for (ph in profile.phases) {
        val durSec = when (ph.type) {
            PhaseType.HEATING -> ph.time.coerceAtLeast(0).toDouble()
            PhaseType.REFLOW  -> ph.holdFor.coerceAtLeast(0).toDouble()
            PhaseType.COOLING -> ph.time.coerceAtLeast(0).toDouble()
        }
        if (durSec <= 0.0) continue

        // close previous segment horizontally
        if (lastTarget != null) {
            xSec += t; yDeg += lastTarget     // keep old target up to boundary
        }

        // new target at the same boundary (vertical step)
        val tgt = ph.targetTemperature.toDouble()
        xSec += t; yDeg += tgt

        lastTarget = tgt
        t += durSec
    }

    // extend final segment to the plan end
    if (lastTarget != null) {
        xSec += t; yDeg += lastTarget
    }

    // Convert seconds -> Date (epoch 0 + sec*1000)
    return xSec.map { it.roundToLong() }.zip(yDeg)

}

private fun buildPhaseCutsSec(profile: ReflowProfile): List<Double> {
    val cuts = mutableListOf<Double>()
    var t = 0.0
    for (ph in profile.phases) {
        cuts += t
        t += when (ph.type) {
            PhaseType.HEATING -> ph.time.coerceAtLeast(0).toDouble()
            PhaseType.REFLOW  -> ph.holdFor.coerceAtLeast(0).toDouble()
            PhaseType.COOLING -> ph.time.coerceAtLeast(0).toDouble()
        }
    }
    if (cuts.isNotEmpty()) cuts += t
    return cuts
}

data class ChartDataBase(
    val phaseCuts: List<Double>,
    val tempPointsA: List<Pair<Long, Float>>,
    val tempPointsB: List<Pair<Long, Float>>,
    val intensityPointsA: List<Pair<Long, Float>>,
    val intensityPointsB: List<Pair<Long, Float>>,
    val profileTemp: List<Pair<Long, Float>>?
)

fun createChartData(
    states: List<State>,
    profile: ReflowProfile?,
    maxPoints: Int = 40
): ChartDataBase? {
    if (states.isEmpty() || maxPoints < 2) return null

    // ---------- 1) Build sample deltas (sec) from earliest sample ----------
    val startMs = states.minOf { it.time }
    val deltaSecPerSample = states.map { ((it.time - startMs) / 1000L) }  // 0-based seconds

    // Raw series from samples (arbitrary cadence)
    val cmdPct     = states.map { it.intensity * 100f }
    val activePct  = states.map { (it.activeIntensity ?: 0f) * 100f }
    val temps      = states.map { it.temperature }
    val targets    = states.map { it.targetTemperature ?: 0f }

    fun seriesPoints(values: List<Float>): List<Pair<Long, Float>> =
        values.mapIndexed { i, v -> deltaSecPerSample[i] to v }
            .sortedBy { it.first }

    val sCmd      = seriesPoints(cmdPct)
    val sActive   = seriesPoints(activePct)
    val sTemp     = seriesPoints(temps)
    val sTarget   = seriesPoints(targets)

    // Profile “plan” as raw (sec,temp); we need it BEFORE maxSec to influence the range
    val profilePlanRaw: List<Pair<Long, Float>>? =
        profile?.let { buildPhasePlan(it) }      // returns List<Pair<Long, Double>>
            ?.map { it.first to it.second.toFloat() }

    // ---------- 2) Compute maxSec using BOTH samples and profile plan, then pad +60s ----------
    val samplesMaxSec = deltaSecPerSample.maxOrNull() ?: 0L
    val planMaxSec    = profilePlanRaw?.maxOfOrNull { it.first } ?: 0L
    val maxSec        = max(samplesMaxSec, planMaxSec) + 60L   // +1 minute pad

    // ---------- 3) Step-hold ONLY within each series window (no extrapolation) ----------
    data class StepSeries(val data: FloatArray, val start: Int, val end: Int)
    fun stepFillWindow(points: List<Pair<Long, Float>>, endSec: Long): StepSeries {
        val out = FloatArray((endSec + 1).toInt())
        if (points.isEmpty()) return StepSeries(out, Int.MAX_VALUE, Int.MIN_VALUE)

        val sorted = points.sortedBy { it.first }
        val start  = sorted.first().first.coerceAtLeast(0L).toInt()
        val end    = sorted.last().first.coerceAtMost(endSec).toInt()
        var j = 0
        var last = sorted.first().second

        for (sec in start..end) {
            while (j < sorted.size && sorted[j].first <= sec.toLong()) {
                last = sorted[j].second
                j++
            }
            out[sec] = last
        }
        return StepSeries(out, start, end)
    }

    val secTemp      = stepFillWindow(sTemp,   maxSec)
    val secTarget    = stepFillWindow(sTarget, maxSec)
    val secCmd       = stepFillWindow(sCmd,    maxSec)
    val secActive    = stepFillWindow(sActive, maxSec)
    val secProfile   = profilePlanRaw?.let { stepFillWindow(it, maxSec) }

    // ---------- 4) Pick ≤maxPoints evenly spaced seconds over full range [0..maxSec] ----------
    fun sampleSecondIndices(totalSec: Long, maxPts: Int): IntArray {
        val length = (totalSec + 1).toInt() // inclusive
        if (length <= maxPts) return IntArray(length) { it }
        val step = totalSec.toDouble() / (maxPts - 1)
        val set = LinkedHashSet<Int>(maxPts)
        for (i in 0 until maxPts) {
            val idx = floor(i * step).toInt().coerceIn(0, length - 1)
            set += idx
        }
        set += 0
        set += (length - 1)
        return set.toList().sorted().toIntArray()
    }

    val idx = sampleSecondIndices(maxSec, maxPoints)

    // Sample helper that ONLY yields points inside the series window
    fun pairsFromSeries(s: StepSeries, indices: IntArray): List<Pair<Long, Float>> =
        indices.asSequence()
            .filter { it in s.start..s.end }
            .map { it.toLong() to s.data[it] }
            .toList()

    val tempPointsA      = pairsFromSeries(secTemp,   idx)
    val tempPointsB      = pairsFromSeries(secTarget, idx)
    val intensityPointsA = pairsFromSeries(secCmd,    idx)
    val intensityPointsB = pairsFromSeries(secActive, idx)
    val profileTemp      = secProfile?.let { pairsFromSeries(it, idx) }

    // ---------- 5) Phase cuts (keep; clamp to range) ----------
    val phaseCuts: List<Double> =
        (profile?.let { buildPhaseCutsSec(it) } ?: emptyList())
            .filter { it in 0.0..maxSec.toDouble() }

    return ChartDataBase(
        phaseCuts = phaseCuts,
        tempPointsA = tempPointsA,
        tempPointsB = tempPointsB,
        intensityPointsA = intensityPointsA,
        intensityPointsB = intensityPointsB,
        profileTemp = profileTemp
    )
}

@Composable
fun IntensityChart(base : ChartDataBase?) {

    if(base == null) {
        return
    }

    val baseColor = MaterialTheme.colorScheme.primary
    val secColor = lerp(baseColor, Color.Black, 0.75f)

    LineChart(
        series = listOf(
            Series("Command %", base.intensityPointsA, baseColor, 2.dp),
            Series("Active %", base.intensityPointsB, secColor)
        ),
        yLabel = { "${it.toInt()}%" },
        minYValue = 0.0,
        maxYValue = 120.0,
        verticalLines = base.phaseCuts
    )

}

@Composable
fun TemperatureChart(base : ChartDataBase?) {

    if(base == null) {
        return
    }

    val maxValue = (listOfNotNull(
        base.profileTemp?.maxOfOrNull { it.second },
        base.tempPointsA.maxOfOrNull { it.second },
        base.tempPointsB.maxOfOrNull { it.second },
    ).maxOfOrNull { it } ?: 300.0f) * 1.2f

    val baseColor = MaterialTheme.colorScheme.primary
    val secColor = lerp(baseColor, Color.Black, 0.75f)
    val altColor = lerp(baseColor, Color.Black, 0.50f)

    LineChart(
        series = listOfNotNull(
            Series("Temp", base.tempPointsA, baseColor, 2.dp),
            Series("Target", base.tempPointsB, secColor),
            base.profileTemp?.let { Series("Profile", it, altColor) }
        ),
        yLabel = { "${it.toInt()}°C" },
        minYValue = 0.0,
        maxYValue =  maxValue.toDouble(),
        verticalLines = base.phaseCuts
    )


}

data class Series(
    val name: String,
    val points: List<Pair<Long, Float>>,
    val color: Color,
    val thickness: Dp = 1.dp
)

@Composable
fun LineChart(
    title: String? = null,
    series: List<Series>,
    yLabel: ((Double) -> String),
    minYValue: Double = 0.0,
    maxYValue: Double = 300.0,
    verticalLines: List<Double> = emptyList()
) {
    Column(Modifier.fillMaxWidth().padding(8.dp)) {
        title?.let { Text(it, style = MaterialTheme.typography.titleMedium) }

        val all = series.flatMap { it.points }
        if (all.isEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("No data", style = MaterialTheme.typography.bodyMedium)
            return
        }

        Spacer(Modifier.height(8.dp))

        Box(Modifier.fillMaxWidth().height(220.dp)) {

            val allTimes = series.map { it.points.map { it.first.toDouble() } }.flatten().toMutableList()
            allTimes.addAll(verticalLines)

            val xMin = (allTimes.minOrNull() ?: 0).toInt()
            val xMax = (allTimes.maxOrNull() ?: (60*5)).toInt()
            val step = 1

            XYGraph(
                xAxisModel = CategoryAxisModel(steppedRangeInclusive(xMin, xMax, step)),
                yAxisModel = DoubleLinearAxisModel(minYValue..maxYValue, minimumMajorTickSpacing = 50.dp),
                xAxisStyle = AxisStyle(labelRotation = 90),
                horizontalMinorGridLineStyle = null,
                verticalMinorGridLineStyle = null,
                horizontalMajorGridLineStyle = null,
                verticalMajorGridLineStyle = null,
                xAxisLabels = {

                    val duration = it.toInt()
                    if(duration % 30 == 0) {
                        val minutes = (duration / 60)
                        val seconds = duration - (minutes * 60)
                        XLabel("${minutes.twoDigits()}:${seconds.twoDigits()}")
                    }

                },
                xAxisTitle = { "Time" },
                yAxisLabels = { YLabel(yLabel.invoke(it)) },
                yAxisTitle = { }
            ) {

                verticalLines.forEach {
                    this.VerticalLineAnnotation(it, LineStyle(SolidColor(Color.Black)))
                }

                series.forEach { s ->
                    val data: List<DefaultPoint<Double, Double>> =
                        s.points.sortedBy { it.first }
                            .map { it.first.toDouble() to it.second.toDouble() }
                            .map { DefaultPoint(it.first, it.second) }

                    LineChart(
                        data = data,
                        primaryColor = s.color,
                        thickness = s.thickness
                    )

                }

            }

        }

        Spacer(Modifier.height(8.dp))

    }

}

@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
private fun XYGraphScope<Double, Double>.LineChart(
    data: List<DefaultPoint<Double, Double>>,
    primaryColor : Color,
    secondaryColor : Color? = null,
    thickness: Dp = 2.dp
) {
    LinePlot(
        data = data,
        lineStyle = LineStyle(
            brush = SolidColor(secondaryColor ?: primaryColor),
            strokeWidth = thickness
        )
    )
}

@Composable
private fun YLabel(label : String, modifier: Modifier = Modifier) {

    Text(text = " $label ",
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        modifier = modifier)

}

@Composable
private fun XAxisTitle(label: String) {

    Text(text = label,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        modifier = Modifier.absolutePadding(right = 4.dp))

}

@Composable
private fun XLabel(label: String, modifier: Modifier = Modifier) {

    Text(text = " $label ",
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        textAlign = TextAlign.End,
        modifier = modifier.padding(start = 8.dp, end = 8.dp))

}

private fun steppedRangeInclusive(xMin: Int, xMax: Int, step: Int): List<Double> {
    require(step > 0) { "step must be > 0" }
    val stepD = step.toDouble()
    return buildList {
        var v = xMin.toDouble()
        add(v)                        // always include min
        while (v + stepD < xMax) {
            v += stepD
            add(v)
        }
        if (last() != xMax.toDouble()) add(xMax.toDouble()) // always include max
    }
}

fun Int.twoDigits(): String =
    if (this >= 0) toString().padStart(2, '0')
    else "-" + (-this).toString().padStart(2, '0')

fun Long.twoDigits(): String =
    if (this >= 0) toString().padStart(2, '0')
    else "-" + (-this).toString().padStart(2, '0')