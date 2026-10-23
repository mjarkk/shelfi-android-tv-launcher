package com.mkopenga.tvhomescreen

import androidx.compose.runtime.Composable
import com.mkopenga.tvhomescreen.ui.HomeScreen

@Composable
fun App(repository: LauncherRepository) {
    HomeScreen(repository)
}
