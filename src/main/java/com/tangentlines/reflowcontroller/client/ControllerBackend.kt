// File: src/main/java/com/tangentlines/reflowcontroller/client/ControllerBackend.kt
package com.tangentlines.reflowcontroller.client

import com.google.gson.JsonObject
import com.tangentlines.reflowcontroller.log.LogEntry
import com.tangentlines.reflowcontroller.log.State
import com.tangentlines.reflowcontroller.reflow.profile.ReflowProfile

/**
 * Thin abstraction so the Swing UI can talk to either the local ApplicationController
 * or a remote HTTP API without caring which one is behind it.
 */
interface ControllerBackend {
    // Connection
    fun availablePorts(): List<String>
    fun connect(portName: String): Boolean
    fun disconnect(): Boolean

    // Manual
    fun manualStart(temp: Float? = null, intensity: Float? = null): Boolean
    fun manualSet(temp: Float, intensity: Float): Boolean
    fun manualStop(): Boolean

    // Profile runs
    fun startProfileByName(profileName: String): Boolean
    fun startProfileInline(profile: ReflowProfile): Boolean
    fun stop(): Boolean

    // Direct control
    fun setTargetTemperature(intensity: Float, temp: Float): Boolean

    // Status & logs
    fun status(): StatusDto
    fun logs(): LogsDto
    fun clearLogs(): Boolean
}

data class StatusDto(
    val connected: Boolean? = null,
    val running: Boolean? = null,
    val phase: String? = null,
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
    val profileSource: String?,
    val profileClient: String?
)

data class LogsDto(
    val messages: List<LogEntry> = emptyList(),
    val states: List<State> = emptyList()
)
