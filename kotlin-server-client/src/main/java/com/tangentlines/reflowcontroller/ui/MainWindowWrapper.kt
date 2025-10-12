package com.tangentlines.reflowcontroller.ui

import com.google.gson.JsonParser
import com.tangentlines.reflowcontroller.ApplicationController
import com.tangentlines.reflowcontroller.client.BackendWithEvents
import com.tangentlines.reflowcontroller.client.LocalControllerBackend
import com.tangentlines.reflowcontroller.client.RemoteControllerBackend
import com.tangentlines.reflowcontroller.log.LogEntry
import com.tangentlines.reflowcontroller.log.ReflowChart
import com.tangentlines.reflowcontroller.log.export
import com.tangentlines.reflowcontroller.reflow.profile.Phase
import com.tangentlines.reflowcontroller.reflow.profile.PhaseType
import com.tangentlines.reflowcontroller.reflow.profile.ReflowProfile
import com.tangentlines.reflowcontroller.reflow.profile.loadProfiles
import org.joda.time.format.DateTimeFormat
import java.awt.Component
import java.awt.Toolkit
import java.awt.event.ActionEvent
import java.util.*
import javax.swing.DefaultComboBoxModel
import javax.swing.JFrame
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JScrollPane
import javax.swing.SwingUtilities
import javax.swing.text.DefaultCaret
import javax.swing.text.JTextComponent

class MainWindowWrapper(private val window : MainWindow, private val controller: ApplicationController, private val port: Int) {

    private var baseTitle: String = "Reflow Controller — Local"
    
    private val backend = BackendWithEvents(LocalControllerBackend(controller))
    private val beeper = BeepNotifier()
    private var lastProfileName: String? = null

    init {

        setWindowTitleLocal()
        window.defaultCloseOperation = JFrame.EXIT_ON_CLOSE

        installLogAreaBehavior()
        installProfileRenderer()

        setupMenu()

        backend.onStateChanged.add { updateUI() }
        backend.onPhaseChanged.add { phaseChanged(it?.first, it?.second, it?.third) }
        backend.onLogsChanged.add { updateLogs(it) }

        window.btnRefresh.addActionListener { updatePorts() }

        updatePorts()
        updateProfiles()

        window.btnChart.addActionListener { executeAction("chart") { ReflowChart(backend).show(window.root) }}
        window.btnExport.addActionListener { executeAction("export", success = true) { export(backend.logs().states) }}
        window.btnClear.addActionListener { executeAction("clear", ask = true) { backend.clearLogs() }}

        window.btnStart.addActionListener { executeAction("start") {
            when (val choice = window.cbProfile.selectedItem) {
                is ProfileChoice.Manual -> backend.startManual(window.slTemperature.value.toFloat(), window.slIntesity.value / 100.0f)
                is ProfileChoice.Local  -> backend.startProfileInline(choice.profile)
                is ProfileChoice.Remote -> backend.startProfileByName(choice.name)
                else -> false
            }
        }}

        window.btnStop.addActionListener { executeAction("stop", ask = true) { backend.stop() }}

        window.btnConnect.addActionListener { executeAction("connect") { backend.connect(window.txtPort.selectedItem as String) } }
        window.btnDisconnect.addActionListener { executeAction("disconnect", ask = true) { backend.disconnect(); updatePorts(); true } }

        setSliderTemperatureText()
        setSliderIntensityText()

        window.slTemperature.addChangeListener { setSliderTemperatureText() }
        window.slIntesity.addChangeListener { setSliderIntensityText() }

        window.btnSend.addActionListener {

            val temp = window.slTemperature.value
            val intensity = (window.slIntesity.value / 100.0f)
            executeAction("send") { backend.setTargetTemperature(intensity, temp.toFloat()) }

        }

        Timer().schedule(object : TimerTask() {

            override fun run() {
                updateUI()
            }

        }, 0, 1000)

    }

    private fun setWindowTitleLocal() {
        window.title = "Reflow Controller — Local"
        applyTitleBadges()
    }

    private fun setWindowTitleRemote(host: String, port: Int) {
        window.title = "Reflow Controller — Remote ($host:$port)"
        applyTitleBadges()
    }

    private fun setupMenu() {
        val bar = JMenuBar()
        val conn = JMenu("Connection")

        // 1) Use Remote API...
        val useRemote = JMenuItem("Use Remote API…").apply {
            addActionListener { _: ActionEvent ->
                RemoteConfigDialog(window, port) { host ->
                    backend.swap(RemoteControllerBackend(host, port))
                    setWindowTitleRemote(host, port)
                    updateProfiles()
                    updatePorts()
                }.isVisible = true
            }
        }

        // 2) Use Local Controller
        val useLocal = JMenuItem("Use Local Controller").apply {
            addActionListener {
                backend.swap(LocalControllerBackend(controller))
                setWindowTitleLocal()
                updateProfiles()
                updatePorts()
            }
        }

        conn.add(useRemote)
        conn.add(useLocal)
        bar.add(conn)

        window.jMenuBar = bar

    }

    private fun phaseChanged(profile: ReflowProfile?, phase: Phase?, finished : Boolean?) {

        if (profile?.name != null) lastProfileName = profile.name
        window.cbProfile.isEnabled = backend.status().running != true

        if(phase?.type == PhaseType.COOLING) {
            val st = backend.status()
            beeper.arm(st.temperature)
        }

        if(finished == true){
            JOptionPane.showMessageDialog(window.root, "Your pcb is now finished. Please open the oven door and let the pcb cool down", "Finished - ${profile?.name}", JOptionPane.INFORMATION_MESSAGE)
        }

    }

    private fun setSliderIntensityText() {
        val intensity = (window.slIntesity.value / 100.0f)
        window.tvPreviewIntensity.text = "${(intensity * 100).toInt()} %"
    }

    private fun setSliderTemperatureText() {
        val temp = window.slTemperature.value
        window.tvPreviewTemperature.text = "$temp C"
    }

    val format = DateTimeFormat.mediumTime().withLocale(Locale.GERMAN)

    private fun installProfileRenderer() {
        window.cbProfile.renderer = object : javax.swing.DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: javax.swing.JList<*>,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): java.awt.Component {
                val c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)

                when (value) {
                    is ProfileChoice.Manual -> {
                        text = "⚙ Manual"
                    }
                    is ProfileChoice.Local -> {
                        text = "💻  ${value.profile.name}"
                    }
                    is ProfileChoice.Remote -> {
                        text = "🌐  ${value.name}"
                    }
                    is ProfileChoice.LocalGhost -> {
                        text = "👻  ${value.name} (external)"
                    }
                    is String -> { // fallback for plain strings
                        text = if (value.equals("Manual", ignoreCase = true)) "⚙️  Manual" else "📁  $value"
                    }
                    else -> {
                        text = value?.toString() ?: "-"
                    }
                }

                return c
            }
        }
    }

    // call this once during UI setup
    private fun installLogAreaBehavior() {
        val caret = (window.txtLog.caret as DefaultCaret)
        caret.updatePolicy = DefaultCaret.NEVER_UPDATE
    }

    // helper: are we scrolled to the very top?
    private fun isAtTop(comp: JTextComponent): Boolean {
        val sp = SwingUtilities.getAncestorOfClass(JScrollPane::class.java, comp) as? JScrollPane ?: return true
        val vbar = sp.verticalScrollBar
        return vbar.value == vbar.minimum
    }

    // your update method, now top-sticky
    private fun updateLogs(entries: List<LogEntry>) {
        val area = window.txtLog
        val wasAtTop = isAtTop(area)

        area.text = entries.asReversed().joinToString("\n") { "${format.print(it.time)}: ${it.message}" }

        if (wasAtTop) {
            // stay pinned to top after content change
            SwingUtilities.invokeLater {
                area.caretPosition = 0
                val sp = SwingUtilities.getAncestorOfClass(JScrollPane::class.java, area) as? JScrollPane
                sp?.verticalScrollBar?.value = sp?.verticalScrollBar?.minimum ?: 0
            }
        }
    }

    private fun updatePorts() {
        val ports = backend.availablePorts()
        val st = backend.status()  // single fetch

        // rebuild model
        window.txtPort.model = DefaultComboBoxModel<Any>(ports.toTypedArray())

        // choose desired selection:
        // 1) server-reported current port
        // 2) keep previous selection if still present
        // 3) first available
        val serverPort = st.port
        val prev = window.txtPort.selectedItem as? String
        val desired = when {
            serverPort != null && ports.contains(serverPort) -> serverPort
            prev != null && ports.contains(prev) -> prev
            ports.isNotEmpty() -> ports.first()
            else -> null
        }
        if (desired != null) window.txtPort.selectedItem = desired

        // optional: lock port selection while connected
        window.txtPort.isEnabled = st.connected != true
    }

    private fun updateProfiles() {
        // Build list items
        val locals = loadProfiles().map { ProfileChoice.Local(it) }
        val remotes = (backend.currentBackend() as? RemoteControllerBackend)
            ?.listProfilesRemote()
            ?.map { ProfileChoice.Remote(it.name) }
            ?: emptyList()

        val items = mutableListOf<Any>(ProfileChoice.Manual).apply {
            addAll(locals)
            addAll(remotes)
        }
        window.cbProfile.model = DefaultComboBoxModel(items.toTypedArray())

        // Decide selection
        val st = backend.status()
        val running = st.running == true
        val currentName = st.profile?.name

        val target: Any? = when {
            running && st.profileSource == "local" && currentName != null -> {
                // "local" on the server == our REMOTE bucket
                remotes.firstOrNull { it.name == currentName } ?: ProfileChoice.Remote(currentName)
            }
            running && st.profileSource == "remote" && currentName != null -> {
                // Inline from some client == our LOCAL bucket; if we don't have it, show a ghost
                locals.firstOrNull { it.profile.name == currentName } ?: ProfileChoice.LocalGhost(currentName)
            }
            else -> window.cbProfile.selectedItem // keep prior selection if idle
        }

        if (target != null) window.cbProfile.selectedItem = target

        // Disable while running so user can’t change mid-run
        window.cbProfile.isEnabled = !running
    }

    private fun applyTitleBadges(st: com.tangentlines.reflowcontroller.client.StatusDto? = null) {
        val s = st ?: kotlin.runCatching { backend.status() }.getOrNull()
        val comBadge = if (s?.connected == true) "COM ●" else "COM ○"
        val runBadge = if (s?.running == true)  "RUN ●" else "RUN ○"
        window.title = "$baseTitle — [$comBadge] [$runBadge]"
    }

    private fun updateUI() {

        val st = backend.status()
        applyTitleBadges(st)
        window.cbProfile.isEnabled = backend.status().running != true

        enableRecursive(window.btnConnect, st.connected != true && backend.availablePorts().isNotEmpty())
        enableRecursive(window.btnDisconnect, st.connected == true)
        enableRecursive(window.panelSettings, st.connected == true && st.running == true && st.phase == null)
        enableRecursive(window.panelStatus, st.connected == true)
        enableRecursive(window.panelLog, st.connected == true)

        enableRecursive(window.btnStart, st.connected == true && st.running != true)
        enableRecursive(window.btnStop, st.running == true)

        window.tvPhase.text = dynamicPhaseTrailHtml(st.phase, st.profile)
        window.tvTemperature.text = UiFormat.tempPair(st.temperature, st.targetTemperature)
        window.tvActiveIntensity.text = UiFormat.percentagePair(st.activeIntensity, st.intensity, fraction = true)
        window.tvTime.text = UiFormat.duration(st.timeAlive?.let { it / 1000 })
        window.tvSlope.text = st.slope?.let { UiFormat.num(it, 1, Locale.ENGLISH) + " °C/sec" } ?: "-"
        window.tvLiquidus.text = UiFormat.duration(st.timeAboveLiquidusMs?.let { it / 1000 })

        val phaseTypeStr = when(st.phase?.type) {
            PhaseType.HEATING -> "Heating"
            PhaseType.REFLOW -> "Reflow"
            null -> "-"
            PhaseType.COOLING -> "Cooling"
        }

        if(st.profile != null) {
            window.tvNextPhaseIn.text = UiFormat.durationPair(st.nextPhaseIn?.let { it / 1000 }, st.phaseTime?.let { it / 1000 }, postFix = "($phaseTypeStr)")
        } else {
            window.tvNextPhaseIn.text = "-"
        }

        window.tvTempOver.text = UiFormat.duration(st.timeSinceTempOver?.let { (it / 1000) })
        window.tvCommandSince.text = UiFormat.duration(st.timeSinceCommand?.let { (it / 1000) })

        if (st.profile?.name != null) lastProfileName = st.profile.name
        beeper.onTick(st.temperature, st.targetTemperature)

    }

    private fun showActionError(title: String, t: Throwable) {
        val message = when (t) {
            is com.tangentlines.reflowcontroller.client.HttpJson.HttpException -> {
                val apiMsg = try {
                    val je = JsonParser.parseString(t.body)
                    if (je.isJsonObject && je.asJsonObject.has("error"))
                        je.asJsonObject.get("error").asString
                    else null
                } catch (_: Exception) { null }
                buildString {
                    append("Server error ${t.code}")
                    if (!apiMsg.isNullOrBlank()) append(": ").append(apiMsg)
                }
            }
            else -> t.message ?: t.javaClass.simpleName
        }
        JOptionPane.showMessageDialog(
            window,
            message,
            "Error: $title",
            JOptionPane.ERROR_MESSAGE
        )
    }


    private fun executeAction(
        title: String,
        ask: Boolean = false,
        success: Boolean = false,
        action: () -> Boolean
    ) {
        if (ask) {
            val result = JOptionPane.showConfirmDialog(window, "Start action '$title'?", "Start", JOptionPane.YES_NO_OPTION)
            if (result == JOptionPane.YES_OPTION) {
                executeAction(title, ask = false, success = success, action = action)
            }
            return
        }

        try {
            val result = action.invoke()
            if (!result) {
                JOptionPane.showMessageDialog(window, "Unable to execute action: $title", "Error", JOptionPane.ERROR_MESSAGE)
            } else if (success) {
                JOptionPane.showMessageDialog(window, "$title successfully executed", "Success", JOptionPane.INFORMATION_MESSAGE)
            }
        } catch (t: Throwable) {
            showActionError(title, t)
        }
    }

    private fun enableRecursive(component : Component, value : Boolean) {

        component.isEnabled = value
        if(component is java.awt.Container){

            for(i in 0 until component.componentCount){
                enableRecursive(component.getComponent(i), value)
            }

        }

    }

}


private class BeepNotifier(
    private val beepIntervalMs: Long = 2000L,   // repeat rate while waiting for oven door
    private val minDropPerTick: Float = 1.5f,   // °C drop between successive samples counts toward "cooling"
    private val dropStreakToOpen: Int = 3,      // need this many successive drops
    private val baselineDropToOpen: Float = 10.0f, // OR total drop from baseline of at least this
    private val targetDeltaToOpen: Float = 15.0f   // OR 5°C below target
) {
    private var armed = false
    private var baselineTemp: Float? = null
    private var lastTemp: Float? = null
    private var lastBeepAt: Long = 0L
    private var dropStreak = 0

    fun arm(currentTemp: Float?) {
        armed = true
        baselineTemp = currentTemp
        lastTemp = currentTemp
        dropStreak = 0
        lastBeepAt = 0L
        beep() // one immediate beep on finish
    }

    fun disarm() {
        armed = false
    }

    fun onTick(currentTemp: Float?, targetTemp: Float?) {
        if (!armed || currentTemp == null) return

        // detect cooling (door opened) by slope or threshold
        val lt = lastTemp
        if (lt != null && currentTemp < lt - minDropPerTick) {
            dropStreak += 1
        } else {
            dropStreak = 0
        }

        val baseline = baselineTemp ?: currentTemp
        val cooling =
            (targetTemp != null && currentTemp <= targetTemp - targetDeltaToOpen) ||
                    (currentTemp <= baseline - baselineDropToOpen) ||
                    (dropStreak >= dropStreakToOpen)

        if (cooling) {
            disarm()
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastBeepAt >= beepIntervalMs) {
            beep()
            lastBeepAt = now
        }

        lastTemp = currentTemp
    }

    private fun beep() {
        try { java.awt.Toolkit.getDefaultToolkit().beep() } catch (_: Exception) {}
    }

}

private sealed class ProfileChoice {
    object Manual : ProfileChoice()
    data class Local(val profile: ReflowProfile) : ProfileChoice()
    data class Remote(val name: String) : ProfileChoice()      // server-stored
    data class LocalGhost(val name: String) : ProfileChoice()  // inline from another client

    override fun toString(): String = when (this) {
        Manual -> "Manual"
        is Local -> "Local: ${profile.name}"
        is Remote -> "Remote: $name"
        is LocalGhost -> "Local (external): $name"
    }
}

private fun dynamicPhaseTrailHtml(
    current: Phase?,
    profile: ReflowProfile?
): String {

    if(profile == null) {
        return "<html>Manual</html>"
    }

    val base = mutableListOf("Idle")
    val names = profile?.phases?.map { it.name.ifBlank { "phase" } } ?: listOf("Preheat","Soak","Reflow")
    base += names
    base += "Finished"

    val cur = when {
        current == null -> "Idle"
        else -> current.name
    }

    val html = base.joinToString(" — ") { p ->
        if (p.equals(cur, ignoreCase = true)) "<b>${p.replaceFirst(p[0].toChar(), p[0].toUpperCase())}</b>"
        else p.replaceFirst(p[0].toChar(), p[0].toUpperCase())
    }
    return "<html>$html</html>"
}