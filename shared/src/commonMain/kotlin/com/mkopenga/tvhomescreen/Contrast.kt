package com.mkopenga.tvhomescreen

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

const val MinTextContrast = 4.5f

/** WCAG 2 contrast ratio, from 1 (identical) to 21 (black on white). Alpha is ignored. */
fun contrastRatio(a: Color, b: Color): Float {
    val la = a.copy(alpha = 1f).luminance()
    val lb = b.copy(alpha = 1f).luminance()
    return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
}

/**
 * Returns this color unchanged if [ink] already reaches [minRatio] on it, otherwise the smallest shift towards
 * black (light ink) or white (dark ink) that does. If even pure black/white falls short, returns that.
 */
fun Color.adjustedForInk(ink: Color, minRatio: Float = MinTextContrast): Color {
    if (contrastRatio(this, ink) >= minRatio) return this
    val target = if (ink.luminance() > 0.5f) Color.Black else Color.White
    var lo = 0f
    var hi = 1f
    repeat(16) {
        val mid = (lo + hi) / 2
        if (contrastRatio(lerp(this, target, mid), ink) >= minRatio) hi = mid else lo = mid
    }
    return lerp(this, target, hi)
}
