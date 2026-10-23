package com.mkopenga.tvhomescreen

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface LauncherRepository {
    val shelves: StateFlow<List<Shelf>>
    val profileName: StateFlow<String>

    /** Emits when Home is pressed while Shelfi is already the foreground home; the UI resets to the first shelf and item. */
    val homeEvents: Flow<Unit>

    /**
     * Main-safe and cached; null for unknown ids or items without artwork.
     * The images are for drawing only: on Android they are hardware bitmaps, so `readPixels` throws.
     */
    suspend fun artwork(itemId: String): Artwork?

    /**
     * Small, shaped alike for every app (see [ShelfItem.spineIcon]), and cheap enough to load for the whole shelf.
     * Main-safe and cached like [artwork]; null without one.
     */
    suspend fun spineIcon(itemId: String): ImageBitmap? = null

    /** Unknown ids and apps that can no longer be started are ignored. */
    fun launch(itemId: String)

    /** Ids the user took off the shelf. Hidden apps stay in [shelves], since settings lists them; the UI leaves them out. */
    val hiddenIds: StateFlow<Set<String>>

    /** Persisted; ids of apps that are not installed are kept, so a reinstalled app stays hidden. */
    fun setHidden(itemId: String, hidden: Boolean)

    /**
     * The name of the ducks' colour, one of those the UI lists; null if it was never set. A name the UI doesn't know
     * gets its default. Known before the first frame, so a duck is never drawn in a colour about to change.
     */
    val duckColour: StateFlow<String?>

    /** Persisted. */
    fun setDuckColour(name: String)

    /** Opens the platform's own settings, if it has any; never throws. */
    fun openSystemSettings()
}
