package com.mkopenga.tvhomescreen

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Positions are fractions of the art's width and height after [backdropInset] is cropped off each side, so the art
 * must be drawn cropped that way.
 */
@Immutable
sealed interface Backdrop {
    data class Solid(val color: Color) : Backdrop

    /**
     * [colors] evenly spaced from [start] to [end], banded perpendicular to that axis in the art's own pixels (so the
     * art must keep its aspect ratio) and holding the end colours past either end. Matches the edges only roughly:
     * covers blend from the art's own edge pixels into it.
     */
    data class Linear(val colors: List<Color>, val start: Offset, val end: Offset) : Backdrop

    /** Edges with detail that can't be carried on (a photo, noise, the logo running off the edge). */
    data class Busy(val dominant: Color) : Backdrop
}

/** Pixels in from each side where the edge is read, past antialiasing and hairline frames; covers crop them off. */
fun backdropInset(sizePx: Int): Int = if (sizePx < 32) 0 else max(1, (sizePx * EdgeInsetShare).roundToInt())

/**
 * [argb] is non-premultiplied and row-major. Null when the edge is mostly transparent: the art has no background of
 * its own.
 */
fun analyseBackdrop(argb: IntArray, width: Int, height: Int): Backdrop? {
    require(width > 0 && height > 0 && argb.size >= width * height) { "argb holds fewer than $width x $height pixels" }
    val edge = Edge.read(argb, width, height) ?: return null
    val median = edge.median()
    if (edge.isFlatAround(median)) return Backdrop.Solid(median.toColor())
    if (!edge.isSmooth()) return Backdrop.Busy(edge.dominant().toColor())
    return edge.ramp() ?: Backdrop.Solid(median.toColor())
}

/**
 * In place, on cropped art. A cover carries a gradient art's edge pixels outward; this keeps dithering, and the
 * corners of shapes crossing the edge, from turning into streaks there. Each pixel is blurred once, along the edge
 * nearest to it.
 */
fun softenEdges(argb: IntArray, width: Int, height: Int) {
    val radius = max(2, (max(width, height) * SoftenShare).roundToInt())
    val source = argb.copyOf()
    for (depth in 0 until SoftenFull + SoftenFade) {
        if (2 * depth >= width || 2 * depth >= height) break
        val weight = if (depth < SoftenFull) 1f else 1f - (depth - SoftenFull + 1f) / (SoftenFade + 1)
        for (y in intArrayOf(depth, height - 1 - depth)) {
            blurLine(source, argb, y * width, 1, width, radius, weight, depth until width - depth)
        }
        for (x in intArrayOf(depth, width - 1 - depth)) {
            blurLine(source, argb, x, width, height, radius, weight, depth + 1 until height - 1 - depth)
        }
    }
}

private const val SoftenShare = 0.025f

/** A cover scales a banner down about twofold, so its edge pixel samples the outermost two or three. */
private const val SoftenFull = 3
private const val SoftenFade = 3

private fun blurLine(source: IntArray, out: IntArray, start: Int, stride: Int, count: Int, radius: Int, weight: Float, write: IntRange) {
    val r = LongArray(count + 1)
    val g = LongArray(count + 1)
    val b = LongArray(count + 1)
    for (i in 0 until count) {
        val p = source[start + i * stride]
        r[i + 1] = r[i] + ((p shr 16) and 0xFF)
        g[i + 1] = g[i] + ((p shr 8) and 0xFF)
        b[i + 1] = b[i] + (p and 0xFF)
    }
    for (i in write) {
        val lo = max(0, i - radius)
        val hi = minOf(count, i + radius + 1)
        val n = (hi - lo).toFloat()
        val p = source[start + i * stride]
        fun mix(sums: LongArray, own: Int) = (own + ((sums[hi] - sums[lo]) / n - own) * weight).roundToInt().coerceIn(0, 255)
        out[start + i * stride] = (p and 0xFF000000.toInt()) or
            (mix(r, (p shr 16) and 0xFF) shl 16) or (mix(g, (p shr 8) and 0xFF) shl 8) or mix(b, p and 0xFF)
    }
}

private const val EdgeInsetShare = 0.01f
private const val MaxSamples = 2048

/** Less opaque edge pixels are see-through or antialiasing; mostly see-through means no background. */
private const val OpaqueAlpha = 0xF0
private const val MinOpaqueShare = 0.6f

// Distances are Euclidean in 0-255 sRGB.
private const val FlatSpread = 4f
private const val FlatOutlier = 24f
private const val MaxOutlierShare = 0.005f

/** A step this large is a shape's edge (a logo meeting the border), not shading. */
private const val HardStep = 96f
private const val MaxHardShare = 0.002f

/** Steps above this are texture; a photo's edge is full of them, a gradient's has none. */
private const val TextureStep = 16f
private const val MaxTextureShare = 0.06f

private const val RampStops = 5

private class Rgb(val r: Float, val g: Float, val b: Float) {
    fun toColor() = Color(r.toChannel(), g.toChannel(), b.toChannel())
}

private fun Float.toChannel(): Int = roundToInt().coerceIn(0, 255)

private fun distance(dr: Float, dg: Float, db: Float) = sqrt(dr * dr + dg * dg + db * db)

private class Edge(
    private val x: FloatArray,
    private val y: FloatArray,
    private val r: FloatArray,
    private val g: FloatArray,
    private val b: FloatArray,
    /** Index around the ring; a gap is where see-through pixels were skipped. */
    private val position: IntArray,
    private val n: Int,
    private val cropWidth: Float,
    private val cropHeight: Float,
) {
    fun median() = Rgb(r.median(n), g.median(n), b.median(n))

    fun isFlatAround(c: Rgb): Boolean {
        val d = FloatArray(n) { distance(r[it] - c.r, g[it] - c.g, b[it] - c.b) }
        return d.percentile(n, 0.95f) <= FlatSpread && d.count(n) { it > FlatOutlier } <= MaxOutlierShare * n
    }

    fun isSmooth(): Boolean {
        var steps = 0
        var hard = 0
        var textured = 0
        for (i in 0 until n - 2) {
            if (position[i + 2] - position[i] != 2) continue
            val step = distance(r[i + 2] - r[i], g[i + 2] - g[i], b[i + 2] - b[i])
            steps++
            if (step > HardStep) hard++
            if (step > TextureStep) textured++
        }
        if (steps == 0) return false
        return hard <= MaxHardShare * steps && textured <= MaxTextureShare * steps
    }

    fun dominant(): Rgb {
        val counts = HashMap<Int, Int>()
        val keys = IntArray(n) { (r[it].toChannel() shr 3 shl 10) or (g[it].toChannel() shr 3 shl 5) or (b[it].toChannel() shr 3) }
        for (k in keys) counts[k] = (counts[k] ?: 0) + 1
        val top = counts.maxBy { it.value }.key
        var sr = 0f
        var sg = 0f
        var sb = 0f
        var m = 0
        for (i in 0 until n) {
            if (keys[i] != top) continue
            sr += r[i]; sg += g[i]; sb += b[i]; m++
        }
        return Rgb(sr / m, sg / m, sb / m)
    }

    fun ramp(): Backdrop.Linear? {
        val inlier = BooleanArray(n) { true }
        var planes = fitPlanes(inlier) ?: return null
        // Refit without the worst-explained samples, so a shape on one side doesn't tilt the planes.
        repeat(2) {
            val residual = FloatArray(n) { planes.residual(it) }
            val cut = max(4f, 2.5f * residual.median(n))
            for (i in 0 until n) inlier[i] = residual[i] <= cut
            planes = fitPlanes(inlier) ?: return null
        }
        // The axis along which the three channels change most together.
        var sxx = 0f
        var sxy = 0f
        var syy = 0f
        for (p in planes.slopes) {
            sxx += p[0] * p[0]; sxy += p[0] * p[1]; syy += p[1] * p[1]
        }
        if (sxx + syy < 1e-8f) return null
        val angle = 0.5f * atan2(2 * sxy, sxx - syy)
        val ux = cos(angle)
        val uy = sin(angle)
        val half = abs(ux) * cropWidth / 2 + abs(uy) * cropHeight / 2
        val stops = fitStops(inlier, ux, uy, half)
        val colors = if (stops.isNearlyStraight()) listOf(stops.first(), stops.last()) else stops
        // Back to fractions of the cropped art, from pixels centred on its middle.
        val start = Offset(0.5f - ux * half / cropWidth, 0.5f - uy * half / cropHeight)
        val end = Offset(0.5f + ux * half / cropWidth, 0.5f + uy * half / cropHeight)
        return Backdrop.Linear(colors.map { it.toColor() }, start, end)
    }

    private class Planes(val offsets: FloatArray, val slopes: Array<FloatArray>)

    private fun Planes.residual(i: Int): Float {
        val e = FloatArray(3) { c -> channel(c)[i] - (offsets[c] + slopes[c][0] * x[i] + slopes[c][1] * y[i]) }
        return distance(e[0], e[1], e[2])
    }

    private fun channel(c: Int) = when (c) {
        0 -> r
        1 -> g
        else -> b
    }

    private fun fitPlanes(inlier: BooleanArray): Planes? {
        val m = Array(3) { DoubleArray(3) }
        val rhs = Array(3) { DoubleArray(3) }
        var count = 0
        for (i in 0 until n) {
            if (!inlier[i]) continue
            count++
            val basis = doubleArrayOf(1.0, x[i].toDouble(), y[i].toDouble())
            for (row in 0..2) {
                for (col in 0..2) m[row][col] += basis[row] * basis[col]
                for (c in 0..2) rhs[c][row] += basis[row] * channel(c)[i]
            }
        }
        if (count < 8) return null
        val solved = Array(3) { c -> solve(m, rhs[c]) ?: return null }
        return Planes(
            offsets = FloatArray(3) { solved[it][0].toFloat() },
            slopes = Array(3) { floatArrayOf(solved[it][1].toFloat(), solved[it][2].toFloat()) },
        )
    }

    private fun fitStops(inlier: BooleanArray, ux: Float, uy: Float, half: Float): List<Rgb> {
        val k = RampStops
        val m = Array(k) { DoubleArray(k) }
        val rhs = Array(3) { DoubleArray(k) }
        var count = 0
        val mean = DoubleArray(3)
        for (i in 0 until n) {
            if (!inlier[i]) continue
            count++
            val s = ((x[i] * ux + y[i] * uy + half) / (2 * half) * (k - 1)).coerceIn(0f, (k - 1).toFloat())
            val j = s.toInt().coerceAtMost(k - 2)
            val f = (s - j).toDouble()
            val w0 = 1 - f
            m[j][j] += w0 * w0; m[j][j + 1] += w0 * f; m[j + 1][j] += w0 * f; m[j + 1][j + 1] += f * f
            for (c in 0..2) {
                val v = channel(c)[i].toDouble()
                rhs[c][j] += w0 * v; rhs[c][j + 1] += f * v
                mean[c] += v
            }
        }
        for (c in 0..2) mean[c] /= count
        // Curvature costs a little, so stops with few samples near them follow their neighbours; the faint pull
        // towards the mean keeps the system solvable when a stop has none at all.
        val bend = 0.001 * count / k
        val ridge = 1e-4 * count / k
        for (j in 1 until k - 1) {
            val d = intArrayOf(j - 1, j, j + 1)
            val w = doubleArrayOf(1.0, -2.0, 1.0)
            for (a in 0..2) for (c in 0..2) m[d[a]][d[c]] += bend * w[a] * w[c]
        }
        for (j in 0 until k) {
            m[j][j] += ridge
            for (c in 0..2) rhs[c][j] += ridge * mean[c]
        }
        val solved = Array(3) { c -> solve(m, rhs[c]) ?: DoubleArray(k) { mean[c] } }
        return List(k) { j -> Rgb(solved[0][j].toFloat().coerceIn(0f, 255f), solved[1][j].toFloat().coerceIn(0f, 255f), solved[2][j].toFloat().coerceIn(0f, 255f)) }
    }

    companion object {
        fun read(argb: IntArray, width: Int, height: Int): Edge? {
            val ix = backdropInset(width)
            val iy = backdropInset(height)
            val left = ix
            val right = width - 1 - ix
            val top = iy
            val bottom = height - 1 - iy
            val across = right - left
            val down = bottom - top
            if (across < 2 || down < 2) return null
            val perimeter = 2 * (across + down)
            val step = max(1, (perimeter + MaxSamples - 1) / MaxSamples)
            val capacity = (perimeter + step - 1) / step
            val xs = FloatArray(capacity)
            val ys = FloatArray(capacity)
            val rs = FloatArray(capacity)
            val gs = FloatArray(capacity)
            val bs = FloatArray(capacity)
            val positions = IntArray(capacity)
            val cx = width / 2f
            val cy = height / 2f
            var n = 0
            for (sample in 0 until capacity) {
                val k = sample * step
                val px: Int
                val py: Int
                when {
                    k < across -> { px = left + k; py = top }
                    k < across + down -> { px = right; py = top + (k - across) }
                    k < 2 * across + down -> { px = right - (k - across - down); py = bottom }
                    else -> { px = left; py = bottom - (k - 2 * across - down) }
                }
                val pixel = argb[py * width + px]
                if (pixel ushr 24 < OpaqueAlpha) continue
                xs[n] = px + 0.5f - cx
                ys[n] = py + 0.5f - cy
                rs[n] = ((pixel shr 16) and 0xFF).toFloat()
                gs[n] = ((pixel shr 8) and 0xFF).toFloat()
                bs[n] = (pixel and 0xFF).toFloat()
                positions[n] = sample
                n++
            }
            if (n < 8 || n < MinOpaqueShare * capacity) return null
            return Edge(xs, ys, rs, gs, bs, positions, n, (width - 2 * ix).toFloat(), (height - 2 * iy).toFloat())
        }
    }
}

private fun List<Rgb>.isNearlyStraight(): Boolean {
    val a = first()
    val z = last()
    return withIndex().all { (j, s) ->
        val t = j.toFloat() / (size - 1)
        distance(s.r - (a.r + (z.r - a.r) * t), s.g - (a.g + (z.g - a.g) * t), s.b - (a.b + (z.b - a.b) * t)) <= 1.5f
    }
}

private fun FloatArray.median(n: Int): Float = percentile(n, 0.5f)

private fun FloatArray.percentile(n: Int, p: Float): Float {
    val sorted = copyOf(n).also { it.sort() }
    return sorted[((n - 1) * p).roundToInt().coerceIn(0, n - 1)]
}

private inline fun FloatArray.count(n: Int, predicate: (Float) -> Boolean): Int {
    var c = 0
    for (i in 0 until n) if (predicate(this[i])) c++
    return c
}

/** Leaves [m] and [rhs] untouched: callers solve the same [m] for each channel. */
private fun solve(m: Array<DoubleArray>, rhs: DoubleArray): DoubleArray? {
    val size = rhs.size
    val a = Array(size) { row -> DoubleArray(size + 1) { col -> if (col < size) m[row][col] else rhs[row] } }
    for (col in 0 until size) {
        val pivot = (col until size).maxBy { abs(a[it][col]) }
        if (abs(a[pivot][col]) < 1e-9) return null
        val swap = a[col]; a[col] = a[pivot]; a[pivot] = swap
        for (row in col + 1 until size) {
            val f = a[row][col] / a[col][col]
            for (c in col..size) a[row][c] -= f * a[col][c]
        }
    }
    val out = DoubleArray(size)
    for (row in size - 1 downTo 0) {
        var s = a[row][size]
        for (c in row + 1 until size) s -= a[row][c] * out[c]
        out[row] = s / a[row][row]
    }
    return out
}
