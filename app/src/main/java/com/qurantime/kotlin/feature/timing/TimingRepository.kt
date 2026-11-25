package com.qurantime.kotlin.feature.timing

import com.qurantime.kotlin.core.api.Api
import com.qurantime.kotlin.core.api.getJson
import com.qurantime.kotlin.core.api.normalizeServer
import com.qurantime.kotlin.core.cache.CachedFetcher
import com.qurantime.kotlin.core.cache.Ttl
import com.qurantime.kotlin.feature.reciters.Moshaf
import com.qurantime.kotlin.feature.reciters.Reciter

/** Ayah timing never changes once published, so it is cached forever. */
class TimingRepository(private val api: Api, private val fetcher: CachedFetcher) {
    suspend fun reads(): List<TimedRead> = fetcher.fetch(
        key = "timing:reads",
        ttl = Ttl.FOREVER,
        remote = { api.getJson("ayat_timing/reads") },
        parse = ::parseTimedReads,
    )

    suspend fun timedSurahs(readId: Int): List<Int> = fetcher.fetch(
        key = "timing:soar:$readId",
        ttl = Ttl.FOREVER,
        remote = { api.getJson("ayat_timing/soar", mapOf("read" to readId)) },
        parse = ::parseTimedSurahs,
    )

    suspend fun timing(readId: Int, surah: Int): List<AyahTiming> = fetcher.fetch(
        key = "timing:$readId:$surah",
        ttl = Ttl.FOREVER,
        remote = { api.getJson("ayat_timing", mapOf("surah" to surah, "read" to readId)) },
        parse = ::parseAyahTimings,
    )
}

/** Index of the timing whose ayah is being recited at [positionMs], or null before the first. */
fun timingIndexAt(timings: List<AyahTiming>, positionMs: Long): Int? {
    if (timings.isEmpty() || positionMs < timings.first().startMs) return null
    var lo = 0
    var hi = timings.lastIndex
    while (lo < hi) {
        val mid = (lo + hi + 1) ushr 1
        if (timings[mid].startMs <= positionMs) lo = mid else hi = mid - 1
    }
    return lo
}

data class ReadRecording(val reciter: Reciter, val moshaf: Moshaf)

/** Links timed reads to the reciter recordings they were timed against (by audio folder). */
class TimedReadMatcher(reads: List<TimedRead>, reciters: List<Reciter>) {
    private val readsByServer = HashMap<String, TimedRead>()
    private val moshafByRead = HashMap<Int, ReadRecording>()

    init {
        for (r in reads) readsByServer[normalizeServer(r.folderUrl)] = r
        for (reciter in reciters) for (m in reciter.moshaf) {
            readsByServer[normalizeServer(m.server)]?.let { moshafByRead[it.id] = ReadRecording(reciter, m) }
        }
    }

    fun readForServer(server: String): TimedRead? = readsByServer[normalizeServer(server)]
    fun moshafForRead(readId: Int): ReadRecording? = moshafByRead[readId]
    fun isTimed(m: Moshaf): Boolean = readForServer(m.server) != null
}
