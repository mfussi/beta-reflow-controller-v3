package com.tangentlines.reflowclient.shared.discovery

actual val Discovery: DiscoveryProvider? by lazy { UdpDiscovery() }