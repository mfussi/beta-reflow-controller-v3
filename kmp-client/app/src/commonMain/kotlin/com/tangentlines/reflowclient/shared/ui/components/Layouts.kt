package com.tangentlines.reflowclient.shared.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tangentlines.reflowclient.shared.ui.UiState

/**
 * Convenience wrapper to animate a card (or any block) based on UiState.
 */
@Composable
fun AnimatedCardForStates(
    uiState: UiState,
    vararg showIn: UiState,
    modifier: Modifier = Modifier,
    enter: EnterTransition = fadeIn() + slideInHorizontally(initialOffsetX = { it / 2 }),
    exit: ExitTransition  = fadeOut() + slideOutHorizontally(targetOffsetX   = { it / 2 }),
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    AnimatedVisibility(
        visible = showIn.any { it == uiState },
        modifier = modifier,
        enter = enter,
        exit = exit
    ) {
        content()
    }
}

/**
 * A single responsive layout that arranges [leftContent] and [rightContent]
 * in one or two columns. Right pane is scrollable only in two-column mode.
 */
@Composable
fun TwoColumnLayout(
    twoColumns: Boolean,
    headerTitle: String,
    leftContent: @Composable ColumnScope.() -> Unit,
    rightContent: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        ReflowHeader(headerTitle)
        Spacer(Modifier.height(8.dp))

        if (twoColumns) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                Column(
                    Modifier.weight(1f),
                ) {
                    leftContent()
                }

                Column(
                    Modifier.weight(1f),
                ) {
                    rightContent()
                }
            }
        } else {
            // One column: stack left then right
            Column(
                Modifier.fillMaxWidth(),
            ) {
                leftContent()
                rightContent()
            }
        }
    }
}