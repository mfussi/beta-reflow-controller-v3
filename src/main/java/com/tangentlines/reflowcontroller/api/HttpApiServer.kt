package com.tangentlines.reflowcontroller.api

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.tangentlines.reflowcontroller.ApplicationController
import com.tangentlines.reflowcontroller.log.Logger
import com.tangentlines.reflowcontroller.log.StateLogger
import com.tangentlines.reflowcontroller.reflow.profile.ReflowProfile
import com.tangentlines.reflowcontroller.reflow.profile.Phase
import com.tangentlines.reflowcontroller.reflow.profile.loadProfiles
import com.sun.net.httpserver.Headers
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

class HttpApiServer(
    private val controller: ApplicationController,
    private val port: Int = 8080
) {
    private val gson: Gson = GsonBuilder().serializeNulls().create()
    private lateinit var server: HttpServer

    private val hasStartManual = hasMethod(controller, "startManual")
    private val hasStopManual = hasMethod(controller, "stopManual")
    private val hasIsManual = hasMethod(controller, "isManual") || hasMethod(controller, "isManualMode")

    fun start() {
        server = HttpServer.create(InetSocketAddress(port), 0)

        // ---- Liveness
        server.createContext("/api/ping", handler { _ -> ok(mapOf("ok" to true)) })

        // ---- Status (each field is safe)
        server.createContext("/api/status", handler { _ -> ok(buildStatusDto()) })

        // ---- Manual mode
        server.createContext("/api/manual/start", handler { ex ->
            ex.requireMethod("POST")
            if (!hasStartManual) throw NotFound("Manual mode not supported by this build")
            ctl("startManual") { invokeNoArg(controller, "startManual") }
            val dto = runCatching { ex.readDto(ManualStartRequest::class.java) }.getOrNull()
            val temp = dto?.temp
            val intensity = dto?.intensity
            if (temp != null && intensity != null) ctl("setTargetTemperature") { controller.setTargetTemperature(intensity, temp) }
            ok(ManualStartResponse(currentMode(), safe(0f) { controller.getTargetTemperature() }, safe(0f) { controller.getIntensity() }))
        })

        server.createContext("/api/manual/set", handler { ex ->
            ex.requireMethod("POST")
            val dto = ex.readDto(ManualSetRequest::class.java)
            if (currentMode() != "manual") throw BadRequest("Not in manual mode")
            val okSet = ctl("setTargetTemperature") { controller.setTargetTemperature(dto.intensity, dto.temp) }
            ok(ManualSetResponse(okSet, safe(0f) { controller.getTargetTemperature() }, safe(0f) { controller.getIntensity() }))
        })

        server.createContext("/api/manual/stop", handler { _ ->
            if (!hasStopManual) throw NotFound("Manual mode not supported by this build")
            ctl("stopManual") { invokeNoArg(controller, "stopManual") }
            ok(ManualStopResponse(currentMode()))
        })

        // ---- Ports
        server.createContext("/api/ports", handler { _ ->
            ok(PortsResponse(safe(listOf()) { controller.availablePorts() }))
        })

        // ---- Connect / Disconnect
        server.createContext("/api/connect", handler { ex ->
            ex.requireMethod("POST")
            val dto = ex.readDto(ConnectRequest::class.java)
            val success = ctl("connect") { controller.connect(dto.port) }
            ok(ConnectResponse(success, safe(false) { controller.isConnected() }))
        })

        server.createContext("/api/disconnect", handler { ex ->
            ex.requireMethod("POST")
            val success = ctl("disconnect") { controller.disconnect() }
            ok(DisconnectResponse(success, safe(false) { controller.isConnected() }))
        })

        // ---- Target
        server.createContext("/api/target", handler { ex ->
            ex.requireMethod("POST")
            val dto = ex.readDto(TargetRequest::class.java)
            val okSet = ctl("setTargetTemperature") { controller.setTargetTemperature(dto.intensity, dto.temp) }
            ok(TargetResponse(okSet, safe(0f) { controller.getTargetTemperature() }, safe(0f) { controller.getIntensity() }))
        })

        // ---- Start profile (name or inline)
        server.createContext("/api/start", handler { ex ->
            ex.requireMethod("POST")
            val dto = ex.readDto(StartRequest::class.java)
            val profile: ReflowProfile = when {
                dto.profile != null -> dto.profile
                !dto.profileName.isNullOrBlank() -> safe(null as ReflowProfile?) {
                    loadProfiles().firstOrNull { it.name == dto.profileName }
                } ?: throw NotFound("Profile '${dto.profileName}' not found")
                else -> throw BadRequest("Provide either 'profile' or 'profileName'")
            }
            val issues = basicValidateProfile(profile)
            if (issues.isNotEmpty()) throw BadRequest("Profile invalid: $issues")
            val okStart = ctl("start") { controller.start(profile) }
            ok(StartResponse(okStart, safe(false) { controller.isRunning() }, safe<String?>(null) { controller.getPhase() }, currentMode(), profile.name))
        })

        server.createContext("/api/start-inline", handler { ex ->
            ex.requireMethod("POST")
            val dto = ex.readDto(StartRequest::class.java)
            val profile = dto.profile ?: throw BadRequest("Expected 'profile' object")
            val issues = basicValidateProfile(profile)
            if (issues.isNotEmpty()) throw BadRequest("Profile invalid: $issues")
            val okStart = ctl("start") { controller.start(profile) }
            ok(StartResponse(okStart, safe(false) { controller.isRunning() }, safe<String?>(null) { controller.getPhase() }, currentMode(), profile.name))
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

        server.createContext("/api/stop", handler { ex ->
            ex.requireMethod("POST")
            val okStop = ctl("stop") { controller.stop() }
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
        println("HTTP API started on http://0.0.0.0:$port (try /api/ping)")
    }

    fun stop() { if (this::server.isInitialized) server.stop(0) }

    // ---------------- Helpers ----------------
    private fun handler(block: (HttpExchange) -> Any?): HttpHandler = HttpHandler { ex ->
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
                null -> { /* route already wrote */ }
                else -> ex.sendJson(200, result)
            }
        } catch (e: ApiError) {
            safeSendError(ex, e.statusCode, e.message ?: "error")
        } catch (e: Throwable) {
            // Catch-all so one bad controller call never kills the server
            e.printStackTrace()
            safeSendError(ex, 500, e.message ?: "internal error")
        } finally {
            try { ex.responseBody.close() } catch (_: Exception) {}
            try { ex.close() } catch (_: Exception) {}
        }
    }

    private fun safeSendError(ex: HttpExchange, code: Int, msg: String) {
        runCatching { ex.sendJson(code, mapOf("error" to msg)) }
    }

    private fun ok(payload: Any) = Response(200, payload)

    private fun buildStatusDto(): StatusDto = StatusDto(
        connected           = safe(false) { controller.isConnected() },
        running             = safe(false) { controller.isRunning() },
        phase               = safe<String?>(null) { controller.getPhase() },
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
        finished            = safe(false) { controller.isFinished() }
    )

    private fun currentMode(): String {
        return try {
            if (hasIsManual) {
                val isManual = (runCatching { invokeNoArg(controller, "isManual") }.getOrNull() as? Boolean)
                    ?: (runCatching { invokeNoArg(controller, "isManualMode") }.getOrNull() as? Boolean)
                if (isManual == true) return "manual"
            }
            if (safe(false) { controller.isRunning() }) "profile" else "idle"
        } catch (_: Exception) { if (safe(false) { controller.isRunning() }) "profile" else "idle" }
    }

    private inner class Response(val status: Int, val payload: Any)

    // Chunked responses are more robust when exceptions happen mid-route
    private fun HttpExchange.sendJson(status: Int, payload: Any) {
        val json = gson.toJson(payload)
        responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        sendResponseHeaders(status, -1) // chunked
        responseBody.use { it.write(json.toByteArray(StandardCharsets.UTF_8)) }
    }

    private fun <T> HttpExchange.readDto(clazz: Class<T>): T {
        InputStreamReader(requestBody, StandardCharsets.UTF_8).use {
            val text = it.readText().trim()
            if (text.isEmpty()) throw BadRequest("Missing JSON body")
            return runCatching { gson.fromJson(text, clazz) }
                .getOrElse { throw BadRequest("Invalid JSON: ${it.message}") }
        }
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
        val phases: List<Pair<String, Phase>> = listOf("preheat" to profile.preheat, "soak" to profile.soak, "reflow" to profile.reflow)
        for ((label, ph) in phases) {
            if (ph.time < 0) issues += "$label.time: must be >= 0"
            if (ph.holdFor < 0) issues += "$label.hold_for: must be >= 0"
            if (ph.targetTemperature < 0f || ph.targetTemperature > 300f) issues += "$label.target_temperature: expected 0..300"
            if (ph.intensity < 0f || ph.intensity > 1f) issues += "$label.intensity: expected 0.0..1.0"
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