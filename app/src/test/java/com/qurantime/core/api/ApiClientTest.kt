package com.qurantime.core.api

import okio.buffer
import okhttp3.ResponseBody.Companion.asResponseBody
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ApiClientTest {
    private val server = MockWebServer()

    @After fun tearDown() = server.shutdown()

    private fun serve(respond: (Int) -> MockResponse) {
        var n = 0
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = respond(n++)
        }
        server.start()
    }

    private fun always(code: Int, body: String) = serve { MockResponse().setResponseCode(code).setBody(body) }

    private fun client() = ApiClient(OkHttpClient(), server.url("/api/v3/"), retryDelays = listOf(0, 0))

    private suspend fun expectApiException(status: Int?, block: suspend () -> Unit) {
        try {
            block()
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(status, e.statusCode)
        }
    }

    @Test
    fun `builds url and drops null params`() = runTest {
        always(200, """{"reciters":[]}""")
        client().getJson("reciters", mapOf("language" to "eng", "sura" to 18, "rewaya" to null))
        assertEquals("/api/v3/reciters?language=eng&sura=18", server.takeRequest().path)
    }

    @Test
    fun `returns decoded json even when served as text html`() = runTest {
        serve { MockResponse().setHeader("Content-Type", "text/html").setBody("""{"suwar":[]}""") }
        val json = client().getJson("suwar")
        assertTrue(json is JsonObject)
        assertTrue(json.jsonObject["suwar"]!!.jsonArray.isEmpty())
    }

    @Test
    fun `bare array`() = runTest {
        always(200, """[{"id":5}]""")
        val json = client().getJson("ayat_timing/reads")
        assertTrue(json is JsonArray)
        assertEquals(5, json.jsonArray.single().jsonObject["id"]!!.jsonPrimitive.int)
    }

    @Test
    fun `retries 5xx twice then throws`() = runTest {
        always(503, "down")
        expectApiException(503) { client().getJson("suwar") }
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `succeeds after one 5xx`() = runTest {
        serve { n -> if (n == 0) MockResponse().setResponseCode(500).setBody("x") else MockResponse().setBody("""{"ok":1}""") }
        val json = client().getJson("suwar")
        assertEquals(1, json.jsonObject["ok"]!!.jsonPrimitive.int)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `no retry on 404`() = runTest {
        always(404, "nope")
        expectApiException(404) { client().getJson("nothing") }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `invalid json throws ApiException`() = runTest {
        always(200, "<html>")
        expectApiException(200) { client().getJson("suwar") }
    }

    // Review I1: callers are often on the main thread; reading the body and
    // parsing it must not happen there.
    @Test
    fun `reads and parses off the caller's thread`() {
        server.enqueue(MockResponse().setBody("""{"suwar":[]}"""))
        var readOn: String? = null
        val ok = OkHttpClient.Builder().addNetworkInterceptor { chain ->
            val res = chain.proceed(chain.request())
            val body = res.body
            val src = object : okio.ForwardingSource(body.source()) {
                override fun read(sink: okio.Buffer, byteCount: Long): Long {
                    readOn = Thread.currentThread().name
                    return super.read(sink, byteCount)
                }
            }
            res.newBuilder().body(src.buffer().asResponseBody(body.contentType(), body.contentLength())).build()
        }.build()
        val caller = java.util.concurrent.Executors.newSingleThreadExecutor { Thread(it, "caller") }.asCoroutineDispatcher()
        kotlinx.coroutines.runBlocking(caller) { ApiClient(ok, server.url("/api/v3/")).getJson("suwar") }
        caller.close()
        assertTrue("body read on $readOn", readOn != null && !readOn!!.startsWith("caller"))
    }
}
