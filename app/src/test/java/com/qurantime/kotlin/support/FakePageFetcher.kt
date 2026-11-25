package com.qurantime.kotlin.support

import com.qurantime.kotlin.feature.mushaf.image.PageFetcher
import java.io.File
import java.io.IOException
import kotlinx.coroutines.yield

/**
 * Serves the real page-003 SVG for any page. Records every URL; [failNext]
 * makes the next fetches write half a file and throw, like a dropped link.
 */
class FakePageFetcher : PageFetcher {
    val fetched: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())
    var failNext = false
    var failPages: Set<Int> = emptySet()
    var error: () -> IOException = { IOException("connection reset") }

    private val svg: ByteArray by lazy {
        FakePageFetcher::class.java.classLoader!!.getResourceAsStream("svg/page-003.svg")!!.readBytes()
    }

    override suspend fun fetch(url: String, target: File) {
        fetched += url
        yield()
        val page = Regex("""(\d+)\.svg$""").find(url)!!.groupValues[1].toInt()
        if (failNext || page in failPages) {
            target.writeText("<svg half")
            target.delete()
            throw error()
        }
        target.writeBytes(svg)
    }

    fun fetchedPages(): List<String> = fetched.map { it.takeLast(7) }
}
