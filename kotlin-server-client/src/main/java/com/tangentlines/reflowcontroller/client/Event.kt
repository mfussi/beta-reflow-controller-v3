// File: src/main/java/com/tangentlines/reflowcontroller/client/Event.kt
package com.tangentlines.reflowcontroller.client

import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.SwingUtilities

class Event<T>(private val onEdt: Boolean = true) {
    private val listeners = CopyOnWriteArrayList<(T) -> Unit>()

    fun add(listener: (T) -> Unit) { listeners.add(listener) }
    fun remove(listener: (T) -> Unit) { listeners.remove(listener) }
    fun clear() { listeners.clear() }

    fun emit(value: T) {
        if (onEdt) {
            SwingUtilities.invokeLater {
                for (l in listeners) try { l(value) } catch (_: Throwable) {}
            }
        } else {
            for (l in listeners) try { l(value) } catch (_: Throwable) {}
        }
    }
}
