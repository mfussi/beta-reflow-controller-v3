package com.tangentlines.reflowcontroller.log

import com.tangentlines.reflowcontroller.client.BackendWithEvents
import com.tangentlines.reflowcontroller.client.ControllerBackend
import com.tangentlines.reflowcontroller.reflow.profile.PhaseType
import com.tangentlines.reflowcontroller.reflow.profile.ReflowProfile
import org.knowm.xchart.*
import java.awt.Color
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.*
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.math.roundToLong

class ReflowChart(private val backend: BackendWithEvents) {

    private val subcharts = mutableListOf<SubChart<Int>>()
    private val wrapper : SwingWrapper<XYChart>

    init {

        subcharts.add(SubChart<Int>("Temperature", java.awt.Color.RED, "°C", 0, 300.0, moreData = { _, chart -> drawPhasesOverlay(chart) }) { s -> s.temperature.toInt() })

        subcharts.add(SubChart<Int>("Intensity",java.awt.Color.GREEN, "%", 0, 100.0) { s -> (s.intensity * 100).toInt() })
        subcharts.add(SubChart<Int>("Target Temperature",java.awt.Color.BLUE, "°C", 0, 300.0) { s -> s.targetTemperature?.toInt() ?: 0 })
        subcharts.add(SubChart<Int>("Active Intensity",java.awt.Color.YELLOW, "%", 0, 100.0) { s -> ((s.activeIntensity)?.times(100)?.toInt() ?: 0) })

        wrapper = SwingWrapper<XYChart>(subcharts.map { it.chart })

    }

    private fun buildPhasePlan(profile: ReflowProfile): Pair<List<Date>, List<Double>> {
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
        val xDates = xSec.map { sec -> Date((sec * 1000.0).roundToLong()) }
        return xDates to yDeg
    }

    // Phase boundary times (sec since start): starts + final end
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

    // Add/update overlay series on the Temperature chart
    private fun drawPhasesOverlay(chart: XYChart) {
        val profile = backend.status().profile ?: return

        // --- Setpoint (plan) step series ---
        val (xPlan, yPlan) = buildPhasePlan(profile)
        val planName = "__setpoint_plan"
        if (chart.seriesMap.containsKey(planName)) {
            chart.updateXYSeries(planName, xPlan, yPlan, null)
        } else {
            val s = chart.addSeries(planName, xPlan, yPlan)
            s.xySeriesRenderStyle = XYSeries.XYSeriesRenderStyle.Line
            s.marker = org.knowm.xchart.style.markers.None()
            s.lineWidth = 2.0f
            s.lineColor = Color(214, 39, 40) // red/orange
            s.isShowInLegend = true
        }

        // --- Vertical phase boundaries ---
        // Clear old boundary series first
        val toRemove = chart.seriesMap.keys.filter { it.startsWith("__phase_cut_") }.toList()
        toRemove.forEach { chart.removeSeries(it) }

        val cutsSec = buildPhaseCutsSec(profile).distinct().sorted()
        val yMin = chart.styler.yAxisMin ?: 0.0
        val yMax = chart.styler.yAxisMax ?: 300.0

        cutsSec.forEachIndexed { i, tSec ->
            val d = Date((tSec * 1000.0).roundToLong())
            val s = chart.addSeries("__phase_cut_$i", listOf(d, d), listOf(yMin, yMax))
            s.xySeriesRenderStyle = XYSeries.XYSeriesRenderStyle.Line
            s.marker = org.knowm.xchart.style.markers.None()
            s.lineWidth = 1.0f
            s.lineColor = Color(150, 150, 150) // gray
            s.isShowInLegend = false
        }
    }

    fun show(parent: JComponent) : Boolean {

        val frame = wrapper.displayChartMatrix()
        frame.setLocationRelativeTo(parent)

        frame.addWindowListener(object : WindowAdapter() {

            override fun windowOpened(e: WindowEvent?) {
                super.windowOpened(e)
                frame.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
                frame.title = "Reflow Chart"
            }

            override fun windowClosed(e: WindowEvent?) {
                super.windowClosed(e)
                backend.onStatesChanged.remove(listener)
            }

        })

        update(backend.logs().states)
        backend.onStatesChanged.add(listener)

        return true

    }

    private val listener : (List<State>) -> Unit = { update(it) }

    private fun update(entries : List<State>){

        SwingUtilities.invokeLater {

            subcharts.forEachIndexed { index, subChart ->

                subChart.update(entries)
                try {
                    wrapper.repaintChart(index)
                } catch (e: Exception){}

            }

        }

    }

    class SubChart<T : Number>(
        title : String,
        private val color : java.awt.Color,
        axisTitle : String,
        private val defaultValue: T,
        private val max : Double,
        private val moreData: ((Boolean, XYChart) -> Unit)? = null,
        private val mapping: ((State) -> T)
    ) {

        fun secondsToDates(sec: DoubleArray, epochMs: Long = 0L): Array<Date> =
            Array(sec.size) { i -> Date(epochMs + (sec[i] * 1000.0).roundToLong()) }

        val chart: XYChart = XYChartBuilder()
                .title(title)
                .xAxisTitle("Time")
                .yAxisTitle(axisTitle)
                .width(600)
                .height(300)
                .build()

        private var xData : MutableList<Date>? = null
        private var yData : MutableList<T>? = null

        init {

            chart.styler.seriesColors = listOf(color).toTypedArray()
            chart.styler.setYAxisMin(0.0)
            chart.styler.setYAxisMax(max)
            chart.styler.setYAxisDecimalPattern("0")

            chart.styler.datePattern = "mm:ss"

        }

        public fun update(entries: List<State>){

            val firstData = xData == null

            if (firstData) {
                xData = mutableListOf()
                yData = mutableListOf()
            }

            if(entries.isNotEmpty()) {


                val xSec = entries.map { it.time.toDouble() / 1000.0f }.toDoubleArray()
                val x    = secondsToDates(xSec)

                xData?.clear()
                xData?.addAll(x)

                yData?.clear()
                yData?.addAll(entries.map { mapping.invoke(it) })

            } else {

                xData?.clear()
                xData?.add(Date())

                yData?.clear()
                yData?.add(defaultValue)

            }

            if (firstData) {

                val series = chart.addSeries("y", xData, yData)
                series.marker = org.knowm.xchart.style.markers.None()

                moreData?.invoke(true, chart)

            } else {

                chart.updateXYSeries("y", xData, yData, null)
                moreData?.invoke(false, chart)

            }

        }

    }

}