package com.mkopenga.tvhomescreen.ui.settings

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Colours of the things hung on the wall. The wallpaper behind them can be anything, so every piece of text sits on
 * its own opaque paper; see WallColorsTest for the contrast pairs.
 */
internal object WallColors {
    val Paper = Color(0xFFF3EEE3)
    val PaperEdge = Color(0xFFD3C9B4)
    /** A hidden app's card: greyed, like a card pulled from the catalogue. */
    val PaperOff = Color(0xFFD5CEBE)
    val Ink = Color(0xFF13241D)
    val InkSoft = Color(0xFF4B4740)
    val InkOff = Color(0xFF3E3A34)
    val MatBevel = Color(0xFFFBF8F1)
    val Brass = Color(0xFFD4A84E)
    val BrassShade = Color(0xFF7E5E22)
    val Cord = Color(0xFF3B2A1C)
    /** The sample book's cloth in the Wallpaper frame, and the mat around the wallpaper on the wall now. */
    val Claret = Color(0xFF6B3434)
}

@Immutable
internal class FrameFinish(val base: Color, val light: Color, val dark: Color)

internal object Finishes {
    val Gilt = FrameFinish(Color(0xFFB98A3B), Color(0xFFE3C274), Color(0xFF6E4F1C))
    val Walnut = FrameFinish(Color(0xFF5A3A22), Color(0xFF8A6342), Color(0xFF2E1C0E))
    val Oak = FrameFinish(Color(0xFF9E6A3C), Color(0xFFCB9862), Color(0xFF5E3C1E))
    val Sage = FrameFinish(Color(0xFF7F9672), Color(0xFFAFC09E), Color(0xFF4A5C40))
}

@Immutable
internal class WallType(family: FontFamily?) {
    val labelTitle = TextStyle(fontFamily = family, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = WallColors.Ink)
    val labelBody = TextStyle(fontFamily = family, fontSize = 20.sp, color = WallColors.InkSoft)
    val cardTitle = TextStyle(fontFamily = family, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = WallColors.Ink)
    val cardTitleOff = cardTitle.copy(color = WallColors.InkOff)
    val cardBody = TextStyle(fontFamily = family, fontSize = 18.sp, color = WallColors.InkSoft)
}
