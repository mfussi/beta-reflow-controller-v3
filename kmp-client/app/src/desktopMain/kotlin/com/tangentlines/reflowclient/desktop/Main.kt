package com.tangentlines.reflowclient.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.sun.tools.javac.tree.TreeInfo.args
import com.tangentlines.reflowclient.shared.ui.utils.BuildSecretsDefaults
import com.tangentlines.reflowclient.shared.ui.ReflowApp
import io.github.koalaplot.core.Symbol

fun main(args: Array<String>) = application {

    val portArg = args.firstOrNull { it.startsWith("--port=") }?.substringAfter("=")
    val port = portArg?.toIntOrNull() ?: 8090

    val secret = args.firstOrNull { it.startsWith("--client-key=") }
        ?.substringAfter("=")?.ifEmpty { null }
        ?: let { System.getenv("CLIENT-KEY") }?.ifEmpty { null }
        ?: let { System.getProperty("CLIENT-KEY") }?.ifEmpty { null }

    args.firstOrNull { it.startsWith("--server-name=") }
        ?.substringAfter("=")
        ?.let { System.setProperty("server.name", it) }

    BuildSecretsDefaults.clientKey = secret
    Window(onCloseRequest = ::exitApplication, title = "Reflow MPP Client") {
        ReflowApp(twoColumnOverride = true, port = port)
    }
}
