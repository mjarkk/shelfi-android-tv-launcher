package com.mkopenga.tvhomescreen

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import com.mkopenga.tvhomescreen.ui.ArtworkCoverLayout
import kotlin.math.floor

class BookColors(val spine: Color, val ink: Color, val mark: Color)

val LightInk = Color(0xFFF3EEE3)
val DarkInk = Color(0xFF13241D)

/** WCAG's minimum for graphics; the spine mark is one. */
private const val MinMarkContrast = 3f

/** [backdrop] is the one to draw, which can differ from the art's. */
class CoverColors(val backdrop: Backdrop?, val colors: BookColors)

/**
 * [artAspect] is width over height and [accents] are mark colours to try, best first; [fallback] is called only when
 * [backdrop] is null. The ink reads at [MinTextContrast] over everything the title can sit on.
 */
fun coverColorsFor(backdrop: Backdrop?, artAspect: Float, banner: Boolean, accents: List<Color>, fallback: () -> Color): CoverColors {
    if (backdrop == null) return CoverColors(null, framedBookColors(fallback(), accents))
    backdrop.bookColors(artAspect, banner, accents)?.let { return CoverColors(backdrop, it) }
    val middle = (backdrop as Backdrop.Linear).colors.let { it[it.size / 2] }
    return CoverColors(Backdrop.Busy(middle), framedBookColors(middle, accents))
}

/** Null only for a gradient that no ink, or no mark colour, stands out on everywhere. */
internal fun Backdrop.bookColors(artAspect: Float, banner: Boolean, accents: List<Color>): BookColors? = when (this) {
    is Backdrop.Solid -> continued(color, listOf(color), listOf(color), accents)
    is Backdrop.Linear -> {
        val art = ArtworkCoverLayout.artRect(artAspect, banner)
        val edge = ArtworkCoverLayout.SpineEdge.samples().map { colorAt(it, art) }.average()
        val title = ArtworkCoverLayout.TitleArea.samples().map { colorAt(it, art) }
        val mark = ArtworkCoverLayout.MarkArea.samples().map { colorAt(it, art) }
        continued(edge, title, mark, accents)
    }
    is Backdrop.Busy -> framedBookColors(dominant, accents)
}

internal fun framedBookColors(base: Color, accents: List<Color>): BookColors {
    val ink = if (contrastRatio(base, LightInk) >= contrastRatio(base, DarkInk)) LightInk else DarkInk
    val spine = base.adjustedForInk(ink)
    return BookColors(spine, ink, accents.firstOrNull { contrastRatio(it, ink) >= 1.5f && contrastRatio(it, spine) >= MinMarkContrast } ?: ink)
}

private fun continued(edge: Color, title: List<Color>, mark: List<Color>, accents: List<Color>): BookColors? {
    val ink = inksFor(title).firstOrNull { it.worstOn(title) >= MinTextContrast } ?: return null
    val spine = edge.adjustedForInk(ink)
    val under = mark + spine
    val markColor = accents.firstOrNull { contrastRatio(it, ink) >= 1.5f && it.worstOn(under) >= MinMarkContrast }
        ?: (listOf(ink) + inksFor(under)).firstOrNull { it.worstOn(under) >= MinMarkContrast }
        ?: return null
    return BookColors(spine, ink, markColor)
}

private fun inksFor(under: List<Color>) =
    listOf(LightInk, DarkInk).sortedByDescending { it.worstOn(under) } + listOf(Color.White, Color.Black).sortedByDescending { it.worstOn(under) }

private fun Color.worstOn(under: List<Color>) = under.minOf { contrastRatio(this, it) }

/** Interpolates in sRGB, as drawing does. */
internal fun Backdrop.Linear.colorAt(p: Offset, art: Rect): Color {
    val sx = art.left + start.x * art.width
    val sy = art.top + start.y * art.height
    val dx = art.left + end.x * art.width - sx
    val dy = art.top + end.y * art.height - sy
    val length2 = dx * dx + dy * dy
    val t = if (length2 == 0f) 0f else (((p.x - sx) * dx + (p.y - sy) * dy) / length2).coerceIn(0f, 1f)
    val position = t * (colors.size - 1)
    val j = floor(position).toInt().coerceAtMost(colors.size - 2)
    return srgbLerp(colors[j], colors[j + 1], position - j)
}

internal fun srgbLerp(a: Color, b: Color, f: Float) =
    Color(a.red + (b.red - a.red) * f, a.green + (b.green - a.green) * f, a.blue + (b.blue - a.blue) * f)

private fun List<Color>.average() =
    Color(sumOf { it.red.toDouble() }.toFloat() / size, sumOf { it.green.toDouble() }.toFloat() / size, sumOf { it.blue.toDouble() }.toFloat() / size)

private fun Rect.samples(): List<Offset> =
    (0..6).flatMap { i -> (0..4).map { j -> Offset(left + width * i / 6, top + height * j / 4) } }
