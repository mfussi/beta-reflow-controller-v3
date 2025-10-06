package com.tangentlines.reflowcontroller.reflow

import com.tangentlines.reflowcontroller.log.Logger
import com.tangentlines.reflowcontroller.reflow.device.BetaLayoutDevice
import com.tangentlines.reflowcontroller.reflow.device.Device
import com.tangentlines.reflowcontroller.reflow.device.FakeDevice
import com.tangentlines.reflowcontroller.reflow.profile.Phase
import com.tangentlines.reflowcontroller.reflow.profile.ReflowProfile
import java.util.*

private const val UPDATE_INTERVAL = 500L
private const val BRAKE = 0.75f

class ReflowController(port : String) {

    var onTempChanged : (() -> Unit)? = null
    var onNewPhase : ((ReflowProfile, Phase?, Boolean) -> Unit)? = null

    private var timer : Timer? = null
    private var controllerTimeAlive : Long? = null

    private var intensity : Float = 1.0f
    private var targetTemperature : Float? = null
    private var lastCommand : Long? = null
    private var connectTime : Long? = null
    private var temOverSince : Long? = null

    private var currentTemperature : Float = -1.0f

    private var reflowProfile : ReflowProfile? = null
    private var currentReflowProfilePhase : Int = -1

    private val device : Device = when(port) {
        FAKE_IDENTIFIER -> FakeDevice()
        else -> BetaLayoutDevice(port)
    }

    init {

        device.addOnTemperatureChanged {
            this.currentTemperature = device.getTemperature()
            onTempChanged?.invoke()
        }

    }

    fun connect(): Boolean {

        val success = device.connect()

        if(success){
            connectTime = System.currentTimeMillis()
        }

        return success

    }

    fun isConnected(): Boolean {
        return device.isConnected()
    }

    fun setProfile(profile: ReflowProfile?){
        this.reflowProfile = profile
        this.currentReflowProfilePhase = -1
    }

    fun setTargetTemperature(intensity : Float, temperature : Float) {

        this.targetTemperature = temperature
        this.intensity = Math.min(1.0f, Math.max(0.0f, intensity))

        this.lastCommand = System.currentTimeMillis()
        this.temOverSince = null

    }

    fun getTargetTemperature() : Float? {
        return targetTemperature
    }

    fun getIntensity() : Float {
        return intensity
    }

    fun getTemperature() : Float {
        return currentTemperature
    }

    fun getTimeAlive() : Long? {
        return connectTime?.let { System.currentTimeMillis() - it }
    }

    private fun goToNextPhase(profile : ReflowProfile){

        currentReflowProfilePhase += 1

        if(currentReflowProfilePhase >= profile.getPhases().size){

            setTargetTemperature(0.0f, 0.0f)

        } else {

            val phase = profile.getPhases()[currentReflowProfilePhase]
            setTargetTemperature(phase.intensity, phase.targetTemperature)
        }

        Logger.addMessage("Changed to phase: " + profile.getNameForPhase(currentReflowProfilePhase))

        val phase = if(currentReflowProfilePhase > 0 && currentReflowProfilePhase < profile.getPhases().size )profile.getPhases()[currentReflowProfilePhase] else null
        onNewPhase?.invoke(profile, phase, currentReflowProfilePhase >= profile.getPhases().size)

    }

    fun update() {

        val currentTemp = getTemperature()
        var holdFor = -1;

        reflowProfile?.let { profile ->

            if(currentReflowProfilePhase == -1){
                goToNextPhase(profile)
            } else {

                if (currentReflowProfilePhase < profile.getPhases().size) {

                    val phase = profile.getPhases()[currentReflowProfilePhase]
                    holdFor = phase.holdFor;

                    if (phase.time > 0 && (getTimeSinceCommand() ?: 0L) > phase.time * 1000L) {

                        // next phase
                        goToNextPhase((profile))

                    } else if (phase.holdFor > 0 && (getTimeSinceTempOver() ?: 0L > (phase.holdFor * 1000L))) {

                        // next phase
                        goToNextPhase((profile))

                    } else if (phase.time == 0 && phase.holdFor == 0 && (currentTemp) >= phase.targetTemperature) {

                        // next phase
                        goToNextPhase((profile))

                    }

                }

            }

        }

        val brake = if(holdFor != -1 && holdFor < 10) 0.0f else BRAKE;
        val start = System.currentTimeMillis()
        val tTemp = targetTemperature

        if(tTemp == null){

            /* only request temperature */
            device.setPulse(0.0f)

        } else {

            if(currentTemp < tTemp - 10 * brake){

                /* far target */
                device.setPulse(intensity * 1.0f)

            } else if(currentTemp < tTemp - 3 * brake){

                /* near target */
                device.setPulse(intensity * 0.5f)

            } else if(currentTemp < tTemp){

                /* close to target */
                device.setPulse(intensity * 0.3f)

            } else if (currentTemp >= tTemp) {

                /* target temp already reached */
                device.setPulse(0.0f)

                if(temOverSince == null){
                    temOverSince = System.currentTimeMillis()
                }

            }

        }

        val end = System.currentTimeMillis()
        //System.out.println("update: ${end - start} ms")

    }

    fun startService() : Boolean {

        val success = device.start()
        if(success){

            this.currentTemperature = device.getTemperature()

            timer = Timer()
            timer!!.schedule(UpdateTask(this), 0, UPDATE_INTERVAL)
            return true

        }

        return false

    }

    fun stopService() : Boolean {

        val success = device.stop()
        if(success){
            timer!!.cancel()
            timer = null
            return true
        }

        return false

    }

    fun getTimeSinceTempOver(): Long? {
        return temOverSince?.let { System.currentTimeMillis() - it }
    }

    fun getTimeSinceCommand(): Long? {
        return lastCommand?.let { System.currentTimeMillis() - it }
    }

    fun getControllerTimeAlive() : Long? {
        return controllerTimeAlive
    }

    fun getActiveIntensity() : Float {
        return device.getPulse()
    }

    fun isRunning(): Boolean {
        return device.isStarted()
    }

    fun disconnect(): Boolean {
        return device.disconnect()
    }

    fun getPhase(): String? {

        if(reflowProfile == null) {
            return "Manual"
        } else {
            return reflowProfile!!.getNameForPhase(currentReflowProfilePhase)
        }

    }

    fun isFinished(): Boolean? {
        val profile = reflowProfile ?: return false
        return currentReflowProfilePhase >= profile.getPhases().size
    }

    fun getProfile() : ReflowProfile? {
        return reflowProfile
    }

}

private class UpdateTask(private val controller : ReflowController) : TimerTask() {

    var isStopped = false

    override fun run() {

        if(!isStopped && controller.isConnected()){
            controller.update()
        }

    }

    override fun cancel(): Boolean {
        isStopped = true
        return super.cancel()
    }

}