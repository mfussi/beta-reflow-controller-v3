# Remote HTTP backend for the Swing client

This adds a small abstraction so your existing Swing app can talk to either:
- the **local** `ApplicationController` (serial/COM), or
- a **remote** HTTP API instance (your headless server).

## Files

- `client/ControllerBackend.kt` — interface used by the UI
- `client/LocalControllerBackend.kt` — wraps your `ApplicationController`
- `client/RemoteControllerBackend.kt` — calls the HTTP API (`/api/*`) using `HttpURLConnection`
- `client/HttpJson.kt` — tiny helper for GET/POST/DELETE JSON
- `ui/RemoteConfigDialog.kt` — dialog to enter host/port and test

## How to wire into your UI

1. Hold a mutable backend in `MainWindow` (or wherever your actions live):
   ```kotlin
   class MainWindow(private val controller: ApplicationController) : JFrame() {
       private var backend: ControllerBackend = LocalControllerBackend(controller)
       // ... use `backend` everywhere instead of calling `controller` directly ...
   }
   ```

2. Replace direct calls to `controller.*` with `backend.*`:
   - `controller.availablePorts()` → `backend.availablePorts()`
   - `controller.connect(port)` → `backend.connect(port)`
   - `controller.setTargetTemperature(i,t)` → `backend.setTargetTemperature(i,t)`
   - `controller.start(profile)` → `backend.startProfileInline(profile)` (or `startProfileByName(name)`)
   - `controller.stop()` → `backend.stop()`
   - Etc.

3. Add a menu item or toolbar button to switch to a remote backend:
   ```kotlin
   JMenuItem("Use Remote API...").apply {
       addActionListener {
           RemoteConfigDialog(this@MainWindow) { host, port ->
               backend = RemoteControllerBackend(host, port)
               JOptionPane.showMessageDialog(this@MainWindow, "Remote API set to http://$host:$port")
           }.isVisible = true
       }
   }
   ```

4. Optionally provide a quick toggle back to local:
   ```kotlin
   JMenuItem("Use Local Controller").apply {
       addActionListener {
           backend = LocalControllerBackend(controller)
           JOptionPane.showMessageDialog(this@MainWindow, "Switched to local controller")
       }
   }
   ```

That’s it — your UI code talks only to `ControllerBackend`, letting you flip between local and remote without rewriting the app.
