package com.tangentlines.reflowcontroller

import com.tangentlines.reflowcontroller.api.HttpApiServer
import com.tangentlines.reflowcontroller.ui.MainWindow

object Main {

    @JvmStatic
    fun main(args: Array<String>) {

        val headless = args.contains("--api")
        val portArg = args.firstOrNull { it.startsWith("--port=") }?.substringAfter("=")
        val port = portArg?.toIntOrNull() ?: 8080

        val controller = ApplicationController()

        if (headless) {
            val api = HttpApiServer(controller, port)
            api.start()
            Thread.currentThread().join()
        } else {
            val window = MainWindow(controller)
            window.pack()
            window.isVisible = true
        }

    }

}