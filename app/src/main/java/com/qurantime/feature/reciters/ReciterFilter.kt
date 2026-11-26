package com.qurantime.feature.reciters

import com.qurantime.feature.catalog.MoshafType
import java.util.Locale

data class ReciterFilter(
    val query: String = "",
    val riwayaId: Int? = null,
    val moshafTypeId: Int? = null,
    val timedOnly: Boolean = false,
    val favoritesOnly: Boolean = false,
) {
    val isEmpty: Boolean get() = query.isBlank() && riwayaId == null && moshafTypeId == null && !timedOnly && !favoritesOnly
}

// Arabic harakat (U+064B–U+0652), tatweel (U+0640) and superscript alef (U+0670).
private val MARKS = Regex("[ً-ْـٰ]")

/** Lowercased, without Arabic diacritics or tatweel, for matching. */
fun foldForSearch(s: String): String = s.replace(MARKS, "").lowercase(Locale.ROOT).trim()

/** The reciters [f] keeps, sorted by letter, then name. */
fun applyReciterFilter(
    all: List<Reciter>,
    f: ReciterFilter,
    types: Map<Int, MoshafType>,
    isTimed: (Moshaf) -> Boolean,
    favoriteIds: Set<Int>,
): List<Reciter> {
    val q = foldForSearch(f.query)
    return all.filter { r ->
        (q.isEmpty() || foldForSearch(r.name).contains(q)) &&
            (!f.favoritesOnly || r.id in favoriteIds) &&
            (f.riwayaId == null || r.moshaf.any { types[it.moshafType]?.riwayaId == f.riwayaId }) &&
            (f.moshafTypeId == null || r.moshaf.any { it.moshafType == f.moshafTypeId }) &&
            (!f.timedOnly || r.moshaf.any(isTimed))
    }.sortedWith(compareBy<Reciter>({ it.letter }, { it.name }))
}
