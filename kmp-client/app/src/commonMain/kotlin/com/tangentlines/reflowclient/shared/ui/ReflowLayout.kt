package com.tangentlines.reflowclient.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tangentlines.reflow.BuildConfig
import com.tangentlines.reflowclient.shared.ui.components.AnimatedCardForStates
import com.tangentlines.reflowclient.shared.ui.components.ApiCard
import com.tangentlines.reflowclient.shared.ui.components.ConnectionCard
import com.tangentlines.reflowclient.shared.ui.components.IntensityChartSection
import com.tangentlines.reflowclient.shared.ui.components.LogSectionCard
import com.tangentlines.reflowclient.shared.ui.components.ManualControlCard
import com.tangentlines.reflowclient.shared.ui.components.ProfileCard
import com.tangentlines.reflowclient.shared.ui.components.TwoColumnLayout
import com.tangentlines.reflowclient.shared.ui.components.StatusCard
import com.tangentlines.reflowclient.shared.ui.components.TemperatureChartSection
import com.tangentlines.reflowclient.shared.ui.components.mapStatusRows

@Composable
fun ReflowColumn(
    twoColumns: Boolean,
    state: ReflowUiState,
    controller: ReflowUiController,
    headerTitle: String = BuildConfig.NAME,
    onAddNewProfile: (() -> Unit)? = null,
    onEditProfile: ((String) -> Unit)? = null,
) {
    TwoColumnLayout(
        twoColumns = twoColumns,
        headerTitle = headerTitle,
        leftContent = {
            LeftPaneCards(state = state, controller = controller, onAddNewProfile = onAddNewProfile, onEditProfile = onEditProfile)
        },
        rightContent = {
            RightPaneCards(state = state, controller = controller)
        }
    )
}

/** Left pane = API / Connection / Profile(+Manual) / Status */
@Composable
private fun LeftPaneCards(
    state: ReflowUiState,
    controller: ReflowUiController,
    onAddNewProfile: (() -> Unit)? = null,
    onEditProfile: ((String) -> Unit)? = null,
) {
    // API card: visible when API is not reachable, or when we’re not yet on a port
    AnimatedCardForStates(
        state.uiState,
        UiState.API_UNCONNECTED, UiState.PORT_UNCONNECTED,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        ApiCard(
            connected = state.apiConnected,
            host = state.host,
            discoveredHosts = state.discoveredHosts,
            onHostChange = controller::selectHost,
            onScan = controller::refreshHosts,
            onDisconnect = controller::apiDisconnect
        )
    }

    // Connection card: visible ONLY when API reachable
    // and we either need a port or are idle but connected.
    // It is NOT visible in RUNNING (including manual) and not visible when API is unconnected.
    AnimatedCardForStates(
        state.uiState,
        UiState.PORT_UNCONNECTED, UiState.READY,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        ConnectionCard(
            connectedPort = state.selectedPort,
            ports = state.ports,
            onRefresh = controller::refreshPorts,
            onDisconnect = controller::disconnectPort,
            onConnectPortClick = controller::connectPort
        )
    }

    // Running/Ready area (Profile + Manual controls + Status)
    AnimatedCardForStates(
        state.uiState,
        UiState.READY, UiState.RUNNING,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ProfileCard(
                availableProfiles = state.profiles,
                selected = state.selectedProfile,
                onChipClick = controller::startProfile,
                onStop = controller::stopProfile,
                onAdd = onAddNewProfile,
                onLongChipClick = onEditProfile,
                onRefresh = controller::loadProfiles
            )
            if (state.showManualControls) {
                ManualControlCard(
                    temp = state.manualTemp,
                    intensity = state.manualIntensity,
                    onChange = controller::setManual
                )
            }
            StatusCard(mapStatusRows(state.status))
        }
    }
}

/** Right pane = Logs + Charts */
@Composable
private fun RightPaneCards(
    state: ReflowUiState,
    controller: ReflowUiController,
) {
    AnimatedCardForStates(
        state.uiState,
        UiState.READY, UiState.RUNNING,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

            TemperatureChartSection(state.states, state.status.profile)
            IntensityChartSection(state.states, state.status.profile)
            LogSectionCard(states = state.states, lines = state.messages, height = 320.dp, onClear = controller::clearLogs)

        }
    }
}
