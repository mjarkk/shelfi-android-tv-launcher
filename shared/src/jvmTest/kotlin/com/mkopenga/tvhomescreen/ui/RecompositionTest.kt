package com.mkopenga.tvhomescreen.ui

import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.tooling.CompositionObserver
import androidx.compose.runtime.tooling.CompositionObserverHandle
import androidx.compose.runtime.tooling.CompositionRegistrationObserver
import androidx.compose.runtime.tooling.ObservableComposition
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.mkopenga.tvhomescreen.LauncherRepository
import com.mkopenga.tvhomescreen.Shelf
import com.mkopenga.tvhomescreen.fake.FakeLauncherRepository
import com.mkopenga.tvhomescreen.fake.FakeShelves
import com.mkopenga.tvhomescreen.ui.wallpaper.CyclingWallpaperController
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import kotlinx.coroutines.CompletableDeferred
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The shelf animates in layout and draw only: frames between key presses must not recompose anything. */
class RecompositionTest {
    @Test
    fun aGlideRecomposesOnlyOnTheFrameAfterEachKeyPress() {
        SceneDriver(FakeLauncherRepository()).use { driver ->
            driver.settle()
            RecompositionCounter().use { counter ->
                driver.press(Key.DirectionRight)
                driver.frame()
                assertTrue(counter.take() > 0, "the info line should recompose for the new focus (is the counter hooked up?)")

                val moving = (0 until 12).map { driver.frame().fingerprint() }
                assertTrue(moving.zipWithNext().any { (a, b) -> a != b }, "the shelf should still be moving")
                assertEquals(0, counter.take(), "frames of a glide recomposed")

                // A held D-pad: a press every other frame for a second, still only recomposing right after presses.
                repeat(30) {
                    driver.press(Key.DirectionRight)
                    driver.frame()
                    counter.take()
                    driver.frame()
                    assertEquals(0, counter.take(), "a frame between key repeats recomposed")
                }
            }
        }
    }

    @Test
    fun holdingTheDpadAcrossALongShelfPansTheRoomAndRecomposesOnlyOnKeyFrames() {
        val books = List(3) { copy -> FakeShelves.games.map { it.copy(id = "${it.id}-$copy") } }.flatten()
        val camera = ShelfCamera()
        SceneDriver(content = {
            HomeScreen(FakeLauncherRepository(shelves = listOf(Shelf("all", "All", books))), remember { CyclingWallpaperController(random = Random(3)) }, camera)
        }).use { driver ->
            driver.settle()
            RecompositionCounter().use { counter ->
                var panning = 0
                repeat(books.size + 1) {
                    driver.press(Key.DirectionRight)
                    driver.frame()
                    counter.take()
                    val before = camera.position
                    driver.frame()
                    assertEquals(0, counter.take(), "a frame between key repeats recomposed")
                    if (camera.position != before) panning++
                }
                assertTrue(panning > books.size / 2, "the room should have panned along with focus, but did on only $panning repeats")
                repeat(60) { driver.frame() }
                assertEquals(ShelfGeometry(books).maxCamera, camera.position)
                assertEquals(0, counter.take(), "frames of the pan settling at the end recomposed")
            }
        }
    }

    @Test
    fun theSettingsPanAndFrameFocusRecomposeOnlyOnKeyFrames() {
        SceneDriver(FakeLauncherRepository()).use { driver ->
            driver.repeat(Key.DirectionRight, FakeShelves.games.size)
            driver.settle()
            RecompositionCounter().use { counter ->
                driver.press(Key.DirectionCenter)
                driver.frame()
                assertTrue(counter.take() > 0, "the wall should compose as settings open")

                val panning = (0 until 12).map { driver.frame().fingerprint() }
                assertTrue(panning.zipWithNext().any { (a, b) -> a != b }, "the view should still be panning")
                assertEquals(0, counter.take(), "frames of the pan recomposed")

                driver.settle()
                counter.take()
                driver.press(Key.DirectionRight)
                driver.frame()
                counter.take()
                repeat(12) { driver.frame() }
                assertEquals(0, counter.take(), "frames of a move between frames recomposed")

                // OK on the Duck frame changes the ducks' colour in place; only that key frame may recompose.
                driver.press(Key.DirectionRight)
                driver.settle()
                counter.take()
                driver.press(Key.DirectionCenter)
                driver.frame()
                counter.take()
                repeat(12) { driver.frame() }
                assertEquals(0, counter.take(), "frames after changing the ducks' colour recomposed")
                driver.repeat(Key.DirectionLeft, 2)

                // Up one more level to the page the frame opens, and back down to the frames and the shelf.
                val pans = listOf(Key.DirectionCenter to "up to a page", Key.Back to "back to the frames", Key.Back to "back to the shelf")
                for ((key, what) in pans) {
                    driver.settle()
                    counter.take()
                    driver.press(key)
                    driver.frame()
                    counter.take()
                    val moving = (0 until 12).map { driver.frame().fingerprint() }
                    assertTrue(moving.zipWithNext().any { (a, b) -> a != b }, "the view should still be panning $what")
                    assertEquals(0, counter.take(), "frames of the pan $what recomposed")
                }
            }
        }
    }

    @Test
    fun focusMovingOntoOffAndPastTheDuckRecomposesOnlyOnKeyFrames() {
        SceneDriver(FakeLauncherRepository()).use { driver ->
            driver.repeat(Key.DirectionRight, FakeShelves.games.size - 1)
            driver.settle()
            RecompositionCounter().use { counter ->
                val moves = listOf(
                    Key.DirectionRight to "onto the duck, the last book turning back past its wing",
                    Key.DirectionLeft to "off the duck, the last book turning out past its wing",
                    Key.DirectionRight to "back onto the duck",
                    Key.Back to "from the duck past every book to the first",
                )
                for ((key, what) in moves) {
                    driver.settle()
                    counter.take()
                    driver.press(key)
                    driver.frame()
                    counter.take()
                    val frames = (0 until 12).map { driver.frame().fingerprint() }
                    assertTrue(frames.zipWithNext().any { (a, b) -> a != b }, "the shelf should be moving $what")
                    assertEquals(0, counter.take(), "frames $what recomposed")
                }

                // Held right along the shelf, through the last book onto the duck and on against the end.
                driver.settle()
                counter.take()
                repeat(FakeShelves.games.size + 4) {
                    driver.press(Key.DirectionRight)
                    driver.frame()
                    counter.take()
                    driver.frame()
                    assertEquals(0, counter.take(), "a frame between key repeats recomposed")
                }
            }
        }
    }

    @Test
    fun spineIconsArriveInDrawWithoutRecomposing() {
        val arrive = CompletableDeferred<Unit>()
        val icon = ImageBitmap(96, 96).also {
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(it), Size(96f, 96f)) { drawRect(Color.Red) }
        }
        val books = FakeShelves.games.map { it.copy(spineIcon = true) }
        val repository = object : LauncherRepository by FakeLauncherRepository(shelves = listOf(Shelf("all", "All", books))) {
            override suspend fun spineIcon(itemId: String): ImageBitmap {
                arrive.await()
                return icon
            }
        }
        SceneDriver(repository).use { driver ->
            driver.settle()
            val before = driver.frame().fingerprint()
            RecompositionCounter().use { counter ->
                arrive.complete(Unit)
                repeat(12) { driver.frame() }
                assertTrue(driver.frame().fingerprint() != before, "the icons should have been drawn")
                assertEquals(0, counter.take(), "spine icons arriving recomposed")
            }
        }
    }

    private fun Image.fingerprint(): Int = requireNotNull(encodeToData(EncodedImageFormat.PNG)).bytes.contentHashCode()
}

/** Counts recompose scopes entered in every composition of every running Recomposer. */
@OptIn(ExperimentalComposeRuntimeApi::class)
private class RecompositionCounter : AutoCloseable {
    private var count = 0
    private val handles = mutableListOf<CompositionObserverHandle?>()

    private val scopes = object : CompositionObserver {
        override fun onBeginComposition(composition: ObservableComposition) = Unit
        override fun onScopeEnter(scope: RecomposeScope) {
            count++
        }
        override fun onReadInScope(scope: RecomposeScope, value: Any) = Unit
        override fun onScopeExit(scope: RecomposeScope) = Unit
        override fun onEndComposition(composition: ObservableComposition) = Unit
        override fun onScopeInvalidated(scope: RecomposeScope, value: Any?) = Unit
        override fun onScopeDisposed(scope: RecomposeScope) = Unit
    }

    init {
        for (recomposer in Recomposer.runningRecomposers.value) {
            handles += recomposer.observe(object : CompositionRegistrationObserver {
                override fun onCompositionRegistered(composition: ObservableComposition) {
                    handles += composition.setObserver(scopes)
                }
                override fun onCompositionUnregistered(composition: ObservableComposition) = Unit
            })
        }
    }

    fun take(): Int = count.also { count = 0 }

    override fun close() = handles.forEach { it?.dispose() }
}
