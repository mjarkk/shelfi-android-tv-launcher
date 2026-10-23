package com.mkopenga.tvhomescreen.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mkopenga.tvhomescreen.resources.Res
import com.mkopenga.tvhomescreen.resources.space_grotesk_bold
import com.mkopenga.tvhomescreen.resources.space_grotesk_regular
import com.mkopenga.tvhomescreen.resources.space_grotesk_variable
import org.jetbrains.compose.resources.Font

internal object Palette {
    /** Only seen where the 16:9 canvas is letterboxed; the wall covers the rest. */
    val Background = Color(0xFF13241D)
    val Cream = Color(0xFFF3EEE3)
    val Muted = Color(0xFFDCCFB6)
    val Plank = Color(0xFF6B4A2E)
    val PlankShadow = Color(0xFF4A3220)

    /** Either side of the cream focus ring, so it reads on a light wallpaper too. */
    val FocusEdge = Color(0xB32A1A0F)

    /** Behind the empty shelf's message, which would otherwise sit straight on the wallpaper. */
    val Plaque = Color(0xFF2E2219)
}

@Immutable
internal class Typography(val family: FontFamily) {
    val infoTitle = TextStyle(fontFamily = family, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Palette.Cream)
    val infoSubtitle = TextStyle(fontFamily = family, fontSize = 20.sp, color = Palette.Muted)
    val spine = TextStyle(fontFamily = family, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.02.em)

    // CSS line-height: 1 puts the half-leading on both sides of each line; Compose's default trims it.
    val coverTitle = TextStyle(
        fontFamily = family,
        fontSize = 40.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 40.sp,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )
    val emptyTitle = TextStyle(fontFamily = family, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = Palette.Cream)
    val emptyBody = TextStyle(fontFamily = family, fontSize = 20.sp, color = Palette.Muted)
}

internal val LocalTypography = staticCompositionLocalOf<Typography> { error("Typography not provided") }

@Composable
internal fun rememberTypography(): Typography {
    // Upstream ships no static SemiBold, so 600 is an instance of the official variable font.
    val regular = Font(Res.font.space_grotesk_regular, FontWeight.Normal)
    val semiBold = Font(Res.font.space_grotesk_variable, FontWeight.SemiBold)
    val bold = Font(Res.font.space_grotesk_bold, FontWeight.Bold)
    return remember(regular, semiBold, bold) { Typography(FontFamily(regular, semiBold, bold)) }
}
