package com.mkopenga.tvhomescreen.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import com.mkopenga.tvhomescreen.ShelfItem
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * [position] is the room's x at the screen's left edge, in design px. Main thread only; read [position] and [px] only
 * in layout and draw.
 */
@Stable
internal class ShelfCamera {
    private val spring = Spring(0f, ShelfOmega, unit = 100f)
    val position: Float get() = spring.position

    /** Design px, at least a screen; read only in draw. */
    var roomWidth: Float by mutableFloatStateOf(DesignWidth.toFloat())
        private set

    private var items: List<ShelfItem>? = null
    private lateinit var geometry: ShelfGeometry

    /** Whole device px, so everything in the room moves in step. */
    fun px(density: Float): Int = (position * density).roundToInt().coerceIn(0, maxPx(roomWidth, density))

    /** Call after every state change, in the same snapshot. [skippedTo] is where a far glide made focus skip ahead. */
    fun follow(state: HomeState, jumped: Boolean, skippedTo: Int?) {
        val items = state.items
        if (items !== this.items) {
            this.items = items
            geometry = ShelfGeometry(items)
            roomWidth = geometry.roomWidth
        }
        val g = geometry
        // Settings hang above wherever the view is, so the camera holds while they're open.
        if (state.screen != Screen.Shelf) {
            if (spring.target > g.maxCamera) spring.glideTo(g.maxCamera)
            return
        }
        val index = state.focusedIndex
        when {
            jumped -> spring.jumpTo(g.cameraFor(index, spring.target))
            skippedTo != null -> {
                // Lands where it would be for the book focus skips to, so that book is in view as both glide on.
                spring.jumpTo(g.cameraFor(skippedTo, spring.target))
                spring.glideTo(g.cameraFor(index, spring.target))
            }
            else -> spring.glideTo(g.cameraFor(index, spring.target))
        }
    }

    suspend fun run() = spring.run()

    companion object {
        fun maxPx(roomWidth: Float, density: Float): Int = floor((roomWidth - DesignWidth) * density).toInt().coerceAtLeast(0)
    }
}
