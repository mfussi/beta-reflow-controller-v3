package com.tangentlines.reflowcontroller.log

import com.google.gson.annotations.SerializedName

object Logger {

    val listeners : MutableList<(() -> Unit)> = mutableListOf()
    private val messages = mutableListOf<LogEntry>()

    fun addMessage(message : String){
        this.messages.add(LogEntry(System.currentTimeMillis(), message))
        this.listeners.forEach { it.invoke() }
    }

    fun clear() : Boolean {
        this.messages.clear()
        this.listeners.forEach { it.invoke() }
        return true
    }

    fun getMessages() : List<LogEntry> {
        return messages.toList()
    }

}

data class LogEntry(

    @SerializedName("time")
    val time : Long,

    @SerializedName("message")
    val message : String

)