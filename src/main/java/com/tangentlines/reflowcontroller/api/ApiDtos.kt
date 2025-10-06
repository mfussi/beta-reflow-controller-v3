// File: src/main/java/com/tangentlines/reflowcontroller/api/ApiDtos.kt
package com.tangentlines.reflowcontroller.api

import com.google.gson.annotations.SerializedName
import com.tangentlines.reflowcontroller.log.LogEntry
import com.tangentlines.reflowcontroller.log.State
import com.tangentlines.reflowcontroller.reflow.profile.ReflowProfile

// -------- Request DTOs --------
data class ConnectRequest(@SerializedName("port") val port: String)
data class ManualStartRequest(
    @SerializedName("temp") val temp: Float?,
    @SerializedName("intensity") val intensity: Float?
)

data class ManualSetRequest(@SerializedName("temp") val temp: Float, @SerializedName("intensity") val intensity: Float)

data class TargetRequest(@SerializedName("temp") val temp: Float, @SerializedName("intensity") val intensity: Float)

data class StartRequest(
    @SerializedName("profileName") val profileName: String?,
    @SerializedName("profile") val profile: ReflowProfile?,
    @SerializedName("clientName") val clientName: String? = null
)

data class ValidateProfileRequest(@SerializedName("profile") val profile: ReflowProfile)

data class SaveProfileRequest(
    @SerializedName("profile") val profile: ReflowProfile,
    @SerializedName("fileName") val fileName: String?
)

// -------- Response DTOs --------
data class StatusDto(
    @SerializedName("connected") val connected: Boolean,
    @SerializedName("running") val running: Boolean,
    @SerializedName("phase") val phase: String?,
    @SerializedName("mode") val mode: String,
    @SerializedName("temperature") val temperature: Float?,
    @SerializedName("targetTemperature") val targetTemperature: Float?,
    @SerializedName("intensity") val intensity: Float?,
    @SerializedName("activeIntensity") val activeIntensity: Float?,
    @SerializedName("timeAlive") val timeAlive: Long?,
    @SerializedName("timeSinceTempOver") val timeSinceTempOver: Long?,
    @SerializedName("timeSinceCommand") val timeSinceCommand: Long?,
    @SerializedName("controllerTimeAlive") val controllerTimeAlive: Long?,
    @SerializedName("profile") val profile: ReflowProfile?,
    @SerializedName("finished") val finished: Boolean?,
    @SerializedName("profileSource") val profileSource: String? = null,
    @SerializedName("profileClient") val profileClient: String? = null,
    @SerializedName("port") val port: String? = null
)

data class PortsResponse(@SerializedName("ports") val ports: List<String>)

data class ProfilesResponse(@SerializedName("profiles") val profiles: List<ReflowProfile>)

data class MessagesResponse(@SerializedName("messages") val messages: List<LogEntry>)

data class StatesResponse(@SerializedName("states") val states: List<State>)

data class LogsCombinedResponse(
    @SerializedName("messages") val messages: List<LogEntry>,
    @SerializedName("states") val states: List<State>
)

data class ConnectResponse(
    @SerializedName("ok") val ok: Boolean,
    @SerializedName("connected") val connected: Boolean,
    @SerializedName("port") val port: String? = null
)

data class DisconnectResponse(
    @SerializedName("ok") val ok: Boolean,
    @SerializedName("connected") val connected: Boolean,
    @SerializedName("port") val port: String? = null
)

data class TargetResponse(
    @SerializedName("ok") val ok: Boolean,
    @SerializedName("targetTemperature") val targetTemperature: Float?,
    @SerializedName("intensity") val intensity: Float?
)

data class ManualStartResponse(
    @SerializedName("mode") val mode: String,
    @SerializedName("targetTemperature") val targetTemperature: Float?,
    @SerializedName("intensity") val intensity: Float?
)

data class ManualSetResponse(
    @SerializedName("ok") val ok: Boolean,
    @SerializedName("targetTemperature") val targetTemperature: Float?,
    @SerializedName("intensity") val intensity: Float?
)

data class ManualStopResponse(@SerializedName("mode") val mode: String)

data class StartResponse(
    @SerializedName("ok") val ok: Boolean,
    @SerializedName("running") val running: Boolean,
    @SerializedName("phase") val phase: String?,
    @SerializedName("mode") val mode: String,
    @SerializedName("profileName") val profileName: String
)

data class ValidateProfileResponse(
    @SerializedName("valid") val valid: Boolean,
    @SerializedName("issues") val issues: List<String>
)

data class SaveProfileResponse(@SerializedName("saved") val saved: Boolean, @SerializedName("file") val file: String)

data class StopResponse(@SerializedName("ok") val ok: Boolean, @SerializedName("running") val running: Boolean)

data class AckResponse(@SerializedName("ok") val ok: Boolean)
