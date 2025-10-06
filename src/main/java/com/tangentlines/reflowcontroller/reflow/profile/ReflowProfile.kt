package com.tangentlines.reflowcontroller.reflow.profile

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.File
import java.io.FileFilter
import java.io.FileReader

// Two explicit phase types
enum class PhaseType {
    @SerializedName("heating") HEATING,
    @SerializedName("reflow")  REFLOW
}

// Profile with dynamic, ordered phases
data class ReflowProfile(
    @SerializedName("name")
    val name: String,

    @SerializedName("description")
    val description: String?,

    @SerializedName("phases")
    val phases: List<Phase>
) {

    fun getNameForPhase(pos: Int): String =
        if (pos !in phases.indices) "finished"
        else phases[pos].name.ifBlank { "phase-${pos + 1}" }

    override fun toString(): String = name
}

// Single phase definition (typed), with optional feed-forward knobs
data class Phase(

    @SerializedName("name")
    val name: String,

    @SerializedName("type")
    val type: PhaseType = PhaseType.HEATING,

    // Common
    @SerializedName("target_temperature")
    val targetTemperature: Float,

    // HEATING: seconds to reach target
    // REFLOW : unused (keep 0) — use holdFor instead
    @SerializedName("time")
    val time: Int = 0,

    // REFLOW: seconds to stay above target (threshold)
    // HEATING: usually 0
    @SerializedName("hold_for")
    val holdFor: Int = 0,

    // Feed-forward start; refined at runtime and suggested back via logs
    @SerializedName("initial_intensity")
    val initialIntensity: Float? = null,

    // Optional: cap actual slope for HEATING (°C/s)
    @SerializedName("max_slope")
    val maxSlope: Float? = null

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
