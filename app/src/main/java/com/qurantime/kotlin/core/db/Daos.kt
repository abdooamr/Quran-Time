package com.qurantime.kotlin.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ApiCacheDao {
    @Query("SELECT * FROM api_cache WHERE `key` = :key")
    suspend fun get(key: String): ApiCacheEntity?

    @Upsert
    suspend fun put(row: ApiCacheEntity)
}

@Dao
interface BookmarksDao {
    @Query("SELECT * FROM bookmarks ORDER BY page ASC, ayah ASC")
    suspend fun all(): List<BookmarkEntity>

    @Insert
    suspend fun add(row: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun removeById(id: Long)

    /** Removes the page-level bookmark of [page] (ayah bookmarks stay). */
    @Query("DELETE FROM bookmarks WHERE page = :page AND ayah IS NULL")
    suspend fun removePage(page: Int)
}

@Dao
interface FavoritesDao {
    @Query("SELECT ref_id FROM favorites WHERE kind = :kind")
    fun watch(kind: String): Flow<List<Int>>

    @Query("DELETE FROM favorites WHERE kind = :kind AND ref_id = :id")
    suspend fun delete(kind: String, id: Int): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(row: FavoriteEntity)

    @Transaction
    suspend fun toggle(kind: String, id: Int, nowMs: Long) {
        if (delete(kind, id) == 0) insert(FavoriteEntity(kind, id, nowMs))
    }
}

@Dao
interface DownloadsDao {
    @Query("SELECT * FROM downloads ORDER BY id ASC")
    suspend fun all(): List<DownloadEntity>

    @Query("SELECT * FROM downloads ORDER BY id ASC")
    fun watchAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE url = :url")
    suspend fun byUrl(url: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE task_id = :taskId")
    suspend fun byTaskId(taskId: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun byId(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE moshaf_id = :moshafId")
    suspend fun byMoshaf(moshafId: Int): List<DownloadEntity>

    @Insert
    suspend fun insert(row: DownloadEntity): Long

    @androidx.room.Update
    suspend fun update(row: DownloadEntity)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface AyahGeometryDao {
    @Query("SELECT EXISTS(SELECT 1 FROM ayah_geometry WHERE surah = :surah LIMIT 1)")
    suspend fun hasSurah(surah: Int): Boolean

    @Upsert
    suspend fun insertAll(rows: List<AyahGeometryEntity>)

    @Query("SELECT * FROM ayah_geometry WHERE page = :page ORDER BY surah ASC, ayah ASC")
    suspend fun forPage(page: Int): List<AyahGeometryEntity>

    @Query("SELECT * FROM ayah_geometry WHERE surah = :surah AND ayah = :ayah")
    suspend fun find(surah: Int, ayah: Int): AyahGeometryEntity?
}
