package com.mkopenga.tvhomescreen

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContrastTest {
    @Test
    fun blackOnWhiteIsTwentyOne() {
        assertEquals(21f, contrastRatio(Color.Black, Color.White), 0.01f)
    }

    @Test
    fun midToneSpinesAreShiftedUntilInkIsReadable() {
        val cream = Color(0xFFF3EEE3)
        val forest = Color(0xFF13241D)
        for (spine in listOf(Color(0xFF5F7F79), Color(0xFFC8693B), Color(0xFF808080))) {
            for (ink in listOf(cream, forest, Color.White)) {
                assertTrue(contrastRatio(spine.adjustedForInk(ink), ink) >= MinTextContrast, "$spine / $ink")
            }
        }
    }

    @Test
    fun readableSpinesAreLeftAlone() {
        val spine = Color(0xFF8A2C2C)
        assertEquals(spine, spine.adjustedForInk(Color(0xFFF3EEE3)))
    }
}
