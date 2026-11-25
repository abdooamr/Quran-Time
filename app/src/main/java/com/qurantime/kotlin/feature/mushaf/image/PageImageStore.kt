package com.qurantime.kotlin.feature.mushaf.image

import androidx.compose.ui.geometry.Size
import com.qurantime.kotlin.core.api.MUSHAF_PAGES
import com.qurantime.kotlin.core.api.mushafPageUrl
import com.qurantime.kotlin.feature.mushaf.viewBoxForPage
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class PageUnavailableException(val page: Int, cause: Throwable? = null) :
    Exception("page $page unavailable: ${cause?.message}", cause)

/**
 * A page's mask file. [exact] is false when the file was made for another
 * screen width (a rotation or a fold); it still shows, scaled, until
 * [PageImageStore.remake] replaces it.
 */
data class MaskResult(val file: File, val bucket: Int, val exact: Boolean)

data class PackProgress(val done: Int, val total: Int, val failed: Int)

/**
 * Keeps Mushaf pages on disk as ready-to-draw masks:
 * `<root>/mushaf/w<bucket>/NNN.qtr`, plus the last [svgLruSize] SVGs gzipped
 * in `<root>/mushaf/svg/` for sharp zoom. Every file is written to `.part`
 * and renamed only once complete; a damaged mask is re-made.
 */
class PageImageStore(
    root: File,
    private val fetcher: PageFetcher,
    private val codec: MaskCodec,
    private val rasterize: (InputStream, Size, Int) -> PageMask = SvgRasterizer::render,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val cpu: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(2),
    private val svgLruSize: Int = 30,
) {
    private val dir = File(root, "mushaf")
    private val svgDir = File(dir, "svg")
    private val work = CoroutineScope(SupervisorJob() + io)
    private val lock = Mutex()
    private val inflight = HashMap<Pair<Int, Int>, Deferred<MaskResult>>()
    private var lastStamp = 0L

    private fun name(page: Int) = page.toString().padStart(3, '0')
    private fun maskFile(bucket: Int, page: Int) = File(dir, "w$bucket/${name(page)}.${codec.ext}")
    private fun svgFile(page: Int) = File(svgDir, "${name(page)}.svg.gz")
    private fun bucketDirs(): List<File> = dir.listFiles { f -> f.isDirectory && f.name.startsWith("w") }?.toList().orEmpty()

    /** Whether any mask of [page] is on disk (for any screen width). */
    fun isCached(page: Int): Boolean = bucketDirs().any { File(it, "${name(page)}.${codec.ext}").exists() }

    /**
     * The mask of [page] for a page area [widthPx] wide: from disk when there,
     * otherwise downloaded and converted. Throws [PageUnavailableException].
     */
    suspend fun mask(page: Int, widthPx: Int): MaskResult {
        require(page in 1..MUSHAF_PAGES) { "page $page" }
        val bucket = renderWidthBucket(widthPx)
        val onDisk = withContext(io) {
            val exact = maskFile(bucket, page)
            if (exact.exists()) {
                if (isValidMask(exact)) return@withContext MaskResult(exact, bucket, true)
                exact.delete()
            }
            for (d in bucketDirs()) {
                val other = File(d, exact.name)
                if (!other.exists() || other.parentFile == exact.parentFile) continue
                if (isValidMask(other)) return@withContext MaskResult(other, d.name.drop(1).toInt(), false)
                other.delete()
            }
            null
        }
        return onDisk ?: make(page, bucket)
    }

    /** Makes [page]'s mask for [widthPx] even if another width's mask exists, and removes the old one. */
    suspend fun remake(page: Int, widthPx: Int): MaskResult = make(page, renderWidthBucket(widthPx))

    /** Fetches the [radius] pages on each side of [page] that aren't on disk. Errors are ignored. */
    suspend fun prefetchAround(page: Int, widthPx: Int, radius: Int = 2) = coroutineScope {
        ((page - radius)..(page + radius))
            .filter { it != page && it in 1..MUSHAF_PAGES && !isCached(it) }
            .map { q -> async { runCatching { mask(q, widthPx) } } }
            .awaitAll()
        Unit
    }

    /** Downloads and converts every page not on disk, [concurrency] at a time. */
    fun downloadAll(widthPx: Int, concurrency: Int = 3): Flow<PackProgress> = channelFlow {
        val missing = (1..MUSHAF_PAGES).filter { !isCached(it) }
        var done = MUSHAF_PAGES - missing.size
        var failed = 0
        send(PackProgress(done, MUSHAF_PAGES, failed))
        val queue = Channel<Int>(Channel.UNLIMITED).also { q -> missing.forEach { q.trySend(it) }; q.close() }
        val counts = Mutex()
        coroutineScope {
            repeat(concurrency) {
                launch {
                    for (page in queue) {
                        val ok = try {
                            mask(page, widthPx)
                            true
                        } catch (e: PageUnavailableException) {
                            false
                        }
                        val p = counts.withLock {
                            if (ok) done++ else failed++
                            PackProgress(done, MUSHAF_PAGES, failed)
                        }
                        send(p)
                    }
                }
            }
        }
    }

    /** The page's SVG (gzipped) for a sharp zoom: from the recent cache, or downloaded; null if unavailable. */
    suspend fun svgFor(page: Int): File? = withContext(io) {
        val gz = svgFile(page)
        if (gz.length() > 0) {
            touch(gz)
            return@withContext gz
        }
        try {
            val part = File(svgDir, "${name(page)}.svg.part")
            svgDir.mkdirs()
            fetcher.fetch(mushafPageUrl(page), part)
            keepSvg(page, part)
            gz
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    suspend fun cachedBytes(): Long = withContext(io) { dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }

    suspend fun clear() {
        withContext(io) { dir.deleteRecursively() }
    }

    private suspend fun make(page: Int, bucket: Int): MaskResult {
        val key = page to bucket
        val job = lock.withLock {
            inflight.getOrPut(key) {
                work.async { doMake(page, bucket) }.also { d ->
                    d.invokeOnCompletion { work.launch { lock.withLock { inflight.remove(key) } } }
                }
            }
        }
        return job.await()
    }

    private suspend fun doMake(page: Int, bucket: Int): MaskResult {
        val target = maskFile(bucket, page)
        val maskPart = File(target.path + ".part")
        val svgPart = File(svgDir, "${name(page)}.svg.part")
        try {
            val viewBox = viewBoxForPage(page)
            val gz = svgFile(page)
            val mask = if (gz.length() > 0 && runCatching { touch(gz) }.isSuccess) {
                try {
                    withContext(cpu) { GZIPInputStream(gz.inputStream().buffered()).use { rasterize(it, viewBox, bucket) } }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    gz.delete() // damaged cache entry: fetch it again
                    fetchAndRender(page, svgPart, viewBox, bucket)
                }
            } else {
                fetchAndRender(page, svgPart, viewBox, bucket)
            }
            val bytes = withContext(cpu) { ByteArrayOutputStream(160_000).also { codec.encode(mask, it) }.toByteArray() }
            withContext(io) {
                target.parentFile!!.mkdirs()
                maskPart.writeBytes(bytes)
                if (!maskPart.renameTo(target)) {
                    target.delete()
                    check(maskPart.renameTo(target)) { "could not save $target" }
                }
                // One copy per page: drop masks made for other widths.
                for (d in bucketDirs()) if (d != target.parentFile) File(d, target.name).delete()
            }
            return MaskResult(target, bucket, true)
        } catch (e: CancellationException) {
            maskPart.delete()
            svgPart.delete()
            throw e
        } catch (e: Exception) {
            maskPart.delete()
            svgPart.delete()
            throw PageUnavailableException(page, e)
        }
    }

    private suspend fun fetchAndRender(page: Int, part: File, viewBox: Size, bucket: Int): PageMask {
        withContext(io) { svgDir.mkdirs() }
        fetcher.fetch(mushafPageUrl(page), part)
        val mask = withContext(cpu) { part.inputStream().buffered().use { rasterize(it, viewBox, bucket) } }
        withContext(io) { keepSvg(page, part) }
        return mask
    }

    /** Gzips a downloaded SVG into the recent cache, then trims the cache to [svgLruSize]. */
    private fun keepSvg(page: Int, plain: File) {
        val gz = svgFile(page)
        val gzPart = File(gz.path + ".part")
        try {
            GZIPOutputStream(gzPart.outputStream().buffered()).use { out -> plain.inputStream().use { it.copyTo(out) } }
            gz.delete()
            gzPart.renameTo(gz)
            touch(gz)
        } finally {
            plain.delete()
            gzPart.delete()
        }
        val cached = svgDir.listFiles { f -> f.name.endsWith(".svg.gz") }.orEmpty().sortedByDescending { it.lastModified() }
        cached.drop(svgLruSize).forEach { it.delete() }
    }

    /** Marks [f] as just used; stamps are strictly increasing so the LRU order is exact. */
    @Synchronized
    private fun touch(f: File) {
        val stamp = maxOf(System.currentTimeMillis(), lastStamp + 1000)
        lastStamp = stamp
        f.setLastModified(stamp)
    }

    /** A complete mask: right header and exactly the promised payload length. */
    private fun isValidMask(f: File): Boolean = try {
        DataInputStream(f.inputStream().buffered()).use { d ->
            val magic = ByteArray(4).also { d.readFully(it) }
            if (!magic.contentEquals(byteArrayOf(81, 84, 77, 49))) return false // "QTM1"
            d.readUnsignedShort()
            d.readUnsignedShort()
            val n = d.readInt()
            n > 0 && f.length() == 12L + n
        }
    } catch (_: Exception) {
        false
    }
}
