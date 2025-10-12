package com.tangentlines.reflowcontroller.reflow

import com.tangentlines.reflowcontroller.log.Logger
import com.tangentlines.reflowcontroller.reflow.device.BetaLayoutDevice
import com.tangentlines.reflowcontroller.reflow.device.Device
import com.tangentlines.reflowcontroller.reflow.device.FakeDevice
import com.tangentlines.reflowcontroller.reflow.executor.ExecOutput
import com.tangentlines.reflowcontroller.reflow.executor.ManualExecOutput
import com.tangentlines.reflowcontroller.reflow.executor.ManualExecutor
import com.tangentlines.reflowcontroller.reflow.executor.ProfileExecutor
import com.tangentlines.reflowcontroller.reflow.executor.planner.ProfileContext
import com.tangentlines.reflowcontroller.reflow.executor.planner.SafetyConfig
import com.tangentlines.reflowcontroller.reflow.profile.Phase
import com.tangentlines.reflowcontroller.reflow.profile.PhaseType
import com.tangentlines.reflowcontroller.reflow.profile.ReflowProfile
import java.util.*
import kotlin.math.max

private const val UPDATE_INTERVAL      = 500L   // ms
private const val MIN_PULSE_DWELL_MS   = 2200L  // min time between pulse changes

class ReflowController(port: String) {

    // ---- Device binding
    private val device: Device = when (port) {
        FAKE_IDENTIFIER -> FakeDevice(FAKE_IDENTIFIER)
        else            -> BetaLayoutDevice(port)
    }

    // ---- Executors
    private var manualExec: ManualExecutor? = null
    private var profileExec: ProfileExecutor? = null

    // ---- Phase/runtime bookkeeping
    private var currentPhaseIdx: Int = -1
    private var phaseStartAtMs: Long = 0L

    private var lastPulseSet: Float = 0f
    private var lastPulseAt: Long = 0L
    private var lastTickMs: Long = 0L

    // ---- Exported status fields (mirrors)
    private var reflowProfile: ReflowProfile? = null
    private var currentTemperature: Float = -1f

    private var controllerTimeAlive: Long? = null
    private var connectTime: Long? = null
    private var startTime: Long? = null
    private var stopTime: Long? = null

    private var intensityOut: Float = 0f                   // last commanded 0..1
    private var targetTemperatureC: Float? = null          // for UI
    private var lastCommandAtMs: Long? = null

    // ---- Service / scheduling
    private var started: Boolean = false
    private var timer: Timer? = null
    private var updateTask: UpdateTask? = null

    // ---- Callbacks
    var onTempChanged: (() -> Unit)? = null
    var onNewPhase: ((ReflowProfile, Phase?, Boolean) -> Unit)? = null

    private var aboveLiquidusSinceMs: Long? = null
    private var timeAboveLiquidusMs: Long = 0L

    // ---- Logging helpers
    private fun f1(x: Float) = String.format(Locale.US, "%.1f", x)
    private fun logMsg(s: String) { try { Logger.addMessage(s) } catch (_: Exception) {} }

    private data class TempSample(val ms: Long, val c: Float)
    private val slopeWindowMs = 30_000L
    private val tempWindow = ArrayDeque<TempSample>()
    private var slopeCPerS: Float? = null

    init {
        device.addOnTemperatureChanged {
            currentTemperature = device.getTemperature()
            onTempChanged?.invoke()
        }
    }

    // -------------------------------------------------------------------------
    // Public gates
    // -------------------------------------------------------------------------

    fun connect(): Boolean {
        val ok = device.connect()
        if (ok) connectTime = System.currentTimeMillis()
        return ok
    }

    fun disconnect(): Boolean = device.disconnect()
    fun isConnected(): Boolean = device.isConnected()

    /** Set profile (null = manual mode). Resets phase machine to “not started”. */
    fun setProfile(profile: ReflowProfile?): Boolean {
        reflowProfile = profile
        currentPhaseIdx = -1
        return true
    }

    /** Manual mode input; only used when profile is null. */
    fun setTargetTemperature(intensity: Float, temperature: Float): Boolean {
        targetTemperatureC = temperature
        intensityOut = intensity.coerceIn(0f, 1f)
        lastCommandAtMs = System.currentTimeMillis()
        return true
    }

    // -------------------------------------------------------------------------
    // Status getters (kept for API compatibility)
    // -------------------------------------------------------------------------

    fun getTargetTemperature(): Float? = targetTemperatureC
    fun getIntensity(): Float = intensityOut
    fun getTemperature(): Float = currentTemperature
    fun getTimeAlive(): Long? = startTime?.let { (stopTime ?: System.currentTimeMillis()) - it }
    fun getControllerTimeAlive(): Long? = controllerTimeAlive
    fun getActiveIntensity(): Float = device.getPulse()
    fun isRunning(): Boolean = device.isStarted()
    fun getProfile(): ReflowProfile? = reflowProfile
    fun getPort(): String? = device.getPort()
    fun getStartTime(): Long? = startTime

    fun getPhase(): Int? = if (reflowProfile == null) -1 else currentPhaseIdx

    fun getPhaseName(): String? {
        val idx = getPhase()
        return when {
            idx == null                   -> "Manual"
            idx == -1                     -> "Manual"
            reflowProfile == null         -> "Manual"
            idx > reflowProfile!!.phases.size -> "Finished"
            else                          -> reflowProfile!!.getNameForPhase(idx)
        }
    }

    fun isFinished(): Boolean? {
        val p = reflowProfile ?: return false
        return currentPhaseIdx >= p.phases.size
    }

    fun getPhaseType(): PhaseType? {
        val p = reflowProfile ?: return null
        return p.phases.getOrNull(currentPhaseIdx)?.type
    }

    /** Rough remaining time:
     *  - HEATING/COOLING: time goal minus elapsed (if time > 0)
     *  - REFLOW: unknown (depends on time above threshold), returns null
     */
    fun getNextPhaseIn(): Long? {
        val p = reflowProfile ?: return null
        val idx = currentPhaseIdx
        if (idx < 0 || idx >= p.phases.size) return null
        val ph = p.phases[idx]
        return when (ph.type) {
            PhaseType.REFLOW  -> null // depends on threshold crossing; report unknown
            else -> if (ph.time > 0) {
                val el = max(0L, System.currentTimeMillis() - phaseStartAtMs)
                (ph.time * 1000L - el).coerceAtLeast(0L)
            } else null
        }
    }

    fun getPhaseTime(): Long? {
        val p = reflowProfile ?: return null
        val idx = currentPhaseIdx
        val ph = p.phases.getOrNull(idx) ?: return null
        return when {
            ph.time > 0     -> ph.time * 1000L
            ph.holdFor > 0  -> ph.holdFor * 1000L
            else            -> null
        }
    }

    private fun resetSlopeWindow(nowMs: Long, tempC: Float) {
        tempWindow.clear()
        tempWindow.addLast(TempSample(nowMs, tempC))
        slopeCPerS = null
    }

    private fun sampleSlope(nowMs: Long, tempC: Float) {
        tempWindow.addLast(TempSample(nowMs, tempC))
        val cutoff = nowMs - slopeWindowMs
        while (tempWindow.size >= 2 && tempWindow.first().ms < cutoff) {
            tempWindow.removeFirst()
        }
        val first = tempWindow.firstOrNull()
        val last = tempWindow.lastOrNull()
        slopeCPerS = if (first != null && last != null && last.ms > first.ms) {
            (last.c - first.c) / ((last.ms - first.ms) / 1000f)
        } else null
    }

    fun getTemperatureSlopeCPerS(): Float? = slopeCPerS

    /** Historical helper kept for API compatibility (unused in new executor flow). */
    fun getTimeSinceTempOver(): Long? = null

    fun getTimeAboveLiquidusMs(): Long? =
        reflowProfile?.liquidusTemperature?.let { timeAboveLiquidusMs }

    fun getTimeSinceCommand(): Long? =
        if (started) lastCommandAtMs?.let { System.currentTimeMillis() - it } else null

    // -------------------------------------------------------------------------
    // Main loop
    // -------------------------------------------------------------------------

    fun update() {
        val now = System.currentTimeMillis()
        val dt  = if (lastTickMs == 0L) 0L else (now - lastTickMs)
        lastTickMs = now

        sampleSlope(now, currentTemperature)

        val profile = reflowProfile
        val tMeas   = getTemperature()

        reflowProfile?.liquidusTemperature?.let { liq ->
            if (currentTemperature >= liq) {
                if (aboveLiquidusSinceMs == null) {
                    aboveLiquidusSinceMs = now
                }
                // continuous time since first crossing
                timeAboveLiquidusMs = now - (aboveLiquidusSinceMs ?: now)
            } else {
                // dropped below: reset continuous counter
                aboveLiquidusSinceMs = null
                timeAboveLiquidusMs = 0L
            }
        }

        // --- Manual mode
        if (profile == null) {

            if(manualExec == null) {
                manualExec = ManualExecutor()
            }

            val res: ManualExecOutput? = manualExec?.update(
                dtMs = dt,
                nowMs = now,
                tMeasC = tMeas,
                targetTempC = targetTemperatureC,
                baseIntensity = intensityOut
            )

            if(res != null) {
                targetTemperatureC = res.targetForUiC // identical to manual target
                applyOutput(res.intensity)
            }
            return
        }

        // --- Profile mode
        if (currentPhaseIdx == -1) {
            // lazy enter: start first phase now
            goToNextPhase(profile)
            return
        }
        if (currentPhaseIdx >= profile.phases.size) {
            // finished
            applyOutput(0f)
            targetTemperatureC = null
            return
        }

        // active phase via ProfileExecutor
        val res: ExecOutput? = profileExec?.update(dtMs = dt, nowMs = now, tMeasC = tMeas)
        if(res != null) {
            targetTemperatureC = res.targetForUiC
            applyOutput(res.intensity)

            if (res.phaseCompleted) {
                goToNextPhase(profile)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Phase transitions
    // -------------------------------------------------------------------------

    private fun onPhaseStart(ph: Phase) {
        val now = System.currentTimeMillis()
        phaseStartAtMs = now
        lastCommandAtMs = now

        resetSlopeWindow(now, currentTemperature)

        // Build context for this profile
        val ctx = buildProfileContext()

        // Initialize executor
        if (profileExec == null) {
            profileExec = ProfileExecutor(context = ctx)
        }
        profileExec?.startPhase(
            nowMs = now,
            startTempC = getTemperature(),
            ph = ph,
            lastKnownOut = lastPulseSet
        )

        // Log
        when (ph.type) {
            PhaseType.HEATING -> logMsg("phase:start name='${ph.name}' type=heating target=${f1(ph.targetTemperature)}°C time=${ph.time}s")
            PhaseType.REFLOW  -> logMsg("phase:start name='${ph.name}' type=reflow  threshold=${f1(ph.targetTemperature)}°C hold_for=${ph.holdFor}s max=${ph.maxTemperature?.let { f1(it) } ?: "n/a"}°C")
            PhaseType.COOLING -> logMsg("phase:start name='${ph.name}' type=cooling time=${ph.time}s")
        }
    }

    private fun onPhaseEnd(ph: Phase) {
        logMsg("phase:end name='${ph.name}'")
    }

    private fun goToNextPhase(profile: ReflowProfile) {
        // end current if valid
        if (currentPhaseIdx in profile.phases.indices) {
            onPhaseEnd(profile.phases[currentPhaseIdx])
        }
        // advance
        currentPhaseIdx++
        if (currentPhaseIdx in profile.phases.indices) {
            val ph = profile.phases[currentPhaseIdx]
            onPhaseStart(ph)
            onNewPhase?.invoke(profile, ph, false)
        } else {
            // finished
            applyOutput(0f)
            targetTemperatureC = null
            intensityOut = 0f
            onNewPhase?.invoke(profile, null, true)
            logMsg("profile:finished name='${profile.name}'")
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun applyOutput(p: Float) {
        val np = p.coerceIn(0f, 1f)
        val now = System.currentTimeMillis()
        val dwellOk = now - lastPulseAt >= MIN_PULSE_DWELL_MS
        if (lastPulseAt == 0L || (np != lastPulseSet && dwellOk)) {
            device.setPulse(np)
            lastPulseSet = np
            lastPulseAt = now
            intensityOut = np
        }
    }

    private fun buildProfileContext(): ProfileContext {
        val liq = reflowProfile?.liquidusTemperature ?: 217f
        val absMax = reflowProfile?.phases?.mapNotNull { it.maxTemperature }?.max() ?: 260f
        return ProfileContext(
            liquidusTemperatureC = liq,
            safety = SafetyConfig(
                absoluteMaxTemperature = absMax.coerceAtLeast(230f),
                sensorFaultHysteresisS = 3
            )
        )
    }

    // -------------------------------------------------------------------------
    // Service control
    // -------------------------------------------------------------------------

    fun startService(): Boolean {
        if (!started) {
            val ok = device.start()
            if (ok) {
                startTime = System.currentTimeMillis()
                stopTime = null
                currentTemperature = device.getTemperature()

                updateTask = UpdateTask(this)
                timer = Timer()
                timer!!.schedule(updateTask!!, 0, UPDATE_INTERVAL)

                aboveLiquidusSinceMs = null
                timeAboveLiquidusMs = 0L

                started = true
                return true
            }
        }
        return false
    }

    fun stopService(): Boolean {
        if (started) {
            val ok = device.stop()
            if (ok) {
                updateTask?.isStopped = true
                updateTask = null

                timer?.cancel()
                timer = null

                stopTime = System.currentTimeMillis()
                started = false
                return true
            }
        }
        return false
    }

    private class UpdateTask(private val controller: ReflowController) : TimerTask() {
        var isStopped = false
        override fun run() {
            if (!isStopped && controller.isConnected()) {
                controller.update()
            }
        }
        override fun cancel(): Boolean {
            isStopped = true
            return super.cancel()
        }
    }
}
