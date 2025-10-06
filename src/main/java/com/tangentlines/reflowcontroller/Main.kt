package com.tangentlines.reflowcontroller

import com.tangentlines.reflowcontroller.api.HttpApiServer
import com.tangentlines.reflowcontroller.ui.MainWindow

object Main {

    @JvmStatic
    fun main(args: Array<String>) {

        val headless = args.contains("--api")
        val portArg = args.firstOrNull { it.startsWith("--port=") }?.substringAfter("=")
        val port = portArg?.toIntOrNull() ?: 8090

        args.firstOrNull { it.startsWith("--client-key=") }
            ?.substringAfter("=")
            ?.let { System.setProperty("client.key", it) }

        args.firstOrNull { it.startsWith("--server-name=") }
            ?.substringAfter("=")
            ?.let { System.setProperty("server.name", it) }

        val controller = ApplicationController()

        if (headless) {
            val api = HttpApiServer(controller, port)
            api.start()
            Thread.currentThread().join()
        } else {
            val window = MainWindow(controller, port)
            window.pack()
            window.isVisible = true
        }

    }

}