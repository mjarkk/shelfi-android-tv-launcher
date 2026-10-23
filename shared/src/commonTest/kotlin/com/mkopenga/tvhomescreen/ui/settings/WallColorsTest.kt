package com.mkopenga.tvhomescreen.ui.settings

import com.mkopenga.tvhomescreen.MinTextContrast
import com.mkopenga.tvhomescreen.contrastRatio
import kotlin.test.Test
import kotlin.test.assertTrue

class WallColorsTest {
    @Test
    fun everyTextOnTheWallIsReadable() {
        val pairs = listOf(
            "label title" to (WallColors.Ink to WallColors.Paper),
            "label body" to (WallColors.InkSoft to WallColors.Paper),
            "hidden card title" to (WallColors.InkOff to WallColors.PaperOff),
            "hidden card body" to (WallColors.InkSoft to WallColors.PaperOff),
        )
        for ((name, colors) in pairs) {
            val ratio = contrastRatio(colors.first, colors.second)
            assertTrue(ratio >= MinTextContrast, "$name: $ratio")
        }
    }
}
