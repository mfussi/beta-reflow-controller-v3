package com.tangentlines.reflowclient.shared.ui.profile.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.unit.Dp
import com.tangentlines.reflowclient.shared.model.EditableProfile
import com.tangentlines.reflowclient.shared.ui.ReflowUiController
import com.tangentlines.reflowclient.shared.ui.profile.logic.ProfileEditorController
import com.tangentlines.reflowclient.shared.ui.utils.launchSafely
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ProfileEditorScreen(
    showEditor: MutableState<Boolean>,
    profileEditor: ProfileEditorController,
    padding: Pair<Dp, Dp>,
    snackbarHostState: SnackbarHostState,
    controller: ReflowUiController,
    scope: CoroutineScope = controllerScope()
) {

    if (showEditor.value) {

        BackHandler(enabled = true) {
            showEditor.value = false
        }

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.first, bottom = padding.second),
            color = MaterialTheme.colorScheme.background
        ) {
            var saving by remember { mutableStateOf(false) }

            Column(modifier = Modifier.fillMaxWidth()) {

                if (saving) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }

                ProfileEditor(
                    state = profileEditor.state.collectAsState().value,
                    controller = profileEditor,
                    onSave = { profile: EditableProfile ->
                        // Validate -> Save -> refresh profiles -> close
                        saving = true
                        scope.launchSafely(snackbarHostState) {

                            val check = suspendCoroutine { cont -> controller.validateProfile(profile, { cont.resume(it)}) }

                            if (!check.valid) {
                                snackbarHostState.showSnackbar(
                                    "Please fix: ${check.issues.joinToString("; ")}"
                                )
                                saving = false
                                return@launchSafely
                            }
                            val saved = suspendCoroutine { cont -> controller.saveProfile(profile, fileName = "${profile.name}.json", { cont.resume(it) }) }
                            if (!saved.success) {
                                snackbarHostState.showSnackbar("Save failed.")
                                saving = false
                                return@launchSafely
                            }
                            // Refresh profiles list through existing flow
                            controller.apiConnect() // pulls /profiles again
                            showEditor.value = false
                            saving = false
                            snackbarHostState.showSnackbar("Profile saved.")
                        }
                    },
                    onDuplicate = { dup: EditableProfile ->

                        val copy = dup.copy(name = dedupeName(dup.name, suffix = "-copy"))
                        profileEditor.loadForEdit(copy)

                        scope.launchSafely(snackbarHostState) {
                            snackbarHostState.showSnackbar("Duplicated as ${copy.name}.")
                        }

                    },
                    onDelete = { doomed: EditableProfile ->
                        scope.launchSafely(snackbarHostState) {
                            val success = suspendCoroutine { cont -> controller.deleteProfile(doomed, { cont.resume(it.success)}) }
                            if(success) {
                                showEditor.value = false
                            } else {
                                snackbarHostState.showSnackbar("Deletion failed")
                            }
                        }
                    },
                    onCancel = { showEditor.value = false },
                )
            }

        }

    }

}

@Composable private fun controllerScope() = rememberCoroutineScope()

private fun dedupeName(base: String, suffix: String): String =
    buildString {
        append(base.trim().ifEmpty { "Profile" })
        append(suffix)
    }
