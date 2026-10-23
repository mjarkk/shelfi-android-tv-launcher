package com.mkopenga.tvhomescreen.ui

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.ImageBitmap
import com.mkopenga.tvhomescreen.Artwork
import com.mkopenga.tvhomescreen.LauncherRepository
import com.mkopenga.tvhomescreen.ShelfItem
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

/**
 * Holds artwork only for books near the focus, nearest first; a cover shows for at most two books at a time, and
 * Android's hardware bitmaps are big. The repository caches and loads off the main thread, so evicting is cheap.
 * Spine icons are small and every visible spine shows one, so they're loaded for the whole shelf and kept.
 */
@Stable
internal class ArtworkStore(private val repository: LauncherRepository) {
    private val loaded = mutableStateMapOf<String, Artwork>()
    private val missing = HashSet<String>()

    // One state per book, so an icon arriving redraws only its own spine. Main thread only.
    private val spineIcons = HashMap<String, MutableState<ImageBitmap?>>()
    private val spineIconsAsked = HashSet<String>()

    /** Snapshot state: a draw that reads this redraws when the artwork arrives. */
    operator fun get(itemId: String): Artwork? = loaded[itemId]

    fun spineIcon(itemId: String): ImageBitmap? = spineIconState(itemId).value

    private fun spineIconState(itemId: String) = spineIcons.getOrPut(itemId) { mutableStateOf(null) }

    suspend fun followFocus(state: HomeState) = coroutineScope {
        launch { loadSpineIcons(state) }
        followCovers(state)
    }

    private suspend fun loadSpineIcons(state: HomeState) {
        snapshotFlow { state.items }.collect { items ->
            val withIcons = items.filter { it.spineIcon }.mapTo(HashSet()) { it.id }
            val order = ArrayList<String>(items.size)
            addAround(items, state.focusedIndex, items.size, order)
            for (id in order) {
                if (id !in withIcons || !spineIconsAsked.add(id)) continue
                repository.spineIcon(id)?.let { spineIconState(id).value = it }
            }
        }
    }

    private suspend fun followCovers(state: HomeState) {
        snapshotFlow { state.items to state.focusedIndex }
            .conflate()
            .collect { (items, focus) ->
                val keep = HashSet<String>()
                addAround(items, focus, KeepAround, keep)
                loaded.keys.retainAll(keep)
                val wanted = ArrayList<String>()
                addAround(items, focus, LoadAround, wanted)
                for (id in wanted) {
                    if (id in loaded || id in missing) continue
                    val artwork = repository.artwork(id)
                    if (artwork == null) missing += id else loaded[id] = artwork
                }
            }
    }

    private fun addAround(items: List<ShelfItem>, focus: Int, around: Int, into: MutableCollection<String>) {
        for (distance in 0..around) {
            items.getOrNull(focus + distance)?.let { into += it.id }
            if (distance > 0) items.getOrNull(focus - distance)?.let { into += it.id }
        }
    }

    private companion object {
        const val LoadAround = 3
        const val KeepAround = 8
    }
}
