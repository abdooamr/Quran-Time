package com.qurantime.feature

import androidx.compose.ui.geometry.Offset
import com.qurantime.feature.audio.AudioClip
import com.qurantime.feature.audio.PlayableItem
import com.qurantime.feature.audio.RadioStream
import com.qurantime.feature.audio.SurahTrack
import com.qurantime.feature.catalog.parseMoshafTypes
import com.qurantime.feature.catalog.parseRiwayat
import com.qurantime.feature.catalog.parseSuwar
import com.qurantime.feature.explore.parseTadabor
import com.qurantime.feature.explore.parseTafasir
import com.qurantime.feature.explore.parseTafsirClips
import com.qurantime.feature.explore.parseVideoTypes
import com.qurantime.feature.explore.parseVideos
import com.qurantime.feature.live.parseLiveTv
import com.qurantime.feature.live.parseRadios
import com.qurantime.feature.reciters.parseRecentReads
import com.qurantime.feature.reciters.parseReciters
import com.qurantime.feature.timing.pageFromSvgUrl
import com.qurantime.feature.timing.parseAyahTimings
import com.qurantime.feature.timing.parsePolygon
import com.qurantime.feature.timing.parseTimedReads
import com.qurantime.feature.timing.parseTimedSurahs
import com.qurantime.support.loadFixtureJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun json(s: String) = Json.parseToJsonElement(s)

/** Ports of the Flutter catalog/models_test.dart. */
class CatalogModelsTest {
    @Test
    fun `parseSuwar fixture`() {
        val suwar = parseSuwar(loadFixtureJson("suwar_eng.json"))
        assertEquals(114, suwar.size)
        assertEquals(1, suwar[0].id)
        assertEquals("Al-Fatihah", suwar[0].name)
        assertEquals(1, suwar[0].startPage)
        assertTrue(suwar[0].isMakki)
        assertEquals(49, suwar[1].endPage)
        assertFalse(suwar[1].isMakki)
        assertEquals(293, suwar[17].startPage)
        assertEquals(114, suwar.last().id)
    }

    @Test
    fun `parseSuwar arabic names are trimmed`() {
        val suwar = parseSuwar(loadFixtureJson("suwar_ar.json"))
        assertTrue(suwar.all { it.name == it.name.trim() && it.name.isNotEmpty() })
    }

    @Test
    fun `parseRiwayat fixture`() {
        val hafs = parseRiwayat(loadFixtureJson("riwayat_eng.json")).first { it.id == 1 }
        assertTrue(hafs.name.startsWith("Rewayat Hafs"))
    }

    @Test
    fun `parseMoshafTypes fixture`() {
        val t11 = parseMoshafTypes(loadFixtureJson("moshaf_eng.json")).first { it.id == 11 }
        assertEquals(1, t11.riwayaId)
        assertEquals(1, t11.styleId)
        assertTrue(t11.name.contains("Murattal"))
    }

    @Test
    fun `every reciter moshaf_type is a known MoshafType id`() {
        val ids = parseMoshafTypes(loadFixtureJson("moshaf_eng.json")).map { it.id }.toSet()
        val reciters = loadFixtureJson("reciters_eng.json").jsonObject["reciters"]!!.jsonArray
        val used = reciters.flatMap { r -> r.jsonObject["moshaf"]!!.jsonArray.map { it.jsonObject["moshaf_type"]!!.jsonPrimitive.int } }.toSet()
        assertTrue(ids.containsAll(used))
    }
}

/** Ports of the Flutter reciters/models_test.dart. */
class ReciterModelsTest {
    @Test
    fun `reciter 231`() {
        val reciters = parseReciters(loadFixtureJson("reciters_231_eng.json"))
        assertEquals(1, reciters.size)
        val r = reciters.single()
        assertEquals("Hazza Al-Balushi", r.name)
        assertEquals("H", r.letter)
        assertNotNull(r.date)
        val m = r.moshaf.first()
        assertTrue(m.server.startsWith("https://"))
        assertTrue(m.server.endsWith("/"))
        assertTrue(m.has(18))
        assertTrue(m.audioUrl(18)!!.endsWith("/018.mp3"))
    }

    @Test
    fun `audioUrl is null for a surah the recording lacks`() {
        val m = parseReciters(loadFixtureJson("reciters_231_eng.json")).single().moshaf.first()
        val missing = (1..114).first { it !in m.surahs }
        assertFalse(m.has(missing))
        assertNull(m.audioUrl(missing))
    }

    @Test
    fun `surahs length matches surah_total for every moshaf`() {
        val reciters = parseReciters(loadFixtureJson("reciters_eng.json"))
        assertTrue(reciters.isNotEmpty())
        for (r in reciters) for (m in r.moshaf) assertEquals("${r.name} / ${m.name}", m.surahTotal, m.surahs.size)
    }

    @Test
    fun `parseRecentReads fixture`() {
        val reads = parseRecentReads(loadFixtureJson("recent_reads_eng.json"))
        assertTrue(reads.isNotEmpty())
        assertTrue(reads.all { it.recentDate != null })
    }

    @Test
    fun `json round trip keeps the reciter`() {
        val r = parseReciters(loadFixtureJson("reciters_231_eng.json")).single()
        val again = parseReciters(json("""{"reciters":[${r.toJson()}]}""")).single()
        assertEquals(r.date, again.date)
        assertEquals(r.moshaf.first().surahs, again.moshaf.first().surahs)
        assertEquals(r.moshaf.first().server, again.moshaf.first().server)
    }
}

/** Ports of the Flutter timing/models_test.dart. */
class TimingModelsTest {
    @Test
    fun `timed reads`() {
        val reads = parseTimedReads(loadFixtureJson("ayat_timing_reads.json"))
        assertTrue(reads.size >= 100)
        val r5 = reads.first { it.id == 5 }
        assertTrue(r5.folderUrl.startsWith("https://"))
        assertEquals(r5.riwaya.trim(), r5.riwaya)
        assertEquals(114, r5.surahCount)
    }

    @Test
    fun `timing for surah 2, read 5`() {
        val t = parseAyahTimings(loadFixtureJson("ayat_timing_2_5.json"))
        assertEquals(287, t.size)
        assertEquals(0, t[0].ayah)
        assertNull(t[0].page)
        assertNull(t[0].polygon)
        assertNull(t[0].marker)
        assertEquals(0L, t[0].startMs)
        assertEquals(5587L, t[0].endMs)
        assertEquals(1, t[1].ayah)
        assertEquals(2, t[1].page)
        assertEquals(4, t[1].polygon!!.size)
        assertEquals(Offset(206.48f, 46.08f), t[1].polygon!![0])
        assertEquals(Offset(178.34f, 63.11f), t[1].marker)
        assertEquals(5587L, t[1].startMs)
        assertEquals(12658L, t[1].endMs)
        assertEquals(3, t.first { it.ayah == 6 }.page)
    }

    @Test
    fun `parsePolygon cases`() {
        assertEquals(listOf(Offset(1f, 2f), Offset(3.5f, 4f), Offset(5f, 6f)), parsePolygon("1,2 3.5,4  5,6"))
    }

    @Test
    fun `pageFromSvgUrl cases`() {
        assertEquals(2, pageFromSvgUrl("https://www.mp3quran.net/api/quran_pages_svg/002.svg"))
        assertEquals(604, pageFromSvgUrl("https://x/604.svg"))
        assertNull(pageFromSvgUrl(null))
        assertNull(pageFromSvgUrl("nope"))
    }

    @Test
    fun `timed surahs`() {
        val ids = parseTimedSurahs(loadFixtureJson("ayat_timing_soar_5.json"))
        assertTrue(1 in ids)
        assertTrue(114 in ids)
    }
}

/** Ports of the Flutter live/models_test.dart. */
class LiveModelsTest {
    @Test
    fun `live tv`() {
        val channels = parseLiveTv(loadFixtureJson("live_tv_eng.json"))
        assertEquals(2, channels.size)
        assertTrue(channels.all { it.url.endsWith(".m3u8") })
        assertEquals("Quran channel", channels.first().name)
    }

    @Test
    fun `radios`() {
        val radios = parseRadios(loadFixtureJson("radios_eng.json"))
        assertTrue(radios.isNotEmpty())
        assertTrue(radios.all { it.url.startsWith("https://") })
        assertTrue(radios.all { it.name.isNotEmpty() })
    }

    @Test
    fun `radio urls are upgraded to https`() {
        val radios = parseRadios(json("""{"radios":[{"id":1,"name":"X ","url":"http://qurango.net/radio/x"}]}"""))
        assertEquals("https://qurango.net/radio/x", radios.single().url)
        assertEquals("X", radios.single().name)
    }
}

/** Ports of the Flutter explore/models_test.dart. */
class ExploreModelsTest {
    @Test
    fun `videos`() {
        val typeIds = parseVideoTypes(loadFixtureJson("video_types_ar.json")).map { it.id }.toSet()
        val groups = parseVideos(loadFixtureJson("videos_ar.json"))
        val videos = groups.flatMap { it.videos }
        assertTrue(groups.isNotEmpty())
        assertTrue(groups.all { it.reciterName.isNotEmpty() })
        assertTrue(videos.all { it.thumbUrl.startsWith("https://") })
        assertTrue(videos.all { it.url.startsWith("https://") })
        assertTrue(videos.all { it.typeId in typeIds })
    }

    @Test
    fun `video thumbs are upgraded to https`() {
        val groups = parseVideos(
            json("""{"videos":[{"id":1,"reciter_name":"A","videos":[{"id":2,"video_type":2,"video_url":"http://a/b.mp4","video_thumb_url":"http://mp3quran.de/x.jpeg"}]}]}"""),
        )
        assertEquals("https://mp3quran.de/x.jpeg", groups.single().videos.single().thumbUrl)
        assertEquals("https://a/b.mp4", groups.single().videos.single().url)
    }

    @Test
    fun `tafasir`() {
        assertTrue(parseTafasir(loadFixtureJson("tafasir_ar.json")).any { it.id == 1 })
    }

    @Test
    fun `tafsir clips for surah 114`() {
        val clips = parseTafsirClips(loadFixtureJson("tafsir_1_114_ar.json"))
        assertEquals(1, clips.size)
        assertEquals(114, clips.single().surah)
        assertEquals(1, clips.single().tafsirId)
        assertTrue(clips.single().url.endsWith("/114.mp3"))
    }

    @Test
    fun `tafsir clips are flattened and sorted by surah then id`() {
        val clips = parseTafsirClips(
            json(
                """{"tafasir":{"name":"T","soar":{
                "2":[{"id":9,"tafsir_id":1,"name":"b","url":"https://x/2b.mp3","sura_id":2},
                     {"id":3,"tafsir_id":1,"name":"a","url":"https://x/2a.mp3","sura_id":2}],
                "1":[{"id":5,"tafsir_id":1,"name":"c","url":"https://x/1.mp3","sura_id":1}]}}}""",
            ),
        )
        assertEquals(listOf(5, 3, 9), clips.map { it.id })
    }

    @Test
    fun `tadabor surah 1`() {
        val clips = parseTadabor(loadFixtureJson("tadabor_1_ar.json"))
        assertTrue(clips.isNotEmpty())
        for (c in clips) {
            assertEquals(1, c.surah)
            assertFalse(c.surahName.contains('\r'))
            assertFalse(c.surahName.contains('\n'))
            assertTrue(c.imageUrl.startsWith("https://"))
            assertTrue((c.audioUrl ?: c.videoUrl)!!.startsWith("https://"))
        }
    }

    @Test
    fun `tadabor audio clip with CRLF name`() {
        val clips = parseTadabor(
            json(
                """{"tadabor":{"3":[{"id":38,"audio_url":"http://server18.mp3quran.net/tadabor/audio/0038.mp3",
                "image_url":"https://server18.mp3quran.net/tadabor/image/0038.jpg","text":"t",
                "sora_name":"آل عمران\r\n","rewaya_name":"حفص عن عاصم","reciter_name":"محمد البراك"}]}}""",
            ),
        )
        val c = clips.single()
        assertEquals(3, c.surah)
        assertEquals("آل عمران", c.surahName)
        assertEquals("https://server18.mp3quran.net/tadabor/audio/0038.mp3", c.audioUrl)
        assertNull(c.videoUrl)
        assertEquals("محمد البراك", c.reciterName)
    }

    @Test
    fun `tadabor with no entries`() {
        assertTrue(parseTadabor(json("""{"tadabor":[]}""")).isEmpty())
        assertTrue(parseTadabor(json("""{"tadabor":{}}""")).isEmpty())
    }
}

/** Ports of the JSON cases of the Flutter audio/playable_item_test.dart (MediaItem cases: Task 20). */
class PlayableItemTest {
    private val track = SurahTrack(
        reciterId = 5, reciterName = "Ahmad Al-Ajmy", moshafId = 5,
        moshafName = "Rewayat Hafs A'n Assem - Murattal",
        server = "https://cdn.mp3quran.net/audio/ahmad-ajmi/r1/", surah = 2, surahName = "Al-Baqarah",
    )

    @Test
    fun `surah track url`() {
        assertEquals("https://cdn.mp3quran.net/audio/ahmad-ajmi/r1/002.mp3", track.url)
    }

    @Test
    fun `json round trip (used to restore playback)`() {
        val items = listOf<PlayableItem>(
            track,
            RadioStream(id = 1, name = "R", url = "https://r"),
            AudioClip(id = "c", title = "t", subtitle = "s", url = "https://c"),
            AudioClip(id = "d", title = "t", subtitle = "s", url = "https://d", artUrl = "https://x/y.jpg"),
        )
        for (item in items) assertEquals(item, PlayableItem.fromJson(item.toJson()))
    }
}
