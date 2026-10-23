package com.mkopenga.tvhomescreen.ui.settings

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.mkopenga.tvhomescreen.ui.DesignHeight
import com.mkopenga.tvhomescreen.ui.FocusMotion
import com.mkopenga.tvhomescreen.ui.HomeState
import com.mkopenga.tvhomescreen.ui.Screen
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.ceil

/** One level of the pan: a whole screen, so each level leaves entirely as the wall above it comes down into view. */
internal const val PanDistance = DesignHeight.toFloat()

/**
 * How high up the wall the eye is for [this] screen: the shelf, the frames one screen above it, and the pages the
 * frames open another screen higher.
 */
internal val Screen.level: Int
    get() = when (this) {
        Screen.Shelf -> 0
        Screen.Frames -> 1
        Screen.WallpaperPicker, Screen.HiddenApps -> 2
    }

/**
 * The springs of the settings wall. Like the shelf's own [FocusMotion], they are read only in layout and draw, so
 * nothing recomposes while they move. One pan carries the eye up the room and back down, level by level.
 */
@Stable
internal class SettingsMotion {
    /** The [level] being looked at: 0 the shelf, 1 the frames, 2 a page opened from a frame. */
    val pan = FocusMotion(0, omega = 9f)
    val frame = FocusMotion(0)
    val wallpaper = FocusMotion(0)

    /** The hide-apps grid's first visible row. */
    val appRow = FocusMotion(0, omega = 16f)

    private var seen = Screen.Shelf

    /** How far the room has moved down, in design px: 0 on the shelf, up to two screens on a page. Read only in layout or draw. */
    val shelfDrop: Float get() = pan.position * PanDistance

    /**
     * The highest level any of which is on screen. Unlike [pan] it changes only on the key that starts a pan up and as
     * a pan down settles, so composition may read it to decide what to compose; a derived state over [pan] would
     * recompose every frame of the pan.
     */
    var reach: Int by mutableIntStateOf(0)
        private set

    /** Call after every state change, in the same snapshot. Whatever comes into view starts in place instead of gliding there. */
    fun sync(state: HomeState) {
        val screen = state.screen
        val entered = screen != seen
        pan.glideTo(screen.level)
        if (screen.level > reach) reach = screen.level
        frame.follow(state.frame.ordinal, jump = entered && seen == Screen.Shelf)
        wallpaper.follow(state.pickerIndex, jump = entered && screen == Screen.WallpaperPicker)
        appRow.follow(firstVisibleRow(state.appIndex, state.allItems.size), jump = entered && screen == Screen.HiddenApps)
        seen = screen
    }

    suspend fun run() {
        coroutineScope {
            for (spring in listOf(pan, frame, wallpaper, appRow)) launch { spring.run() }
            snapshotFlow { ceil(pan.position).toInt() }.collect { reach = it }
        }
    }

    private fun FocusMotion.follow(index: Int, jump: Boolean) = if (jump) jumpTo(index) else glideTo(index)
}
