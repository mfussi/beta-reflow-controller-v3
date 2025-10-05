// File: src/main/java/com/tangentlines/reflowcontroller/api/HttpApiServer.kt
package com.tangentlines.reflowcontroller.api

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
import java.io.OutputStream
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

class HttpApiServer(
    private val controller: ApplicationController,
    private val port: Int = 8080
) {
    private val gson: Gson = GsonBuilder().serializeNulls().create()
    private lateinit var server: HttpServer

    // --- Optional manual-mode reflection hooks (support both designs) ---
    private val hasStartManual = hasMethod(controller, "startManual")
    private val hasStopManual = hasMethod(controller, "stopManual")
    private val hasIsManual = hasMethod(controller, "isManual") || hasMethod(controller, "isManualMode")

    fun start() {
        server = HttpServer.create(InetSocketAddress(port), 0)

        // --- Routing ---
        server.createContext("/api/status", handler { _ ->
            ok(
                mapOf(
                    "connected" to controller.isConnected(),
                    "running" to controller.isRunning(),
                    "phase" to controller.getPhase(),
                    "mode" to currentMode(),
                    "temperature" to controller.getTemperature(),
                    "targetTemperature" to controller.getTargetTemperature(),
                    "intensity" to controller.getIntensity(),
                    "activeIntensity" to controller.getActiveIntensity(),
                    "timeAlive" to controller.getTime(),
                    "timeSinceTempOver" to controller.getTimeSinceTempOver(),
                    "timeSinceCommand" to controller.getTimeSinceCommand(),
                    "controllerTimeAlive" to controller.getControllerTimeAlive(),
                    "profile" to controller.getProfile(),
                    "finished" to controller.isFinished()
                )
            )
        })

        // --- Manual mode endpoints ---
        server.createContext("/api/manual/start", handler { ex ->
            ex.requireMethod("POST")
            if (!hasStartManual) throw NotFound("Manual mode not supported by this build")
            invokeNoArg(controller, "startManual")
            val body = ex.parseJson()
            val temp = body?.getAsJsonPrimitive("temp")?.asFloat
            val intensity = body?.getAsJsonPrimitive("intensity")?.asFloat
            if (temp != null && intensity != null) {
                controller.setTargetTemperature(intensity, temp)
            }
            ok(mapOf("mode" to currentMode(), "targetTemperature" to controller.getTargetTemperature(), "intensity" to controller.getIntensity()))
        })

        server.createContext("/api/manual/set", handler { ex ->
            ex.requireMethod("POST")
            val body = ex.parseJson()
            val temp = body?.getAsJsonPrimitive("temp")?.asFloat
                ?: throw BadRequest("Missing 'temp' (Float)")
            val intensity = body.getAsJsonPrimitiveOrNull("intensity")?.asFloat
                ?: throw BadRequest("Missing 'intensity' (Float 0.0..1.0)")
            val mode = currentMode()
            if (mode != "manual") throw BadRequest("Not in manual mode (mode=$mode)")
            val okSet = controller.setTargetTemperature(intensity, temp)
            ok(mapOf("ok" to okSet, "targetTemperature" to controller.getTargetTemperature(), "intensity" to controller.getIntensity()))
        })

        server.createContext("/api/manual/stop", handler { _ ->
            if (!hasStopManual) throw NotFound("Manual mode not supported by this build")
            invokeNoArg(controller, "stopManual")
            ok(mapOf("mode" to currentMode()))
        })

        server.createContext("/api/ports", handler { _ ->
            ok(mapOf("ports" to controller.availablePorts()))
        })

        server.createContext("/api/connect", handler { ex ->
            ex.requireMethod("POST")
            val body = ex.parseJson()
            val portName = body?.getAsJsonPrimitive("port")?.asString
                ?: throw BadRequest("Missing 'port'")
            val success = controller.connect(portName)
            ok(mapOf("ok" to success, "connected" to controller.isConnected()))
        })

        server.createContext("/api/disconnect", handler { ex ->
            ex.requireMethod("POST")
            val success = controller.disconnect()
            ok(mapOf("ok" to success, "connected" to controller.isConnected()))
        })

        server.createContext("/api/target", handler { ex ->
            ex.requireMethod("POST")
            val body = ex.parseJson()
            val temp = body?.getAsJsonPrimitive("temp")?.asFloat
                ?: throw BadRequest("Missing 'temp' (Float)")
            val intensity = body.getAsJsonPrimitiveOrNull("intensity")?.asFloat
                ?: throw BadRequest("Missing 'intensity' (Float 0.0..1.0)")
            val ok = controller.setTargetTemperature(intensity, temp)
            ok(mapOf("ok" to ok, "targetTemperature" to controller.getTargetTemperature(), "intensity" to controller.getIntensity()))
        })

        server.createContext("/api/start", handler { ex ->
            ex.requireMethod("POST")
            val body = ex.parseJson() ?: throw BadRequest("Missing JSON body")

            val profile: ReflowProfile = if (body.has("profile")) {
                // Inline profile payload (matches ReflowProfile/Phase schema)
                gson.fromJson(body.get("profile"), ReflowProfile::class.java)
            } else {
                val profileName = body.getAsJsonPrimitive("profileName")?.asString
                    ?: throw BadRequest("Provide either 'profile' object or 'profileName'")
                loadProfiles().firstOrNull { it.name == profileName }
                    ?: throw NotFound("Profile '$profileName' not found")
            }

            val issues = basicValidateProfile(profile)
            if (issues.isNotEmpty()) throw BadRequest("Profile invalid: $issues")

            val ok = controller.start(profile)
            ok(mapOf(
                "ok" to ok,
                "running" to controller.isRunning(),
                "phase" to controller.getPhase(),
                "mode" to currentMode(),
                "profileName" to profile.name
            ))
        })

        // Optional explicit alias, identical semantics to /api/start
        server.createContext("/api/start-inline", handler { ex ->
            ex.requireMethod("POST")
            val body = ex.parseJson() ?: throw BadRequest("Missing JSON body")
            if (!body.has("profile")) throw BadRequest("Expected 'profile' object")
            val profile = gson.fromJson(body.get("profile"), ReflowProfile::class.java)
            val issues = basicValidateProfile(profile)
            if (issues.isNotEmpty()) throw BadRequest("Profile invalid: $issues")
            val ok = controller.start(profile)
            ok(mapOf("ok" to ok, "running" to controller.isRunning(), "phase" to controller.getPhase(), "mode" to currentMode()))
        })

        // Validate profile without starting
        server.createContext("/api/profiles/validate", handler { ex ->
            ex.requireMethod("POST")
            val body = ex.parseJson() ?: throw BadRequest("Missing JSON body")
            if (!body.has("profile")) throw BadRequest("Expected 'profile' object")
            val profile = gson.fromJson(body.get("profile"), ReflowProfile::class.java)
            val issues = basicValidateProfile(profile)
            ok(mapOf("valid" to issues.isEmpty(), "issues" to issues))
        })

        // Save profile JSON onto the device
        server.createContext("/api/profiles/save", handler { ex ->
            ex.requireMethod("POST")
            val body = ex.parseJson() ?: throw BadRequest("Missing JSON body")
            if (!body.has("profile")) throw BadRequest("Expected 'profile' object")
            val profileEl = body.get("profile")
            val profile = gson.fromJson(profileEl, ReflowProfile::class.java)
            val issues = basicValidateProfile(profile)
            if (issues.isNotEmpty()) throw BadRequest("Profile invalid: $issues")

            val saveName = body.getAsJsonPrimitiveOrNull("fileName")?.asString
                ?: run {
                    // Try to derive from profile.name
                    val n = profile.name
                    (n.ifBlank { "profile" }) + ".json"
                }
            val safeName = saveName.replace("/", "_").replace("..", "_")
            val dir = java.io.File("profiles").apply { if (!exists()) mkdirs() }
            val file = java.io.File(dir, safeName)
            file.writeText(profileEl.toString())
            ok(mapOf("saved" to true, "file" to file.absolutePath))
        })

        server.createContext("/api/stop", handler { ex ->
            ex.requireMethod("POST")
            val ok = controller.stop()
            ok(mapOf("ok" to ok, "running" to controller.isRunning()))
        })

        server.createContext("/api/profiles", handler { _ ->
            val profiles = loadProfiles()
            ok(mapOf("profiles" to profiles))
        })

        server.createContext("/api/logs/messages", handler { _ ->
            ok(mapOf("messages" to Logger.getMessages()))
        })

        server.createContext("/api/logs/states", handler { _ ->
            ok(mapOf("states" to StateLogger.getEntries()))
        })

        server.createContext("/api/logs", handler { ex ->
            if (ex.requestMethod.equals("DELETE", ignoreCase = true)) {
                val ok = controller.clearLogs()
                ok(mapOf("ok" to ok))
            } else if (ex.requestMethod.equals("GET", ignoreCase = true)) {
                ok(
                    mapOf(
                        "messages" to Logger.getMessages(),
                        "states" to StateLogger.getEntries()
                    )
                )
            } else {
                throw MethodNotAllowed("Allowed: GET, DELETE")
            }
        })

        server.executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors().coerceAtLeast(2))
        server.start()
        println("HTTP API started on http://0.0.0.0:$port/api/status")
    }

    fun stop() {
        if (this::server.isInitialized) server.stop(0)
    }

    // ---------------- Helpers ----------------
    private fun handler(block: (HttpExchange) -> Any?): HttpHandler = HttpHandler { ex ->
        try {
            ex.responseHeaders.enableCors()
            if (ex.requestMethod.equals("OPTIONS", ignoreCase = true)) {
                ex.sendResponseHeaders(204, -1) // preflight
                return@HttpHandler
            }

            val result = block(ex)
            when (result) {
                is Response -> ex.sendJson(result.status, result.payload)
                is Map<*, *> -> ex.sendJson(200, result)
                is Collection<*> -> ex.sendJson(200, result)
                is String -> ex.sendJson(200, mapOf("message" to result))
                null -> { /* route already wrote the response */ }
                else -> ex.sendJson(200, result)
            }
        } catch (e: ApiError) {
            safeSendError(ex, e.statusCode, e.message ?: "error")
        } catch (e: Exception) {
            e.printStackTrace()
            safeSendError(ex, 500, e.message ?: "internal error")
        } finally {
            try { ex.responseBody.close() } catch (_: Exception) {}
            try { ex.close() } catch (_: Exception) {}
        }
    }

    private fun safeSendError(ex: HttpExchange, code: Int, msg: String) {
        try { ex.sendJson(code, mapOf("error" to msg)) } catch (_: Exception) {}
    }

    private fun ok(payload: Any) = Response(200, payload)

    private fun currentMode(): String {
        return try {
            if (hasIsManual) {
                val isManual = (invokeNoArg(controller, "isManual") as? Boolean)
                    ?: (invokeNoArg(controller, "isManualMode") as? Boolean)
                if (isManual == true) return "manual"
            }
            if (controller.isRunning()) return "profile"
            "idle"
        } catch (_: Exception) { if (controller.isRunning()) "profile" else "idle" }
    }

    private inner class Response(val status: Int, val payload: Any)

    private fun HttpExchange.sendJson(status: Int, payload: Any) {
        val json = gson.toJson(payload)
        this.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        this.sendResponseHeaders(status, json.toByteArray(StandardCharsets.UTF_8).size.toLong())
        this.responseBody.use { os: OutputStream ->
            os.write(json.toByteArray(StandardCharsets.UTF_8))
        }
    }

    private fun HttpExchange.parseJson(): JsonObject? {
        if (this.requestBody == null) return null
        InputStreamReader(this.requestBody, StandardCharsets.UTF_8).use {
            val text = it.readText().trim()
            if (text.isEmpty()) return null
            return JsonParser.parseString(text).asJsonObject
        }
    }

    private fun JsonObject?.getAsJsonPrimitiveOrNull(key: String) = this?.getAsJsonPrimitive(key)

    private fun HttpExchange.requireMethod(expected: String) {
        if (!this.requestMethod.equals(expected, ignoreCase = true)) throw MethodNotAllowed("Method $expected required")
    }

    private fun Headers.enableCors() {
        this.add("Access-Control-Allow-Origin", "*")
        this.add("Access-Control-Allow-Headers", "Content-Type, Authorization")
        this.add("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS")
    }

    // ---- Profile validation aligned to ReflowProfile/Phase schema ----
    private fun basicValidateProfile(profile: ReflowProfile): List<String> {
        val issues = mutableListOf<String>()
        if (profile.name.isBlank()) issues += "name: must not be blank"
        val phases: List<Pair<String, Phase>> = listOf(
            "preheat" to profile.preheat,
            "soak" to profile.soak,
            "reflow" to profile.reflow
        )
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
    target::class.java.getMethod(name)
    true
} catch (_: NoSuchMethodException) { false }

private fun invokeNoArg(target: Any, name: String): Any? = try {
    val m = target::class.java.getMethod(name)
    m.isAccessible = true
    m.invoke(target)
} catch (e: Exception) { throw RuntimeException(e) }

// ---- Error machinery ----
private open class ApiError(message: String, val statusCode: Int) : RuntimeException(message)
private class BadRequest(message: String) : ApiError(message, 400)
private class NotFound(message: String) : ApiError(message, 404)
private class MethodNotAllowed(message: String) : ApiError(message, 405)
