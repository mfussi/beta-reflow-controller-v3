package com.tangentlines.reflowcontroller.log

import org.knowm.xchart.*
import org.knowm.xchart.style.markers.Marker
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.*
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.SwingUtilities

class ReflowChart() {

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
                StateLogger.onNewEntry.remove(newData)
            }

        })

        update()
        StateLogger.onNewEntry.add(newData)
        return true

    }

    private fun update(){

        val entries = StateLogger.getEntries()

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

        }

        public fun update(entries: List<State>){

            val firstData = xData == null

            if (firstData) {
                xData = mutableListOf()
                yData = mutableListOf()
            }

            if(entries.isNotEmpty()) {

                xData?.clear()
                xData?.addAll(entries.map { Date(it.time) })

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

    private val newData : (() -> Unit) = { update() }

}