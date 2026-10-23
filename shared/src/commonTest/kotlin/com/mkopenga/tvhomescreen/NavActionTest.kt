package com.mkopenga.tvhomescreen

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NavActionTest {
    private val expected = mapOf(
        NavAction.Up to listOf(Key.DirectionUp),
        NavAction.Down to listOf(Key.DirectionDown),
        NavAction.Left to listOf(Key.DirectionLeft),
        NavAction.Right to listOf(Key.DirectionRight),
        NavAction.Confirm to listOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.ButtonA),
        NavAction.Back to listOf(Key.Back, Key.Escape, Key.Backspace, Key.ButtonB),
        NavAction.Sort to listOf(Key.ButtonX, Key.X),
        NavAction.PrevShelf to listOf(Key.ButtonL1, Key.Q, Key.PageUp),
        NavAction.NextShelf to listOf(Key.ButtonR1, Key.E, Key.PageDown),
        NavAction.Menu to listOf(Key.Menu, Key.ButtonY, Key.M),
    )

    @Test
    fun remoteGamepadAndKeyboardKeysMapToActions() {
        for ((action, keys) in expected) {
            for (key in keys) assertEquals(action, key.toNavAction(), "$key")
        }
    }

    @Test
    fun everyActionIsReachable() {
        assertEquals(NavAction.entries.toSet(), expected.keys)
    }

    @Test
    fun unrelatedKeysAreIgnored() {
        for (key in listOf(Key.A, Key.Spacebar, Key.Tab, Key.VolumeUp, Key.ButtonL2)) {
            assertNull(key.toNavAction(), "$key")
        }
    }
}
