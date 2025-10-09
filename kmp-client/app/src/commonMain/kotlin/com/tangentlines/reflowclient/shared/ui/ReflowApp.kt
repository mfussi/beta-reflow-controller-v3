package com.tangentlines.reflowclient.shared.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.tangentlines.reflow.BuildConfig
import com.tangentlines.reflowclient.shared.ui.components.CoolingBeepOverlay
import com.tangentlines.reflowclient.shared.ui.components.beep
import com.tangentlines.reflowclient.shared.ui.components.rememberScrollEdges
import com.tangentlines.reflowclient.shared.ui.profile.logic.rememberProfileEditorController
import com.tangentlines.reflowclient.shared.ui.profile.ui.ProfileEditorScreen
import com.tangentlines.reflowclient.shared.ui.utils.launchSafely
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Composable
fun ReflowApp(
    twoColumnOverride: Boolean? = null,
    port: Int = 8090,
) {
    ReflowTheme {
        val snackbarHostState = remember { SnackbarHostState() }
        val controller = rememberReflowController(
            initialPort = port,
            snackbarHostState = snackbarHostState
        )
        val s by controller.state.collectAsState()

        val showEditor = remember { mutableStateOf(false) }

        // Editor controller from the editor package (assumes it uses ReflowProfile directly)
        // If your editor uses EditableProfile, just map to/from ReflowProfile in the onSave below.
        val profileEditor = rememberProfileEditorController(
            initial = null,               // start with a fresh profile
            snackbar = snackbarHostState
        )

        Scaffold(
            snackbarHost = {
                SnackbarHost(snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        contentColor = Color.White,
                        containerColor = Color(0xFF323232)
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets()
        ) { _ ->

            val top = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
            val bottom =  with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }

            Box(Modifier.fillMaxSize()) {

                val scope = rememberCoroutineScope()
                val scrollState = rememberScrollState()
                val edges = rememberScrollEdges(scrollState, topOffset = 16.dp, bottomOffset = 24.dp)

                Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {

                    Spacer(Modifier.height(top))
                    Spacer(Modifier.height(16.dp))

                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val width = this.maxWidth

                        Column(modifier = Modifier.fillMaxWidth()) {

                            ReflowColumn(
                                twoColumns = twoColumnOverride ?: (width >= 900.dp),
                                state = s,
                                controller = controller,
                                headerTitle = BuildConfig.NAME,
                                onAddNewProfile = {
                                    profileEditor.resetForNew() // helper in the editor controller
                                    showEditor.value = true
                                },
                                onEditProfile = { name ->
                                    if(!name.equals("manual", true)) {
                                        scope.launchSafely(snackbarHostState) {

                                            val profile =
                                                suspendCoroutine { cont -> controller.getProfileByName(name, { cont.resume(it) }) }
                                            if (profile == null) {
                                                snackbarHostState.showSnackbar("Profile \"$name\" not found.")
                                                return@launchSafely
                                            }

                                            profileEditor.loadForEdit(profile)
                                            showEditor.value = true

                                        }
                                    }
                                }
                            )

                        }

                    }

                    Spacer(Modifier.height(bottom))
                    Spacer(Modifier.height(16.dp))

                }

                CoolingBeepOverlay(
                    active = s.showBeep,
                    currentTemp = s.status.temperature,
                    targetTemp = s.status.targetTemperature,
                    onBeep = { beep() },
                    onDismiss = { },
                    modifier = Modifier.padding(top = top, bottom = bottom)
                )

                if (s.busy > 0) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = top)
                            .align(Alignment.TopCenter)
                    )
                }

                ProfileEditorScreen(
                    showEditor = showEditor,
                    profileEditor = profileEditor,
                    padding = top to bottom,
                    snackbarHostState = snackbarHostState,
                    controller = controller,
                )

                AnimatedFakeStatusBar(
                    visible = !edges.atTop,
                    height = top,
                    modifier = Modifier.align(Alignment.TopCenter)
                )

                AnimatedFakeStatusBar(
                    visible = !edges.atBottom,
                    height = bottom,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )

            }

        }
    }
}

@Composable
private fun AnimatedFakeStatusBar(
    visible : Boolean,
    height : Dp,
    modifier: Modifier = Modifier
) {

    if(height == 0.dp) {
        return
    }

    val statusBarColor = lerp(MaterialTheme.colorScheme.primary.copy(alpha = 1.0f), Color.White, 0.75f)

    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxWidth(),
        enter = fadeIn(tween(durationMillis = 1000, easing = LinearEasing)),
        exit = fadeOut(tween(durationMillis = 1000, easing = LinearEasing)),
        label = "Statusbar",
        content = {
            Box(modifier = modifier.fillMaxWidth().height(height).background(statusBarColor))
        }
    )

}