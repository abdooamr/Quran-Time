package com.qurantime.feature.mushaf.image

import com.qurantime.core.api.await
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink

/** Downloads [url] into [target]. Throws [IOException] on failure and leaves no file behind. */
fun interface PageFetcher {
    suspend fun fetch(url: String, target: File)
}

/**
 * The production fetcher. OkHttp asks for gzip and unpacks it, so a 550 KB
 * page crosses the network as about 150 KB. The client's read timeout ends
 * a stalled download.
 */
class OkHttpPageFetcher(private val client: OkHttpClient) : PageFetcher {
    override suspend fun fetch(url: String, target: File) {
        try {
            client.newCall(Request.Builder().url(url).build()).await().use { res ->
                if (!res.isSuccessful) throw IOException("HTTP ${res.code} for $url")
                withContext(Dispatchers.IO) {
                    target.sink().buffer().use { it.writeAll(res.body.source()) }
                }
            }
        } catch (e: CancellationException) {
            target.delete()
            throw e
        } catch (e: IOException) {
            target.delete()
            throw e
        }
    }
}
