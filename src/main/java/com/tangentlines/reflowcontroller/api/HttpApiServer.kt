// File: src/main/java/com/tangentlines/reflowcontroller/api/HttpApiServer.kt
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

        server.createContext("/api/status", handler { _ -> ok(buildStatusDto()) })

        server.createContext("/api/manual/start", handler { ex ->
            ex.requireMethod("POST")
            if (!hasStartManual) throw NotFound("Manual mode not supported by this build")
            invokeNoArg(controller, "startManual")
            val dto = try { ex.readDto(ManualStartRequest::class.java) } catch (_: Exception) { null }
            val temp = dto?.temp
            val intensity = dto?.intensity
            if (temp != null && intensity != null) controller.setTargetTemperature(intensity, temp)
            ok(ManualStartResponse(currentMode(), controller.getTargetTemperature(), controller.getIntensity()))
        })

        server.createContext("/api/manual/set", handler { ex ->
            ex.requireMethod("POST")
            val dto = ex.readDto(ManualSetRequest::class.java)
            if (currentMode() != "manual") throw BadRequest("Not in manual mode")
            val okSet = controller.setTargetTemperature(dto.intensity, dto.temp)
            ok(ManualSetResponse(okSet, controller.getTargetTemperature(), controller.getIntensity()))
        })

        server.createContext("/api/manual/stop", handler { _ ->
            if (!hasStopManual) throw NotFound("Manual mode not supported by this build")
            invokeNoArg(controller, "stopManual")
            ok(ManualStopResponse(currentMode()))
        })

        server.createContext("/api/ports", handler { _ -> ok(PortsResponse(controller.availablePorts())) })

        server.createContext("/api/connect", handler { ex ->
            ex.requireMethod("POST")
            val dto = ex.readDto(ConnectRequest::class.java)
            val success = controller.connect(dto.port)
            ok(ConnectResponse(success, controller.isConnected()))
        })

        server.createContext("/api/disconnect", handler { ex ->
            ex.requireMethod("POST")
            val success = controller.disconnect()
            ok(DisconnectResponse(success, controller.isConnected()))
        })

        server.createContext("/api/target", handler { ex ->
            ex.requireMethod("POST")
            val dto = ex.readDto(TargetRequest::class.java)
            val okSet = controller.setTargetTemperature(dto.intensity, dto.temp)
            ok(TargetResponse(okSet, controller.getTargetTemperature(), controller.getIntensity()))
        })

        server.createContext("/api/start", handler { ex ->
            ex.requireMethod("POST")
            val dto = ex.readDto(StartRequest::class.java)
            val profile: ReflowProfile = when {
                dto.profile != null -> dto.profile
                !dto.profileName.isNullOrBlank() -> loadProfiles().firstOrNull { it.name == dto.profileName }
                    ?: throw NotFound("Profile '${dto.profileName}' not found")
                else -> throw BadRequest("Provide either 'profile' or 'profileName'")
            }
            val issues = basicValidateProfile(profile)
            if (issues.isNotEmpty()) throw BadRequest("Profile invalid: $issues")
            val okStart = controller.start(profile)
            ok(StartResponse(okStart, controller.isRunning(), controller.getPhase(), currentMode(), profile.name))
        })

        server.createContext("/api/start-inline", handler { ex ->
            ex.requireMethod("POST")
            val dto = ex.readDto(StartRequest::class.java)
            val profile = dto.profile ?: throw BadRequest("Expected 'profile' object")
            val issues = basicValidateProfile(profile)
            if (issues.isNotEmpty()) throw BadRequest("Profile invalid: $issues")
            val okStart = controller.start(profile)
            ok(StartResponse(okStart, controller.isRunning(), controller.getPhase(), currentMode(), profile.name))
        })

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
            file.writeText(gson.toJson(dto.profile))
            ok(SaveProfileResponse(true, file.absolutePath))
        })

        server.createContext("/api/stop", handler { ex ->
            ex.requireMethod("POST")
            val okStop = controller.stop()
            ok(StopResponse(okStop, controller.isRunning()))
        })

        server.createContext("/api/profiles", handler { _ -> ok(ProfilesResponse(loadProfiles())) })
        server.createContext("/api/logs/messages", handler { _ -> ok(MessagesResponse(Logger.getMessages())) })
        server.createContext("/api/logs/states", handler { _ -> ok(StatesResponse(StateLogger.getEntries())) })

        server.createContext("/api/logs", handler { ex ->
            if (ex.requestMethod.equals("DELETE", ignoreCase = true)) {
                ok(AckResponse(controller.clearLogs()))
            } else if (ex.requestMethod.equals("GET", ignoreCase = true)) {
                ok(LogsCombinedResponse(Logger.getMessages(), StateLogger.getEntries()))
            } else throw MethodNotAllowed("Allowed: GET, DELETE")
        })

        server.executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors().coerceAtLeast(2))
        server.start()
        println("HTTP API started on http://0.0.0.0:$port/api/status")
    }

    fun stop() { if (this::server.isInitialized) server.stop(0) }

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
                null -> {}
                else -> ex.sendJson(200, result)
            }
        } catch (e: ApiError) {
            safeSendError(ex, e.statusCode, e.message ?: "error")
        } catch (e: Exception) {
            e.printStackTrace(); safeSendError(ex, 500, e.message ?: "internal error")
        } finally {
            try { ex.responseBody.close() } catch (_: Exception) {}
            try { ex.close() } catch (_: Exception) {}
        }
    }

    private fun safeSendError(ex: HttpExchange, code: Int, msg: String) {
        try { ex.sendJson(code, mapOf("error" to msg)) } catch (_: Exception) {}
    }

    private fun ok(payload: Any) = Response(200, payload)

    private fun buildStatusDto(): StatusDto = StatusDto(
        connected = controller.isConnected(),
        running = controller.isRunning(),
        phase = controller.getPhase(),
        mode = currentMode(),
        temperature = controller.getTemperature(),
        targetTemperature = controller.getTargetTemperature(),
        intensity = controller.getIntensity(),
        activeIntensity = controller.getActiveIntensity(),
        timeAlive = controller.getTime(),
        timeSinceTempOver = controller.getTimeSinceTempOver(),
        timeSinceCommand = controller.getTimeSinceCommand(),
        controllerTimeAlive = controller.getControllerTimeAlive(),
        profile = controller.getProfile(),
        finished = controller.isFinished()
    )

    private fun currentMode(): String {
        return try {
            if (hasIsManual) {
                val isManual = (invokeNoArg(controller, "isManual") as? Boolean) ?: (invokeNoArg(controller, "isManualMode") as? Boolean)
                if (isManual == true) return "manual"
            }
            if (controller.isRunning()) "profile" else "idle"
        } catch (_: Exception) { if (controller.isRunning()) "profile" else "idle" }
    }

    private inner class Response(val status: Int, val payload: Any)

    private fun HttpExchange.sendJson(status: Int, payload: Any) {
        val json = gson.toJson(payload)
        responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        sendResponseHeaders(status, json.toByteArray(StandardCharsets.UTF_8).size.toLong())
        responseBody.use { it.write(json.toByteArray(StandardCharsets.UTF_8)) }
    }

    private fun <T> HttpExchange.readDto(clazz: Class<T>): T {
        InputStreamReader(requestBody, StandardCharsets.UTF_8).use {
            val text = it.readText().trim()
            if (text.isEmpty()) throw BadRequest("Missing JSON body")
            try { return gson.fromJson(text, clazz) } catch (e: Exception) {
                throw BadRequest("Invalid JSON: ${e.message}")
            }
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
