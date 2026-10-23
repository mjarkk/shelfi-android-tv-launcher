package com.mkopenga.tvhomescreen

import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.mkopenga.tvhomescreen.fake.FakeLauncherRepository

fun main() = application {
    val repository = remember { FakeLauncherRepository() }
    Window(
        onCloseRequest = ::exitApplication,
        title = "Shelfi",
        state = rememberWindowState(width = 1280.dp, height = 720.dp, position = WindowPosition(Alignment.Center)),
        onPreviewKeyEvent = { event ->
            // Stand-in for the remote's Home button, which a Mac keyboard doesn't have.
            val isHome = event.type == KeyEventType.KeyDown && (event.key == Key.MoveHome || event.key == Key.H)
            if (isHome) repository.pressHome()
            isHome
        },
    ) {
        App(repository)
    }
}
