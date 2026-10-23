package com.mkopenga.tvhomescreen.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.min

internal const val DesignWidth = 1920
internal const val DesignHeight = 1080

/**
 * Lays [content] out on a 1920x1080 canvas where one design px of the mockup is 1.dp, scaled to fit the available
 * space and letterboxed to 16:9. Font scale is pinned to 1 so the TV's accessibility font size can't break the layout.
 *
 * [content] paints the whole canvas; only the bars around it are filled with [letterbox]. A fill under the opaque
 * wall would cost the GPU a full-screen pass every frame for nothing.
 */
@Composable
internal fun DesignCanvas(letterbox: Color, content: @Composable BoxScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().drawBehind { drawLetterbox(letterbox) }, contentAlignment = Alignment.Center) {
        val scale = min(constraints.maxWidth / DesignWidth.toFloat(), constraints.maxHeight / DesignHeight.toFloat())
        val density = remember(scale) { Density(scale, fontScale = 1f) }
        CompositionLocalProvider(LocalDensity provides density) {
            Box(Modifier.size(DesignWidth.dp, DesignHeight.dp).clipToBounds(), content = content)
        }
    }
}

private fun DrawScope.drawLetterbox(color: Color) {
    val scale = min(size.width / DesignWidth, size.height / DesignHeight)
    // A pixel of overlap, so rounding never leaves a seam between a bar and the canvas.
    val side = (size.width - DesignWidth * scale) / 2 + 1f
    val top = (size.height - DesignHeight * scale) / 2 + 1f
    if (side > 1.5f) {
        drawRect(color, size = Size(side, size.height))
        drawRect(color, Offset(size.width - side, 0f), Size(side, size.height))
    }
    if (top > 1.5f) {
        drawRect(color, size = Size(size.width, top))
        drawRect(color, Offset(0f, size.height - top), Size(size.width, top))
    }
}
