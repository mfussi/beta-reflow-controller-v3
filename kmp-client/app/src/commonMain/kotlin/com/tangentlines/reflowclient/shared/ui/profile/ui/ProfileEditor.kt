package com.tangentlines.reflowclient.shared.ui.profile.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tangentlines.reflowclient.shared.model.EditablePhase
import com.tangentlines.reflowclient.shared.model.EditableProfile
import com.tangentlines.reflowclient.shared.model.PhaseType
import com.tangentlines.reflowclient.shared.ui.profile.logic.EditorState
import com.tangentlines.reflowclient.shared.ui.profile.logic.ProfileEditorController
import com.tangentlines.reflowclient.shared.ui.components.OutlineAccentIconButton

@Composable
fun ProfileEditor(
    state: EditorState,
    controller: ProfileEditorController,
    onSave: (EditableProfile) -> Unit,
    onDuplicate: (EditableProfile) -> Unit,
    onDelete: (EditableProfile) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isEditMode = state.profile.name.isNotBlank()

    Column(modifier.padding(16.dp).fillMaxSize()) {
        // Header row with actions
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isEditMode) state.profile.name else "New Profile",
                style = MaterialTheme.typography.headlineLarge, // larger title
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (isEditMode) {
                // Duplicate & Delete (only in edit mode)
                OutlineAccentIconButton(
                    icon = Icons.Rounded.ContentCopy,
                    contentDescription = "Duplicate",
                    onClick = { controller.duplicateProfile(onDuplicate) }
                )
                Spacer(Modifier.width(8.dp))
                OutlineAccentIconButton(
                    icon = Icons.Rounded.Delete,
                    contentDescription = "Delete",
                    onClick = { controller.deleteProfile(onDelete) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Profile fields
        TextFieldSimple("Profile Name", state.profile.name, onValue = controller::setName)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.profile.description ?: "",
            onValueChange = { controller.setDescription(it) },
            label = { Text("Description (optional)") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4
        )

        FloatField("Reflow Temp Temperature (°C)", state.profile.reflowAt ?: 0f) { v ->
            controller.setReflowAt(v)
        }

        Spacer(Modifier.height(16.dp))

        // Phases list
        Text("Phases", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant // light orange like the rest
            )
        ) {
            if (state.profile.phases.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("No phases yet.")
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    itemsIndexed(state.profile.phases) { index, phase ->
                        PhaseEditorRow(
                            index = index,
                            phase = phase,
                            onUp = { controller.movePhaseUp(index) },
                            onDown = { controller.movePhaseDown(index) },
                            onDuplicate = { controller.duplicatePhase(index) },
                            onRemove = { controller.removePhase(index) },
                            onChange = { controller.updatePhase(index, it) }
                        )
                        if (index != state.profile.phases.lastIndex) Divider()
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = { controller.addPhase() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor   = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add Phase")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Errors
        if (state.errors.isNotEmpty()) {
            Text(
                text = state.errors.joinToString("\n"),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
        }

        // Footer actions
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
            Spacer(Modifier.width(8.dp))
            Button(
                enabled = state.canSave,
                onClick = { controller.save(onSave) }
            ) { Text("Save") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhaseEditorRow(
    index: Int,
    phase: EditablePhase,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onDuplicate: () -> Unit,
    onRemove: () -> Unit,
    onChange: ((EditablePhase) -> EditablePhase) -> Unit
) {
    Column(
        verticalArrangement = spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = phase.name.ifBlank { "phase-${index + 1}" },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            // Positioning controls (up/down) as regular icon buttons
            IconButton(onClick = onUp)   { Icon(Icons.Rounded.KeyboardArrowUp,   contentDescription = "Move up") }
            IconButton(onClick = onDown) { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move down") }
            // Accent-styled duplicate/delete
            OutlineAccentIconButton(
                icon = Icons.Rounded.ContentCopy,
                contentDescription = "Duplicate",
                onClick = onDuplicate
            )
            Spacer(Modifier.width(8.dp))
            OutlineAccentIconButton(
                icon = Icons.Rounded.Delete,
                contentDescription = "Delete",
                onClick = onRemove
            )
        }

        // Name
        TextFieldSimple("Phase Name", phase.name) { newName -> onChange { it.copy(name = newName) } }

        // Type dropdown
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(
                value = phase.type.name.lowercase().replaceFirstChar { it.uppercase() },
                onValueChange = {},
                readOnly = true,
                label = { Text("Phase Type") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor         = MaterialTheme.colorScheme.primary,
                    focusedLabelColor          = MaterialTheme.colorScheme.primary,
                    focusedTrailingIconColor   = MaterialTheme.colorScheme.primary,
                    unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    cursorColor                = MaterialTheme.colorScheme.primary
                )
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                PhaseType.entries.forEach { t ->
                    DropdownMenuItem(
                        text = { Text(t.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        onClick = { onChange { it.copy(type = t) }; expanded = false }
                    )
                }
            }
        }

        // Target temperature
        FloatField("Target Temperature (°C)", phase.targetTemperature) { v ->
            onChange { it.copy(targetTemperature = v) }
        }

        IntField("Time (s)", phase.time) { v -> onChange { it.copy(time = v) } }

        IntField("Hold For (s)", phase.holdFor) { v -> onChange { it.copy(holdFor = v) } }


        FloatField("Initial Intensity (0..1)", phase.initialIntensity ?: 0.5f) { v ->
            onChange { it.copy(initialIntensity = v.coerceIn(0f, 1f)) }
        }

        FloatField("Max Slope (°C/s)", phase.maxSlope ?: 2f) { v ->
            onChange { it.copy(maxSlope = v.coerceAtLeast(0f)) }
        }

        // Max Temperature (explicit; standalone)
        FloatField("Max Temperature (°C)", phase.maxTemperature ?: 0f) { v ->
            onChange { it.copy(maxTemperature = v.takeIf { !it.isNaN() && it > 0f }) }
        }
    }
}
