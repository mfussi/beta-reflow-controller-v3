package com.tangentlines.reflowclient.shared.net
import io.ktor.client.engine.*
import io.ktor.client.engine.js.*
actual fun defaultEngine(): HttpClientEngine = Js.create()
