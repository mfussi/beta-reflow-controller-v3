package com.tangentlines.reflowclient.shared.ui.utils

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

suspend inline fun <T> safely(
    snackbar: SnackbarHostState,
    crossinline action: suspend () -> T
): T? = try {
    action()
} catch (t: Throwable) {
    val msg = t.message ?: (t::class.simpleName ?: "Error")
    snackbar.showSnackbar(message = msg, withDismissAction = true, duration = SnackbarDuration.Short)
    null
}

fun CoroutineScope.launchSafely(snackbar: SnackbarHostState, block: suspend () -> Unit) =
    this.launch {
        try {
            block()
        } catch (t: Throwable) {
            val msg = t.message ?: (t::class.simpleName ?: "Error")
            snackbar.showSnackbar(message = msg, withDismissAction = true, duration = SnackbarDuration.Short)
        }
    }
