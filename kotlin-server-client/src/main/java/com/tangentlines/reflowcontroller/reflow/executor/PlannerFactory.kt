package com.tangentlines.reflowcontroller.reflow.executor

import com.tangentlines.reflowcontroller.reflow.profile.Phase
import com.tangentlines.reflowcontroller.reflow.profile.PhaseType

object PhasePlannerFactory {
    fun forPhase(phase: Phase): PhasePlanner =
        when (phase.type) {
            PhaseType.HEATING, PhaseType.COOLING -> HeatingCoolingPlanner()
            PhaseType.REFLOW -> ReflowPlanner()
        }
}