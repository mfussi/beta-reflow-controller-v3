// File: src/main/java/com/tangentlines/reflowcontroller/client/BackendWithEvents.kt
package com.tangentlines.reflowcontroller.client

import com.google.gson.JsonObject
import com.tangentlines.reflowcontroller.log.LogEntry
import com.tangentlines.reflowcontroller.log.State
import com.tangentlines.reflowcontroller.reflow.profile.ReflowProfile
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

/**
 * Wraps any ControllerBackend and provides polling-based UI events:
 *  - onStateChanged(StatusDto)
 *  - onPhaseChanged(String?)
 *  - onLogsChanged(List<String>)            // NEW
 *  - onStatesChanged(List<JsonObject>)      // NEW
 *
 * Swap underlying backends (local/remote) via swap().
 */
class BackendWithEvents(
    initial: ControllerBackend,
    private val pollMs: Long = 500L
) : ControllerBackend {

    private val current = AtomicReference(initial)

    // existing events
    val onStateChanged = Event<StatusDto>(onEdt = true)
    val onPhaseChanged = Event<Triple<ReflowProfile?, String?, Boolean?>?>(onEdt = true)

    // logs & states events
    val onLogsChanged = Event<List<LogEntry>>(onEdt = true)
    val onStatesChanged = Event<List<State>>(onEdt = true)

    @Volatile private var lastStatus: StatusDto? = null

    // NEW: fingerprints to avoid spamming events with identical data
    @Volatile private var lastLogsFp: String? = null
    @Volatile private var lastStatesFp: String? = null

    // poll logs less frequently than status; default ~2s
    private val logsEveryTicks: Int = max(1, (2000L / pollMs).toInt())
    @Volatile private var tick: Int = 0

    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "backend-events-poller").apply { isDaemon = true }
    }

    init {
        scheduler.scheduleAtFixedRate(::pollOnce, 0, pollMs, TimeUnit.MILLISECONDS)
    }

    fun swap(newBackend: ControllerBackend) {
        current.set(newBackend)
        // reset fingerprints so next poll emits fresh data
        lastStatus = null
        lastLogsFp = null
        lastStatesFp = null
        tick = 0
        // Force an immediate refresh of both status and logs/states
        scheduler.execute {
            pollStatus()
            pollLogs()
        }
    }

    fun shutdown() {
        scheduler.shutdownNow()
    }

    fun currentBackend(): ControllerBackend = current.get()

    // --- polling ---
    private fun pollOnce() {
        pollStatus()
        tick += 1
        if (tick % logsEveryTicks == 0) {
            pollLogs()
        }
    }

    private fun pollStatus() {
        val impl = current.get()
        try {
            val status = impl.status()
            val prev = lastStatus
            lastStatus = status

            if (prev == null || status != prev) {
                onStateChanged.emit(status)
            }
            if (status.phase != prev?.phase) {
                onPhaseChanged.emit(Triple(status.profile, status.phase, status.finished))
            }
        } catch (_: Exception) {
            // swallow poll errors; UI keeps last known state
        }
    }

    private fun pollLogs() {
        val impl = current.get()
        try {
            val logs: LogsDto = impl.logs()

            val logsFp = fingerprintLogs(logs.messages)
            if (logsFp != lastLogsFp) {
                lastLogsFp = logsFp
                onLogsChanged.emit(logs.messages)
            }

            val statesFp = fingerprintStates(logs.states)
            if (statesFp != lastStatesFp) {
                lastStatesFp = statesFp
                onStatesChanged.emit(logs.states)
            }
        } catch (_: Exception) {
            // ignore transient failures (remote might be busy)
        }
    }

    private fun fingerprintLogs(messages: List<LogEntry>): String =
        if (messages.isEmpty()) "0"
        else "${messages.size}:${messages.last().hashCode()}"

    private fun fingerprintStates(states: List<State>): String =
        if (states.isEmpty()) "0"
        else "${states.size}:${states.last().toString().hashCode()}"

    // ---- ControllerBackend delegation ----
    override fun availablePorts() = current.get().availablePorts()
    override fun connect(portName: String) = current.get().connect(portName)
    override fun disconnect() = current.get().disconnect()

    override fun manualStart(temp: Float?, intensity: Float?) = current.get().manualStart(temp, intensity)
    override fun manualSet(temp: Float, intensity: Float) = current.get().manualSet(temp, intensity)
    override fun manualStop() = current.get().manualStop()

    override fun startProfileByName(profileName: String) = current.get().startProfileByName(profileName)
    override fun startProfileInline(profile: ReflowProfile) = current.get().startProfileInline(profile)
    override fun stop() = current.get().stop()

    override fun setTargetTemperature(intensity: Float, temp: Float) = current.get().setTargetTemperature(intensity, temp)

    override fun status() = current.get().status()
    override fun logs() = current.get().logs()
    override fun clearLogs() = current.get().clearLogs()
}
