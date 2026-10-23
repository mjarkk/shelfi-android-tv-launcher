package com.mkopenga.tvhomescreen.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.channels.Channel
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sign

/** Focus and the camera share it so they arrive together. */
internal const val ShelfOmega = 20f

/**
 * Hand-rolled instead of an Animatable so a jump (other shelf, re-sort) lands synchronously with the state change
 * that caused it; an Animatable's snapTo is a suspend call that can lose the race against the next frame. A new
 * target keeps the current velocity, so a held D-pad glides instead of restarting from rest on every repeat.
 * [unit] is the size of one step in the target's units, which sets when it counts as at rest.
 *
 * Read [position] only from layout or draw, never in composition.
 */
@Stable
internal open class Spring(initial: Float, private val omega: Float, unit: Float = 1f) {
    private val state = mutableFloatStateOf(initial)
    val position: Float get() = state.floatValue

    var target = initial
        private set
    private var velocity = 0f
    private val restDistance = 0.0005f * unit
    private val restVelocity = 0.01f * unit
    private val wake = Channel<Unit>(Channel.CONFLATED)

    fun glideTo(to: Float, maxGlide: Float = Float.POSITIVE_INFINITY) {
        target = to
        val distance = target - state.floatValue
        if (abs(distance) > maxGlide) {
            state.floatValue = target - sign(distance) * maxGlide
            velocity = 0f
        }
        wake.trySend(Unit)
    }

    fun jumpTo(to: Float) {
        target = to
        velocity = 0f
        state.floatValue = target
    }

    suspend fun run() {
        while (true) {
            if (atRest()) wake.receive()
            var last = -1L
            while (!atRest()) {
                withFrameNanos { now ->
                    // The first frame after a key press moves too, rather than only recording a start time.
                    step(if (last < 0) NominalFrame else (now - last) / 1e9f)
                    last = now
                }
            }
        }
    }

    private fun atRest(): Boolean = state.floatValue == target && velocity == 0f

    // Exact solution of x'' = -ω²x - 2ωx' over dt, so frame time hiccups can't destabilise it.
    private fun step(dt: Float) {
        val x0 = state.floatValue - target
        val v0 = velocity
        val decay = exp(-omega * dt)
        val b = v0 + omega * x0
        val x = (x0 + b * dt) * decay
        val v = (v0 - omega * b * dt) * decay
        if (abs(x) < restDistance && abs(v) < restVelocity) {
            state.floatValue = target
            velocity = 0f
        } else {
            state.floatValue = target + x
            velocity = v
        }
    }

    private companion object {
        const val NominalFrame = 1 / 60f
    }
}

@Stable
internal class FocusMotion(initial: Int, omega: Float = ShelfOmega) : Spring(initial.toFloat(), omega) {
    // A far jump (Back to the first item) skips ahead instead of strobing through every cover on the way.
    fun glideTo(index: Int) = glideTo(index.toFloat(), MaxGlide)

    fun jumpTo(index: Int) = jumpTo(index.toFloat())

    private companion object {
        const val MaxGlide = 6f
    }
}
