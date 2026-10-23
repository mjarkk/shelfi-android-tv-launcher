package com.mkopenga.tvhomescreen.ui.wallpaper

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mkopenga.tvhomescreen.fake.FakeLauncherRepository
import com.mkopenga.tvhomescreen.ui.HomeScreen
import com.mkopenga.tvhomescreen.ui.Palette
import com.mkopenga.tvhomescreen.ui.SceneDriver
import com.mkopenga.tvhomescreen.ui.rememberTypography
import java.io.File
import kotlin.random.Random

/** The home screen on every wallpaper (default focus), and a sheet of every thumbnail as a picker would show them. */
internal fun renderWallpaperSnapshots(dir: File, only: Set<String> = emptySet()) {
    for ((i, wallpaper) in Wallpapers.withIndex()) {
        if (only.isNotEmpty() && wallpaper.id !in only) continue
        SceneDriver(content = {
            val controller = remember { CyclingWallpaperController(random = Random(0)).apply { select(wallpaper.id) } }
            HomeScreen(FakeLauncherRepository(), controller)
        }).use {
            it.settle()
            it.save(File(dir, "wallpaper-%02d-%s.png".format(i + 1, wallpaper.id)))
        }
    }
    val rows = Wallpapers.chunked(5)
    SceneDriver(width = 1920, height = 40 + rows.size * 300, content = {
        val typography = rememberTypography()
        Column(
            Modifier.fillMaxSize().background(Palette.Background).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            for (row in rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    for (wallpaper in row) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            WallpaperThumbnail(wallpaper, Modifier.size(356.dp, 200.dp))
                            BasicText(wallpaper.name, style = typography.infoSubtitle)
                        }
                    }
                }
            }
        }
    }).use { it.save(File(dir, "wallpaper-thumbnails.png")) }
}
