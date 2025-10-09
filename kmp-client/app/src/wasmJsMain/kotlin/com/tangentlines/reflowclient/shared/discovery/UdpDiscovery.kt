package com.tangentlines.reflowclient.shared.discovery

import com.tangentlines.reflowclient.shared.model.DiscoveryReply

class UdpDiscovery: DiscoveryProvider { override suspend fun scan(timeoutMs: Long) = emptyList<DiscoveryReply>() }