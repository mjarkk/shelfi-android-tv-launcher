package com.mkopenga.tvhomescreen

import android.app.Application

class ShelfiApplication : Application() {
    val repository: AndroidLauncherRepository by lazy { AndroidLauncherRepository(this) }
}
