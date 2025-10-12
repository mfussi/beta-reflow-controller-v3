package com.tangentlines.reflowclient.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EditablePhase(
    @SerialName("name") val name: String = "",
    @SerialName("type") val type: PhaseType = PhaseType.HEATING,

    // Common
    @SerialName("target_temperature") val targetTemperature: Float = 0f, // REFLOW=threshold
    @SerialName("time") val time: Int = 0,        // HEATING goal time, COOLING duration; REFLOW often 0
    @SerialName("hold_for") val holdFor: Int = 0, // REFLOW hold seconds; others typically 0

    // Feed-forward
    @SerialName("initial_intensity") val initialIntensity: Float? = null,

    // HEATING optional: slope limit °C/s
    @SerialName("max_slope") val maxSlope: Float? = null,

    // REFLOW: cap temperature
    @SerialName("max_temperature") val maxTemperature: Float? = null,

    @SerialName("min_temperature") val minTemperature: Float? = null

)

@Serializable
data class EditableProfile(
    @SerialName("name") val name: String = "",
    @SerialName("description") val description: String? = null,
    @SerialName("liquidus_temperature") val liquidusTemperature: Float? = null,
    @SerialName("phases") val phases: List<EditablePhase> = emptyList()
)

fun EditableProfile.toWire(): ReflowProfile {
    // Adjust field names if your EditablePhase differs; this matches your shared model
    return ReflowProfile(
        name = name.trim(),
        description = description?.trim(),
        liquidusTemperature = liquidusTemperature,
        phases = phases.map { ep ->
            Phase(
                name = ep.name.trim(),
                type = ep.type,
                targetTemperature = ep.targetTemperature,
                time = ep.time,
                holdFor = ep.holdFor,
                initialIntensity = ep.initialIntensity,
                maxSlope = ep.maxSlope,
                maxTemperature = ep.maxTemperature,
                minTemperature = ep.minTemperature
            )
        }
    )
}