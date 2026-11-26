package com.qurantime.core.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Raw API JSON with when it was fetched; read by [com.qurantime.core.cache.CachedFetcher]. */
@Entity(tableName = "api_cache")
data class ApiCacheEntity(
    @PrimaryKey val key: String,
    val json: String,
    @ColumnInfo(name = "fetched_at_ms") val fetchedAtMs: Long,
)

/** A page bookmark (surah and ayah null) or an ayah bookmark. */
@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val page: Int,
    val surah: Int? = null,
    val ayah: Int? = null,
    @ColumnInfo(name = "created_at_ms") val createdAtMs: Long,
)

@Entity(tableName = "favorites", primaryKeys = ["kind", "ref_id"])
data class FavoriteEntity(
    val kind: String,
    @ColumnInfo(name = "ref_id") val refId: Int,
    @ColumnInfo(name = "created_at_ms") val createdAtMs: Long,
)

enum class DownloadStatus { QUEUED, RUNNING, PAUSED, DONE, FAILED, MISSING }

@Entity(tableName = "downloads", indices = [Index(value = ["url"], unique = true), Index("task_id"), Index("moshaf_id")])
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    @ColumnInfo(name = "moshaf_id") val moshafId: Int? = null,
    val surah: Int? = null,
    val url: String,
    val path: String,
    val status: DownloadStatus,
    @ColumnInfo(name = "total_bytes") val totalBytes: Long? = null,
    @ColumnInfo(name = "received_bytes", defaultValue = "0") val receivedBytes: Long = 0,
    @ColumnInfo(name = "task_id") val taskId: String? = null,
)

/** One ayah's outline on its Mushaf page, in the page's viewBox units. */
@Entity(tableName = "ayah_geometry", primaryKeys = ["surah", "ayah"], indices = [Index("page")])
data class AyahGeometryEntity(
    val surah: Int,
    val ayah: Int,
    val page: Int,
    /** `x,y x,y ...` */
    val polygon: String,
    @ColumnInfo(name = "marker_x") val markerX: Double? = null,
    @ColumnInfo(name = "marker_y") val markerY: Double? = null,
)
