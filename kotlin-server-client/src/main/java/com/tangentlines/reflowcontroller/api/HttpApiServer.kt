package com.tangentlines.reflowcontroller.api

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.tangentlines.reflowcontroller.ApplicationController
import com.tangentlines.reflowcontroller.log.Logger
import com.tangentlines.reflowcontroller.log.StateLogger
import com.tangentlines.reflowcontroller.reflow.profile.ReflowProfile
import com.tangentlines.reflowcontroller.reflow.profile.loadProfiles
import com.sun.net.httpserver.Headers
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

class HttpApiServer(
    private val controller: ApplicationController,
    private val port: Int = 8090,
    key: String? = null
) {

    private val serverName: String =
        System.getProperty("server.name") ?:
        System.getenv("REFLOW_SERVER_NAME") ?:
        ("Reflow @" + runCatching { java.net.InetAddress.getLocalHost().hostName }.getOrDefault("host"))

    private val discoveryPort: Int =
        (System.getProperty("discovery.port") ?: System.getenv("REFLOW_DISCOVERY_PORT"))?.toIntOrNull() ?: 52525

    private val requiredClientKey: String? =
        key ?: System.getProperty("client.key")            // e.g. -Dclient.key=secret
            ?: System.getenv("REFLOW_CLIENT_KEY")       // or env REFLOW_CLIENT_KEY=secret
            ?: System.getenv("CLIENT_KEY")

    @Volatile private var discoverySocket: DatagramSocket? = null
    @Volatile private var discoveryThread: Thread? = null

    private val gson: Gson = GsonBuilder().setPrettyPrinting().serializeNulls().create()
    private lateinit var server: HttpServer

    @Volatile private var lastProfileSource: String? = null
    @Volatile private var lastProfileClient: String? = null

    fun start() {
        server = HttpServer.create(InetSocketAddress(port), 0)

        server.createContext("/api/hello", openHandler { _ ->
            ok(mapOf(
                "name" to serverName,
                "port" to port,
                "requiresAuth" to (requiredClientKey != null),
                "version" to "1.0"  // optional static string
            ))
        })

        // ---- Liveness
        server.createContext("/api/ping", handler { _ -> ok(mapOf("ok" to true)) })

        // ---- Status (each field is safe)
        server.createContext("/api/status", handler { _ -> ok(buildStatusDto()) })

        server.createContext("/api/set", handler { ex ->
            ex.requireMethod("POST")
            val dto = ex.readDto(SetRequest::class.java)
            val okSet = ctl("setTargetTemperature") { controller.setTargetTemperature(dto.intensity, dto.temp) }
            ok(ManualSetResponse(okSet, safe(0f) { controller.getTargetTemperature() }, safe(0f) { controller.getIntensity() }))
        })

        // ---- Ports
        server.createContext("/api/ports", handler { _ ->
            ok(PortsResponse(safe(listOf()) { controller.availablePorts() }))
        })

        // ---- Connect / Disconnect
        server.createContext("/api/connect", handler { ex ->
            ex.requireMethod("POST")
            val dto = ex.readDto(ConnectRequest::class.java)
            val result = runCatching { controller.connect(dto.port) }
            val ok = result.getOrDefault(false)

            if (!ok || result.isFailure) {
                runCatching { controller.disconnect() }
                val err = result.exceptionOrNull()
                val className = err?.javaClass?.name ?: ""
                val msg = err?.message ?: "connect failed"
                if (className == "gnu.io.PortInUseException") throw ApiError("Port in use: ${dto.port}", 409)
                throw BadRequest("Connect failed on ${dto.port}: $msg")
            }

            ok(ConnectResponse(true, safe(false) { controller.isConnected() }, controller.getPort()))
        })

        server.createContext("/api/disconnect", handler { ex ->
            ex.requireMethod("POST")
            val port = controller.getPort()
            val success = ctl("disconnect") { controller.disconnect() }
            ok(DisconnectResponse(success, safe(false) { controller.isConnected() }, port))
        })

        // ---- Target
        server.createContext("/api/target", handler { ex ->
            ex.requireMethod("POST")
            val dto = ex.readDto(TargetRequest::class.java)
            val okSet = ctl("setTargetTemperature") { controller.setTargetTemperature(dto.intensity, dto.temp) }
            ok(TargetResponse(okSet, safe(0f) { controller.getTargetTemperature() }, safe(0f) { controller.getIntensity() }))
        })

        server.createContext("/api/start", handler { ex ->
            ex.requireMethod("POST")
            val dto = ex.readDto(StartRequest::class.java)


            val (profile, source, client) = when {
                dto.profile != null -> {
                    val clientName = dto.clientName
                        ?: ex.requestHeaders.getFirst("X-Client-Name")
                        ?: ex.remoteAddress?.address?.hostAddress
                        ?: "unknown"
                    Triple(dto.profile, "remote", clientName)        // inline from client
                }
                !dto.profileName.isNullOrBlank() -> {
                    val p = safe(null as ReflowProfile?) { loadProfiles().firstOrNull { it.name == dto.profileName } }
                        ?: throw NotFound("Profile '${dto.profileName}' not found")
                    Triple(p, "local", null)                          // from server store
                }
                else -> Triple(null, null, null)
            }

            /* no profile is set, start in manual mode */
            if(profile == null) {

                val temp = dto.temp
                val intensity = dto.intensity

                ctl("start") { controller.startManual(intensity, temp) }
                return@handler true

            }

            val issues = basicValidateProfile(profile)
            if (issues.isNotEmpty()) throw BadRequest("Profile invalid: $issues")

            val okStart = ctl("start") { controller.start(profile) }

            // remember meta for status
            lastProfileSource = source
            lastProfileClient = client

            ok(StartResponse(okStart, safe(false) { controller.isRunning() }, safe<Int?>(null) { controller.getPhase() }, currentMode(), profile.name))
        })

        // /api/start-inline (alias)
        server.createContext("/api/start-inline", handler { ex ->
            ex.requireMethod("POST")
            val dto = ex.readDto(StartRequest::class.java)
            val profile = dto.profile ?: throw BadRequest("Expected 'profile' object")
            val issues = basicValidateProfile(profile)
            if (issues.isNotEmpty()) throw BadRequest("Profile invalid: $issues")

            val clientName = dto.clientName
                ?: ex.requestHeaders.getFirst("X-Client-Name")
                ?: ex.remoteAddress?.address?.hostAddress
                ?: "unknown"

            val okStart = ctl("start") { controller.start(profile) }

            lastProfileSource = "remote"
            lastProfileClient = clientName

            ok(StartResponse(okStart, safe(false) { controller.isRunning() }, safe<Int?>(null) { controller.getPhase() }, currentMode(), profile.name))
        })

        // ---- Profiles helpers
        server.createContext("/api/profiles/validate", handler { ex ->
            ex.requireMethod("POST")
            val dto = ex.readDto(ValidateProfileRequest::class.java)
            val issues = basicValidateProfile(dto.profile)
            ok(ValidateProfileResponse(issues.isEmpty(), issues))
        })

        server.createContext("/api/profiles/save", handler { ex ->
            ex.requireMethod("POST")
            val dto = ex.readDto(SaveProfileRequest::class.java)
            val issues = basicValidateProfile(dto.profile)
            if (issues.isNotEmpty()) throw BadRequest("Profile invalid: $issues")
            val saveName = (dto.fileName ?: dto.profile.name.ifBlank { "profile" } + ".json")
                .replace("/", "_").replace("..", "_")
            val dir = java.io.File("profiles").apply { if (!exists()) mkdirs() }
            val file = java.io.File(dir, saveName)
            runCatching { file.writeText(gson.toJson(dto.profile)) }
                .getOrElse { throw ControllerError("save profile failed: ${it.message}") }
            ok(SaveProfileResponse(true, file.absolutePath))
        })

        server.createContext("/api/profiles/", handler { ex ->
                ex.requireMethod("DELETE")

                // Extract "<name>" from /api/profiles/<name>
                val path = ex.requestURI.path // e.g., "/api/profiles/LeadFree-Example"
                val base = "/api/profiles/"
                val tail = path.substringAfter(base, missingDelimiterValue = "")
                if (tail.isBlank()) throw BadRequest("Missing profile name in path.")

                // Decode URL-encoded characters, then sanitize (no traversal / separators)
                val rawName = java.net.URLDecoder.decode(tail, StandardCharsets.UTF_8)
                val safeName = rawName
                    .replace("/", "_")
                    .replace("\\", "_")
                    .replace("..", "_")
                    .trim()

                if (safeName.isBlank()) throw BadRequest("Invalid profile name.")

                // Normalize to .json filename if caller passed a bare name
                val fileName = if (safeName.endsWith(".json", ignoreCase = true)) safeName else "$safeName.json"

                val dir = java.io.File("profiles")
                val file = java.io.File(dir, fileName)

                if (!file.exists()) {
                    throw NotFound("Profile not found: $rawName")
                }

                val deleted = runCatching { file.delete() }.getOrElse {
                    throw ControllerError("Delete failed: ${it.message}")
                }

                if (!deleted) throw ControllerError("Delete failed: could not remove file.")

                ok(DeleteProfileResponse(success = true, deleted = fileName))
            })

        server.createContext("/api/stop", handler { ex ->
            ex.requireMethod("POST")
            val okStop = ctl("stop") { controller.stop(); }
            lastProfileSource = null
            lastProfileClient = null
            ok(StopResponse(okStop, safe(false) { controller.isRunning() }))
        })

        // ---- Lists & logs (safe)
        server.createContext("/api/profiles", handler { _ ->
            ok(ProfilesResponse(safe(listOf()) { loadProfiles() }))
        })
        server.createContext("/api/logs/messages", handler { _ ->
            ok(MessagesResponse(safe(listOf()) { Logger.getMessages() }))
        })
        server.createContext("/api/logs/states", handler { _ ->
            ok(StatesResponse(safe(listOf()) { StateLogger.getEntries() }))
        })
        server.createContext("/api/logs", handler { ex ->
            when {
                ex.requestMethod.equals("DELETE", ignoreCase = true) -> {
                    val cleared = runCatching { controller.clearLogs() }.getOrDefault(false)
                    ok(AckResponse(cleared))
                }
                ex.requestMethod.equals("GET", ignoreCase = true) -> {
                    ok(LogsCombinedResponse(
                        safe(listOf()) { Logger.getMessages() },
                        safe(listOf()) { StateLogger.getEntries() }
                    ))
                }
                else -> throw MethodNotAllowed("Allowed: GET, DELETE")
            }
        })

        // ---- Executor with uncaught handler (keeps server alive)
        val threads = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
        server.executor = Executors.newFixedThreadPool(threads, object : ThreadFactory {
            private var idx = 0
            override fun newThread(r: Runnable): Thread {
                val t = Thread(r, "http-worker-${idx++}")
                t.isDaemon = true
                t.uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, e ->
                    e.printStackTrace()
                }
                return t
            }
        })

        server.start()
        startDiscoveryResponder()

        println("HTTP API started on http://0.0.0.0:$port (try /api/ping)")
    }

    fun stop() {
        if (this::server.isInitialized) {
            server.stop(0)
        }
        stopDiscoveryResponder()
    }

    private fun startDiscoveryResponder() {
        // simple, stateless responder
        val sock = DatagramSocket(discoveryPort).apply {
            soTimeout = 1000 // 1s read timeout for responsive shutdown
            reuseAddress = true
        }
        discoverySocket = sock
        discoveryThread = Thread({
            val buf = ByteArray(1024)
            while (!Thread.currentThread().isInterrupted) {
                try {
                    val packet = DatagramPacket(buf, buf.size)
                    sock.receive(packet)
                    val msg = String(packet.data, packet.offset, packet.length, StandardCharsets.UTF_8).trim()
                    if (msg == "REFLOW_DISCOVERY?") {
                        val payload = """{"name":"$serverName","port":$port,"requiresAuth":${requiredClientKey != null}}"""
                        val bytes = payload.toByteArray(StandardCharsets.UTF_8)
                        val reply = DatagramPacket(bytes, bytes.size, packet.address, packet.port)
                        sock.send(reply)
                    }
                } catch (_: SocketTimeoutException) {
                    // loop
                } catch (_: Throwable) {
                    // ignore transient errors, keep serving
                }
            }
        }, "reflow-discovery").apply { isDaemon = true; start() }
    }

    private fun stopDiscoveryResponder() {
        try { discoveryThread?.interrupt() } catch (_: Exception) {}
        try { discoverySocket?.close() } catch (_: Exception) {}
        discoveryThread = null
        discoverySocket = null
    }

    // ---------------- Helpers ----------------
    // open/public handler (no Basic Auth)
    private fun openHandler(block: (HttpExchange) -> Any?): HttpHandler = HttpHandler { ex ->
        try {
            ex.responseHeaders.enableCors()
            if (ex.requestMethod.equals("OPTIONS", ignoreCase = true)) {
                ex.sendResponseHeaders(204, -1); return@HttpHandler
            }
            val result = block(ex)
            when (result) {
                is Response -> ex.sendJson(result.status, result.payload)
                is Map<*, *> -> ex.sendJson(200, result)
                is Collection<*> -> ex.sendJson(200, result)
                is String -> ex.sendJson(200, mapOf("message" to result))
                null -> ex.sendJson(200, mapOf("ok" to true))
                else -> ex.sendJson(200, result)
            }
        } catch (e: ApiError) {
            safeSendError(ex, e.statusCode, e.message ?: "error")
        } catch (e: Throwable) {
            e.printStackTrace(); safeSendError(ex, 500, e.message ?: "internal error")
        } finally {
            try { ex.responseBody.close() } catch (_: Exception) {}
            try { ex.close() } catch (_: Exception) {}
        }
    }

    private fun handler(block: (HttpExchange) -> Any?): HttpHandler = HttpHandler { ex ->
        try {
            ex.responseHeaders.enableCors()
            if (ex.requestMethod.equals("OPTIONS", ignoreCase = true)) {
                ex.sendResponseHeaders(204, -1)
                return@HttpHandler
            }

            ex.requireAuth()

            val result = block(ex)
            when (result) {
                is Response     -> ex.sendJson(result.status, result.payload)
                is Map<*, *>    -> ex.sendJson(200, result)
                is Collection<*>-> ex.sendJson(200, result)
                is String       -> ex.sendJson(200, mapOf("message" to result))
                null            -> ex.sendJson(200, mapOf("ok" to true))   // <— ensure a body
                else            -> ex.sendJson(200, result)
            }
        } catch (e: ApiError) {
            safeSendError(ex, e.statusCode, e.message ?: "error")
        } catch (e: Throwable) {
            e.printStackTrace()
            safeSendError(ex, 500, e.message ?: "internal error")
        } finally {
            try { ex.responseBody.close() } catch (_: Exception) {}
            try { ex.close() } catch (_: Exception) {}
        }
    }

    private fun safeSendError(ex: HttpExchange, code: Int, msg: String) {
        if (code == 401) ex.responseHeaders.set("WWW-Authenticate", """Basic realm="ReflowAPI"""")
        runCatching { ex.sendJson(code, mapOf("error" to msg)) }
    }

    private fun ok(payload: Any) = Response(200, payload)

    private fun buildStatusDto(): StatusDto {
        val running = safe(false) { controller.isRunning() }
        return StatusDto(
            connected           = safe(false) { controller.isConnected() },
            running             = running,
            phase               = safe<Int?>(null) { controller.getPhase() },
            mode                = currentMode(),
            temperature         = safe(0f) { controller.getTemperature() },
            targetTemperature   = safe(0f) { controller.getTargetTemperature() },
            intensity           = safe(0f) { controller.getIntensity() },
            activeIntensity     = safe(0f) { controller.getActiveIntensity() },
            timeAlive           = safe(0L) { controller.getTime() },
            timeSinceTempOver   = safe(0L) { controller.getTimeSinceTempOver() },
            timeSinceCommand    = safe(0L) { controller.getTimeSinceCommand() },
            controllerTimeAlive = safe(0L) { controller.getControllerTimeAlive() },
            profile             = safe(null as ReflowProfile?) { controller.getProfile() },
            finished            = safe(false) { controller.isFinished() },
            profileSource       = if (running) lastProfileSource else null,
            profileClient       = if (running) lastProfileClient else null,
            port                = controller.getPort(),
            phaseTime           = controller.getPhaseTime(),
            nextPhaseIn         = controller.getNextPhaseIn()
        )
    }

    private fun currentMode(): String {
        return try {
            val isManual = controller.getProfile() == null
            if (safe(false) { controller.isRunning() }) if(isManual) "manual" else "profile" else "idle"
        } catch (_: Exception) { if (safe(false) { controller.isRunning() }) "profile" else "idle" }
    }

    private inner class Response(val status: Int, val payload: Any)

    // Chunked responses are more robust when exceptions happen mid-route
    private fun HttpExchange.sendJson(status: Int, payload: Any) {
        val json = gson.toJson(payload)
        val bytes = json.toByteArray(StandardCharsets.UTF_8)
        responseHeaders.set("Content-Type", "application/json; charset=utf-8")
        sendResponseHeaders(status, bytes.size.toLong())   // <— fixed length
        try {
            responseBody.write(bytes)
            responseBody.flush()
        } finally {
            try { responseBody.close() } catch (_: Exception) {}
        }
    }

    private fun <T> HttpExchange.readDto(clazz: Class<T>): T {
        InputStreamReader(requestBody, StandardCharsets.UTF_8).use {
            val text = it.readText().trim()
            if (text.isEmpty()) throw BadRequest("Missing JSON body")
            return runCatching { gson.fromJson(text, clazz) }
                .getOrElse { throw BadRequest("Invalid JSON: ${it.message}") }
        }
    }

    private fun HttpExchange.requireAuth() {
        val key = requiredClientKey ?: return // auth disabled if no key configured
        val hdr = requestHeaders.getFirst("Authorization") ?: throw Unauthorized("missing Authorization")
        if (!hdr.startsWith("Basic ", ignoreCase = true)) throw Unauthorized("invalid auth scheme")

        val raw = hdr.substring(6).trim()
        val decoded = try {
            String(Base64.getDecoder().decode(raw), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            throw Unauthorized("invalid base64")
        }

        // Accept either "user:key" or ":key". Username is ignored.
        val supplied = decoded.substringAfter(':', missingDelimiterValue = decoded)
        // Also accept the edge-case where client sent only the key (non-standard but harmless)
        val ok = (supplied == key) || (decoded == key)
        if (!ok) throw Unauthorized("invalid credentials")
    }

    private fun HttpExchange.requireMethod(expected: String) {
        if (!requestMethod.equals(expected, ignoreCase = true)) throw MethodNotAllowed("Method $expected required")
    }

    private fun Headers.enableCors() {
        add("Access-Control-Allow-Origin", "*")
        add("Access-Control-Allow-Headers", "Content-Type, Authorization")
        add("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS")
    }

    // Controller call guard -> always turns controller exceptions into 500 responses
    private inline fun <T> ctl(op: String, block: () -> T): T =
        try { block() } catch (e: Throwable) { throw ControllerError("$op failed: ${e.message ?: e::class.simpleName}") }

    // Safe getter for status (keeps the server answering even if a field explodes)
    private inline fun <T> safe(default: T, get: () -> T): T =
        try { get() } catch (_: Throwable) { default }

    private fun basicValidateProfile(profile: ReflowProfile): List<String> {
        val issues = mutableListOf<String>()
        if (profile.name.isBlank()) issues += "name: must not be blank"
        if (profile.phases.isEmpty()) issues += "phases: must contain at least one phase"

        profile.phases.forEachIndexed { idx, ph ->
            val label = ph.name.ifBlank { "phase-${idx + 1}" }
            if (ph.name.isBlank()) issues += "$label.name: must not be blank"
            if (ph.time < 0) issues += "$label.time: must be >= 0"
            if (ph.holdFor < 0) issues += "$label.hold_for: must be >= 0"
            if (ph.targetTemperature !in 0f..300f) issues += "$label.target_temperature: expected 0..300"
            if ((ph.initialIntensity ?: 0f) !in 0f..1f) issues += "$label.intensity: expected 0.0..1.0"
        }
        return issues
    }
}

private fun hasMethod(target: Any, name: String): Boolean = try {
    target::class.java.getMethod(name); true
} catch (_: NoSuchMethodException) { false }

private fun invokeNoArg(target: Any, name: String): Any? = try {
    val m = target::class.java.getMethod(name); m.isAccessible = true; m.invoke(target)
} catch (e: Exception) { throw RuntimeException(e) }

private open class ApiError(message: String, val statusCode: Int) : RuntimeException(message)
private class BadRequest(message: String) : ApiError(message, 400)
private class NotFound(message: String) : ApiError(message, 404)
private class MethodNotAllowed(message: String) : ApiError(message, 405)
private class ControllerError(message: String) : ApiError(message, 500)

private class Unauthorized(message: String) : ApiError(message, 401)