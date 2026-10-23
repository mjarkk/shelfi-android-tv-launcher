package com.mkopenga.tvhomescreen.ui

import com.mkopenga.tvhomescreen.fake.FakeShelves
import kotlin.test.Test
import kotlin.test.assertEquals

class SortingTest {
    @Test
    fun mostRecentFirstThenNeverOpenedByTitle() {
        assertEquals(
            listOf(
                "iron-saints", "fern-and-the-deep-wood", "tin-orchestra", "moth-lantern", "kite-season", "cartographer",
                "quiet-harbour", "sundial", "bloom", "brick-by-brick",
                "grand-prix-deluxe", "lowlight", "pocket-kingdoms", "velvet-static",
            ),
            FakeShelves.games.reversed().recentFirst().map { it.id },
        )
    }

    @Test
    fun neverOpenedTitlesIgnoreCase() {
        val items = listOf("beta", "Alpha", "gamma").map { FakeShelves.games[0].copy(id = it, title = it, lastUsedAt = null) }
        assertEquals(listOf("Alpha", "beta", "gamma"), items.recentFirst().map { it.title })
    }
}
