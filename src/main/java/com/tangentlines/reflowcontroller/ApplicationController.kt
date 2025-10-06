package com.tangentlines.reflowcontroller

import com.tangentlines.reflowcontroller.log.Logger
import com.tangentlines.reflowcontroller.log.State
import com.tangentlines.reflowcontroller.log.StateLogger
import com.tangentlines.reflowcontroller.reflow.COMConnector
import com.tangentlines.reflowcontroller.reflow.ReflowController
import com.tangentlines.reflowcontroller.reflow.profile.Phase
import com.tangentlines.reflowcontroller.reflow.profile.ReflowProfile

class ApplicationController() {

    var onStateChanged : MutableList<(() -> Unit)> = mutableListOf()
    var onPhaseChanged : MutableList<((ReflowProfile, Phase?, Boolean) -> Unit)> = mutableListOf()

    private var reflow : ReflowController? = null

    fun availablePorts() : List<String>{
        return COMConnector.available()
    }

    fun connect(port : String) : Boolean {

        if(reflow == null) {

            val connector = ReflowController(port)
            val success = connector.connect()

            connector.onTempChanged = {

                notifyChanges();
                Logger.addMessage("${String.format("%.1f °C", connector.getTemperature())} - ${String.format("%.0f %%", connector.getActiveIntensity() * 100.0f)}")
                StateLogger.add(State(System.currentTimeMillis(), connector.getPhaseName() ?: "unknown", connector.getTemperature(), connector.getActiveIntensity(), connector.getTargetTemperature(), connector.getIntensity()))

            }

            connector.onNewPhase = { profile, phase, isFinished ->
                onPhaseChanged.forEach { it.invoke(profile, phase, isFinished) }
            }

            this.reflow = connector

            notifyChanges()
            return success

        }

        return false

    }

    fun disconnect() : Boolean {

        reflow?.let {

            if(!it.isConnected()) return false
            val success = it.disconnect()
            reflow = null

            notifyChanges()
            return success

        }

        return false

    }

    fun isConnected(): Boolean {
        return reflow?.isConnected() == true
    }

    fun setTargetTemperature(intensity: Float, temp: Float): Boolean {
        reflow?.setTargetTemperature(intensity, temp)
        return reflow != null
    }

    fun getNextPhaseIn() : Long? {
        return reflow?.getNextPhaseIn()
    }

    fun getPhaseTime() : Long? {
        return reflow?.getPhaseTime()
    }

    fun getIntensity(): Float? {
        return reflow?.getIntensity()
    }

    fun getTemperature(): Float? {
        return reflow?.getTemperature()
    }

    fun getTargetTemperature(): Float? {
        return reflow?.getTargetTemperature()
    }

    fun getTime(): Long? {
        return reflow?.getTimeAlive()
    }

    fun getTimeSinceTempOver(): Long? {
        return reflow?.getTimeSinceTempOver()
    }

    fun getTimeSinceCommand(): Long? {
        return reflow?.getTimeSinceCommand()
    }

    fun getControllerTimeAlive() : Long? {
        return reflow?.getControllerTimeAlive()
    }

    fun getActiveIntensity() : Float? {
        return reflow?.getActiveIntensity()
    }

    private fun notifyChanges() {
        onStateChanged.forEach { it.invoke() }
    }

    fun clearLogs(): Boolean {
        return StateLogger.clear() && Logger.clear()
    }

    fun start(profile : ReflowProfile?): Boolean {
        Logger.clear()
        StateLogger.clear()
        reflow?.setProfile(profile)
        return reflow?.startService() ?: false

    }

    fun stop(): Boolean {
        return reflow?.stopService() ?: false
    }

    fun isRunning(): Boolean {
        return isConnected() && reflow?.isRunning() ?: false
    }

    fun getPhase(): Int? {
        return reflow?.getPhase()
    }

    fun getProfile(): ReflowProfile? {
        return reflow?.getProfile()
    }

    fun isFinished(): Boolean? {
        return reflow?.isFinished()
    }

    fun getPort(): String? {
        return reflow?.getPort()
    }

    fun getPhaseType(): Phase.PhaseType? {
        return reflow?.getPhaseType()
    }

}