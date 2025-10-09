package com.tangentlines.reflowclient.shared.net
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
actual fun defaultEngine(): HttpClientEngine = CIO.create()
