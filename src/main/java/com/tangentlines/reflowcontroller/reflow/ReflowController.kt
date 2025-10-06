package com.tangentlines.reflowcontroller.reflow

import com.tangentlines.reflowcontroller.log.Logger
import com.tangentlines.reflowcontroller.reflow.device.BetaLayoutDevice
import com.tangentlines.reflowcontroller.reflow.device.Device
import com.tangentlines.reflowcontroller.reflow.device.FakeDevice
import com.tangentlines.reflowcontroller.reflow.profile.Phase
import com.tangentlines.reflowcontroller.reflow.profile.PhaseType
import com.tangentlines.reflowcontroller.reflow.profile.ReflowProfile
import java.util.*
import kotlin.math.max
import kotlin.math.min

private const val UPDATE_INTERVAL = 500L
private const val BRAKE = 0.75f

private const val TEMP_HYSTERESIS_UP   = 2.5f   // °C above target -> force OFF
private const val TEMP_HYSTERESIS_DOWN = 0.5f   // °C below target -> don't ramp up aggressively
private const val MIN_PULSE_DWELL_MS   = 2200L  // min time between pulse changes

class ReflowController(port : String) {

    private var phaseStartAt: Long = 0L
    private var phaseStartTemp: Float = 0f
    private var baseIntensity: Float = 0.5f
    private var holdPulseSum: Double = 0.0
    private var holdPulseCount: Long = 0
    private var lastPulseSet: Float = 0.0f
    private var lastPulseAt: Long = 0L
    private var overTarget: Boolean = false
    private var holdAboveMs: Long = 0L
    private var lastTickMs: Long = 0L

    private fun f1(x: Float) = String.format(Locale.US, "%.1f", x)
    private fun f2(x: Float) = String.format(Locale.US, "%.2f", x)
    private fun logMsg(s: String) { try { Logger.addMessage(s) } catch (_: Exception) {} }

    var onTempChanged : (() -> Unit)? = null
    var onNewPhase : ((ReflowProfile, Phase?, Boolean) -> Unit)? = null

    private var timer : Timer? = null
    private var controllerTimeAlive : Long? = null

    private var intensity : Float = 1.0f
    private var targetTemperature : Float? = null
    private var lastCommand : Long? = null
    private var connectTime : Long? = null
    private var startTime : Long? = null
    private var stopTime : Long? = null
    private var temOverSince : Long? = null

    private var currentTemperature : Float = -1.0f

    private var reflowProfile : ReflowProfile? = null
    private var currentReflowProfilePhase : Int = -1

    private var started : Boolean = false
    private var updateTask: UpdateTask? = null

    private val device : Device = when(port) {
        FAKE_IDENTIFIER -> FakeDevice(FAKE_IDENTIFIER)
        else -> BetaLayoutDevice(port)
    }

    init {

        device.addOnTemperatureChanged {
            this.currentTemperature = device.getTemperature()
            onTempChanged?.invoke()
        }

    }

    fun connect(): Boolean {

        val success = device.connect()

        if(success){
            connectTime = System.currentTimeMillis()
        }

        return success

    }

    fun isConnected(): Boolean {
        return device.isConnected()
    }

    fun setProfile(profile: ReflowProfile?){
        this.reflowProfile = profile
        this.currentReflowProfilePhase = -1
    }

    fun setTargetTemperature(intensity : Float, temperature : Float) {

        this.targetTemperature = temperature
        this.intensity = Math.min(1.0f, Math.max(0.0f, intensity))

        this.lastCommand = System.currentTimeMillis()
        this.temOverSince = null

    }

    fun getTargetTemperature() : Float? {
        return targetTemperature
    }

    fun getIntensity() : Float {
        return intensity
    }

    fun getTemperature() : Float {
        return currentTemperature
    }

    fun getTimeAlive() : Long? {
        return startTime?.let { (stopTime ?: System.currentTimeMillis()) - it }
    }

    fun update() {

        val currentTemp = getTemperature()
        var holdFor = -1;

        reflowProfile?.let { profile ->

            if (currentReflowProfilePhase == -1) {
                goToNextPhase(profile)
                onPhaseChangedReset()
            } else if (currentReflowProfilePhase < profile.phases.size) {

                val phase = profile.phases[currentReflowProfilePhase]
                val currentTemp = getTemperature()
                val elapsedMs = getTimeSinceCommand() ?: 0L
                val reached = (temOverSince != null) || (targetTemperature?.let { currentTemp >= it } == true)

                when (phase.type) {
                    PhaseType.HEATING -> {
                        // Only advance when target reached (hysteresis handled elsewhere).
                        if (reached) {
                            goToNextPhase(profile); onPhaseChangedReset()
                        } else if (phase.time > 0 && elapsedMs > phase.time * 1000L) {
                            logMsg("phase:behind name='${phase.name}' time_target=${phase.time}s T=${f1(currentTemp)}°C target=${f1(phase.targetTemperature)}°C")
                        }
                    }
                    PhaseType.REFLOW -> {
                        // End when cumulative time ABOVE threshold meets hold_for
                        if (phase.holdFor > 0 && holdAboveMs >= phase.holdFor * 1000L) {
                            goToNextPhase(profile); onPhaseChangedReset()
                        } else if (phase.holdFor == 0 && overTarget) {
                            // no hold time configured: once above, advance
                            goToNextPhase(profile); onPhaseChangedReset()
                        }
                    }
                    PhaseType.COOLING -> {
                        if (phase.time > 0 && elapsedMs >= phase.time * 1000L) {
                            goToNextPhase(profile); onPhaseChangedReset()
                        }
                    }
                }

                // Fallback for phases with no constraints: end when reached (useful for quick ramps)
                if (phase.time == 0 && phase.holdFor == 0 && reached && phase.type != PhaseType.COOLING) {
                    goToNextPhase(profile); onPhaseChangedReset()
                }
            }
        }


        val now = System.currentTimeMillis()
        val tTemp = targetTemperature

        val profile = reflowProfile
        val idx = currentReflowProfilePhase
        val phase = if (profile != null && idx in profile!!.phases.indices) profile!!.phases[idx] else null

        val dt = if (lastTickMs == 0L) 0L else (now - lastTickMs)
        lastTickMs = now
        if (phase?.type == PhaseType.REFLOW && overTarget && dt > 0) {
            holdAboveMs += dt
        }

        if (phase == null || tTemp == null) {
            setPulseIfChanged(0.0f)
            return
        }

        // One-shot threshold crossing with hysteresis
        if (!overTarget && currentTemp >= tTemp + TEMP_HYSTERESIS_UP) {
            overTarget = true
            if (temOverSince == null) {
                temOverSince = now
                val elapsed = (now - phaseStartAt) / 1000f
                logMsg("phase:reached name='${phase.name}' at=${f1(currentTemp)}°C t=${f1(elapsed)}s")
            }
        } else if (overTarget && currentTemp <= tTemp - TEMP_HYSTERESIS_DOWN) {
            overTarget = false
            // do not clear temOverSince within the phase
        }

        val elapsedSec = (now - phaseStartAt) / 1000f
        val kpHeat = 0.004f
        val kpHold = 0.010f


        val desired: Float = when (phase.type) {

            PhaseType.HEATING -> {
                val target = phase.targetTemperature
                val planAlpha = if (phase.time > 0) (elapsedSec / phase.time).coerceIn(0f, 1f) else 1f
                val plannedTemp = phaseStartTemp + (target - phaseStartTemp) * planAlpha
                val err = plannedTemp - currentTemp
                var cmd = (baseIntensity + kpHeat * err).coerceIn(0f, 1f)

                phase.maxSlope?.let { maxS ->
                    val dt = max(0.1f, elapsedSec)
                    val slope = (currentTemp - phaseStartTemp) / dt
                    if (slope > maxS + 0.1f) cmd = (cmd * 0.8f).coerceAtLeast(0f)
                }

                when {
                    currentTemp >= target + TEMP_HYSTERESIS_UP   -> 0.0f
                    currentTemp >= target - TEMP_HYSTERESIS_DOWN -> min(lastPulseSet, (cmd * 0.5f).coerceAtMost(0.35f))
                    else                                          -> cmd
                }
            }

            PhaseType.REFLOW -> {
                val thr = phase.targetTemperature               // threshold for hold timing
                val maxT = phase.maxTemperature ?: thr          // must be provided by profile validator
                val errToMax = maxT - currentTemp               // drive toward maxT
                val base = baseIntensity

                // push up while below maxT, coast when above
                var cmd = if (errToMax > 0f) (base + kpHold * errToMax).coerceIn(0f, 1f)
                else (base * 0.4f).coerceIn(0f, 1f)

                // enforce MAX temperature with hysteresis
                val out = when {
                    currentTemp >= maxT + TEMP_HYSTERESIS_UP   -> 0.0f
                    currentTemp >= maxT - TEMP_HYSTERESIS_DOWN -> min(lastPulseSet, (cmd * 0.4f).coerceAtMost(0.25f))
                    // near threshold but not yet at max: allow some moderation to reduce ringing
                    currentTemp >= thr - TEMP_HYSTERESIS_DOWN  -> min(lastPulseSet, (cmd * 0.6f).coerceAtMost(0.35f))
                    else                                       -> cmd
                }

                // collect average power only while above threshold (for suggestions)
                if (overTarget) { holdPulseSum += out.toDouble(); holdPulseCount++ }

                out
            }

            PhaseType.COOLING -> {
                // Heater OFF, no active target control
                0.0f
            }
        }

        setPulseIfChanged(desired)

    }

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

    private fun onPhaseStart(ph: Phase) {
        phaseStartAt = System.currentTimeMillis()
        phaseStartTemp = getTemperature()
        baseIntensity = (ph.initialIntensity ?: lastPulseSet.takeIf { it > 0f } ?: 0.5f).coerceIn(0f, 1f)

        // init per-phase timers
        lastTickMs = phaseStartAt
        holdAboveMs = 0L
        overTarget = false
        temOverSince = null
        holdPulseSum = 0.0
        holdPulseCount = 0

        when (ph.type) {
            PhaseType.HEATING, PhaseType.REFLOW -> {
                this.intensity = baseIntensity
                // For REFLOW, targetTemperature is the **threshold** used to define "above"
                this.targetTemperature = ph.targetTemperature
                this.lastCommand = phaseStartAt
            }
            PhaseType.COOLING -> {
                this.intensity = 0.0f
                this.targetTemperature = null
                this.lastCommand = phaseStartAt
                setPulseIfChanged(0.0f)
            }
        }

        when (ph.type) {
            PhaseType.HEATING -> logMsg("phase:start name='${ph.name}' type=heating target=${f1(ph.targetTemperature)}°C time=${ph.time}s baseI=${f2(baseIntensity)} T0=${f1(phaseStartTemp)}°C")
            PhaseType.REFLOW  -> logMsg("phase:start name='${ph.name}' type=reflow  threshold=${f1(ph.targetTemperature)}°C hold_for=${ph.holdFor}s max=${ph.maxTemperature?.let(::f1) ?: "n/a"}°C baseI=${f2(baseIntensity)} T0=${f1(phaseStartTemp)}°C")
            PhaseType.COOLING -> logMsg("phase:start name='${ph.name}' type=cooling time=${ph.time}s baseI=0.00 T0=${f1(phaseStartTemp)}°C")
        }
    }

    private fun onPhaseEnd(ph: Phase) {
        val reachedMs = temOverSince?.let { it - phaseStartAt } ?: -1L
        when (ph.type) {
            PhaseType.HEATING -> {
                val targetSec = ph.time.toFloat()
                val suggested = if (reachedMs > 0) {
                    val actualSec = reachedMs / 1000f
                    (baseIntensity * (targetSec / actualSec)).coerceIn(0.05f, 1f)
                } else baseIntensity
                val actualStr = if (reachedMs > 0) f1(reachedMs / 1000f) else "n/a"
                logMsg("[suggestion] name='${ph.name}' type=heating target=${f1(ph.targetTemperature)}°C time_target=${f1(targetSec)}s time_actual=$actualStr baseI=${f2(baseIntensity)} suggest.initial_intensity=${f2(suggested)}")
            }
            PhaseType.REFLOW -> {
                val avg = if (holdPulseCount > 0) (holdPulseSum / holdPulseCount).toFloat() else baseIntensity
                logMsg("[suggestion] name='${ph.name}' type=reflow threshold=${f1(ph.targetTemperature)}°C hold_for=${ph.holdFor}s avg_intensity=${f2(avg)} suggest.initial_intensity=${f2(avg)}")
            }
        }
    }

    private fun goToNextPhase(profile: ReflowProfile) {
        if (currentReflowProfilePhase in profile.phases.indices) {
            onPhaseEnd(profile.phases[currentReflowProfilePhase])
        }
        currentReflowProfilePhase++
        if (currentReflowProfilePhase in profile.phases.indices) {
            onPhaseStart(profile.phases[currentReflowProfilePhase])
        } else {
            device.setPulse(0.0f)
            targetTemperature = null        // <<< optional
            intensity = 0.0f                // <<< optional
            logMsg("profile:finished name='${profile.name}'")
        }
    }

    // call this when switching phases / profiles / manual start/stop
    private fun onPhaseChangedReset() {
        overTarget = false
        temOverSince = null
    }

    fun startService() : Boolean {

        if(!started) {
            val success = device.start()
            if (success) {

                this.startTime = System.currentTimeMillis()
                this.stopTime = null

                this.currentTemperature = device.getTemperature()

                updateTask = UpdateTask(this)

                timer = Timer()
                timer!!.schedule(updateTask!!, 0, UPDATE_INTERVAL)

                started = true
                return true

            }
        }

        return false

    }

    fun stopService() : Boolean {

        if(started) {
            val success = device.stop()
            if (success) {

                updateTask?.isStopped = true
                updateTask = null

                timer!!.cancel()
                timer = null

                stopTime = System.currentTimeMillis()
                started = false
                return true

            }
        }

        return false

    }

    fun getTimeSinceTempOver(): Long? {
        return if(started) temOverSince?.let { System.currentTimeMillis() - it } else null
    }

    fun getTimeSinceCommand(): Long? {
        return if(started) lastCommand?.let { System.currentTimeMillis() - it } else null
    }

    fun getControllerTimeAlive() : Long? {
        return controllerTimeAlive
    }

    fun getActiveIntensity() : Float {
        return device.getPulse()
    }

    fun isRunning(): Boolean {
        return device.isStarted()
    }

    fun disconnect(): Boolean {
        return device.disconnect()
    }

    fun getPhase(): Int? {

        return if(reflowProfile == null) {
            -1
        } else {
            currentReflowProfilePhase
        }

    }

    fun getPhaseName() : String? {
        val idx = getPhase();
        return when {
            idx == null -> "Manual"
            idx == -1 -> "Manual"
            reflowProfile == null -> "Manual"
            idx > reflowProfile!!.phases.size -> "Finished"
            else -> reflowProfile!!.getNameForPhase(idx)
        }
    }

    fun isFinished(): Boolean? {
        val profile = reflowProfile ?: return false
        return currentReflowProfilePhase >= profile.phases.size
    }

    fun getProfile() : ReflowProfile? {
        return reflowProfile
    }

    fun getPort(): String? {
        return this.device.getPort()
    }

    fun getPhaseTime() : Long? {

        val profile = reflowProfile ?: return null
        if(currentReflowProfilePhase == -1) return null
        if(currentReflowProfilePhase >= profile.phases.size) return null

        val phase = profile.phases.getOrNull(currentReflowProfilePhase) ?: return null

        return when {
            phase.time > 0 -> (phase.time * 1000L)
            phase.holdFor > 0 -> (phase.holdFor * 1000L)
            else -> null
        }

    }

    fun getNextPhaseIn(): Long? {
        val profile = reflowProfile ?: return null
        val idx = currentReflowProfilePhase
        if (idx == -1) return null
        if (idx >= profile.phases.size) return null

        val phase = profile.phases.getOrNull(idx) ?: return null

        val sinceCmd = getTimeSinceCommand() ?: 0L
        val sinceOver = getTimeSinceTempOver() // null until first threshold hit
        val curTemp = getTemperature()

        // Option A: phase.time (always counts from command start)
        val byTime = if (phase.time > 0) phase.time * 1000L - sinceCmd else Long.MAX_VALUE

        // Option B: phase.holdFor (only counts after we reached the temp threshold)
        val byHold = if (phase.holdFor > 0) {
            if (sinceOver != null) phase.holdFor * 1000L - sinceOver else Long.MAX_VALUE
        } else Long.MAX_VALUE

        // If both are disabled (0/0), this phase transitions immediately once target is reached
        if (phase.time == 0 && phase.holdFor == 0) {
            return if (curTemp >= phase.targetTemperature) 0L else null
        }

        val next = minOf(byTime, byHold)
        if (next == Long.MAX_VALUE) return null  // not determinable yet (e.g., waiting to hit temp)

        return max(0L, next)
    }

    fun getPhaseType(): PhaseType? {
        val profile = reflowProfile ?: return null
        val phase = profile.phases.getOrNull(currentReflowProfilePhase) ?: return null
        return phase.type
    }

    fun getStartTime() : Long? {
        return this.startTime
    }

}

private class UpdateTask(private val controller : ReflowController) : TimerTask() {

    var isStopped = false

    override fun run() {

        if(!isStopped && controller.isConnected()){
            controller.update()
        }

    }

    override fun cancel(): Boolean {
        isStopped = true
        return super.cancel()
    }

}