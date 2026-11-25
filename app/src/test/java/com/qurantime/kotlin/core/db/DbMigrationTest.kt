package com.qurantime.kotlin.core.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DbMigrationTest {
    @Test
    fun `schema v1 opens and enum is stored by name`() = runTest {
        // Schema v1 is exported, so later versions can be checked against it.
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val schema = context.assets.open("com.qurantime.kotlin.core.db.AppDatabase/1.json").bufferedReader().readText()
        assertTrue(schema.contains("\"tableName\": \"downloads\""))
        assertTrue(Regex(""""columnName":\s*"status",\s*"affinity":\s*"TEXT"""").containsMatchIn(schema))

        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        db.downloadsDao().insert(
            DownloadEntity(kind = "surah", moshafId = 1, surah = 2, url = "https://x/002.mp3", path = "/p", status = DownloadStatus.DONE),
        )
        db.openHelper.readableDatabase.query("SELECT status FROM downloads").use { c ->
            c.moveToFirst()
            assertEquals("DONE", c.getString(0))
        }
        db.close()
    }
}
