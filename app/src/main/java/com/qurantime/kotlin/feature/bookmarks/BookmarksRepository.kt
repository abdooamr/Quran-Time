package com.qurantime.kotlin.feature.bookmarks

import com.qurantime.kotlin.core.db.BookmarkEntity
import com.qurantime.kotlin.core.db.BookmarksDao
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Bookmark(val id: Long, val page: Int, val surah: Int?, val ayah: Int?, val createdAtMs: Long) {
    val isAyah: Boolean get() = ayah != null
}

/**
 * Page and ayah bookmarks. [items] is kept in memory, sorted by page, so the
 * Mushaf header shows a page's bookmark state without waiting on the database.
 */
class BookmarksRepository(private val dao: BookmarksDao, private val now: () -> Long = System::currentTimeMillis) {
    private val _items = MutableStateFlow<List<Bookmark>>(emptyList())
    val items: StateFlow<List<Bookmark>> = _items.asStateFlow()
    private val ready = CompletableDeferred<Unit>()

    /** Loads the saved bookmarks; call once. */
    suspend fun load() {
        reload()
        ready.complete(Unit)
    }

    fun hasPage(page: Int): Boolean = _items.value.any { it.page == page && !it.isAyah }

    suspend fun all(): List<Bookmark> {
        ready.await()
        return _items.value
    }

    suspend fun isPageBookmarked(page: Int): Boolean {
        ready.await()
        return hasPage(page)
    }

    suspend fun togglePage(page: Int) {
        if (isPageBookmarked(page)) dao.removePage(page) else dao.add(BookmarkEntity(page = page, createdAtMs = now()))
        reload()
    }

    /** Bookmarks an ayah; does nothing when that ayah is already bookmarked. */
    suspend fun addAyah(surah: Int, ayah: Int, page: Int) {
        ready.await()
        if (_items.value.any { it.surah == surah && it.ayah == ayah }) return
        dao.add(BookmarkEntity(page = page, surah = surah, ayah = ayah, createdAtMs = now()))
        reload()
    }

    /** Puts back a bookmark removed with [remove]. */
    suspend fun restore(b: Bookmark) {
        dao.add(BookmarkEntity(page = b.page, surah = b.surah, ayah = b.ayah, createdAtMs = b.createdAtMs))
        reload()
    }

    suspend fun remove(id: Long) {
        dao.removeById(id)
        reload()
    }

    private suspend fun reload() {
        _items.value = dao.all().map { Bookmark(it.id, it.page, it.surah, it.ayah, it.createdAtMs) }
    }
}
