package com.tangentlines.reflowclient.shared.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import kotlin.math.max

data class ScrollEdge(val atTop: Boolean, val atBottom: Boolean)

/** Returns true when scrolled within [topOffset] of the top or [bottomOffset] of the bottom. */
@Composable
fun rememberScrollEdges(
    scrollState: ScrollState,
    topOffset: Dp = 0.dp,
    bottomOffset: Dp = 0.dp
): ScrollEdge {
    val density = LocalDensity.current
    val topPx = with(density) { topOffset.roundToPx() }
    val bottomPx = with(density) { bottomOffset.roundToPx() }

    val atTop by remember {
        derivedStateOf { scrollState.value <= topPx }
    }
    val atBottom by remember {
        derivedStateOf {
            val maxVal = scrollState.maxValue
            // If content is smaller than viewport, treat as bottom.
            if (maxVal <= 0) true
            else scrollState.value >= max(0, maxVal - bottomPx)
        }
    }
    return ScrollEdge(atTop, atBottom)
}