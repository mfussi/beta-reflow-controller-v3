package com.tangentlines.reflowcontroller.reflow.executor

import com.tangentlines.reflowcontroller.reflow.executor.planner.SlopeTracker
import kotlin.math.max

data class ManualExecOutput(
    val intensity: Float,
    val targetForUiC: Float?
)

class ManualExecutor(
    private val brake: Float = 0.75f,
    private val slope: SlopeTracker = SlopeTracker()
) {
    private var lastOut: Float = 0f
    private var startedAtMs: Long = 0L
    private var startedTempC: Float = 0f
    private var currentSlope: Float? = null

    fun start(nowMs: Long, startTempC: Float, lastKnownOut: Float = 0f) {
        startedAtMs = nowMs
        startedTempC = startTempC
        lastOut = lastKnownOut
        slope.reset(startTempC, nowMs)
        currentSlope = null
    }

    /**
     * @param targetTempC null means “no target” (heater off)
     * @param baseIntensity operator-selected intensity (0..1)
     */
    fun update(
        dtMs: Long,
        nowMs: Long,
        tMeasC: Float,
        targetTempC: Float?,
        baseIntensity: Float
    ): ManualExecOutput {
        currentSlope = slope.sample(tMeasC, nowMs) // °C/s

        val out = when {
            targetTempC == null -> 0f
            tMeasC < targetTempC - 10f * brake -> (baseIntensity * 1.0f)
            tMeasC < targetTempC - 3f  * brake -> (baseIntensity * 0.5f)
            tMeasC < targetTempC               -> (baseIntensity * 0.3f)
            else                               -> 0f
        }.coerceIn(0f, 1f)

        lastOut = out
        return ManualExecOutput(intensity = out, targetForUiC = targetTempC)
    }

    fun getSlope(): Float? = currentSlope
}