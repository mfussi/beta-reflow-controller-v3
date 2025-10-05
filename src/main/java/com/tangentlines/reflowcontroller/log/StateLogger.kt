package com.tangentlines.reflowcontroller.log

import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import com.tangentlines.reflowcontroller.CONFIGURATION_USE_FAKE_STATE_DATA
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.*
import javax.swing.SwingUtilities

private val STEP = 2000L

object StateLogger {

    val onNewEntry : MutableList<(() -> Unit)> = mutableListOf()

    private var lastState = 0L
    private val states = mutableListOf<State>()

    init {

        if(CONFIGURATION_USE_FAKE_STATE_DATA) {

            val count = 100
            val time = System.currentTimeMillis() - (count * 1000)
            for (i in 0 until count) {
                states.add(State(time + i * 1000L, "manual", (Math.random() * 200.0f).toFloat(), 50.0f, 240.0f, 1.0f))
            }

            Timer().schedule(object : TimerTask() {

                override fun run() {
                    add(State(System.currentTimeMillis(), "manual", (Math.random() * 200.0f).toFloat(), (Math.random() * 50.0f).toFloat(), (Math.random() * 240.0f).toFloat(), (Math.random() * 1.0f).toFloat()))
                }

            }, STEP, STEP)

        }

    }

    fun add(state : State){

        val now = System.currentTimeMillis()
        if(lastState / STEP != now / STEP){
            states.add(state)
            lastState = now
        }

        SwingUtilities.invokeLater { onNewEntry.forEach { it.invoke() } }

    }

    fun clear() : Boolean {

        states.clear()
        lastState = 0L

        SwingUtilities.invokeLater { onNewEntry.forEach { it.invoke() } }
        return true

    }

    fun export() : Boolean {

        val title = "export/temp-${System.currentTimeMillis()}"
        val data = Export(states.toList().sortedBy { it.time })

        try {

            val dir = File("export")
            if(!dir.exists()){
                dir.mkdirs()
            }

            FileWriter(File("$title.json")).use {
                it.write(GsonBuilder().setPrettyPrinting().create().toJson(data))
            }

            CSVPrinter(FileWriter("$title.csv"), CSVFormat.EXCEL).use { printer ->
                printer.printRecord("time", "phase", "temperature", "activeIntensity", "targetTemperature", "intensity", "timems")
                data.data.forEach { printer.printRecord(it.timeStr, it.phase, it.temperature, it.activeIntensity, it.targetTemperature, it.intensity, it.time.toString()) }
            }

        } catch (ex: IOException) {
            ex.printStackTrace()
            return false
        }

        return true

    }

    fun getEntries(): List<State> {
        return this.states.sortedBy { it.time }.toList()
    }

}

class Export(@SerializedName("data") val data : List<State>)

class State(
    @SerializedName("time") val time : Long,
    @SerializedName("phase") val phase : String,
    @SerializedName("temperature") val temperature : Float,
    @SerializedName("activeIntensity") val activeIntensity : Float?,
    @SerializedName("targetTemperature") val targetTemperature : Float?,
    @SerializedName("intensity") val intensity : Float
) {

    val timeStr = DateTimeFormat.forPattern("HH:mm:ss").print(time)

}