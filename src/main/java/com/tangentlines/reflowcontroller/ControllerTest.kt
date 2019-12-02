package com.tangentlines.reflowcontroller

import gnu.io.CommPortIdentifier
import gnu.io.SerialPort
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream

fun test(port : String) {

    val portIdentifier = CommPortIdentifier.getPortIdentifier(port)
    val serialPort = portIdentifier.open("Connector", 2000) as SerialPort

    serialPort.setSerialPortParams(9600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE)

    val inputStream = serialPort.inputStream
    val outputStream = serialPort.outputStream

    while(true) {

        //outputStream.write("tempshow\r".toByteArray(Charsets.US_ASCII))

        //var data = readAll(inputStream)
        //System.out.println(data)

        outputStream.write("shot 50\r".toByteArray(Charsets.US_ASCII))
        //data = readAll(inputStream)

        //System.out.println(data)

    }

}

private fun readAll(inputStream : InputStream) : String {

    val output = StringBuilder()
    val buffer = ByteArray(1024)

    var available = inputStream.available()
    while(available == 0){
        Thread.sleep(10)
        available = inputStream.available()
    }

    Thread.sleep(10)

    while(available > 0){

        val length = inputStream.read(buffer)
        output.append(String(buffer, 0, length))
        available = inputStream.available()

    }

    return output.toString()

}