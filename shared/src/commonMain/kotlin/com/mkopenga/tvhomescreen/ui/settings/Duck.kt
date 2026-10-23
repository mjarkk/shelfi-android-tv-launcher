package com.mkopenga.tvhomescreen.ui.settings

import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.mkopenga.tvhomescreen.ui.Palette
import com.mkopenga.tvhomescreen.ui.smoothstep
import com.mkopenga.tvhomescreen.ui.wallpaper.Ink
import com.mkopenga.tvhomescreen.ui.wallpaper.inkStroke
import com.mkopenga.tvhomescreen.ui.wallpaper.smoothClosed
import com.mkopenga.tvhomescreen.ui.wallpaper.smoothOpen
import kotlin.math.ceil
import kotlin.math.floor

// The ducks that hold the books up, the one after the last book being the way into settings. Design px; each stands
// on its bottom edge with its chest against the books. The art faces left, as the duck after the books stands, so
// x 0 is the side against the books; the duck before them is its mirror image.
internal const val BookendWidth = 210f
internal const val BookendHeight = 300f

/** A second, decorative duck holds up the start of the shelf, like a pair of bookends. */
internal const val LeftDuck = true

/** How far the hugging wing reaches over the book's spine: short of a centred title on the narrowest spine. */
internal const val WingReach = 18f

/**
 * The ducks' looks, in the order the Duck frame in settings steps through them; the first is the default. Persisted by
 * [name], so a new look is one more entry here.
 */
internal enum class DuckColours(
    val label: String,
    val body: Color,
    val shade: Color,
    val light: Color,
    val bill: Color,
    val wing: Color,
    val wingShade: Color,
    val blush: Color,
    val blushesAtRest: Boolean,
    val tuft: Boolean,
    val restsWithEyesClosed: Boolean,
) {
    Yellow(
        label = "Yellow",
        body = Color(0xFFF5C94C),
        shade = Color(0xFFE0A53A),
        light = Color(0xFFFDEDB5),
        bill = Color(0xFFEC8A3A),
        wing = Color(0xFFEC8A3A),
        wingShade = Color(0xFFCD6B2B),
        blush = Color(0xFFF08A6C),
        blushesAtRest = false,
        tuft = false,
        restsWithEyesClosed = false,
    ),
    White(
        label = "White",
        body = Color(0xFFF8F1E3),
        shade = Color(0xFFE2D2B6),
        light = Color(0xFFFFFFFF),
        bill = Color(0xFFF2A53C),
        wing = Color(0xFFF8F1E3),
        wingShade = Color(0xFFE2D2B6),
        blush = Color(0xFFEFA39C),
        blushesAtRest = true,
        tuft = true,
        restsWithEyesClosed = true,
    ),
    ;

    val next: DuckColours get() = entries[(ordinal + 1) % entries.size]

    companion object {
        fun named(name: String?): DuckColours = entries.firstOrNull { it.name == name } ?: entries.first()
    }
}

/** How many duck pieces have been rasterised; tests use it to prove that moving focus only blits them. */
internal var duckBakes = 0
    private set

/**
 * Every duck's art, kept for as long as the home screen, so that only a new density or colour bakes a duck again:
 * not a change to the shelf, and not settings coming back into view. Main thread only.
 */
@Stable
internal class Ducks {
    private var end: DuckArt? = null
    private var start: DuckArt? = null
    private var picture: DuckArt? = null

    /** The duck after the last book, the way into settings. */
    fun end(density: Float, colours: DuckColours): DuckArt =
        end.or(density, colours) { DuckArt(density, colours, mirrored = false, focusable = true) }.also { end = it }

    /** The duck before the first book. */
    fun start(density: Float, colours: DuckColours): DuckArt =
        start.or(density, colours) { DuckArt(density, colours, mirrored = true, focusable = false) }.also { start = it }

    /** The one in the Duck frame's picture, at its own [density]. */
    fun picture(density: Float, colours: DuckColours): DuckArt =
        picture.or(density, colours) { DuckArt(density, colours, mirrored = false, focusable = false) }.also { picture = it }

    private inline fun DuckArt?.or(density: Float, colours: DuckColours, bake: () -> DuckArt): DuckArt =
        this?.takeIf { it.density == density && it.colours == colours } ?: bake()
}

/**
 * One duck rendered once at one density, each piece a bitmap, so a glide only blits and fades them: drawing its
 * curves live cost every frame of a glide a full rasterisation, which a software GPU feels. Nothing of it moves but
 * its face. [mirrored] turns it to face right, for the start of the shelf. Only a [focusable] duck bakes the focus
 * ring and its second face.
 *
 * The duck is drawn behind the books ([draw]) and the tip of its wing again in front of them ([drawWingTip]), so the
 * wing lies over the spine it hugs, and a book turning its cover out passes in front of it.
 */
internal class DuckArt(val density: Float, val colours: DuckColours, private val mirrored: Boolean, focusable: Boolean) {
    private val body = piece(BodyBox) {
        drawBody(colours)
        drawWing(colours)
    }
    private val tip = piece(TipBox) { clipRect(TipBox.left, TipBox.top, 0f, TipBox.bottom) { drawWing(colours) } }
    private val restingFace = piece(FaceBox) { drawFace(colours, focused = false) }
    private val focusedFace = if (focusable) piece(FaceBox) { drawFace(colours, focused = true) } else null
    private val ring = if (focusable) piece(RingBox) { drawRing(colours) } else null

    /** Everything this draws lies between these, in px from the duck's left edge, whichever way it faces. */
    val reachLeft: Float = -Reach * density
    val reachRight: Float = (W + Reach) * density

    /**
     * Draws the duck with its top-left at [left],[top] px. [focus] (0..1), on the shelf's own spring, turns its face
     * happy and fades in the cream ring the books' focus wears, in a soft glow.
     */
    fun DrawScope.draw(left: Float, top: Float, focus: Float) {
        if (ring != null) {
            val alpha = smoothstep(0.55f, 1f, focus)
            if (alpha > 0f) {
                // Where the duck meets the books its ring runs on behind them, so it never crosses the one it holds,
                // and the glow stops at the plank.
                val books = left + (if (mirrored) W else 0f) * density
                clipRect(if (mirrored) -Far else books, -Far, if (mirrored) books else Far, top + H * density) { drawPiece(ring, left, top, alpha) }
            }
        }
        drawPiece(body, left, top)
        val happy = if (focusedFace != null) smoothstep(0.3f, 0.75f, focus) else 0f
        if (happy < 1f) drawPiece(restingFace, left, top, 1f - happy)
        if (happy > 0f) drawPiece(focusedFace!!, left, top, happy)
    }

    /**
     * Draws again, in front of the books, the tip of the wing that lies over the book it holds, only between [from]
     * and [to] px: the part of that book's spine still facing out. As the book turns, its cover sweeps across from the
     * edge the spine shares with it and covers the tip, which stays where it is.
     */
    fun DrawScope.drawWingTip(left: Float, top: Float, from: Float, to: Float) {
        if (to <= from) return
        clipRect(from, -Far, to, Far) { drawPiece(tip, left, top) }
    }

    private fun DrawScope.drawPiece(piece: Piece, left: Float, top: Float, alpha: Float = 1f) {
        drawImage(piece.image, Offset(left + piece.x, top + piece.y), alpha = alpha)
    }

    /** [box] in design px of the art as drawn facing left, baked whole-pixel aligned so blits stay sharp. */
    private fun piece(box: Rect, art: DrawScope.() -> Unit): Piece {
        val d = density
        val boxLeft = if (mirrored) W - box.right else box.left
        val x = floor(boxLeft * d)
        val y = floor(box.top * d)
        val image = ImageBitmap(ceil(box.width * d + 1).toInt(), ceil(box.height * d + 1).toInt())
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(image.width.toFloat(), image.height.toFloat())) {
            translate(-x, -y) {
                scale(d, d, Offset.Zero) {
                    if (mirrored) scale(-1f, 1f, Offset(W / 2, 0f)) { art() } else art()
                }
            }
        }
        duckBakes++
        return Piece(image, x, y)
    }

    private class Piece(val image: ImageBitmap, val x: Float, val y: Float)
}

private const val W = BookendWidth
private const val H = BookendHeight
private const val Line = 3.2f
private const val Far = 1e6f
private val EyeInk = Color(0xFF3A2619)

/** Fills sit a hair off their outlines, as in the wallpapers. */
private val Misprint = Offset(1.1f, 0.9f)

// A big round head on a plump pear of a body, chest and face both against the books.
private val BodyPath = smoothClosed(
    listOf(
        Offset(38f, 298f), Offset(8f, 266f), Offset(5f, 218f), Offset(10f, 172f), Offset(11f, 124f),
        Offset(18f, 76f), Offset(44f, 34f), Offset(88f, 14f), Offset(134f, 22f), Offset(160f, 54f),
        Offset(164f, 98f), Offset(162f, 130f), Offset(182f, 162f), Offset(204f, 206f), Offset(207f, 252f),
        Offset(190f, 287f), Offset(146f, 300f), Offset(90f, 302f),
    ),
)
private val TailPath = smoothClosed(
    listOf(Offset(182f, 244f), Offset(188f, 208f), Offset(204f, 182f), Offset(220f, 168f), Offset(227f, 182f), Offset(224f, 214f), Offset(212f, 244f)),
)
private val BillPath = smoothClosed(
    listOf(Offset(50f, 98f), Offset(28f, 92f), Offset(8f, 96f), Offset(0f, 108f), Offset(5f, 121f), Offset(26f, 128f), Offset(48f, 123f)),
)
private val Mouth = smoothOpen(listOf(Offset(7f, 112f), Offset(24f, 115f), Offset(40f, 110f)))
private val NearFoot = smoothClosed(
    listOf(Offset(70f, 300f), Offset(66f, 287f), Offset(44f, 281f), Offset(20f, 284f), Offset(3f, 292f), Offset(8f, 300f)),
)
private val FarFoot = smoothClosed(
    listOf(Offset(164f, 300f), Offset(160f, 289f), Offset(142f, 285f), Offset(120f, 288f), Offset(108f, 294f), Offset(112f, 300f)),
)
private val Toes = listOf(
    smoothOpen(listOf(Offset(25f, 299f), Offset(28f, 291f))),
    smoothOpen(listOf(Offset(44f, 299f), Offset(46f, 289f))),
    smoothOpen(listOf(Offset(128f, 299f), Offset(131f, 292f))),
    smoothOpen(listOf(Offset(145f, 299f), Offset(147f, 290f))),
)
private val Tuft = listOf(
    smoothClosed(listOf(Offset(80f, 20f), Offset(70f, 4f), Offset(74f, -8f), Offset(86f, 2f), Offset(92f, 18f))),
    smoothClosed(listOf(Offset(92f, 18f), Offset(98f, 0f), Offset(110f, -8f), Offset(112f, 6f), Offset(104f, 20f))),
)
private val EyeAt = Offset(64f, 80f)
private val BlushAt = Offset(84f, 112f)

// The hugging wing: a plump flipper from the side, forward over the edge of the book, the tip pressed round the spine.
private val WingPath = smoothClosed(
    listOf(
        Offset(116f, 166f), Offset(96f, 155f), Offset(62f, 157f), Offset(30f, 163f), Offset(6f, 166f), Offset(-10f, 168f),
        Offset(-17f, 178f), Offset(-WingReach, 190f), Offset(-13f, 201f), Offset(-2f, 205f), Offset(14f, 201f), Offset(44f, 201f),
        Offset(78f, 204f), Offset(106f, 199f), Offset(121f, 184f),
    ),
)
private val Feathers = listOf(
    smoothOpen(listOf(Offset(-1f, 177f), Offset(3f, 188f), Offset(0f, 199f))),
    smoothOpen(listOf(Offset(14f, 174f), Offset(18f, 187f), Offset(15f, 199f))),
)

// The focus ring follows the duck's outline at the books' ring offset: a cream band with a dark edge either side.
private const val RingInner = 6f
private const val RingEdgeIn = 8f
private const val RingEdgeOut = 13f
private const val RingOuter = 15f

/** A soft cream glow fades out this far beyond the ring, muting the wallpaper round a focused duck. */
private const val Glow = 36f

private fun silhouette(c: DuckColours): List<Path> = listOf(BodyPath, TailPath, BillPath, NearFoot, FarFoot) + if (c.tuft) Tuft else emptyList()

private fun Path.bounds(margin: Float): Rect = getBounds().inflate(margin)

private fun Rect.union(other: Rect) = Rect(minOf(left, other.left), minOf(top, other.top), maxOf(right, other.right), maxOf(bottom, other.bottom))

private val BodyBox = (listOf(BodyPath, TailPath, BillPath, NearFoot, FarFoot, WingPath) + Tuft).map { it.bounds(Line + 2f) }.reduce { a, b -> a.union(b) }
private val TipBox = WingPath.bounds(Line + 2f).let { Rect(it.left, it.top, 0f, it.bottom) }
private val RingBox = BodyBox.inflate(RingOuter + Glow)

/** The eyes and the blush, all that changes when the duck takes focus. */
internal val FaceBox = Rect(EyeAt.x - 16f, EyeAt.y - 16f, BlushAt.x + 18f, BlushAt.y + 12f)

/** Past either side of its slot, how far the duck's ring, glow and wing reach. */
private const val Reach = RingOuter + Glow + 8f

/** Fills [path]'s bounds, for use inside a clip. */
private fun DrawScope.cover(path: Path, color: Color, alpha: Float) {
    val box = path.getBounds()
    drawRect(color, box.topLeft, box.size, alpha = alpha)
}

private fun DrawScope.fillMisprinted(path: Path, color: Color) = translate(Misprint.x, Misprint.y) { drawPath(path, color) }

private fun DrawScope.outline(path: Path, width: Float = Line, color: Color = Ink) = drawPath(path, color, style = inkStroke(width))

private fun DrawScope.drawBody(c: DuckColours) {
    if (c.tuft) for (feather in Tuft) {
        fillMisprinted(feather, c.body)
        outline(feather)
    }
    fillMisprinted(TailPath, c.body)
    outline(TailPath)

    fillMisprinted(BodyPath, c.body)
    clipPath(BodyPath) {
        // One soft shade, away from the light: the body less itself moved towards the light, stacked a few times.
        for (shift in listOf(Offset(-34f, -26f), Offset(-22f, -16f), Offset(-12f, -8f))) {
            clipPath(Path().apply { addPath(BodyPath, shift) }, ClipOp.Difference) { cover(BodyPath, c.shade, 0.42f) }
        }
        // And one soft highlight, glazed on the crown.
        val glint = Offset(66f, 44f)
        drawCircle(Brush.radialGradient(listOf(c.light.copy(alpha = 0.95f), c.light.copy(alpha = 0f)), glint, 26f), 26f, glint)
    }
    outline(BodyPath)

    for (foot in listOf(FarFoot, NearFoot)) {
        fillMisprinted(foot, c.bill)
        outline(foot)
    }
    for (toe in Toes) outline(toe, Line * 0.7f, Ink.copy(alpha = 0.6f))

    fillMisprinted(BillPath, c.bill)
    outline(BillPath)
    outline(Mouth, Line * 0.8f, Ink.copy(alpha = 0.75f))
}

private fun DrawScope.drawFace(c: DuckColours, focused: Boolean) {
    if (focused || c.blushesAtRest) drawOval(c.blush.copy(alpha = 0.6f), BlushAt - Offset(13f, 7f), Size(26f, 14f))
    if (focused != c.restsWithEyesClosed) {
        // Happy: the curve of a smile, upside down.
        drawPath(smoothOpen(listOf(EyeAt + Offset(-9f, 4f), EyeAt + Offset(0f, -5f), EyeAt + Offset(9f, 4f))), EyeInk, style = inkStroke(Line * 1.35f))
    } else {
        drawOval(EyeInk, EyeAt - Offset(7f, 9f), Size(14f, 18f))
        drawCircle(Color.White, 2.8f, EyeAt + Offset(-2.2f, -3.8f))
    }
}

private fun DrawScope.drawWing(c: DuckColours) {
    fillMisprinted(WingPath, c.wing)
    clipPath(WingPath) {
        clipPath(Path().apply { addPath(WingPath, Offset(-4f, -8f)) }, ClipOp.Difference) { cover(WingPath, c.wingShade, 0.6f) }
    }
    outline(WingPath)
    for (feather in Feathers) outline(feather, Line * 0.75f, Ink.copy(alpha = 0.6f))
}

/**
 * The glow's rings stack, so it is densest at the ring. Then each band of the ring covers everything within its
 * distance of the silhouette and replaces what the band before it left there.
 */
private fun DrawScope.drawRing(c: DuckColours) {
    val parts = silhouette(c)
    for (k in 1..12) {
        val glow = Stroke(2 * (RingOuter + Glow * k / 12), join = StrokeJoin.Round)
        for (part in parts) drawPath(part, Palette.Cream, alpha = 0.17f, style = glow)
    }
    fun band(distance: Float, color: Color, mode: BlendMode) {
        val stroke = Stroke(2 * distance, join = StrokeJoin.Round)
        for (part in parts) {
            drawPath(part, color, blendMode = mode)
            drawPath(part, color, style = stroke, blendMode = mode)
        }
    }
    band(RingOuter, Palette.FocusEdge, BlendMode.Src)
    band(RingEdgeOut, Palette.Cream, BlendMode.Src)
    band(RingEdgeIn, Palette.FocusEdge, BlendMode.Src)
    band(RingInner, Color.Transparent, BlendMode.Clear)
}
