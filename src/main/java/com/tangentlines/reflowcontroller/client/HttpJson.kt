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

internal class HttpJson(private val baseUrl: String, private val connectTimeoutMs: Int = 5000, private val readTimeoutMs: Int = 10000) {
    private val gson = Gson()

    fun get(path: String): JsonObject = request("GET", path, null)
    fun delete(path: String): JsonObject = request("DELETE", path, null)
    fun post(path: String, payload: Any?): JsonObject = request("POST", path, payload)

    private fun request(method: String, path: String, payload: Any?): JsonObject {
        val url = URL(baseUrl.trimEnd('/') + path)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Connection", "close")
            useCaches = false
            doInput = true
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            if (payload != null) {
                doOutput = true
            }
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

        val je = if (text.isBlank()) JsonObject() else JsonParser.parseString(text)
        if (!je.isJsonObject) {
            val o = JsonObject()
            o.addProperty("raw", text)
            return o
        }
        return je.asJsonObject
    }
}
