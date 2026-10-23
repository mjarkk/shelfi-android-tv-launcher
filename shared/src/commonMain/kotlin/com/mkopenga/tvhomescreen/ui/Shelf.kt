package com.mkopenga.tvhomescreen.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.mkopenga.tvhomescreen.ShelfItem
import com.mkopenga.tvhomescreen.ui.settings.BookendHeight
import com.mkopenga.tvhomescreen.ui.settings.DuckArt
import com.mkopenga.tvhomescreen.ui.settings.Ducks
import com.mkopenga.tvhomescreen.ui.settings.DuckColours
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The plank's top edge. The mockup has it at 820 under a header; without one, the shelf and its info line are
 * centred on the screen instead.
 */
private const val PlankTop = 766f

// The books' own box: the screen's width (books past its edges are simply off screen), from above the lifted
// cover's focus ring to below its shadow.
private const val BooksTop = PlankTop - 650f
private const val BooksHeight = 760f

/** The height a lifted book and its focus ring must fit in. */
internal const val Baseline = PlankTop - BooksTop
private const val OutlineWidth = 5f
private const val OutlineOffset = 8f
private const val OutlineEdge = 2f

internal const val FocusRingReach = OutlineOffset + OutlineWidth / 2 + (OutlineWidth + 2 * OutlineEdge) / 2

/**
 * The shelf: its books and the ducks holding them up, the plank, and the focused book's title underneath. [drop] is how
 * far the whole shelf has moved down out of view while settings are open, in design px; read in the layer only.
 * Everything but the title pans with [camera].
 */
@Composable
internal fun BoxScope.Shelf(
    state: HomeState,
    motion: FocusMotion,
    camera: ShelfCamera,
    artworks: ArtworkStore,
    press: PressFeedback,
    ducks: Ducks,
    drop: () -> Float = { 0f },
) {
    Box(Modifier.fillMaxSize().graphicsLayer { translationY = drop() * density }) {
        val items = state.items
        val geometry = remember(items) { ShelfGeometry(items) }
        val books = Modifier.offset(0.dp, BooksTop.dp).size(DesignWidth.dp, BooksHeight.dp)
        if (items.isEmpty()) {
            Box(books) {
                EmptyShelf(Modifier.align(Alignment.BottomCenter).padding(bottom = 210.dp), everythingHidden = state.allItems.isNotEmpty())
            }
        }
        Books(items, geometry, motion, camera, artworks, press, ducks, { state.duckColours }, books)
        Plank(geometry.plankLength, camera)
        InfoLine(state, Modifier.offset(ShelfLeft.dp, (PlankTop + 80f).dp).width(1680.dp))
    }
}

@Composable
private fun Books(
    items: List<ShelfItem>,
    geometry: ShelfGeometry,
    motion: FocusMotion,
    camera: ShelfCamera,
    artworks: ArtworkStore,
    press: PressFeedback,
    ducks: Ducks,
    duckColours: () -> DuckColours,
    modifier: Modifier,
) {
    val typography = LocalTypography.current
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val shadow = remember { CoverShadow.create() }
    val measurePolicy = remember(items, geometry, motion, camera, press) { BooksMeasurePolicy(items, geometry, motion, camera, press) }

    Layout(
        content = {
            for (item in items) {
                key(item.id) {
                    val painter = remember(item, typography, measurer) { ItemPainter(item, typography, measurer) }
                    BookFaces(painter, artworks)
                }
            }
        },
        modifier = modifier.drawWithCache {
            val d = density
            val outline = Stroke(OutlineWidth * d)
            val outlineEdge = Stroke((OutlineWidth + 2 * OutlineEdge) * d)
            // Read here, in the cache, so a new colour bakes the ducks once without recomposing the shelf.
            val colours = duckColours()
            val endDuck = ducks.end(d, colours)
            val startDuck = if (geometry.lead > 0f) ducks.start(d, colours) else null
            val duckTop = ((Baseline - BookendHeight) * d).roundToInt().toFloat()
            onDrawWithContent {
                val p = geometry.clampPosition(motion.position)
                val scroll = camera.px(d)
                forTurned(geometry, p) { i, e ->
                    val h = geometry.itemHeight(i, e)
                    val width = geometry.turn(i, e).width
                    with(shadow) { drawShadow(leftPx(geometry, i, p, scroll, d), topPx(h, e, d), (width * d).roundToInt(), (h * d).roundToInt(), e) }
                }
                val startLeft = ((ShelfLeft * d).roundToInt() - scroll).toFloat()
                val end = geometry.bookend
                val endLeft = leftPx(geometry, end, p, scroll, d).toFloat()
                if (startDuck != null && isOnScreen(startDuck, startLeft)) with(startDuck) { draw(startLeft, duckTop, focus = 0f) }
                if (isOnScreen(endDuck, endLeft)) with(endDuck) { draw(endLeft, duckTop, geometry.expansion(end, p)) }
                drawContent()
                // Each duck's wing lies over the book it holds, as far as that book still shows its spine.
                if (startDuck != null && isOnScreen(startDuck, startLeft)) drawWingTip(startDuck, startLeft, duckTop, geometry, 0, p, scroll)
                if (end > 0 && isOnScreen(endDuck, endLeft)) drawWingTip(endDuck, endLeft, duckTop, geometry, end - 1, p, scroll)
                forTurned(geometry, p) { i, e ->
                    val alpha = smoothstep(0.55f, 1f, e)
                    if (alpha > 0f) {
                        val h = geometry.itemHeight(i, e)
                        val width = geometry.turn(i, e).width
                        drawOutline(leftPx(geometry, i, p, scroll, d), topPx(h, e, d), (width * d).roundToInt(), (h * d).roundToInt(), BookCorner, alpha, outline, outlineEdge)
                    }
                }
            }
        },
        measurePolicy = measurePolicy,
    )
}

private fun DrawScope.isOnScreen(duck: DuckArt, left: Float): Boolean = left + duck.reachRight > 0f && left + duck.reachLeft < size.width

/** The spine of book [i] faces out from its box's left edge to where it meets the cover, [ShelfGeometry.Turn.edge] on. */
private fun DrawScope.drawWingTip(duck: DuckArt, left: Float, top: Float, geometry: ShelfGeometry, i: Int, p: Float, scroll: Int) {
    val spineLeft = leftPx(geometry, i, p, scroll, density).toFloat()
    val spineRight = spineLeft + geometry.turn(i, geometry.expansion(i, p)).edge * density
    with(duck) { drawWingTip(left, top, spineLeft, spineRight) }
}

/** A book's two faces, each in its own layer that the shelf turns and scales, so moving never redraws them. */
@Composable
private fun BookFaces(painter: ItemPainter, artworks: ArtworkStore) {
    val spine = rememberGraphicsLayer()
    val cover = rememberGraphicsLayer()
    val density = LocalDensity.current.density
    SideEffect {
        cover.clip = true
        cover.setRoundRectOutline(size = Size(CoverWidth * density, painter.coverHeight * density), cornerRadius = BookCorner * density)
    }
    Spacer(Modifier.layoutId(spine).drawBehind { painter.drawSpine(this, artworks) })
    Spacer(Modifier.layoutId(cover).drawBehind { painter.drawCover(this, artworks) })
}

/** Recorded once; it pans in its layer. */
@Composable
private fun Plank(length: Float, camera: ShelfCamera) {
    Spacer(
        Modifier
            .fillMaxSize()
            .graphicsLayer { translationX = -camera.px(density).toFloat() }
            .drawWithCache {
                val u = density
                val left = PlankLeft * u
                val right = (PlankLeft + length) * u
                onDrawBehind {
                    drawBracket(left + BracketInset * u)
                    drawBracket(right - (BracketInset + BracketWidth) * u)
                    drawRoundRect(Palette.Plank, Offset(left, PlankTop * u), Size(right - left, PlankThickness * u), CornerRadius(4f * u))
                    drawRect(Palette.PlankShadow, Offset(left + 20f * u, (PlankTop + PlankThickness) * u), Size(right - left - 40f * u, 14f * u))
                    drawEndGrain(left, right)
                }
            },
    )
}

private const val PlankThickness = 28f
private const val BracketInset = 34f
private const val BracketWidth = 22f
private const val BracketDrop = 44f
private val PlankLight = lerp(Palette.Plank, Palette.Cream, 0.18f)

private fun DrawScope.drawBracket(x: Float) {
    val u = density
    val top = (PlankTop + PlankThickness) * u
    val width = BracketWidth * u
    val height = BracketDrop * u
    val radius = CornerRadius(width / 2)
    drawRoundRect(Palette.PlankShadow, Offset(x, top), Size(width, height), radius)
    drawRoundRect(Palette.Plank, Offset(x, top), Size(width - 4f * u, height - 3f * u), radius)
    drawRect(Palette.Plank, Offset(x, top), Size(width, width / 2))
    drawRect(PlankLight, Offset(x + 3f * u, top), Size(2f * u, height - width / 2))
}

private fun DrawScope.drawEndGrain(left: Float, right: Float) {
    val u = density
    val top = (PlankTop + 3f) * u
    val size = Size(3f * u, (PlankThickness - 6f) * u)
    drawRect(Palette.PlankShadow, Offset(left + 3f * u, top), size, alpha = 0.6f)
    drawRect(Palette.PlankShadow, Offset(right - 6f * u, top), size, alpha = 0.6f)
}

/**
 * Places each visible book's two faces as a turning box: the spine swings away about the edge it shares with the
 * cover while the cover swings in, both in perspective from [CameraDistance]. Per frame only positions and layer
 * properties change; the faces keep their natural size, so they are measured and recorded once.
 */
private class BooksMeasurePolicy(
    private val items: List<ShelfItem>,
    private val geometry: ShelfGeometry,
    private val motion: FocusMotion,
    private val camera: ShelfCamera,
    private val press: PressFeedback,
) : MeasurePolicy {
    private val count = geometry.count
    private val spines = arrayOfNulls<Placeable>(count)
    private val covers = arrayOfNulls<Placeable>(count)
    private val xs = IntArray(count)
    private val es = FloatArray(count)

    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val d = density
        val p = geometry.clampPosition(motion.position)
        val scroll = camera.px(d)
        val right = constraints.maxWidth
        var first = 0
        var last = -1
        for (i in 0 until count) {
            val e = geometry.expansion(i, p)
            val x = leftPx(geometry, i, p, scroll, d)
            if (x + geometry.slotWidth(i, e) * d <= 0) {
                first = i + 1
                continue
            }
            if (x >= right) break
            last = i
            xs[i] = x
            es[i] = e
            spines[i] = measurables[2 * i].measure(
                Constraints.fixed((geometry.spineWidths[i] * d).roundToInt(), (geometry.spineHeights[i] * d).roundToInt()),
            )
            covers[i] = if (e > 0f) {
                measurables[2 * i + 1].measure(Constraints.fixed((CoverWidth * d).roundToInt(), (geometry.coverHeights[i] * d).roundToInt()))
            } else {
                null
            }
        }
        return layout(constraints.maxWidth, constraints.maxHeight) {
            val camera = cameraDistanceFor(d)
            for (i in first..last) {
                val e = es[i]
                val turn = geometry.turn(i, e)
                val bottom = (Baseline - CoverLift * e) * d
                val edge = xs[i] + turn.edge * d
                val pressed = press.scaleFor(items[i].id)
                val spine = spines[i]!!
                (measurables[2 * i].layoutId as GraphicsLayer).run {
                    pivotOffset = Offset(spine.width.toFloat(), spine.height.toFloat())
                    scaleX = turn.spineScale * turn.squeeze * pressed
                    scaleY = turn.spineScale * pressed
                    // Positive rotationY swings a layer's right side away; the spine's left side must go.
                    rotationY = -turn.angle
                    cameraDistance = camera
                    colorFilter = FaceShade.of(sin(turn.angle * DegToRad))
                    spine.placeWithLayer((edge - spine.width).roundToInt(), (bottom - spine.height).roundToInt(), this, zIndex = e)
                }
                val cover = covers[i] ?: continue
                (measurables[2 * i + 1].layoutId as GraphicsLayer).run {
                    pivotOffset = Offset(0f, cover.height.toFloat())
                    scaleX = turn.coverScale * turn.squeeze * pressed
                    scaleY = turn.coverScale * pressed
                    rotationY = 90f - turn.angle
                    cameraDistance = camera
                    colorFilter = FaceShade.of(cos(turn.angle * DegToRad))
                    cover.placeWithLayer(edge.roundToInt(), (bottom - cover.height).roundToInt(), this, zIndex = e)
                }
            }
        }
    }
}

/**
 * Darkens a face as it turns away from the viewer, which sells the turn far more than perspective alone. Steps
 * are cached so placement allocates nothing; a face at rest gets no filter (and so no compositing layer).
 */
private object FaceShade {
    private const val Steps = 24
    private const val Darkest = 0.42f
    private val filters = arrayOfNulls<ColorFilter>(Steps + 1)

    fun of(away: Float): ColorFilter? {
        val step = (away * Steps).roundToInt().coerceIn(0, Steps)
        if (step == 0) return null
        return filters[step] ?: ColorFilter.tint(Color.Black.copy(alpha = Darkest * step / Steps), BlendMode.SrcAtop)
            .also { filters[step] = it }
    }
}

private const val DegToRad = (PI / 180).toFloat()

private inline fun forTurned(geometry: ShelfGeometry, p: Float, block: (Int, Float) -> Unit) {
    val k = floor(p).toInt()
    for (i in k..k + 1) {
        if (i !in 0 until geometry.count) continue
        val e = geometry.expansion(i, p)
        if (e > 0f) block(i, e)
    }
}

private fun leftPx(geometry: ShelfGeometry, i: Int, p: Float, scroll: Int, d: Float): Int =
    ((ShelfLeft + geometry.itemLeft(i, p)) * d).roundToInt() - scroll

private fun topPx(height: Float, e: Float, d: Float): Int = ((Baseline - height - CoverLift * e) * d).roundToInt()

private fun DrawScope.drawOutline(left: Int, top: Int, width: Int, height: Int, radius: Float, alpha: Float, stroke: Stroke, edge: Stroke) {
    // CSS outline: 5px wide, 8px outside the border box, following the corner radius outwards.
    val inset = (OutlineOffset + OutlineWidth / 2) * density
    // A dark edge either side keeps the cream ring visible on a light wallpaper.
    drawRoundRect(
        color = Palette.FocusEdge,
        topLeft = Offset(left - inset, top - inset),
        size = Size(width + 2 * inset, height + 2 * inset),
        cornerRadius = CornerRadius(radius * density + inset),
        style = edge,
        alpha = alpha,
    )
    drawRoundRect(
        color = Palette.Cream,
        topLeft = Offset(left - inset, top - inset),
        size = Size(width + 2 * inset, height + 2 * inset),
        cornerRadius = CornerRadius(radius * density + inset),
        style = stroke,
        alpha = alpha,
    )
}
