package com.tangentlines.reflowcontroller.reflow.executor.planner

import com.tangentlines.reflowcontroller.reflow.profile.Phase
import com.tangentlines.reflowcontroller.reflow.profile.PhaseType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class DefaultIntensityRegulator(
    private val kpHeat: Float = 0.010f,
    private val kpReflow: Float = 0.009f,
    private val kBand: Float = 0.004f,
    private val dwellMs: Long = 2000L,
    private val slopeDampen: Float = 0.75f
) : IntensityRegulator {

    private var safety: SafetyConfig = SafetyConfig()
    private var uBias: Float = 0.5f
    private var maxSlope: Float? = null
    private var lastChangeAt: Long = 0L
    private var lastOut: Float = 0f

    override fun reset(initialIntensity: Float, maxSlopeCPerS: Float?, safety: SafetyConfig) {
        this.uBias = initialIntensity.coerceIn(0f, 1f)
        this.maxSlope = maxSlopeCPerS
        this.safety = safety
        lastChangeAt = 0L
        lastOut = uBias
    }

    override fun compute(
        nowMs: Long,
        dtMs: Long,
        tMeasC: Float,
        dTdtCPerS: Float,
        tPlanC: Float,
        phase: Phase,
        prevIntensity: Float
    ): Float {

        // Safety hard cap
        if (tMeasC >= safety.absoluteMaxTemperature) return 0f

        val err = tPlanC - tMeasC
        var u = when (phase.type) {
            PhaseType.HEATING -> uBias + kpHeat * err
            PhaseType.REFLOW  -> uBias + kpReflow * err
            PhaseType.COOLING -> 0f
        }

        // Slope clamp
        maxSlope?.let { sMax ->
            if (dTdtCPerS > sMax) u *= slopeDampen
        }

        // Phase-specific bounds / hysteresis
        when (phase.type) {
            PhaseType.HEATING -> {
                val nearPlan = tMeasC >= (tPlanC - 0.5f)
                if (nearPlan) u = min(u, 0.35f)
                phase.maxTemperature?.let { maxT ->
                    if (tMeasC >= maxT - 0.5f) u = 0f
                }
            }
            PhaseType.REFLOW -> {
                val minT = max(phase.minTemperature ?: tPlanC, phase.targetTemperature)
                val maxT = phase.maxTemperature ?: (minT + 25f)

                if (tMeasC >= maxT - 0.3f) {
                    u = 0f
                } else if (tMeasC < minT) {
                    u = max(u, uBias + kpReflow * (minT - tMeasC))
                } else {
                    val mid = (minT + maxT) / 2f
                    u += kBand * (mid - tMeasC)
                }
            }
            PhaseType.COOLING -> { /* u already 0 */ }
        }

        // Dwell to prevent chatter
        u = u.coerceIn(0f, 1f)
        if (lastChangeAt != 0L && (nowMs - lastChangeAt) < dwellMs) {
            return lastOut
        }

        if (abs(u - lastOut) > 1e-3f) {
            lastChangeAt = nowMs
            lastOut = u
        }
        return u
    }
}