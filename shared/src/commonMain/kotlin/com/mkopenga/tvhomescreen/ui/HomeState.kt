package com.mkopenga.tvhomescreen.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mkopenga.tvhomescreen.NavAction
import com.mkopenga.tvhomescreen.Shelf
import com.mkopenga.tvhomescreen.ShelfItem
import com.mkopenga.tvhomescreen.ui.settings.DuckColours
import com.mkopenga.tvhomescreen.ui.settings.HiddenAppsColumns
import com.mkopenga.tvhomescreen.ui.settings.SettingsFrame
import com.mkopenga.tvhomescreen.ui.settings.wallpaperSamples
import com.mkopenga.tvhomescreen.ui.wallpaper.Wallpaper

/** Where the remote is: on the shelf, at the frames on the wall above it, or on a page opened from a frame. */
internal enum class Screen {
    Shelf, Frames, WallpaperPicker, HiddenApps;

    val isPage: Boolean get() = this == WallpaperPicker || this == HiddenApps
}

/** What a key asks of the world outside the state. */
internal sealed interface HomeEffect {
    data class Launch(val id: String) : HomeEffect
    data class SetHidden(val id: String, val hidden: Boolean) : HomeEffect
    data class SelectWallpaper(val id: String) : HomeEffect
    data class SetDuckColour(val name: String) : HomeEffect
    data object OpenSystemSettings : HomeEffect
}

@Immutable
internal data class InfoLabel(val title: String, val subtitle: String)

private val BookendLabel = InfoLabel("Shelf settings", "Wallpaper, hidden apps, the duck and system settings")

private val AToZ: Comparator<ShelfItem> = compareBy<ShelfItem> { it.title.lowercase() }.thenBy { it.id }

/**
 * Navigation state of the home screen, driven by [NavAction]s from the single key handler at the root. Plain
 * snapshot state with no Compose UI dependency, so it is unit-testable.
 *
 * The shelf holds every app that isn't hidden, most recently used first, and ends in a bookend: focus stops
 * 0 until items.size are books and items.size is the bookend, which opens settings. Settings are frames on the
 * wall above the shelf ([SettingsFrame]); two of them open a page of their own, and the Duck frame changes the
 * ducks' colour in place.
 *
 * Focus is tracked by id, so it stays on the same book (or the bookend) when apps are installed, removed, hidden or
 * re-ordered by a launch. [focusJumps] counts focus changes that must not animate (the focused book moving underneath).
 */
@Stable
internal class HomeState(shelves: List<Shelf> = emptyList(), hiddenIds: Set<String> = emptySet(), duckColour: String? = null) {
    var items: List<ShelfItem> by mutableStateOf(emptyList())
        private set

    /** Every app once, hidden ones included, A–Z: the hide-apps page. */
    var allItems: List<ShelfItem> by mutableStateOf(emptyList())
        private set
    var hiddenIds: Set<String> by mutableStateOf(hiddenIds)
        private set
    var duckColours: DuckColours by mutableStateOf(DuckColours.named(duckColour))
        private set
    var focusedIndex: Int by mutableIntStateOf(0)
        private set
    var focusJumps: Int by mutableIntStateOf(0)
        private set

    var screen: Screen by mutableStateOf(Screen.Shelf)
        private set
    var frame: SettingsFrame by mutableStateOf(SettingsFrame.Wallpaper)
        private set

    /** The page opened most recently; it stays on the wall while it closes. */
    var lastPage: Screen by mutableStateOf(Screen.WallpaperPicker)
        private set

    var wallpapers: List<Wallpaper> by mutableStateOf(emptyList())
        private set
    var currentWallpaperId: String? by mutableStateOf(null)
        private set
    var pickerIndex: Int by mutableIntStateOf(0)
        private set

    /** Focus on the hide-apps page, an index into [allItems]. */
    var appIndex: Int by mutableIntStateOf(0)
        private set

    private var merged: List<ShelfItem> = emptyList()

    val isBookendFocused: Boolean get() = focusedIndex == items.size
    val focusedItem: ShelfItem? get() = items.getOrNull(focusedIndex)
    val focusedApp: ShelfItem? get() = allItems.getOrNull(appIndex)

    /** The info line under the plank. */
    val focusedLabel: InfoLabel?
        get() = focusedItem?.let { InfoLabel(it.title, it.subtitle) } ?: BookendLabel.takeIf { isBookendFocused }

    val currentWallpaper: Wallpaper?
        get() = wallpapers.firstOrNull { it.id == currentWallpaperId } ?: wallpapers.firstOrNull()

    /** The other wallpapers the Wallpaper frame shows; they move on whenever the current one changes. */
    val wallpaperSamples: List<Wallpaper>
        get() = wallpaperSamples(wallpapers, currentWallpaper)

    init {
        updateShelves(shelves)
    }

    /** The repository's shelves are merged into one; the same app on several shelves appears once. */
    fun updateShelves(shelves: List<Shelf>) {
        merged = shelves.flatMap { it.items }.distinctBy { it.id }
        rebuild()
    }

    /** [name] as the repository has it; null or unknown is the default. */
    fun updateDuckColour(name: String?) {
        duckColours = DuckColours.named(name)
    }

    fun updateHidden(ids: Set<String>) {
        if (ids == hiddenIds) return
        hiddenIds = ids
        rebuild()
    }

    /** [currentId] is the wallpaper on the wall now; it changes on a pick and every hour by itself. */
    fun updateWallpapers(all: List<Wallpaper>, currentId: String) {
        val pickedId = wallpapers.getOrNull(pickerIndex)?.id
        wallpapers = all
        currentWallpaperId = currentId
        pickerIndex = all.indexOfFirst { it.id == pickedId }.takeIf { it >= 0 }
            ?: pickerIndex.coerceIn(0, (all.size - 1).coerceAtLeast(0))
    }

    fun onAction(action: NavAction): HomeEffect? = when (screen) {
        Screen.Shelf -> onShelf(action)
        Screen.Frames -> onFrames(action)
        Screen.WallpaperPicker -> onPicker(action)
        Screen.HiddenApps -> onHiddenApps(action)
    }

    /** Home pressed while already home: back to the first book, from wherever. */
    fun goHome() {
        screen = Screen.Shelf
        moveTo(0)
    }

    private fun onShelf(action: NavAction): HomeEffect? {
        when (action) {
            NavAction.Left -> moveTo(focusedIndex - 1)
            NavAction.Right -> moveTo(focusedIndex + 1)
            // Back never leaves the launcher; it just returns to the start of the shelf.
            NavAction.Back -> moveTo(0)
            NavAction.Confirm -> {
                val item = focusedItem
                if (item != null) return HomeEffect.Launch(item.id)
                openSettings()
            }
            // The frames hang on the wall above the shelf.
            NavAction.Up -> if (isBookendFocused) openSettings()
            NavAction.Down, NavAction.Sort, NavAction.PrevShelf, NavAction.NextShelf, NavAction.Menu -> Unit
        }
        return null
    }

    private fun onFrames(action: NavAction): HomeEffect? {
        val frames = SettingsFrame.entries
        when (action) {
            NavAction.Left -> frame = frames[(frame.ordinal - 1).coerceAtLeast(0)]
            NavAction.Right -> frame = frames[(frame.ordinal + 1).coerceAtMost(frames.lastIndex)]
            // Down looks back at the shelf below. Focus never left the bookend.
            NavAction.Back, NavAction.Down -> screen = Screen.Shelf
            NavAction.Confirm -> when (frame) {
                SettingsFrame.Wallpaper -> openPicker()
                SettingsFrame.HiddenApps -> openPage(Screen.HiddenApps)
                SettingsFrame.Duck -> {
                    // Here as well as by the repository, so the frame and the ducks change on this very frame.
                    duckColours = duckColours.next
                    return HomeEffect.SetDuckColour(duckColours.name)
                }
                SettingsFrame.System -> return HomeEffect.OpenSystemSettings
            }
            NavAction.Up, NavAction.Sort, NavAction.PrevShelf, NavAction.NextShelf, NavAction.Menu -> Unit
        }
        return null
    }

    private fun onPicker(action: NavAction): HomeEffect? {
        when (action) {
            NavAction.Left -> pickerIndex = (pickerIndex - 1).coerceAtLeast(0)
            NavAction.Right -> pickerIndex = (pickerIndex + 1).coerceAtMost((wallpapers.size - 1).coerceAtLeast(0))
            NavAction.Back -> screen = Screen.Frames
            NavAction.Confirm -> {
                screen = Screen.Frames
                val picked = wallpapers.getOrNull(pickerIndex) ?: return null
                currentWallpaperId = picked.id
                return HomeEffect.SelectWallpaper(picked.id)
            }
            NavAction.Up, NavAction.Down, NavAction.Sort, NavAction.PrevShelf, NavAction.NextShelf, NavAction.Menu -> Unit
        }
        return null
    }

    private fun onHiddenApps(action: NavAction): HomeEffect? {
        val count = allItems.size
        val columns = HiddenAppsColumns
        when (action) {
            NavAction.Left -> if (appIndex % columns > 0) appIndex--
            NavAction.Right -> if (appIndex % columns < columns - 1 && appIndex + 1 < count) appIndex++
            NavAction.Up -> if (appIndex >= columns) appIndex -= columns
            // From a column the last, partial row doesn't reach, Down lands on its last app.
            NavAction.Down -> if (appIndex / columns < (count - 1) / columns) appIndex = (appIndex + columns).coerceAtMost(count - 1)
            NavAction.Back -> screen = Screen.Frames
            NavAction.Confirm -> {
                val app = focusedApp ?: return null
                val hide = app.id !in hiddenIds
                // Applied here as well as by the repository, so the card and the shelf change on this very frame.
                updateHidden(if (hide) hiddenIds + app.id else hiddenIds - app.id)
                return HomeEffect.SetHidden(app.id, hide)
            }
            NavAction.Sort, NavAction.PrevShelf, NavAction.NextShelf, NavAction.Menu -> Unit
        }
        return null
    }

    private fun openSettings() {
        frame = SettingsFrame.Wallpaper
        screen = Screen.Frames
    }

    private fun openPicker() {
        if (wallpapers.isEmpty()) return
        pickerIndex = wallpapers.indexOfFirst { it.id == currentWallpaper?.id }.coerceAtLeast(0)
        openPage(Screen.WallpaperPicker)
    }

    private fun openPage(page: Screen) {
        if (page == Screen.HiddenApps) appIndex = 0
        lastPage = page
        screen = page
    }

    private fun rebuild() {
        val catalogue = merged.sortedWith(AToZ)
        if (catalogue != allItems) {
            val appId = focusedApp?.id
            allItems = catalogue
            appIndex = catalogue.indexOfFirst { it.id == appId }.takeIf { it >= 0 }
                ?: appIndex.coerceIn(0, (catalogue.size - 1).coerceAtLeast(0))
        }
        val shelf = merged.filterNot { it.id in hiddenIds }.recentFirst()
        if (shelf == items) return
        // The bookend keeps focus through changes made from settings, but the first app list to arrive gets it.
        val onBookend = isBookendFocused && (items.isNotEmpty() || screen != Screen.Shelf)
        val focusedId = focusedItem?.id
        val oldIndex = focusedIndex
        items = shelf
        val index = if (onBookend) {
            shelf.size
        } else {
            shelf.indexOfFirst { it.id == focusedId }.takeIf { it >= 0 } ?: oldIndex.coerceAtMost(shelf.size - 1)
        }
        // A glide in progress survives a refresh unless the focused book moved underneath it.
        moveTo(index, jump = index != oldIndex || shelf.getOrNull(index)?.id != focusedId)
    }

    private fun moveTo(index: Int, jump: Boolean = false) {
        if (jump) focusJumps++
        focusedIndex = index.coerceIn(0, items.size)
    }
}
