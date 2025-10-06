package com.tangentlines.reflowcontroller.reflow.profile

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.File
import java.io.FileFilter
import java.io.FileReader

data class ReflowProfile(

        @SerializedName("name")
        val name: String,

        @SerializedName("preheat")
        val preheat: Phase,

        @SerializedName("soak")
        val soak: Phase,

        @SerializedName("reflow")
        val reflow: Phase

) {

    fun getPhases() : List<Phase> {
        return listOf(preheat, soak, reflow)
    }

    fun getNameForPhase(pos : Int) : String {

        if(pos >= getPhases().size){
            return "finished"
        }

        return when(pos){
            0 -> "preheat"
            1 -> "soak"
            2 -> "reflow"
            else -> "undefined"
        }

    }

    override fun toString(): String {
        return name
    }

}

data class Phase(

        @SerializedName("target_temperature")
        val targetTemperature: Float,                   // target temperature (0  - 300)

        @SerializedName("intensity")
        val intensity: Float,                           // heating strength (0.0 - 1.0)

        @SerializedName("hold_for")
        val holdFor: Int,                               // seconds the target temperature needs to be held

        @SerializedName("time")
        val time: Int                                   // length of the phase (0 - no limit)

) {

    fun phaseType() : PhaseType {

        return if(holdFor > 0) {
            PhaseType.HOLD
        } else if(time > 0) {
            PhaseType.TIME
        } else {
            PhaseType.UNTIL_TEMP
        }

    }

    enum class PhaseType {
        HOLD, TIME, UNTIL_TEMP
    }

}

fun loadProfiles() : List<ReflowProfile> {

    try {

        val gson = Gson()
        val parentFile = File("profiles")
        if(!parentFile.exists()) parentFile.mkdirs()

        return parentFile.listFiles(FileFilter { it.absolutePath.endsWith(".json") })?.map { gson.fromJson(FileReader(it), ReflowProfile::class.java) } ?: listOf()

    } catch (e : Exception){
        e.printStackTrace()
        return listOf()
    }

}