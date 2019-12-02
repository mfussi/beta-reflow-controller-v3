package com.tangentlines.reflowcontroller.reflow

import com.tangentlines.reflowcontroller.CONFIGURATION_HAS_FAKE_DEVICE
import gnu.io.CommPort
import gnu.io.CommPortIdentifier
import gnu.io.SerialPort
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

const val FAKE_IDENTIFIER = "Fake Device"

class COMConnector(private val port : String, private val baudRate : Int, val dataBits : Int, val stopBits : Int, val parity : Int) {

    private var isConnected = false

    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var serialReader: SerialReader? = null
    private var commPort: CommPort? = null
    private var serialReaderThread : Thread? = null

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

    open fun close() : Boolean {

        if(isConnected) {

            isConnected = false

            serialReader?.isStopped = true
            serialReaderThread?.stop()

            inputStream?.close()
            outputStream?.close()
            //commPort?.close()

            commPort = null
            return true

        }

        return true

    }

    open fun connect(): Boolean {

        if(isConnected) return false

        try {
            val portIdentifier = CommPortIdentifier.getPortIdentifier(port)
            if (portIdentifier.isCurrentlyOwned) {
                println("Error: Port is currently in use")
            } else {

                commPort = portIdentifier.open("Connector", 2000)

                if (commPort is SerialPort) {
                    val serialPort = commPort as SerialPort
                    serialPort.setSerialPortParams(baudRate, dataBits, stopBits, parity)

                    val inputStream = serialPort.getInputStream()
                    val outputStream = serialPort.getOutputStream()

                    serialReader = SerialReader(this, inputStream)
                    serialReaderThread = Thread(serialReader)
                    serialReaderThread!!.start()

                    this.inputStream = inputStream
                    this.outputStream = outputStream

                    isConnected = true
                    return true

                } else {
                    println("Error: Only serial ports are handled")
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return false
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

            this.outputStream?.write((line + "\r\n").toByteArray(Charsets.US_ASCII))

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

            this.outputStream?.write((line + "\r").toByteArray(Charsets.US_ASCII))

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