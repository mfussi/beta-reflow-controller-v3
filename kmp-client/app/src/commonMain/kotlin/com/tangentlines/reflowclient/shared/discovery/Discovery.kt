package com.tangentlines.reflowclient.shared.discovery

import com.tangentlines.reflowclient.shared.model.DiscoveryReply

interface DiscoveryProvider { suspend fun scan(timeoutMs: Long = 1500L): List<DiscoveryReply> }

expect val Discovery : DiscoveryProvider?