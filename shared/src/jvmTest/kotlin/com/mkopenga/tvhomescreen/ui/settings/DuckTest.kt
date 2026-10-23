package com.mkopenga.tvhomescreen.ui.settings

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.mkopenga.tvhomescreen.fake.FakeLauncherRepository
import com.mkopenga.tvhomescreen.fake.FakeShelves
import com.mkopenga.tvhomescreen.ui.SceneDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DuckTest {
    @Test
    fun theDucksAreNotBakedAgainWhileFocusMovesAroundThemOrSettingsReopenOrTheShelfChanges() {
        val repository = FakeLauncherRepository()
        val before = duckBakes
        SceneDriver(repository).use { driver ->
            // Every duck drawn once: both on the shelf, and the one in the Duck frame.
            driver.repeat(Key.DirectionRight, FakeShelves.games.size + 2)
            driver.press(Key.DirectionUp)
            driver.settle()
            driver.press(Key.Back)
            driver.settle()
            val baked = duckBakes
            assertTrue(baked > before, "the ducks should have been baked")

            driver.press(Key.DirectionLeft)
            driver.settle()
            driver.press(Key.DirectionRight)
            driver.settle()
            driver.press(Key.DirectionUp)
            driver.settle()
            driver.press(Key.Back)
            driver.settle()
            repository.setHidden(FakeShelves.games[3].id, hidden = true)
            driver.settle()
            driver.press(Key.Back)
            driver.settle()
            assertEquals(baked, duckBakes, "a duck was baked again")
        }
    }

    @Test
    fun aNewColourBakesTheDucksOnceAndThenNeverAgainWhileFocusMoves() {
        SceneDriver(FakeLauncherRepository()).use { driver ->
            driver.repeat(Key.DirectionRight, FakeShelves.games.size + 1)
            driver.press(Key.DirectionCenter)
            driver.repeat(Key.DirectionRight, 2)
            driver.settle()

            val before = duckBakes
            driver.press(Key.DirectionCenter)
            driver.frame()
            val once = duckBakes - before
            assertTrue(once > 0, "the new colour should have been baked")
            driver.settle()
            assertEquals(before + once, duckBakes, "frames after the change baked again")
            driver.press(Key.DirectionCenter)
            driver.settle()
            assertEquals(before + 2 * once, duckBakes, "the second change should bake as much as the first")

            driver.press(Key.Back)
            driver.settle()
            val baked = duckBakes
            driver.press(Key.DirectionLeft)
            driver.settle()
            driver.press(Key.DirectionRight)
            driver.settle()
            driver.press(Key.Back)
            driver.settle()
            assertEquals(baked, duckBakes, "moving focus with the colour unchanged baked again")
        }
    }

    @Test
    fun focusChangesNoOpaquePixelOfTheDuckButItsFace() {
        for (colours in DuckColours.entries) {
            val duck = DuckArt(1f, colours, mirrored = false, focusable = true)
            val rest = render { with(duck) { draw(Left, Top, focus = 0f) } }
            val focused = render { with(duck) { draw(Left, Top, focus = 1f) } }
            var face = 0
            for (y in 0 until rest.height) for (x in 0 until rest.width) {
                if (rest[x, y] == focused[x, y]) continue
                val inFace = FaceBox.contains(Offset(x - Left, y - Top))
                if (inFace) face++
                // The ring and its glow show round the duck and through the soft edges of what lies in front of them.
                assertTrue(inFace || rest[x, y].alpha < 1f, "$colours: focus changed the duck itself at $x,$y")
            }
            assertTrue(face > 0, "$colours: the face should change with focus")
        }
    }

    @Test
    fun theWingTipIsDrawnOnlyOverThePartOfTheSpineStillFacingOut() {
        val duck = DuckArt(1f, DuckColours.Yellow, mirrored = false, focusable = true)
        val spine = render { with(duck) { drawWingTip(Left, Top, Left - 84f, Left) } }
        val covered = render { with(duck) { drawWingTip(Left, Top, Left - 84f, Left - 84f) } }
        val half = render { with(duck) { drawWingTip(Left, Top, Left - 84f, Left - WingReach / 2) } }
        val drawn = { pixels: PixelMap -> (0 until pixels.height).flatMap { y -> (0 until pixels.width).filter { x -> pixels[x, y].alpha > 0f } } }
        assertTrue(drawn(spine).isNotEmpty() && drawn(spine).all { it < Left && it >= Left - WingReach - 4 }, "the tip lies over the spine, no further")
        assertTrue(drawn(covered).isEmpty(), "a cover turned out over the spine hides the tip")
        assertTrue(drawn(half).isNotEmpty() && drawn(half).all { it < Left - WingReach / 2 }, "the tip shows only where the spine still faces out")
    }

    private fun render(draw: DrawScope.() -> Unit): PixelMap {
        val image = ImageBitmap(420, 460)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(420f, 460f), draw)
        return image.toPixelMap()
    }

    private companion object {
        const val Left = 100f
        const val Top = 80f
    }
}
