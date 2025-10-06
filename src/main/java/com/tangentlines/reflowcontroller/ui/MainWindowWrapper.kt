package com.tangentlines.reflowcontroller.ui

import com.tangentlines.reflowcontroller.ApplicationController
import com.tangentlines.reflowcontroller.client.BackendWithEvents
import com.tangentlines.reflowcontroller.client.LocalControllerBackend
import com.tangentlines.reflowcontroller.client.RemoteControllerBackend
import com.tangentlines.reflowcontroller.log.LogEntry
import com.tangentlines.reflowcontroller.log.ReflowChart
import com.tangentlines.reflowcontroller.log.export
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

class MainWindowWrapper(private val window : MainWindow, private val controller: ApplicationController) {

    private var baseTitle: String = "Reflow Controller — Local"
    
    private val backend = BackendWithEvents(LocalControllerBackend(controller))
    
    init {

        setWindowTitleLocal()
        window.defaultCloseOperation = JFrame.EXIT_ON_CLOSE

        installLogAreaBehavior()
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

            val profile = window.cbProfile.selectedItem
            when(profile){
                is String -> backend.manualStart(null)
                is ReflowProfile -> backend.startProfileByName(profile.name)
                else -> false
            }

        }}

        window.btnStop.addActionListener { executeAction("stop", ask = true) { backend.stop() }}

        window.btnConnect.addActionListener { executeAction("connect") { backend.connect(window.txtPort.selectedItem as String) } }
        window.btnDisconnect.addActionListener { executeAction("disconnect", ask = true) { backend.disconnect() } }

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
                RemoteConfigDialog(window) { host, port ->
                    backend.swap(RemoteControllerBackend(host, port))
                    JOptionPane.showMessageDialog(
                        window,
                        "Remote API set to http://$host:$port",
                        "Remote",
                        JOptionPane.INFORMATION_MESSAGE
                    )
                    setWindowTitleRemote(host, port)
                }.isVisible = true
            }
        }

        // 2) Use Local Controller
        val useLocal = JMenuItem("Use Local Controller").apply {
            addActionListener {
                backend.swap(LocalControllerBackend(controller))
                JOptionPane.showMessageDialog(
                    window,
                    "Switched to local controller",
                    "Local",
                    JOptionPane.INFORMATION_MESSAGE
                )
                setWindowTitleLocal()
            }
        }

        conn.add(useRemote)
        conn.add(useLocal)
        bar.add(conn)

        window.jMenuBar = bar

    }

    private fun phaseChanged(profile: ReflowProfile?, phase: String?, finished : Boolean?) {

        if(finished == true){

            Toolkit.getDefaultToolkit().beep()
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
        window.txtPort.model = DefaultComboBoxModel<Any>(backend.availablePorts().toTypedArray())
    }

    private fun updateProfiles() {

        val profiles = loadProfiles().toMutableList<Any>().apply {
            add("Manual")
        }

        window.cbProfile.model = DefaultComboBoxModel<Any>(profiles.toTypedArray())

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

        enableRecursive(window.btnConnect, st.connected != true && backend.availablePorts().isNotEmpty())
        enableRecursive(window.btnDisconnect, st.connected == true)
        enableRecursive(window.panelSettings, st.connected == true && st.running == true && st.phase == "Manual")
        enableRecursive(window.panelStatus, st.connected == true)
        enableRecursive(window.panelLog, st.connected == true)

        enableRecursive(window.btnStart, st.connected == true && st.running != true)
        enableRecursive(window.btnStop, st.running == true)

        window.tvPhase.text = st.phase ?: "-"
        window.tvTemperature.text = st.temperature?.let { String.format("%.1f", st.temperature) } ?: "-"
        window.tvIntensity.text = st.intensity?.let { String.format("%.1f", (it * 100)) } ?: "-"
        window.tvActiveIntensity.text = st.activeIntensity?.let { String.format("%.1f", (it * 100)) } ?: "-"
        window.tvTargetTemperature.text = st.targetTemperature ?.toString() ?: "-"
        window.tvTime.text = st.timeAlive?.let { (it / 1000).toString() } ?: "-"

        window.tvTempOver.text = st.timeSinceTempOver?.let { (it / 1000).toString() } ?: "-"
        window.tvCommandSince.text = st.timeSinceCommand?.let { (it / 1000).toString() } ?: "-"

        st.targetTemperature?.let {

            if(st.running == true && (st.targetTemperature ?: 0.0f) - 10 > it){
                Toolkit.getDefaultToolkit().beep()
            }

        }

    }

    private fun executeAction(title : String, ask : Boolean = false, success : Boolean = false, action : (() -> Boolean)) {

        if(ask){

            val result = JOptionPane.showConfirmDialog(window, "Start action '$title'?", "Start", JOptionPane.YES_NO_OPTION)
            if(result == JOptionPane.YES_OPTION) {
                executeAction(title, false, success, action)
            }
            return

        }

        val result = action.invoke()
        if(!result){

            JOptionPane.showMessageDialog(window, "Unable to execute action: $title", "Error", JOptionPane.ERROR_MESSAGE);

        } else if(success && result){

            JOptionPane.showMessageDialog(window, "$title successfully executed", "Success", JOptionPane.INFORMATION_MESSAGE);

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

