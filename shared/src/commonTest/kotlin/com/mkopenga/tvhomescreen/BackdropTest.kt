package com.mkopenga.tvhomescreen

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackdropTest {
    private val orange = Color(0xFFFF5500)
    private val purple = Color(0xFF5A2DB0)
    private val navy = Color(0xFF2E2F5A)

    @Test
    fun solidBackgroundAroundALogoIsThatExactColour() {
        val image = Image(320, 180) { _, _ -> orange }.withLogo(Color.White)
        assertEquals(Backdrop.Solid(orange), analyseBackdrop(image.argb, image.width, image.height))
    }

    @Test
    fun verticalGradientIsFittedAlongItsAxis() {
        val image = Image(320, 180) { _, y -> mix(purple, navy, y / 179f) }.withLogo(Color.White)
        val linear = assertIs<Backdrop.Linear>(analyseBackdrop(image.argb, image.width, image.height))
        assertTrue(abs(linear.end.x - linear.start.x) < 0.02f, "axis $linear is not vertical")
        assertMatchesEdges(linear, image)
    }

    @Test
    fun horizontalGradientIsFittedAlongItsAxis() {
        val image = Image(320, 180) { x, _ -> mix(purple, navy, x / 319f) }.withLogo(Color.White)
        val linear = assertIs<Backdrop.Linear>(analyseBackdrop(image.argb, image.width, image.height))
        assertTrue(abs(linear.end.y - linear.start.y) < 0.02f, "axis $linear is not horizontal")
        assertMatchesEdges(linear, image)
    }

    @Test
    fun diagonalGradientIsFittedAlongItsAxis() {
        val image = Image(320, 180) { x, y -> mix(purple, navy, (x / 319f + y / 179f) / 2) }.withLogo(Color.White)
        val linear = assertIs<Backdrop.Linear>(analyseBackdrop(image.argb, image.width, image.height))
        assertMatchesEdges(linear, image)
    }

    @Test
    fun gradientThroughThreeColoursKeepsItsMiddle() {
        val image = Image(320, 180) { x, _ ->
            val t = x / 319f
            if (t < 0.5f) mix(orange, purple, t * 2) else mix(purple, navy, t * 2 - 1)
        }.withLogo(Color.White)
        val linear = assertIs<Backdrop.Linear>(analyseBackdrop(image.argb, image.width, image.height))
        assertTrue(linear.colors.size > 2)
        assertMatchesEdges(linear, image)
    }

    @Test
    fun photoLikeEdgesAreBusy() {
        val random = Random(7)
        val image = Image(320, 180) { _, _ -> Color(random.nextInt(256), random.nextInt(256), random.nextInt(256)) }
        assertIs<Backdrop.Busy>(analyseBackdrop(image.argb, image.width, image.height))
    }

    @Test
    fun logoRunningOffTheEdgeMakesItBusyOnTheBackgroundColour() {
        val image = Image(320, 180) { x, y -> if (x in 140..180 && y < 60) Color.White else orange }
        assertEquals(Backdrop.Busy(orange), analyseBackdrop(image.argb, image.width, image.height))
    }

    @Test
    fun seeThroughBorderHasNoBackdrop() {
        val image = Image(192, 192) { x, y ->
            val dx = x - 95.5f
            val dy = y - 95.5f
            if (sqrt(dx * dx + dy * dy) < 70f) orange else Color.Transparent
        }
        assertNull(analyseBackdrop(image.argb, image.width, image.height))
    }

    @Test
    fun seeThroughRoundedCornersStillReadTheEdges() {
        val image = Image(320, 180) { x, y ->
            val cx = x.coerceIn(16, 303)
            val cy = y.coerceIn(16, 163)
            val dx = (x - cx).toFloat()
            val dy = (y - cy).toFloat()
            if (dx * dx + dy * dy > 16f * 16f) Color.Transparent else orange
        }.withLogo(Color.White)
        assertEquals(Backdrop.Solid(orange), analyseBackdrop(image.argb, image.width, image.height))
    }

    @Test
    fun ditheredSolidIsStillOneColour() {
        val random = Random(3)
        val image = Image(320, 180) { _, _ -> orange.shifted(random.nextInt(-1, 2)) }.withLogo(Color.White)
        val solid = assertIs<Backdrop.Solid>(analyseBackdrop(image.argb, image.width, image.height))
        assertTrue(solid.color.distanceTo(orange) <= 2f)
    }

    @Test
    fun softeningBlursTheOutermostPixelsAlongTheEdgeAndLeavesTheRestAlone() {
        val random = Random(5)
        val image = Image(200, 120) { _, _ -> Color(random.nextInt(256), random.nextInt(256), random.nextInt(256)) }
        val before = image.argb.copyOf()
        softenEdges(image.argb, image.width, image.height)
        for (y in 6 until image.height - 6) for (x in 6 until image.width - 6) {
            assertEquals(before[y * image.width + x], image.argb[y * image.width + x])
        }
        val steps = (1 until image.width).map { image[it, 0].distanceTo(image[it - 1, 0]) }
        val noise = (1 until image.width).map { Color(before[it]).distanceTo(Color(before[it - 1])) }
        assertTrue(steps.max() < noise.average() / 4, "outermost row still steps by up to ${steps.max()}")
    }

    private fun assertMatchesEdges(linear: Backdrop.Linear, image: Image) {
        val ix = backdropInset(image.width)
        val iy = backdropInset(image.height)
        val crop = Rect(0f, 0f, (image.width - 2 * ix).toFloat(), (image.height - 2 * iy).toFloat())
        var worst = 0f
        for ((x, y) in image.edgePixels(ix, iy)) {
            val fitted = linear.colorAt(Offset(x + 0.5f - ix, y + 0.5f - iy), crop)
            worst = maxOf(worst, fitted.distanceTo(image[x, y]))
        }
        assertTrue(worst <= 4f, "fit is $worst off the edge somewhere")
    }
}

private class Image(val width: Int, val height: Int, color: (Int, Int) -> Color) {
    val argb = IntArray(width * height) { color(it % width, it / width).toArgb() }

    operator fun get(x: Int, y: Int) = Color(argb[y * width + x])

    fun withLogo(logo: Color): Image {
        for (y in height / 4 until height * 3 / 4) for (x in width / 4 until width * 3 / 4) argb[y * width + x] = logo.toArgb()
        return this
    }

    fun edgePixels(ix: Int, iy: Int): List<Pair<Int, Int>> {
        val right = width - 1 - ix
        val bottom = height - 1 - iy
        return (ix..right).flatMap { listOf(it to iy, it to bottom) } + (iy..bottom).flatMap { listOf(ix to it, right to it) }
    }
}

private fun mix(a: Color, b: Color, t: Float) = srgbLerp(a, b, t)

private fun Color.shifted(by: Int) = Color(
    ((red * 255).roundToInt() + by).coerceIn(0, 255),
    ((green * 255).roundToInt() + by).coerceIn(0, 255),
    ((blue * 255).roundToInt() + by).coerceIn(0, 255),
)

private fun Color.distanceTo(o: Color): Float {
    val dr = (red - o.red) * 255
    val dg = (green - o.green) * 255
    val db = (blue - o.blue) * 255
    return sqrt(dr * dr + dg * dg + db * db)
}
