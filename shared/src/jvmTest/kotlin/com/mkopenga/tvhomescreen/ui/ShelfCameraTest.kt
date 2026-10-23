package com.mkopenga.tvhomescreen.ui

import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import com.mkopenga.tvhomescreen.Shelf
import com.mkopenga.tvhomescreen.ShelfItem
import com.mkopenga.tvhomescreen.fake.FakeLauncherRepository
import com.mkopenga.tvhomescreen.fake.FakeShelves
import com.mkopenga.tvhomescreen.ui.wallpaper.CyclingWallpaperController
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShelfCameraTest {
    private val long = List(3) { copy -> FakeShelves.games.map { it.copy(id = "${it.id}-$copy") } }.flatten()
    private val fits = FakeShelves.games.take(8)

    private fun scene(books: List<ShelfItem>, camera: ShelfCamera, repository: FakeLauncherRepository = shelfOf(books)) = SceneDriver(content = {
        HomeScreen(repository, remember { CyclingWallpaperController(random = Random(3)) }, camera)
    })

    private fun shelfOf(books: List<ShelfItem>) = FakeLauncherRepository(shelves = listOf(Shelf("all", "All", books)))

    private fun SceneDriver.hold(camera: ShelfCamera, key: Key, presses: Int, gap: Int): List<Float> {
        val trace = mutableListOf(camera.position)
        repeat(presses) {
            press(key)
            repeat(gap) { frame(); trace += camera.position }
        }
        repeat(90) { frame(); trace += camera.position }
        return trace
    }

    @Test
    fun aHeldDpadPansSteadilyOneWayAndStopsWithoutOvershooting() {
        for (gap in listOf(2, 3)) {
            val camera = ShelfCamera()
            scene(long, camera).use { driver ->
                driver.settle()
                val geometry = ShelfGeometry(long)
                val trace = driver.hold(camera, Key.DirectionRight, long.size + 1, gap)
                val steps = trace.zipWithNext { a, b -> b - a }
                assertEquals(geometry.maxCamera, trace.last(), "at the end of the room")
                assertTrue(steps.all { it >= 0f }, "the camera went back while the D-pad held right (repeat every $gap frames)")
                assertTrue(trace.all { it <= geometry.maxCamera }, "the camera overshot its rest")
                // No stalls or lurches: frame to frame, the speed changes by a fraction of the top speed.
                val top = steps.max()
                val jerk = steps.zipWithNext { a, b -> abs(b - a) }.max()
                assertTrue(jerk < 0.2f * top, "the camera's step changed by $jerk px in a frame, at a top speed of $top px a frame (repeat every $gap frames)")
            }
        }
    }

    @Test
    fun aShelfThatFitsNeverMovesTheCamera() {
        val camera = ShelfCamera()
        scene(fits, camera).use { driver ->
            driver.settle()
            val there = driver.hold(camera, Key.DirectionRight, fits.size + 1, 2)
            val back = driver.hold(camera, Key.DirectionLeft, fits.size + 1, 2)
            assertTrue((there + back).all { it == 0f })
        }
    }

    @Test
    fun focusMovesFreelyUntilItNeedsRoom() {
        val camera = ShelfCamera()
        scene(long, camera).use { driver ->
            driver.settle()
            driver.repeat(Key.DirectionRight, 8, gapMillis = 120)
            driver.settle()
            assertEquals(0f, camera.position, "eight books in, focus still has room")
            driver.repeat(Key.DirectionRight, 8, gapMillis = 120)
            driver.settle()
            val panned = camera.position
            assertTrue(panned > 0f)
            driver.repeat(Key.DirectionLeft, 5, gapMillis = 120)
            driver.settle()
            assertEquals(panned, camera.position, "turning back stays inside the view")
        }
    }

    @Test
    fun homeTakesTheCameraBackToTheStart() {
        val camera = ShelfCamera()
        val repository = shelfOf(long)
        scene(long, camera, repository).use { driver ->
            driver.repeat(Key.DirectionRight, long.size, gapMillis = 33)
            driver.settle()
            assertTrue(camera.position > 0f)
            repository.pressHome()
            driver.settle()
            assertEquals(0f, camera.position)
        }
    }

    @Test
    fun settingsOpenAboveTheFarEndAndBackReturnsThere() {
        val camera = ShelfCamera()
        scene(long, camera).use { driver ->
            driver.repeat(Key.DirectionRight, long.size + 1, gapMillis = 33)
            driver.settle()
            val end = camera.position
            assertEquals(ShelfGeometry(long).maxCamera, end)
            driver.press(Key.DirectionUp)
            driver.settle()
            assertEquals(end, camera.position, "settings opened")
            driver.press(Key.Back)
            driver.settle()
            assertEquals(end, camera.position, "back on the shelf")
        }
    }
}
