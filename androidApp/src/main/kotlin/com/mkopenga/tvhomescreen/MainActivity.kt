package com.mkopenga.tvhomescreen

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnPreDraw

// No dispatchGenericMotionEvent override on purpose: Compose leaves joystick motion unhandled, so ViewRootImpl's
// SyntheticJoystickHandler turns left-stick and hat motion into repeating D-pad key events for the UI.
class MainActivity : ComponentActivity() {
    private val repository by lazy { (application as ShelfiApplication).repository }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).run {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        // A home activity must never finish. Back reaches this only after the UI left the key event unconsumed.
        onBackPressedDispatcher.addCallback(this) {}
        setContent { App(repository) }
        // The theme's background covers the launch until Compose draws. After that the opaque wall hides it, and
        // painting it anyway would cost a full-screen fill every frame.
        window.decorView.doOnPreDraw { it.post { window.setBackgroundDrawable(null) } }
    }

    override fun onResume() {
        super.onResume()
        repository.refresh()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Launcher3's test: Home pressed while already home keeps window focus and isn't a bring-to-front.
        val alreadyHome = hasWindowFocus() && intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT == 0
        if (alreadyHome && intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            repository.onHomePressed()
        }
    }
}
