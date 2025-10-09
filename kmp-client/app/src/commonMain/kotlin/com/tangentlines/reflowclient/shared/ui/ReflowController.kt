package com.tangentlines.reflowclient.shared.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateListOf
import com.tangentlines.reflowclient.shared.model.EditableProfile
import com.tangentlines.reflowclient.shared.model.toWire
import com.tangentlines.reflowclient.shared.discovery.Discovery
import com.tangentlines.reflowclient.shared.model.*
import com.tangentlines.reflowclient.shared.model.State
import com.tangentlines.reflowclient.shared.net.ReflowApi
import com.tangentlines.reflowclient.shared.ui.utils.BuildSecretsDefaults.clientKey
import com.tangentlines.reflowclient.shared.ui.utils.launchSafely
import io.ktor.util.date.getTimeMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

enum class UiState { API_UNCONNECTED, PORT_UNCONNECTED, READY, RUNNING }

data class ValidationResult(val valid: Boolean, val issues: List<String> = emptyList())
data class SaveResult(val success: Boolean)
data class DeleteResult(val success: Boolean, val message: String? = null)

data class ReflowUiState(
    val uiState: UiState = UiState.API_UNCONNECTED,

    // API / host
    val apiConnected: Boolean = false,          // user "connected" intent (local)
    val apiReachable: Boolean = false,          // actual reachability from /status
    val host: String = "http://localhost:8090",
    val discoveredHosts: List<String> = emptyList(),

    // Ports
    val ports: List<String> = emptyList(),
    val selectedPort: String? = null,

    // Profiles
    val profiles: List<String> = emptyList(),   // includes "Manual" + local + remote
    val selectedProfile: String? = null,        // "manual" or profile name when running

    // Manual
    val manualTemp: Float = 150f,
    val manualIntensity: Float = 0.5f,
    val showManualControls: Boolean = false,

    // Status & logs
    val status: StatusDto = StatusDto(),
    val messages: List<LogMessage> = emptyList(),
    val states: List<State> = emptyList(),

    // UI helpers
    val showBeep: Boolean = false,
    val busy: Int = 0
)

interface ReflowUiController {
    val state: StateFlow<ReflowUiState>

    // API / discovery
    fun refreshHosts()
    fun selectHost(host: String)
    fun apiConnect()
    fun apiDisconnect()

    // Ports
    fun refreshPorts()
    fun connectPort(port: String)
    fun disconnectPort()

    // Profiles
    fun startProfile(name: String)
    fun stopProfile()
    fun loadProfiles()

    // Manual adjustments (fast, no busy spinner)
    fun setManual(temp: Float, intensity: Float)

    fun share(data : String)
    fun clearLogs()

    fun validateProfile(profile: EditableProfile, callback: (ValidationResult) -> Unit)
    fun saveProfile(profile: EditableProfile, fileName: String, callback: (SaveResult) -> Unit)
    fun deleteProfile(profile: EditableProfile, callback: (DeleteResult) -> Unit)
    fun getProfileByName(name: String, callback: (ReflowProfile?) -> Unit)

}

@Composable
fun rememberReflowController(
    initialPort: Int = 8090,
    snackbarHostState: SnackbarHostState
): ReflowUiController {
    val scope = rememberCoroutineScope()

    // Internal mutable flow state
    val _state = remember {
        MutableStateFlow(ReflowUiState(host = "http://localhost:$initialPort"))
    }

    val remoteProfiles = remember { mutableStateListOf<ReflowProfile>() }

    // Build API from current host
    fun api(): ReflowApi = ReflowApi(_state.value.host,  { clientKey })
    fun baseProfiles(): List<String> = (listOf("Manual")).distinct()

    // ---- Pure derivation from StatusDto + local caches (+ apiConnected gate)
    fun recomputeComputedFields(st: ReflowUiState, status: StatusDto = st.status): ReflowUiState {
        val apiConnected = st.apiConnected
        val reachable    = st.apiReachable
        val connected    = status.connected == true
        val isRunning    = status.running == true          // manual OR profile

        val ui = when {
            !apiConnected -> UiState.API_UNCONNECTED       // ← local gate wins for UI
            !reachable    -> UiState.API_UNCONNECTED
            !connected    -> UiState.PORT_UNCONNECTED
            isRunning     -> UiState.RUNNING
            else          -> UiState.READY
        }

        val selectedProfile: String? =
            when {
                status.running == true && status.mode?.equals("manual", true) == true -> "Manual"
                status.running == true && status.profile?.name != null               -> status.profile.name
                else -> null
            }

        val names = (listOf("Manual") + remoteProfiles.map { it.name }).distinct()

        val showBeep =
            status.profile != null &&
                    status.phase != null &&
                    status.profile?.phases?.getOrNull(status.phase ?: 0)?.type == PhaseType.COOLING

        val showManualControls =
            (selectedProfile?.equals("manual", true) == true) ||
                    (status.mode?.equals("manual", true) == true)

        // Mirror server values for manual when available
        val manualTemp      = status.targetTemperature ?: st.manualTemp
        val manualIntensity = status.intensity          ?: st.manualIntensity

        return st.copy(
            uiState            = ui,
            selectedProfile    = selectedProfile,
            profiles           = names,
            showManualControls = showManualControls,
            showBeep           = showBeep,
            manualTemp         = manualTemp,
            manualIntensity    = manualIntensity,
            selectedPort       = status.port             // reflect server
        )
    }

    // ---- Busy wrapper for user actions
    fun <T> userCall(after: (suspend () -> Unit)? = null, block: suspend () -> T?) {
        scope.launchSafely(snackbarHostState) {
            _state.update { it.copy(busy = it.busy + 1) }
            try {
                val result = block()
                after?.invoke()
                result
            } finally {
                _state.update { it.copy(busy = (it.busy - 1).coerceAtLeast(0)) }
            }
        }
    }

    // ---- Central refresh: status() is the source of truth
    suspend fun refreshAll() {
        // Respect local API gate: when user disconnected, don't ping server
        if (!_state.value.apiConnected) {
            _state.update { it.copy(apiReachable = false).let(::recomputeComputedFields) }
            return
        }

        val st = runCatching { api().status() }.getOrNull()
        if (st == null) {
            _state.update { it.copy(apiReachable = false).let(::recomputeComputedFields) }
            return
        }

        _state.update { it.copy(apiReachable = true, status = st) }

        if (st.connected == true) {
            runCatching { api().logStates().states }.getOrNull()?.let { list ->
                _state.update { if (it.states != list) it.copy(states = list) else it }
            }
            runCatching { api().logMessages().messages.distinctBy { it.time }.takeLast((1024*8)) }.getOrNull()?.let { list ->
                _state.update { if (it.messages != list) it.copy(messages = list) else it }
            }
        }

        _state.update { recomputeComputedFields(it, st) }
    }

    // ---- The controller
    val controller = remember {
        object : ReflowUiController {
            override val state: StateFlow<ReflowUiState> = _state

            // Discovery
            override fun refreshHosts() = userCall {
                val hosts = Discovery?.scan()?.map { "http://${it.host}:${it.port}" } ?: emptyList()
                _state.update { it.copy(discoveredHosts = hosts) }
            }

            override fun selectHost(host: String) {
                _state.update { it.copy(host = host) }
                apiConnect()
            }

            // API gate
            override fun apiConnect() = userCall(after = { refreshAll() }) {
                val st = api().status(force = true)
                _state.update {
                    it.copy(
                        apiConnected = true,              // ← set local gate true
                        apiReachable = true,
                        status = st
                    )
                }
                runCatching { api().ports().ports }
                    .onSuccess { ports -> _state.update { it.copy(ports = ports) } }
                runCatching { api().profiles().profiles }
                    .onSuccess { remote ->
                        remoteProfiles.clear(); remoteProfiles.addAll(remote)
                    }
                _state.update { recomputeComputedFields(it) }
            }

            override fun apiDisconnect() = userCall {
                // Clear caches that depend on the server
                remoteProfiles.clear()

                // Preserve host and discovered hosts for convenience
                val preservedHost = _state.value.host
                val preservedDiscovered = _state.value.discoveredHosts

                // Fully reset UI state; status() is no longer the source of truth after disconnect
                val cleared = ReflowUiState(
                    uiState         = UiState.API_UNCONNECTED,
                    apiConnected    = false,
                    apiReachable    = false,
                    host            = preservedHost,
                    discoveredHosts = preservedDiscovered,

                    // Ports
                    ports           = emptyList(),
                    selectedPort    = null,

                    // Profiles (keep Manual + local; remote is cleared)
                    profiles        = baseProfiles(),
                    selectedProfile = null,

                    // Manual defaults
                    manualTemp      = 150f,
                    manualIntensity = 0.5f,
                    showManualControls = false,

                    // Status & logs cleared
                    status          = StatusDto(),
                    messages        = emptyList(),
                    states          = emptyList(),

                    // UI helpers
                    showBeep        = false,
                    busy            = 0
                )

                // Set the cleared state (no recompute needed, but safe if you prefer):
                _state.value = cleared
            }

            // Ports
            override fun refreshPorts() = userCall(after = { refreshAll() }) {
                runCatching { api().ports().ports }.onSuccess { ports ->
                    _state.update { it.copy(ports = ports) }
                }
            }

            override fun connectPort(port: String) = userCall(after = { refreshAll() }) {
                api().connect(port)
            }

            override fun disconnectPort() = userCall(after = { refreshAll() }) {
                api().disconnect()
            }

            // Profiles
            override fun startProfile(name: String) = userCall(after = { refreshAll() }) {
                when {
                    name.equals("manual", true) -> {
                        val s = _state.value
                        api().startManual(s.manualTemp, s.manualIntensity)
                    }
                    remoteProfiles.any { it.name == name } -> {
                        api().startByName(name)
                    }
                    else -> error("Profile '$name' not found")
                }
            }

            override fun stopProfile() = userCall(after = { refreshAll() }) {
                api().stop()
            }

            override fun loadProfiles() = userCall(after = { refreshAll() }) {
                val profiles = api().profiles().profiles
                remoteProfiles.clear();
                remoteProfiles.addAll(profiles)
            }

            override fun validateProfile(
                profile: EditableProfile,
                callback: (ValidationResult) -> Unit
            ) = userCall {
                val res = api().validateProfile(profile.toWire()) // ReflowApi call (see section 3)
                callback.invoke(ValidationResult(valid = res.valid, issues = res.issues))
            }

            override fun saveProfile(
                profile: EditableProfile,
                fileName: String,
                callback: (SaveResult) -> Unit
            ) = userCall {
                val res = api().saveProfile(profile.toWire(), fileName) // ReflowApi call
                // After a successful save you likely want the fresh list next time
                if (res.saved) runCatching { api().profiles() }.onSuccess { pr ->
                    remoteProfiles.clear(); remoteProfiles.addAll(pr.profiles)
                    _state.update { recomputeComputedFields(it) }
                }
                callback.invoke(SaveResult(success = res.saved))
            }

            override fun deleteProfile(
                profile: EditableProfile,
                callback: (DeleteResult) -> Unit
            ) = userCall {
                // If your server exposes deletion; otherwise return a graceful failure
                callback.invoke(try {
                    val res = api().deleteProfile(profile.name.trim()) // ReflowApi call; see section 3
                    if (res) {
                        // prune local cache
                        remoteProfiles.removeAll { it.name.equals(profile.name, ignoreCase = true) }
                        _state.update { recomputeComputedFields(it) }
                    }
                    DeleteResult(success = res, message = null)
                } catch (t: Throwable) {
                    DeleteResult(success = false, message = t.message)
                })
            }

            override fun getProfileByName(name: String, callback: (ReflowProfile?) -> Unit) = userCall {
                val profile = api().profiles().profiles.firstOrNull { it.name.equals(name, ignoreCase = true) }
                callback.invoke(profile)
            }

            // Manual adjustments (fast; no busy spinner)
            override fun setManual(temp: Float, intensity: Float) {
                _state.update { it.copy(manualTemp = temp, manualIntensity = intensity) }
                scope.launchSafely(snackbarHostState) {
                    runCatching { api().set(temp, intensity) }
                }
            }

            override fun clearLogs() = userCall(after = { refreshAll() }) {
                // assumes ReflowApi has DELETE /api/logs implemented as clearLogs()
                runCatching { api().clearLogs() }
            }

            override fun share(data: String) {
                runCatching {  }
            }

        }

    }

    // ---- one-time: auto-scan hosts
    LaunchedEffect(Unit) {
        controller.refreshHosts()
    }

    // ---- polling loop (keyed by host + clientKey)
    val snapshot by controller.state.collectAsState()
    LaunchedEffect(snapshot.host, snapshot.apiConnected, clientKey) {
        if (!snapshot.apiConnected) return@LaunchedEffect   // don't start when disconnected

        while (true) {
            val t0 = getTimeMillis()
            runCatching { refreshAll() }
            val elapsed = getTimeMillis() - t0
            delay((1000L - elapsed).coerceAtLeast(200L))

            // stop looping immediately if user disconnects
            if (!controller.state.value.apiConnected) break
        }
    }

    return controller
}
