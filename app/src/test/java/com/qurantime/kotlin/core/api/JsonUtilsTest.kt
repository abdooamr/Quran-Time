package com.qurantime.kotlin.core.api

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonUtilsTest {
    @Test
    fun `parseSurahList cases`() {
        assertEquals(listOf(1, 2, 114), parseSurahList(JsonPrimitive("1,2,,114,")))
        assertEquals(listOf(3, 4), parseSurahList(JsonPrimitive(" 3, 4 ")))
        assertTrue(parseSurahList(null).isEmpty())
        assertTrue(parseSurahList(JsonNull).isEmpty())
        assertTrue(parseSurahList(JsonPrimitive("")).isEmpty())
    }

    @Test
    fun `cleanName trims spaces and CR LF`() {
        assertEquals("الفاتحة", cleanName(JsonPrimitive("الفاتحة\r\n")))
        assertEquals("Al-Fatihah", cleanName(JsonPrimitive("Al-Fatihah ")))
        assertEquals("", cleanName(null))
        assertEquals("", cleanName(JsonNull))
    }

    @Test
    fun `parseInt accepts strings and ints`() {
        assertEquals(1, parseInt(JsonPrimitive("1")))
        assertEquals(7, parseInt(JsonPrimitive(7)))
        assertEquals(7, parseInt(JsonPrimitive(7.0)))
        assertThrows(NumberFormatException::class.java) { parseInt(JsonPrimitive("x")) }
    }

    @Test
    fun `parseDate cases`() {
        // 2025-09-14T04:55:55Z
        assertEquals(1757825755000L, parseDate(JsonPrimitive("2025-09-14T04:55:55.000000Z")))
        assertEquals(1757825755123L, parseDate(JsonPrimitive("2025-09-14T04:55:55.123Z")))
        assertEquals(1757825755000L, parseDate(JsonPrimitive("2025-09-14 04:55:55")))
        assertNull(parseDate(null))
        assertNull(parseDate(JsonPrimitive("not a date")))
    }
}
