package com.mkopenga.tvhomescreen.ui.settings

import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.mkopenga.tvhomescreen.Artwork
import com.mkopenga.tvhomescreen.LauncherRepository
import com.mkopenga.tvhomescreen.Shelf
import com.mkopenga.tvhomescreen.fake.FakeLauncherRepository
import com.mkopenga.tvhomescreen.fake.FakeShelves
import com.mkopenga.tvhomescreen.ui.HomeScreen
import com.mkopenga.tvhomescreen.ui.SceneDriver
import com.mkopenga.tvhomescreen.ui.stableHash
import com.mkopenga.tvhomescreen.ui.wallpaper.CyclingWallpaperController
import com.mkopenga.tvhomescreen.ui.wallpaper.Wallpaper
import com.mkopenga.tvhomescreen.ui.wallpaper.Wallpapers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import kotlin.random.Random

/** The bookend, the frames, the wallpaper picker and the hide-apps page, driven by keys as on a remote. */
internal fun renderSettingsSnapshots(dir: File) {
    fun SceneDriver.save(name: String) = save(File(dir, "settings-$name.png"))

    onTheWall(WithIcons()) {
        repeat(Key.DirectionRight, FakeShelves.games.size + 1)
        settle()
        save("01-bookend")
        press(Key.DirectionCenter)
        advance(160)
        save("02-panning-up")
        settle()
        save("03-frames")
        press(Key.DirectionRight)
        settle()
        save("04-frame-hidden-apps")
        press(Key.DirectionRight)
        settle()
        save("05-frame-duck")
        press(Key.DirectionRight)
        settle()
        save("05-frame-system")

        repeat(Key.DirectionLeft, 3)
        press(Key.DirectionCenter)
        advance(160)
        save("06-panning-up-to-wallpapers")
        settle()
        save("07-wallpaper-picker")
        repeat(Key.DirectionRight, 3)
        settle()
        save("08-wallpaper-picker-browsed")
        press(Key.Back)
        advance(160)
        save("09-panning-down-to-frames")
        settle()

        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        settle()
        save("10-hide-apps")
        press(Key.DirectionCenter)
        repeat(Key.DirectionRight, 2)
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        settle()
        save("11-hide-apps-three-hidden")

        press(Key.Back)
        press(Key.Back)
        settle()
        save("12-shelf-with-hidden-apps")
    }
    val many = List(3) { copy -> FakeShelves.games.map { it.copy(id = "${it.id}-$copy", title = "${it.title} ${copy + 1}") } }.flatten()
    onTheWall(WithIcons(listOf(Shelf("all", "All", many)))) {
        repeat(Key.DirectionRight, many.size + 1)
        settle()
        press(Key.DirectionCenter)
        advance(160)
        save("13-panning-up-from-the-far-end")
        settle()
        save("14-frames-above-the-far-end")
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        settle()
        repeat(Key.DirectionDown, 5)
        advance(60)
        save("15-hide-apps-scrolling")
        settle()
        save("16-hide-apps-scrolled")
        press(Key.Back)
        press(Key.Back)
        settle()
        save("17-back-at-the-far-end")
    }
    onTheWall(FakeLauncherRepository(hidden = FakeShelves.games.map { it.id }.toSet())) {
        settle()
        save("18-every-app-hidden")
    }
    for (wallpaper in listOf(Wallpapers[5], Wallpapers.last())) {
        onTheWall(FakeLauncherRepository(), wallpaper) {
            repeat(Key.DirectionRight, FakeShelves.games.size + 1)
            press(Key.DirectionCenter)
            settle()
            save("19-frames-on-${wallpaper.id}")
            press(Key.DirectionCenter)
            settle()
            save("20-picker-on-${wallpaper.id}")
        }
    }
}

/** The fake apps (or [apps]), each with a generated icon, as an Android TV repository would give them. */
private class WithIcons(apps: List<Shelf>? = null, private val fake: FakeLauncherRepository = FakeLauncherRepository()) : LauncherRepository by fake {
    override val shelves: StateFlow<List<Shelf>> = if (apps == null) fake.shelves else MutableStateFlow(apps)

    override suspend fun artwork(itemId: String): Artwork = Artwork(icon = icon(itemId), banner = null)

    private fun icon(seed: String): ImageBitmap {
        val image = ImageBitmap(192, 192)
        val hue = stableHash(seed).mod(360).toFloat()
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(192f, 192f)) {
            drawRoundRect(
                Brush.linearGradient(listOf(Color.hsv(hue, 0.6f, 0.45f), Color.hsv((hue + 50f) % 360f, 0.55f, 0.95f))),
                cornerRadius = CornerRadius(36f),
            )
            drawCircle(Color.White.copy(alpha = 0.92f), 44f, Offset(76f, 96f))
            drawRoundRect(Color.White.copy(alpha = 0.92f), Offset(106f, 70f), Size(50f, 52f), CornerRadius(12f))
        }
        return image
    }
}

/** A fixed wallpaper and a many-app shelf-sized catalogue, so the pictures are the same on every run. */
private fun onTheWall(repository: LauncherRepository, wallpaper: Wallpaper = Wallpapers.first(), block: SceneDriver.() -> Unit) = SceneDriver(content = {
    val controller = remember { CyclingWallpaperController(random = Random(0)).apply { select(wallpaper.id) } }
    HomeScreen(repository, controller)
}).use(block)
