package com.mkopenga.tvhomescreen.ui.settings

import androidx.compose.ui.input.key.Key
import com.mkopenga.tvhomescreen.LauncherRepository
import com.mkopenga.tvhomescreen.fake.FakeLauncherRepository
import com.mkopenga.tvhomescreen.fake.FakeShelves
import com.mkopenga.tvhomescreen.ui.HomeScreen
import com.mkopenga.tvhomescreen.ui.SceneDriver
import com.mkopenga.tvhomescreen.ui.wallpaper.CyclingWallpaperController
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/** Settings driven end to end through the root key handler, with a remote's keys and a gamepad's A and B. */
class SettingsSceneTest {
    private class Recording(private val fake: FakeLauncherRepository = FakeLauncherRepository()) : LauncherRepository by fake {
        var systemSettingsOpened = 0

        override fun openSystemSettings() {
            systemSettingsOpened++
        }
    }

    private fun SceneDriver.toFrames() {
        repeat(Key.DirectionRight, FakeShelves.games.size + 1)
        press(Key.DirectionCenter)
        settle()
    }

    @Test
    fun aRemoteHidesAnAppAndOpensSystemSettings() {
        val repository = Recording()
        SceneDriver(repository).use { driver ->
            driver.toFrames()
            driver.press(Key.DirectionRight)
            driver.press(Key.ButtonA)
            driver.press(Key.DirectionCenter)
            driver.settle()
            assertEquals(setOf("bloom"), repository.hiddenIds.value)

            driver.press(Key.ButtonB)
            driver.repeat(Key.DirectionRight, 2)
            driver.press(Key.Enter)
            assertEquals(1, repository.systemSettingsOpened)
        }
    }

    @Test
    fun aRemoteChangesTheDucksColourAndTheRepositoryKeepsIt() {
        val repository = FakeLauncherRepository()
        SceneDriver(repository).use { driver ->
            driver.toFrames()
            driver.repeat(Key.DirectionRight, 2)
            driver.press(Key.DirectionCenter)
            assertEquals("White", repository.duckColour.value)
            driver.press(Key.ButtonA)
            assertEquals("Yellow", repository.duckColour.value)
        }
    }

    @Test
    fun aRemotePicksTheNextWallpaper() {
        val controller = CyclingWallpaperController(random = Random(0))
        val start = controller.wallpapers.indexOf(controller.current.value)
        val next = controller.wallpapers[(start + 1).coerceAtMost(controller.wallpapers.lastIndex)]
        SceneDriver(content = { HomeScreen(FakeLauncherRepository(), controller) }).use { driver ->
            driver.toFrames()
            driver.press(Key.DirectionCenter)
            driver.press(Key.DirectionRight)
            driver.press(Key.DirectionCenter)
            driver.settle()
            assertEquals(next, controller.current.value)
        }
    }
}
