package com.tangentlines.reflowcontroller.reflow.executor


class SlopeTracker {
    private var lastTemp: Float? = null
    private var lastMs: Long = 0L

    fun reset(tempC: Float, timeMs: Long) {
        lastTemp = tempC
        lastMs = timeMs
    }

    /** returns °C/s (unsmoothed) */
    fun sample(tempC: Float, timeMs: Long): Float {
        val prevT = lastTemp
        val prevMs = lastMs
        lastTemp = tempC
        lastMs = timeMs
        if (prevT == null || prevMs == 0L) return 0f
        val dt = (timeMs - prevMs).coerceAtLeast(1L) / 1000f
        return (tempC - prevT) / dt
    }
}