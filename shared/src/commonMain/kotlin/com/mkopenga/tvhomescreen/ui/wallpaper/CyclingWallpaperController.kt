package com.mkopenga.tvhomescreen.ui.wallpaper

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.TimeSource

/**
 * Starts on a random wallpaper and moves to a different random one every [period] while [run] runs. [select]
 * shows the chosen one at once and starts a fresh period. Main thread only.
 */
@Stable
internal class CyclingWallpaperController(
    override val wallpapers: List<Wallpaper> = Wallpapers,
    private val random: Random = Random.Default,
    private val period: Duration = 1.hours,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : WallpaperController {
    init {
        require(wallpapers.isNotEmpty())
    }

    private val state = MutableStateFlow(wallpapers[random.nextInt(wallpapers.size)])
    override val current: StateFlow<Wallpaper> = state.asStateFlow()

    private var next = otherThan(state.value)
    private var shownAt = timeSource.markNow()
    private val restarts = Channel<Unit>(Channel.CONFLATED)

    override fun upcoming(): Wallpaper = next

    override fun select(id: String) {
        show(wallpapers.firstOrNull { it.id == id } ?: return)
    }

    /** Moves on if the period is up. Returns how long until it next will. */
    fun tick(): Duration {
        val left = period - shownAt.elapsedNow()
        if (left > Duration.ZERO) return left
        show(next)
        return period
    }

    suspend fun run(): Nothing {
        while (true) {
            withTimeoutOrNull(tick()) { restarts.receive() }
        }
    }

    private fun show(wallpaper: Wallpaper) {
        state.value = wallpaper
        shownAt = timeSource.markNow()
        next = otherThan(wallpaper)
        restarts.trySend(Unit)
    }

    private fun otherThan(wallpaper: Wallpaper): Wallpaper {
        val others = wallpapers.filter { it != wallpaper }
        return if (others.isEmpty()) wallpaper else others[random.nextInt(others.size)]
    }
}

/** Where [rememberWallpaperController] gets its randomness; snapshots provide a seeded one. */
internal val LocalWallpaperRandom = staticCompositionLocalOf<Random> { Random.Default }

@Composable
internal fun rememberWallpaperController(): WallpaperController {
    val random = LocalWallpaperRandom.current
    val controller = remember { CyclingWallpaperController(random = random) }
    LaunchedEffect(controller) { controller.run() }
    return controller
}
