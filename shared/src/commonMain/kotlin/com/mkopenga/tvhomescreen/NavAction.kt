package com.mkopenga.tvhomescreen

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

enum class NavAction { Up, Down, Left, Right, Confirm, Back, Sort, PrevShelf, NextShelf, Menu }

/** Null for key-up events and unmapped keys; auto-repeated key-downs map like the first press. */
fun KeyEvent.toNavAction(): NavAction? =
    if (type == KeyEventType.KeyDown) key.toNavAction() else null

internal fun Key.toNavAction(): NavAction? = when (this) {
    Key.DirectionUp -> NavAction.Up
    Key.DirectionDown -> NavAction.Down
    Key.DirectionLeft -> NavAction.Left
    Key.DirectionRight -> NavAction.Right
    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.ButtonA -> NavAction.Confirm
    Key.Back, Key.Escape, Key.Backspace, Key.ButtonB -> NavAction.Back
    Key.ButtonX, Key.X -> NavAction.Sort
    Key.ButtonL1, Key.Q, Key.PageUp -> NavAction.PrevShelf
    Key.ButtonR1, Key.E, Key.PageDown -> NavAction.NextShelf
    Key.Menu, Key.ButtonY, Key.M -> NavAction.Menu
    else -> null
}
