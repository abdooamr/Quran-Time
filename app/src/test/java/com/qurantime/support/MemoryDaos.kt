package com.qurantime.support

import com.qurantime.core.db.ApiCacheDao
import com.qurantime.core.db.ApiCacheEntity
import java.util.concurrent.ConcurrentHashMap

/** An [ApiCacheDao] in a map, for plain JVM tests. */
class MemoryApiCacheDao : ApiCacheDao {
    private val rows = ConcurrentHashMap<String, ApiCacheEntity>()
    override suspend fun get(key: String): ApiCacheEntity? = rows[key]
    override suspend fun put(row: ApiCacheEntity) {
        rows[row.key] = row
    }
}
