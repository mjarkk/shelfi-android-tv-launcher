package com.mkopenga.tvhomescreen.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The cover's `box-shadow: 0 40px 60px -20px rgba(0,0,0,.6)`, rendered once into a small bitmap and stretched to
 * the item's size every frame. Blurring live would cost a RenderEffect (API 31+) or an offscreen pass per frame.
 */
internal class CoverShadow private constructor(private val image: ImageBitmap) {
    private val srcSize = IntSize(image.width, image.height)

    /** Draws the shadow of an item at [left],[top] of [width]x[height] px, scaled from the 400x560 cover. */
    fun DrawScope.drawShadow(left: Int, top: Int, width: Int, height: Int, alpha: Float) {
        val sx = width / CoverWidth
        val sy = height / CoverHeight
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = srcSize,
            dstOffset = IntOffset((left + MinX * sx).roundToInt(), (top + MinY * sy).roundToInt()),
            dstSize = IntSize((SpanX * sx).roundToInt(), (SpanY * sy).roundToInt()),
            alpha = alpha,
        )
    }

    companion object {
        private const val Sigma = 30f
        private const val Reach = 3 * Sigma
        private const val Spread = -20f
        private const val OffsetY = 40f
        private const val Opacity = 0.6f
        private const val Resolution = 0.5f

        private const val RectLeft = -Spread
        private const val RectRight = CoverWidth + Spread
        private const val RectTop = -Spread + OffsetY
        private const val RectBottom = CoverHeight + Spread + OffsetY
        private const val MinX = RectLeft - Reach
        private const val MinY = RectTop - Reach
        private const val SpanX = RectRight - RectLeft + 2 * Reach
        private const val SpanY = RectBottom - RectTop + 2 * Reach

        fun create(): CoverShadow {
            val width = (SpanX * Resolution).roundToInt()
            val height = (SpanY * Resolution).roundToInt()
            val image = ImageBitmap(width, height)
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(width.toFloat(), height.toFloat())) {
                // A blurred rectangle is separable: alpha(x, y) = fx(x) * fy(y), so a vertical gradient masked by a
                // horizontal one (DstIn multiplies alphas) reproduces the Gaussian exactly at the stops.
                drawRect(profile(vertical = true), size = size)
                drawRect(profile(vertical = false), size = size, blendMode = BlendMode.DstIn)
            }
            return CoverShadow(image)
        }

        private fun DrawScope.profile(vertical: Boolean): Brush {
            val (from, to) = if (vertical) RectTop to RectBottom else RectLeft to RectRight
            val min = if (vertical) MinY else MinX
            val span = if (vertical) SpanY else SpanX
            val opacity = if (vertical) Opacity else 1f
            val stops = Array(Stops) { i ->
                val f = i / (Stops - 1f)
                val x = min + f * span
                f to Color.Black.copy(alpha = opacity * blurredBox(x, from, to))
            }
            return if (vertical) Brush.verticalGradient(*stops, endY = size.height) else Brush.horizontalGradient(*stops, endX = size.width)
        }

        private const val Stops = 48

        private fun blurredBox(x: Float, from: Float, to: Float): Float {
            val k = 1f / (Sigma * sqrt(2f))
            return 0.5f * (erf((x - from) * k) - erf((x - to) * k))
        }

        // Abramowitz-Stegun 7.1.26, max error 1.5e-7.
        private fun erf(x: Float): Float {
            val t = 1f / (1f + 0.3275911f * abs(x))
            val y = 1f - (((((1.061405429f * t - 1.453152027f) * t) + 1.421413741f) * t - 0.284496736f) * t + 0.254829592f) * t * exp(-x * x)
            return if (x >= 0) y else -y
        }
    }
}
