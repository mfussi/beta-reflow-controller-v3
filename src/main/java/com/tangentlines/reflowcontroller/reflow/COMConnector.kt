package com.tangentlines.reflowcontroller.reflow

import com.tangentlines.reflowcontroller.CONFIGURATION_HAS_FAKE_DEVICE
import gnu.io.CommPort
import gnu.io.CommPortIdentifier
import gnu.io.SerialPort
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

const val FAKE_IDENTIFIER = "Fake Device"

class COMConnector(private val baudRate : Int, val dataBits : Int, val stopBits : Int, val parity : Int) {

    @Volatile private var isConnected = false
    @Volatile private var serialPort: SerialPort? = null
    @Volatile private var inStream: InputStream? = null
    @Volatile private var outStream: OutputStream? = null

    private var serialReader: SerialReader? = null

    var onNewLine: ((String) -> Unit)? = null

    companion object {

        fun available() : List<String> {


            val list = CommPortIdentifier.getPortIdentifiers().toList().map {
                (it as CommPortIdentifier).name
            }.toMutableList()

            if(CONFIGURATION_HAS_FAKE_DEVICE){
                list.add(FAKE_IDENTIFIER)
            }

            return list

        }

    }

    fun isConnected() : Boolean {
        return isConnected
    }

    @Synchronized
    fun connect(portName: String): Boolean {
        if (serialPort != null) return true // already open

        var opened: SerialPort? = null
        try {
            val id = CommPortIdentifier.getPortIdentifier(portName)
            if (id.isCurrentlyOwned) throw gnu.io.PortInUseException()

            opened = id.open("ReflowController", /*timeout ms*/ 2000) as gnu.io.SerialPort
            opened.setSerialPortParams(baudRate, dataBits, stopBits, parity)
            opened.flowControlMode = SerialPort.FLOWCONTROL_NONE

            // only publish fields AFTER all of the above succeeds
            serialPort = opened
            inStream = opened.inputStream
            outStream = opened.outputStream

            // attach listeners here if you use them; if any step throws, we’ll close in catch/finally
            // opened.addEventListener(...); opened.notifyOnDataAvailable(true)

            return true
        } catch (t: Throwable) {
            // full cleanup on ANY error
            runCatching { opened?.removeEventListener() }
            runCatching { inStream?.close() }
            runCatching { outStream?.close() }
            runCatching { opened?.close() }
            serialPort = null; inStream = null; outStream = null
            throw t
        }
    }

    @Synchronized
    fun close(): Boolean {

        if(isConnected) {
            isConnected = false

            serialReader?.isStopped = true
            serialReader?.stop()
            serialReader = null

            val sp = serialPort
            serialPort = null
            runCatching { sp?.removeEventListener() }
            runCatching { inStream?.close() }
            runCatching { outStream?.close() }
            runCatching { sp?.close() }
            inStream = null; outStream = null
            return true

        }

        return true

    }

    private var wait = false
    private var response: MutableList<String>? = null
    private var count : Int = 0

    fun sendAndForget(line: String) {

        if(wait){
            return
        }

        wait = true

        try {

            this.outStream?.write((line + "\r\n").toByteArray(Charsets.US_ASCII))

        } catch (e: IOException) {
            e.printStackTrace()
            close()
            wait = false
            return
        }

        wait = false
        return

    }

    protected fun send(line: String, size: Int = 1, timeout: Long = 20000L): List<String>? {

        if(wait){
            return null
        }

        val startTime = System.currentTimeMillis()
        count = size
        wait = true
        response = mutableListOf()

        try {

            this.outStream?.write((line + "\r").toByteArray(Charsets.US_ASCII))

        } catch (e: IOException) {
            e.printStackTrace()
            close()
            return null
        }

        serialReader!!.waitFor(object : Callback {

            override fun onTransmit(data: String) {

                count--
                response!!.add(data)

                if(wait && count == 0) {
                    wait = false
                }

            }

        })

        while (wait) {

            if(startTime + timeout < System.currentTimeMillis()){
                wait = false
                count = 0
                response = null
                return null
            }

            try {
                Thread.sleep(20)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }

        }

        return if((response?.size ?: 0) == size) response else null

    }

}

private class SerialReader(internal var parent: COMConnector, internal var `in`: InputStream) : Runnable {

    internal var mCallback: Callback? = null
    var isStopped = false;

    fun stop() {
        isStopped = true
    }

    override fun run() {

        while(!isStopped) {

            val data = StringBuilder()
            val buffer = ByteArray(1024)
            var len = -1

            try {

                len = this.`in`.read(buffer)

                while (len > -1) {

                    data.append(String(buffer, 0, len))

                    if(data.endsWith("\n")) {

                        data.toString()
                                .split("\n")
                                .map { it.replace("\r", "").trim() }
                                .filter { it.isNotEmpty() }
                                .forEach { transmitLine(it) }

                        data.clear()

                    }

                    len = this.`in`.read(buffer)

                }

                Thread.sleep(20);

            } catch (e: IOException) {
                e.printStackTrace()
                isStopped = true
                parent.close()

            }

        }

    }

    private fun transmitLine(data: String) {
        mCallback?.onTransmit(data)
        parent.onNewLine?.invoke(data)
    }

    fun waitFor(callback: Callback) {
        this.mCallback = callback
    }

}

private interface Callback {
    fun onTransmit(response: String)
}