package com.tangentlines.reflowclient.shared.ui.profile.logic

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.tangentlines.reflowclient.shared.model.EditablePhase
import com.tangentlines.reflowclient.shared.model.EditableProfile
import com.tangentlines.reflowclient.shared.model.PhaseType
import com.tangentlines.reflowclient.shared.model.ReflowProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditorState(
    val profile: EditableProfile = EditableProfile(),
    val errors: List<String> = emptyList(),
    val isDirty: Boolean = false,
    val canSave: Boolean = false
)

interface ProfileEditorController {
    fun resetForNew()
    val state: StateFlow<EditorState>
    fun setName(name: String)
    fun setDescription(desc: String)
    fun addPhase()
    fun duplicatePhase(index: Int)
    fun removePhase(index: Int)
    fun movePhaseUp(index: Int)
    fun movePhaseDown(index: Int)
    fun updatePhase(index: Int, update: (EditablePhase) -> EditablePhase)
    fun save(onSaved: (EditableProfile) -> Unit)
    fun duplicateProfile(onDuplicated: (EditableProfile) -> Unit)
    fun deleteProfile(onDelete: (EditableProfile) -> Unit)
    fun loadForEdit(profile: ReflowProfile)
    fun loadForEdit(profile: EditableProfile)
}

@Composable
fun rememberProfileEditorController(
    initial: EditableProfile? = null,
    snackbar: SnackbarHostState? = null
): ProfileEditorController {
    val scope = rememberCoroutineScope()
    val _state = remember { MutableStateFlow(EditorState(profile = initial ?: EditableProfile())) }

    fun recalc(profile: EditableProfile, dirty: Boolean): EditorState {
        val issues = validateProfile(profile)
        return EditorState(
            profile = profile,
            errors = issues,
            isDirty = dirty,
            canSave = issues.isEmpty() && profile.name.isNotBlank()
        )
    }

    val controller = object : ProfileEditorController {

        override val state: StateFlow<EditorState> = _state

        override fun resetForNew() {
            _state.update { recalc(EditableProfile(), dirty = false) }
        }

        override fun setName(name: String) {
            _state.update { recalc(it.profile.copy(name = name), dirty = true) }
        }

        override fun setDescription(desc: String) {
            _state.update { recalc(it.profile.copy(description = desc.ifBlank { null }), dirty = true) }
        }

        override fun addPhase() {
            _state.update {
                val next = it.profile.phases + defaultPhase(it.profile.phases.size)
                recalc(it.profile.copy(phases = next), dirty = true)
            }
        }

        override fun duplicatePhase(index: Int) {
            _state.update {
                val list = it.profile.phases.toMutableList()
                val copy = list.getOrNull(index)?.copy(name = list[index].name + " (copy)") ?: return@update it
                list.add(index + 1, copy)
                recalc(it.profile.copy(phases = list), dirty = true)
            }
        }

        override fun removePhase(index: Int) {
            _state.update {
                val list = it.profile.phases.toMutableList()
                if (index !in list.indices) return@update it
                list.removeAt(index)
                recalc(it.profile.copy(phases = list), dirty = true)
            }
        }

        override fun movePhaseUp(index: Int) {
            _state.update {
                val list = it.profile.phases.toMutableList()
                if (index <= 0 || index >= list.size) return@update it
                val tmp = list[index - 1]; list[index - 1] = list[index]; list[index] = tmp
                recalc(it.profile.copy(phases = list), dirty = true)
            }
        }

        override fun movePhaseDown(index: Int) {
            _state.update {
                val list = it.profile.phases.toMutableList()
                if (index < 0 || index >= list.size - 1) return@update it
                val tmp = list[index + 1]; list[index + 1] = list[index]; list[index] = tmp
                recalc(it.profile.copy(phases = list), dirty = true)
            }
        }

        override fun updatePhase(index: Int, update: (EditablePhase) -> EditablePhase) {
            _state.update {
                val list = it.profile.phases.toMutableList()
                if (index !in list.indices) return@update it
                list[index] = update(list[index])
                recalc(it.profile.copy(phases = list), dirty = true)
            }
        }

        override fun save(onSaved: (EditableProfile) -> Unit) {
            val p = _state.value.profile
            val issues = validateProfile(p)
            if (issues.isNotEmpty()) {
                snackbar?.let { s -> scope.launch { s.showSnackbar(issues.joinToString("\n")) } }
                return
            }
            onSaved(p)
            _state.update { it.copy(isDirty = false) }
        }

        override fun duplicateProfile(onDuplicated: (EditableProfile) -> Unit) {
            val names = emptySet<String>() // caller can pass if needed
            val base = _state.value.profile.name.ifBlank { "Profile" }
            val newName = suggestedProfileName(base, names)
            val dup = _state.value.profile.copy(name = newName)
            onDuplicated(dup)
        }

        override fun deleteProfile(onDelete: (EditableProfile) -> Unit) {
            onDelete(_state.value.profile)
        }

        override fun loadForEdit(editable: EditableProfile) {

            val issues = mutableListOf<String>()
            if (editable.name.isBlank()) issues += "Name is required."
            editable.phases.forEachIndexed { idx, ph ->
                val n = idx + 1
                if (ph.targetTemperature.isNaN()) issues += "Phase $n: target temperature is invalid."
                if (ph.time < 0)                 issues += "Phase $n: time must be ≥ 0."
                if (ph.holdFor < 0)              issues += "Phase $n: hold duration must be ≥ 0."
                ph.initialIntensity?.let { if (it !in 0f..1f) issues += "Phase $n: initial intensity must be between 0 and 1." }
                ph.maxSlope?.let        { if (it < 0f)       issues += "Phase $n: max slope must be ≥ 0." }
                ph.maxTemperature?.let  { if (it <= 0f)      issues += "Phase $n: max temperature must be > 0." }
            }
            val canSave = issues.isEmpty()

            _state.update {
                it.copy(
                    profile   = editable,
                    errors    = issues,
                    canSave   = canSave
                )
            }

        }

        override fun loadForEdit(profile: ReflowProfile) {

            // Map runtime model -> editable model
            val editable = EditableProfile(
                name = profile.name,
                description = profile.description,
                phases = profile.phases.map { p ->
                    EditablePhase(
                        name = p.name,
                        type = when (p.type) {
                            PhaseType.HEATING -> PhaseType.HEATING
                            PhaseType.REFLOW  -> PhaseType.REFLOW
                            PhaseType.COOLING -> PhaseType.COOLING
                        },
                        targetTemperature = p.targetTemperature,
                        time             = p.time,
                        holdFor          = p.holdFor,
                        initialIntensity = p.initialIntensity,
                        maxSlope         = p.maxSlope,
                        maxTemperature   = p.maxTemperature
                    )
                }
            )
            loadForEdit(editable)

        }

    }

    return controller
}