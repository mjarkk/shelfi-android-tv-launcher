package com.mkopenga.tvhomescreen

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.sqrt

/** Twice the largest spine icon, in design px, so it stays sharp on a 4K screen. */
internal const val SpineIconPx = 96

private const val TileCorner = 0.24f
private const val LegacyScale = 0.78f
private const val NearColour = 40f

internal fun Drawable.spineIcon(spine: Color, plate: Color): Bitmap {
    val px = SpineIconPx
    if (this is AdaptiveIconDrawable) {
        val back = background
        val front = foreground
        // The logo alone, as if printed on the spine.
        if (back == null || back.isNear(spine)) return layered(px, px, listOfNotNull(front))
        return tile(layered(px, px, listOfNotNull(back, front)))
    }
    val own = toBitmap(px, px, Bitmap.Config.ARGB_8888)
    if (own.fillsSquare()) return tile(own)
    val content = createBitmap(px, px)
    val canvas = Canvas(content)
    canvas.drawColor(plate.toArgb())
    val side = (px * LegacyScale).toInt()
    val inset = ((px - side) / 2).toFloat()
    canvas.drawBitmap(toBitmap(side, side, Bitmap.Config.ARGB_8888), inset, inset, Paint(Paint.FILTER_BITMAP_FLAG))
    return tile(content)
}

private fun Drawable.isNear(color: Color): Boolean {
    val bitmap = layered(24, 24, listOf(this))
    val pixels = IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
    val r = pixels.sumOf { (it shr 16) and 0xFF } / pixels.size.toFloat()
    val g = pixels.sumOf { (it shr 8) and 0xFF } / pixels.size.toFloat()
    val b = pixels.sumOf { it and 0xFF } / pixels.size.toFloat()
    val dr = r - color.red * 255
    val dg = g - color.green * 255
    val db = b - color.blue * 255
    return sqrt(dr * dr + dg * dg + db * db) <= NearColour
}

private fun Bitmap.fillsSquare(): Boolean {
    val last = width - 3
    return listOf(2 to 2, last to 2, 2 to last, last to last).all { (x, y) -> getPixel(x, y) ushr 24 >= 0xF0 }
}

// A shader, not a clip: a software canvas doesn't antialias clips.
private fun tile(content: Bitmap): Bitmap {
    val out = createBitmap(content.width, content.height)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(content, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
    val r = content.width * TileCorner
    Canvas(out).drawRoundRect(0f, 0f, content.width.toFloat(), content.height.toFloat(), r, r, paint)
    return out
}
