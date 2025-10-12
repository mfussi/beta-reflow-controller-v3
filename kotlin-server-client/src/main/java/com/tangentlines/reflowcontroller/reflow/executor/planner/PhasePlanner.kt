package com.tangentlines.reflowcontroller.reflow.executor.planner

import com.tangentlines.reflowcontroller.reflow.profile.Phase

interface PhasePlanner {
    fun reset(startTimeMs: Long, startTempC: Float, phase: Phase, profile: ProfileContext)
    fun update(nowMs: Long, currentTempC: Float): PlanResult
}