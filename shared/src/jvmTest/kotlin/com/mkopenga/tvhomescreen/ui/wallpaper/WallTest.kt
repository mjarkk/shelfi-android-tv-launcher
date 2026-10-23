package com.mkopenga.tvhomescreen.ui.wallpaper

import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.mkopenga.tvhomescreen.Shelf
import com.mkopenga.tvhomescreen.fake.FakeLauncherRepository
import com.mkopenga.tvhomescreen.fake.FakeShelves
import com.mkopenga.tvhomescreen.ui.HomeScreen
import com.mkopenga.tvhomescreen.ui.SceneDriver
import com.mkopenga.tvhomescreen.ui.ShelfCamera
import com.mkopenga.tvhomescreen.ui.ShelfGeometry
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WallTest {
    @Test
    fun theShelfMovingNeverRedrawsTheWall() {
        val controller = CyclingWallpaperController(random = Random(3))
        val bakes = wallBakes
        SceneDriver(content = { HomeScreen(FakeLauncherRepository(), controller) }).use { driver ->
            driver.settle()
            driver.awaitReal { wallBakes > bakes }
            val drawn = wallDraws
            assertTrue(drawn > 0, "the wall never drew")
            driver.repeat(Key.DirectionRight, 10, gapMillis = 33)
            driver.settle()
            driver.press(Key.Back)
            driver.settle()
            assertEquals(drawn, wallDraws, "the wall redrew while the shelf moved")
        }
    }

    @Test
    fun panningAlongALongShelfNeverRedrawsTheWall() {
        val books = List(3) { copy -> FakeShelves.games.map { it.copy(id = "${it.id}-$copy") } }.flatten()
        val controller = CyclingWallpaperController(random = Random(3))
        val camera = ShelfCamera()
        val bakes = wallBakes
        SceneDriver(content = { HomeScreen(FakeLauncherRepository(shelves = listOf(Shelf("all", "All", books))), controller, camera) }).use { driver ->
            driver.settle()
            driver.awaitReal { wallBakes > bakes }
            val drawn = wallDraws
            driver.repeat(Key.DirectionRight, books.size + 1, gapMillis = 33)
            driver.settle()
            assertEquals(ShelfGeometry(books).maxCamera, camera.position, "the room should have panned to its far end")
            driver.press(Key.Back)
            driver.settle()
            assertEquals(0f, camera.position)
            assertEquals(drawn, wallDraws, "the wall redrew while the room panned")
        }
    }

    @Test
    fun movingBetweenTheSettingsFramesNeverRedrawsTheWall() {
        val bakes = wallBakes
        SceneDriver(FakeLauncherRepository()).use { driver ->
            driver.repeat(Key.DirectionRight, FakeShelves.games.size + 1)
            driver.press(Key.DirectionCenter)
            driver.settle()
            driver.awaitReal { wallBakes >= bakes + 1 + 2 }
            driver.settle()
            val drawn = wallDraws
            repeat(3) {
                driver.press(Key.DirectionRight)
                driver.settle()
                driver.press(Key.DirectionLeft)
                driver.settle()
            }
            assertEquals(drawn, wallDraws, "the wall redrew while focus moved between frames")
        }
    }

    @Test
    fun aNewWallpaperCrossfadesInOverSixHundredMillis() {
        val controller = CyclingWallpaperController(random = Random(3))
        lateinit var scene: WallScene
        SceneDriver(content = {
            scene = remember { WallScene(controller) }
            Wall(scene)
        }).use { driver ->
            driver.settle()
            val old = controller.current.value
            val new = Wallpapers.first { it != old }
            controller.select(new.id)
            driver.awaitReal { scene.shown.wallpaper == new }
            driver.advance(150)
            assertEquals(new, scene.shown.wallpaper)
            assertEquals(old, assertNotNull(scene.previous).wallpaper)
            assertTrue(scene.fadeAlpha > 0f && scene.fadeAlpha < 1f, "alpha ${scene.fadeAlpha} 150 ms in")
            driver.advance(550)
            assertNull(scene.previous)
            assertEquals(1f, scene.fadeAlpha)
        }
    }

    @Test
    fun theBakedRoomMatchesTheLiveOne() {
        // A room one screen wide, looking up the wall too, and a long one panned to its start, past its middle and to
        // its end, where the strips of its middle meet its two ends.
        val views = listOf(
            Triple(1920f, 0, 0f), Triple(1920f, 0, 1080f), Triple(1920f, 0, 1700f),
            Triple(4321f, 0, 0f), Triple(4321f, 1137, 0f), Triple(4321f, 2401, 0f), Triple(4321f, 1137, 1700f),
        )
        for ((room, pan, drop) in views) {
            val controller = CyclingWallpaperController(random = Random(5))
            val bakes = wallBakes
            SceneDriver(content = { Wall(remember { WallScene(controller) }, drop = { drop }, pan = { pan }, roomWidth = { room }) }).use { driver ->
                val live = driver.frame().toComposeImageBitmap().toPixelMap()
                driver.awaitReal { wallBakes >= bakes + (if (drop > 0f) 3 else 1) }
                val baked = driver.frame().toComposeImageBitmap().toPixelMap()
                var worst = 0f
                for (y in 0 until live.height step 7) for (x in 0 until live.width step 3) {
                    val a = live[x, y]
                    val b = baked[x, y]
                    worst = maxOf(worst, abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue))
                }
                assertTrue(worst < 0.04f, "baking changed the wall $room wide, panned $pan across and $drop down, by up to $worst")
            }
        }
    }

    @Test
    fun aLongRoomsLightRunsOnWithoutASeam() {
        val screen = IntSize(1920, 1080)
        val room = 4321
        for (band in 0..2) {
            val height = if (band == 0) 1200 else 1080
            val image = ImageBitmap(room, height)
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(room.toFloat(), height.toFloat())) {
                with(RoomLight(1f)) { drawStretched(band, room, screen, 0, height, 0, room) }
            }
            val pixels = image.toPixelMap()
            var worst = 0f
            var at = 0
            for (y in 0 until height step 3) {
                for (x in 0 until room - 1) {
                    val a = pixels[x, y]
                    val b = pixels[x + 1, y]
                    val d = maxOf(abs(a.alpha - b.alpha), abs(a.red * a.alpha - b.red * b.alpha), abs(a.blue * a.alpha - b.blue * b.alpha))
                    if (d > worst) {
                        worst = d
                        at = x
                    }
                }
            }
            assertTrue(worst <= 3.5f / 255, "band $band: neighbouring columns at x=$at differ by ${worst * 255}/255")
        }
    }

    /** Baking runs on a real background thread, so wait in real time, rendering frames meanwhile. */
    private fun SceneDriver.awaitReal(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
            frame()
        }
    }

    @Test
    fun tilesRepeatWithoutASeam() {
        for (pattern in Patterns) {
            for (scale in listOf(1f, 1.37f)) {
                val pixels = pattern.render(scale).toPixelMap()
                val w = pixels.width
                val h = pixels.height
                fun diff(a: Color, b: Color) = abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)
                val inside = (0 until h).sumOf { y -> (0 until w - 1).sumOf { x -> diff(pixels[x, y], pixels[x + 1, y]).toDouble() } } / (h * (w - 1))
                val across = (0 until h).sumOf { y -> diff(pixels[w - 1, y], pixels[0, y]).toDouble() } / h
                assertTrue(across < inside * 2.5 + 0.01, "${pattern.wallpaper.id} at $scale: left/right edges differ by $across, neighbours by $inside")
                val insideV = (0 until w).sumOf { x -> (0 until h - 1).sumOf { y -> diff(pixels[x, y], pixels[x, y + 1]).toDouble() } } / (w * (h - 1))
                val acrossV = (0 until w).sumOf { x -> diff(pixels[x, h - 1], pixels[x, 0]).toDouble() } / w
                assertTrue(acrossV < insideV * 2.5 + 0.01, "${pattern.wallpaper.id} at $scale: top/bottom edges differ by $acrossV, neighbours by $insideV")
            }
        }
    }
}
