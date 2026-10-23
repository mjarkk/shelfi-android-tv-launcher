package com.mkopenga.tvhomescreen.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.mkopenga.tvhomescreen.Artwork
import com.mkopenga.tvhomescreen.Backdrop
import com.mkopenga.tvhomescreen.ShelfItem
import com.mkopenga.tvhomescreen.SpineMark
import com.mkopenga.tvhomescreen.analyseBackdrop
import com.mkopenga.tvhomescreen.backdropInset
import com.mkopenga.tvhomescreen.coverColorsFor
import com.mkopenga.tvhomescreen.softenEdges
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import kotlin.random.Random

/** An app as the Android repository hands it over. */
internal class AnalysedApp(val item: ShelfItem, val artwork: Artwork, val spineIcon: ImageBitmap)

internal fun analysedApp(title: String, banner: ImageBitmap? = null, icon: ImageBitmap? = null): AnalysedApp {
    val art = requireNotNull(banner ?: icon)
    val pixels = IntArray(art.width * art.height).also { art.readPixels(it) }
    val cover = coverColorsFor(analyseBackdrop(pixels, art.width, art.height), art.width.toFloat() / art.height, banner != null, emptyList()) {
        Color(0xFF2F4B6E)
    }
    val drawn = banner?.preparedFor(cover.backdrop)
    val item = ShelfItem(
        id = title.lowercase().replace(' ', '-'),
        title = title,
        spineColor = cover.colors.spine,
        inkColor = cover.colors.ink,
        markColor = cover.colors.mark,
        mark = SpineMark.Dot,
        subtitle = cover.backdrop.toString(),
        isGame = false,
        lastUsedAt = null,
        spineIcon = true,
    )
    return AnalysedApp(item, Artwork(icon = icon, banner = drawn, backdrop = cover.backdrop), FixtureArt.spineIcon(cover.colors.spine))
}

internal object FixtureArt {
    val soundCloudLike = banner { drawRect(Color(0xFFFF5500)); cloud(Color.White) }

    /** Like KIJK's: a darker hill crosses the gradient's bottom edge. */
    val kijkLike = banner {
        drawRect(Brush.linearGradient(listOf(Color(0xFF6A34C8), Color(0xFF2E2F5A)), Offset(0f, size.height), Offset(size.width, size.height * 0.2f)))
        drawOval(Color(0xFF34305E), Offset(size.width * 0.12f, size.height * 0.84f), Size(size.width * 0.76f, size.height * 0.5f))
        drawOval(Color.White, Offset(size.width * 0.2f, size.height * 0.14f), Size(size.width * 0.6f, size.height * 0.6f), style = Stroke(size.height * 0.07f))
        drawRect(Color.White, Offset(size.width * 0.38f, size.height * 0.36f), Size(size.width * 0.24f, size.height * 0.16f))
    }

    val verticalGradient = banner {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF1FA2A6), Color(0xFF0E4D3A))))
        cloud(Color(0xFFF3EEE3))
    }

    val diagonalSunset = banner {
        drawRect(Brush.linearGradient(listOf(Color(0xFFFFB25B), Color(0xFFE8517A), Color(0xFF6B2C91)), Offset.Zero, Offset(size.width, size.height)))
        cloud(Color.White)
    }

    val photo = banner {
        val random = Random(11)
        repeat(400) {
            val c = Color(random.nextInt(40, 230), random.nextInt(40, 200), random.nextInt(30, 180))
            drawRect(c, Offset(random.nextFloat() * size.width - 40f, random.nextFloat() * size.height - 40f), Size(20f + random.nextFloat() * 90f, 20f + random.nextFloat() * 90f))
        }
        cloud(Color.White)
    }

    val logoOffTheEdge = banner {
        drawRect(Color(0xFF1E5BD8))
        drawCircle(Color.White, size.height * 0.45f, Offset(size.width * 0.5f, size.height * 0.1f))
    }

    /** No ink reads across it, so it's framed. */
    val steepGradient = banner {
        drawRect(Brush.horizontalGradient(listOf(Color.White, Color.Black)))
        cloud(Color(0xFFE8517A))
    }

    val roundedIcon = image(384, 384) {
        drawRoundRect(Color(0xFFC62E3A), cornerRadius = CornerRadius(size.width * 0.18f))
        drawCircle(Color.White, size.width * 0.22f, center)
    }

    val clearIcon = image(384, 384) {
        drawCircle(Color(0xFF3F6E4E), size.width * 0.4f, center)
        drawCircle(Color(0xFFF3EEE3), size.width * 0.18f, center)
    }

    /** As Android shapes one whose background matches the spine: the logo alone. */
    fun spineIcon(spine: Color) = image(96, 96) { cloud(if (spine.luminance() > 0.5f) Color(0xFF13241D) else Color.White) }

    private fun DrawScope.cloud(color: Color) {
        val h = size.height
        val cx = size.width / 2
        val cy = h * 0.52f
        drawCircle(color, h * 0.16f, Offset(cx - h * 0.18f, cy))
        drawCircle(color, h * 0.22f, Offset(cx + h * 0.06f, cy - h * 0.06f))
        drawRoundRect(color, Offset(cx - h * 0.34f, cy), Size(h * 0.68f, h * 0.16f), CornerRadius(h * 0.08f))
    }

    private fun banner(draw: DrawScope.() -> Unit) = image(640, 360, draw)

    private fun image(width: Int, height: Int, draw: DrawScope.() -> Unit): ImageBitmap {
        val image = ImageBitmap(width, height)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(width.toFloat(), height.toFloat()), draw)
        return image
    }
}

/** As the Android repository prepares a banner for its cover. */
private fun ImageBitmap.preparedFor(backdrop: Backdrop?): ImageBitmap {
    if (backdrop !is Backdrop.Solid && backdrop !is Backdrop.Linear) return this
    val ix = backdropInset(width)
    val iy = backdropInset(height)
    val w = width - 2 * ix
    val h = height - 2 * iy
    val pixels = IntArray(w * h).also { readPixels(it, startX = ix, startY = iy, width = w, height = h) }
    if (backdrop is Backdrop.Linear) softenEdges(pixels, w, h)
    return Image.makeRaster(ImageInfo.makeN32(w, h, ColorAlphaType.UNPREMUL), pixels.toBgraBytes(), w * 4).toComposeImageBitmap()
}

private fun IntArray.toBgraBytes(): ByteArray {
    val bytes = ByteArray(size * 4)
    for ((i, p) in withIndex()) {
        bytes[i * 4] = (p and 0xFF).toByte()
        bytes[i * 4 + 1] = ((p shr 8) and 0xFF).toByte()
        bytes[i * 4 + 2] = ((p shr 16) and 0xFF).toByte()
        bytes[i * 4 + 3] = (p ushr 24).toByte()
    }
    return bytes
}
