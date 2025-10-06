package com.tangentlines.reflowcontroller.reflow.device

import java.util.*
import kotlin.math.max
import kotlin.math.min

private const val SPEED_FACTOR = 2.0f                                  // 1.0f -> for normal speed; > 1.0f - speed run

private const val PERIOD = 1000L
private const val HEATING_SPEED = (0.17f * SPEED_FACTOR)               // 3 C per second
private const val INC = 0.3f                                           // heating adaption speed
private const val COOLING_SPEED = (0.025f * SPEED_FACTOR)              // 0.125 C per second

class FakeDevice(private val port : String) : AbstractDevice() {

    private var isConnected: Boolean = false
    private var isStarted: Boolean = false

    private var currentHeatingSpeed = 0.0f
    private var currentCoolingSpeed = 0.0f
    private var currentTemperature: Float = 20.0f
    private var currentPulse: Float = 0.0f

    private var updateTimer : Timer? = null

    override fun getPort(): String {
        return port
    }

    override fun connect(): Boolean {

        if (!isConnected) {
            isConnected = true
            updateTimer = Timer()
            updateTimer!!.schedule(updateTask, 0L, PERIOD)
            return true
        }

        return false

    }

    override fun disconnect(): Boolean {

        if (isConnected) {
            isConnected = false

            updateTimer!!.cancel()
            updateTimer = null

            return true
        }

        return false

    }

    override fun isConnected(): Boolean {
        return isConnected
    }

    override fun start(): Boolean {

        if(isConnected && !isStarted){

            updateTask.reset()
            isStarted = true
            return true

        }

        return false

    }

    override fun stop(): Boolean {

        if(isConnected && isStarted) {

            isStarted = false
            currentTemperature = 20.0f
            return true

        }

        return false

    }

    override fun isStarted(): Boolean {
        return isStarted
    }

    override fun setPulse(pulse: Float) {
        this.currentPulse = pulse
    }

    override fun getPulse(): Float {
        return currentPulse
    }

    override fun getTemperature(): Float {
        return currentTemperature
    }

    private val updateTask = object : TimerTask() {

        private var lastUpdate = System.currentTimeMillis()

        fun reset() {
            lastUpdate = System.currentTimeMillis()
        }

        override fun run() {

            val now = System.currentTimeMillis()

            if(isConnected && isStarted){

                val heating = (HEATING_SPEED * currentPulse)
                val cooling = max(0.0f, min(1.0f, 1.0f - currentPulse)) * (COOLING_SPEED)

                currentHeatingSpeed = (currentHeatingSpeed * (1.0f - INC) + heating * INC)
                currentCoolingSpeed = (currentCoolingSpeed * (1.0f - INC) + cooling * INC)

                val fraction = (now - lastUpdate) / 1000.0f

                if(currentHeatingSpeed >= 0) {
                    currentTemperature += (currentHeatingSpeed * fraction)
                }

                if(currentCoolingSpeed >= 0) {
                    currentTemperature = max(20.0f, currentTemperature - (currentCoolingSpeed * fraction))
                }

                lastUpdate = now
                notifyTemperatureChanged()

            }

        }

    }


}