package com.tangentlines.reflowcontroller.reflow.executor.planner

import com.tangentlines.reflowcontroller.reflow.profile.Phase
import com.tangentlines.reflowcontroller.reflow.profile.PhaseType
import kotlin.math.max

class ReflowPlanner : PhasePlanner {
    private lateinit var phase: Phase
    private lateinit var ctx: ProfileContext
    private var t0Ms: Long = 0L
    private var T0: Float = 0f

    private var TALms: Long = 0L
    private var lastMs: Long = 0L
    private var hitMinTemp: Boolean = false

    private var reflowThr = 0f
    private var holdForMs = 0L
    private var Tmin = 0f
    private var Tmax = 0f
    private var bandMid = 0f

    override fun reset(startTimeMs: Long, startTempC: Float, phase: Phase, profile: ProfileContext) {
        require(phase.type == PhaseType.REFLOW)
        this.phase = phase
        this.ctx = profile
        this.t0Ms = startTimeMs
        this.T0 = startTempC
        this.lastMs = startTimeMs

        reflowThr = phase.targetTemperature /* in your current model: targetTemperature stores threshold for reflow */
        holdForMs = (phase.holdFor.takeIf { it > 0 } ?: 0) * 1000L
        Tmin = max(phase.minTemperature ?: reflowThr, reflowThr)
        Tmax = phase.maxTemperature ?: (Tmin + 25f)
        bandMid = (Tmin + Tmax) / 2f

        TALms = 0L
        hitMinTemp = false
    }

    override fun update(nowMs: Long, currentTempC: Float): PlanResult {
        val dt = (nowMs - lastMs).coerceAtLeast(0L)
        lastMs = nowMs

        if (currentTempC >= reflowThr) TALms += dt
        if (currentTempC >= Tmin) hitMinTemp = true

        val margin = 2f
        val tPlan = bandMid.coerceIn(Tmin + margin, Tmax - margin)

        val done = (TALms >= holdForMs) && hitMinTemp

        return PlanResult(
            tPlanC = tPlan,
            phaseCompleted = done,
            aux = PlannerAux(talMs = TALms, hitMinTemp = hitMinTemp)
        )
    }
}