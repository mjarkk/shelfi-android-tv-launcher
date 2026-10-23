package com.mkopenga.tvhomescreen.ui.wallpaper

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.mkopenga.tvhomescreen.ui.DesignWidth
import com.mkopenga.tvhomescreen.ui.ShelfCamera
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Where the wallpaper meets the panelling, in design px. The plank (766-808) sits just above, like a plate rail. */
internal const val DadoTop = 800f

/**
 * Screens of wall above the shelf's: settings frames hang one screen up and their pages another screen above that,
 * and the view pans up to them. One more is drawn live for a pan that overshoots.
 */
private const val ScreensAbove = 2

/** How far the panelling goes below the screen, for a pan that overshoots downwards. */
private const val PanelBelow = 120f

private const val CrossfadeMillis = 600
private const val PrefetchDelayMillis = 5_000L

/** How long the screens above stay baked after the view comes back down to the shelf. */
private const val KeepAboveMillis = 10_000L

/** The warm brown of shadows in the room. */
private val Dusk = Color(0xFF2A1609)

/** How many times a wallpaper layer has recorded its drawing; tests use it to prove the moving shelf never redraws the wall. */
internal var wallDraws = 0
    private set

/** How many bands of a room have been baked; tests wait on it, as baking runs in the background. */
internal var wallBakes = 0
    private set

internal fun roomPx(roomWidth: Float, density: Float, screen: IntSize): Int = screen.width + ShelfCamera.maxPx(roomWidth, density)

/**
 * The room behind the shelf: the controller's wallpaper over painted panelling under a warm light, crossfading when
 * it changes. Each wallpaper's room is baked in the background into opaque pieces, so however far it pans the wall
 * costs one screen of blits per frame. Main thread only.
 */
@Stable
internal class WallScene(private val controller: WallpaperController) {
    var shown: Pattern by mutableStateOf(patternOf(controller.current.value))
        private set
    var previous: Pattern? by mutableStateOf(null)
        private set
    private val fade = Animatable(1f)

    /** Read only from draw or layer blocks. */
    val fadeAlpha: Float get() = fade.value

    private var density = 0f
    private var screen = IntSize.Zero
    private var roomWidth = DesignWidth.toFloat()

    /** Whether the view is (or was just) looking up the wall, so the screens up there are worth baking. */
    private var lookingUp = false
    private val looks = HashMap<String, WallLook>()
    private var light: RoomLight? = null
    private val baking = Mutex()

    /** The look to draw [pattern] with right now; renders its tile on the spot if nothing prepared it. */
    fun look(pattern: Pattern, density: Float, screen: IntSize): WallLook {
        use(density, screen)
        return looks.getOrPut(pattern.wallpaper.id) { WallLook(pattern, pattern.render(density)) }
    }

    fun light(density: Float): RoomLight {
        use(density, screen)
        return light ?: RoomLight(density).also { light = it }
    }

    private fun use(density: Float, screen: IntSize) {
        if (density == this.density && screen == this.screen) return
        if (density != this.density) {
            looks.clear()
            light = null
        } else {
            looks.values.forEach { it.forget() }
        }
        this.density = density
        this.screen = screen
    }

    suspend fun follow() {
        while (density == 0f) withFrameNanos {}
        controller.current.collectLatest { wallpaper ->
            val next = patternOf(wallpaper)
            prepare(next)
            if (next != shown) {
                previous = shown
                shown = next
                fade.snapTo(0f)
            }
            // Also finishes a crossfade that a quick second change interrupted.
            fade.animateTo(1f, tween(CrossfadeMillis, easing = FastOutSlowInEasing))
            previous = null
            val upcoming = controller.upcoming()?.let(::patternOf)
            looks.keys.retainAll(setOf(shown.wallpaper.id, upcoming?.wallpaper?.id))
            if (upcoming != null && upcoming != shown) {
                delay(PrefetchDelayMillis)
                prepareTile(upcoming)
            }
        }
    }

    /** Bakes the screens above while the view looks up the wall, and lets them go a while after it comes down. */
    suspend fun followPan(drop: () -> Float) {
        snapshotFlow { drop() > 0.5f }.distinctUntilChanged().collectLatest { up ->
            if (up) {
                lookingUp = true
                prepare(shown)
            } else {
                delay(KeepAboveMillis)
                lookingUp = false
                looks.values.forEach { it.forgetAbove() }
            }
        }
    }

    suspend fun followRoom(width: () -> Float) {
        snapshotFlow { width() }.collectLatest {
            roomWidth = it
            if (density != 0f) prepare(shown)
        }
    }

    /** Renders [pattern]'s tile and bakes its room off the main thread, so a change never stalls a frame. */
    private suspend fun prepare(pattern: Pattern) = baking.withLock {
        val look = prepareTile(pattern) ?: return@withLock
        for (band in 0..(if (lookingUp) ScreensAbove else 0)) {
            val d = density
            val size = screen
            val room = roomPx(roomWidth, d, size)
            val done = look.baked(band)
            if (done != null && done.room == room) continue
            val baked = withContext(Dispatchers.Default) { look.bake(band, room, size, d, done) }
            if (d != density || size != screen || room != roomPx(roomWidth, density, screen) || looks[pattern.wallpaper.id] !== look) return@withLock
            look.setBaked(band, baked)
            wallBakes++
        }
    }

    private suspend fun prepareTile(pattern: Pattern): WallLook? {
        looks[pattern.wallpaper.id]?.let { return it }
        val d = density
        val tile = withContext(Dispatchers.Default) { pattern.render(d) }
        if (d != density) return null
        return looks.getOrPut(pattern.wallpaper.id) { WallLook(pattern, tile) }
    }
}

/**
 * The room behind the shelf, panned down by [drop] design px as the view looks up the wall and left by [pan] px at
 * the given density, both read only in the layer phase. [roomWidth] is in design px.
 */
@Composable
internal fun Wall(
    scene: WallScene,
    drop: () -> Float = { 0f },
    pan: (Float) -> Int = { 0 },
    roomWidth: () -> Float = { DesignWidth.toFloat() },
) {
    val currentDrop by rememberUpdatedState(drop)
    val currentRoom by rememberUpdatedState(roomWidth)
    LaunchedEffect(scene) { scene.follow() }
    LaunchedEffect(scene) { scene.followPan { currentDrop() } }
    LaunchedEffect(scene) { scene.followRoom { currentRoom() } }
    Box(
        Modifier.fillMaxSize().graphicsLayer {
            translationX = -pan(density).toFloat()
            translationY = drop() * density
        },
    ) {
        scene.previous?.let { key(it.wallpaper.id) { RoomLayer(scene, it, fading = false, roomWidth) } }
        val shown = scene.shown
        key(shown.wallpaper.id) { RoomLayer(scene, shown, fading = true, roomWidth) }
    }
}

@Composable
private fun RoomLayer(scene: WallScene, pattern: Pattern, fading: Boolean, roomWidth: () -> Float) {
    Spacer(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                // Per-draw alpha instead of an offscreen buffer: a baked room is opaque draws side by side.
                compositingStrategy = CompositingStrategy.ModulateAlpha
                alpha = if (fading) scene.fadeAlpha else 1f
            }
            .drawWithCache {
                val screen = IntSize(size.width.roundToInt(), size.height.roundToInt())
                val look = scene.look(pattern, density, screen)
                val light = scene.light(density)
                onDrawBehind { look.drawRoom(this, light, roomPx(roomWidth(), density, screen), screen) }
            },
    )
}

internal class BakedBand(
    val room: Int,
    val left: ImageBitmap,
    val right: ImageBitmap,
    val paper: ImageBitmap,
    val panels: ImageBitmap?,
)

/**
 * One wallpaper on the wall at one density: its tile, the brush that repeats it, and its room baked into opaque
 * pieces once ready: band 0 is the shelf's screen, band n the nth screen up the wall.
 */
internal class WallLook(val pattern: Pattern, private val tile: ImageBitmap) {
    private val paper = ShaderBrush(repeating())

    /** Snapshot state, so the layer swaps the pieces in when they land. */
    private val baked = mutableStateListOf<BakedBand?>().apply { repeat(ScreensAbove + 1) { add(null) } }

    fun baked(band: Int): BakedBand? = baked[band]

    fun setBaked(band: Int, pieces: BakedBand) {
        baked[band] = pieces
    }

    fun forget() {
        for (band in baked.indices) baked[band] = null
    }

    fun forgetAbove() {
        for (band in 1 until baked.size) baked[band] = null
    }

    private fun repeating(): Shader = ImageShader(tile, TileMode.Repeated, TileMode.Repeated)

    /** The whole room from the scope's x 0; the layer pans it. */
    fun drawRoom(scope: DrawScope, light: RoomLight, room: Int, screen: IntSize) = with(scope) {
        wallDraws++
        val h = screen.height
        // Bands off screen cost nothing; those above are only seen while the view pans up.
        for (band in 0..ScreensAbove + 1) {
            translate(0f, -band * h.toFloat()) {
                val pieces = baked.getOrNull(band)
                if (pieces != null) {
                    drawPieces(pieces, band, room, screen, light)
                } else {
                    drawBand(band, room, screen, paper, light, 0, room)
                }
            }
        }
        val below = (PanelBelow * density).roundToInt()
        drawRect(skirting(pattern.wainscot), Offset(0f, h.toFloat()), Size(room.toFloat(), below.toFloat()))
        with(light) { drawStretched(0, room, screen, h, h + below, 0, room) }
    }

    private fun DrawScope.drawPieces(pieces: BakedBand, band: Int, room: Int, screen: IntSize, light: RoomLight) {
        val half = screen.width / 2
        val rightStart = room - (screen.width - half)
        blit(pieces.left, 0, 0, 0, pieces.left.width)
        repeatAcross(pieces.paper, half, rightStart, 0)
        pieces.panels?.let { repeatAcross(it, half, rightStart, pieces.paper.height) }
        if (pieces.room == room) {
            blit(pieces.right, rightStart, 0, 0, pieces.right.width)
        } else {
            drawBand(band, room, screen, paper, light, rightStart, room)
        }
    }

    private fun DrawScope.repeatAcross(strip: ImageBitmap, from: Int, to: Int, top: Int) {
        val w = strip.width
        var x = from / w * w
        while (x < to) {
            val start = max(x, from)
            val end = min(x + w, to)
            blit(strip, start, top, start - x, end - start)
            x += w
        }
    }

    /** Reuses the pieces of [previous] that don't depend on the room's width. Safe off the main thread. */
    fun bake(band: Int, room: Int, screen: IntSize, density: Float, previous: BakedBand?): BakedBand {
        val light = RoomLight(density)
        val paper = ShaderBrush(repeating())
        val h = screen.height
        val half = screen.width / 2
        val rightStart = room - (screen.width - half)
        val dado = if (band == 0) ceil(DadoTop * density).toInt() else h
        fun image(width: Int, height: Int, art: DrawScope.() -> Unit): ImageBitmap {
            val image = ImageBitmap(width, height, hasAlpha = false)
            CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, Canvas(image), Size(width.toFloat(), height.toFloat()), art)
            return image
        }
        val left = previous?.left ?: image(half, h) { drawBand(band, room, screen, paper, light, 0, half) }
        val right = image(screen.width - half, h) {
            translate(-rightStart.toFloat(), 0f) { drawBand(band, room, screen, paper, light, rightStart, room) }
        }
        val paperStrip = previous?.paper ?: image(tile.width, dado) { drawMiddle(band, screen, paper, light, tile.width) }
        val panels = when {
            band != 0 -> null
            previous?.panels != null -> previous.panels
            else -> image(panelPitchPx(density), h - dado) {
                translate(0f, -dado.toFloat()) { drawMiddle(band, screen, paper, light, panelPitchPx(density)) }
            }
        }
        return BakedBand(room, left, right, paperStrip, panels)
    }

    /**
     * One screen's height of the room, [band] screens up the wall, between the room's x [from] and [to] px, drawn as
     * if it were on screen.
     */
    private fun DrawScope.drawBand(band: Int, room: Int, screen: IntSize, paper: Brush, light: RoomLight, from: Int, to: Int) {
        val h = screen.height.toFloat()
        clipRect(from.toFloat(), 0f, to.toFloat(), h) {
            drawPeriodic(band, screen, paper, from, to, wrap = false)
            with(light) { drawStretched(band, room, screen, 0, screen.height, from, to) }
        }
    }

    /** From a point in the room where the paper and the panels both start a repeat. */
    private fun DrawScope.drawMiddle(band: Int, screen: IntSize, paper: Brush, light: RoomLight, width: Int) {
        drawPeriodic(band, screen, paper, 0, width, wrap = true)
        with(light) { drawColumn(band, screen, 0, width, 0, screen.height) }
    }

    private fun DrawScope.drawPeriodic(band: Int, screen: IntSize, paper: Brush, from: Int, to: Int, wrap: Boolean) {
        val h = screen.height.toFloat()
        val width = (to - from).toFloat()
        if (band > 0) {
            // The paper's repeat is anchored to the room, not to the band.
            translate(0f, band * h) { drawRect(paper, Offset(from.toFloat(), -band * h), Size(width, h)) }
            return
        }
        drawRect(paper, Offset(from.toFloat(), 0f), Size(width, (DadoTop + 4f) * density))
        drawWainscot(pattern.wainscot, from.toFloat(), to.toFloat(), h, wrap)
    }

    /** Pixel for pixel, so no filtering: a software GPU would otherwise take four samples per pixel. */
    private fun DrawScope.blit(image: ImageBitmap, x: Int, y: Int, srcX: Int, width: Int) = drawImage(
        image,
        srcOffset = IntOffset(srcX, 0),
        srcSize = IntSize(width, image.height),
        dstOffset = IntOffset(x, y),
        dstSize = IntSize(width, image.height),
        filterQuality = FilterQuality.None,
    )
}

private const val PanelTop = 962f
private const val PanelBottom = 1050f
private const val PanelWidth = 276f
private const val PanelPitch = 310f
private const val PanelStart = 47f
private const val Skirting = 1062f

/** Whole px, so the panelling repeats exactly and a strip of it can stand in for any stretch of the room. */
private fun panelPitchPx(density: Float): Int = (PanelPitch * density).roundToInt()

private fun skirting(paint: Color) = lerp(paint, Color.Black, 0.28f)

/**
 * Painted panelling between the room's x [from] and [to] px, down to [bottom] px: a dado rail, a calm band for the
 * info line, recessed panels, a skirting. With [wrap] the panels run on past the room's left end, as in the middle of
 * a long room.
 */
private fun DrawScope.drawWainscot(paint: Color, from: Float, to: Float, bottom: Float, wrap: Boolean) {
    val d = density
    val w = to - from
    val top = DadoTop * d
    val light = lerp(paint, Tone.Paper, 0.16f)
    val shade = lerp(paint, Color.Black, 0.38f)
    val face = lerp(paint, Color.Black, 0.07f)
    drawRect(paint, Offset(from, top), Size(w, bottom - top))
    drawRect(light, Offset(from, top), Size(w, 7f * d))
    drawRect(shade, Offset(from, top + 7f * d), Size(w, 4f * d))

    drawRect(shade, Offset(from, (PanelTop - 14f) * d), Size(w, 2f * d))
    drawRect(light.copy(alpha = 0.5f), Offset(from, (PanelTop - 12f) * d), Size(w, 1.5f * d))
    val pitch = panelPitchPx(d)
    val start = PanelStart * d
    val panelWidth = PanelWidth * d
    val panelTop = PanelTop * d
    val panelHeight = (PanelBottom - PanelTop) * d
    var k = floor((from - start - panelWidth) / pitch).toInt() + 1
    if (!wrap) k = max(k, 0)
    while (start + k * pitch < to) {
        val left = start + k * pitch
        drawRect(face, Offset(left, panelTop), Size(panelWidth, panelHeight))
        drawRect(shade, Offset(left, panelTop), Size(panelWidth, 3f * d))
        drawRect(shade, Offset(left, panelTop), Size(3f * d, panelHeight))
        drawRect(light, Offset(left, panelTop + panelHeight - 2f * d), Size(panelWidth, 2f * d))
        drawRect(light, Offset(left + panelWidth - 2f * d, panelTop), Size(2f * d, panelHeight))
        k++
    }

    drawRect(skirting(paint), Offset(from, Skirting * d), Size(w, bottom - Skirting * d))
    drawRect(light.copy(alpha = 0.6f), Offset(from, Skirting * d), Size(w, 2f * d))
}

/**
 * The room's light, the same whatever the wallpaper: a warm glow over the shelf falling off to dusky corners and
 * up the wall, a contact shadow where the plank meets the wall, and the plank's shadow on the panelling. It is laid
 * out for a room one screen wide: a longer room gets its two halves at its ends and its middle column drawn out
 * between them, so the ends stay dusky and the light keeps still on the wall as the view pans.
 */
internal class RoomLight(private val d: Float) {
    private val glow = ShaderBrush(glowShader())
    private val alongPlank = softShadow(440, 24, vertical = listOf(0f, 0.02f, 0.06f, 0.12f, 0.2f, 0.28f), horizontal = edgesOf(40))
    private val underPlank = softShadow(440, 24, vertical = listOf(0.55f, 0.3f, 0.14f, 0.05f, 0f), horizontal = edgesOf(24))
    private val columns = arrayOfNulls<ImageBitmap>(3)
    private var columnsFor = IntSize.Zero

    private fun glowShader(): Shader = RadialGradientShader(
        center = Offset(960f * d, 330f * d),
        radius = 1350f * d,
        colors = listOf(Color(0xFFFFE4A6).copy(alpha = 0.13f), Color.Transparent, Dusk.copy(alpha = 0.14f), Dusk.copy(alpha = 0.46f)),
        colorStops = listOf(0f, 0.36f, 0.66f, 1f),
    )

    fun DrawScope.drawStretched(band: Int, room: Int, screen: IntSize, top: Int, bottom: Int, from: Int, to: Int) {
        val half = screen.width / 2
        val rightStart = room - (screen.width - half)
        if (from < half) {
            clipRect(from.toFloat(), top.toFloat(), min(to, half).toFloat(), bottom.toFloat()) { drawOnScreen(band, screen, top, bottom) }
        }
        val middleStart = max(from, half)
        val middleEnd = min(to, rightStart)
        if (middleEnd > middleStart) drawColumn(band, screen, middleStart, middleEnd, top, bottom)
        if (to > rightStart) {
            clipRect(max(from, rightStart).toFloat(), top.toFloat(), to.toFloat(), bottom.toFloat()) {
                translate((rightStart - half).toFloat(), 0f) { drawOnScreen(band, screen, top, bottom) }
            }
        }
    }

    fun DrawScope.drawColumn(band: Int, screen: IntSize, from: Int, to: Int, top: Int, bottom: Int) {
        drawImage(
            column(band, screen),
            srcOffset = IntOffset(0, top),
            srcSize = IntSize(1, bottom - top),
            dstOffset = IntOffset(from, top),
            dstSize = IntSize(to - from, bottom - top),
            filterQuality = FilterQuality.None,
        )
    }

    private fun DrawScope.drawOnScreen(band: Int, screen: IntSize, top: Int, bottom: Int) {
        when {
            band == 0 -> {
                drawShadows(this)
                drawGlow(this, top.toFloat(), bottom.toFloat(), screen.width.toFloat())
            }
            // One screen of wall [band] screens above the shelf's is the shelf's screen folded at every screen edge
            // (mirrored, then straight, and so on), so it is seamless at each fold.
            band % 2 == 1 -> scale(1f, -1f, pivot = Offset(0f, screen.height / 2f)) {
                drawGlow(this, 0f, screen.height.toFloat(), screen.width.toFloat())
            }
            else -> drawGlow(this, 0f, screen.height.toFloat(), screen.width.toFloat())
        }
    }

    private fun column(band: Int, screen: IntSize): ImageBitmap {
        if (screen != columnsFor) {
            columns.fill(null)
            columnsFor = screen
        }
        val key = if (band == 0) 0 else 2 - band % 2
        columns[key]?.let { return it }
        val height = screen.height + if (band == 0) (PanelBelow * d).roundToInt() else 0
        val image = ImageBitmap(1, height)
        CanvasDrawScope().draw(Density(d), LayoutDirection.Ltr, Canvas(image), Size(1f, height.toFloat())) {
            translate(-(screen.width / 2).toFloat(), 0f) { drawOnScreen(band, screen, 0, height) }
        }
        columns[key] = image
        return image
    }

    private fun drawShadows(scope: DrawScope) = with(scope) {
        // Where the plank meets the wall, whether or not books stand there; it only shows in the gaps between them.
        stretch(alongPlank, 80f, 700f, 1760f, 68f)
        stretch(underPlank, 90f, 798f, 1740f, 84f)
    }

    private fun drawGlow(scope: DrawScope, top: Float, bottom: Float, width: Float) =
        scope.drawRect(glow, Offset(0f, top), Size(width, bottom - top))

    private fun DrawScope.stretch(image: ImageBitmap, x: Float, y: Float, width: Float, height: Float) {
        drawImage(
            image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset((x * d).roundToInt(), (y * d).roundToInt()),
            dstSize = IntSize((width * d).roundToInt(), (height * d).roundToInt()),
            filterQuality = FilterQuality.Low,
        )
    }

    private companion object {
        fun edgesOf(stops: Int): List<Float> = List(stops) { if (it == 0 || it == stops - 1) 0f else 1f }

        /** A smooth shadow small enough to render on the spot: alpha is a vertical profile times a horizontal one. */
        fun softShadow(width: Int, height: Int, vertical: List<Float>, horizontal: List<Float>): ImageBitmap {
            val image = ImageBitmap(width, height)
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(width.toFloat(), height.toFloat())) {
                drawRect(Brush.verticalGradient(vertical.map { Dusk.copy(alpha = it) }))
                drawRect(Brush.horizontalGradient(horizontal.map { Color.Black.copy(alpha = it) }), blendMode = BlendMode.DstIn)
            }
            return image
        }
    }
}
