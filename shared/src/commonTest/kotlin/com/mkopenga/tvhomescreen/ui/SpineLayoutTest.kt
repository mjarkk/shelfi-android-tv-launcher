package com.mkopenga.tvhomescreen.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpineLayoutTest {
    @Test
    fun iconIsAsWideAsTheSpineAllowsUpToFortyEight() {
        assertEquals(42f, SpineLayout.of(70f, 500f, icon = true).iconSize)
        assertEquals(48f, SpineLayout.of(84f, 500f, icon = true).iconSize)
        assertEquals(48f, SpineLayout.of(96f, 500f, icon = true).iconSize)
    }

    @Test
    fun iconSitsAsFarFromTheTopAsFromTheSides() {
        for (width in 70..96) {
            val layout = SpineLayout.of(width.toFloat(), 500f, icon = true)
            assertTrue(kotlin.math.abs(layout.iconTop - (width - layout.iconSize) / 2) <= 0.5f, "width $width: top ${layout.iconTop}")
        }
    }

    @Test
    fun titleStopsAGapShortOfTheIcon() {
        for (width in 70..96) for (height in listOf(460f, 505f, 550f)) {
            val layout = SpineLayout.of(width.toFloat(), height, icon = true)
            val titleEnd = height - SpinePadding - layout.titleLength
            assertEquals(layout.iconTop + layout.iconSize + 16f, titleEnd, "width $width, height $height")
        }
    }

    @Test
    fun iconTakesTitleRoomThatTheMarkLeftFree() {
        for (width in 70..96) {
            val icon = SpineLayout.of(width.toFloat(), 500f, icon = true)
            val mark = SpineLayout.of(width.toFloat(), 500f, icon = false)
            assertTrue(icon.titleLength < mark.titleLength, "width $width")
        }
    }

    @Test
    fun withoutAnIconTheMarkKeepsItsPlaceAndTheTitleItsLength() {
        val layout = SpineLayout.of(84f, 500f, icon = false)
        assertEquals(SpinePadding, layout.iconTop)
        assertEquals(500f - 2 * SpinePadding - MarkExtent - 16f, layout.titleLength)
    }
}
