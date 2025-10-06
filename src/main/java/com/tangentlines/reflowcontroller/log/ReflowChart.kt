package com.tangentlines.reflowcontroller.log

import com.tangentlines.reflowcontroller.client.BackendWithEvents
import com.tangentlines.reflowcontroller.client.ControllerBackend
import org.knowm.xchart.*
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

        subcharts.add(SubChart<Int>("Temperature", java.awt.Color.RED, "°C", 0, 300.0) { s -> s.temperature.toInt() })
        subcharts.add(SubChart<Int>("Intensity",java.awt.Color.GREEN, "%", 0, 100.0) { s -> (s.intensity * 100).toInt() })
        subcharts.add(SubChart<Int>("Target Temperature",java.awt.Color.BLUE, "°C", 0, 300.0) { s -> s.targetTemperature?.toInt() ?: 0 })
        subcharts.add(SubChart<Int>("Active Intensity",java.awt.Color.YELLOW, "%", 0, 100.0) { s -> ((s.activeIntensity)?.times(100)?.toInt() ?: 0) })

        wrapper = SwingWrapper<XYChart>(subcharts.map { it.chart })

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

    class SubChart<T : Number>(title : String, private val color : java.awt.Color, axisTitle : String, private val defaultValue: T, private val max : Double, private val mapping: ((State) -> T)) {

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

            } else {
                chart.updateXYSeries("y", xData, yData, null)
            }

        }

    }

}