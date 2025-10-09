package com.tangentlines.reflowclient.shared.net
import io.ktor.client.engine.*
import io.ktor.client.engine.darwin.*
actual fun defaultEngine(): HttpClientEngine = Darwin.create()
