package com.mkopenga.tvhomescreen.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.mkopenga.tvhomescreen.Artwork
import com.mkopenga.tvhomescreen.Backdrop
import kotlin.math.roundToInt

/** In cover px. */
internal object ArtworkCoverLayout {
    private const val Margin = 32f
    private const val BannerMaxHeight = 240f
    private const val IconSize = 232f
    private const val CentreY = 350f

    /** Up to three 40px title lines from the top margin. */
    val TitleArea = Rect(Margin, 24f, CoverWidth - Margin, 172f)

    val MarkArea = Rect(Margin - 1f, CoverHeight - 60f, Margin + 27f, CoverHeight - 32f)

    val SpineEdge = Rect(0f, 0f, 2f, CoverHeight)

    fun artRect(aspect: Float, banner: Boolean): Rect {
        var width = IconSize
        var height = IconSize
        if (banner) {
            width = CoverWidth - 2 * Margin
            height = width / aspect
            if (height > BannerMaxHeight) {
                height = BannerMaxHeight
                width = height * aspect
            }
        }
        val left = ((CoverWidth - width) / 2).roundToInt().toFloat()
        val top = (CentreY - height / 2).roundToInt().toFloat()
        return Rect(left, top, left + width.roundToInt(), top + height.roundToInt())
    }
}

/** Draws in cover px, over the spine colour and under the title and mark. */
internal class ArtworkCover(val artwork: Artwork, spine: Color, ink: Color) {
    private val banner = artwork.banner
    private val art: ImageBitmap? = banner ?: artwork.icon
    private val rect = art?.let { ArtworkCoverLayout.artRect(it.width.toFloat() / it.height, banner != null) }
    private val fill: Brush? = when (val b = artwork.backdrop) {
        is Backdrop.Solid -> SolidColor(b.color)
        is Backdrop.Linear -> rect?.let {
            Brush.linearGradient(b.colors, it.topLeft + b.start.scaledBy(it.size), it.topLeft + b.end.scaledBy(it.size), TileMode.Clamp)
        }
        is Backdrop.Busy, null -> null
    }

    // The fitted gradient only roughly matches a banner's edge, so the edge pixels themselves are carried outward,
    // fading into the fit. Icons are drawn masked; their see-through corners already show the fill.
    private val blend = art != null && banner != null && artwork.backdrop is Backdrop.Linear
    private val edgeShader = if (blend) ShaderBrush(ImageShader(art!!, TileMode.Clamp, TileMode.Clamp)) else null

    private val plate = lerp(spine, ink, 0.12f)
    private val bannerClip = if (banner != null && fill == null) Path().apply { addRoundRect(RoundRect(rect!!, CornerRadius(8f))) } else null

    fun draw(scope: DrawScope) {
        val art = art ?: return
        val rect = rect!!
        scope.drawAround(art, rect)
    }

    private fun DrawScope.drawAround(art: ImageBitmap, rect: Rect) {
        if (fill != null) {
            drawRect(fill, size = Size(CoverWidth, CoverHeight))
            if (edgeShader != null) drawEdgeBlend(art, rect, edgeShader)
            drawArt(art, rect)
        } else if (bannerClip != null) {
            drawRoundRect(plate, rect.topLeft - Offset(MatWidth, MatWidth), Size(rect.width + 2 * MatWidth, rect.height + 2 * MatWidth), CornerRadius(8f + MatWidth))
            clipPath(bannerClip) { drawArt(art, rect) }
        } else {
            drawCircle(plate, radius = 120f, center = rect.center)
            drawArt(art, Rect(rect.center, 80f))
        }
    }

    private fun DrawScope.drawArt(art: ImageBitmap, rect: Rect) {
        drawImage(
            art,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(art.width, art.height),
            dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
            dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt()),
        )
    }

    private fun DrawScope.drawEdgeBlend(art: ImageBitmap, rect: Rect, shader: Brush) {
        val outer = Rect(rect.left - EdgeBlend, rect.top - EdgeBlend, rect.right + EdgeBlend, rect.bottom + EdgeBlend)
        drawContext.canvas.saveLayer(outer, Paint())
        val sx = rect.width / art.width
        val sy = rect.height / art.height
        translate(rect.left, rect.top) {
            scale(sx, sy, pivot = Offset.Zero) {
                drawRect(shader, Offset(-EdgeBlend / sx, -EdgeBlend / sy), Size(outer.width / sx, outer.height / sy))
            }
        }
        val e = EdgeBlend
        drawRect(Brush.verticalGradient(Fade, rect.top, rect.top - e), Offset(rect.left, rect.top - e), Size(rect.width, e), blendMode = BlendMode.DstIn)
        drawRect(Brush.verticalGradient(Fade, rect.bottom, rect.bottom + e), Offset(rect.left, rect.bottom), Size(rect.width, e), blendMode = BlendMode.DstIn)
        drawRect(Brush.horizontalGradient(Fade, rect.left, rect.left - e), Offset(rect.left - e, rect.top), Size(e, rect.height), blendMode = BlendMode.DstIn)
        drawRect(Brush.horizontalGradient(Fade, rect.right, rect.right + e), Offset(rect.right, rect.top), Size(e, rect.height), blendMode = BlendMode.DstIn)
        for (corner in listOf(rect.topLeft, rect.topRight, rect.bottomLeft, rect.bottomRight)) {
            val left = if (corner.x == rect.left) corner.x - e else corner.x
            val top = if (corner.y == rect.top) corner.y - e else corner.y
            drawRect(Brush.radialGradient(Fade, corner, e), Offset(left, top), Size(e, e), blendMode = BlendMode.DstIn)
        }
        drawContext.canvas.restore()
    }

    private companion object {
        const val MatWidth = 8f
        const val EdgeBlend = 40f

        /** 1 - smoothstep, from the art's edge outward. */
        val Fade = listOf(1f, 0.896f, 0.648f, 0.352f, 0.104f, 0f).map { Color.Black.copy(alpha = it) }
    }
}

private fun Offset.scaledBy(size: Size) = Offset(x * size.width, y * size.height)
