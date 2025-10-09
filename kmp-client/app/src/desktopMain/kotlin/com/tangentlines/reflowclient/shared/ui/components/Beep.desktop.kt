package com.tangentlines.reflowclient.shared.ui.components

import androidx.compose.runtime.Composable

actual fun beep() {
    try { java.awt.Toolkit.getDefaultToolkit().beep() } catch (_: Exception) {}
}