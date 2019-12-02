package com.tangentlines.reflowcontroller

import com.tangentlines.reflowcontroller.ui.MainWindow

object Main {

    @JvmStatic
    fun main(args: Array<String>) {

        val controller = ApplicationController()

        val window = MainWindow(controller)
        window.pack()
        window.isVisible = true

    }

}