package com.mkopenga.tvhomescreen.ui.settings

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A spur gear centred on the origin, with its axle hole left open (even-odd), so whatever is behind shows through. */
internal fun gearPath(teeth: Int, outer: Float, inner: Float, hole: Float): Path {
    val path = Path()
    val step = 2 * PI.toFloat() / teeth
    fun point(r: Float, a: Float, first: Boolean = false) {
        if (first) path.moveTo(r * cos(a), r * sin(a)) else path.lineTo(r * cos(a), r * sin(a))
    }
    for (k in 0 until teeth) {
        val a = k * step
        point(inner, a - step * 0.28f, first = k == 0)
        point(outer, a - step * 0.16f)
        point(outer, a + step * 0.16f)
        point(inner, a + step * 0.28f)
        for (j in 1..3) point(inner, a + step * (0.28f + 0.44f * j / 4))
    }
    path.close()
    path.addOval(Rect(-hole, -hole, hole, hole))
    path.fillType = PathFillType.EvenOdd
    return path
}

/** An eye, open or struck through, [width] px wide and centred on the origin. Build once per size. */
internal class EyeIcon(width: Float, private val strokeWidth: Float) {
    private val half = width / 2
    private val lid = Path().apply {
        moveTo(-half, 0f)
        quadraticTo(0f, -half * 1.1f, half, 0f)
        quadraticTo(0f, half * 1.1f, -half, 0f)
        close()
    }
    private val stroke = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val pupil = width * 0.15f

    /** [behind] is what the eye is drawn on; the slash cuts through the eye in that colour so the two stay apart. */
    fun DrawScope.drawEye(center: Offset, color: Color, crossed: Boolean, behind: Color) {
        translate(center.x, center.y) {
            drawPath(lid, color, style = stroke)
            drawCircle(color, pupil, Offset.Zero)
            if (crossed) {
                val from = Offset(-half * 0.78f, -half * 0.78f)
                val to = Offset(half * 0.78f, half * 0.78f)
                drawLine(behind, from, to, strokeWidth * 2.8f, StrokeCap.Round)
                drawLine(color, from, to, strokeWidth, StrokeCap.Round)
            }
        }
    }
}

/** A brass nail head, lit from the top left. [u] is px per design px. */
internal fun DrawScope.drawNail(at: Offset, u: Float) {
    drawCircle(WallColors.BrassShade, 7.5f * u, at)
    drawCircle(WallColors.Brass, 5.5f * u, at - Offset(u, u))
}

internal fun DrawScope.drawCord(from: Offset, to: Offset, u: Float) {
    drawLine(WallColors.Cord, from, to, 2.5f * u, StrokeCap.Round)
}
