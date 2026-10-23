package com.mkopenga.tvhomescreen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mkopenga.tvhomescreen.Artwork
import com.mkopenga.tvhomescreen.LauncherRepository
import com.mkopenga.tvhomescreen.Shelf
import com.mkopenga.tvhomescreen.fake.FakeLauncherRepository
import com.mkopenga.tvhomescreen.fake.FakeShelves
import com.mkopenga.tvhomescreen.ui.settings.renderDuckSnapshots
import com.mkopenga.tvhomescreen.ui.settings.renderSettingsSnapshots
import com.mkopenga.tvhomescreen.ui.wallpaper.renderWallpaperSnapshots
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import kotlin.test.Test

/**
 * Writes PNGs of the home screen to compare against design/Shelf.dc.html. Excluded from `jvmTest`; run it with
 * `mise run snapshot` (the `:shared:snapshot` task), which sets the output directory.
 */
class SnapshotRenderer {
    private val dir = File(requireNotNull(System.getProperty("shelfi.snapshotDir")) { "run via :shared:snapshot" })

    @Test
    fun render() {
        dir.mkdirs()
        dir.listFiles { f -> f.extension == "png" && !f.name.startsWith("emulator-") }?.forEach(File::delete)

        SceneDriver(FakeLauncherRepository()).use {
            it.settle()
            it.save("01-start")
        }
        fromFern { save("02-fern") }
        fromFern {
            repeat(Key.DirectionRight, 3)
            settle()
            save("03-focus-right-3")
        }
        fromFern {
            press(Key.DirectionRight)
            advance(120)
            save("04-mid-turn")
        }
        fromFern {
            repeat(Key.DirectionRight, 6, gapMillis = 40)
            save("05-held-dpad")
        }
        SceneDriver(WithShelves(emptyList())).use {
            it.settle()
            it.save("06-empty")
        }
        longShelf()
        SceneDriver(WithShelves(listOf(Shelf("short", "Short", FakeShelves.games.take(8))))).use {
            it.settle()
            it.save("08-short-01-start")
            it.repeat(Key.DirectionRight, 8, gapMillis = 33)
            it.settle()
            it.save("08-short-02-bookend")
        }
        SceneDriver(WithShelves(FakeShelves.all, artwork = true)).use {
            it.settle()
            it.save("09-artwork-banner")
            it.press(Key.DirectionRight)
            it.settle()
            it.save("10-artwork-icon")
        }
        coverSheet()
        renderBackdropSnapshots(dir)
        renderSettingsSnapshots(dir)
        renderDuckSnapshots(dir)
        renderWallpaperSnapshots(dir)
    }

    /** Focus on Fern and the Deep Wood, the mockup's cover; it's second because it was played yesterday. */
    private fun fromFern(block: SceneDriver.() -> Unit) = SceneDriver(FakeLauncherRepository()).use {
        it.press(Key.DirectionRight)
        it.settle()
        it.block()
    }

    private fun SceneDriver.save(name: String) = save(File(dir, "$name.png"))

    private fun longShelf() {
        val books = List(3) { copy -> FakeShelves.games.map { it.copy(id = "${it.id}-$copy", title = "${it.title} ${copy + 1}") } }.flatten()
        SceneDriver(WithShelves(listOf(Shelf("all", "All", books)))).use {
            it.settle()
            it.save("07-long-01-start")
            it.repeat(Key.DirectionRight, 9, gapMillis = 33)
            it.settle()
            it.save("07-long-02-focus-moved-room-still")
            it.repeat(Key.DirectionRight, 6, gapMillis = 33)
            it.save("07-long-03-mid-pan")
            it.settle()
            it.save("07-long-04-mid")
            it.repeat(Key.DirectionRight, books.size - 16, gapMillis = 33)
            it.settle()
            it.save("07-long-05-last-book")
            it.press(Key.DirectionRight)
            it.settle()
            it.save("07-long-06-bookend")
            it.repeat(Key.DirectionLeft, 3, gapMillis = 120)
            it.settle()
            it.save("07-long-07-back-three")
        }
    }

    /** Every procedural cover at full expansion, 0.65x, for reviewing the art in one place. */
    private fun coverSheet() {
        val items = FakeShelves.games
        SceneDriver(width = 1920, height = 770, density = 0.65f, content = {
            val typography = rememberTypography()
            val measurer = rememberTextMeasurer(cacheSize = 0)
            val artworks = remember { ArtworkStore(FakeLauncherRepository()) }
            Column(
                Modifier.fillMaxSize().background(Palette.Background).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                for (row in items.chunked(7)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        for (item in row) {
                            val painter = remember(item) { ItemPainter(item, typography, measurer) }
                            Spacer(Modifier.size(CoverWidth.dp, CoverHeight.dp).drawBehind { painter.drawCover(this, artworks) })
                        }
                    }
                }
            }
        }).use { it.save("11-covers") }
    }
}

/** The fake shelves, optionally replaced, optionally with generated banners and icons for every other item. */
private class WithShelves(shelves: List<Shelf>, private val artwork: Boolean = false) : LauncherRepository by FakeLauncherRepository() {
    override val shelves: StateFlow<List<Shelf>> = MutableStateFlow(shelves)
    private val ids = FakeShelves.games.map { it.id }

    override suspend fun artwork(itemId: String): Artwork? {
        if (!artwork) return null
        return when (ids.indexOf(itemId) % 2) {
            0 -> Artwork(icon = generated(384, 384, itemId), banner = null)
            else -> Artwork(icon = null, banner = generated(640, 360, itemId))
        }
    }

    private fun generated(width: Int, height: Int, seed: String): ImageBitmap {
        val image = ImageBitmap(width, height)
        val hue = (stableHash(seed).mod(360)).toFloat()
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(width.toFloat(), height.toFloat())) {
            drawLogo(Color.hsv(hue, 0.55f, 0.5f), Color.hsv((hue + 40f) % 360f, 0.6f, 0.95f))
        }
        return image
    }

    private fun DrawScope.drawLogo(dark: Color, light: Color) {
        drawRoundRect(Brush.linearGradient(listOf(dark, light)), cornerRadius = CornerRadius(size.minDimension * 0.18f))
        drawCircle(Color.White.copy(alpha = 0.9f), size.minDimension * 0.22f, Offset(size.width * 0.35f, size.height / 2))
        drawRoundRect(
            Color.White.copy(alpha = 0.9f),
            topLeft = Offset(size.width * 0.55f, size.height * 0.38f),
            size = Size(size.width * 0.3f, size.height * 0.24f),
            cornerRadius = CornerRadius(size.height * 0.06f),
        )
    }
}
