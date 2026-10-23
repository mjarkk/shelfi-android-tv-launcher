package com.mkopenga.tvhomescreen.ui.settings

import com.mkopenga.tvhomescreen.ui.wallpaper.Wallpaper
import com.mkopenga.tvhomescreen.ui.wallpaper.Wallpapers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WallpaperSamplesTest {
    @Test
    fun noWallpaperInTheCatalogueIsAmongItsOwnSamples() {
        for (current in Wallpapers) {
            val samples = wallpaperSamples(Wallpapers, current)
            assertEquals(WallpaperSampleCount, samples.size, current.id)
            assertFalse(samples.any { it.id == current.id }, "${current.id}: ${samples.map { it.id }}")
        }
    }

    @Test
    fun theSamplesAreTheNextWallpapersInCatalogueOrderWrappingRound() {
        for ((i, current) in Wallpapers.withIndex()) {
            val expected = (1..WallpaperSampleCount).map { Wallpapers[(i + it) % Wallpapers.size].id }
            assertEquals(expected, wallpaperSamples(Wallpapers, current).map { it.id }, current.id)
        }
    }

    @Test
    fun withoutAKnownCurrentWallpaperTheSamplesStartAtTheFirst() {
        val first = Wallpapers.take(WallpaperSampleCount)
        assertEquals(first, wallpaperSamples(Wallpapers, null))
        assertEquals(first, wallpaperSamples(Wallpapers, Wallpaper("gone", "Gone")))
    }

    @Test
    fun aShortCatalogueGivesAsManySamplesAsItHasOthers() {
        val one = Wallpaper("plain", "Plain")
        val two = Wallpaper("moss", "Moss")
        assertTrue(wallpaperSamples(listOf(one), one).isEmpty())
        assertEquals(listOf(two), wallpaperSamples(listOf(one, two), one))
        assertTrue(wallpaperSamples(emptyList(), null).isEmpty())
    }
}
