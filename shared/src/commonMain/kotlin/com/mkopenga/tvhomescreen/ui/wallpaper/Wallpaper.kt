package com.mkopenga.tvhomescreen.ui.wallpaper

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.lerp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.max
import kotlin.math.roundToInt

@Immutable
data class Wallpaper(val id: String, val name: String)

/** Main thread only. [current] changes on [select] and on the hourly auto-cycle. */
@Stable
interface WallpaperController {
    val wallpapers: List<Wallpaper>
    val current: StateFlow<Wallpaper>
    fun select(id: String)

    /** The wallpaper the next automatic change will show, if known, so its art can be prepared ahead. */
    fun upcoming(): Wallpaper? = null
}

val LocalWallpaperController = staticCompositionLocalOf<WallpaperController> { PlainWallpaper }

/** How many design px of wall a thumbnail's height shows: a close-up, so the motifs read at picker size. */
private const val ThumbnailWallHeight = 380f

/** A miniature of [wallpaper] for pickers, filling [modifier]'s bounds: its pattern above a strip of its panelling. */
@Composable
fun WallpaperThumbnail(wallpaper: Wallpaper, modifier: Modifier = Modifier) {
    val pattern = patternOf(wallpaper)
    Spacer(
        modifier.drawWithCache {
            val tile = ThumbnailTiles.of(pattern, (size.height / ThumbnailWallHeight).coerceIn(0.15f, 2f))
            val paper = ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
            val dado = size.height * 0.84f
            val rail = max(1f, size.height * 0.018f)
            val light = Brush.radialGradient(
                listOf(Color(0xFFFFE9B5).copy(alpha = 0.08f), Color.Transparent, Color(0xFF2A1609).copy(alpha = 0.3f)),
                center = Offset(size.width / 2, size.height * 0.35f),
                radius = size.maxDimension * 0.8f,
            )
            onDrawBehind {
                drawRect(paper, size = Size(size.width, dado))
                drawRect(pattern.wainscot, Offset(0f, dado), Size(size.width, size.height - dado))
                drawRect(lerp(pattern.wainscot, Tone.Paper, 0.16f), Offset(0f, dado), Size(size.width, rail))
                drawRect(light)
            }
        },
    )
}

/** Small tiles for thumbnails, kept for good: ten of them at picker size are a few hundred KB. Main thread only. */
private object ThumbnailTiles {
    private val tiles = HashMap<Pair<String, Int>, ImageBitmap>()

    fun of(pattern: Pattern, scale: Float): ImageBitmap {
        val key = pattern.wallpaper.id to (scale * 100).roundToInt()
        return tiles.getOrPut(key) { pattern.render(key.second / 100f) }
    }
}

private object PlainWallpaper : WallpaperController {
    override val wallpapers = listOf(PlainPattern.wallpaper)
    override val current: StateFlow<Wallpaper> = MutableStateFlow(PlainPattern.wallpaper)
    override fun select(id: String) {}
}
