package com.mkopenga.tvhomescreen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import kotlin.math.max
import kotlin.math.roundToInt

internal class SpineColors(val spine: Color, val ink: Color, val mark: Color, val backdrop: Backdrop?)

// Used when the palette filters everything out, e.g. a pure black-and-white logo.
private val FallbackSpines = listOf(
    0xFF8A2C2C, 0xFF2F4B6E, 0xFF3F6E4E, 0xFF5A3A7A, 0xFFC8693B, 0xFF1F3A4A, 0xFFE3B23C, 0xFF7FB7C9,
).map(::Color)

private const val AnalysisLongSide = 320
private const val AnalysisIconPx = 192

internal const val BannerFallbackWidthPx = 640
internal const val BannerFallbackHeightPx = 360

/** Reads a small render of [art], so it's cheap enough for every app on the shelf. */
internal fun spineColorsFor(art: Drawable, banner: Boolean, seed: String): SpineColors {
    val (width, height) = if (banner) analysisSize(art) else AnalysisIconPx to AnalysisIconPx
    val drawn = art.coverBitmap(width, height, banner)
    // A masked icon's corners are see-through; what's behind the logo is then the adaptive icon's layers unmasked.
    val backdrop = drawn.backdrop() ?: (art as? AdaptiveIconDrawable)?.let { it.fullBleed(width, height).backdrop() }
    val palette = Palette.from(drawn).maximumColorCount(16).generate()
    val accents = listOfNotNull(palette.vibrantSwatch, palette.lightVibrantSwatch, palette.darkVibrantSwatch).map { Color(it.rgb) }
    val cover = coverColorsFor(backdrop, width.toFloat() / height, banner, accents) {
        palette.dominantSwatch?.let { Color(it.rgb) } ?: FallbackSpines[Math.floorMod(seed.hashCode(), FallbackSpines.size)]
    }
    return SpineColors(cover.colors.spine, cover.colors.ink, cover.colors.mark, cover.backdrop)
}

internal fun Drawable.coverBitmap(width: Int, height: Int, banner: Boolean): Bitmap =
    if (banner && this is AdaptiveIconDrawable) fullBleed(width, height) else toBitmap(width, height, Bitmap.Config.ARGB_8888)

/** Before the bitmap goes to the GPU, where it can no longer be read. */
internal fun Bitmap.preparedFor(backdrop: Backdrop?): Bitmap {
    if (backdrop !is Backdrop.Solid && backdrop !is Backdrop.Linear) return this
    val ix = backdropInset(width)
    val iy = backdropInset(height)
    val w = width - 2 * ix
    val h = height - 2 * iy
    val pixels = IntArray(w * h)
    getPixels(pixels, 0, w, ix, iy, w, h)
    if (backdrop is Backdrop.Linear) softenEdges(pixels, w, h)
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
}

private fun analysisSize(art: Drawable): Pair<Int, Int> {
    val w = art.intrinsicWidth.takeIf { it > 0 } ?: BannerFallbackWidthPx
    val h = art.intrinsicHeight.takeIf { it > 0 } ?: BannerFallbackHeightPx
    val scale = AnalysisLongSide.toFloat() / max(w, h)
    return max(1, (w * scale).roundToInt()) to max(1, (h * scale).roundToInt())
}

private fun AdaptiveIconDrawable.fullBleed(width: Int, height: Int): Bitmap = layered(width, height, listOfNotNull(background, foreground))

/** [layers] placed as an adaptive icon places them, but unmasked. */
internal fun layered(width: Int, height: Int, layers: List<Drawable>): Bitmap {
    val bitmap = createBitmap(width, height)
    val canvas = Canvas(bitmap)
    val layerBounds = Rect(-width / 4, -height / 4, width + width / 4, height + height / 4)
    for (layer in layers) {
        val old = layer.copyBounds()
        layer.bounds = layerBounds
        layer.draw(canvas)
        layer.bounds = old
    }
    return bitmap
}

internal fun Bitmap.backdrop(): Backdrop? {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    return analyseBackdrop(pixels, width, height)
}
