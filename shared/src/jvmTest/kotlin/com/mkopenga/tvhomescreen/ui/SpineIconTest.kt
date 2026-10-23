package com.mkopenga.tvhomescreen.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.mkopenga.tvhomescreen.LauncherRepository
import com.mkopenga.tvhomescreen.Shelf
import com.mkopenga.tvhomescreen.ShelfItem
import com.mkopenga.tvhomescreen.SpineMark
import com.mkopenga.tvhomescreen.fake.FakeLauncherRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.skia.Bitmap

class SpineIconTest {
    private val iconColor = Color(0xFFD02020)
    private val markColor = Color(0xFFF3EEE3)

    @Test
    fun spineShowsTheAppIconInPlaceOfTheMark() {
        val pixels = renderSpine(item(spineIcon = true))
        val layout = SpineLayout.of(84f, 500f, icon = true)
        assertEquals(iconColor.toArgb(), pixels.getColor(42, (layout.iconTop + layout.iconSize / 2).toInt()))
    }

    @Test
    fun spineOfAnAppWithoutItsOwnIconKeepsTheMark() {
        // A Tile mark is a 22px square from 24px below the top.
        val pixels = renderSpine(item(spineIcon = false))
        assertEquals(markColor.toArgb(), pixels.getColor(42, 35))
    }

    private fun item(spineIcon: Boolean) = ShelfItem(
        id = "app",
        title = "App",
        spineColor = Color(0xFF2F4B6E),
        inkColor = markColor,
        markColor = markColor,
        mark = SpineMark.Tile,
        subtitle = "",
        isGame = false,
        lastUsedAt = null,
        spineWidth = 84,
        spineHeight = 500,
        spineIcon = spineIcon,
    )

    private fun renderSpine(item: ShelfItem): Bitmap {
        val icon = ImageBitmap(96, 96).also {
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(it), Size(96f, 96f)) { drawRect(iconColor) }
        }
        val repository = object : LauncherRepository by FakeLauncherRepository() {
            override suspend fun spineIcon(itemId: String): ImageBitmap = icon
        }
        return SceneDriver(width = 84, height = 500, content = {
            val typography = rememberTypography()
            val measurer = rememberTextMeasurer(cacheSize = 0)
            val store = remember { ArtworkStore(repository) }
            LaunchedEffect(Unit) { store.followFocus(HomeState(listOf(Shelf("one", "One", listOf(item))))) }
            val painter = remember { ItemPainter(item, typography, measurer) }
            Spacer(Modifier.fillMaxSize().drawBehind { painter.drawSpine(this, store) })
        }).use {
            it.settle()
            Bitmap.makeFromImage(it.frame())
        }
    }
}
