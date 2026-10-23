package com.mkopenga.tvhomescreen.ui.settings

import com.mkopenga.tvhomescreen.ui.wallpaper.Wallpaper

/** The frames on the wall above the shelf, left to right. */
internal enum class SettingsFrame { Wallpaper, HiddenApps, Duck, System }

/** The hide-apps page is a grid this many cards wide, showing this many rows at a time. */
internal const val HiddenAppsColumns = 4
internal const val HiddenAppsVisibleRows = 6

/** The hide-apps grid's first visible row: focus rides on the third visible row while there are rows left to scroll. */
internal fun firstVisibleRow(appIndex: Int, count: Int): Int {
    val rows = (count + HiddenAppsColumns - 1) / HiddenAppsColumns
    return (appIndex / HiddenAppsColumns - 2).coerceIn(0, (rows - HiddenAppsVisibleRows).coerceAtLeast(0))
}

/** How many other wallpapers the Wallpaper frame fans out. */
internal const val WallpaperSampleCount = 3

/**
 * The Wallpaper frame's samples: the wallpapers after [current] in [all]'s order, wrapping round. Never [current]
 * itself, which would read as a hole cut in the same wallpaper around the frame.
 */
internal fun wallpaperSamples(all: List<Wallpaper>, current: Wallpaper?): List<Wallpaper> {
    val start = all.indexOfFirst { it.id == current?.id } + 1
    return all.indices.map { all[(start + it) % all.size] }.filter { it.id != current?.id }.take(WallpaperSampleCount)
}
