package com.mkopenga.tvhomescreen.ui.wallpaper

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TestTimeSource

class CyclingWallpaperControllerTest {
    private val clock = TestTimeSource()

    private fun controller(seed: Int = 7) = CyclingWallpaperController(random = Random(seed), timeSource = clock)

    @Test
    fun startsOnTheRandomPick() {
        for (seed in 0 until 20) {
            val expected = Wallpapers[Random(seed).nextInt(Wallpapers.size)]
            assertEquals(expected, controller(seed).current.value)
        }
        val starts = (0 until 50).map { controller(it).current.value }.toSet()
        assertTrue(starts.size >= 5, "50 seeds started on only $starts")
    }

    @Test
    fun nothingChangesBeforeTheHourIsUp() {
        val c = controller()
        val first = c.current.value
        clock += 59.minutes
        assertEquals(1.minutes, c.tick())
        assertEquals(first, c.current.value)
    }

    @Test
    fun everyHourBringsADifferentWallpaper() {
        val c = controller()
        val seen = HashSet<Wallpaper>()
        repeat(200) {
            val before = c.current.value
            val next = c.upcoming()
            clock += 1.hours
            assertEquals(1.hours, c.tick())
            assertNotEquals(before, c.current.value, "cycle $it repeated ${before.id}")
            assertEquals(next, c.current.value, "the cycle should show what upcoming() promised")
            seen += c.current.value
        }
        assertEquals(Wallpapers.toSet(), seen, "200 hours should have shown every wallpaper")
    }

    @Test
    fun selectShowsItNowAndRestartsTheHour() {
        val c = controller()
        val other = Wallpapers.first { it != c.current.value }
        clock += 50.minutes
        c.select(other.id)
        assertEquals(other, c.current.value)
        assertNotEquals(other, c.upcoming())

        clock += 50.minutes
        assertEquals(10.minutes, c.tick())
        assertEquals(other, c.current.value, "the hour should count from the selection")

        clock += 10.minutes
        c.tick()
        assertNotEquals(other, c.current.value)
    }

    @Test
    fun selectingTheShownWallpaperStillRestartsTheHour() {
        val c = controller()
        val shown = c.current.value
        clock += 59.minutes
        c.select(shown.id)
        clock += 59.minutes
        c.tick()
        assertEquals(shown, c.current.value)
    }

    @Test
    fun unknownIdsAreIgnored() {
        val c = controller()
        val shown = c.current.value
        clock += 30.minutes
        c.select("no-such-wallpaper")
        assertEquals(shown, c.current.value)
        assertEquals(30.minutes, c.tick())
    }
}
