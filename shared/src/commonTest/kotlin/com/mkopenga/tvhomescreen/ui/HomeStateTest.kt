package com.mkopenga.tvhomescreen.ui

import com.mkopenga.tvhomescreen.NavAction
import com.mkopenga.tvhomescreen.NavAction.Back
import com.mkopenga.tvhomescreen.NavAction.Confirm
import com.mkopenga.tvhomescreen.NavAction.Down
import com.mkopenga.tvhomescreen.NavAction.Left
import com.mkopenga.tvhomescreen.NavAction.Right
import com.mkopenga.tvhomescreen.NavAction.Up
import com.mkopenga.tvhomescreen.Shelf
import com.mkopenga.tvhomescreen.fake.FakeShelves
import com.mkopenga.tvhomescreen.ui.settings.DuckColours
import com.mkopenga.tvhomescreen.ui.settings.SettingsFrame
import com.mkopenga.tvhomescreen.ui.settings.level
import com.mkopenga.tvhomescreen.ui.wallpaper.Wallpaper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeStateTest {
    private val state = HomeState(FakeShelves.all)

    private fun press(vararg actions: NavAction): HomeEffect? = actions.map(state::onAction).last()

    private val focusedId get() = state.focusedItem?.id

    private fun withLastUsed(id: String, at: Long) =
        FakeShelves.all.map { shelf -> shelf.copy(items = shelf.items.map { if (it.id == id) it.copy(lastUsedAt = at) else it }) }

    private fun toBookend() = repeat(FakeShelves.games.size + 2) { press(Right) }

    private fun openFrames() {
        toBookend()
        press(Confirm)
    }

    private val wallpapers = listOf(Wallpaper("moss", "Moss"), Wallpaper("linen", "Linen"), Wallpaper("dusk", "Dusk"))

    @Test
    fun everyShelfIsMergedIntoOneMostRecentFirst() {
        assertEquals(FakeShelves.games.size, state.items.size)
        assertEquals(listOf("iron-saints", "fern-and-the-deep-wood", "tin-orchestra"), state.items.take(3).map { it.id })
        assertEquals("iron-saints", focusedId)
    }

    @Test
    fun leftAndRightMoveAndStopAtTheEnds() {
        press(Right, Right)
        assertEquals("tin-orchestra", focusedId)
        repeat(5) { press(Left) }
        assertEquals(0, state.focusedIndex)
        repeat(40) { press(Right) }
        assertTrue(state.isBookendFocused)
        assertEquals(FakeShelves.games.size, state.focusedIndex)
    }

    @Test
    fun theBookendIsTheLastStopAfterTheLastBook() {
        toBookend()
        assertNull(state.focusedItem)
        assertEquals(InfoLabel("Shelf settings", "Wallpaper, hidden apps, the duck and system settings"), state.focusedLabel)
        press(Left)
        assertEquals(state.items.last().id, focusedId)
        assertEquals(state.items.last().title, state.focusedLabel?.title)
    }

    @Test
    fun movingFocusAnimates() {
        val jumps = state.focusJumps
        press(Right, Right, Left)
        toBookend()
        assertEquals(jumps, state.focusJumps)
    }

    @Test
    fun confirmLaunchesTheFocusedBook() {
        assertEquals(HomeEffect.Launch("fern-and-the-deep-wood"), press(Right, Confirm))
        assertEquals(Screen.Shelf, state.screen)
    }

    @Test
    fun backReturnsToTheFirstBookAndNeverLeaves() {
        press(Right, Right, Right)
        assertNull(press(Back))
        assertEquals(0, state.focusedIndex)
        assertNull(press(Back))
        assertEquals(0, state.focusedIndex)
        toBookend()
        assertNull(press(Back))
        assertEquals(0, state.focusedIndex)
    }

    @Test
    fun shelfAndSortKeysDoNothingOnABook() {
        press(Right)
        for (action in listOf(Up, Down, NavAction.Sort, NavAction.PrevShelf, NavAction.NextShelf, NavAction.Menu)) {
            assertNull(press(action))
            assertEquals("fern-and-the-deep-wood", focusedId, "$action")
            assertEquals(Screen.Shelf, state.screen, "$action")
        }
    }

    @Test
    fun okOnTheBookendOpensTheFramesAndBackReturnsToTheBookend() {
        toBookend()
        assertNull(press(Confirm))
        assertEquals(Screen.Frames, state.screen)
        assertEquals(SettingsFrame.Wallpaper, state.frame)
        press(Right, Right, Right, Right)
        assertEquals(SettingsFrame.System, state.frame)
        press(Left)
        assertEquals(SettingsFrame.Duck, state.frame)
        assertNull(press(Back))
        assertEquals(Screen.Shelf, state.screen)
        assertTrue(state.isBookendFocused)
    }

    @Test
    fun upOnTheBookendOpensTheFramesAndDownComesBack() {
        toBookend()
        press(Up)
        assertEquals(Screen.Frames, state.screen)
        press(Up)
        assertEquals(Screen.Frames, state.screen)
        press(Down)
        assertEquals(Screen.Shelf, state.screen)
        assertTrue(state.isBookendFocused)
    }

    @Test
    fun reopeningSettingsStartsAtTheFirstFrame() {
        openFrames()
        press(Right, Right, Back, Confirm)
        assertEquals(SettingsFrame.Wallpaper, state.frame)
    }

    @Test
    fun theSystemFrameOpensSystemSettingsAndStaysPut() {
        openFrames()
        assertEquals(HomeEffect.OpenSystemSettings, press(Right, Right, Right, Confirm))
        assertEquals(Screen.Frames, state.screen)
    }

    @Test
    fun okOnTheDuckFrameStepsThroughEveryColourInPlaceAndBackToTheFirst() {
        openFrames()
        press(Right, Right)
        assertEquals(SettingsFrame.Duck, state.frame)
        assertEquals(DuckColours.Yellow, state.duckColours)
        val seen = mutableListOf(state.duckColours)
        repeat(DuckColours.entries.size) {
            val colours = state.duckColours.next
            assertEquals(HomeEffect.SetDuckColour(colours.name), press(Confirm))
            assertEquals(colours, state.duckColours)
            assertEquals(Screen.Frames, state.screen, "no page, no pan")
            assertEquals(SettingsFrame.Duck, state.frame)
            seen += colours
        }
        assertEquals(DuckColours.entries + DuckColours.Yellow, seen)
    }

    @Test
    fun theDucksTakeTheRepositorysColourAndAnUnknownOrUnsetOneIsYellow() {
        assertEquals(DuckColours.White, HomeState(FakeShelves.all, duckColour = "White").duckColours)
        assertEquals(DuckColours.Yellow, HomeState(FakeShelves.all, duckColour = null).duckColours)
        state.updateDuckColour("White")
        assertEquals(DuckColours.White, state.duckColours)
        state.updateDuckColour("Mallard")
        assertEquals(DuckColours.Yellow, state.duckColours)
    }

    @Test
    fun theWallpaperPickerStartsOnTheCurrentOneAndPicks() {
        state.updateWallpapers(wallpapers, currentId = "linen")
        openFrames()
        press(Confirm)
        assertEquals(Screen.WallpaperPicker, state.screen)
        assertEquals(1, state.pickerIndex)
        press(Right, Right)
        assertEquals(2, state.pickerIndex)
        press(Left, Left, Left)
        assertEquals(0, state.pickerIndex)
        assertEquals(HomeEffect.SelectWallpaper("moss"), press(Confirm))
        assertEquals(Screen.Frames, state.screen)
        assertEquals(SettingsFrame.Wallpaper, state.frame)
        assertEquals("moss", state.currentWallpaper?.id)
    }

    @Test
    fun backFromThePickerChangesNothing() {
        state.updateWallpapers(wallpapers, currentId = "linen")
        openFrames()
        assertNull(press(Confirm, Right, Back))
        assertEquals(Screen.Frames, state.screen)
        assertEquals("linen", state.currentWallpaper?.id)
    }

    @Test
    fun anHourlyChangeMovesTheCurrentWallpaper() {
        state.updateWallpapers(wallpapers, currentId = "linen")
        state.updateWallpapers(wallpapers, currentId = "dusk")
        assertEquals("Dusk", state.currentWallpaper?.name)
    }

    @Test
    fun theWallpaperFramesSamplesMoveOnWithAnHourlyChangeAndAPick() {
        state.updateWallpapers(wallpapers, currentId = "linen")
        assertEquals(listOf("dusk", "moss"), state.wallpaperSamples.map { it.id })
        state.updateWallpapers(wallpapers, currentId = "dusk")
        assertEquals(listOf("moss", "linen"), state.wallpaperSamples.map { it.id })
        openFrames()
        press(Confirm, Left, Left, Confirm)
        assertEquals("moss", state.currentWallpaper?.id)
        assertEquals(listOf("linen", "dusk"), state.wallpaperSamples.map { it.id })
    }

    @Test
    fun hideAppsListsEveryAppAToZAndTogglesThem() {
        openFrames()
        press(Right, Confirm)
        assertEquals(Screen.HiddenApps, state.screen)
        assertEquals(FakeShelves.games.map { it.title }.sortedBy { it.lowercase() }, state.allItems.map { it.title })
        assertEquals("bloom", state.focusedApp?.id)

        assertEquals(HomeEffect.SetHidden("bloom", true), press(Confirm))
        assertEquals(setOf("bloom"), state.hiddenIds)
        assertFalse(state.items.any { it.id == "bloom" })
        assertEquals(FakeShelves.games.size, state.allItems.size, "hidden apps stay on the list")

        assertEquals(HomeEffect.SetHidden("bloom", false), press(Confirm))
        assertEquals(emptySet(), state.hiddenIds)
        assertTrue(state.items.any { it.id == "bloom" })
    }

    @Test
    fun theHideAppsGridMovesInFourColumns() {
        openFrames()
        press(Right, Confirm)
        val count = state.allItems.size
        press(Right, Right, Right, Right)
        assertEquals(3, state.appIndex, "Right stops at the end of a row")
        press(Down)
        assertEquals(7, state.appIndex)
        press(Left, Left, Left, Left)
        assertEquals(4, state.appIndex, "Left stops at the start of a row")
        press(Up, Up)
        assertEquals(0, state.appIndex)
        press(Right, Right, Right)
        repeat(10) { press(Down) }
        assertEquals(count - 1, state.appIndex, "Down into the partial last row lands on its last app")
    }

    @Test
    fun eachStepIntoSettingsLooksOneLevelFurtherUpTheWall() {
        state.updateWallpapers(wallpapers, currentId = "linen")
        val levels = mutableListOf(state.screen.level)
        for (action in listOf(Confirm, Confirm, Back, Back)) {
            if (state.screen == Screen.Shelf) toBookend()
            press(action)
            levels += state.screen.level
        }
        assertEquals(listOf(0, 1, 2, 1, 0), levels)
        assertTrue(state.isBookendFocused)
    }

    @Test
    fun backFromThePickerReturnsToTheWallpaperFrame() {
        state.updateWallpapers(wallpapers, currentId = "linen")
        openFrames()
        press(Confirm, Back)
        assertEquals(Screen.Frames, state.screen)
        assertEquals(SettingsFrame.Wallpaper, state.frame)
    }

    @Test
    fun backFromHideAppsReturnsToItsFrame() {
        openFrames()
        assertNull(press(Right, Confirm, Back))
        assertEquals(Screen.Frames, state.screen)
        assertEquals(SettingsFrame.HiddenApps, state.frame)
        assertEquals(Screen.HiddenApps, state.lastPage)
    }

    @Test
    fun hiddenAppsLeaveTheShelfAndFocusStaysOnTheSameBook() {
        press(Right, Right)
        assertEquals("tin-orchestra", focusedId)
        state.updateHidden(setOf("iron-saints", "lowlight"))
        assertEquals("tin-orchestra", focusedId)
        assertEquals(1, state.focusedIndex)
        assertEquals(FakeShelves.games.size - 2, state.items.size)
        state.updateHidden(emptySet())
        assertEquals("tin-orchestra", focusedId)
        assertEquals(2, state.focusedIndex)
    }

    @Test
    fun hidingTheFocusedBookKeepsTheSameSlot() {
        press(Right, Right)
        val jumps = state.focusJumps
        state.updateHidden(setOf("tin-orchestra"))
        assertEquals(2, state.focusedIndex)
        assertEquals("moth-lantern", focusedId)
        assertNotEquals(jumps, state.focusJumps)
    }

    @Test
    fun theBookendKeepsFocusWhileAppsAreHiddenFromSettings() {
        openFrames()
        press(Right, Confirm, Confirm, Right, Confirm)
        assertEquals(2, state.hiddenIds.size)
        assertTrue(state.isBookendFocused)
        assertEquals(state.items.size, state.focusedIndex)
        press(Back, Back)
        assertEquals(Screen.Shelf, state.screen)
        assertTrue(state.isBookendFocused)
    }

    @Test
    fun withEveryAppHiddenTheBookendIsStillThere() {
        val all = FakeShelves.games.map { it.id }.toSet()
        state.updateHidden(all)
        assertTrue(state.items.isEmpty())
        assertTrue(state.isBookendFocused)
        press(Confirm, Right, Confirm)
        assertEquals(Screen.HiddenApps, state.screen)
        assertEquals(FakeShelves.games.size, state.allItems.size)
        assertEquals(HomeEffect.SetHidden("bloom", false), press(Confirm))
        assertEquals(listOf("bloom"), state.items.map { it.id })
        assertTrue(state.isBookendFocused)
    }

    @Test
    fun hiddenIdsGivenUpFrontNeverReachTheShelf() {
        val hidden = HomeState(FakeShelves.all, hiddenIds = setOf("iron-saints"))
        assertEquals("fern-and-the-deep-wood", hidden.focusedItem?.id)
        assertTrue(hidden.allItems.any { it.id == "iron-saints" })
    }

    @Test
    fun homeClosesSettingsFromAnywhere() {
        state.updateWallpapers(wallpapers, currentId = "linen")
        openFrames()
        press(Confirm)
        state.goHome()
        assertEquals(Screen.Shelf, state.screen)
        assertEquals(0, state.focusedIndex)
    }

    @Test
    fun aLaunchMovesTheBookToTheFrontAndFocusFollowsIt() {
        repeat(6) { press(Right) }
        assertEquals("quiet-harbour", focusedId)
        val jumps = state.focusJumps
        state.updateShelves(withLastUsed("quiet-harbour", FakeShelves.NOW))
        assertEquals(0, state.focusedIndex)
        assertEquals("quiet-harbour", focusedId)
        assertNotEquals(jumps, state.focusJumps)
    }

    @Test
    fun aRefreshThatLeavesTheFocusedBookInPlaceDoesNotInterruptAGlide() {
        press(Right, Right)
        val jumps = state.focusJumps
        val installed = FakeShelves.games.first().copy(id = "zzz-new", title = "Zed", lastUsedAt = null)
        state.updateShelves(FakeShelves.all + Shelf("new", "New", listOf(installed)))
        assertEquals(jumps, state.focusJumps)
        assertEquals("tin-orchestra", focusedId)
        assertEquals("zzz-new", state.items.last().id)
    }

    @Test
    fun removingTheFocusedBookKeepsTheSameSlot() {
        press(Right, Right)
        state.updateShelves(FakeShelves.all.map { shelf -> shelf.copy(items = shelf.items.filterNot { it.id == "tin-orchestra" }) })
        assertEquals(2, state.focusedIndex)
        assertEquals("moth-lantern", focusedId)
    }

    @Test
    fun removingTheLastBookWhileFocusedMovesToTheBookBeforeIt() {
        repeat(FakeShelves.games.size - 1) { press(Right) }
        val last = focusedId
        val before = state.items[state.items.size - 2].id
        state.updateShelves(FakeShelves.all.map { shelf -> shelf.copy(items = shelf.items.filterNot { it.id == last }) })
        assertEquals(before, focusedId)
    }

    @Test
    fun homeReturnsToTheFirstBook() {
        press(Right, Right, Right)
        state.goHome()
        assertEquals(0, state.focusedIndex)
    }

    @Test
    fun startingWithNoShelvesIsSafe() {
        val empty = HomeState()
        for (action in NavAction.entries) assertNull(empty.onAction(action))
        empty.goHome()
        assertNull(empty.focusedItem)
        empty.updateShelves(FakeShelves.all)
        assertEquals("iron-saints", empty.focusedItem?.id)
    }
}
