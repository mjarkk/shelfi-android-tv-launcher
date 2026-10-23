package com.mkopenga.tvhomescreen.fake

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import com.mkopenga.tvhomescreen.Artwork
import com.mkopenga.tvhomescreen.LauncherRepository
import com.mkopenga.tvhomescreen.Shelf
import com.mkopenga.tvhomescreen.ShelfItem
import com.mkopenga.tvhomescreen.SpineMark

class FakeLauncherRepository(
    hidden: Set<String> = emptySet(),
    shelves: List<Shelf> = FakeShelves.all,
    duckColour: String? = null,
) : LauncherRepository {
    private val homeEventsFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val hiddenState = MutableStateFlow(hidden)
    private val duckColourState = MutableStateFlow(duckColour)

    override val shelves: StateFlow<List<Shelf>> = MutableStateFlow(shelves).asStateFlow()
    override val profileName: StateFlow<String> = MutableStateFlow("Mara").asStateFlow()
    override val homeEvents: Flow<Unit> = homeEventsFlow.asSharedFlow()
    override val hiddenIds: StateFlow<Set<String>> = hiddenState.asStateFlow()
    override val duckColour: StateFlow<String?> = duckColourState.asStateFlow()

    override suspend fun artwork(itemId: String): Artwork? = null

    override fun launch(itemId: String) {
        println("launch $itemId")
    }

    override fun setHidden(itemId: String, hidden: Boolean) {
        hiddenState.value = if (hidden) hiddenState.value + itemId else hiddenState.value - itemId
    }

    override fun setDuckColour(name: String) {
        duckColourState.value = name
    }

    override fun openSystemSettings() {
        println("open system settings")
    }

    fun pressHome() {
        homeEventsFlow.tryEmit(Unit)
    }
}

object FakeShelves {
    /** 2026-10-22 21:47 CEST, the clock in the mockup; every lastUsedAt is relative to it. */
    const val NOW = 1_792_698_420_000L

    private const val HOUR = 3_600_000L
    private const val DAY = 24 * HOUR
    private const val CREAM = 0xFFF3EEE3

    // Colors are verbatim from design/Shelf.dc.html. Tin Orchestra and Sundial only reach 3.8:1 there,
    // below the 4.5:1 ink contract; kept so screens can be compared against the mockup.
    val games: List<ShelfItem> = listOf(
        game("Quiet Harbour", 0xFFD9C9A3, 0xFF3A2E1A, SpineMark.Dot, "Fishing · Finished · Last played 3 weeks ago", NOW - 21 * DAY, size = 78 by 500),
        game("Iron Saints", 0xFF8A2C2C, CREAM, SpineMark.Bar, "Action · Chapter 4 of 7 · Last played today", NOW - 3 * HOUR, size = 92 by 540, label = "IRON SAINTS"),
        game("Bloom", 0xFFF1A7B8, 0xFF4A1828, SpineMark.Leaf, "Puzzle · Finished · Last played 2 months ago", NOW - 61 * DAY, size = 70 by 470, label = "bloom"),
        game("Cartographer", 0xFF2F4B6E, CREAM, SpineMark.Square, "Strategy · Finished · Last played 2 weeks ago", NOW - 14 * DAY, size = 84 by 520),
        // The mockup only shows Fern as a cover; 84 is the spine width whose cover gets the mockup's 28px margins.
        game("Fern and the Deep Wood", 0xFFE3B23C, 0xFF1E2A20, SpineMark.Leaf, "Exploration · 9 of 12 seeds found · Last played yesterday", NOW - 26 * HOUR, size = 84 by 515),
        game("Lowlight", 0xFF2A2A2E, CREAM, SpineMark.Ring, "Stealth · Not started", null, size = 74 by 490, label = "LOWLIGHT"),
        game("Tin Orchestra", 0xFF5F7F79, CREAM, SpineMark.Triangle, "Rhythm · 31 of 40 songs cleared · Last played 2 days ago", NOW - 2 * DAY, size = 88 by 530),
        game("Pocket Kingdoms", CREAM, 0xFF13241D, SpineMark.Tile, "Strategy · Not started", null, mark = 0xFF8A2C2C, size = 80 by 480),
        game("Velvet Static", 0xFF5A3A7A, CREAM, SpineMark.Bar, "Adventure · Not started", null, mark = 0xFFF1A7B8, size = 96 by 550, label = "VELVET STATIC"),
        game("Sundial", 0xFFC8693B, 0xFFFFFFFF, SpineMark.Dot, "Puzzle · Finished · Last played 5 weeks ago", NOW - 35 * DAY, size = 70 by 460),
        game("Brick by Brick", 0xFF3F6E4E, CREAM, SpineMark.Tile, "Building · Finished · Last played 3 months ago", NOW - 92 * DAY, size = 82 by 510),
        game("Moth Lantern", 0xFF1F3A4A, 0xFFF7E7B4, SpineMark.Dot, "Adventure · Act 2 · Last played 4 days ago", NOW - 4 * DAY, size = 76 by 500),
        game("Grand Prix Deluxe", 0xFFE3E0D6, 0xFF8A2C2C, SpineMark.Bar, "Racing · Not started", null, size = 90 by 535, label = "GRAND PRIX DELUXE"),
        game("Kite Season", 0xFF7FB7C9, 0xFF13241D, SpineMark.Diamond, "Sports · Season 1 · Last played 6 days ago", NOW - 6 * DAY, size = 72 by 470),
    )

    val all: List<Shelf> = listOf(
        Shelf("all", "All games", games),
        Shelf("playing", "Playing now", pick("iron-saints", "fern-and-the-deep-wood", "tin-orchestra", "moth-lantern", "kite-season")),
        Shelf("finished", "Finished", pick("quiet-harbour", "bloom", "cartographer", "sundial", "brick-by-brick")),
        Shelf("wishlist", "Wishlist", pick("velvet-static", "grand-prix-deluxe", "lowlight")),
        Shelf("store", "Store", pick("pocket-kingdoms", "grand-prix-deluxe", "velvet-static", "lowlight", "kite-season", "bloom")),
    )

    private fun pick(vararg ids: String): List<ShelfItem> = ids.map { id -> games.single { it.id == id } }

    private fun game(
        title: String,
        spine: Long,
        ink: Long,
        markShape: SpineMark,
        subtitle: String,
        lastUsedAt: Long?,
        mark: Long = ink,
        size: Pair<Int, Int>? = null,
        label: String = title,
    ) = ShelfItem(
        id = title.lowercase().replace(' ', '-'),
        title = title,
        spineColor = Color(spine),
        inkColor = Color(ink),
        markColor = Color(mark),
        mark = markShape,
        subtitle = subtitle,
        isGame = true,
        lastUsedAt = lastUsedAt,
        spineLabel = label,
        spineWidth = size?.first,
        spineHeight = size?.second,
    )

    private infix fun Int.by(height: Int) = Pair(this, height)
}
