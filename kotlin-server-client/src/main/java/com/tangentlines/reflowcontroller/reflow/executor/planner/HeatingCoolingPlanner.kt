package com.tangentlines.reflowcontroller.reflow.executor.planner

import com.tangentlines.reflowcontroller.reflow.profile.Phase
import com.tangentlines.reflowcontroller.reflow.profile.PhaseType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class HeatingCoolingPlanner : PhasePlanner {
    private lateinit var phase: Phase
    private lateinit var ctx: ProfileContext
    private var t0Ms: Long = 0L
    private var T0: Float = 0f

    private var target: Float = 0f
    private var tt: Long = 0L
    private var tmin: Long = 0L
    private var sMax: Float = Float.POSITIVE_INFINITY
    private var sPlan: Float = 0f

    override fun reset(startTimeMs: Long, startTempC: Float, phase: Phase, profile: ProfileContext) {
        require(phase.type == PhaseType.HEATING || phase.type == PhaseType.COOLING)
        this.phase = phase
        this.ctx = profile
        this.t0Ms = startTimeMs
        this.T0 = startTempC

        target = phase.targetTemperature
        tt = (phase.time.takeIf { it > 0 } ?: 0) * 1000L
        tmin = (phase.holdFor.takeIf { it > 0 } ?: phase.time.takeIf { it > 0 } ?: 0) * 1000L // min time rule
        sMax = phase.maxSlope ?: Float.POSITIVE_INFINITY

        val sNeed = if (tt > 0) (target - T0) / (tt / 1000f) else Float.POSITIVE_INFINITY
        sPlan = when {
            sMax.isFinite() -> sNeed.coerceIn(-abs(sMax), abs(sMax))
            else -> sNeed
        }
    }

    override fun update(nowMs: Long, currentTempC: Float): PlanResult {
        val dt = nowMs - t0Ms
        val rampEndMs = if (tt > 0) t0Ms + tt else nowMs
        val planNow = if (tt > 0) {
            val alpha = ((nowMs - t0Ms) / 1000f).coerceAtLeast(0f)
            val T = T0 + sPlan * alpha
            if (nowMs >= rampEndMs) target else when {
                sPlan >= 0f -> min(T, target)
                else        -> max(T, target)
            }
        } else target

        val minTimeReached = dt >= tmin
        val atOrPastTarget = if (sPlan >= 0f) currentTempC >= (target - 0.5f) else currentTempC <= (target + 0.5f)
        val timeoutReached = tt > 0 && nowMs >= (t0Ms + tt)

        val done = (minTimeReached && atOrPastTarget) || timeoutReached

        return PlanResult(
            tPlanC = planNow,
            phaseCompleted = done
        )
    }
}