package com.mkopenga.tvhomescreen.ui.wallpaper

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp

/** The warm, slightly faded colours every wallpaper is painted from. */
internal object Tone {
    val Ivory = Color(0xFFF6EBD5)
    val Cream = Color(0xFFF1E3C6)
    val Paper = Color(0xFFFFF8EA)
    val Butter = Color(0xFFF1D58A)
    val Oat = Color(0xFFE6D5B6)
    val Blush = Color(0xFFF0D2C3)
    val Rose = Color(0xFFDC9590)
    val RoseDeep = Color(0xFFB55E5A)
    val Terracotta = Color(0xFFC9673F)
    val Clay = Color(0xFFDE936D)
    val Mustard = Color(0xFFDDA23E)
    val Sage = Color(0xFF97AC80)
    val SageDeep = Color(0xFF6E8A5E)
    val SageLight = Color(0xFFC6D1AC)
    val Cherry = Color(0xFFB83C35)
    val Tea = Color(0xFFA9714A)
    val Strawberry = Color(0xFFD24A3C)
    val Leaf = Color(0xFF7FA05A)
    val Lemon = Color(0xFFF2CF4C)
    val LemonShade = Color(0xFFDDB03A)
    val Ginger = Color(0xFFDD8C4F)
    val Grey = Color(0xFFA59C93)
    val Soot = Color(0xFF5E4C43)
    val Pink = Color(0xFFE9A3A0)
}

internal fun DrawScope.leaf(hand: Hand, base: Offset, degrees: Float, length: Float, width: Float, fill: Color = Tone.Sage) {
    inked(hand.leaf(base, degrees, length, width), fill)
    line(hand.midrib(base, degrees, length, width), Ink.copy(alpha = 0.55f), Line * 0.65f)
}

/** A cabbage rose: scalloped outer petals, a paler cup, and the spiral every child draws a rose with. */
internal fun DrawScope.rose(hand: Hand, r: Float, body: Color = Tone.Rose, deep: Color = Tone.RoseDeep) {
    val start = hand.between(0f, 60f)
    for (i in 0 until 6) {
        val a = start + i * 60f + hand.jitter(9f)
        inked(hand.circle(polar(r * 0.52f, a), r * 0.5f, 0.09f), body)
    }
    val cup = lerp(body, Tone.Paper, 0.28f)
    inked(hand.circle(Offset(hand.jitter(r * 0.05f), hand.jitter(r * 0.05f)), r * 0.62f, 0.07f), cup)
    val a0 = hand.between(0f, 360f)
    val spiral = List(9) { i ->
        val t = i / 8f
        polar(r * (0.5f - 0.42f * t), a0 + t * 520f) + Offset(hand.jitter(r * 0.03f), hand.jitter(r * 0.03f))
    }
    line(smoothOpen(spiral), deep, Line * 0.95f)
    for (k in 0 until 3) {
        val a = a0 + 100f + k * 115f + hand.jitter(15f)
        val arc = listOf(polar(r * 0.78f, a - 22f), polar(r * 0.88f, a), polar(r * 0.78f, a + 22f))
        line(smoothOpen(arc), deep.copy(alpha = 0.75f), Line * 0.8f)
    }
}

/** A daisy with [r] the petal tips' radius. */
internal fun DrawScope.daisy(hand: Hand, r: Float, petals: Color = Tone.Paper, heart: Color = Tone.Mustard) {
    val n = 9 + hand.int(3)
    val start = hand.between(0f, 360f)
    for (i in 0 until n) {
        val a = start + i * 360f / n + hand.jitter(6f)
        inked(hand.petal(polar(r * 0.16f, a), a, r * 0.86f, r * 0.38f), petals, Line * 0.8f)
    }
    inked(hand.circle(Offset.Zero, r * 0.3f, 0.08f), heart, Line * 0.85f)
    val shade = lerp(heart, Ink, 0.35f)
    repeat(3) {
        drawCircle(shade, r * 0.045f, polar(r * hand.between(0.06f, 0.17f), hand.between(0f, 360f)))
    }
}

/** A little five-petalled flower, forget-me-not style. */
internal fun DrawScope.blossom(hand: Hand, r: Float, petals: Color = Tone.Paper, heart: Color = Tone.Mustard) {
    val start = hand.between(0f, 72f)
    for (i in 0 until 5) {
        inked(hand.circle(polar(r * 0.5f, start + i * 72f + hand.jitter(6f)), r * 0.42f, 0.06f), petals, Line * 0.7f)
    }
    inked(hand.circle(Offset.Zero, r * 0.24f, 0.05f), heart, Line * 0.6f)
}

/** Two small leaves on a short curved stem, for filling the gaps in a ditsy print. */
internal fun DrawScope.sprig(hand: Hand, size: Float, degrees: Float, fill: Color = Tone.Sage) {
    val stem = listOf(polar(size * 0.5f, degrees + 180f), polar(size * 0.06f, degrees + 90f + hand.jitter(20f)), polar(size * 0.36f, degrees))
    line(smoothOpen(stem), Ink, Line * 0.75f)
    leaf(hand, stem[1], degrees - 60f + hand.jitter(10f), size * 0.5f, size * 0.3f, fill)
    leaf(hand, stem[1], degrees + 60f + hand.jitter(10f), size * 0.46f, size * 0.28f, fill)
    leaf(hand, stem[2], degrees + hand.jitter(12f), size * 0.5f, size * 0.32f, fill)
}

/**
 * A teacup on its saucer, [w] wide across the rim, rim at y = -0.26w and saucer around y = 0.31w.
 * [decor] patterns the cup's body: dots, a band with a heart, or a little flower.
 */
internal fun DrawScope.teacup(hand: Hand, w: Float, cup: Color, saucer: Color, decor: Color, style: Int) {
    val h = w * 0.62f
    val rimY = -h * 0.42f
    val footY = h * 0.42f
    fun j() = hand.jitter(w * 0.012f)

    inked(hand.ellipse(Offset(0f, footY + h * 0.06f), w * 0.8f, h * 0.17f, wobble = 0.03f, points = 12), saucer)
    line(hand.ellipse(Offset(0f, footY + h * 0.03f), w * 0.4f, h * 0.075f, wobble = 0.03f), Ink.copy(alpha = 0.45f), Line * 0.6f)

    val handle = smoothOpen(listOf(
        Offset(w * 0.42f, rimY + h * 0.2f), Offset(w * 0.66f + j(), rimY + h * 0.16f),
        Offset(w * 0.68f + j(), rimY + h * 0.5f), Offset(w * 0.32f, rimY + h * 0.74f),
    ))
    band(handle, cup, h * 0.11f)

    val body = Path().apply {
        moveTo(-w / 2, rimY)
        cubicTo(-w / 2 + j(), rimY + h * 0.72f, -w * 0.3f + j(), footY, -w * 0.15f, footY)
        lineTo(w * 0.15f, footY)
        cubicTo(w * 0.3f + j(), footY, w / 2 + j(), rimY + h * 0.72f, w / 2, rimY)
        close()
    }
    inked(body, cup)
    clipPath(body) {
        when (style) {
            0 -> for (row in 0..3) for (col in -3..3) {
                val x = col * w * 0.16f + (row % 2) * w * 0.08f
                val y = rimY + h * 0.2f + row * h * 0.17f
                drawCircle(decor, w * 0.03f, Offset(x + j(), y + j()))
            }
            1 -> {
                drawRect(decor, topLeft = Offset(-w, rimY + h * 0.17f), size = Size(2 * w, h * 0.09f))
                inked(heart(Offset(0f, rimY + h * 0.52f), w * 0.11f), decor, Line * 0.6f)
            }
            else -> {
                blossom(hand, w * 0.1f, decor, Tone.Mustard)
                at(Offset(-w * 0.24f, rimY + h * 0.5f)) { leaf(hand, Offset.Zero, 200f, w * 0.12f, w * 0.06f) }
                at(Offset(w * 0.22f, rimY + h * 0.5f)) { leaf(hand, Offset.Zero, -20f, w * 0.12f, w * 0.06f) }
            }
        }
    }
    line(body, Ink)

    inked(hand.ellipse(Offset(0f, rimY), w * 0.5f, h * 0.14f, wobble = 0.02f, points = 12), lerp(cup, Tone.Paper, 0.5f))
    drawPath(hand.ellipse(Offset(0f, rimY + h * 0.025f), w * 0.42f, h * 0.09f, wobble = 0.02f), Tone.Tea)

    val steam = Ink.copy(alpha = 0.35f)
    for (k in 0..1) {
        val x0 = -w * 0.12f + k * w * 0.2f
        val pts = List(4) { i -> Offset(x0 + (if (i % 2 == 0) 0f else w * 0.07f) + j(), rimY - h * 0.22f - i * h * 0.17f) }
        line(smoothOpen(pts), steam, Line * 0.8f)
    }
}

/** A plump heart centred on [c], [s] its half-width. */
internal fun heart(c: Offset, s: Float): Path = Path().apply {
    moveTo(c.x, c.y + s * 0.95f)
    cubicTo(c.x - s * 0.35f, c.y + s * 0.6f, c.x - s * 1.05f, c.y + s * 0.2f, c.x - s * 0.95f, c.y - s * 0.3f)
    cubicTo(c.x - s * 0.85f, c.y - s * 0.85f, c.x - s * 0.15f, c.y - s * 0.9f, c.x, c.y - s * 0.4f)
    cubicTo(c.x + s * 0.15f, c.y - s * 0.9f, c.x + s * 0.85f, c.y - s * 0.85f, c.x + s * 0.95f, c.y - s * 0.3f)
    cubicTo(c.x + s * 1.05f, c.y + s * 0.2f, c.x + s * 0.35f, c.y + s * 0.6f, c.x, c.y + s * 0.95f)
    close()
}

internal inline fun DrawScope.at(o: Offset, block: DrawScope.() -> Unit) = translate(o.x, o.y, block)

/** A strawberry hanging from its cap, [s] half its width; the cap sits at the origin. */
internal fun DrawScope.strawberry(hand: Hand, s: Float) {
    fun j() = hand.jitter(s * 0.05f)
    val body = smoothClosed(listOf(
        Offset(-0.95f * s + j(), 0.1f * s), Offset(-0.85f * s + j(), 0.75f * s), Offset(-0.45f * s + j(), 1.35f * s),
        Offset(0.05f * s + j(), 1.7f * s), Offset(0.5f * s + j(), 1.35f * s), Offset(0.88f * s + j(), 0.75f * s),
        Offset(0.95f * s + j(), 0.1f * s), Offset(0.5f * s, -0.12f * s), Offset(0f, -0.05f * s), Offset(-0.5f * s, -0.12f * s),
    ))
    inked(body, Tone.Strawberry)
    clipPath(body) {
        val seed = Color(0xFFF7E19A)
        for (row in 0..4) for (col in -2..2) {
            val x = (col + (row % 2) * 0.5f) * s * 0.42f + hand.jitter(s * 0.04f)
            val y = 0.32f * s + row * 0.3f * s
            drawPath(hand.ellipse(Offset(x, y), s * 0.06f, s * 0.09f, degrees = x * 2f, wobble = 0.1f, points = 6), seed)
        }
        drawPath(hand.ellipse(Offset(-0.45f * s, 0.45f * s), s * 0.12f, s * 0.28f, degrees = 20f), Color.White.copy(alpha = 0.35f))
    }
    for (k in 0 until 5) {
        val d = 15f + k * 37f + hand.jitter(8f)
        leaf(hand, Offset(0f, 0.05f * s), d, s * 0.72f, s * 0.36f, Tone.Leaf)
    }
    line(smoothOpen(listOf(Offset(0f, 0f), Offset(0.1f * s, -0.4f * s), Offset(-0.05f * s, -0.75f * s))), Ink, Line * 1.1f)
}

/** A toadstool with its cap's rim on the origin's line, [s] the cap's half-width. */
internal fun DrawScope.toadstool(hand: Hand, s: Float, cap: Color, spots: Color = Tone.Paper) {
    fun j() = hand.jitter(s * 0.04f)
    val stem = smoothClosed(listOf(
        Offset(-0.3f * s, 0.05f * s), Offset(-0.33f * s + j(), 0.6f * s), Offset(-0.42f * s, 1.1f * s), Offset(0f, 1.18f * s),
        Offset(0.42f * s, 1.1f * s), Offset(0.33f * s + j(), 0.6f * s), Offset(0.3f * s, 0.05f * s),
    ))
    inked(stem, Tone.Paper)
    line(smoothOpen(listOf(Offset(-0.33f * s, 0.42f * s), Offset(0f, 0.5f * s), Offset(0.33f * s, 0.42f * s))), Ink.copy(alpha = 0.6f), Line * 0.7f)
    inked(hand.ellipse(Offset(0f, 0.04f * s), 0.88f * s, 0.16f * s, wobble = 0.03f), Color(0xFFEBD3AE))
    val dome = smoothClosed(listOf(
        Offset(-1f * s, 0f), Offset(-0.85f * s + j(), -0.5f * s), Offset(-0.45f * s + j(), -0.85f * s), Offset(0f, -0.95f * s + j()),
        Offset(0.45f * s + j(), -0.85f * s), Offset(0.85f * s + j(), -0.5f * s), Offset(1f * s, 0f), Offset(0f, 0.12f * s),
    ))
    inked(dome, cap)
    clipPath(dome) {
        for ((x, y, r) in listOf(Triple(-0.5f, -0.45f, 0.16f), Triple(0.05f, -0.68f, 0.13f), Triple(0.55f, -0.35f, 0.15f), Triple(-0.1f, -0.25f, 0.1f), Triple(0.85f, -0.1f, 0.09f))) {
            inked(hand.circle(Offset((x + hand.jitter(0.04f)) * s, y * s), r * s, 0.1f), spots, Line * 0.6f)
        }
    }
    val grass = Tone.SageDeep
    for (k in -2..2) {
        val x = k * 0.22f * s
        line(smoothOpen(listOf(Offset(x, 1.16f * s), Offset(x + k * 0.06f * s, 0.96f * s), Offset(x + k * 0.14f * s + hand.jitter(2f), 0.82f * s))), grass, Line * 0.9f)
    }
}

/** A fern frond from the origin, [length] long, heading [degrees] and curling by [bend] (-1..1). */
internal fun DrawScope.fern(hand: Hand, length: Float, degrees: Float, bend: Float, fill: Color = Tone.SageLight) {
    val tip = polar(length, degrees)
    val ctrl = polar(length * 0.55f, degrees) + polar(length * 0.35f * bend, degrees + 90f)
    fun at(t: Float) = ctrl * (2 * (1 - t) * t) + tip * (t * t)
    fun dir(t: Float): Float {
        val d = ctrl * (2 * (1 - 2 * t)) + tip * (2 * t)
        return kotlin.math.atan2(d.y, d.x) * 180f / kotlin.math.PI.toFloat()
    }
    line(smoothOpen(List(8) { at(it / 7f) }), Ink, Line * 0.8f)
    val pairs = 8
    for (i in 0 until pairs) {
        val t = 0.12f + i * (0.82f / pairs)
        val p = at(t)
        val a = dir(t)
        val l = length * 0.3f * (1f - t * 0.7f)
        inked(hand.leaf(p, a - 58f + hand.jitter(6f), l, l * 0.5f), fill, Line * 0.55f)
        inked(hand.leaf(p, a + 58f + hand.jitter(6f), l, l * 0.5f), fill, Line * 0.55f)
    }
    inked(hand.leaf(at(0.94f), dir(0.94f), length * 0.12f, length * 0.07f), fill, Line * 0.55f)
}

/** A rosebud on a curved stem with two leaves; the bud's base is at the origin, the stem hangs below. */
internal fun DrawScope.rosebud(hand: Hand, s: Float, bud: Color = Tone.Rose, deep: Color = Tone.RoseDeep) {
    val stem = smoothOpen(listOf(Offset(0f, 0f), Offset(0.25f * s, 0.9f * s), Offset(-0.1f * s, 1.8f * s), Offset(0.15f * s, 2.5f * s)))
    line(stem, Tone.SageDeep, Line * 1.2f)
    leaf(hand, Offset(0.22f * s, 0.95f * s), -10f, 0.9f * s, 0.45f * s)
    leaf(hand, Offset(0f, 1.65f * s), 175f, 0.85f * s, 0.42f * s)
    val head = hand.petal(Offset(0f, 0.15f * s), -90f, 1.15f * s, 0.95f * s)
    inked(head, bud)
    line(smoothOpen(listOf(Offset(-0.18f * s, -0.7f * s), Offset(0.05f * s, -0.38f * s), Offset(0.1f * s, -0.1f * s))), deep, Line * 0.8f)
    leaf(hand, Offset(0f, 0.12f * s), -125f, 0.6f * s, 0.26f * s, Tone.SageDeep)
    leaf(hand, Offset(0f, 0.12f * s), -55f, 0.6f * s, 0.26f * s, Tone.SageDeep)
}

/** Two cherries on stems joined at a leafy knot just above the origin; [s] about one cherry's diameter. */
internal fun DrawScope.cherries(hand: Hand, s: Float) {
    val knot = Offset(0.1f * s, -1.1f * s)
    val left = Offset(-0.5f * s + hand.jitter(2f), 0.2f * s)
    val right = Offset(0.55f * s + hand.jitter(2f), 0.38f * s)
    line(smoothOpen(listOf(left, Offset(-0.35f * s, -0.5f * s), knot)), Color(0xFF6E5530), Line * 0.95f)
    line(smoothOpen(listOf(right, Offset(0.55f * s, -0.4f * s), knot)), Color(0xFF6E5530), Line * 0.95f)
    leaf(hand, knot, -20f + hand.jitter(10f), 0.95f * s, 0.45f * s, Tone.Leaf)
    for (c in listOf(left, right)) {
        inked(hand.circle(c + Offset(0f, 0.38f * s), 0.42f * s, 0.05f), Tone.Cherry)
        drawPath(hand.ellipse(c + Offset(-0.15f * s, 0.25f * s), 0.09f * s, 0.14f * s, degrees = 30f), Color.White.copy(alpha = 0.6f))
    }
}

/** A folk-art tulip on a stem with two sweeping leaves; the flower's base is at the origin, [s] its half-width. */
internal fun DrawScope.folkTulip(hand: Hand, s: Float, outer: Color, inner: Color, leafColor: Color) {
    line(smoothOpen(listOf(Offset(0f, 0f), Offset(0.05f * s, 1f * s), Offset(0f, 2.1f * s))), Ink, Line * 2.6f)
    line(smoothOpen(listOf(Offset(0f, 0f), Offset(0.05f * s, 1f * s), Offset(0f, 2.1f * s))), leafColor, Line * 1.3f)
    leaf(hand, Offset(0f, 1.9f * s), -150f, 1.5f * s, 0.5f * s, leafColor)
    leaf(hand, Offset(0f, 1.9f * s), -30f, 1.5f * s, 0.5f * s, leafColor)
    for (side in listOf(-1f, 1f)) {
        val petal = smoothClosed(listOf(
            Offset(0f, 0.15f * s), Offset(side * 0.65f * s, -0.05f * s), Offset(side * 1.05f * s, -0.6f * s),
            Offset(side * 1.15f * s + hand.jitter(1f), -1.3f * s), Offset(side * 0.6f * s, -0.85f * s), Offset(side * 0.15f * s, -0.45f * s),
        ))
        inked(petal, outer)
    }
    val middle = hand.petal(Offset(0f, 0.12f * s), -90f, 1.55f * s, 0.95f * s)
    inked(middle, inner)
    line(smoothOpen(listOf(Offset(0f, -0.15f * s), Offset(0f, -0.95f * s))), Ink.copy(alpha = 0.5f), Line * 0.7f)
    for (k in -1..1) drawCircle(Ink.copy(alpha = 0.55f), 0.06f * s, Offset(k * 0.22f * s, -0.55f * s - kotlin.math.abs(k) * 0.12f * s))
}

/** A lemon lying along [degrees], [s] half its length. */
internal fun DrawScope.lemon(hand: Hand, s: Float, degrees: Float) {
    val turn = degrees * kotlin.math.PI.toFloat() / 180f
    fun p(x: Float, y: Float) = rotated(Offset(x * s + hand.jitter(s * 0.02f), y * s + hand.jitter(s * 0.02f)), turn)
    val body = smoothClosed(listOf(
        p(-1.12f, 0f), p(-0.95f, -0.25f), p(-0.6f, -0.62f), p(0f, -0.72f), p(0.6f, -0.62f), p(0.95f, -0.25f),
        p(1.12f, 0f), p(0.95f, 0.25f), p(0.6f, 0.62f), p(0f, 0.72f), p(-0.6f, 0.62f), p(-0.95f, 0.25f),
    ))
    inked(body, Tone.Lemon)
    clipPath(body) {
        drawPath(hand.ellipse(p(0.1f, 0.62f), 1.05f * s, 0.42f * s, degrees = degrees), Tone.LemonShade)
        for (k in 0 until 14) drawCircle(Tone.LemonShade.copy(alpha = 0.7f), 0.03f * s, p(hand.between(-0.7f, 0.7f), hand.between(-0.5f, 0.3f)))
    }
    line(smoothOpen(listOf(p(-0.55f, -0.38f), p(-0.15f, -0.5f), p(0.2f, -0.48f))), Color.White.copy(alpha = 0.75f), Line * 1.2f)
}

/** A lemon cut across: rind, pith and segments. */
internal fun DrawScope.lemonSlice(hand: Hand, r: Float) {
    inked(hand.circle(Offset.Zero, r, 0.03f), Tone.Lemon)
    drawPath(hand.circle(Offset.Zero, r * 0.84f, 0.02f), Color(0xFFFBF0C4))
    val start = hand.between(0f, 45f)
    for (k in 0 until 8) {
        val a = start + k * 45f
        val seg = smoothClosed(listOf(polar(r * 0.1f, a), polar(r * 0.72f, a - 17f), polar(r * 0.76f, a), polar(r * 0.72f, a + 17f)))
        drawPath(seg, Color(0xFFF6D86A))
        line(seg, Tone.LemonShade, Line * 0.45f)
    }
    drawCircle(Color(0xFFFBF0C4), r * 0.09f, Offset.Zero)
    line(hand.circle(Offset.Zero, r * 0.84f, 0.02f), Ink.copy(alpha = 0.35f), Line * 0.5f)
}

/** A cat sitting facing us, eyes closed and content; [s] about half its height, paws on y = s. */
internal fun DrawScope.cat(hand: Hand, s: Float, fur: Color, stripes: Color?, tailRight: Boolean) {
    val side = if (tailRight) 1f else -1f
    val tail = smoothOpen(listOf(Offset(side * 0.5f * s, 0.85f * s), Offset(side * 1.05f * s, 0.75f * s), Offset(side * 1.05f * s, 0.15f * s), Offset(side * 0.8f * s, -0.15f * s)))
    band(tail, fur, 0.2f * s)
    val body = smoothClosed(listOf(
        Offset(-0.62f * s, 1f * s), Offset(-0.62f * s, 0.45f * s), Offset(-0.38f * s, -0.05f * s), Offset(0f, -0.15f * s),
        Offset(0.38f * s, -0.05f * s), Offset(0.62f * s, 0.45f * s), Offset(0.62f * s, 1f * s), Offset(0f, 1.05f * s),
    ))
    inked(body, fur)
    val chest = lerp(fur, Tone.Paper, 0.55f)
    drawPath(hand.ellipse(Offset(0f, 0.45f * s), 0.22f * s, 0.32f * s, wobble = 0.06f), chest)
    for (x in listOf(-0.22f, 0.22f)) inked(hand.ellipse(Offset(x * s, 0.98f * s), 0.17f * s, 0.1f * s, wobble = 0.04f), fur, Line * 0.8f)
    for (sx in listOf(-1f, 1f)) {
        val ear = smoothClosed(listOf(Offset(sx * 0.18f * s, -0.82f * s), Offset(sx * 0.5f * s, -1.18f * s), Offset(sx * 0.56f * s, -0.62f * s)))
        inked(ear, fur)
        drawPath(smoothClosed(listOf(Offset(sx * 0.3f * s, -0.8f * s), Offset(sx * 0.48f * s, -1.02f * s), Offset(sx * 0.5f * s, -0.72f * s))), Tone.Pink)
    }
    val head = hand.ellipse(Offset(0f, -0.5f * s), 0.58f * s, 0.46f * s, wobble = 0.03f)
    inked(head, fur)
    if (stripes != null) {
        clipPath(head) {
            for (k in -1..1) line(smoothOpen(listOf(Offset(k * 0.14f * s, -0.95f * s), Offset(k * 0.12f * s, -0.78f * s))), stripes, Line * 1.4f)
        }
        clipPath(body) {
            for (k in 0..2) line(smoothOpen(listOf(Offset(side * 0.7f * s, (0.15f + k * 0.22f) * s), Offset(side * 0.42f * s, (0.22f + k * 0.22f) * s))), stripes, Line * 1.4f)
        }
    }
    for (sx in listOf(-1f, 1f)) {
        line(smoothOpen(listOf(Offset(sx * 0.3f * s, -0.52f * s), Offset(sx * 0.2f * s, -0.44f * s), Offset(sx * 0.1f * s, -0.52f * s))), Ink, Line * 0.9f)
        drawCircle(Tone.Pink.copy(alpha = 0.6f), 0.08f * s, Offset(sx * 0.33f * s, -0.36f * s))
        for (k in 0..1) line(smoothOpen(listOf(Offset(sx * 0.3f * s, (-0.34f + k * 0.07f) * s), Offset(sx * 0.62f * s, (-0.38f + k * 0.12f) * s))), Ink.copy(alpha = 0.45f), Line * 0.5f)
    }
    drawPath(heart(Offset(0f, -0.38f * s), 0.06f * s), Tone.Pink)
    for (sx in listOf(-1f, 1f)) {
        line(smoothOpen(listOf(Offset(0f, -0.31f * s), Offset(sx * 0.05f * s, -0.26f * s), Offset(sx * 0.1f * s, -0.3f * s))), Ink, Line * 0.6f)
    }
}

/** A ball of yarn, with a loose end trailing off along [degrees]. */
internal fun DrawScope.yarn(hand: Hand, r: Float, color: Color, degrees: Float, needles: Boolean = false) {
    val end = smoothOpen(listOf(polar(r * 0.9f, degrees), polar(r * 1.5f, degrees + 15f), polar(r * 1.9f, degrees - 10f), polar(r * 2.4f, degrees + 12f)))
    band(end, color, 1.6f, Line * 0.5f)
    if (needles) {
        for (a in listOf(-120f, -60f)) {
            line(smoothOpen(listOf(polar(r * 0.3f, a + 180f), polar(r * 1.9f, a))), Ink, 3.4f)
            line(smoothOpen(listOf(polar(r * 0.3f, a + 180f), polar(r * 1.9f, a))), Color(0xFFD9C29A), 1.8f)
            drawCircle(Tone.Terracotta, 3.2f, polar(r * 1.95f, a))
        }
    }
    val ball = hand.circle(Offset.Zero, r, 0.04f)
    inked(ball, color)
    val strand = lerp(color, Ink, 0.35f)
    clipPath(ball) {
        val a = hand.between(0f, 180f)
        for (k in 0 until 4) {
            val off = (k - 1.5f) * r * 0.42f
            line(smoothOpen(listOf(polar(r * 1.1f, a + 180f) + polar(off, a + 90f), polar(off * 0.4f, a + 90f), polar(r * 1.1f, a) + polar(off, a + 90f))), strand, Line * 0.7f)
        }
        for (k in 0 until 3) {
            val off = (k - 1f) * r * 0.5f
            line(smoothOpen(listOf(polar(r * 1.1f, a + 240f) + polar(off, a + 330f), polar(off * 0.3f, a + 330f), polar(r * 1.1f, a + 60f) + polar(off, a + 330f))), strand.copy(alpha = 0.7f), Line * 0.6f)
        }
    }
}
