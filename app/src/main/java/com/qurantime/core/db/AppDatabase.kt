package com.qurantime.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

/** Enums are stored by name, so reordering the enum never corrupts rows. */
class Converters {
    @TypeConverter fun fromStatus(s: DownloadStatus): String = s.name
    @TypeConverter fun toStatus(s: String): DownloadStatus = DownloadStatus.valueOf(s)
}

/**
 * The app's SQLite database (Android's own SQLite; nothing bundled).
 *
 * Version 1. Any later version adds a [androidx.room.migration.Migration];
 * there is no destructive fallback, so user data is never dropped silently.
 */
@Database(
    entities = [ApiCacheEntity::class, BookmarkEntity::class, FavoriteEntity::class, DownloadEntity::class, AyahGeometryEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun apiCacheDao(): ApiCacheDao
    abstract fun bookmarksDao(): BookmarksDao
    abstract fun favoritesDao(): FavoritesDao
    abstract fun downloadsDao(): DownloadsDao
    abstract fun ayahGeometryDao(): AyahGeometryDao

    companion object {
        /** Builds the database; the file is opened on first query, off the main thread. */
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "quran_time.db").build()
    }
}
