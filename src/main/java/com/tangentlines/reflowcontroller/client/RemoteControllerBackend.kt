// File: src/main/java/com/tangentlines/reflowcontroller/client/RemoteControllerBackend.kt
package com.tangentlines.reflowcontroller.client

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.tangentlines.reflowcontroller.log.LogEntry
import com.tangentlines.reflowcontroller.log.State
import com.tangentlines.reflowcontroller.reflow.profile.ReflowProfile

class RemoteControllerBackend(host: String, port: Int) : ControllerBackend {
    private val base = "http://$host:$port"
    private val http = HttpJson(base)
    private val gson = Gson()

    override fun availablePorts(): List<String> {
        val json = http.get("/api/ports")
        val arr = json.getAsJsonArray("ports") ?: return emptyList()
        return arr.map { it.asString }
    }

    override fun connect(portName: String): Boolean {
        val json = http.post("/api/connect", mapOf("port" to portName))
        return json.getAsJsonPrimitive("ok")?.asBoolean ?: false
    }

    override fun disconnect(): Boolean {
        val json = http.post("/api/disconnect", null)
        return json.getAsJsonPrimitive("ok")?.asBoolean ?: false
    }

    override fun manualStart(temp: Float?, intensity: Float?): Boolean {
        val payload = JsonObject().apply {
            if (temp != null) addProperty("temp", temp)
            if (intensity != null) addProperty("intensity", intensity)
        }
        val json = http.post("/api/manual/start", if (payload.entrySet().isEmpty()) null else payload)
        return (json.get("mode")?.asString ?: "") == "manual"
    }

    override fun manualSet(temp: Float, intensity: Float): Boolean {
        val json = http.post("/api/manual/set", mapOf("temp" to temp, "intensity" to intensity))
        return json.getAsJsonPrimitive("ok")?.asBoolean ?: false
    }

    override fun manualStop(): Boolean {
        val json = http.post("/api/manual/stop", null)
        return (json.get("mode")?.asString ?: "") != "manual"
    }

    override fun startProfileByName(profileName: String): Boolean {
        val json = http.post("/api/start", mapOf("profileName" to profileName))
        return json.getAsJsonPrimitive("ok")?.asBoolean ?: false
    }

    override fun startProfileInline(profile: ReflowProfile): Boolean {
        val json = http.post("/api/start", mapOf("profile" to gson.toJsonTree(profile)))
        return json.getAsJsonPrimitive("ok")?.asBoolean ?: false
    }

    override fun stop(): Boolean {
        val json = http.post("/api/stop", null)
        return json.getAsJsonPrimitive("ok")?.asBoolean ?: false
    }

    override fun setTargetTemperature(intensity: Float, temp: Float): Boolean {
        val json = http.post("/api/target", mapOf("temp" to temp, "intensity" to intensity))
        return json.getAsJsonPrimitive("ok")?.asBoolean ?: false
    }

    override fun status(): StatusDto {
        val json = http.get("/api/status")
        return StatusDto(
            connected = json.getAsJsonPrimitive("connected")?.asBoolean,
            running = json.getAsJsonPrimitive("running")?.asBoolean,
            phase = json.getAsJsonPrimitive("phase")?.asString,
            mode = json.getAsJsonPrimitive("mode")?.asString,
            temperature = json.getAsJsonPrimitive("temperature")?.asFloat,
            targetTemperature = json.getAsJsonPrimitive("targetTemperature")?.asFloat,
            intensity = json.getAsJsonPrimitive("intensity")?.asFloat,
            activeIntensity = json.getAsJsonPrimitive("activeIntensity")?.asFloat,
            timeAlive = json.getAsJsonPrimitive("timeAlive")?.asLong,
            timeSinceTempOver = json.getAsJsonPrimitive("timeSinceTempOver")?.asLong,
            timeSinceCommand = json.getAsJsonPrimitive("timeSinceCommand")?.asLong,
            controllerTimeAlive = json.getAsJsonPrimitive("controllerTimeAlive")?.asInt,
            finished = json.getAsJsonPrimitive("finished")?.asBoolean,
            profile = json.getAsJsonPrimitive("profile")?.asString
        )
    }

    override fun logs(): LogsDto {
        val json = http.get("/api/logs")
        val messages = json.getAsJsonArray("messages")?.map { it.asJsonObject }?.map { gson.fromJson<LogEntry>(it, LogEntry::class.java) } ?: emptyList()
        val states = json.getAsJsonArray("states")?.map { it.asJsonObject }?.map { gson.fromJson<State>(it, State::class.java) } ?: emptyList()
        return LogsDto(messages, states)
    }

    override fun clearLogs(): Boolean {
        val json = http.delete("/api/logs")
        return json.getAsJsonPrimitive("ok")?.asBoolean ?: false
    }
}
