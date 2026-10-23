package com.mkopenga.tvhomescreen

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.drawable.toBitmap
import java.text.Collator
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AndroidLauncherRepository(context: Context) : LauncherRepository {
    private val context = context.applicationContext
    private val pm = this.context.packageManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val io = Dispatchers.IO.limitedParallelism(4)
    private val recentPrefs = this.context.getSharedPreferences("recent_launches", Context.MODE_PRIVATE)
    private val hiddenPrefs = this.context.getSharedPreferences("hidden_apps", Context.MODE_PRIVATE)
    private val settingsPrefs = this.context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val shelvesState = MutableStateFlow<List<Shelf>>(emptyList())
    private val profileNameState = MutableStateFlow(Build.MODEL)
    private val homeEventsFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val hiddenState = MutableStateFlow<Set<String>>(emptySet())

    // Read here on the main thread, unlike the other preferences: the duck is drawn on the very first frame, before
    // any app has loaded, and waiting on this one small file beats a duck that changes colour as you look at it.
    private val duckColourState = MutableStateFlow(settingsPrefs.getString(DUCK_COLOUR_KEY, null))
    private val refreshRequests = Channel<Unit>(Channel.CONFLATED)

    private val colorCache = ConcurrentHashMap<String, SpineColors>()
    private val artworkCache = object : LruCache<String, Artwork>(artworkCacheBytes()) {
        override fun sizeOf(key: String, value: Artwork): Int =
            listOfNotNull(value.icon, value.banner).sumOf { it.width * it.height * 4 }
    }
    private val spineIconCache = object : LruCache<String, ImageBitmap>(SPINE_ICON_CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    @Volatile
    private var entries: Map<String, Entry> = emptyMap()

    // Main thread only.
    private var recent: Map<String, Long> = emptyMap()
    private var recentLoaded = false
    private var hiddenLoaded = false

    override val shelves: StateFlow<List<Shelf>> = shelvesState.asStateFlow()
    override val profileName: StateFlow<String> = profileNameState.asStateFlow()
    override val homeEvents: Flow<Unit> = homeEventsFlow.asSharedFlow()
    override val hiddenIds: StateFlow<Set<String>> = hiddenState.asStateFlow()
    override val duckColour: StateFlow<String?> = duckColourState.asStateFlow()

    init {
        ContextCompat.registerReceiver(this.context, PackageChangeReceiver(), packageChanges(), ContextCompat.RECEIVER_NOT_EXPORTED)
        scope.launch {
            for (request in refreshRequests) {
                try {
                    reload()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Refreshing apps failed", e)
                }
            }
        }
        refresh()
    }

    fun refresh() {
        refreshRequests.trySend(Unit)
    }

    fun onHomePressed() {
        homeEventsFlow.tryEmit(Unit)
    }

    override suspend fun artwork(itemId: String): Artwork? {
        val artwork = artworkCache.get(itemId) ?: run {
            val entry = entries[itemId] ?: return null
            withContext(io) { loadArtwork(entry.activity, entry.colors.backdrop) }.also { artworkCache.put(itemId, it) }
        }
        return artwork.takeIf { it.icon != null || it.banner != null }
    }

    // loadIcon, not the cover's densest asset: the spine needs a few dozen pixels.
    override suspend fun spineIcon(itemId: String): ImageBitmap? {
        spineIconCache.get(itemId)?.let { return it }
        val entry = entries[itemId]?.takeIf { it.hasIcon } ?: return null
        val plate = lerp(entry.colors.spine, entry.colors.ink, 0.12f)
        return withContext(io) { entry.activity.loadIcon(pm).spineIcon(entry.colors.spine, plate).forDrawing().asImageBitmap() }
            .also { spineIconCache.put(itemId, it) }
    }

    override fun launch(itemId: String) {
        val intent = launchIntentFor(itemId) ?: return
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Cannot launch $itemId", e)
            return
        } catch (e: SecurityException) {
            Log.w(TAG, "Not allowed to launch $itemId", e)
            return
        }
        val now = System.currentTimeMillis()
        recent = recent + (itemId to now)
        recentPrefs.edit { putLong(itemId, now) }
        publish()
    }

    override fun setHidden(itemId: String, hidden: Boolean) {
        val ids = if (hidden) hiddenState.value + itemId else hiddenState.value - itemId
        if (ids == hiddenState.value) return
        hiddenState.value = ids
        hiddenPrefs.edit { putStringSet(HIDDEN_KEY, ids) }
    }

    override fun setDuckColour(name: String) {
        if (name == duckColourState.value) return
        duckColourState.value = name
        settingsPrefs.edit { putString(DUCK_COLOUR_KEY, name) }
    }

    override fun openSystemSettings() {
        try {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No system settings on this device", e)
        } catch (e: SecurityException) {
            Log.w(TAG, "Not allowed to open system settings", e)
        }
    }

    private suspend fun reload() {
        val loaded = withContext(io) { loadEntries() }
        if (!recentLoaded) {
            // Launches recorded before the first load are newer than anything on disk.
            recent = withContext(io) { readRecent() } + recent
            recentLoaded = true
        }
        // Before the first publish, so hidden apps never flash onto the shelf; nothing can call setHidden earlier,
        // because settings only lists apps that have been published.
        if (!hiddenLoaded) {
            hiddenState.value = withContext(io) { readHidden() }
            hiddenLoaded = true
        }
        profileNameState.value = withContext(io) { readProfileName() }
        entries = loaded.associateBy { it.id }
        publish()
    }

    private fun publish() {
        val now = System.currentTimeMillis()
        val all = entries.values
            .map { it.toShelfItem(recent[it.id], now) }
            .sortedWith(compareBy(Collator.getInstance()) { it.title })
        shelvesState.value = listOf(
            Shelf("all", "All", all),
            Shelf("games", "Games", all.filter { it.isGame }),
            Shelf("apps", "Apps", all.filterNot { it.isGame }),
            Shelf("recent", "Recent", all.filter { it.lastUsedAt != null }.sortedByDescending { it.lastUsedAt }),
        )
    }

    private suspend fun loadEntries(): List<Entry> = coroutineScope {
        // System settings open from Shelfi's own settings instead; on Android TV the settings app is launchable itself.
        val skipped = queryActivities(
            Intent(Settings.ACTION_SETTINGS),
            PackageManager.MATCH_DEFAULT_ONLY or PackageManager.MATCH_SYSTEM_ONLY,
        ).mapTo(hashSetOf(context.packageName)) { it.activityInfo.packageName }
        val launchable = LinkedHashMap<String, ResolveInfo>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            for (info in queryActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                val packageName = info.activityInfo.packageName
                if (packageName !in skipped) launchable.putIfAbsent(packageName, info)
            }
        }
        launchable.map { (id, info) -> async { entryFor(id, info) } }.awaitAll()
    }

    private fun entryFor(id: String, info: ResolveInfo): Entry {
        val activity = info.activityInfo
        return Entry(
            id = id,
            title = info.loadLabel(pm).toString(),
            activity = activity,
            isGame = activity.applicationInfo.isGame,
            colors = colorCache.getOrPut(id) { coverColors(activity, seed = id) },
            mark = SpineMark.entries[Math.floorMod(id.hashCode(), SpineMark.entries.size)],
            // Zero means the platform's generic icon stands in.
            hasIcon = activity.iconResource != 0,
        )
    }

    // The same art the cover will draw.
    private fun coverColors(activity: ActivityInfo, seed: String): SpineColors {
        val banner = activity.loadBanner(pm)
        return if (banner != null) spineColorsFor(banner, banner = true, seed) else spineColorsFor(highResIcon(activity), banner = false, seed)
    }

    private fun loadArtwork(activity: ActivityInfo, backdrop: Backdrop?): Artwork {
        // loadBanner falls back from the activity banner to the application banner.
        val banner = activity.loadBanner(pm)?.let { drawable ->
            val bitmap = drawable.coverBitmap(
                drawable.intrinsicWidth.takeIf { it > 0 } ?: BannerFallbackWidthPx,
                drawable.intrinsicHeight.takeIf { it > 0 } ?: BannerFallbackHeightPx,
                banner = true,
            )
            bitmap.preparedFor(backdrop)
        }
        val icon = highResIcon(activity).toBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
        return Artwork(icon = icon.forDrawing().asImageBitmap(), banner = banner?.forDrawing()?.asImageBitmap(), backdrop = backdrop)
    }

    // Covers draw icons far larger than launcher icon size, so ask for the densest asset the app ships.
    private fun highResIcon(activity: ActivityInfo): Drawable {
        val iconRes = activity.iconResource
        if (iconRes != 0) {
            try {
                pm.getResourcesForApplication(activity.applicationInfo)
                    .getDrawableForDensity(iconRes, DisplayMetrics.DENSITY_XXXHIGH, null)
                    ?.let { return it }
            } catch (_: PackageManager.NameNotFoundException) {
            } catch (_: Resources.NotFoundException) {
            }
        }
        return activity.loadIcon(pm)
    }

    private fun launchIntentFor(itemId: String): Intent? =
        pm.getLeanbackLaunchIntentForPackage(itemId) ?: pm.getLaunchIntentForPackage(itemId)

    @Suppress("DEPRECATION")
    private fun queryActivities(intent: Intent, flags: Int): List<ResolveInfo> = pm.queryIntentActivities(intent, flags)

    private fun readRecent(): Map<String, Long> =
        recentPrefs.all.mapNotNull { (id, at) -> (at as? Long)?.let { id to it } }.toMap()

    // getStringSet's result must not be modified or kept, so copy it.
    private fun readHidden(): Set<String> = hiddenPrefs.getStringSet(HIDDEN_KEY, null)?.toSet() ?: emptySet()

    private fun readProfileName(): String =
        Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() }
            ?: Build.MODEL

    private fun packageChanges() = IntentFilter().apply {
        addAction(Intent.ACTION_PACKAGE_ADDED)
        addAction(Intent.ACTION_PACKAGE_REMOVED)
        addAction(Intent.ACTION_PACKAGE_CHANGED)
        addAction(Intent.ACTION_PACKAGE_REPLACED)
        addDataScheme("package")
    }

    private inner class PackageChangeReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val packageName = intent.data?.schemeSpecificPart ?: return
            // An update sends REMOVED+ADDED with EXTRA_REPLACING, then REPLACED; skip the removal so the app doesn't blink out.
            if (intent.action == Intent.ACTION_PACKAGE_REMOVED && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
            colorCache.remove(packageName)
            artworkCache.remove(packageName)
            spineIconCache.remove(packageName)
            refresh()
        }
    }

    private class Entry(
        val id: String,
        val title: String,
        val activity: ActivityInfo,
        val isGame: Boolean,
        val colors: SpineColors,
        val mark: SpineMark,
        val hasIcon: Boolean,
    ) {
        fun toShelfItem(lastUsedAt: Long?, now: Long) = ShelfItem(
            id = id,
            title = title,
            spineColor = colors.spine,
            inkColor = colors.ink,
            markColor = colors.mark,
            mark = mark,
            subtitle = subtitleFor(isGame, lastUsedAt, now),
            isGame = isGame,
            lastUsedAt = lastUsedAt,
            spineIcon = hasIcon,
        )
    }

    private companion object {
        const val TAG = "Shelfi"
        const val HIDDEN_KEY = "ids"
        const val DUCK_COLOUR_KEY = "duck_colour"
        const val ICON_PX = 384
        const val SPINE_ICON_CACHE_BYTES = 8 * 1024 * 1024

        fun artworkCacheBytes(): Int = (Runtime.getRuntime().maxMemory() / 4).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}

@Suppress("DEPRECATION")
private val ApplicationInfo.isGame: Boolean
    get() = category == ApplicationInfo.CATEGORY_GAME || flags and ApplicationInfo.FLAG_IS_GAME != 0

// Uploads to the GPU here, off the main thread, instead of on first draw while scrolling.
private fun Bitmap.forDrawing(): Bitmap = copy(Bitmap.Config.HARDWARE, false) ?: this
