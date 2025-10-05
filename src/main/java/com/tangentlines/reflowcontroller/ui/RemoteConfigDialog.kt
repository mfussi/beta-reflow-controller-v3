// File: src/main/java/com/tangentlines/reflowcontroller/ui/RemoteConfigDialog.kt
package com.tangentlines.reflowcontroller.ui

import com.tangentlines.reflowcontroller.client.RemoteControllerBackend
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import java.util.prefs.Preferences
import javax.swing.*

class RemoteConfigDialog(
    parent: JFrame?,
    private val onSelect: (host: String, port: Int) -> Unit
) : JDialog(parent, "Remote API", true) {

    private val prefs: Preferences = Preferences.userRoot().node("com.tangentlines.reflowcontroller.remote")
    private val hostField = JTextField(prefs.get("remote_host", "localhost"), 16)
    private val portField = JTextField(prefs.getInt("remote_port", 8090).toString(), 6)
    private val statusLabel = JLabel(" ")

    init {
        layout = BorderLayout(8, 8)

        val form = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(labeled("Host / IP:", hostField))
            add(Box.createVerticalStrut(6))
            add(labeled("Port:", portField))
        }
        add(form, BorderLayout.CENTER)

        val buttons = JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
            val testBtn = JButton("Test").apply { addActionListener(this@RemoteConfigDialog::onTest) }
            val okBtn = JButton("Use").apply { addActionListener(this@RemoteConfigDialog::onUse) }
            val cancelBtn = JButton("Cancel").apply { addActionListener { dispose() } }
            add(testBtn); add(okBtn); add(cancelBtn)
        }
        add(buttons, BorderLayout.SOUTH)
        add(statusLabel, BorderLayout.NORTH)

        pack()
        setLocationRelativeTo(parent)
    }

    private fun labeled(label: String, field: JComponent): JPanel =
        JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(JLabel(label))
            add(field)
        }

    private fun onTest(@Suppress("UNUSED_PARAMETER") e: ActionEvent) {
        val (host, port) = readFields() ?: return
        try {
            val backend = RemoteControllerBackend(host, port)
            val st = backend.status()
            statusLabel.text = "OK – mode=${st.mode ?: "unknown"}, running=${st.running ?: false}"
        } catch (ex: Exception) {
            statusLabel.text = "Failed: ${ex.message}"
        }
    }

    private fun onUse(@Suppress("UNUSED_PARAMETER") e: ActionEvent) {
        val (host, port) = readFields() ?: return
        // Persist the last used host/port
        try {
            prefs.put("remote_host", host)
            prefs.putInt("remote_port", port)
        } catch (_: Exception) {
            // ignore preference write failures
        }
        onSelect(host, port)
        dispose()
    }

    private fun readFields(): Pair<String, Int>? {
        val host = hostField.text.trim().ifBlank { "localhost" }
        val port = portField.text.trim().toIntOrNull()
        if (port == null || port !in 1..65535) {
            JOptionPane.showMessageDialog(this, "Invalid port", "Error", JOptionPane.ERROR_MESSAGE)
            return null
        }
        return host to port
    }
}
