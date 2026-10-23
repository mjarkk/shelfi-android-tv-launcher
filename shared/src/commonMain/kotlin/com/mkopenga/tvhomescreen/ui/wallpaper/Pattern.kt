package com.mkopenga.tvhomescreen.ui.wallpaper

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * One wallpaper's art: a [width]x[height] design-px repeat painted by [motifs] over a [ground] colour and a paper
 * texture, and the colour of the panelling below the dado that goes with it.
 */
internal class Pattern(
    val wallpaper: Wallpaper,
    val width: Float,
    val height: Float,
    val ground: Color,
    /** The painted wainscot under the shelf; the info line's cream text sits on it, so it must stay dark. */
    val wainscot: Color,
    private val seed: Int,
    private val motifs: TileScope.() -> Unit,
) {
    /** Renders one repeat at [scale] device px per design px. Safe off the main thread. */
    fun render(scale: Float): ImageBitmap {
        val w = (width * scale).roundToInt().coerceAtLeast(1)
        val h = (height * scale).roundToInt().coerceAtLeast(1)
        val image = ImageBitmap(w, h)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(w.toFloat(), h.toFloat())) {
            // Scaled per axis to the rounded pixel size, so the repeat stays seamless at any density.
            scale(w / width, h / height, pivot = Offset.Zero) {
                drawRect(ground, size = Size(width, height))
                val tile = TileScope(this, width, height)
                tile.paper(Hand(seed), ground)
                tile.motifs()
                tile.grain(Hand(seed + 1), ground)
            }
        }
        return image
    }
}

/** Draws into one repeat of a pattern; anything placed near an edge is drawn again on the far side. */
internal class TileScope(private val scope: DrawScope, val width: Float, val height: Float) {
    /** Draws [block] with its origin at [x],[y], and its wrapped copies wherever [reach] crosses an edge. */
    fun place(x: Float, y: Float, reach: Float, block: DrawScope.() -> Unit) {
        for (dx in -1..1) {
            val cx = x + dx * width
            if (cx + reach < 0f || cx - reach > width) continue
            for (dy in -1..1) {
                val cy = y + dy * height
                if (cy + reach < 0f || cy - reach > height) continue
                scope.translate(cx, cy) { block() }
            }
        }
    }

    /** Draws something laid out over the whole repeat, like stripes, that is seamless by construction. */
    fun across(block: DrawScope.() -> Unit) = scope.block()

    /**
     * Up to [count] random points at least [spacing] apart, wrapping around the edges like the pattern does, and
     * [clearance] away from everything in [avoid].
     */
    fun scatter(hand: Hand, count: Int, spacing: Float, avoid: List<Offset> = emptyList(), clearance: Float = 0f): List<Offset> {
        val out = ArrayList<Offset>(count)
        var tries = 0
        while (out.size < count && tries < count * 300) {
            tries++
            val p = Offset(hand.between(0f, width), hand.between(0f, height))
            if (out.all { distance(it, p) >= spacing } && avoid.all { distance(it, p) >= clearance }) out += p
        }
        return out
    }

    fun distance(a: Offset, b: Offset): Float {
        val dx = abs(a.x - b.x).let { min(it, width - it) }
        val dy = abs(a.y - b.y).let { min(it, height - it) }
        return sqrt(dx * dx + dy * dy)
    }

    /** Old paper: faint mottling, specks and a few fibres, in the ground's own darker and lighter tones. */
    fun paper(hand: Hand, ground: Color) {
        val dark = lerp(ground, Ink, 0.55f)
        val light = lerp(ground, Color.White, 0.7f)
        repeat(7) {
            val x = hand.between(0f, width)
            val y = hand.between(0f, height)
            val r = hand.between(40f, 95f)
            val tone = if (hand.chance(0.55f)) dark else light
            val a = hand.between(0.05f, 0.09f)
            val brush = Brush.radialGradient(listOf(tone.copy(alpha = a), tone.copy(alpha = 0f)), center = Offset.Zero, radius = r)
            place(x, y, r) { drawCircle(brush, r, Offset.Zero) }
        }
        repeat((width * height / 2600f).roundToInt()) {
            val x = hand.between(0f, width)
            val y = hand.between(0f, height)
            val a = polar(hand.between(2.5f, 6f), hand.between(0f, 360f))
            val bend = Offset(hand.jitter(1.2f), hand.jitter(1.2f))
            val color = dark.copy(alpha = hand.between(0.08f, 0.16f))
            val path = smoothOpen(listOf(-a, bend, a))
            place(x, y, 7f) { line(path, color, 0.6f) }
        }
        grain(hand, ground)
    }

    /** Tiny specks, also dusted over the motifs so they sit in the paper rather than on it. */
    fun grain(hand: Hand, ground: Color) {
        val dark = lerp(ground, Ink, 0.6f)
        val light = lerp(ground, Color.White, 0.8f)
        repeat((width * height / 520f).roundToInt()) {
            val x = hand.between(0f, width)
            val y = hand.between(0f, height)
            val r = hand.between(0.35f, 0.95f)
            val color = (if (hand.chance(0.7f)) dark else light).copy(alpha = hand.between(0.1f, 0.24f))
            place(x, y, r) { drawCircle(color, r, Offset.Zero) }
        }
    }
}
