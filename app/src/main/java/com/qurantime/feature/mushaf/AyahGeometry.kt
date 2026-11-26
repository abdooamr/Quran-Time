package com.qurantime.feature.mushaf

import androidx.compose.ui.geometry.Offset
import com.qurantime.core.db.AyahGeometryDao
import com.qurantime.core.db.AyahGeometryEntity
import com.qurantime.feature.catalog.Surah
import com.qurantime.feature.timing.AyahTiming
import com.qurantime.feature.timing.TimingRepository
import com.qurantime.feature.timing.parsePolygon
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One ayah's outline on its page, in the page's viewBox units. */
data class AyahShape(val surah: Int, val ayah: Int, val page: Int, val polygon: List<Offset>, val marker: Offset? = null)

/** Even–odd ray casting; works for the concave outlines ayahs have. */
fun pointInPolygon(p: Offset, poly: List<Offset>): Boolean {
    var inside = false
    var j = poly.lastIndex
    for (i in poly.indices) {
        val a = poly[i]
        val b = poly[j]
        if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) inside = !inside
        j = i
    }
    return inside
}

/** The ayah under [viewBoxPoint], or null. */
fun ayahAt(viewBoxPoint: Offset, shapes: List<AyahShape>): AyahShape? = shapes.firstOrNull { pointInPolygon(viewBoxPoint, it.polygon) }

/**
 * Where every ayah sits on the Mushaf pages, built from one timed recording
 * ([referenceReadId], Al-Ajmi) a surah at a time and kept in the database, so
 * taps and bookmarks know the ayah under a finger whatever reciter plays.
 */
class AyahGeometryRepository(
    private val timing: TimingRepository,
    private val dao: AyahGeometryDao,
    private val suwar: suspend () -> List<Surah>,
    private val referenceReadId: Int = 5,
) {
    private val ready = HashSet<Int>()
    private val lock = Mutex()
    private val inflight = HashMap<Int, CompletableDeferred<Unit>>()

    suspend fun forPage(page: Int): List<AyahShape> {
        for (s in surahsOnPage(page, suwar())) ensureSurah(s.id)
        return dao.forPage(page).map(::toShape)
    }

    suspend fun find(surah: Int, ayah: Int): AyahShape? {
        ensureSurah(surah)
        return dao.find(surah, ayah)?.let(::toShape)
    }

    suspend fun ensureSurah(surah: Int) {
        val (mine, waiter) = lock.withLock {
            if (surah in ready) return
            val existing = inflight[surah]
            if (existing != null) false to existing else true to CompletableDeferred<Unit>().also { inflight[surah] = it }
        }
        if (!mine) return waiter.await()
        try {
            load(surah)
            lock.withLock { ready += surah }
            waiter.complete(Unit)
        } catch (e: Throwable) {
            waiter.completeExceptionally(e)
            throw e
        } finally {
            lock.withLock { inflight.remove(surah) }
        }
    }

    private suspend fun load(surah: Int) {
        if (dao.hasSurah(surah)) return
        val rows = timing.timing(referenceReadId, surah)
            .filter { it.ayah > 0 && it.page != null && it.polygon != null }
            .map { toRow(surah, it) }
        dao.insertAll(rows)
    }

    /** Fills the whole index (114 requests, one at a time). */
    suspend fun ensureAll(onProgress: (done: Int) -> Unit = {}) {
        for (s in 1..114) {
            ensureSurah(s)
            onProgress(s)
        }
    }

    private fun toRow(surah: Int, t: AyahTiming) = AyahGeometryEntity(
        surah = surah,
        ayah = t.ayah,
        page = t.page!!,
        polygon = t.polygon!!.joinToString(" ") { "${it.x},${it.y}" },
        markerX = t.marker?.x?.toDouble(),
        markerY = t.marker?.y?.toDouble(),
    )

    private fun toShape(r: AyahGeometryEntity) = AyahShape(
        surah = r.surah,
        ayah = r.ayah,
        page = r.page,
        polygon = parsePolygon(r.polygon),
        marker = if (r.markerX != null && r.markerY != null) Offset(r.markerX.toFloat(), r.markerY.toFloat()) else null,
    )
}
