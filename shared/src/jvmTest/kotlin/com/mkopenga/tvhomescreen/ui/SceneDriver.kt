package com.mkopenga.tvhomescreen.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.unit.Density
import com.mkopenga.tvhomescreen.App
import com.mkopenga.tvhomescreen.LauncherRepository
import com.mkopenga.tvhomescreen.ui.wallpaper.LocalWallpaperRandom
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.random.Random

/**
 * Renders the app headlessly at 1920x1080 with a virtual frame clock, so animations are deterministic: [advance]
 * renders 60 Hz frames, and key presses go through the same root key handler as on a device.
 */
internal class SceneDriver(
    width: Int = DesignWidth,
    height: Int = DesignHeight,
    density: Float = 1f,
    content: @Composable () -> Unit,
) : AutoCloseable {
    // A seeded wallpaper pick, so every run renders the same wall behind the shelf.
    constructor(repository: LauncherRepository) : this(content = {
        val random = remember { Random(WallpaperSeed) }
        CompositionLocalProvider(LocalWallpaperRandom provides random) { App(repository) }
    })

    private var nanos = 0L
    private val scene = ImageComposeScene(width, height, Density(density), content = content)

    init {
        advance(100)
    }

    fun frame(): Image {
        nanos += FrameNanos
        return scene.render(nanos)
    }

    fun advance(millis: Long) {
        val end = nanos + millis * 1_000_000
        while (nanos < end) frame()
    }

    // The skiko KeyEvent factory is the only way to synthesise a key without an AWT component; test-only use.
    @OptIn(InternalComposeUiApi::class)
    fun press(key: Key) {
        scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
        scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
    }

    /** Presses [key] [times] times, [gapMillis] apart, like a D-pad's key repeat. */
    fun repeat(key: Key, times: Int, gapMillis: Long = 40) {
        repeat(times) {
            press(key)
            advance(gapMillis)
        }
    }

    fun settle() = advance(1200)

    fun save(file: File) {
        val image = scene.render(nanos)
        file.parentFile.mkdirs()
        file.writeBytes(requireNotNull(image.encodeToData(EncodedImageFormat.PNG)).bytes)
    }

    override fun close() = scene.close()

    private companion object {
        const val FrameNanos = 16_666_667L
        const val WallpaperSeed = 1
    }
}
