package com.mkopenga.tvhomescreen.ui

import com.mkopenga.tvhomescreen.ShelfItem

/** Most recently used first; never-opened apps follow, A–Z. */
internal fun List<ShelfItem>.recentFirst(): List<ShelfItem> = sortedWith(RecentFirst)

private val RecentFirst: Comparator<ShelfItem> =
    compareBy<ShelfItem> { it.lastUsedAt == null }
        .thenByDescending { it.lastUsedAt }
        .thenBy { it.title.lowercase() }
        .thenBy { it.id }
