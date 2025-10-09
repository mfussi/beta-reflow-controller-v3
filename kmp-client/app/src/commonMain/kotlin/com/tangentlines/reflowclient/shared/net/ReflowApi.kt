package com.tangentlines.reflowclient.shared.net

import com.tangentlines.reflowclient.shared.model.*
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.date.getTimeMillis
import kotlinx.serialization.json.Json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

class ApiException(code: Int, msg: String): Exception("HTTP $code: $msg")

class ReflowApi(
    private val baseUrl: String,
    private val clientKey: (() -> String?)? = null,
    engine: HttpClientEngine? = null
) {
    private val client = HttpClient(engine ?: defaultEngine()) {
        install(Logging) { level = LogLevel.INFO }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
        install(HttpTimeout) { requestTimeoutMillis = 10000; connectTimeoutMillis = 10000; socketTimeoutMillis = 10000 }
        defaultRequest {
            url(baseUrl)
            clientKey?.invoke()?.takeIf { it.isNotBlank() }?.let {
                val token = kotlin.io.encoding.Base64.Default.encode(it.encodeToByteArray())
                headers.append(HttpHeaders.Authorization, "Basic $token")
            }
        }
    }

    // ----- status() cache -----
    private val statusMutex = Mutex()
    @Volatile private var lastStatus: StatusDto? = null
    @Volatile private var lastStatusAt: Long = 0L
    private val statusTtlMs = 1000L

    private fun invalidateStatusCache() {
        lastStatus = null
        lastStatusAt = 0L
    }

    suspend fun status(force : Boolean = false): StatusDto = statusMutex.withLock {
        val now = getTimeMillis()
        val cached = lastStatus
        if (force == false && cached != null && (now - lastStatusAt) < statusTtlMs) {
            return cached
        }
        // fetch fresh
        val fresh: StatusDto = getJson("/api/status")
        lastStatus = fresh
        lastStatusAt = getTimeMillis()
        return fresh
    }

    suspend fun ports(): PortsResponse = getJson("/api/ports")

    suspend fun connect(port: String): ConnectResponse {
        val res: ConnectResponse = postJson("/api/connect", ConnectRequest(port))
        invalidateStatusCache()
        return res
    }

    suspend fun disconnect(): AckResponse {
        val res: AckResponse = postJson("/api/disconnect", Unit)
        invalidateStatusCache()
        return res
    }

    suspend fun startByName(name: String): StartResponse {
        val res: StartResponse = postJson("/api/start", StartRequest(profileName = name))
        invalidateStatusCache()
        return res
    }

    suspend fun startInline(profile: ReflowProfile): StartResponse {
        val res: StartResponse = postJson("/api/start", StartRequest(profile = profile))
        invalidateStatusCache()
        return res
    }

    suspend fun startManual(temp: Float?, intensity: Float?): Boolean {
        val res: Boolean = postJson("/api/start", StartRequest(temp = temp, intensity = intensity))
        invalidateStatusCache()
        return res
    }

    suspend fun clearLogs() : Boolean {
        val res = client.request {
            method = HttpMethod.Delete
            url("/api/logs")
        }
        if (!res.status.isSuccess()) throw ApiException(res.status.value, res.bodyAsText())
        val ack: AckResponse = res.body()
        return ack.ok
    }

    suspend fun deleteProfile(name : String) : Boolean {
        val res = client.request {
            method = HttpMethod.Delete
            url("/api/profiles/${name}")
        }
        if (!res.status.isSuccess()) throw ApiException(res.status.value, res.bodyAsText())
        val ack: DeleteProfileResponse = res.body()
        return ack.success
    }

    suspend fun validateProfile(profile: ReflowProfile): ValidateProfileResponse =
        postJson("/api/profiles/validate", ValidateProfileRequest(profile))

    suspend fun saveProfile(profile: ReflowProfile, fileName: String? = null): SaveProfileResponse =
        postJson("/api/profiles/save", SaveProfileRequest(profile, fileName))

    suspend fun stop(): AckResponse {
        val res: AckResponse = postJson("/api/stop", Unit)
        invalidateStatusCache()
        return res
    }

    suspend fun set(temp: Float, intensity: Float): ManualSetResponse {
        val res: ManualSetResponse = postJson("/api/set", ManualSetRequest(temp, intensity))
        invalidateStatusCache()
        return res
    }

    suspend fun profiles(): ProfilesResponse = getJson("/api/profiles")
    suspend fun logMessages(): MessagesResponse = getJson("/api/logs/messages")
    suspend fun logStates(): StatesResponse = getJson("/api/logs/states")

    private suspend inline fun <reified T> getJson(path: String): T {
        val res = client.get { url(path) }
        if (!res.status.isSuccess()) throw ApiException(res.status.value, res.bodyAsText())
        return res.body()
    }

    private suspend inline fun <reified Req: Any, reified T> postJson(path: String, body: Req): T {
        val res = client.post { url(path); contentType(ContentType.Application.Json); setBody(body) }
        if (!res.status.isSuccess()) throw ApiException(res.status.value, res.bodyAsText())
        return res.body()
    }

}

expect fun defaultEngine(): HttpClientEngine
