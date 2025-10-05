package com.tangentlines.reflowcontroller.ui

import com.tangentlines.reflowcontroller.ApplicationController
import com.tangentlines.reflowcontroller.client.BackendWithEvents
import com.tangentlines.reflowcontroller.client.ControllerBackend
import com.tangentlines.reflowcontroller.client.Event
import com.tangentlines.reflowcontroller.client.LocalControllerBackend
import com.tangentlines.reflowcontroller.client.RemoteControllerBackend
import com.tangentlines.reflowcontroller.client.StatusDto
import com.tangentlines.reflowcontroller.log.Logger
import com.tangentlines.reflowcontroller.log.ReflowChart
import com.tangentlines.reflowcontroller.log.StateLogger
import com.tangentlines.reflowcontroller.reflow.profile.Phase
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

class MainWindowWrapper(private val window : MainWindow, private val controller: ApplicationController) {

    private val backend = BackendWithEvents(LocalControllerBackend(controller))
    
    init {

        window.title = "Reflow Controller"
        window.defaultCloseOperation = JFrame.EXIT_ON_CLOSE

        setupMenu()

        backend.onStateChanged.add { updateUI() }
        backend.onPhaseChanged.add { phaseChanged(it?.first, it?.second, it?.third) }

        window.btnRefresh.addActionListener { updatePorts() }
        updatePorts()
        updateProfiles()

        Logger.listeners.add(::updateLogs)

        window.btnChart.addActionListener { executeAction("chart") { ReflowChart().show(window.root) }}
        window.btnExport.addActionListener { executeAction("export", success = true) { StateLogger.export() }}
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
            }
        }

        conn.add(useRemote)
        conn.add(useLocal)
        bar.add(conn)

        // Attach the menubar to the window
        window.jMenuBar = bar
        // If your codebase uses `menuBar` instead of `jMenuBar`, do this:
        // this.menuBar = bar
    }

    private fun phaseChanged(profile: String?, phase: String?, finished : Boolean?) {

        if(finished == true){

            Toolkit.getDefaultToolkit().beep();
            JOptionPane.showMessageDialog(window.root, "Your pcb is now finished. Please open the oven door and let the pcb cool down", "Finished - ${profile}", JOptionPane.INFORMATION_MESSAGE)

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

    private fun updateLogs(){
        window.txtLog.text = Logger.getMessages().filterNotNull().reversed().joinToString("\n") { "${format.print(it.time)}: ${it.message}" }
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

    private fun updateUI() {

        enableRecursive(window.btnConnect, backend.status().connected != true && backend.availablePorts().isNotEmpty())
        enableRecursive(window.btnDisconnect, backend.status().connected == true)
        enableRecursive(window.panelSettings, backend.status().connected == true && backend.status().running == true && backend.status().phase == "Manual")
        enableRecursive(window.panelStatus, backend.status().connected == true)
        enableRecursive(window.panelLog, backend.status().connected == true)

        enableRecursive(window.btnStart, backend.status().connected == true && backend.status().running != true)
        enableRecursive(window.btnStop, backend.status().running == true)

        window.tvPhase.text = backend.status().phase ?: "-"
        window.tvTemperature.text = backend.status().temperature?.let { String.format("%.1f", backend.status().temperature) } ?: "-"
        window.tvIntensity.text = backend.status().intensity?.let { String.format("%.1f", (it * 100)) } ?: "-"
        window.tvActiveIntensity.text = backend.status().activeIntensity?.let { String.format("%.1f", (it * 100)) } ?: "-"
        window.tvTargetTemperature.text = backend.status().targetTemperature ?.toString() ?: "-"
        window.tvTime.text = backend.status().timeAlive?.let { (it / 1000).toString() } ?: "-"

        window.tvTempOver.text = backend.status().timeSinceTempOver?.let { (it / 1000).toString() } ?: "-"
        window.tvCommandSince.text = backend.status().timeSinceCommand?.let { (it / 1000).toString() } ?: "-"

        backend.status().targetTemperature?.let {

            if(backend.status().running == true && (backend.status().targetTemperature ?: 0.0f) - 10 > it){
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

