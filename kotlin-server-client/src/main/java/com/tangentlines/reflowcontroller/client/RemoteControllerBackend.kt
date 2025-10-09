// File: src/main/java/com/tangentlines/reflowcontroller/client/RemoteControllerBackend.kt
package com.tangentlines.reflowcontroller.client

import com.tangentlines.reflowcontroller.api.*
import com.tangentlines.reflowcontroller.reflow.profile.ReflowProfile

class RemoteControllerBackend(host: String, port: Int, clientKey: String? = null) : ControllerBackend {

    private val resolvedKey = clientKey
        ?: System.getProperty("client.key")
        ?: System.getenv("REFLOW_CLIENT_KEY")
        ?: System.getenv("CLIENT_KEY")

    private val http = HttpJson(
        "http://$host:$port",
        defaultHeaders = mapOf<String, String>(
            resolvedKey?.let { Pair("Authorization", HttpJson.basicAuthHeader(password = it)) } ?: ("" to ""),
            System.getProperty("user.name")?.let { Pair("X-Client-Name", it) } ?: ("" to "")
        ).filter { !it.value.isEmpty() }
    )

    override fun availablePorts(): List<String> =
        http.get<PortsResponse>("/api/ports").ports

    override fun connect(portName: String): Boolean =
        http.post<ConnectResponse>("/api/connect", ConnectRequest(portName)).ok

    override fun disconnect(): Boolean =
        http.post<DisconnectResponse>("/api/disconnect", null).ok

    override fun set(temp: Float, intensity: Float): Boolean =
        http.post<ManualSetResponse>("/api/set", SetRequest(temp, intensity)).ok

    override fun startProfileByName(profileName: String): Boolean =
        http.post<StartResponse>("/api/start", StartRequest(profileName, null)).ok

    override fun startProfileInline(profile: ReflowProfile): Boolean =
        http.post<StartResponse>("/api/start", StartRequest(null, profile)).ok

    override fun startManual(temp: Float?, intensity: Float?): Boolean =
        http.post<StartResponse>("/api/start", StartRequest(null, null, null, temp, intensity)).ok

    override fun stop(): Boolean =
        http.post<StopResponse>("/api/stop", null).ok

    override fun setTargetTemperature(intensity: Float, temp: Float): Boolean =
        http.post<TargetResponse>("/api/target", TargetRequest(temp, intensity)).ok

    override fun status(): StatusDto {
        val s = http.get<com.tangentlines.reflowcontroller.api.StatusDto>("/api/status")
        return StatusDto(
            connected = s.connected,
            running = s.running,
            phase = s.phase?.let { s.profile?.phases?.getOrNull(it) },
            mode = s.mode,
            temperature = s.temperature,
            targetTemperature = s.targetTemperature,
            intensity = s.intensity,
            activeIntensity = s.activeIntensity,
            timeAlive = s.timeAlive,
            timeSinceTempOver = s.timeSinceTempOver,
            timeSinceCommand = s.timeSinceCommand,
            controllerTimeAlive = s.controllerTimeAlive,
            profile = s.profile,
            finished = s.finished,
            profileSource = s.profileSource,
            profileClient = s.profileClient,
            port = s.port,
            nextPhaseIn = s.nextPhaseIn,
            phaseTime = s.phaseTime
        )
    }

    override fun logs(): LogsDto {
        val dto = http.get<LogsCombinedResponse>("/api/logs")
        return LogsDto(messages = dto.messages, states = dto.states)
    }

    override fun clearLogs(): Boolean =
        http.delete<AckResponse>("/api/logs").ok

    fun listProfilesRemote(): List<ReflowProfile> =
        http.get<ProfilesResponse>("/api/profiles").profiles

}
