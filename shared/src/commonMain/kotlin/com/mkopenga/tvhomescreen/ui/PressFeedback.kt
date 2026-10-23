package com.mkopenga.tvhomescreen.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The quick press-in of a launched book. It animates the book's scale, which the focus spring never touches, so
 * the two can't fight; it is read in placement only.
 */
@Stable
internal class PressFeedback {
    private var pressedId by mutableStateOf<String?>(null)
    private val scale = Animatable(1f)

    fun scaleFor(itemId: String): Float = if (pressedId == itemId) scale.value else 1f

    suspend fun play(itemId: String) {
        pressedId = itemId
        scale.snapTo(1f)
        scale.animateTo(0.94f, tween(durationMillis = 70))
        scale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 700f))
    }
}
