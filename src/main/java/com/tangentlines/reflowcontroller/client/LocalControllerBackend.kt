// File: src/main/java/com/tangentlines/reflowcontroller/client/LocalControllerBackend.kt
package com.tangentlines.reflowcontroller.client

import com.tangentlines.reflowcontroller.ApplicationController
import com.tangentlines.reflowcontroller.log.Logger
import com.tangentlines.reflowcontroller.log.StateLogger
import com.tangentlines.reflowcontroller.reflow.profile.ReflowProfile

class LocalControllerBackend(private val controller: ApplicationController) : ControllerBackend {
    override fun availablePorts(): List<String> = controller.availablePorts()

    override fun connect(portName: String): Boolean = controller.connect(portName)

    override fun disconnect(): Boolean = controller.disconnect()

    override fun manualStart(temp: Float?, intensity: Float?): Boolean {
        try { controller::class.java.getMethod("startManual").invoke(controller) } catch (_: Exception) { /* ignore */ }
        if (temp != null && intensity != null) controller.setTargetTemperature(intensity, temp)
        return true
    }

    override fun manualSet(temp: Float, intensity: Float): Boolean =
        controller.setTargetTemperature(intensity, temp)

    override fun manualStop(): Boolean {
        try { controller::class.java.getMethod("stopManual").invoke(controller) } catch (_: Exception) { /* ignore */ }
        return true
    }

    override fun startProfileByName(profileName: String): Boolean {
        val profile = com.tangentlines.reflowcontroller.reflow.profile.loadProfiles().firstOrNull { it.name == profileName }
            ?: return false
        return controller.start(profile)
    }

    override fun startProfileInline(profile: ReflowProfile): Boolean = controller.start(profile)

    override fun stop(): Boolean = controller.stop()

    override fun setTargetTemperature(intensity: Float, temp: Float): Boolean =
        controller.setTargetTemperature(intensity, temp)

    override fun status(): StatusDto = StatusDto(
        connected = controller.isConnected(),
        running = controller.isRunning(),
        phase = controller.getPhase(),
        mode = try {
            val isManual = try { controller::class.java.getMethod("isManual").invoke(controller) as? Boolean } catch (_: Exception) { null }
                ?: try { controller::class.java.getMethod("isManualMode").invoke(controller) as? Boolean } catch (_: Exception) { null }
            if (isManual == true) "manual" else if (controller.isRunning()) "profile" else "idle"
        } catch (_: Exception) { if (controller.isRunning()) "profile" else "idle" },
        temperature = controller.getTemperature(),
        targetTemperature = controller.getTargetTemperature(),
        intensity = controller.getIntensity(),
        activeIntensity = controller.getActiveIntensity(),
        timeAlive = controller.getTime(),
        timeSinceTempOver = controller.getTimeSinceTempOver(),
        timeSinceCommand = controller.getTimeSinceCommand(),
        controllerTimeAlive = controller.getControllerTimeAlive(),
        finished = controller.isFinished(),
        profile = controller.getProfile(),
        profileSource = "local",
        profileClient = "local"
    )

    override fun logs(): LogsDto = LogsDto(
        messages = Logger.getMessages(),
        states = StateLogger.getEntries()
    )

    override fun clearLogs(): Boolean = try {
        controller.clearLogs()
    } catch (_: Exception) { false }
}
