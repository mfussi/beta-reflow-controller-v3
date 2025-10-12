package com.tangentlines.reflowcontroller.reflow.executor.planner

import com.tangentlines.reflowcontroller.reflow.profile.Phase


interface IntensityRegulator {
    fun reset(initialIntensity: Float, maxSlopeCPerS: Float?, safety: SafetyConfig)
    fun compute(
        nowMs: Long,
        dtMs: Long,
        tMeasC: Float,
        dTdtCPerS: Float,
        tPlanC: Float,
        phase: Phase,
        prevIntensity: Float
    ): Float
}