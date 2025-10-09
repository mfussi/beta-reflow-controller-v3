// File: src/main/java/com/tangentlines/reflowcontroller/client/HttpJson.kt
package com.tangentlines.reflowcontroller.client

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Tiny, dependency-free HTTP JSON client with typed helpers.
 * Usage:
 *   val http = HttpJson("http://host:port")
 *   val dto: MyDto = http.get("/api/status")
 *   val resp: RespDto = http.post("/api/connect", ConnectDto(...))
 */
class HttpJson(private val baseUrl: String,
               private val connectTimeoutMs: Int = 5000,
               private val readTimeoutMs: Int = 15000,
               private val defaultHeaders: Map<String,String> = emptyMap()) {

    private val gson = Gson()

    // ---- Typed helpers (reified) ----
    inline fun <reified T> get(path: String): T = get(path, T::class.java)
    inline fun <reified T> delete(path: String): T = delete(path, T::class.java)
    inline fun <reified T> post(path: String, payload: Any?): T = post(path, payload, T::class.java)

    // ---- Typed helpers (Class<T>) ----
    fun <T> get(path: String, clazz: Class<T>): T = request("GET", path, null, clazz)
    fun <T> delete(path: String, clazz: Class<T>): T = request("DELETE", path, null, clazz)
    fun <T> post(path: String, payload: Any?, clazz: Class<T>): T = request("POST", path, payload, clazz)

    // ---- Raw fallbacks (JsonObject) ----
    fun getRaw(path: String): JsonObject = requestRaw("GET", path, null)
    fun deleteRaw(path: String): JsonObject = requestRaw("DELETE", path, null)
    fun postRaw(path: String, payload: Any?): JsonObject = requestRaw("POST", path, payload)

    // ---- Core ----
    private fun <T> request(method: String, path: String, payload: Any?, clazz: Class<T>): T {
        val text = requestText(method, path, payload)
        if (text.isBlank()) throw HttpException(204, "Empty response")
        return gson.fromJson(text, clazz)
    }

    private fun requestRaw(method: String, path: String, payload: Any?): JsonObject {
        val text = requestText(method, path, payload)
        if (text.isBlank()) return JsonObject()
        val je = JsonParser.parseString(text)
        return if (je.isJsonObject) je.asJsonObject else JsonObject().apply { addProperty("raw", text) }
    }

    private fun requestText(method: String, path: String, payload: Any?): String {
        val url = URL(baseUrl.trimEnd('/') + path)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Connection", "close")
            defaultHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
            useCaches = false
            doInput = true
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            if (payload != null) doOutput = true
        }

        if (payload != null) {
            val json = when (payload) {
                is String -> payload
                is JsonElement -> payload.toString()
                else -> gson.toJson(payload)
            }
            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(json) }
        }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = if (stream != null) BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() } else ""
        conn.disconnect()
        if (code !in 200..299) throw HttpException(code, text)
        return text
    }

    class HttpException(val code: Int, val body: String)
        : RuntimeException("HTTP $code: ${body.take(256)}")

    companion object {
        fun basicAuthHeader(username: String = "client", password: String): String {
            val token = java.util.Base64.getEncoder().encodeToString(
                "$username:$password".toByteArray(StandardCharsets.UTF_8)
            )
            return "Basic $token"
        }
    }

}
