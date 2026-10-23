package com.mkopenga.tvhomescreen.ui.wallpaper

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.mkopenga.tvhomescreen.MinTextContrast
import com.mkopenga.tvhomescreen.contrastRatio
import com.mkopenga.tvhomescreen.ui.Palette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WallpaperCatalogueTest {
    @Test
    fun eightToTenWallpapersWithDistinctIdsAndNames() {
        assertTrue(Patterns.size in 8..10, "${Patterns.size} wallpapers")
        assertEquals(Patterns.size, Wallpapers.map { it.id }.toSet().size)
        assertEquals(Patterns.size, Wallpapers.map { it.name }.toSet().size)
        assertTrue(Wallpapers.all { it.name.isNotBlank() })
    }

    @Test
    fun theInfoLineIsReadableOnEveryWainscot() {
        // The room's warm glow is clear by the time it reaches the info line; assume it reaches it anyway.
        val glow = Color(0xFFFFE4A6).copy(alpha = 0.13f)
        for (pattern in Patterns + PlainPattern) {
            val lightest = glow.compositeOver(pattern.wainscot)
            for (ink in listOf(Palette.Cream, Palette.Muted)) {
                val ratio = contrastRatio(ink, lightest)
                assertTrue(ratio >= MinTextContrast, "${pattern.wallpaper.id}: $ink on ${pattern.wainscot} is $ratio:1")
            }
        }
    }

    @Test
    fun theEmptyShelfPlaqueIsReadable() {
        assertTrue(contrastRatio(Palette.Cream, Palette.Plaque) >= MinTextContrast)
        assertTrue(contrastRatio(Palette.Muted, Palette.Plaque) >= MinTextContrast)
    }

    @Test
    fun unknownWallpapersFallBackToPlain() {
        assertEquals(PlainPattern, patternOf(Wallpaper("gone", "Gone")))
        for (pattern in Patterns) assertEquals(pattern, patternOf(pattern.wallpaper.copy(name = "renamed")))
    }
}
