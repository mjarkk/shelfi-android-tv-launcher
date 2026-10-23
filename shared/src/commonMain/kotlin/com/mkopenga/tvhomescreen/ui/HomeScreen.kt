package com.mkopenga.tvhomescreen.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.mkopenga.tvhomescreen.LauncherRepository
import com.mkopenga.tvhomescreen.NavAction
import com.mkopenga.tvhomescreen.Shelf
import com.mkopenga.tvhomescreen.toNavAction
import com.mkopenga.tvhomescreen.ui.settings.Ducks
import com.mkopenga.tvhomescreen.ui.settings.SettingsMotion
import com.mkopenga.tvhomescreen.ui.settings.SettingsWall
import com.mkopenga.tvhomescreen.ui.wallpaper.LocalWallpaperController
import com.mkopenga.tvhomescreen.ui.wallpaper.Wall
import com.mkopenga.tvhomescreen.ui.wallpaper.WallScene
import com.mkopenga.tvhomescreen.ui.wallpaper.Wallpaper
import com.mkopenga.tvhomescreen.ui.wallpaper.WallpaperController
import com.mkopenga.tvhomescreen.ui.wallpaper.rememberWallpaperController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun HomeScreen(
    repository: LauncherRepository,
    wallpapers: WallpaperController = rememberWallpaperController(),
    camera: ShelfCamera = remember { ShelfCamera() },
) {
    val typography = rememberTypography()
    val wall = remember(wallpapers) { WallScene(wallpapers) }
    val settings = remember { SettingsMotion() }
    CompositionLocalProvider(
        LocalTypography provides typography,
        LocalWallpaperController provides wallpapers,
    ) {
        DesignCanvas(Palette.Background) {
            Wall(wall, drop = { settings.shelfDrop }, pan = camera::px, roomWidth = { camera.roomWidth })
            HomeContent(repository, settings, camera)
        }
    }
}

@Composable
private fun HomeContent(repository: LauncherRepository, settings: SettingsMotion, camera: ShelfCamera) {
    val scope = rememberCoroutineScope()
    val controller = remember(repository) { HomeController(repository, scope, settings, camera) }
    val focusRequester = remember { FocusRequester() }
    val wallpapers = LocalWallpaperController.current

    LaunchedEffect(controller) { controller.motion.run() }
    LaunchedEffect(controller) { controller.camera.run() }
    LaunchedEffect(controller) { controller.settings.run() }
    LaunchedEffect(controller) { repository.shelves.collect(controller::onShelves) }
    LaunchedEffect(controller) { repository.hiddenIds.collect(controller::onHidden) }
    LaunchedEffect(controller) { repository.duckColour.collect(controller::onDuckColour) }
    LaunchedEffect(controller, wallpapers) { wallpapers.current.collect { controller.onWallpaper(wallpapers, it) } }
    LaunchedEffect(controller) { repository.homeEvents.collect { controller.onHome() } }
    LaunchedEffect(controller) { controller.artworks.followFocus(controller.state) }
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }

    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                // Key-ups of handled keys are consumed too, so Back's key-up never reaches the Activity.
                val action = event.key.toNavAction() ?: return@onPreviewKeyEvent false
                if (event.type == KeyEventType.KeyDown) controller.onAction(action)
                true
            }
            .focusRequester(focusRequester)
            .focusable(),
    ) {
        val ducks = remember { Ducks() }
        Shelf(controller.state, controller.motion, controller.camera, controller.artworks, controller.press, ducks, drop = { controller.settings.shelfDrop })
        SettingsWall(controller.state, controller.settings, repository, ducks)
    }
}

/** Glue between keys, the navigation state, the springs, the press feedback and the outside world. Main thread only. */
@Stable
private class HomeController(
    private val repository: LauncherRepository,
    private val scope: CoroutineScope,
    val settings: SettingsMotion,
    val camera: ShelfCamera,
) {
    val state = HomeState(repository.shelves.value, repository.hiddenIds.value, repository.duckColour.value)
    val motion = FocusMotion(state.focusedIndex)
    val artworks = ArtworkStore(repository)
    val press = PressFeedback()
    private var seenJumps = state.focusJumps
    private var launching: Job? = null
    private var heldShelves: List<Shelf>? = null
    private var wallpapers: WallpaperController? = null

    init {
        camera.follow(state, jumped = true, skippedTo = null)
    }

    fun onAction(action: NavAction) {
        val effect = state.onAction(action)
        sync()
        when (effect) {
            is HomeEffect.Launch -> launchApp(effect.id)
            is HomeEffect.SetHidden -> repository.setHidden(effect.id, effect.hidden)
            is HomeEffect.SelectWallpaper -> wallpapers?.select(effect.id)
            is HomeEffect.SetDuckColour -> repository.setDuckColour(effect.name)
            HomeEffect.OpenSystemSettings -> repository.openSystemSettings()
            null -> Unit
        }
    }

    private fun launchApp(id: String) {
        // A launch re-sorts the shelf (most recent first). Holding that back until the app has opened keeps
        // the books from reshuffling under the press; the new order is laid out while nobody is looking.
        // Started before launch() so a repository that publishes synchronously is held too.
        launching = scope.launch {
            delay(LaunchSettleMillis)
            heldShelves?.let(::apply)
            heldShelves = null
        }
        repository.launch(id)
        scope.launch { press.play(id) }
    }

    fun onShelves(shelves: List<Shelf>) {
        if (launching?.isActive == true) heldShelves = shelves else apply(shelves)
    }

    fun onHome() {
        state.goHome()
        sync()
    }

    fun onHidden(ids: Set<String>) {
        state.updateHidden(ids)
        sync()
    }

    fun onDuckColour(name: String?) {
        state.updateDuckColour(name)
    }

    fun onWallpaper(controller: WallpaperController, current: Wallpaper) {
        wallpapers = controller
        state.updateWallpapers(controller.wallpapers, current.id)
        sync()
    }

    private fun apply(shelves: List<Shelf>) {
        state.updateShelves(shelves)
        sync()
    }

    // State and spring change in the same snapshot, so the next frame lays out the new focus with the new items.
    private fun sync() {
        val jumped = state.focusJumps != seenJumps
        val from = motion.position
        if (jumped) {
            seenJumps = state.focusJumps
            motion.jumpTo(state.focusedIndex)
        } else {
            motion.glideTo(state.focusedIndex)
        }
        // Only a far glide moves focus on the spot.
        val skippedTo = if (!jumped && motion.position != from) motion.position.roundToInt() else null
        camera.follow(state, jumped, skippedTo)
        settings.sync(state)
    }

    private companion object {
        const val LaunchSettleMillis = 1500L
    }
}
