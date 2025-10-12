package com.tangentlines.reflowcontroller.reflow

import com.tangentlines.reflowcontroller.log.Logger
import com.tangentlines.reflowcontroller.reflow.device.BetaLayoutDevice
import com.tangentlines.reflowcontroller.reflow.device.Device
import com.tangentlines.reflowcontroller.reflow.device.FakeDevice
import com.tangentlines.reflowcontroller.reflow.executor.*
import com.tangentlines.reflowcontroller.reflow.profile.Phase
import com.tangentlines.reflowcontroller.reflow.profile.PhaseType
import com.tangentlines.reflowcontroller.reflow.profile.ReflowProfile
import java.util.*
import kotlin.math.max

private const val UPDATE_INTERVAL = 500L
private const val BRAKE = 0.75f
private const val MIN_PULSE_DWELL_MS = 2200L  // min time between pulse changes

class ReflowController(port: String) {

    // ---- Phase & runtime bookkeeping
    private var phaseStartAt: Long = 0L
    private var phaseStartTemp: Float = 0f
    private var baseIntensity: Float = 0.5f
    private var lastPulseSet: Float = 0.0f
    private var lastPulseAt: Long = 0L
    private var lastTickMs: Long = 0L

    // simple logging helpers
    private fun f1(x: Float) = String.format(Locale.US, "%.1f", x)
    private fun f2(x: Float) = String.format(Locale.US, "%.2f", x)
    private fun logMsg(s: String) { try { Logger.addMessage(s) } catch (_: Exception) {} }

    // callbacks
    var onTempChanged: (() -> Unit)? = null
    var onNewPhase: ((ReflowProfile, Phase?, Boolean) -> Unit)? = null

    // timers
    private var timer: Timer? = null
    private var updateTask: UpdateTask? = null

    // exported status fields
    private var controllerTimeAlive: Long? = null
    private var intensity: Float = 1.0f
    private var targetTemperature: Float? = null
    private var lastCommand: Long? = null
    private var connectTime: Long? = null
    private var startTime: Long? = null
    private var stopTime: Long? = null

    // sensors
    private var currentTemperature: Float = -1.0f

    // profile/phase
    private var reflowProfile: ReflowProfile? = null
    private var currentReflowProfilePhase: Int = -1

    // service gate
    private var started: Boolean = false

    // ---- New: planner/regulator/slope
    private var planner: PhasePlanner? = null
    private var lastPlanAux: PlannerAux? = null
    private val regulator = DefaultIntensityRegulator()
    private val slope = SlopeTracker()
    private var lastSlopeCPerS: Float = 0f

    // Construct a ProfileContext from the loaded profile
    private fun buildProfileContext(): ProfileContext {
        val liq = reflowProfile?.liquidusTemperature ?: 217f
        val absMax = (reflowProfile?.phases?.mapNotNull { it.maxTemperature }?.max() ?: 260f)
            .coerceAtLeast(230f)
        return ProfileContext(
            liquidusTemperatureC = liq,
            safety = SafetyConfig(absoluteMaxTemperature = absMax, sensorFaultHysteresisS = 3)
        )
    }

    // Simple planner factory (swappable if you want to inject)
    private fun newPlannerFor(ph: Phase): PhasePlanner = when (ph.type) {
        PhaseType.HEATING, PhaseType.COOLING -> HeatingCoolingPlanner()
        PhaseType.REFLOW                     -> ReflowPlanner()
    }

    // device binding
    private val device: Device = when (port) {
        FAKE_IDENTIFIER -> FakeDevice(FAKE_IDENTIFIER)
        else            -> BetaLayoutDevice(port)
    }

    init {
        device.addOnTemperatureChanged {
            this.currentTemperature = device.getTemperature()
            onTempChanged?.invoke()
        }
    }

    // ---- Connection gates
    fun connect(): Boolean {
        val ok = device.connect()
        if (ok) connectTime = System.currentTimeMillis()
        return ok
    }

    fun disconnect(): Boolean {
        return device.disconnect()
    }

    fun isConnected(): Boolean = device.isConnected()

    // ---- Profile / manual mode gates
    fun setProfile(profile: ReflowProfile?): Boolean {
        reflowProfile = profile
        currentReflowProfilePhase = -1
        return true
    }

    fun setTargetTemperature(intensity: Float, temperature: Float): Boolean {
        // manual mode target
        this.targetTemperature = temperature
        this.intensity = intensity.coerceIn(0f, 1f)
        this.lastCommand = System.currentTimeMillis()
        return true
    }

    // ---- Status getters
    fun getTargetTemperature(): Float? = targetTemperature
    fun getIntensity(): Float = intensity
    fun getTemperature(): Float = currentTemperature
    fun getTemperatureSlopeCPerS(): Float? = lastSlopeCPerS
    fun getTimeAlive(): Long? = startTime?.let { (stopTime ?: System.currentTimeMillis()) - it }
    fun getControllerTimeAlive(): Long? = controllerTimeAlive
    fun getActiveIntensity(): Float = device.getPulse()
    fun isRunning(): Boolean = device.isStarted()
    fun getProfile(): ReflowProfile? = reflowProfile
    fun getPort(): String? = device.getPort()
    fun getStartTime(): Long? = startTime

    fun getPhase(): Int? =
        if (reflowProfile == null) -1 else currentReflowProfilePhase

    fun getPhaseName(): String? {
        val idx = getPhase()
        return when {
            idx == null                 -> "Manual"
            idx == -1                   -> "Manual"
            reflowProfile == null       -> "Manual"
            idx > reflowProfile!!.phases.size -> "Finished"
            else                        -> reflowProfile!!.getNameForPhase(idx)
        }
    }

    fun isFinished(): Boolean? {
        val p = reflowProfile ?: return false
        return currentReflowProfilePhase >= p.phases.size
    }

    fun getPhaseType(): PhaseType? {
        val p = reflowProfile ?: return null
        return p.phases.getOrNull(currentReflowProfilePhase)?.type
    }

    /** Rough remaining time using planner aux (TAL) or phase time goal if available. */
    fun getNextPhaseIn(): Long? {
        val p = reflowProfile ?: return null
        val idx = currentReflowProfilePhase
        if (idx == -1 || idx >= p.phases.size) return null
        val ph = p.phases[idx]

        val sinceCmd = getTimeSinceCommand() ?: 0L
        val byTime = if (ph.time > 0) ph.time * 1000L - sinceCmd else Long.MAX_VALUE

        val auxTal = lastPlanAux?.talMs
        val byHold = if (ph.holdFor > 0 && auxTal != null) ph.holdFor * 1000L - auxTal else Long.MAX_VALUE

        val next = minOf(byTime, byHold)
        return if (next == Long.MAX_VALUE) null else max(0L, next)
    }

    fun getPhaseTime(): Long? {
        val p = reflowProfile ?: return null
        if (currentReflowProfilePhase == -1) return null
        val ph = p.phases.getOrNull(currentReflowProfilePhase) ?: return null
        return when {
            ph.time > 0     -> (ph.time * 1000L)
            ph.holdFor > 0  -> (ph.holdFor * 1000L)
            else            -> null
        }
    }

    fun getTimeSinceCommand(): Long? =
        if (started) lastCommand?.let { System.currentTimeMillis() - it } else null

    // ---- Main update loop
    fun update() {
        val now = System.currentTimeMillis()
        val curT = getTemperature()

        val dt = if (lastTickMs == 0L) 0L else (now - lastTickMs)
        lastTickMs = now

        lastSlopeCPerS = slope.sample(curT, now)

        val profile = reflowProfile
        val idx = currentReflowProfilePhase
        val phase = if (profile != null && idx in profile.phases.indices) profile.phases[idx] else null

        // ---- Manual mode
        if (profile == null) {
            val tTemp = targetTemperature
            if (tTemp == null) {
                device.setPulse(0.0f)
            } else {
                when {
                    curT < tTemp - 10 * BRAKE -> device.setPulse(intensity * 1.0f)
                    curT < tTemp - 3 * BRAKE  -> device.setPulse(intensity * 0.5f)
                    curT < tTemp              -> device.setPulse(intensity * 0.3f)
                    else                      -> device.setPulse(0.0f)
                }
            }
            return
        }

        // ---- Profile mode (planner + regulator)
        if (currentReflowProfilePhase == -1) {
            goToNextPhase(profile)   // will call onPhaseStart for phase 0
            return
        } else if (currentReflowProfilePhase >= profile.phases.size) {
            // finished
            setPulseIfChanged(0f)
            targetTemperature = null
            return
        }

        // Active phase: ask planner for current plan; regulator for output
        val planner = this.planner ?: return
        val ph = phase!!

        val plan = planner.update(now, curT)
        lastPlanAux = plan.aux

        // Publish planned temperature as target for UI (heating/reflow dynamic, cooling null)
        targetTemperature = when (ph.type) {
            PhaseType.COOLING -> null
            else              -> plan.tPlanC
        }

        val out = regulator.compute(
            nowMs = now,
            dtMs = dt,
            tMeasC = curT,
            dTdtCPerS = lastSlopeCPerS,
            tPlanC = plan.tPlanC,
            phase = ph,
            prevIntensity = lastPulseSet
        )
        setPulseIfChanged(out)

        if (plan.phaseCompleted) {
            goToNextPhase(profile)
        }
    }

    // ---- Phase transitions
    private fun onPhaseStart(ph: Phase) {
        val now = System.currentTimeMillis()
        phaseStartAt = now
        phaseStartTemp = getTemperature()

        baseIntensity = (ph.initialIntensity ?: lastPulseSet.takeIf { it > 0f } ?: 0.5f).coerceIn(0f, 1f)

        // reset tick/slope
        lastTickMs = now
        slope.reset(phaseStartTemp, now)

        // build context & initialize planner/regulator
        val ctx = buildProfileContext()
        planner = newPlannerFor(ph).also { it.reset(now, phaseStartTemp, ph, ctx) }
        regulator.reset(baseIntensity, ph.maxSlope, ctx.safety)

        // status mirrors
        lastCommand = now
        intensity = baseIntensity
        targetTemperature = when (ph.type) {
            PhaseType.COOLING -> null
            else              -> phaseStartTemp // will be updated next tick by planner
        }

        when (ph.type) {
            PhaseType.HEATING -> logMsg("phase:start name='${ph.name}' type=heating target=${f1(ph.targetTemperature)}°C time=${ph.time}s baseI=${f2(baseIntensity)} T0=${f1(phaseStartTemp)}°C")
            PhaseType.REFLOW  -> logMsg("phase:start name='${ph.name}' type=reflow  threshold=${f1(ph.targetTemperature)}°C hold_for=${ph.holdFor}s max=${ph.maxTemperature?.let(::f1) ?: "n/a"}°C baseI=${f2(baseIntensity)} T0=${f1(phaseStartTemp)}°C")
            PhaseType.COOLING -> logMsg("phase:start name='${ph.name}' type=cooling time=${ph.time}s baseI=0.00 T0=${f1(phaseStartTemp)}°C")
        }
    }

    private fun onPhaseEnd(ph: Phase) {
        // Optional: keep your suggestion logs if you want (requires extra accumulation).
        // For simplicity we only mark end here.
        logMsg("phase:end name='${ph.name}'")
    }

    private fun goToNextPhase(profile: ReflowProfile) {
        if (currentReflowProfilePhase in profile.phases.indices) {
            onPhaseEnd(profile.phases[currentReflowProfilePhase])
        }
        currentReflowProfilePhase++
        if (currentReflowProfilePhase in profile.phases.indices) {
            val ph = profile.phases[currentReflowProfilePhase]
            onPhaseStart(ph)
            onNewPhase?.invoke(profile, ph, false)
        } else {
            // finished
            setPulseIfChanged(0.0f)
            targetTemperature = null
            intensity = 0.0f
            onNewPhase?.invoke(profile, null, true)
            logMsg("profile:finished name='${profile.name}'")
        }
    }

    // ---- Pulse out with dwell
    private fun setPulseIfChanged(p: Float) {
        val now = System.currentTimeMillis()
        val np = p.coerceIn(0f, 1f)
        val dwellOk = now - lastPulseAt >= MIN_PULSE_DWELL_MS
        if (lastPulseAt == 0L || (np != lastPulseSet && dwellOk)) {
            device.setPulse(np)
            lastPulseSet = np
            lastPulseAt = now
        }
    }

    // ---- Service control
    fun startService(): Boolean {
        if (!started) {
            val success = device.start()
            if (success) {
                startTime = System.currentTimeMillis()
                stopTime = null
                currentTemperature = device.getTemperature()

                updateTask = UpdateTask(this)
                timer = Timer()
                timer!!.schedule(updateTask!!, 0, UPDATE_INTERVAL)

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

    // Historical helpers kept for API compatibility
    fun getTimeSinceTempOver(): Long? = null // handled via planner aux if you want; not used directly now

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
