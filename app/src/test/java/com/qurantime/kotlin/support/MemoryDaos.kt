package com.qurantime.kotlin.support

import com.qurantime.kotlin.core.db.ApiCacheDao
import com.qurantime.kotlin.core.db.ApiCacheEntity
import java.util.concurrent.ConcurrentHashMap

/** An [ApiCacheDao] in a map, for plain JVM tests. */
class MemoryApiCacheDao : ApiCacheDao {
    private val rows = ConcurrentHashMap<String, ApiCacheEntity>()
    override suspend fun get(key: String): ApiCacheEntity? = rows[key]
    override suspend fun put(row: ApiCacheEntity) {
        rows[row.key] = row
    }
}
