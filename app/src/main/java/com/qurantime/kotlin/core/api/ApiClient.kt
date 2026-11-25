package com.qurantime.kotlin.core.api

import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class ApiException(val statusCode: Int?, message: String) : Exception(message) {
    override fun toString(): String = "ApiException(${statusCode ?: "-"}): $message"
}

/** Reads JSON from the MP3Quran API. [path] is relative, e.g. `reciters`. */
fun interface Api {
    suspend fun getJson(path: String, query: Map<String, Any?>): JsonElement
}

suspend fun Api.getJson(path: String): JsonElement = getJson(path, emptyMap())

/** The shared OkHttp client: one connection pool and cache of DNS for the app. */
fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()

/**
 * Fetches API JSON. Timeouts and 5xx responses are retried after
 * [retryDelays] (ms); other failures throw [ApiException] at once.
 */
class ApiClient(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl = API_BASE.toHttpUrl(),
    private val retryDelays: List<Long> = listOf(500, 1500),
) : Api {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getJson(path: String, query: Map<String, Any?>): JsonElement {
        val url = baseUrl.newBuilder().addPathSegments(path).apply {
            for ((k, v) in query) if (v != null) addQueryParameter(k, v.toString())
        }.build()
        var attempt = 0
        while (true) {
            val outcome = try {
                client.newCall(Request.Builder().url(url).build()).await().use { res ->
                    val body = res.body.string()
                    if (res.isSuccessful) {
                        try {
                            val parsed = json.parseToJsonElement(body)
                            // The API only answers with objects or arrays; anything
                            // else (an HTML page read as a bare literal) is an error.
                            if (parsed !is JsonObject && parsed !is JsonArray) {
                                throw ApiException(res.code, "Invalid JSON from $path")
                            }
                            return parsed
                        } catch (e: SerializationException) {
                            throw ApiException(res.code, "Invalid JSON from $path: ${e.message}")
                        } catch (e: IllegalArgumentException) {
                            throw ApiException(res.code, "Invalid JSON from $path: ${e.message}")
                        }
                    }
                    Failure(res.code, res.message.ifEmpty { "HTTP ${res.code}" }, retryable = res.code >= 500)
                }
            } catch (e: InterruptedIOException) {
                Failure(null, e.message ?: "timeout", retryable = true)
            } catch (e: IOException) {
                Failure(null, e.message ?: e.javaClass.simpleName, retryable = false)
            }
            if (outcome.retryable && attempt < retryDelays.size) {
                delay(retryDelays[attempt++])
                continue
            }
            throw ApiException(outcome.status, outcome.message)
        }
    }

    private class Failure(val status: Int?, val message: String, val retryable: Boolean)
}

/** Runs the call on OkHttp's pool; cancelling the coroutine cancels the call. */
suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            cont.resume(response) { _, r, _ -> r.close() }
        }

        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }
    })
}
