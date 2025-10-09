package com.tangentlines.reflowclient.shared.discovery

import com.tangentlines.reflowclient.shared.model.DiscoveryReply
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.*
import kotlin.math.max

class UdpDiscovery(
    private val port: Int = 52525,          // server's default
    private val alsoTryLegacy: Boolean = true  // try 35888 for older servers
) : DiscoveryProvider {

    override suspend fun scan(timeoutMs: Long): List<DiscoveryReply> = withContext(Dispatchers.IO) {
        val out = mutableListOf<DiscoveryReply>()
        val tried = mutableSetOf<String>()

        DatagramSocket(null).use { socket ->
            socket.reuseAddress = true
            socket.broadcast = true
            socket.bind(InetSocketAddress(0))

            // Send discovery to global + directed broadcast on both ports
            val portsToTry = buildSet {
                add(port)
                if (alsoTryLegacy && port != 35888) add(35888)
            }

            val payload = "REFLOW_DISCOVERY?".toByteArray(Charsets.UTF_8)

            fun send(to: InetAddress, p: Int) {
                try {
                    socket.send(DatagramPacket(payload, payload.size, to, p))
                } catch (_: Throwable) { /* ignore */ }
            }

            // Global broadcast
            val globalBc = InetAddress.getByName("255.255.255.255")
            portsToTry.forEach { send(globalBc, it) }

            // Directed broadcasts per interface (often required on 10.0.0.x)
            try {
                val nets = NetworkInterface.getNetworkInterfaces()
                while (nets.hasMoreElements()) {
                    val ni = nets.nextElement()
                    if (!ni.isUp || ni.isLoopback) continue
                    ni.interfaceAddresses?.forEach { ia ->
                        val b = ia.broadcast ?: return@forEach
                        portsToTry.forEach { send(b, it) }
                    }
                }
            } catch (_: Throwable) { /* best effort */ }

            // Collect replies until timeout
            val deadline = System.currentTimeMillis() + timeoutMs
            val buf = ByteArray(1024)

            while (true) {
                val remaining = max(1L, deadline - System.currentTimeMillis()).toInt()
                socket.soTimeout = remaining
                val pkt = DatagramPacket(buf, buf.size)
                try {
                    socket.receive(pkt)
                } catch (e: SocketTimeoutException) {
                    break
                } catch (_: Throwable) {
                    break
                }

                val text = String(pkt.data, 0, pkt.length, Charsets.UTF_8).trim()
                val reply = parseReply(text, pkt) ?: continue

                val key = "${reply.host}:${reply.port}"
                if (tried.add(key)) out += reply
                if (System.currentTimeMillis() >= deadline) break
            }
        }

        out
    }

    private fun parseReply(s: String, pkt: DatagramPacket): DiscoveryReply? {
        return if (s.startsWith("{")) {
            // JSON: {"name":"…","port":8090,"requiresAuth":true}
            runCatching {
                val obj = Json.parseToJsonElement(s).jsonObject
                val name = obj["name"]?.jsonPrimitive?.content ?: "Reflow"
                val port = obj["port"]?.jsonPrimitive?.int ?: return null
                DiscoveryReply(name, pkt.address.hostAddress, port)
            }.getOrNull()
        } else {
            // Legacy: name|ip|port
            val parts = s.split("|")
            if (parts.size >= 3) {
                val name = parts[0]
                val port = parts[2].toIntOrNull() ?: return null
                DiscoveryReply(name, pkt.address.hostAddress, port)
            } else null
        }
    }
}