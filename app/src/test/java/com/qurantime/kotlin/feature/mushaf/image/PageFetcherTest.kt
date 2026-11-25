package com.qurantime.kotlin.feature.mushaf.image

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.GzipSink
import okio.buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PageFetcherTest {
    @get:Rule val tmp = TemporaryFolder()
    private val server = MockWebServer()

    @After fun tearDown() = server.shutdown()

    private suspend fun expectIOException(block: suspend () -> Unit) {
        try {
            block()
            fail("expected IOException")
        } catch (_: IOException) {
        }
    }

    @Test
    fun `writes the body to the target`() = runTest {
        val svg = "<svg viewBox=\"0 0 345 550\"/>"
        val gz = Buffer().also { b -> GzipSink(b).buffer().use { it.writeUtf8(svg) } }
        server.enqueue(MockResponse().setHeader("Content-Encoding", "gzip").setBody(gz))
        val target = File(tmp.root, "003.svg")
        OkHttpPageFetcher(OkHttpClient()).fetch(server.url("/003.svg").toString(), target)
        assertEquals(svg, target.readText())
    }

    @Test
    fun `404 throws and leaves no file`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("nope"))
        val target = File(tmp.root, "605.svg")
        expectIOException { OkHttpPageFetcher(OkHttpClient()).fetch(server.url("/605.svg").toString(), target) }
        assertFalse(target.exists())
    }

    @Test
    fun `stalled body times out`() = runTest {
        server.enqueue(MockResponse().setBody("x".repeat(10_000)).throttleBody(10, 1, TimeUnit.SECONDS))
        val client = OkHttpClient.Builder().readTimeout(1, TimeUnit.SECONDS).build()
        val target = File(tmp.root, "004.svg")
        expectIOException { OkHttpPageFetcher(client).fetch(server.url("/004.svg").toString(), target) }
    }
}
