package com.mkopenga.tvhomescreen.ui.settings

import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.mkopenga.tvhomescreen.Shelf
import com.mkopenga.tvhomescreen.ShelfItem
import com.mkopenga.tvhomescreen.fake.FakeLauncherRepository
import com.mkopenga.tvhomescreen.fake.FakeShelves
import com.mkopenga.tvhomescreen.ui.HomeScreen
import com.mkopenga.tvhomescreen.ui.Palette
import com.mkopenga.tvhomescreen.ui.SceneDriver
import com.mkopenga.tvhomescreen.ui.wallpaper.CyclingWallpaperController
import com.mkopenga.tvhomescreen.ui.wallpaper.Tone
import com.mkopenga.tvhomescreen.ui.wallpaper.Wallpapers
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import java.io.File
import kotlin.random.Random

/** The ducks on the shelf in each palette, so one can be picked, and a sheet of each palette's duck. */
internal fun renderDuckSnapshots(dir: File) {
    renderDuckSheets(dir)
    val long = List(3) { copy -> FakeShelves.games.map { it.copy(id = "${it.id}-$copy", title = "${it.title} ${copy + 1}") } }.flatten()
    for (colours in DuckColours.entries) {
        val name = colours.name.lowercase()
        fun SceneDriver.save(shot: String) = save(File(dir, "duck-$name-$shot.png"))
        onTheWall(colours, long) {
            settle()
            save("01-start-first-book-out")
            press(Key.DirectionRight)
            settle()
            save("02-start-hugging-the-first-book")
            repeat(Key.DirectionRight, long.size, gapMillis = 33)
            settle()
            repeat(Key.DirectionLeft, 2, gapMillis = 120)
            settle()
            save("03-end-at-rest")
            repeat(Key.DirectionRight, 2, gapMillis = 120)
            settle()
            save("04-focused")
            press(Key.DirectionLeft)
            saveFrames(File(dir, "duck-$name-05-last-book-turning-out-frames.png"))
            settle()
            save("06-last-book-out")
            press(Key.DirectionRight)
            saveFrames(File(dir, "duck-$name-07-last-book-turning-back-frames.png"), skip = 8)
            settle()
        }
        onTheWall(colours, FakeShelves.games.take(8)) {
            settle()
            save("08-short-shelf")
            repeat(Key.DirectionRight, 8, gapMillis = 120)
            settle()
            save("09-short-shelf-focused")
        }
    }
    // The Duck frame in settings, in one colour and then, after OK, the next; and the shelf below it after the change.
    onTheWall(DuckColours.entries.first(), FakeShelves.games) {
        repeat(Key.DirectionRight, FakeShelves.games.size + 1)
        press(Key.DirectionCenter)
        repeat(Key.DirectionRight, 2)
        for ((k, colours) in DuckColours.entries.withIndex()) {
            if (k > 0) press(Key.DirectionCenter)
            settle()
            save(File(dir, "duck-${10 + k}-frame-${colours.name.lowercase()}.png"))
        }
        press(Key.Back)
        settle()
        save(File(dir, "duck-${10 + DuckColours.entries.size}-shelf-end-after-switching-to-${DuckColours.entries.last().name.lowercase()}.png"))
    }
}

/**
 * The next frames after a key press, one after another (after [skip] frames), cropped to the end of the shelf: the
 * last book and the duck. Read left to right, top to bottom.
 */
private fun SceneDriver.saveFrames(file: File, skip: Int = 0, count: Int = 8) {
    repeat(skip) { frame() }
    val crop = Rect.makeXYWH(860f, 330f, 1060f, 480f)
    val scale = 0.5f
    val w = crop.width * scale
    val h = crop.height * scale
    val surface = Surface.makeRasterN32Premul((w * 4).toInt(), (h * ((count + 3) / 4)).toInt())
    repeat(count) { k ->
        val image = frame()
        surface.canvas.drawImageRect(image, crop, Rect.makeXYWH((k % 4) * w, (k / 4) * h, w, h), SamplingMode.LINEAR, null, true)
    }
    file.writeBytes(requireNotNull(surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)).bytes)
}

internal fun renderDuckSheets(dir: File) {
    for (colours in DuckColours.entries) {
        renderDuckSheet(colours, File(dir, "duck-00-sheet-${colours.name.lowercase()}.png"))
    }
}

/** On the same light wallpaper every time, the hardest one for a white duck to stand out on. */
private fun onTheWall(colours: DuckColours, books: List<ShelfItem>, block: SceneDriver.() -> Unit) = SceneDriver(content = {
    val controller = remember { CyclingWallpaperController(random = Random(0)).apply { select(Wallpapers.first().id) } }
    val repository = remember { FakeLauncherRepository(shelves = listOf(Shelf("all", "All", books)), duckColour = colours.name) }
    HomeScreen(repository, controller)
}).use(block)

/** The duck at rest, half focused and focused, and its mirror image, each holding a spine, at 1.5x. */
private fun renderDuckSheet(colours: DuckColours, file: File) {
    val scale = 1.5f
    val cell = 340f
    val focuses = listOf(0f, 0.5f, 1f)
    val width = (cell * (focuses.size + 1) * scale).toInt()
    val height = (520 * scale).toInt()
    val image = ImageBitmap(width, height)
    val end = DuckArt(scale, colours, mirrored = false, focusable = true)
    val start = DuckArt(scale, colours, mirrored = true, focusable = false)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(width.toFloat(), height.toFloat())) {
        drawRect(Tone.Ivory)
        val plank = 440f * scale
        val top = plank - BookendHeight * scale
        val spine = Size(84f * scale, 500f * scale)
        for ((i, focus) in focuses.withIndex()) {
            val x = (i * cell + 110f) * scale
            with(end) { draw(x, top, focus) }
            drawRoundRect(Color(0xFF5B3A8C), Offset(x - spine.width, plank - spine.height), spine, CornerRadius(4f * scale))
            with(end) { drawWingTip(x, top, x - spine.width, x) }
        }
        val x = (focuses.size * cell + 20f) * scale
        val books = x + BookendWidth * scale
        with(start) { draw(x, top, 0f) }
        drawRoundRect(Color(0xFF8C2F2F), Offset(books, plank - spine.height), spine, CornerRadius(4f * scale))
        with(start) { drawWingTip(x, top, books, books + spine.width) }
        drawRect(Palette.Plank, Offset(0f, plank), Size(width.toFloat(), 28f * scale))
        drawRect(Color(0xFF2F4A3C), Offset(0f, plank + 28f * scale), Size(width.toFloat(), height - plank))
    }
    file.parentFile.mkdirs()
    file.writeBytes(requireNotNull(Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)).bytes)
}
