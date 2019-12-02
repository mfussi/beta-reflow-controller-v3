package com.tangentlines.reflowcontroller.reflow.device

interface Device {

    fun connect() : Boolean
    fun disconnect() : Boolean
    fun isConnected() : Boolean

    fun start() : Boolean
    fun stop() : Boolean
    fun isStarted() : Boolean

    fun setPulse(pulse : Float)
    fun getPulse() : Float

    fun getTemperature() : Float

    fun addOnTemperatureChanged(l : (() -> Unit))
    fun removeOnTemperatureChanged(l : (() -> Unit))
}