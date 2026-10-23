package com.mkopenga.tvhomescreen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mkopenga.tvhomescreen.Artwork
import com.mkopenga.tvhomescreen.LauncherRepository
import com.mkopenga.tvhomescreen.Shelf
import com.mkopenga.tvhomescreen.fake.FakeLauncherRepository
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal fun renderBackdropSnapshots(dir: File) {
    val apps = listOf(
        analysedApp("Soundwave", banner = FixtureArt.soundCloudLike),
        analysedApp("Kijken", banner = FixtureArt.kijkLike),
        analysedApp("Lagoon", banner = FixtureArt.verticalGradient),
        analysedApp("Dusk", banner = FixtureArt.diagonalSunset),
        analysedApp("Snapshot", banner = FixtureArt.photo),
        analysedApp("Overflow", banner = FixtureArt.logoOffTheEdge),
        analysedApp("Contrast", banner = FixtureArt.steepGradient),
        analysedApp("Tile", icon = FixtureArt.roundedIcon),
        analysedApp("Badge", icon = FixtureArt.clearIcon),
    )
    val repository = AnalysedRepository(apps)
    val rows = apps.chunked(3)
    SceneDriver(width = 1920, height = 80 + rows.size * (CoverHeight.toInt() + 90), content = {
        val typography = rememberTypography()
        val measurer = rememberTextMeasurer(cacheSize = 0)
        val caption = TextStyle(fontFamily = typography.family, fontSize = 18.sp, color = Color(0xFF3B2717))
        Column(
            Modifier.fillMaxSize().background(Color(0xFFE8DCC0)).padding(start = 64.dp, top = 40.dp),
            verticalArrangement = Arrangement.spacedBy(40.dp),
        ) {
            for (row in rows) {
                // A store loads only around its shelf's focus, which reaches every book of a row this short.
                val artworks = remember(row) { ArtworkStore(repository) }
                LaunchedEffect(row) { artworks.followFocus(HomeState(listOf(Shelf("row", "Row", row.map { it.item })))) }
                Row(horizontalArrangement = Arrangement.spacedBy(40.dp), verticalAlignment = Alignment.Bottom) {
                    for (app in row) {
                        val painter = remember(app) { ItemPainter(app.item, typography, measurer) }
                        val spine = ShelfGeometry.spineWidthOf(app.item).toFloat()
                        Column(Modifier.width(552.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Spacer(Modifier.size(spine.dp, ShelfGeometry.spineHeightOf(app.item).dp).drawBehind { painter.drawSpine(this, artworks) })
                                Spacer(Modifier.size(CoverWidth.dp, CoverHeight.dp).drawBehind { painter.drawCover(this, artworks) })
                            }
                            BasicText("${app.item.title} · ${app.artwork.backdrop?.let { it::class.simpleName } ?: "no background"}", style = caption, maxLines = 1)
                        }
                    }
                }
            }
        }
    }).use {
        it.settle()
        it.save(File(dir, "backdrop-01-sheet.png"))
    }
    SceneDriver(repository).use {
        it.settle()
        it.save(File(dir, "backdrop-02-shelf.png"))
        it.press(Key.DirectionRight)
        it.advance(120)
        it.save(File(dir, "backdrop-03-mid-turn.png"))
        it.settle()
        it.save(File(dir, "backdrop-04-shelf-next.png"))
    }
}

private class AnalysedRepository(private val apps: List<AnalysedApp>) : LauncherRepository by FakeLauncherRepository() {
    override val shelves: StateFlow<List<Shelf>> = MutableStateFlow(listOf(Shelf("all", "All", apps.map { it.item })))

    override suspend fun artwork(itemId: String): Artwork? = apps.firstOrNull { it.item.id == itemId }?.artwork

    override suspend fun spineIcon(itemId: String): ImageBitmap? = apps.firstOrNull { it.item.id == itemId }?.spineIcon
}
