package com.tangentlines.reflowcontroller.reflow.device

import com.tangentlines.reflowcontroller.reflow.COMConnector
import gnu.io.SerialPort
import java.util.*

private const val CMD_TEMP_SHOW = "tempshow"
private const val CMD_SHOT = "shot"
private const val CMD_MANUAL = "manual"

private val REGEX_TEMP = "([+-][0-9\\\\.]+)C".toRegex()
private val REGEX_SHORT = "([a-zA-Z0-9 ]+),([0-9]+),([+-][0-9\\\\.]+),C".toRegex()

class BetaLayoutDevice(port : String) : AbstractDevice() {

    private val connector = COMConnector(port, 9600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE)

    private var currentTemperature : Float = 0.0f
    private var currentPulse : Float = 0.0f

    private var serviceRunning : Boolean = false

    private var timer : Timer? = null
    private var lastShot : Long = 0L

    init {
        connector.onNewLine = { data -> Thread(Runnable { newLine(data) }).start() }
    }

    override fun connect(): Boolean {
        return connector.connect()
    }

    override fun disconnect(): Boolean {
        return connector.close()
    }

    override fun isConnected(): Boolean {
        return connector.isConnected()
    }

    override fun start(): Boolean {

        if(!serviceRunning) {

            var init = true
            init = init && requestManual(true)                   // enable manual mode
            init = init && requestTemperatureUpdate(1)          // one second temperature updates

            if (init) {

                timer = Timer()
                timer!!.schedule(loopTask, 0L, 100L)

                serviceRunning = true
                return true
            }

        }

        return false
    }

    override fun stop(): Boolean {

        if(serviceRunning) {

            timer?.cancel()
            timer = null

            serviceRunning = false
            return true
        }

        return true

    }

    override fun isStarted(): Boolean {
        return serviceRunning
    }

    override fun setPulse(pulse: Float) {
        val oldPulse = this.currentPulse
        this.currentPulse = pulse
        if(isConnected() && isStarted() && oldPulse == 0.0f) this.requestShot(this.currentPulse)
    }

    override fun getPulse(): Float {
        return currentPulse
    }

    override fun getTemperature(): Float {
        return currentTemperature
    }

    private fun requestShot(intensity: Float) : Boolean {
        val cmd = "$CMD_SHOT ${(intensity * 100.0f).toInt()}"
        connector.sendAndForget(cmd)
        lastShot = System.currentTimeMillis()
        return true
    }

    private fun requestTemperature() : Boolean {
        connector.sendAndForget(CMD_TEMP_SHOW)
        return true
    }

    private fun requestTemperatureUpdate(seconds : Int) : Boolean {
        val cmd = "$CMD_TEMP_SHOW $seconds"
        connector.sendAndForget(cmd)
        return true
    }

    private fun requestManual(enable : Boolean) : Boolean {

        val cmd = "$CMD_MANUAL ${if(enable) "1" else "0"}"
        connector.sendAndForget(cmd)
        return true;

    }

    private fun newLine(data: String) {

        val rdata = data.replace(" ", "")

        // read temperature data
        if(REGEX_TEMP.matches(rdata)){
            this.currentTemperature = REGEX_TEMP.matchEntire(rdata)?.groupValues?.get(1)?.toFloatOrNull() ?: -1.0f
            notifyTemperatureChanged()
            return
        }

        // read temperature in another format
        if(REGEX_SHORT.matches(rdata)){

            val reg = REGEX_SHORT.matchEntire(rdata)
            this.currentTemperature = reg?.groupValues?.get(3)?.toFloatOrNull() ?: -1.0f
            notifyTemperatureChanged()
            return

        }

        /* resend current intensity */
        if(rdata == "shot" && currentPulse != 0.0f){
            requestShot(currentPulse)
            return
        }

    }

    private val loopTask = object : TimerTask() {

        override fun run() {

            if(serviceRunning && currentPulse != 0.0f){

                if(lastShot + 1010L < System.currentTimeMillis()){
                    requestShot(currentPulse)
                }

            }

        }

    }

}