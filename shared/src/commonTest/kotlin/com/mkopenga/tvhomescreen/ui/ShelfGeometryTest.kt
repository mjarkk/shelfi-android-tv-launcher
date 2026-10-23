package com.mkopenga.tvhomescreen.ui

import com.mkopenga.tvhomescreen.fake.FakeShelves
import com.mkopenga.tvhomescreen.ui.settings.BookendWidth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShelfGeometryTest {
    // The mockup has no duck before the first book; every other shelf here has both.
    private val mockup = ShelfGeometry(FakeShelves.games, leftDuck = false)
    private val paired = ShelfGeometry(FakeShelves.games, leftDuck = true)
    private val fits = ShelfGeometry(FakeShelves.games.take(8), leftDuck = true)
    private val longBooks = List(4) { copy -> FakeShelves.games.map { it.copy(id = "${it.id}-$copy") } }.flatten()
    private val long = ShelfGeometry(longBooks, leftDuck = true)
    private val longWithOneDuck = ShelfGeometry(longBooks, leftDuck = false)
    private val empty = ShelfGeometry(emptyList(), leftDuck = true)

    // Spine sizes past the derived range.
    private val extremeSizes = listOf(460 to 64, 600 to 112, 548 to 96, 600 to 64, 460 to 112)
    private val mixedItems = FakeShelves.games.flatMapIndexed { i, game ->
        val (height, width) = extremeSizes[i % extremeSizes.size]
        listOf(game, game.copy(id = "${game.id}-sized", spineWidth = width, spineHeight = height))
    }
    private val mixed = ShelfGeometry(mixedItems, leftDuck = true)

    private fun ShelfGeometry.focusedSlot(i: Int): ClosedFloatingPointRange<Float> {
        val left = ShelfLeft + slotLeft(i, i.toFloat())
        return left..left + slotWidth(i, 1f)
    }

    @Test
    fun onlyTheFocusedBookTurnsOut() {
        for (i in 0 until mockup.count) assertEquals(if (i == 4) 1f else 0f, mockup.expansion(i, 4f))
        assertEquals(0.5f, mockup.expansion(4, 4.5f))
        assertEquals(0.5f, mockup.expansion(5, 4.5f))
    }

    @Test
    fun aTurningBookGoesFromSpineToCoverInsideItsSlot() {
        assertEquals(mockup.spineWidths[4], mockup.turn(4, 0f).width, 0.001f)
        assertEquals(CoverWidth, mockup.turn(4, 1f).width, 0.001f)
        var e = 0f
        while (e <= 1f) {
            val turn = mockup.turn(4, e)
            assertEquals(e * 90f, turn.angle, 0.001f)
            assertTrue(turn.width <= mockup.slotWidth(4, e) + 0.01f, "box wider than its slot at e=$e")
            assertTrue(turn.edge in 0f..turn.width)
            e += 0.05f
        }
    }

    @Test
    fun theMockupLayoutComesOutExactly() {
        assertEquals(0f, mockup.cameraFor(4, 0f))
        assertEquals(504f, ShelfLeft + mockup.itemLeft(4, 4f), 0.01f)
        assertEquals(940f, ShelfLeft + mockup.slotLeft(5, 4f) + 0f, 0.01f)
        assertEquals(1732f, ShelfLeft + mockup.slotLeft(mockup.bookend, 4f), 0.01f, "the bookend stands against the last book")
    }

    @Test
    fun theBookendIsTheLastFocusStop() {
        assertEquals(mockup.count, mockup.bookend)
        assertEquals(mockup.count.toFloat(), mockup.clampPosition(100f))
        assertEquals(1f, mockup.expansion(mockup.bookend, mockup.count.toFloat()))
        assertEquals(0f, mockup.expansion(mockup.count - 1, mockup.count.toFloat()))
    }

    @Test
    fun movingBetweenBooksNeverShiftsTheRestOfTheShelf() {
        val last = mockup.count - 1
        val restingRight = mockup.slotLeft(last, 3f) + mockup.slotWidth(last, 0f)
        var p = 3f
        while (p <= 5f) {
            assertEquals(restingRight, mockup.slotLeft(last, p) + mockup.slotWidth(last, 0f), 0.01f, "p=$p")
            assertEquals(mockup.slotLeft(2, 3f), mockup.slotLeft(2, p), 0.01f, "p=$p")
            p += 0.01f
        }
    }

    @Test
    fun theBookendNeverMovesAgainstTheBooksItHolds() {
        for (geometry in listOf(mockup, paired, mixed)) {
            val last = geometry.count - 1
            var p = last.toFloat()
            while (p <= geometry.count) {
                assertEquals(geometry.slotLeft(last, p) + geometry.slotWidth(last, geometry.expansion(last, p)), geometry.itemLeft(geometry.bookend, p), 0.01f, "p=$p")
                assertEquals(BookendWidth, geometry.slotWidth(geometry.bookend, geometry.expansion(geometry.bookend, p)))
                p += 0.01f
            }
        }
    }

    @Test
    fun turningFromTheLastBookToTheBookendLeavesEveryBookInPlace() {
        for (geometry in listOf(mockup, paired, mixed)) {
            val last = geometry.count - 1
            val before = FloatArray(geometry.count) { geometry.slotLeft(it, last.toFloat()) }
            var p = last.toFloat()
            while (p <= geometry.count) {
                for (j in 0 until geometry.count) assertEquals(before[j], geometry.slotLeft(j, p), 0.01f, "book $j at p=$p")
                p += 0.01f
            }
        }
    }

    @Test
    fun theLeftDuckStandsBeforeTheFirstBookAndEveryBookMovesOverByItsWidth() {
        assertEquals(BookendWidth, long.lead)
        assertEquals(0f, longWithOneDuck.lead)
        for (p in listOf(0f, 0.5f, 7f, long.count.toFloat())) {
            for (i in 0..long.bookend) {
                assertEquals(longWithOneDuck.slotLeft(i, p) + BookendWidth, long.slotLeft(i, p), 0.01f, "slot $i at p=$p")
            }
        }
        assertEquals(BookendWidth, long.slotLeft(0, 3f), 0.01f, "the first book stands against the duck")
        assertEquals(longWithOneDuck.plankLength + BookendWidth, long.plankLength, 0.01f)
    }

    @Test
    fun atTheStartTheLeftDuckAndThePlanksLeftEndAreInView() {
        for (geometry in listOf(long, fits, mixed)) {
            for (from in listOf(0f, 900f, geometry.maxCamera)) assertEquals(0f, geometry.cameraFor(0, from), "from $from")
            assertTrue(PlankLeft >= 0f && PlankLeft <= ShelfLeft, "the duck stands on the plank, whose end is on screen")
            assertTrue(ShelfLeft + geometry.lead + geometry.slotWidth(0, 1f) <= DesignWidth - SafeMargin, "the first book with its cover out")
        }
    }

    @Test
    fun atTheFarEndTheDuckAndThePlanksRightEndAreInView() {
        for (geometry in listOf(long, mixed)) {
            val camera = geometry.cameraFor(geometry.bookend, 0f)
            assertEquals(geometry.maxCamera, camera)
            val duckRight = ShelfLeft + geometry.itemLeft(geometry.bookend, geometry.count.toFloat()) + BookendWidth - camera
            val plankRight = PlankLeft + geometry.plankLength - camera
            assertTrue(duckRight <= plankRight && plankRight <= DesignWidth, "duck to $duckRight, plank to $plankRight")
        }
    }

    @Test
    fun anEmptyShelfHasNoDuckBeforeItsBooks() {
        assertEquals(0f, empty.lead)
    }

    @Test
    fun aShelfThatFitsBetweenItsDucksGetsTheShortestPlankAndNeverMovesTheCamera() {
        assertEquals(PlankMinLength, fits.plankLength)
        assertEquals(DesignWidth.toFloat(), fits.roomWidth)
        assertEquals(0f, fits.maxCamera)
        for (i in 0..fits.bookend) assertEquals(0f, fits.cameraFor(i, 0f), "focus $i")
        assertTrue(ShelfLeft + fits.maxContentWidth <= PlankLeft + fits.plankLength - (ShelfLeft - PlankLeft))
    }

    @Test
    fun aPlankHoldsEveryBookWithACoverOutAndTheBookendAndAnEmptyShelfKeepsTheShortest() {
        val margin = ShelfLeft - PlankLeft
        assertEquals(long.maxContentWidth + 2 * margin, long.plankLength)
        assertEquals(PlankLeft + long.plankLength, ShelfLeft + long.slotLeft(long.bookend, 0f) + BookendWidth + margin, 0.01f, "the bookend with a cover out")
        assertEquals(long.plankLength + 2 * PlankLeft, long.roomWidth)
        assertEquals(PlankMinLength, empty.plankLength)
    }

    @Test
    fun theCameraStaysInsideTheRoom() {
        for (from in listOf(-500f, 0f, 1234f, long.maxCamera, 1e5f)) {
            for (i in 0..long.bookend) assertTrue(long.cameraFor(i, from) in 0f..long.maxCamera, "focus $i from $from")
            assertEquals(0f, long.cameraFor(0, from), "the first book shows the plank's left end, from $from")
            assertEquals(long.maxCamera, long.cameraFor(long.bookend, from), "the bookend shows the plank's right end, from $from")
        }
    }

    @Test
    fun theCameraMovesOnlyWhenFocusWouldComeTooCloseToAnEdge() {
        for (geometry in listOf(long, mixed)) {
            var camera = 0f
            var moves = 0
            val path = (1..geometry.bookend) + (geometry.bookend - 1 downTo 0)
            for ((step, i) in path.withIndex()) {
                val next = geometry.cameraFor(i, camera)
                val rightward = step < geometry.bookend
                val neighbour = if (rightward) i + 1 else i - 1
                if (neighbour in 0 until geometry.bookend) {
                    val p = i.toFloat()
                    val edge = if (rightward) {
                        ShelfLeft + geometry.slotLeft(neighbour, p) + geometry.slotWidth(neighbour, 0f) - camera
                    } else {
                        ShelfLeft + geometry.slotLeft(neighbour, p) - camera
                    }
                    val needed = if (rightward) edge > DesignWidth - SafeMargin else edge < SafeMargin
                    assertEquals(needed, next != camera, "step $step to $i: neighbour edge on screen at $edge")
                    if (needed) {
                        val settled = edge + camera - next
                        assertEquals(if (rightward) DesignWidth - SafeMargin else SafeMargin, settled, 0.01f, "step $step to $i")
                    }
                }
                if (next != camera) moves++
                val slot = geometry.focusedSlot(i)
                assertTrue(slot.start - next >= SafeMargin - 0.01f && slot.endInclusive - next <= DesignWidth - SafeMargin + 0.01f, "focus $i off the safe area")
                camera = next
            }
            assertTrue(moves > 2, "the camera should have followed the focus")
            assertEquals(0f, camera)
        }
    }

    @Test
    fun turningBackLeavesTheCameraWhereItIs() {
        var camera = 0f
        for (i in 1..20) camera = long.cameraFor(i, camera)
        assertTrue(camera > 0f)
        for (i in 19 downTo 14) assertEquals(camera, long.cameraFor(i, camera), "back to $i")
    }

    @Test
    fun anEmptyShelfStillHasItsBookend() {
        assertEquals(0, empty.bookend)
        assertEquals(0f, empty.clampPosition(3f))
        assertEquals(0f, empty.cameraFor(0, 0f))
        assertEquals(0f, empty.itemLeft(empty.bookend, 0f), 0.01f)
    }

    @Test
    fun eachBookKeepsItsOwnSpineSizeAndEveryCoverIsTheSameHeight() {
        for ((i, item) in mixedItems.withIndex()) {
            assertEquals(item.spineWidth?.toFloat(), mixed.spineWidths[i], item.id)
            assertEquals(item.spineHeight?.toFloat(), mixed.spineHeights[i], item.id)
            assertEquals(CoverHeight, mixed.coverHeights[i], item.id)
        }
    }

    @Test
    fun theTallestAndShortestSpinesTurnIntoTheCoverInsideTheirSlots() {
        for (height in listOf(600, 460)) {
            val i = mixedItems.indexOfFirst { it.spineHeight == height }
            var e = 0f
            while (e <= 1f) {
                assertEquals(lerp(height.toFloat(), CoverHeight, e), mixed.itemHeight(i, e), 0.001f, "$height at e=$e")
                assertTrue(mixed.turn(i, e).width <= mixed.slotWidth(i, e) + 0.01f, "$height: box wider than its slot at e=$e")
                e += 0.05f
            }
            assertEquals(CoverWidth, mixed.turn(i, 1f).width, 0.001f)
        }
    }

    @Test
    fun movingToANeighbouringBookOfAMixedShelfNeverShiftsTheOthers() {
        for (k in 0 until mixed.count - 1) {
            val before = FloatArray(mixed.count) { mixed.slotLeft(it, k.toFloat()) }
            val pairWidth = mixed.slotWidth(k, 1f) + mixed.slotWidth(k + 1, 0f)
            var p = k.toFloat()
            while (p <= k + 1) {
                assertEquals(pairWidth, mixed.slotWidth(k, mixed.expansion(k, p)) + mixed.slotWidth(k + 1, mixed.expansion(k + 1, p)), 0.01f, "k=$k p=$p")
                for (j in 0 until mixed.count) {
                    if (j == k + 1) continue
                    assertEquals(before[j], mixed.slotLeft(j, p), 0.01f, "book $j while moving $k -> ${k + 1}, p=$p")
                }
                p += 0.05f
            }
        }
    }

    @Test
    fun theNarrowestSpinesTurnedCoverKeepsItsFocusRingInsideItsSlot() {
        val i = mixedItems.indexOfFirst { it.spineWidth == 64 }
        assertTrue((mixed.slotWidth(i, 1f) - CoverWidth) / 2 >= FocusRingReach)
    }

    @Test
    fun aLiftedCoverWithItsFocusRingFitsAboveThePlank() {
        assertTrue(mixed.coverHeights.max() + CoverLift + FocusRingReach <= Baseline)
    }

    @Test
    fun derivedSpineSizesStayInTheMockupsRange() {
        for (n in 0 until 500) {
            val item = FakeShelves.games[0].copy(id = "com.example.app$n", spineWidth = null, spineHeight = null)
            assertTrue(ShelfGeometry.spineWidthOf(item) in 70..96)
            assertTrue(ShelfGeometry.spineHeightOf(item) in 460..550)
        }
    }
}
