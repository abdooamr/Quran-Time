package com.qurantime.kotlin.support

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.qurantime.kotlin.core.db.AppDatabase

/** A fresh in-memory database (Robolectric tests only). */
fun memoryDb(): AppDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()

/** 2026-01-01T00:00:00Z */
const val JAN_1_2026 = 1_767_225_600_000L
const val DAY_MS = 86_400_000L
const val HOUR_MS = 3_600_000L
