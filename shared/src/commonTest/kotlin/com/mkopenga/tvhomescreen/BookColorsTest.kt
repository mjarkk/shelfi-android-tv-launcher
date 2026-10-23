package com.mkopenga.tvhomescreen

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.mkopenga.tvhomescreen.ui.ArtworkCoverLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BookColorsTest {
    private val banner = 16f / 9f

    @Test
    fun solidBackdropIsTheSpineUnchanged() {
        for (color in listOf(Color(0xFFFF5500), Color(0xFF777777), Color(0xFFF3EEE3), Color(0xFF101010), Color(0xFF3355FF))) {
            val colors = assertNotNull(Backdrop.Solid(color).bookColors(banner, banner = true, accents = emptyList()))
            assertEquals(color, colors.spine)
            assertTrue(contrastRatio(colors.ink, color) >= MinTextContrast, "ink ${colors.ink} on $color")
        }
    }

    @Test
    fun inkReadsEverywhereUnderTheTitleOnAGradient() {
        val gradients = listOf(
            Backdrop.Linear(listOf(Color(0xFF5A2DB0), Color(0xFF2E2F5A)), Offset(0f, 0.5f), Offset(1f, 0.5f)),
            Backdrop.Linear(listOf(Color(0xFFF7D9A0), Color(0xFFB0507A)), Offset(0.5f, 0f), Offset(0.5f, 1f)),
            Backdrop.Linear(listOf(Color(0xFFFFE6B3), Color(0xFFF0C070), Color(0xFFE0A040)), Offset(0f, 0f), Offset(1f, 1f)),
        )
        for (gradient in gradients) {
            val colors = assertNotNull(gradient.bookColors(banner, banner = true, accents = emptyList()))
            val art = ArtworkCoverLayout.artRect(banner, banner = true)
            val title = ArtworkCoverLayout.TitleArea
            for (i in 0..20) for (j in 0..10) {
                val under = gradient.colorAt(Offset(title.left + title.width * i / 20, title.top + title.height * j / 10), art)
                assertTrue(contrastRatio(colors.ink, under) >= MinTextContrast, "ink ${colors.ink} on $under")
            }
            assertTrue(contrastRatio(colors.ink, colors.spine) >= MinTextContrast)
        }
    }

    @Test
    fun spineContinuesTheCoversLeftEdge() {
        val gradient = Backdrop.Linear(listOf(Color(0xFF5A2DB0), Color(0xFF2E2F5A)), Offset(0f, 0.5f), Offset(1f, 0.5f))
        val colors = assertNotNull(gradient.bookColors(banner, banner = true, accents = emptyList()))
        assertEquals(Color(0xFF5A2DB0), colors.spine)
    }

    @Test
    fun gradientFromWhiteToBlackAcrossTheTitleHasNoInk() {
        val steep = Backdrop.Linear(listOf(Color.White, Color.Black), Offset(0f, 0.5f), Offset(1f, 0.5f))
        assertNull(steep.bookColors(banner, banner = true, accents = emptyList()))
    }

    @Test
    fun busyBackdropShiftsItsColourUntilTheInkReads() {
        val colors = Backdrop.Busy(Color(0xFF8899AA)).bookColors(banner, banner = true, accents = emptyList())!!
        assertTrue(contrastRatio(colors.ink, colors.spine) >= MinTextContrast)
    }

    @Test
    fun accentTooFaintUnderTheMarkFallsBackToInk() {
        val faint = Color(0xFFFF6A1A)
        val colors = assertNotNull(Backdrop.Solid(Color(0xFFFF5500)).bookColors(banner, banner = true, accents = listOf(faint)))
        assertEquals(colors.ink, colors.mark)
    }
}
