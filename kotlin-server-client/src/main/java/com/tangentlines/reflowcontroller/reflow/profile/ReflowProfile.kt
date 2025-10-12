package com.tangentlines.reflowcontroller.reflow.profile

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.File
import java.io.FileFilter
import java.io.FileReader

// Two explicit phase types
enum class PhaseType {
    @SerializedName("heating") HEATING,
    @SerializedName("reflow")  REFLOW,
    @SerializedName("cooling") COOLING
}

// Profile with dynamic, ordered phases
data class ReflowProfile(
    @SerializedName("name")
    val name: String,

    @SerializedName("description")
    val description: String?,

    @SerializedName("liquidus_temperature")
    val liquidusTemperature: Float?,

    @SerializedName("phases")
    val phases: List<Phase>
) {

    fun getNameForPhase(pos: Int): String =
        if (pos !in phases.indices) "finished"
        else phases[pos].name.ifBlank { "phase-${pos + 1}" }

    override fun toString(): String = name
}

/**
 * Phase types:
 *  - HEATING: reach target_temperature within `time` seconds (time is a goal, not auto-advance).
 *  - REFLOW : hold above target_temperature (threshold) for `hold_for` seconds; do not exceed `max_temperature`.
 *  - COOLING: heater OFF for `time` seconds (no active target).
 */
data class Phase(
    @SerializedName("name") val name: String,
    @SerializedName("type") val type: PhaseType = PhaseType.HEATING,

    // Common
    @SerializedName("target_temperature") val targetTemperature: Float, // REFLOW=threshold
    @SerializedName("time") val time: Int = 0,        // HEATING goal time, COOLING duration; REFLOW usually 0
    @SerializedName("hold_for") val holdFor: Int = 0, // REFLOW hold seconds; others typically 0

    // Feed-forward
    @SerializedName("initial_intensity") val initialIntensity: Float? = null,

    // HEATING optional: slope limit °C/s
    @SerializedName("max_slope") val maxSlope: Float? = null,

    // REFLOW: cap temperature
    @SerializedName("max_temperature") val maxTemperature: Float? = null,
    @SerializedName("min_temperature") val minTemperature: Float? = null
)

/** Load all profiles from ./profiles (new schema only). */
fun loadProfiles(): List<ReflowProfile> = try {
    val dir = File("profiles").apply { if (!exists()) mkdirs() }
    val gson = Gson()
    dir.listFiles(FileFilter { it.isFile && it.name.endsWith(".json", ignoreCase = true) })
        ?.mapNotNull { f ->
            try {
                FileReader(f).use { r -> gson.fromJson(r, ReflowProfile::class.java) }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        } ?: emptyList()
} catch (e: Exception) {
    e.printStackTrace()
    emptyList()
}
