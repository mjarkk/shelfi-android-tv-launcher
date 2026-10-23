package com.mkopenga.tvhomescreen.ui

import com.mkopenga.tvhomescreen.ShelfItem
import com.mkopenga.tvhomescreen.ui.settings.BookendWidth
import com.mkopenga.tvhomescreen.ui.settings.LeftDuck
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

// Design px from design/Shelf.dc.html.
internal const val CoverWidth = 400f
internal const val CoverHeight = 560f
internal const val CoverMargin = 28f
internal const val CoverLift = 36f
internal const val ItemGap = 8f

/**
 * The left edge of what stands on the shelf (the first book, or the duck holding it up) in the room, whose x 0 is
 * the screen's left edge with the camera at rest.
 */
internal const val ShelfLeft = 120f

/** The plank reaches as far past the books at its right end. */
internal const val PlankLeft = 80f

/** The plank of a shelf that fits on the screen. */
internal const val PlankMinLength = 1760f

/**
 * How close the focused book and its neighbours may come to a screen edge before the camera moves: the TV's
 * overscan-safe margin.
 */
internal const val SafeMargin = 96f

/** Eye distance for the book's turn, in design px: close enough to read as 3D, far enough not to distort. */
internal const val CameraDistance = 2200f

/** A spine this wide gets exactly the mockup's 28px either side of its cover. */
private const val ReferenceSpine = 84f

/**
 * What a slot grows by as its book turns out. The same for every book, so while focus moves from one book to the
 * next their two slots always add up to the same width and nothing else on the shelf shifts.
 */
private const val SlotGrowth = CoverWidth + 2 * CoverMargin - ReferenceSpine

/**
 * Pure shelf layout as a function of the continuous focus position `p`. Book i turns from spine to cover by
 * `e = clamp(1 - |i - p|, 0, 1)`, and everything else (its slot, its turned box, lift, outline) derives
 * from that, so one animated float lays out the whole shelf. The bookend after the last book is one more focus stop
 * at [bookend] and takes part the same way. With [leftDuck], a duck that is no focus stop holds up the first book,
 * from x 0 to [lead].
 */
internal class ShelfGeometry(items: List<ShelfItem>, leftDuck: Boolean = LeftDuck) {
    /** Books only. */
    val count: Int = items.size

    /** The bookend's slot, after the last book. */
    val bookend: Int = count

    /** Where the first book stands; the duck before it, if any, has the shelf up to there. An empty shelf has none. */
    val lead: Float = if (leftDuck && count > 0) BookendWidth else 0f
    val spineWidths = FloatArray(count) { spineWidthOf(items[it]).toFloat() }
    val spineHeights = FloatArray(count) { spineHeightOf(items[it]).toFloat() }
    val coverHeights = FloatArray(count) { coverHeightOf(items[it]) }

    /** prefix[i] = left edge of slot i when nothing is turned out. */
    private val prefix = FloatArray(count + 1).also { p ->
        p[0] = lead
        for (i in 0 until count) p[i + 1] = p[i] + spineWidths[i] + ItemGap
    }

    /** The bookend stands against the last book, holding it up: no gap. */
    private val bookendLeft: Float = if (count > 0) prefix[count] - ItemGap else 0f

    /** The widest the shelf gets, with a book turned out. Focus never changes the bookend's size. */
    val maxContentWidth: Float = bookendLeft + BookendWidth + if (count > 0) SlotGrowth else 0f

    val plankLength: Float = maxOf(PlankMinLength, maxContentWidth + 2 * (ShelfLeft - PlankLeft))

    /** As far as the camera ever shows. */
    val roomWidth: Float = plankLength + 2 * PlankLeft

    val maxCamera: Float = roomWidth - DesignWidth

    fun clampPosition(p: Float): Float = p.coerceIn(0f, count.toFloat())

    fun expansion(i: Int, p: Float): Float = (1f - abs(i - p)).coerceIn(0f, 1f)

    fun slotWidth(i: Int, e: Float): Float = if (i == bookend) BookendWidth else spineWidths[i] + SlotGrowth * e

    fun slotLeft(i: Int, p: Float): Float {
        var x = if (i == bookend) bookendLeft else prefix[i]
        val k = floor(p).toInt()
        for (j in k..k + 1) {
            if (j in 0 until i) x += SlotGrowth * expansion(j, p)
        }
        return x
    }

    fun itemHeight(i: Int, e: Float): Float = lerp(spineHeights[i], coverHeights[i], e)

    /** The turned book's left edge, centred in its slot; the bookend fills its slot. */
    fun itemLeft(i: Int, p: Float): Float {
        if (i == bookend) return slotLeft(i, p)
        val e = expansion(i, p)
        return slotLeft(i, p) + (slotWidth(i, e) - turn(i, e).width) / 2
    }

    /** Uses the books' rest positions, which focus moving never shifts, so the camera never chases the books turning. */
    fun cameraFor(index: Int, from: Float): Float {
        val p = index.toFloat()
        val left = if (index > 0) ShelfLeft + slotLeft(index - 1, p) else 0f
        val right = if (index < bookend) ShelfLeft + slotLeft(index + 1, p) + slotWidth(index + 1, 0f) else roomWidth
        var camera = from
        if (right - camera > DesignWidth - SafeMargin) camera = right - (DesignWidth - SafeMargin)
        if (left - camera < SafeMargin) camera = left - SafeMargin
        return camera.coerceIn(0f, maxCamera)
    }

    /**
     * Book i turned by e (0 = spine facing out, 1 = cover facing out) about the edge where spine meets cover, seen
     * in perspective from [CameraDistance]. Both faces scale to the shared height so the book stays one box. A
     * half-turned box is wider than its slot, so it is narrowed (both faces' depth, never their height) to fit.
     */
    fun turn(i: Int, e: Float): Turn {
        val turn = scratch
        val h = itemHeight(i, e)
        turn.spineScale = h / spineHeights[i]
        turn.coverScale = h / coverHeights[i]
        turn.angle = e * 90f
        val theta = e * (PI.toFloat() / 2)
        val depth = spineWidths[i] * turn.spineScale
        val face = CoverWidth * turn.coverScale
        val slot = slotWidth(i, e)
        var squeeze = 1f
        // Perspective makes the width a little less than linear in the squeeze, so iterate; a few steps converge.
        for (step in 0 until 6) {
            val width = projected(depth * squeeze, cos(theta), sin(theta)) + projected(face * squeeze, sin(theta), cos(theta))
            if (width <= slot + 0.01f) break
            squeeze *= slot / width
        }
        turn.squeeze = squeeze.coerceAtMost(1f)
        turn.edge = projected(depth * turn.squeeze, cos(theta), sin(theta))
        turn.width = turn.edge + projected(face * turn.squeeze, sin(theta), cos(theta))
        return turn
    }

    /** Reused by [turn] so layout and draw allocate nothing per frame; read it before calling [turn] again. */
    private val scratch = Turn()

    class Turn {
        /** Where the spine meets the cover, from the box's left edge. */
        var edge = 0f
        var width = 0f
        var angle = 0f
        var spineScale = 1f
        var coverScale = 1f

        /** Horizontal narrowing applied to both faces, 1 when the turned box fits its slot. */
        var squeeze = 1f
    }

    companion object {
        fun spineWidthOf(item: ShelfItem): Int = item.spineWidth ?: (70 + stableHash(item.id).mod(27))

        fun spineHeightOf(item: ShelfItem): Int = item.spineHeight ?: (460 + (stableHash(item.id) / 27).mod(91))

        fun coverHeightOf(item: ShelfItem): Float = CoverHeight

        /** Width on screen of a face [length] long, turned so its far edge is [away] (sin) into the screen. */
        private fun projected(length: Float, facing: Float, away: Float): Float =
            length * facing * CameraDistance / (CameraDistance + length * away)
    }
}

/**
 * [CameraDistance] in the unit GraphicsLayer.cameraDistance takes. Android's and Skiko's render nodes both place
 * the camera like Skia's SkCamera3D, in inches of 72 px whatever the density; [density] is px per design px.
 */
internal fun cameraDistanceFor(density: Float): Float = CameraDistance * density / 72f

internal fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/** FNV-1a, so derived sizes and cover art never depend on a platform's String.hashCode. */
internal fun stableHash(s: String): Int {
    var h = -0x7ee3623b
    for (c in s) {
        h = h xor c.code
        h *= 0x01000193
    }
    return h
}
