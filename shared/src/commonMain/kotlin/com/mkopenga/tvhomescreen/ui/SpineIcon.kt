package com.mkopenga.tvhomescreen.ui

import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/** In spine px; [titleLength] runs up from [SpinePadding] above the bottom. */
internal class SpineLayout private constructor(val iconTop: Float, val iconSize: Float, val titleLength: Float) {
    companion object {
        private const val MaxIcon = 48f
        private const val MinIconMargin = 14f
        private const val TitleGap = 16f

        /** With [icon], the margin above it matches those beside it. */
        fun of(width: Float, height: Float, icon: Boolean): SpineLayout {
            if (!icon) return SpineLayout(SpinePadding, MarkExtent, height - 2 * SpinePadding - MarkExtent - TitleGap)
            val size = minOf(MaxIcon, width - 2 * MinIconMargin).roundToInt().toFloat()
            val top = ((width - size) / 2).roundToInt().toFloat()
            return SpineLayout(top, size, height - SpinePadding - (top + size) - TitleGap)
        }
    }
}

/** [icon] arrives already shaped (see [com.mkopenga.tvhomescreen.LauncherRepository.spineIcon]); this only places it. */
internal fun DrawScope.drawSpineIcon(icon: ImageBitmap, layout: SpineLayout, centreX: Float) {
    val size = layout.iconSize.roundToInt()
    drawImage(
        icon,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(icon.width, icon.height),
        dstOffset = IntOffset((centreX - layout.iconSize / 2).roundToInt(), layout.iconTop.roundToInt()),
        dstSize = IntSize(size, size),
        filterQuality = FilterQuality.Medium,
    )
}
