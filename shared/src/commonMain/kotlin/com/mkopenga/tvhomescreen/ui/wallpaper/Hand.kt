package com.mkopenga.tvhomescreen.ui.wallpaper

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** The soft warm brown every motif is outlined in. */
internal val Ink = Color(0xFF5E3D2B)

internal const val Line = 2.4f

/** Fills sit a hair off their outlines, like a print whose colour plates didn't quite line up. */
private val Misprint = Offset(1.1f, 0.9f)

private const val Tau = 2 * PI.toFloat()

/**
 * A seeded hand: the shapes it draws come out a little irregular, like a pattern painted with a brush rather than
 * plotted, and the same seed always draws exactly the same shapes. That matters because a motif that wraps over a
 * tile edge is drawn twice and both halves must meet.
 */
internal class Hand(seed: Int) {
    private val random = Random(seed)

    fun between(from: Float, to: Float): Float = from + random.nextFloat() * (to - from)

    fun jitter(amount: Float): Float = between(-amount, amount)

    fun chance(p: Float): Boolean = random.nextFloat() < p

    fun int(until: Int): Int = random.nextInt(until)

    fun <T> pick(options: List<T>): T = options[random.nextInt(options.size)]

    /** A closed, slightly lumpy ellipse; [wobble] is how far the radius drifts, as a fraction. */
    fun ellipse(center: Offset, rx: Float, ry: Float = rx, degrees: Float = 0f, wobble: Float = 0.05f, points: Int = 10): Path {
        val p1 = between(0f, Tau)
        val p2 = between(0f, Tau)
        val turn = degrees * Tau / 360f
        val pts = List(points) { i ->
            val t = i * Tau / points
            val r = 1f + wobble * (0.6f * sin(2 * t + p1) + 0.4f * sin(3 * t + p2)) + jitter(wobble * 0.35f)
            center + rotated(Offset(rx * r * cos(t), ry * r * sin(t)), turn)
        }
        return smoothClosed(pts)
    }

    fun circle(center: Offset, r: Float, wobble: Float = 0.05f): Path =
        ellipse(center, r, r, wobble = wobble, points = if (r < 6f) 7 else if (r < 16f) 9 else 12)

    /**
     * A leaf from [base] towards [degrees] (0 = right, 90 = down): two bulging edges meeting at a slightly
     * off-centre tip.
     */
    fun leaf(base: Offset, degrees: Float, length: Float, width: Float): Path {
        val turn = degrees * Tau / 360f
        val half = width / 2
        val a = 1f + jitter(0.15f)
        val b = 1f + jitter(0.15f)
        val tipY = jitter(width * 0.12f)
        fun p(x: Float, y: Float) = base + rotated(Offset(x, y), turn)
        return Path().apply {
            val o = p(0f, 0f)
            moveTo(o.x, o.y)
            cubic(p(length * 0.22f, -half * 1.3f * a), p(length * 0.7f, -half * 0.95f * a), p(length, tipY))
            cubic(p(length * 0.7f, half * 0.95f * b), p(length * 0.22f, half * 1.3f * b), o)
            close()
        }
    }

    /** The vein down a [leaf] drawn with the same arguments. */
    fun midrib(base: Offset, degrees: Float, length: Float, width: Float): Path {
        val turn = degrees * Tau / 360f
        fun p(x: Float, y: Float) = base + rotated(Offset(x, y), turn)
        return smoothOpen(listOf(p(length * 0.08f, 0f), p(length * 0.45f, jitter(width * 0.08f)), p(length * 0.78f, 0f)))
    }

    /** A petal from [base], round at the far end, pointed at the base. */
    fun petal(base: Offset, degrees: Float, length: Float, width: Float): Path {
        val turn = degrees * Tau / 360f
        val half = width / 2 * (1f + jitter(0.12f))
        val l = length * (1f + jitter(0.08f))
        fun p(x: Float, y: Float) = base + rotated(Offset(x, y), turn)
        return Path().apply {
            val o = p(0f, 0f)
            moveTo(o.x, o.y)
            cubic(p(l * 0.15f, -half * 0.9f), p(l * 0.75f, -half * 1.25f), p(l, jitter(half * 0.2f)))
            cubic(p(l * 0.75f, half * 1.25f), p(l * 0.15f, half * 0.9f), o)
            close()
        }
    }

    /** [points] with every interior one nudged by up to [amount], for strokes that shouldn't look ruled. */
    fun shaky(points: List<Offset>, amount: Float): List<Offset> =
        points.mapIndexed { i, o -> if (i == 0 || i == points.lastIndex) o else o + Offset(jitter(amount), jitter(amount)) }
}

internal fun rotated(o: Offset, radians: Float): Offset {
    if (radians == 0f) return o
    val c = cos(radians)
    val s = sin(radians)
    return Offset(o.x * c - o.y * s, o.x * s + o.y * c)
}

internal fun polar(radius: Float, degrees: Float): Offset {
    val t = degrees * Tau / 360f
    return Offset(radius * cos(t), radius * sin(t))
}

private fun Path.cubic(c1: Offset, c2: Offset, to: Offset) = cubicTo(c1.x, c1.y, c2.x, c2.y, to.x, to.y)

/** A smooth closed curve through [points] (Catmull-Rom as cubic Béziers). */
internal fun smoothClosed(points: List<Offset>): Path {
    val n = points.size
    val path = Path()
    path.moveTo(points[0].x, points[0].y)
    for (i in 0 until n) {
        val p0 = points[(i - 1 + n) % n]
        val p1 = points[i]
        val p2 = points[(i + 1) % n]
        val p3 = points[(i + 2) % n]
        path.cubic(p1 + (p2 - p0) / 6f, p2 - (p3 - p1) / 6f, p2)
    }
    path.close()
    return path
}

/** A smooth open curve through [points]. */
internal fun smoothOpen(points: List<Offset>): Path {
    val n = points.size
    val path = Path()
    path.moveTo(points[0].x, points[0].y)
    if (n == 2) {
        path.lineTo(points[1].x, points[1].y)
        return path
    }
    for (i in 0 until n - 1) {
        val p0 = points[(i - 1).coerceAtLeast(0)]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points[(i + 2).coerceAtMost(n - 1)]
        path.cubic(p1 + (p2 - p0) / 6f, p2 - (p3 - p1) / 6f, p2)
    }
    return path
}

internal fun inkStroke(width: Float = Line) = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round)

/** Fills [path] and outlines it in [ink], the fill slightly misregistered. */
internal fun DrawScope.inked(path: Path, fill: Color, line: Float = Line, ink: Color = Ink) {
    translate(Misprint.x, Misprint.y) { drawPath(path, fill) }
    drawPath(path, ink, style = inkStroke(line))
}

internal fun DrawScope.line(path: Path, color: Color = Ink, width: Float = Line) {
    drawPath(path, color, style = inkStroke(width))
}

/** A thick coloured stroke with an ink edge on both sides: stems, handles, strings. */
internal fun DrawScope.band(path: Path, fill: Color, width: Float, line: Float = Line * 0.8f, ink: Color = Ink) {
    drawPath(path, ink, style = inkStroke(width + 2 * line))
    drawPath(path, fill, style = inkStroke(width))
}
