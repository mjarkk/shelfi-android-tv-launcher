package com.mkopenga.tvhomescreen.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.mkopenga.tvhomescreen.Artwork
import com.mkopenga.tvhomescreen.ShelfItem
import com.mkopenga.tvhomescreen.SpineMark
import kotlin.math.roundToInt

internal const val SpinePadding = 24f

/** In design px: a bound book's corner, not a box's. */
internal const val BookCorner = 6f
private const val CoverTitleLeft = 32f
private const val CoverTitleTop = 36f
private const val MinTitleSize = 26f

// Space Grotesk's hhea ascender and descender, in em.
private const val FontAscent = 0.984f
private const val FontDescent = 0.292f

/** Text is measured in design px; draws happen inside a scale(density) so one layout serves every screen size. */
private val DesignDensity = Density(1f)

/**
 * Draws the two faces of one book at their natural design sizes: the spine (its own width and height) and the
 * cover (400 wide, [coverHeight] tall). The shelf turns and scales them with layer transforms, so a face is recorded
 * once and never redrawn while it moves; only artwork arriving invalidates a face.
 *
 * Text layouts and cover art are built lazily on first draw (only books that come on screen pay for them) and kept.
 */
internal class ItemPainter(
    private val item: ShelfItem,
    private val typography: Typography,
    private val measurer: TextMeasurer,
) {
    private val spineWidth = ShelfGeometry.spineWidthOf(item).toFloat()
    private val spineHeight = ShelfGeometry.spineHeightOf(item).toFloat()
    val coverHeight = ShelfGeometry.coverHeightOf(item)

    private val spineLayout = SpineLayout.of(spineWidth, spineHeight, icon = item.spineIcon)
    private val spineText: TextLayoutResult by lazy(LazyThreadSafetyMode.NONE) {
        measure(item.spineLabel, typography.spine, width = spineLayout.titleLength, maxLines = 1)
    }
    private val coverTitle: CoverTitle by lazy(LazyThreadSafetyMode.NONE) { coverTitle(width = 228f, maxLines = 4) }
    private val artworkTitle: CoverTitle by lazy(LazyThreadSafetyMode.NONE) {
        coverTitle(width = CoverWidth - 2 * CoverTitleLeft, maxLines = 3)
    }
    private val art: CoverArt by lazy(LazyThreadSafetyMode.NONE) { CoverArt.of(item) }
    private var artworkCover: ArtworkCover? = null

    /** Reads [artworks] in the draw, so the spine redraws when its icon arrives. */
    fun drawSpine(scope: DrawScope, artworks: ArtworkStore? = null) {
        scope.drawProceduralSpine(if (item.spineIcon) artworks?.spineIcon(item.id) else null)
    }

    // .spine in the mockup: vertical-rl text rotated 180°, so the title starts 24px above the bottom and reads
    // upwards, and the mark sits 24px below the top. The rotation also flips the triangle to point down.
    private fun DrawScope.drawProceduralSpine(icon: ImageBitmap?) {
        drawRoundRect(item.spineColor, cornerRadius = CornerRadius(BookCorner * density))
        scale(density, density, pivot = Offset.Zero) {
            val cx = spineWidth / 2f
            val text = spineText
            withTransform({
                translate(cx, spineHeight - SpinePadding)
                rotate(-90f, pivot = Offset.Zero)
            }) {
                drawText(text, color = item.inkColor, topLeft = Offset(0f, -text.size.height / 2f))
            }
            if (icon != null) drawSpineIcon(icon, spineLayout, cx) else drawMark(item.mark, item.markColor, cx, SpinePadding, 1f)
        }
    }

    fun drawCover(scope: DrawScope, artworks: ArtworkStore) = with(scope) {
        drawRect(item.spineColor)
        scale(density, density, pivot = Offset.Zero) {
            val artwork = artworks[item.id]
            if (artwork != null) drawArtworkCover(artwork) else drawProceduralCover()
        }
    }

    private fun DrawScope.drawProceduralCover() {
        art.draw(this)
        drawTitle(coverTitle)
    }

    private fun DrawScope.drawTitle(title: CoverTitle) {
        drawText(title.layout, color = item.inkColor, topLeft = Offset(CoverTitleLeft, title.top))
    }

    // Title on top as on every cover, the app's own art centred in the space below, and the spine's mark at the
    // foot like a publisher's logo.
    private fun DrawScope.drawArtworkCover(artwork: Artwork) {
        val cover = artworkCover?.takeIf { it.artwork === artwork }
            ?: ArtworkCover(artwork, item.spineColor, item.inkColor).also { artworkCover = it }
        cover.draw(this)
        drawTitle(artworkTitle)
        drawMark(item.mark, item.markColor, CoverTitleLeft + 13f, CoverHeight - 36f - 24f, 1f)
    }

    /** Shrinks the 40px title only as far as its longest word needs, so no word is ever broken across lines. */
    private fun coverTitle(width: Float, maxLines: Int): CoverTitle {
        var style = typography.coverTitle
        val widest = item.title.split(' ').maxOf { word -> measure(word, style, Float.POSITIVE_INFINITY, 1).size.width }
        if (widest > width) {
            val size = (style.fontSize.value * width / widest).coerceAtLeast(MinTitleSize)
            style = style.copy(fontSize = size.sp, lineHeight = size.sp)
        }
        val layout = measure(item.title, style, width, maxLines)
        // CSS line-height: 1 hangs each line's glyphs half a leading above the line box, which is negative here.
        // Text engines differ on that for the first line, so place the first baseline explicitly.
        val size = style.fontSize.value
        val baseline = CoverTitleTop + size / 2 + size * (FontAscent - FontDescent) / 2
        return CoverTitle(layout, baseline - layout.firstBaseline)
    }

    private class CoverTitle(val layout: TextLayoutResult, val top: Float)

    private fun measure(text: String, style: TextStyle, width: Float, maxLines: Int): TextLayoutResult =
        measurer.measure(
            text = text,
            style = style,
            overflow = TextOverflow.Ellipsis,
            maxLines = maxLines,
            constraints = if (width.isFinite()) Constraints(maxWidth = width.roundToInt().coerceAtLeast(0)) else Constraints(),
            layoutDirection = LayoutDirection.Ltr,
            density = DesignDensity,
            skipCache = true,
        )
}

internal const val MarkExtent = 28f
private val MarkStroke = Stroke(width = 3f)

private val LeafPath = Path().apply {
    addRoundRect(
        RoundRect(
            left = 0f, top = 0f, right = 20f, bottom = 20f,
            topLeftCornerRadius = CornerRadius(10f), bottomRightCornerRadius = CornerRadius(10f),
        ),
    )
}
private val DownTrianglePath = Path().apply {
    moveTo(0f, 0f)
    lineTo(24f, 0f)
    lineTo(12f, 24f)
    close()
}
private const val DiamondHalf = 14.142136f
private val DiamondPath = Path().apply {
    moveTo(0f, -DiamondHalf)
    lineTo(DiamondHalf, 0f)
    lineTo(0f, DiamondHalf)
    lineTo(-DiamondHalf, 0f)
    close()
}

/** Marks as sized in the mockup, centred on [cx] with their box's top edge at [top]. */
internal fun DrawScope.drawMark(mark: SpineMark, color: Color, cx: Float, top: Float, alpha: Float) {
    when (mark) {
        SpineMark.Dot -> drawCircle(color, 11f, Offset(cx, top + 11f), alpha)
        SpineMark.Ring -> drawCircle(color, 9.5f, Offset(cx, top + 11f), alpha, style = MarkStroke)
        SpineMark.Bar -> drawRect(color, Offset(cx - 13f, top), Size(26f, 6f), alpha)
        SpineMark.Square -> drawRect(color, Offset(cx - 9.5f, top + 1.5f), Size(19f, 19f), alpha, style = MarkStroke)
        SpineMark.Tile -> drawRect(color, Offset(cx - 11f, top), Size(22f, 22f), alpha)
        SpineMark.Leaf -> translate(cx - 10f, top) { drawPath(LeafPath, color, alpha) }
        SpineMark.Triangle -> translate(cx - 12f, top) { drawPath(DownTrianglePath, color, alpha) }
        SpineMark.Diamond -> translate(cx, top + 10f) { drawPath(DiamondPath, color, alpha) }
    }
}

internal fun smoothstep(from: Float, to: Float, x: Float): Float {
    val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
