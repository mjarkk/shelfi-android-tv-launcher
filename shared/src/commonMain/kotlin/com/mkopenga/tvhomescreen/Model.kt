package com.mkopenga.tvhomescreen

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap

// Shapes as drawn in design/Shelf.dc.html: Dot = filled circle, Ring = outlined circle, Bar = short wide bar,
// Leaf = square rounded on two opposite corners, Square = outlined square, Tile = filled block,
// Triangle = filled, pointing up, Diamond = filled square rotated 45°.
enum class SpineMark { Dot, Bar, Leaf, Square, Ring, Triangle, Tile, Diamond }

@Immutable
data class ShelfItem(
    /** Stable across refreshes; the package name on Android. */
    val id: String,
    val title: String,
    val spineColor: Color,
    /** Text on spine and cover; at least 4.5:1 against [spineColor]. */
    val inkColor: Color,
    val markColor: Color,
    val mark: SpineMark,
    /** Info line under the shelf, e.g. "Exploration · 9 of 12 seeds found · Last played yesterday". */
    val subtitle: String,
    val isGame: Boolean,
    /** Epoch millis; null if never launched from Shelfi. */
    val lastUsedAt: Long?,
    /** Spine text when its casing differs from [title], e.g. "IRON SAINTS"; covers and the info line use [title]. */
    val spineLabel: String = title,
    /** Spine size in 1920x1080 design px; null derives a size from [id]. */
    val spineWidth: Int? = null,
    val spineHeight: Int? = null,
    /**
     * An icon from [LauncherRepository.spineIcon] replaces [mark]; the spine keeps room for it before it loads. False
     * for no icon, or only the platform's generic one.
     */
    val spineIcon: Boolean = false,
)

@Immutable
data class Shelf(val id: String, val title: String, val items: List<ShelfItem>)

/**
 * [backdrop] belongs to [banner], else [icon]. For a solid or gradient backdrop the banner arrives already cropped by
 * [backdropInset], as [Backdrop]'s positions assume.
 */
@Immutable
class Artwork(val icon: ImageBitmap?, val banner: ImageBitmap?, val backdrop: Backdrop? = null)
