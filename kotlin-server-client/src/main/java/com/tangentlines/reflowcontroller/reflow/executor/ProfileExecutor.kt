package com.tangentlines.reflowcontroller.reflow.executor

import com.tangentlines.reflowcontroller.reflow.executor.planner.DefaultIntensityRegulator
import com.tangentlines.reflowcontroller.reflow.executor.planner.HeatingCoolingPlanner
import com.tangentlines.reflowcontroller.reflow.executor.planner.IntensityRegulator
import com.tangentlines.reflowcontroller.reflow.executor.planner.PhasePlanner
import com.tangentlines.reflowcontroller.reflow.executor.planner.ProfileContext
import com.tangentlines.reflowcontroller.reflow.executor.planner.ReflowPlanner
import com.tangentlines.reflowcontroller.reflow.executor.planner.SlopeTracker
import com.tangentlines.reflowcontroller.reflow.profile.Phase
import com.tangentlines.reflowcontroller.reflow.profile.PhaseType
import kotlin.math.max

data class ExecOutput(
    val intensity: Float,
    val targetForUiC: Float?,
    val phaseCompleted: Boolean
)

class ProfileExecutor(
    private val context: ProfileContext,
    private val regulator: IntensityRegulator = DefaultIntensityRegulator(),
    private val slope: SlopeTracker = SlopeTracker(),
    private val heatingCoolingPlannerFactory: () -> PhasePlanner = { HeatingCoolingPlanner() },
    private val reflowPlannerFactory: () -> PhasePlanner = { ReflowPlanner() }
) {
    private val H_UP = 2.5f
    private val H_DN = 0.5f

    private var planner: PhasePlanner? = null
    private var phase: Phase? = null
    private var phaseStartAtMs: Long = 0L
    private var phaseStartTempC: Float = 0f
    private var baseIntensity: Float = 0.5f
    private var lastOut: Float = 0f

    // Reflow timing/state
    private var aboveThreshold: Boolean = false           // above reflow threshold (with hysteresis)
    private var holdAboveMs: Long = 0L                    // cumulative time above threshold
    private var minTempReached: Boolean = false           // reached minTemperature at least once

    private var currentSlope: Float? = null

    fun startPhase(nowMs: Long, startTempC: Float, ph: Phase, lastKnownOut: Float) {
        phase = ph
        phaseStartAtMs = nowMs
        phaseStartTempC = startTempC
        baseIntensity = (ph.initialIntensity ?: if (lastKnownOut > 0f) lastKnownOut else 0.5f).coerceIn(0f, 1f)
        lastOut = baseIntensity

        slope.reset(startTempC, nowMs)
        aboveThreshold = false
        holdAboveMs = 0L
        minTempReached = false
        currentSlope = null

        planner = when (ph.type) {
            PhaseType.HEATING, PhaseType.COOLING -> heatingCoolingPlannerFactory()
            PhaseType.REFLOW                     -> reflowPlannerFactory()
        }.also { it.reset(nowMs, startTempC, ph, context) }

        regulator.reset(baseIntensity, ph.maxSlope, context.safety)
    }

    fun update(dtMs: Long, nowMs: Long, tMeasC: Float): ExecOutput {
        val ph = phase ?: return ExecOutput(0f, null, phaseCompleted = false)
        val pl = planner ?: return ExecOutput(0f, null, phaseCompleted = false)

        // slope for both modes
        val dTdt = slope.sample(tMeasC, nowMs)
        currentSlope = dTdt

        val plan = pl.update(nowMs, tMeasC)
        val targetForUi = if (ph.type == PhaseType.COOLING) null else plan.tPlanC

        val out = regulator.compute(
            nowMs = nowMs,
            dtMs = dtMs,
            tMeasC = tMeasC,
            dTdtCPerS = dTdt,
            tPlanC = plan.tPlanC,
            phase = ph,
            prevIntensity = lastOut
        ).coerceIn(0f, 1f)
        lastOut = out

        // ----- completion logic
        val elapsedMs = nowMs - phaseStartAtMs
        val minTimeMs = (ph.minTime.takeIf { it > 0 } ?: 0).toLong() * 1000L

        var completed = false
        when (ph.type) {
            PhaseType.HEATING -> {
                // complete when target reached (with hysteresis); then enforce minTime gate below
                val tTarget = ph.targetTemperature
                val reached = tMeasC >= tTarget + H_UP
                completed = reached
            }

            PhaseType.REFLOW -> {
                // 1) track “above reflow threshold”
                val thr = ph.minTemperature ?: ph.targetTemperature
                if (!aboveThreshold && tMeasC >= thr + H_UP) {
                    aboveThreshold = true
                } else if (aboveThreshold && tMeasC <= thr - H_DN) {
                    aboveThreshold = false
                }
                if (aboveThreshold && dtMs > 0) holdAboveMs += dtMs

                // 2) track min temperature reached at least once (no hysteresis needed, single-shot)
                val minT = ph.minTemperature ?: thr
                if (!minTempReached && tMeasC >= minT) minTempReached = true

                // 3) completion requires BOTH: (a) hold above reflow threshold for hold_for, AND (b) min temperature reached
                val needHold = ph.holdFor.takeIf { it > 0 }?.times(1000L)
                val holdOk = when {
                    needHold != null -> holdAboveMs >= needHold
                    else             -> aboveThreshold          // no hold time → once above threshold
                }
                completed = holdOk && minTempReached
            }

            PhaseType.COOLING -> {
                completed = (ph.time > 0 && elapsedMs >= ph.time * 1000L)
            }
        }

        // global min duration gate
        if (minTimeMs > 0 && elapsedMs < minTimeMs) {
            completed = false
        }

        return ExecOutput(intensity = out, targetForUiC = targetForUi, phaseCompleted = completed)
    }

    fun elapsedSincePhaseStartMs(nowMs: Long): Long = max(0L, nowMs - phaseStartAtMs)
    fun getSlope(): Float? = currentSlope
}