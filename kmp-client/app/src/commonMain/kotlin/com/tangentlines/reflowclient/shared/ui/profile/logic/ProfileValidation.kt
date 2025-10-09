package com.tangentlines.reflowclient.shared.ui.profile.logic

import com.tangentlines.reflowclient.shared.model.EditablePhase
import com.tangentlines.reflowclient.shared.model.EditableProfile
import com.tangentlines.reflowclient.shared.model.PhaseType

/**
 * Returns a list of human-readable issues. Empty list means OK.
 */
fun validateProfile(p: EditableProfile): List<String> {
    val issues = mutableListOf<String>()
    if (p.name.isBlank()) issues += "Profile name must not be empty."
    if (p.phases.isEmpty()) issues += "Profile must contain at least one phase."

    p.phases.forEachIndexed { idx, ph ->
        val label = ph.name.ifBlank { "phase-${idx + 1}" }
        if (ph.name.isBlank()) issues += "$label: name must not be blank."
        if (ph.targetTemperature !in 0f..300f) issues += "$label: target temperature must be between 0 and 300 °C."
        if (ph.time < 0) issues += "$label: time must be ≥ 0 seconds."
        if (ph.holdFor < 0) issues += "$label: hold_for must be ≥ 0 seconds."
        ph.initialIntensity?.let {
            if (it !in 0f..1f) issues += "$label: initial intensity must be between 0.0 and 1.0."
        }
        ph.maxSlope?.let {
            if (it < 0f) issues += "$label: max slope must be ≥ 0 °C/s."
        }
        ph.maxTemperature?.let {
            if (it !in 0f..350f) issues += "$label: max temperature must be between 0 and 350 °C."
            if (ph.type == PhaseType.REFLOW && it < ph.targetTemperature) {
                issues += "$label: max temperature should not be lower than target threshold."
            }
        }
        if (ph.type == PhaseType.REFLOW && ph.holdFor <= 0) {
            issues += "$label: reflow phases usually require a positive hold_for time."
        }
    }

    return issues
}

fun defaultPhase(index: Int = 0): EditablePhase =
    EditablePhase(
        name = "phase-${index + 1}",
        type = PhaseType.HEATING,
        targetTemperature = 150f,
        time = 60,
        holdFor = 0,
        initialIntensity = 0.5f,
        maxSlope = 2.0f,
        maxTemperature = null
    )

fun suggestedProfileName(base: String, existing: Set<String>): String {
    if (base !in existing) return base
    var i = 2
    while ("$base ($i)" in existing) i++
    return "$base ($i)"
}