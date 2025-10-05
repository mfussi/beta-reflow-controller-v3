// File: src/main/java/com/tangentlines/reflowcontroller/client/BackendWithEvents.kt
package com.tangentlines.reflowcontroller.client

import com.tangentlines.reflowcontroller.reflow.profile.ReflowProfile
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class BackendWithEvents(initial: ControllerBackend, private val pollMs: Long = 500L) : ControllerBackend {
    private val current = AtomicReference(initial)

    val onStateChanged = Event<StatusDto>(onEdt = true)
    val onPhaseChanged = Event<Triple<String?, String?, Boolean?>?>(onEdt = true)

    @Volatile private var lastStatus: StatusDto? = null

    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "backend-events-poller").apply { isDaemon = true }
    }

    init {
        scheduler.scheduleAtFixedRate(::pollOnce, 0, pollMs, TimeUnit.MILLISECONDS)
    }

    fun swap(newBackend: ControllerBackend) {
        current.set(newBackend)
        scheduler.execute { pollOnce() }
    }

    fun shutdown() {
        scheduler.shutdownNow()
    }

    private fun pollOnce() {
        val impl = current.get()
        try {
            val status = impl.status()
            val prev = lastStatus
            lastStatus = status

            if (prev == null || status != prev) onStateChanged.emit(status)
            if (status.phase != prev?.phase) onPhaseChanged.emit(Triple(status.profile, status.phase, status.finished))
        } catch (_: Exception) { /* ignore */ }
    }

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
