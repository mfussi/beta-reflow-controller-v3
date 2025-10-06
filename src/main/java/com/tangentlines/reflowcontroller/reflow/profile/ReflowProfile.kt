package com.tangentlines.reflowcontroller.reflow.profile

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import java.io.File
import java.io.FileFilter
import java.io.FileReader


data class ReflowProfile(

    @SerializedName("name")
    val name: String,

    @SerializedName("phases")
    val phases: List<Phase>

) {

    fun getNameForPhase(pos: Int): String {
        if (pos < 0) return "undefined"
        if (pos >= phases.size) return "finished"
        return phases[pos].name.ifBlank { "phase-${pos + 1}" }
    }

    override fun toString(): String = name
}

data class Phase(
    @SerializedName("name")
    val name: String,                             // e.g. "preheat", "soak", "reflow", "cool", ...

    @SerializedName("target_temperature")
    val targetTemperature: Float,                 // 0..300

    @SerializedName("intensity")
    val intensity: Float,                         // 0.0..1.0

    @SerializedName("hold_for")
    val holdFor: Int,                             // seconds

    @SerializedName("time")
    val time: Int                                 // seconds (0 = no limit)
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

/**
 * Loads profiles from ./profiles.
 * Backward compatible: if a file uses legacy fields (preheat/soak/reflow),
 * we convert them into the dynamic schema.
 */
fun loadProfiles(): List<ReflowProfile> = try {
    val gson = Gson()
    val parent = File("profiles").apply { if (!exists()) mkdirs() }

    parent.listFiles(FileFilter { it.isFile && it.name.endsWith(".json", ignoreCase = true) })
        ?.mapNotNull { f ->
            try {
                FileReader(f).use { r ->
                    val text = r.readText()
                    val root = JsonParser.parseString(text).asJsonObject
                    parseProfileJson(root, gson)
                }
            } catch (e: Exception) {
                e.printStackTrace(); null
            }
        } ?: emptyList()
} catch (e: Exception) {
    e.printStackTrace(); emptyList()
}

private fun parseProfileJson(root: JsonObject, gson: Gson): ReflowProfile {
    // NEW schema
    if (root.has("phases")) {
        return gson.fromJson(root, ReflowProfile::class.java)
    }
    // LEGACY schema -> adapt to dynamic
    val name = root.get("name")?.asString ?: "Unnamed"
    val phases = mutableListOf<Phase>()
    fun legacyPhase(key: String): Phase? =
        if (root.has(key) && root.get(key).isJsonObject) {
            val o = root.getAsJsonObject(key)
            Phase(
                name = key,
                targetTemperature = o.get("target_temperature")?.asFloat ?: 0f,
                intensity = o.get("intensity")?.asFloat ?: 0f,
                holdFor = o.get("hold_for")?.asInt ?: 0,
                time = o.get("time")?.asInt ?: 0
            )
        } else null

    listOf("preheat", "soak", "reflow").mapNotNullTo(phases) { legacyPhase(it) }
    return ReflowProfile(name = name, phases = phases)
}