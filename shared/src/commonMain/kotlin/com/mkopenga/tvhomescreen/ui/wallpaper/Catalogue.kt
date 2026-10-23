package com.mkopenga.tvhomescreen.ui.wallpaper

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.sin

internal val Patterns: List<Pattern> = listOf(
    roseTrellis(),
    daisies(),
    teacups(),
    strawberries(),
    woodland(),
    sageStripe(),
    cherries(),
    folkTulips(),
    lemons(),
    cats(),
)

internal val Wallpapers: List<Wallpaper> = Patterns.map { it.wallpaper }

/** Shown for a wallpaper this build doesn't know, such as the placeholder controller's. */
internal val PlainPattern = Pattern(Wallpaper("plain", "Plain"), 64f, 64f, Color(0xFFE8D5B5), Color(0xFF2F4535), seed = 1) {}

internal fun patternOf(wallpaper: Wallpaper): Pattern = Patterns.firstOrNull { it.wallpaper.id == wallpaper.id } ?: PlainPattern

private fun roseTrellis() = Pattern(
    Wallpaper("rose-trellis", "Rose trellis"), 280f, 280f,
    ground = Tone.Ivory,
    wainscot = Color(0xFF2E4434),
    seed = 11,
) {
    val slat = Color(0xFFD5D9B4)
    // Slats every 140 along both diagonals. A slat leaving the repeat comes back as the slat two over, so the
    // wave's phase alternates and its period divides the repeat.
    across {
        for (k in -2..4) {
            val c = k * 140f
            val rising = List(11) { i ->
                val x = -20f + i * 32f
                val wave = 1.3f * sin((x / 140f) * 2 * PI.toFloat() + k.mod(2) * 1.7f)
                Offset(x + wave, c - x + wave)
            }
            band(smoothOpen(rising), slat, 6f, Line * 0.6f)
        }
        for (k in -2..2) {
            val c = k * 140f
            val falling = List(11) { i ->
                val x = -20f + i * 32f
                val wave = 1.3f * sin((x / 140f) * 2 * PI.toFloat() + k.mod(2) * 2.3f)
                Offset(x - wave, x - c + wave)
            }
            band(smoothOpen(falling), slat, 6f, Line * 0.6f)
        }
    }
    for ((i, p) in listOf(Offset(0f, 0f), Offset(140f, 140f)).withIndex()) {
        place(p.x, p.y, 52f) {
            val hand = Hand(100 + i)
            val a = hand.between(0f, 120f)
            for (k in 0 until 3) {
                val d = a + k * 120f + hand.jitter(20f)
                leaf(hand, polar(18f, d), d, 30f, 17f)
            }
            rose(hand, 27f, if (i == 0) Tone.Rose else lerp(Tone.Rose, Tone.Clay, 0.35f))
        }
    }
    for ((i, p) in listOf(Offset(140f, 0f), Offset(0f, 140f)).withIndex()) {
        place(p.x, p.y, 34f) {
            val hand = Hand(110 + i)
            val d = hand.between(0f, 360f)
            leaf(hand, polar(8f, d), d, 22f, 12f)
            leaf(hand, polar(8f, d + 150f), d + 150f, 20f, 11f)
            rose(hand, 14f, lerp(Tone.Rose, Tone.Blush, 0.3f))
        }
    }
    for ((i, p) in listOf(Offset(70f, 70f), Offset(210f, 70f), Offset(70f, 210f), Offset(210f, 210f)).withIndex()) {
        place(p.x, p.y, 14f) { blossom(Hand(120 + i), 9f, Tone.Paper, Tone.Mustard) }
    }
}

private fun daisies() = Pattern(
    Wallpaper("daisies", "Daisies"), 300f, 300f,
    ground = Tone.Butter,
    wainscot = Color(0xFF2B4642),
    seed = 21,
) {
    val hand = Hand(2100)
    val flowers = scatter(hand, count = 10, spacing = 72f)
    for ((i, p) in flowers.withIndex()) {
        val r = hand.between(17f, 23f)
        place(p.x, p.y, r + 4f) { daisy(Hand(2200 + i), r) }
    }
    val sprigs = scatter(hand, count = 7, spacing = 50f, avoid = flowers, clearance = 46f)
    for ((i, p) in sprigs.withIndex()) {
        val d = hand.between(0f, 360f)
        place(p.x, p.y, 26f) { sprig(Hand(2300 + i), 36f, d) }
    }
    val dots = scatter(hand, count = 22, spacing = 18f, avoid = flowers + sprigs, clearance = 28f)
    for ((i, p) in dots.withIndex()) {
        val color = if (i % 3 == 0) Tone.Paper else Tone.Terracotta.copy(alpha = 0.75f)
        place(p.x, p.y, 4f) { drawPath(Hand(2400 + i).circle(Offset.Zero, 2.4f), color) }
    }
}

private fun teacups() = Pattern(
    Wallpaper("teacups", "Teatime"), 360f, 340f,
    ground = Tone.Blush,
    wainscot = Color(0xFF55292A),
    seed = 31,
) {
    data class Cup(val at: Offset, val cup: Color, val saucer: Color, val decor: Color, val style: Int, val tilt: Float)
    val cups = listOf(
        Cup(Offset(90f, 90f), Tone.Sage, Tone.Paper, Tone.Paper, 0, -6f),
        Cup(Offset(270f, 175f), Tone.Paper, Tone.Clay, Tone.Rose, 2, 5f),
        Cup(Offset(90f, 260f), Tone.Mustard, Tone.Sage, Tone.Paper, 1, 4f),
        Cup(Offset(270f, 5f), Tone.Terracotta, Tone.Butter, Tone.Paper, 0, -4f),
    )
    for ((i, c) in cups.withIndex()) {
        place(c.at.x, c.at.y, 70f) {
            rotate(c.tilt, pivot = Offset.Zero) { teacup(Hand(3100 + i), 74f, c.cup, c.saucer, c.decor, c.style) }
        }
    }
    val hand = Hand(3200)
    val fillers = listOf(Offset(180f, 92f), Offset(0f, 175f), Offset(180f, 262f))
    for ((i, p) in listOf(fillers[0], fillers[2]).withIndex()) {
        place(p.x, p.y, 16f) { blossom(Hand(3300 + i), 11f, Tone.Paper, Tone.Rose) }
    }
    place(fillers[1].x, fillers[1].y, 16f) { inked(heart(Offset.Zero, 9f), Tone.Rose, Line * 0.8f) }
    val dots = scatter(hand, count = 22, spacing = 26f, avoid = cups.map { it.at } + fillers, clearance = 62f)
    for ((i, p) in dots.withIndex()) {
        place(p.x, p.y, 4f) { drawPath(Hand(3400 + i).circle(Offset.Zero, 2.6f), Tone.Paper.copy(alpha = 0.9f)) }
    }
}

private fun strawberries() = Pattern(
    Wallpaper("strawberries", "Strawberry gingham"), 240f, 240f,
    ground = Color(0xFFF7EEDC),
    wainscot = Color(0xFF2C3E4F),
    seed = 41,
) {
    val check = 20f
    val weave = Color(0xFFD9786F).copy(alpha = 0.26f)
    across {
        // Bands straddle the repeat's edges, so the wrap falls inside a band rather than on an edge.
        for (i in -1 until 6) {
            drawRect(weave, Offset(0f, i * 2 * check + 30f), Size(width, check))
            drawRect(weave, Offset(i * 2 * check + 30f, 0f), Size(check, height))
        }
        val thread = Color(0xFFB85A52).copy(alpha = 0.12f)
        for (i in 0 until 48) {
            drawRect(thread, Offset(0f, i * 5f + 2f), Size(width, 0.8f))
            drawRect(thread, Offset(i * 5f + 2f, 0f), Size(0.8f, height))
        }
    }
    for ((i, p) in listOf(Offset(60f, 48f), Offset(180f, 168f)).withIndex()) {
        place(p.x, p.y, 44f) { rotate(if (i == 0) -14f else 12f, pivot = Offset.Zero) { strawberry(Hand(4100 + i), 17f) } }
    }
    for ((i, p) in listOf(Offset(180f, 62f), Offset(60f, 182f)).withIndex()) {
        place(p.x, p.y, 26f) {
            val hand = Hand(4200 + i)
            leaf(hand, Offset(4f, 4f), 40f + i * 160f, 18f, 9f, Tone.Leaf)
            leaf(hand, Offset(-4f, 2f), 160f + i * 160f, 16f, 8f, Tone.Leaf)
            blossom(hand, 12f, Tone.Paper, Tone.Mustard)
        }
    }
}

private fun woodland() = Pattern(
    Wallpaper("woodland", "Toadstools and ferns"), 300f, 300f,
    ground = Tone.Oat,
    wainscot = Color(0xFF3A3A24),
    seed = 51,
) {
    val red = Color(0xFFC4513A)
    val brown = Color(0xFFC38A45)
    place(225f, 95f, 70f) { fern(Hand(5100), 96f, -105f, 0.7f) }
    place(75f, 245f, 70f) { fern(Hand(5101), 92f, -76f, -0.7f, Color(0xFFB4C29A)) }
    place(62f, 84f, 40f) { toadstool(Hand(5201), 12f, brown) }
    place(90f, 70f, 50f) { toadstool(Hand(5202), 22f, red) }
    place(220f, 228f, 50f) { toadstool(Hand(5203), 21f, brown) }
    place(250f, 242f, 30f) { toadstool(Hand(5204), 11f, red) }
    val hand = Hand(5300)
    for ((i, p) in listOf(Offset(160f, 160f), Offset(10f, 155f), Offset(155f, 10f)).withIndex()) {
        place(p.x, p.y, 16f) {
            val h = Hand(5400 + i)
            line(smoothOpen(listOf(Offset(0f, 10f), Offset(1f, 0f), Offset(-6f, -8f))), Ink, Line * 0.7f)
            line(smoothOpen(listOf(Offset(1f, 0f), Offset(7f, -7f))), Ink, Line * 0.7f)
            for (b in listOf(Offset(-6f, -8f), Offset(7f, -7f), Offset(1f, -11f))) inked(h.circle(b, 3.4f), red, Line * 0.6f)
        }
    }
    val dots = scatter(hand, count = 18, spacing = 24f, avoid = listOf(Offset(225f, 60f), Offset(75f, 210f), Offset(80f, 80f), Offset(230f, 235f), Offset(160f, 160f), Offset(10f, 155f), Offset(155f, 10f)), clearance = 46f)
    for ((i, p) in dots.withIndex()) {
        val color = if (i % 2 == 0) Tone.Sage else Tone.Terracotta.copy(alpha = 0.7f)
        place(p.x, p.y, 4f) { drawPath(Hand(5500 + i).circle(Offset.Zero, 2.4f), color) }
    }
}

private fun sageStripe() = Pattern(
    Wallpaper("sage-stripe", "Sage stripe"), 240f, 220f,
    ground = Color(0xFFF4EAD3),
    wainscot = Color(0xFF4E2A28),
    seed = 61,
) {
    val sage = Color(0xFFB8C49F)
    val pin = Tone.SageDeep.copy(alpha = 0.55f)
    across {
        for (x in listOf(-108f, 12f, 132f)) {
            drawRect(sage, Offset(x, 0f), Size(56f, height))
            drawRect(pin, Offset(x + 62f, 0f), Size(1.4f, height))
            drawRect(pin, Offset(x + 114f, 0f), Size(1.4f, height))
        }
    }
    for ((i, p) in listOf(Offset(88f, 40f), Offset(208f, 150f)).withIndex()) {
        place(p.x + 12f, p.y, 50f) { rotate(if (i == 0) -8f else 8f, pivot = Offset.Zero) { at(Offset(0f, -8f)) { rosebud(Hand(6100 + i), 13f) } } }
    }
    for ((i, p) in listOf(Offset(40f, 150f), Offset(160f, 40f)).withIndex()) {
        place(p.x, p.y, 10f) {
            val h = Hand(6200 + i)
            for (k in 0 until 3) drawPath(h.circle(polar(5f, -90f + k * 120f), 2.6f), Tone.Paper)
        }
    }
}

private fun cherries() = Pattern(
    Wallpaper("cherries", "Cherries"), 240f, 240f,
    ground = Color(0xFFE3A99B),
    wainscot = Color(0xFF2E4434),
    seed = 71,
) {
    val spots = listOf(Offset(60f, 60f), Offset(180f, 180f), Offset(180f, 60f), Offset(60f, 180f))
    val dot = Color(0xFFF8EBDD)
    for (j in 0 until 8) for (i in 0 until 8) {
        val p = Offset(30f * i + (j % 2) * 15f, 30f * j)
        if (spots.any { distance(it, p) < 36f }) continue
        place(p.x, p.y, 6f) { drawPath(Hand(7000 + j * 8 + i).circle(Offset.Zero, 4.2f), dot) }
    }
    for ((i, p) in spots.withIndex()) {
        place(p.x, p.y, 36f) {
            val tilt = if (i % 2 == 0) -12f else 16f
            rotate(tilt, pivot = Offset.Zero) { at(Offset(0f, 4f)) { cherries(Hand(7100 + i), 19f) } }
        }
    }
}

private fun folkTulips() = Pattern(
    Wallpaper("folk-tulips", "Folk tulips"), 230f, 270f,
    ground = Color(0xFFA9503E),
    wainscot = Color(0xFF243A30),
    seed = 81,
) {
    val cream = Color(0xFFF5E6C8)
    val stem = Color(0xFF9DB085)
    place(57f, 72f, 80f) { folkTulip(Hand(8100), 24f, cream, Tone.Mustard, stem) }
    place(172f, 207f, 80f) { folkTulip(Hand(8101), 24f, Tone.Mustard, cream, stem) }
    for ((i, p) in listOf(Offset(172f, 82f), Offset(57f, 217f)).withIndex()) {
        place(p.x, p.y, 24f) {
            inked(heart(Offset.Zero, 11f), if (i == 0) Tone.Mustard else cream, Line * 0.8f)
            for (k in 0 until 3) drawCircle(cream, 2.8f, polar(21f, -30f + k * 120f))
        }
    }
    for ((i, p) in listOf(Offset(115f, 140f), Offset(0f, 5f)).withIndex()) {
        place(p.x, p.y, 10f) {
            val h = Hand(8200 + i)
            for (k in 0 until 4) drawPath(h.circle(polar(6f, k * 90f + 45f), 2.4f), cream.copy(alpha = 0.85f))
        }
    }
}

private fun lemons() = Pattern(
    Wallpaper("lemons", "Lemon grove"), 280f, 280f,
    ground = Color(0xFFDCE2C6),
    wainscot = Color(0xFF273B54),
    seed = 91,
) {
    for ((i, p) in listOf(Offset(70f, 70f), Offset(210f, 210f)).withIndex()) {
        place(p.x, p.y, 64f) {
            val hand = Hand(9100 + i)
            val tilt = if (i == 0) -24f else 18f
            val stalk = polar(26f, tilt + 180f)
            leaf(hand, stalk, tilt + 215f, 40f, 19f, Tone.SageDeep)
            leaf(hand, stalk, tilt + 145f, 36f, 17f, Tone.Sage)
            lemon(hand, 28f, tilt)
            at(stalk + polar(30f, tilt + 180f)) { blossom(hand, 9f, Tone.Paper, Tone.Mustard) }
        }
    }
    place(210f, 72f, 26f) { lemonSlice(Hand(9200), 21f) }
    place(70f, 212f, 34f) {
        val hand = Hand(9201)
        leaf(hand, Offset(3f, 3f), -40f, 30f, 14f, Tone.SageDeep)
        leaf(hand, Offset(-3f, 3f), 210f, 28f, 13f, Tone.Sage)
        at(Offset(-8f, -4f)) { blossom(hand, 10f, Tone.Paper, Tone.Mustard) }
        at(Offset(9f, -10f)) { blossom(hand, 8f, Tone.Paper, Tone.Mustard) }
    }
    val hand = Hand(9300)
    val dots = scatter(hand, count = 14, spacing = 30f, avoid = listOf(Offset(70f, 70f), Offset(210f, 210f), Offset(210f, 72f), Offset(70f, 212f)), clearance = 56f)
    for ((i, p) in dots.withIndex()) place(p.x, p.y, 5f) { drawPath(Hand(9400 + i).circle(Offset.Zero, 2.6f), Tone.Paper.copy(alpha = 0.9f)) }
}

private fun cats() = Pattern(
    Wallpaper("cats", "Cats and yarn"), 290f, 270f,
    ground = Color(0xFFC3CDB7),
    wainscot = Color(0xFF47304A),
    seed = 101,
) {
    place(72f, 76f, 64f) { cat(Hand(10100), 33f, Tone.Ginger, lerp(Tone.Ginger, Ink, 0.35f), tailRight = true) }
    place(217f, 211f, 64f) { cat(Hand(10101), 33f, Tone.Grey, null, tailRight = false) }
    place(219f, 84f, 50f) { yarn(Hand(10200), 15f, Tone.Rose, 150f, needles = true) }
    place(74f, 216f, 50f) { yarn(Hand(10201), 15f, Tone.Mustard, 20f) }
    place(146f, 146f, 30f) { yarn(Hand(10202), 9f, Tone.Paper, 200f) }
    val hand = Hand(10300)
    val dots = scatter(hand, count = 12, spacing = 34f, avoid = listOf(Offset(72f, 76f), Offset(217f, 211f), Offset(219f, 84f), Offset(74f, 216f), Offset(146f, 146f)), clearance = 56f)
    for ((i, p) in dots.withIndex()) {
        place(p.x, p.y, 8f) {
            val h = Hand(10400 + i)
            drawPath(h.ellipse(Offset(0f, 2f), 3.6f, 3f), Tone.Paper.copy(alpha = 0.85f))
            for (k in 0 until 3) drawPath(h.circle(Offset((k - 1) * 3.6f, -3.2f + kotlin.math.abs(k - 1) * 1.2f), 1.5f), Tone.Paper.copy(alpha = 0.85f))
        }
    }
}
