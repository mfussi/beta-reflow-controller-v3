package com.tangentlines.reflowclient.shared.ui.profile.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

@Composable
fun IntField(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
    onValue: (Int) -> Unit,
) {
    var local by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = local,
        onValueChange = {
            local = it
            it.toIntOrNull()?.let(onValue)
        },
        label = { Text(label) },
        singleLine = true,
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun FloatField(
    label: String,
    value: Float,
    modifier: Modifier = Modifier,
    onValue: (Float) -> Unit,
) {
    var local by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = local,
        onValueChange = {
            local = it
            it.toFloatOrNull()?.let(onValue)
        },
        label = { Text(label) },
        singleLine = true,
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun TextFieldSimple(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onValue: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier.fillMaxWidth()
    )
}