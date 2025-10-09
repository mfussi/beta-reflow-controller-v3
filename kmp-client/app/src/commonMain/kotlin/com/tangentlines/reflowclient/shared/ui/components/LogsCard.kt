package com.tangentlines.reflowclient.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tangentlines.reflowclient.shared.model.LogMessage
import com.tangentlines.reflowclient.shared.model.State
import com.tangentlines.reflowclient.shared.ui.utils.unixMsToHHmm
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Composable
fun LogSectionCard(
    lines: List<LogMessage>,
    states: List<State>,
    height: Dp,
    onClear: () -> Unit
) {
    // Pretty JSON for sharing/copy; recompute only when data changes
    @Serializable
    data class Payload(val logs: List<LogMessage>, val states: List<State>)

    val shareJson by remember(lines, states) {
        mutableStateOf(
            Json {
                prettyPrint = true
                encodeDefaults = true
                ignoreUnknownKeys = true
            }.encodeToString(Payload(lines, states))
        )
    }

    val clipboard = LocalClipboardManager.current
    val actions = listOfNotNull(
        if(lines.isNotEmpty()) ActionButton("Clear", Icons.Rounded.DeleteSweep, onClear) else null,
        if(lines.isNotEmpty()) ActionButton("Copy", Icons.Rounded.ContentCopy, { clipboard.setText(AnnotatedString(shareJson)) }) else null
    )

    SectionCard(
        title = "Logs",
        description = "Chronological record of system events and readings. Use it for troubleshooting and auditing actions during a session.",
        actions = actions
    ) {
        val listState = rememberLazyListState()

        // We render newest at the BOTTOM (natural order), so we want to
        // start at the bottom and stay there only if the user didn't scroll up.
        val totalCount = lines.size

        // Track whether viewport is currently at the bottom
        val isAtBottom by remember {
            derivedStateOf {
                val info = listState.layoutInfo
                if (info.totalItemsCount == 0) return@derivedStateOf true
                val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                lastVisible >= info.totalItemsCount - 1
            }
        }

        // Snap to bottom on first composition
        LaunchedEffect(totalCount) {
            if (totalCount > 0 && isAtBottom) {
                listState.scrollToItem(totalCount - 1)
            }
        }

        // If new items arrive and the user is at bottom, keep them anchored to bottom
        LaunchedEffect(totalCount) {
            if (totalCount > 0 && isAtBottom) {
                listState.animateScrollToItem(totalCount - 1)
            }
        }

        // Detect manual user scrolls to avoid auto-scrolling when they’re browsing history
        var userAnchoredToBottom by remember { mutableStateOf(true) }
        LaunchedEffect(listState) {
            snapshotFlow {
                val info = listState.layoutInfo
                val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                lastVisible >= info.totalItemsCount - 1
            }
                .distinctUntilChanged()
                .collectLatest { atBottom -> userAnchoredToBottom = atBottom }
        }

        LaunchedEffect(lines.size) {
            if (lines.isNotEmpty() && userAnchoredToBottom) {
                listState.animateScrollToItem(lines.lastIndex)
            }
        }

        if(lines.isNotEmpty()){
            // Brighter inner panel so logs stand out inside the card
            val panelShape = RoundedCornerShape(12.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(height)
                    .clip(panelShape)
                    .padding(0.dp)
            ) {
                // Apply the brighter background as padding content, not the whole card
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                        .clip(panelShape)
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.50f))
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Assumes lines are oldest -> newest (newest at bottom)
                        items(
                            items = lines,
                            key = { "${it.time}:${it.message.hashCode()}" }
                        ) { log ->
                            Text(
                                text = "${unixMsToHHmm(log.time)}: ${log.message}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    // Fixed-width font just for log entries
                                    fontFamily = FontFamily.Monospace
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
