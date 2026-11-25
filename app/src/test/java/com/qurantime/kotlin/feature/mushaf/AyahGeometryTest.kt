package com.qurantime.kotlin.feature.mushaf

import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qurantime.kotlin.core.cache.CachedFetcher
import com.qurantime.kotlin.core.db.AppDatabase
import com.qurantime.kotlin.feature.catalog.parseSuwar
import com.qurantime.kotlin.feature.timing.TimingRepository
import com.qurantime.kotlin.feature.timing.parseAyahTimings
import com.qurantime.kotlin.support.FixtureApi
import com.qurantime.kotlin.support.loadFixtureJson
import com.qurantime.kotlin.support.memoryDb
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Ports of the Flutter ayah_geometry_test.dart. */
@RunWith(AndroidJUnit4::class)
class AyahGeometryTest {
    private lateinit var db: AppDatabase
    private val api = FixtureApi()
    private lateinit var repo: AyahGeometryRepository
    private val suwar = parseSuwar(loadFixtureJson("suwar_eng.json"))

    @Before fun setUp() {
        db = memoryDb()
        repo = AyahGeometryRepository(TimingRepository(api, CachedFetcher(db.apiCacheDao())), db.ayahGeometryDao(), suwar = { suwar })
    }

    @After fun tearDown() = db.close()

    @Test
    fun `forPage(3) builds the index from read 5 once`() = runTest {
        val shapes = repo.forPage(3)
        assertTrue(shapes.filter { it.surah == 2 }.map { it.ayah }.containsAll(listOf(6, 7)))
        assertTrue(shapes.all { it.page == 3 })
        assertEquals(mapOf("surah" to "2", "read" to "5"), api.callsTo("ayat_timing").single().query)
        repo.forPage(3)
        repo.forPage(2)
        assertEquals(1, api.callsTo("ayat_timing").size)
    }

    @Test
    fun `the intro (ayah 0) is never stored`() = runTest {
        repo.ensureSurah(2)
        val page2 = repo.forPage(2)
        assertFalse(page2.any { it.ayah == 0 })
        assertEquals(1, page2.first().ayah)
        assertEquals(Offset(206.48f, 46.08f), page2.first().polygon.first())
        assertEquals(Offset(178.34f, 63.11f), page2.first().marker)
    }

    private val shapes = parseAyahTimings(loadFixtureJson("ayat_timing_2_5.json"))
        .filter { it.page == 3 && it.polygon != null }
        .map { AyahShape(surah = 2, ayah = it.ayah, page = 3, polygon = it.polygon!!, marker = it.marker) }
    private val poly7 = shapes.first { it.ayah == 7 }.polygon

    @Test
    fun `pointInPolygon on a square`() {
        val sq = listOf(Offset(0f, 0f), Offset(10f, 0f), Offset(10f, 10f), Offset(0f, 10f))
        assertTrue(pointInPolygon(Offset(5f, 5f), sq))
        assertFalse(pointInPolygon(Offset(11f, 5f), sq))
    }

    @Test
    fun `pointInPolygon on the concave outline of ayah 7`() {
        assertTrue(pointInPolygon(Offset(300f, 100f), poly7))
        assertTrue(pointInPolygon(Offset(150f, 60f), poly7))
        assertFalse(pointInPolygon(Offset(300f, 60f), poly7))
        assertFalse(pointInPolygon(Offset(40f, 100f), poly7))
    }

    @Test
    fun `ayahAt picks the right ayah`() {
        assertEquals(7, ayahAt(Offset(300f, 100f), shapes)!!.ayah)
        assertEquals(6, ayahAt(Offset(300f, 60f), shapes)!!.ayah)
        assertNull(ayahAt(Offset(-5f, -5f), shapes))
    }
}
