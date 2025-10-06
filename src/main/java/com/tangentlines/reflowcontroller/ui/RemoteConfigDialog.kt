package com.tangentlines.reflowcontroller.ui

import com.tangentlines.reflowcontroller.client.RemoteControllerBackend
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.prefs.Preferences
import javax.swing.*
import javax.swing.SwingUtilities.invokeLater
import javax.swing.border.EmptyBorder

class RemoteConfigDialog(
    parent: JFrame?,
    private val port : Int,
    private val onSelect: (host: String) -> Unit
) : JDialog(parent, "Remote API", true) {

    data class DiscoveredServer(
        val name: String,
        val host: String,
        val port: Int,
        val requiresAuth: Boolean
    ) {
        override fun toString(): String {
            val lock = if (requiresAuth) "🔒" else "🔓"
            return "$lock  $name — $host:$port"
        }
    }

    private val prefs: Preferences = Preferences.userRoot().node("com.tangentlines.reflowcontroller.remote")
    private val hostField = JTextField(prefs.get("remote_host", "localhost"), 16)
    private val statusLabel = JLabel(" ")

    private val listModel = DefaultListModel<DiscoveredServer>()
    private val list = JList(listModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        fixedCellHeight = 22
        visibleRowCount = 6
        border = EmptyBorder(4,4,4,4)
        addMouseListener(object: MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2 && selectedValue != null) {
                    useSelected()
                }
            }
        })
        cellRenderer = object: DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
            ): java.awt.Component {
                val c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                if (value is DiscoveredServer) {
                    val lock = if (value.requiresAuth) "🔒" else "🔓"
                    text = "$lock  ${value.name} — ${value.host}:${value.port}"
                }
                return c
            }
        }
    }

    init {
        layout = BorderLayout(8, 8)

        val form = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(labeled("Host / IP:", hostField))
            add(JLabel("Discovered servers:"))
            add(JScrollPane(list))
        }
        add(form, BorderLayout.CENTER)

        val buttons = JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
            val scanBtn  = JButton("Scan").apply { addActionListener(this@RemoteConfigDialog::onScan) }
            val testBtn  = JButton("Test").apply { addActionListener(this@RemoteConfigDialog::onTest) }
            val useBtn   = JButton("Use").apply { addActionListener { useSelectedOrField() } }
            val cancelBtn= JButton("Cancel").apply { addActionListener { dispose() } }
            add(scanBtn); add(testBtn); add(useBtn); add(cancelBtn)
        }
        add(buttons, BorderLayout.SOUTH)
        add(statusLabel, BorderLayout.NORTH)

        pack()
        setLocationRelativeTo(parent)

        // Auto scan on open for convenience
        SwingUtilities.invokeLater { onScan(ActionEvent(this, 0, "auto")) }
    }

    private fun labeled(label: String, field: JComponent): JPanel =
        JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(JLabel(label))
            add(field)
        }

    private fun onScan(@Suppress("UNUSED_PARAMETER") e: ActionEvent) {
        statusLabel.text = "Scanning…"
        listModel.clear()
        Thread({
            val found = discover(port, timeoutMs = 1200)
            invokeLater {
                if (found.isEmpty()) {
                    statusLabel.text = "No servers found."
                } else {
                    found.forEach { listModel.addElement(it) }
                    statusLabel.text = "Found ${found.size} server(s)."
                }
            }
        }, "reflow-scan").apply { isDaemon = true; start() }
    }

    private fun onTest(@Suppress("UNUSED_PARAMETER") e: ActionEvent) {
        val host = readFields() ?: return
        try {
            val backend = RemoteControllerBackend(host, port)
            val st = backend.status()
            statusLabel.text = "OK – ${host}:${port} (mode=${st.mode ?: "unknown"}, running=${st.running ?: false})"
        } catch (ex: Exception) {
            statusLabel.text = "Failed: ${ex.message}"
        }
    }

    private fun useSelected() {
        val sel = list.selectedValue ?: return
        selectHost(sel.host)
    }

    private fun useSelectedOrField() {
        val sel = list.selectedValue
        if (sel != null) {
            selectHost(sel.host)
        } else {
            val host = readFields() ?: return
            selectHost(host)
        }
    }

    private fun selectHost(host: String) {
        try { prefs.put("remote_host", host) } catch (_: Exception) {}
        onSelect(host)
        dispose()
    }

    private fun readFields():String? =
        hostField.text.trim().ifBlank { "localhost" }

    // ---- UDP broadcast discovery + HTTP hello verification ----
    private fun discover(port: Int, timeoutMs: Int): List<DiscoveredServer> {
        val results = LinkedHashMap<String, DiscoveredServer>() // key by host
        val socket = DatagramSocket(null).apply {
            reuseAddress = true
            soTimeout = 300
            broadcast = true
            bind(InetSocketAddress(0))
        }
        val query = "REFLOW_DISCOVERY?".toByteArray(StandardCharsets.UTF_8)
        val broadcast = InetAddress.getByName("255.255.255.255")
        val start = System.currentTimeMillis()

        // send a couple of broadcasts (helpful on some networks)
        repeat(2) {
            socket.send(DatagramPacket(query, query.size, broadcast, 52525))
        }

        // collect responses until timeout
        val buf = ByteArray(1024)
        while (System.currentTimeMillis() - start < timeoutMs) {
            try {
                val p = DatagramPacket(buf, buf.size)
                socket.receive(p)
                val text = String(p.data, p.offset, p.length, StandardCharsets.UTF_8).trim()
                // Expected: {"name":"…","port":8081,"requiresAuth":true}
                val (name, srvPort, auth) = parseHelloJson(text) ?: continue
                val host = p.address.hostAddress
                results.putIfAbsent(host, DiscoveredServer(name, host, srvPort, auth))
            } catch (_: SocketTimeoutException) {
                // keep looping
            } catch (_: Throwable) {
                // ignore bad packets
            }
        }
        socket.close()

        // OPTIONAL: verify via HTTP /api/hello (no auth) to the dialog's configured port
        // (We respect the app's chosen port; server may advertise a different one.)
        return results.values.map { d ->
            val verified = runCatching {
                val url = java.net.URL("http://${d.host}:$port/api/hello")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 600; conn.readTimeout = 600
                conn.requestMethod = "GET"
                conn.getInputStream().use { it.readBytes() }
                true
            }.getOrDefault(false)
            if (verified) d.copy(port = port) else d
        }.toList()
    }

    private fun parseHelloJson(s: String): Triple<String, Int, Boolean>? = try {
        val obj = com.google.gson.JsonParser.parseString(s).asJsonObject
        val name = obj.get("name")?.asString
        val port = obj.get("port")?.asInt ?: 8080
        val requiresAuth = obj.get("requiresAuth")?.asBoolean == true
        name?.let { Triple(name, port, requiresAuth) }
    } catch (_: Exception) { null }
}
