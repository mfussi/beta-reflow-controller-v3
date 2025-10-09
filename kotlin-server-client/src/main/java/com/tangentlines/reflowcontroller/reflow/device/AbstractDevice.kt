package com.tangentlines.reflowcontroller.reflow.device

abstract class AbstractDevice : Device {

    private val temperatureListener = mutableListOf<(() -> Unit)>()

    protected fun notifyTemperatureChanged() {
        temperatureListener.forEach { it.invoke() }
    }

    override fun addOnTemperatureChanged(l: () -> Unit) {
        this.temperatureListener.add(l)
    }

    override fun removeOnTemperatureChanged(l: () -> Unit) {
        this.temperatureListener.remove(l)
    }

}