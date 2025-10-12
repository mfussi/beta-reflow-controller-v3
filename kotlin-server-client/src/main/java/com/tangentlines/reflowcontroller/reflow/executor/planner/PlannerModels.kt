package com.tangentlines.reflowcontroller.reflow.executor.planner

data class SafetyConfig(
    val absoluteMaxTemperature: Float = 260f,
    val sensorFaultHysteresisS: Int = 3
)

data class ProfileContext(
    val liquidusTemperatureC: Float,
    val safety: SafetyConfig = SafetyConfig()
)

data class PlannerAux(
    val talMs: Long = 0L,           // time above phase reflow temp (or liquidus if you choose)
    val hitMinTemp: Boolean = false // for reflow band: min_temperature hit at least once
)

data class PlanResult(
    val tPlanC: Float,          // current planned temperature
    val phaseCompleted: Boolean,
    val aux: PlannerAux = PlannerAux()
)