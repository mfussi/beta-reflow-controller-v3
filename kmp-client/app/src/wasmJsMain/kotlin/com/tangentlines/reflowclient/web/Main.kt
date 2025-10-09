package com.tangentlines.reflowclient.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.tangentlines.reflowclient.shared.ui.ReflowApp
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {

    ComposeViewport(document.body!!) {
        ReflowApp(twoColumnOverride = true)
    }


}
