package com.tangentlines.reflowclient.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PhaseType {
    @SerialName("heating") HEATING,
    @SerialName("reflow")  REFLOW,
    @SerialName("cooling") COOLING
}

@Serializable
data class Phase(
    @SerialName("name") val name: String,
    @SerialName("type") val type: PhaseType,
    @SerialName("target_temperature") val targetTemperature: Float,
    @SerialName("time") val time: Int,
    @SerialName("hold_for") val holdFor: Int,
    @SerialName("initial_intensity") val initialIntensity: Float?,
    @SerialName("max_slope") val maxSlope: Float?,
    @SerialName("max_temperature") val maxTemperature: Float?
)

@Serializable
data class ReflowProfile(
    val name: String,
    val description: String? = null,
    val phases: List<Phase> = listOf()
)

@Serializable data class StatusDto(
    val connected: Boolean? = null,
    val running: Boolean? = null,
    val phase: Int? = null,
    val mode: String? = null,
    val temperature: Float? = null,
    val targetTemperature: Float? = null,
    val intensity: Float? = null,
    val activeIntensity: Float? = null,
    val timeAlive: Long? = null,
    val timeSinceTempOver: Long? = null,
    val timeSinceCommand: Long? = null,
    val controllerTimeAlive: Long? = null,
    val profile: ReflowProfile? = null,
    val finished: Boolean? = null,
    val port: String? = null,
    val phaseTime: Long? = null,
    val nextPhaseIn: Long? = null
)

@Serializable data class PortsResponse(val ports: List<String> = emptyList())
@Serializable data class ConnectRequest(val port: String)
@Serializable data class ConnectResponse(val ok: Boolean, val connected: Boolean? = null)
@Serializable data class AckResponse(val ok: Boolean)
@Serializable data class StartRequest(val profileName: String? = null, val profile: ReflowProfile? = null, val temp: Float? = null, val intensity: Float? = null)
@Serializable data class StartResponse(val ok: Boolean, val running: Boolean? = null, val phase: Int? = null, val mode: String? = null, val profileName: String? = null)
@Serializable data class ManualSetRequest(val temp: Float, val intensity: Float)
@Serializable data class ManualSetResponse(val ok: Boolean, val targetTemperature: Float? = null, val intensity: Float? = null)
@Serializable data class TargetRequest(val temp: Float, val intensity: Float)
@Serializable data class TargetResponse(val ok: Boolean, val targetTemperature: Float? = null, val intensity: Float? = null)
@Serializable data class ProfilesResponse(val profiles: List<ReflowProfile> = emptyList())
@Serializable data class LogMessage(val time: Long, val message: String)
@Serializable data class MessagesResponse(val messages: List<LogMessage> = emptyList())
@Serializable data class State(val time: Long, val temperature: Float, val targetTemperature: Float? = null, val intensity: Float = 0f, val activeIntensity: Float? = null)
@Serializable data class StatesResponse(val states: List<State> = emptyList())
@Serializable data class DiscoveryReply(val name: String, val host: String, val port: Int)
@Serializable data class ValidateProfileRequest(val profile: ReflowProfile)
@Serializable data class ValidateProfileResponse(val valid: Boolean, val issues: List<String> = emptyList())
@Serializable data class DeleteProfileResponse(val success: Boolean, val deleted: String)

@Serializable data class SaveProfileRequest(val profile: ReflowProfile, val fileName: String? = null)
@Serializable data class SaveProfileResponse(val saved: Boolean, val path: String? = null)
