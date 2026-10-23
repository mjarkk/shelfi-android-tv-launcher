package com.mkopenga.tvhomescreen.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.mkopenga.tvhomescreen.ShelfItem
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Flat-shape cover art in the mockup's style, for items without artwork. Built once per item into a list of
 * primitives in 400x560 cover coordinates, so drawing allocates nothing.
 *
 * A motif is picked from the title when a word suggests one (so every mockup game gets its own), otherwise from
 * the id's hash. Colours come from the item; random details are seeded by the id, so a cover never changes.
 */
internal class CoverArt private constructor(private val ops: Array<CoverOp>) {
    fun draw(scope: DrawScope) {
        for (op in ops) op.paint(scope)
    }

    companion object {
        fun of(item: ShelfItem): CoverArt {
            val motif = Motif.forTitle(item.title) ?: Motif.entries[stableHash(item.id).mod(Motif.entries.size)]
            val builder = CoverBuilder(item, Random(stableHash(item.id)))
            motif.build(builder)
            return CoverArt(builder.ops.toTypedArray())
        }
    }
}

private enum class Motif(vararg val keywords: String) {
    Forest("wood", "forest", "fern", "pine", "tree", "grove", "jungle"),
    Harbour("harbour", "harbor", "sea", "ocean", "boat", "sail", "fish", "island", "lake"),
    Sun("sun", "sundial", "summer", "dawn", "desert", "solar"),
    Map("cartograph", "map", "atlas", "compass", "explor", "journey", "travel"),
    Music("orchestra", "music", "song", "rhythm", "band", "beat", "piano", "radio", "sound", "audio"),
    Race("prix", "race", "racing", "rally", "kart", "speed", "motor"),
    Kite("kite", "sky", "wind", "cloud", "bird", "fly", "flight"),
    Flower("bloom", "flower", "garden", "petal", "rose"),
    Bricks("brick", "build", "block", "craft", "tower", "wall"),
    Lantern("lantern", "moth", "night", "lamp", "candle", "star", "moon", "glow"),
    Castle("kingdom", "castle", "king", "queen", "empire", "realm", "crown", "throne"),
    Signal("static", "signal", "tv", "video", "stream", "glitch", "channel", "screen", "broadcast"),
    Stealth("lowlight", "shadow", "stealth", "dark", "eye", "spy", "ghost", "hide"),
    Shield("iron", "saint", "knight", "shield", "war", "battle", "sword", "legion", "guard");

    fun build(b: CoverBuilder) = when (this) {
        Forest -> b.forest()
        Harbour -> b.harbour()
        Sun -> b.sun()
        Map -> b.map()
        Music -> b.music()
        Race -> b.race()
        Kite -> b.kite()
        Flower -> b.flower()
        Bricks -> b.bricks()
        Lantern -> b.lantern()
        Castle -> b.castle()
        Signal -> b.signal()
        Stealth -> b.stealth()
        Shield -> b.shield()
    }

    companion object {
        // Keywords match word prefixes ("kingdom" in "Kingdoms"), except short ones, which must be the whole word
        // so "sea" doesn't claim "Season".
        fun forTitle(title: String): Motif? {
            val words = title.lowercase().split(' ', '-', ':', '.', '&')
            return entries.firstOrNull { motif ->
                words.any { word -> motif.keywords.any { if (it.length <= 3) word == it else word.startsWith(it) } }
            }
        }
    }
}

private class CoverBuilder(item: ShelfItem, val rng: Random) {
    val ops = ArrayList<CoverOp>()
    val spine = item.spineColor
    val ink = item.inkColor
    val mark = item.markColor

    fun tone(t: Float): Color = lerp(spine, ink, t)
    fun shade(t: Float): Color = lerp(spine, Color.Black, t)
    fun light(t: Float): Color = lerp(spine, Color.White, t)

    fun rect(x: Float, y: Float, w: Float, h: Float, color: Color, radius: Float = 0f) {
        ops += CoverOp.Rect(x, y, w, h, color, radius)
    }

    fun circle(cx: Float, cy: Float, r: Float, color: Color) {
        ops += CoverOp.Circle(cx, cy, r, color, null)
    }

    fun ring(cx: Float, cy: Float, r: Float, color: Color, width: Float) {
        ops += CoverOp.Circle(cx, cy, r, color, Stroke(width))
    }

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Color, width: Float, dash: Float = 0f) {
        val effect = if (dash > 0f) PathEffect.dashPathEffect(floatArrayOf(dash, dash * 0.8f)) else null
        ops += CoverOp.Line(x1, y1, x2, y2, color, width, effect)
    }

    fun poly(color: Color, vararg xy: Float) {
        ops += CoverOp.Shape(polygon(*xy), color, null)
    }

    fun path(color: Color, stroke: Float = 0f, dash: Float = 0f, build: Path.() -> Unit) {
        val style = when {
            stroke <= 0f -> null
            dash > 0f -> Stroke(stroke, cap = StrokeCap.Butt, join = StrokeJoin.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash * 0.7f)))
            else -> Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        }
        ops += CoverOp.Shape(Path().apply(build), color, style)
    }

    fun roundRect(x: Float, y: Float, w: Float, h: Float, color: Color, top: Float, bottom: Float) = path(color) {
        addRoundRect(
            RoundRect(
                left = x, top = y, right = x + w, bottom = y + h,
                topLeftCornerRadius = CornerRadius(top), topRightCornerRadius = CornerRadius(top),
                bottomRightCornerRadius = CornerRadius(bottom), bottomLeftCornerRadius = CornerRadius(bottom),
            ),
        )
    }
}

private fun polygon(vararg xy: Float) = Path().apply {
    moveTo(xy[0], xy[1])
    for (i in 2 until xy.size step 2) lineTo(xy[i], xy[i + 1])
    close()
}

/** Exactly the mockup's cover for Fern and the Deep Wood: the wood's greens and the moon are the motif's own. */
private fun CoverBuilder.forest() {
    rect(0f, 300f, 400f, 260f, Color(0xFF2E5E3E))
    poly(Color(0xFF1E4430), 40f, 420f, 180f, 420f, 110f, 200f)
    poly(Color(0xFF183A28), 200f, 400f, 380f, 400f, 290f, 120f)
    circle(305f, 105f, 45f, Color(0xFFF7E7B4))
    roundRect(180f, 430f, 40f, 70f, spine, top = 20f, bottom = 4f)
}

private fun CoverBuilder.harbour() {
    circle(300f, 300f, 52f, light(0.55f))
    val bands = floatArrayOf(330f, 400f, 470f)
    for ((i, top) in bands.withIndex()) {
        val phase = rng.nextFloat() * 40f
        path(tone(0.22f + 0.17f * i)) {
            moveTo(0f, top)
            var x = -phase
            while (x < 400f) {
                quadraticTo(x + 20f, top - 9f, x + 40f, top)
                quadraticTo(x + 60f, top + 9f, x + 80f, top)
                x += 80f
            }
            lineTo(400f, 560f)
            lineTo(0f, 560f)
            close()
        }
        if (i == 0) {
            poly(ink, 110f, 322f, 206f, 322f, 192f, 342f, 124f, 342f)
            line(158f, 232f, 158f, 322f, ink, 4f)
            poly(light(0.7f), 162f, 236f, 162f, 316f, 206f, 316f)
            poly(tone(0.4f), 154f, 250f, 154f, 316f, 118f, 316f)
        }
    }
}

private fun CoverBuilder.sun() {
    rect(0f, 430f, 400f, 130f, shade(0.22f))
    path(light(0.22f)) { addOval(Rect(50f, 384f, 350f, 476f)) }
    for (h in 0..10) {
        val a = PI.toFloat() * (1f + h / 10f)
        line(200f + cos(a) * 128f, 430f + sin(a) * 38f, 200f + cos(a) * 142f, 430f + sin(a) * 42f, ink.copy(alpha = 0.7f), 4f)
    }
    poly(shade(0.35f), 200f, 430f, 96f, 452f, 104f, 438f)
    poly(ink, 196f, 430f, 196f, 300f, 270f, 430f)
    circle(304f, 214f, 50f, ink)
    for (r in 0 until 8) {
        val a = 2 * PI.toFloat() * r / 8 + 0.2f
        line(304f + cos(a) * 66f, 214f + sin(a) * 66f, 304f + cos(a) * 84f, 214f + sin(a) * 84f, ink, 6f)
    }
}

private fun CoverBuilder.map() {
    val grid = ink.copy(alpha = 0.1f)
    for (x in 40..360 step 40) line(x.toFloat(), 190f, x.toFloat(), 560f, grid, 2f)
    for (y in 200..560 step 40) line(0f, y.toFloat(), 400f, y.toFloat(), grid, 2f)
    poly(tone(0.14f), 20f, 260f, 120f, 230f, 170f, 290f, 150f, 360f, 60f, 380f, 30f, 330f)
    poly(tone(0.14f), 210f, 300f, 330f, 280f, 380f, 360f, 350f, 470f, 250f, 500f, 200f, 420f)
    val contour = ink.copy(alpha = 0.35f)
    for (r in 1..3) ring(285f, 385f, 22f + r * 26f, contour, 3f)
    val jitter = rng.nextFloat() * 30f
    path(mark, stroke = 5f, dash = 16f) {
        moveTo(70f, 520f)
        cubicTo(120f, 430f + jitter, 170f, 520f - jitter, 210f, 450f)
        quadraticTo(240f, 400f, 285f, 385f)
    }
    line(273f, 373f, 297f, 397f, mark, 7f)
    line(297f, 373f, 273f, 397f, mark, 7f)
    poly(ink, 340f, 46f, 352f, 80f, 328f, 80f)
    poly(tone(0.45f), 328f, 80f, 352f, 80f, 340f, 114f)
}

private fun CoverBuilder.music() {
    for (i in 0 until 8) {
        val h = 70f + rng.nextFloat() * 190f
        rect(40f + i * 42f, 500f - h, 26f, h, tone(if (i % 2 == 0) 0.28f else 0.5f), radius = 13f)
    }
    rect(32f, 510f, 336f, 6f, ink, radius = 3f)
    circle(284f, 190f, 15f, ink)
    circle(334f, 176f, 15f, ink)
    line(297f, 190f, 297f, 110f, ink, 6f)
    line(347f, 176f, 347f, 96f, ink, 6f)
    poly(ink, 294f, 104f, 350f, 90f, 350f, 108f, 294f, 122f)
}

private fun CoverBuilder.race() {
    val stripe = floatArrayOf(380f, 438f, 496f)
    val colors = arrayOf(ink, tone(0.45f), ink)
    for (i in stripe.indices) {
        val y = stripe[i]
        poly(colors[i], 0f, y, 0f, y + 38f, 400f, y + 38f - 250f, 400f, y - 250f)
    }
    for (row in 0..1) for (col in 0 until 20) {
        if ((row + col) % 2 == 0) rect(col * 20f, 516f + row * 20f, 20f, 20f, ink)
    }
    ring(300f, 190f, 40f, shade(0.6f), 18f)
    circle(300f, 190f, 11f, ink)
}

private fun CoverBuilder.kite() {
    circle(130f, 820f, 340f, tone(0.4f))
    circle(390f, 850f, 320f, tone(0.6f))
    val cloud = light(0.6f)
    for ((cx, cy) in listOf(80f to 262f, 300f to 412f)) {
        circle(cx - 22f, cy + 8f, 26f, cloud)
        circle(cx + 6f, cy - 6f, 34f, cloud)
        circle(cx + 38f, cy + 10f, 24f, cloud)
        rect(cx - 48f, cy + 6f, 110f, 28f, cloud, radius = 14f)
    }
    path(ink, stroke = 3f) {
        moveTo(280f, 330f)
        cubicTo(230f, 400f, 330f, 450f, 260f, 540f)
    }
    for ((x, y) in listOf(262f to 380f, 292f to 452f)) poly(ink, x - 12f, y - 8f, x + 12f, y + 8f, x + 12f, y - 8f, x - 12f, y + 8f)
    poly(ink, 280f, 140f, 330f, 214f, 280f, 214f)
    poly(tone(0.55f), 280f, 140f, 280f, 214f, 230f, 214f)
    poly(tone(0.55f), 330f, 214f, 280f, 330f, 280f, 214f)
    poly(ink, 230f, 214f, 280f, 214f, 280f, 330f)
    line(280f, 140f, 280f, 330f, light(0.5f), 3f)
    line(230f, 214f, 330f, 214f, light(0.5f), 3f)
}

private fun CoverBuilder.flower() {
    circle(84f, 470f, 14f, tone(0.3f))
    circle(336f, 420f, 10f, tone(0.3f))
    path(ink, stroke = 8f) {
        moveTo(200f, 560f)
        quadraticTo(190f, 450f, 210f, 360f)
    }
    path(tone(0.55f)) {
        moveTo(202f, 474f)
        quadraticTo(232f, 410f, 290f, 430f)
        quadraticTo(262f, 494f, 202f, 474f)
    }
    for (p in 0 until 6) {
        val a = 2 * PI.toFloat() * p / 6 - PI.toFloat() / 2
        circle(210f + cos(a) * 52f, 330f + sin(a) * 52f, 44f, tone(if (p % 2 == 0) 0.16f else 0.28f))
    }
    circle(210f, 330f, 30f, ink)
    circle(210f, 330f, 10f, spine)
}

private fun CoverBuilder.bricks() {
    val rowHeight = 40f
    for (row in 0 until 6) {
        val y = 560f - (row + 1) * rowHeight + 6f
        val limit = when (row) {
            4 -> 250f
            5 -> 130f
            else -> 400f
        }
        var x = if (row % 2 == 0) -38f else 0f
        while (x < limit) {
            val w = minOf(76f, limit - x)
            rect(x, y, w, 34f, tone(0.1f + rng.nextFloat() * 0.18f), radius = 3f)
            x += 82f
        }
    }
    rect(258f, 300f, 76f, 34f, shade(0.25f), radius = 3f)
    rect(262f, 262f, 76f, 34f, ink, radius = 3f)
}

private fun CoverBuilder.lantern() {
    circle(200f, 360f, 170f, tone(0.07f))
    circle(200f, 360f, 125f, tone(0.13f))
    circle(200f, 360f, 85f, tone(0.22f))
    line(200f, 160f, 200f, 278f, ink.copy(alpha = 0.7f), 3f)
    ring(200f, 290f, 14f, ink, 4f)
    rect(170f, 300f, 60f, 14f, ink, radius = 4f)
    rect(178f, 314f, 44f, 72f, ink, radius = 10f)
    rect(170f, 386f, 60f, 12f, ink, radius = 4f)
    path(tone(0.5f)) {
        moveTo(200f, 330f)
        quadraticTo(214f, 352f, 200f, 366f)
        quadraticTo(186f, 352f, 200f, 330f)
    }
    for ((x, y, s) in listOf(Triple(300f, 236f, 1f), Triple(112f, 300f, 0.7f))) {
        poly(ink, x, y, x - 30f * s, y - 20f * s, x - 22f * s, y + 14f * s)
        poly(ink, x, y, x + 30f * s, y - 20f * s, x + 22f * s, y + 14f * s)
    }
}

private fun CoverBuilder.castle() {
    circle(200f, 900f, 440f, tone(0.12f))
    rect(110f, 360f, 180f, 110f, ink)
    for (i in 0 until 6) rect(116f + i * 30f, 342f, 18f, 20f, ink)
    rect(80f, 300f, 50f, 170f, ink)
    rect(270f, 300f, 50f, 170f, ink)
    poly(mark, 72f, 302f, 138f, 302f, 105f, 236f)
    poly(mark, 262f, 302f, 328f, 302f, 295f, 236f)
    line(105f, 238f, 105f, 200f, ink, 3f)
    line(295f, 238f, 295f, 200f, ink, 3f)
    poly(mark, 106f, 200f, 132f, 208f, 106f, 216f)
    poly(mark, 296f, 200f, 322f, 208f, 296f, 216f)
    roundRect(176f, 410f, 48f, 60f, spine, top = 24f, bottom = 0f)
    rect(98f, 330f, 14f, 22f, spine, radius = 7f)
    rect(288f, 330f, 14f, 22f, spine, radius = 7f)
}

private fun CoverBuilder.signal() {
    line(200f, 214f, 150f, 150f, ink, 5f)
    line(200f, 214f, 262f, 140f, ink, 5f)
    circle(150f, 150f, 8f, mark)
    circle(262f, 140f, 8f, mark)
    rect(48f, 210f, 304f, 230f, shade(0.35f), radius = 24f)
    val palette = arrayOf(mark, ink, tone(0.4f), mark.copy(alpha = 0.55f))
    for (row in 0 until 11) {
        val y = 232f + row * 18f
        var x = 70f + rng.nextFloat() * 30f
        while (x < 320f) {
            val w = (18f + rng.nextFloat() * 70f).coerceAtMost(330f - x)
            rect(x, y, w, 7f, palette[rng.nextInt(palette.size)])
            x += w + 10f + rng.nextFloat() * 30f
        }
    }
    rect(160f, 448f, 80f, 12f, ink, radius = 6f)
    path(mark, stroke = 5f) {
        moveTo(40f, 510f)
        var x = 40f
        while (x < 360f) {
            quadraticTo(x + 10f, 490f, x + 20f, 510f)
            quadraticTo(x + 30f, 530f, x + 40f, 510f)
            x += 40f
        }
    }
}

private fun CoverBuilder.stealth() {
    poly(tone(0.08f), 262f, 0f, 318f, 0f, 400f, 360f, 400f, 560f, 150f, 560f)
    for (r in 1..3) ring(200f, 400f, 40f + r * 38f, ink.copy(alpha = 0.32f - r * 0.07f), 3f)
    path(ink) {
        moveTo(130f, 400f)
        quadraticTo(200f, 340f, 270f, 400f)
        quadraticTo(200f, 460f, 130f, 400f)
    }
    circle(200f, 400f, 22f, spine)
    circle(208f, 392f, 6f, ink)
}

private fun CoverBuilder.shield() {
    ring(200f, 232f, 52f, ink, 6f)
    path(shade(0.3f)) {
        moveTo(110f, 290f)
        lineTo(290f, 290f)
        lineTo(290f, 400f)
        quadraticTo(290f, 480f, 200f, 524f)
        quadraticTo(110f, 480f, 110f, 400f)
        close()
    }
    rect(188f, 310f, 24f, 180f, ink, radius = 3f)
    rect(140f, 366f, 120f, 24f, ink, radius = 3f)
    for ((x, y) in listOf(126f to 306f, 274f to 306f)) circle(x, y, 5f, tone(0.4f))
}

private sealed class CoverOp {
    abstract fun paint(scope: DrawScope)

    class Rect(val x: Float, val y: Float, val w: Float, val h: Float, val color: Color, val radius: Float) : CoverOp() {
        override fun paint(scope: DrawScope) {
            if (radius > 0f) scope.drawRoundRect(color, Offset(x, y), Size(w, h), CornerRadius(radius))
            else scope.drawRect(color, Offset(x, y), Size(w, h))
        }
    }

    class Circle(val cx: Float, val cy: Float, val r: Float, val color: Color, val stroke: Stroke?) : CoverOp() {
        override fun paint(scope: DrawScope) {
            if (stroke != null) scope.drawCircle(color, r, Offset(cx, cy), style = stroke)
            else scope.drawCircle(color, r, Offset(cx, cy))
        }
    }

    class Line(
        val x1: Float, val y1: Float, val x2: Float, val y2: Float,
        val color: Color, val width: Float, val effect: PathEffect?,
    ) : CoverOp() {
        override fun paint(scope: DrawScope) {
            scope.drawLine(color, Offset(x1, y1), Offset(x2, y2), width, StrokeCap.Round, effect)
        }
    }

    class Shape(val path: Path, val color: Color, val stroke: Stroke?) : CoverOp() {
        override fun paint(scope: DrawScope) {
            if (stroke != null) scope.drawPath(path, color, style = stroke) else scope.drawPath(path, color)
        }
    }
}
