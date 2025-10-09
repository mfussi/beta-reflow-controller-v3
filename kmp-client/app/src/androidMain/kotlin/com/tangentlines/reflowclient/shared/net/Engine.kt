package com.tangentlines.reflowclient.shared.net
import io.ktor.client.engine.*
import io.ktor.client.engine.okhttp.*
actual fun defaultEngine(): HttpClientEngine = OkHttp.create()
