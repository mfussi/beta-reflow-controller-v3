package com.tangentlines.reflowcontroller.ui

import com.tangentlines.reflowcontroller.ApplicationController
import com.tangentlines.reflowcontroller.log.Logger
import com.tangentlines.reflowcontroller.log.ReflowChart
import com.tangentlines.reflowcontroller.log.StateLogger
import com.tangentlines.reflowcontroller.reflow.profile.Phase
import com.tangentlines.reflowcontroller.reflow.profile.ReflowProfile
import com.tangentlines.reflowcontroller.reflow.profile.loadProfiles
import org.joda.time.format.DateTimeFormat
import java.awt.Component
import java.awt.Toolkit
import java.util.*
import javax.swing.DefaultComboBoxModel
import javax.swing.JFrame
import javax.swing.JOptionPane

class MainWindowWrapper(private val window : MainWindow, private val controller: ApplicationController) {

    init {

        window.title = "Reflow Controller"
        window.defaultCloseOperation = JFrame.EXIT_ON_CLOSE

        controller.onStateChanged.add( ::updateUI )
        controller.onPhaseChanged.add( ::phaseChanged )

        window.btnRefresh.addActionListener { updatePorts() }
        updatePorts()
        updateProfiles()

        Logger.listeners.add(::updateLogs)

        window.btnChart.addActionListener { executeAction("chart") { ReflowChart().show(window.root) }}
        window.btnExport.addActionListener { executeAction("export", success = true) { StateLogger.export() }}
        window.btnClear.addActionListener { executeAction("clear", ask = true) { controller.clearLogs() }}

        window.btnStart.addActionListener { executeAction("start") {

            val profile = window.cbProfile.selectedItem
            when(profile){
                is String -> controller.start(null)
                is ReflowProfile -> controller.start(profile)
                else -> false
            }

        }}

        window.btnStop.addActionListener { executeAction("stop", ask = true) { controller.stop() }}

        window.btnConnect.addActionListener { executeAction("connect") { controller.connect(window.txtPort.selectedItem as String) } }
        window.btnDisconnect.addActionListener { executeAction("disconnect", ask = true) { controller.disconnect() } }

        setSliderTemperatureText()
        setSliderIntensityText()

        window.slTemperature.addChangeListener { setSliderTemperatureText() }
        window.slIntesity.addChangeListener { setSliderIntensityText() }

        window.btnSend.addActionListener {

            val temp = window.slTemperature.value
            val intensity = (window.slIntesity.value / 100.0f)
            executeAction("send") { controller.setTargetTemperature(intensity, temp.toFloat()) }

        }

        Timer().schedule(object : TimerTask() {

            override fun run() {
                updateUI()
            }

        }, 0, 1000)

    }

    private fun phaseChanged(reflowProfile: ReflowProfile, phase: Phase?, finished: Boolean) {

        if(finished){

            Toolkit.getDefaultToolkit().beep();
            JOptionPane.showMessageDialog(window.root, "Your pcb is now finished. Please open the oven door and let the pcb cool down", "Finished - ${reflowProfile.name}", JOptionPane.INFORMATION_MESSAGE)

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
        window.txtPort.model = DefaultComboBoxModel<Any>(controller.availablePorts().toTypedArray())
    }

    private fun updateProfiles() {

        val profiles = loadProfiles().toMutableList<Any>().apply {
            add("Manual")
        }

        window.cbProfile.model = DefaultComboBoxModel<Any>(profiles.toTypedArray())

    }

    private fun updateUI() {

        enableRecursive(window.btnConnect,!controller.isConnected() && controller.availablePorts().isNotEmpty())
        enableRecursive(window.btnDisconnect, controller.isConnected())
        enableRecursive(window.panelSettings, controller.isConnected() && controller.isRunning() && controller.getPhase() == "Manual")
        enableRecursive(window.panelStatus, controller.isConnected())
        enableRecursive(window.panelLog, controller.isConnected())

        enableRecursive(window.btnStart, controller.isConnected() && !controller.isRunning())
        enableRecursive(window.btnStop, controller.isRunning())

        window.tvPhase.text = controller.getPhase() ?: "-"
        window.tvTemperature.text = controller.getTemperature()?.let { String.format("%.1f", controller.getTemperature()) } ?: "-"
        window.tvIntensity.text = controller.getIntensity()?.let { String.format("%.1f", (it * 100)) } ?: "-"
        window.tvActiveIntensity.text = controller.getActiveIntensity()?.let { String.format("%.1f", (it * 100)) } ?: "-"
        window.tvTargetTemperature.text = controller.getTargetTemperature() ?.toString() ?: "-"
        window.tvTime.text = controller.getTime()?.let { (it / 1000).toString() } ?: "-"

        window.tvTempOver.text = controller.getTimeSinceTempOver()?.let { (it / 1000).toString() } ?: "-"
        window.tvCommandSince.text = controller.getTimeSinceCommand()?.let { (it / 1000).toString() } ?: "-"

        controller.getTargetTemperature()?.let {

            if(controller.isRunning() && (controller.getTemperature() ?: 0.0f) - 10 > it){
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

