package com.mkopenga.tvhomescreen.ui.settings

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mkopenga.tvhomescreen.LauncherRepository
import com.mkopenga.tvhomescreen.ShelfItem
import com.mkopenga.tvhomescreen.ui.HomeState
import com.mkopenga.tvhomescreen.ui.Palette
import com.mkopenga.tvhomescreen.ui.drawMark
import kotlin.math.roundToInt

private const val CardWidth = 400f
private const val CardHeight = 96f
private const val ColumnPitch = 426f
private const val RowPitch = 116f
private const val GridLeft = 120f
private const val GridTop = 254f
private const val HeaderTop = 96f

// Room around the grid's clip for a focused card's growth and ring, but less than the gap to the next row, so a
// row scrolled out of view never peeks in.
private const val GridMarginX = 24f
private const val GridMarginY = 18f

private const val SpineStrip = 10f
private const val IconLeft = 26f
private const val IconSize = 64f
private const val TextLeft = IconLeft + IconSize + 18f
private const val CardFocusGrowth = 0.03f
private const val RingWidth = 5f
private const val RingInset = 8f + RingWidth / 2

/** The apps' own icons, loaded when the page opens, A–Z as the grid shows them, and let go when it closes. */
@Stable
private class AppIcons(private val repository: LauncherRepository) {
    private val loaded = mutableStateMapOf<String, ImageBitmap>()

    /** Snapshot state: a draw that reads this redraws when the icon arrives. */
    operator fun get(id: String): ImageBitmap? = loaded[id]

    suspend fun load(apps: List<ShelfItem>) {
        for (app in apps) {
            if (app.id in loaded) continue
            repository.artwork(app.id)?.icon?.let { loaded[app.id] = it }
        }
    }
}

/** Every app as a catalogue card, A–Z; OK takes a card's app off the shelf or puts it back. */
@Composable
internal fun HiddenAppsPage(state: HomeState, motion: SettingsMotion, type: WallType, repository: LauncherRepository) {
    val apps = state.allItems
    val icons = remember(repository) { AppIcons(repository) }
    LaunchedEffect(icons, apps) { icons.load(apps) }
    val hidden = apps.size - state.items.size
    MuseumLabel(
        "Hide apps",
        listOf(
            "Hidden apps stay installed; they just leave the shelf.",
            if (hidden == 0) "Nothing hidden · ${state.items.size} on the shelf" else "$hidden hidden · ${state.items.size} on the shelf",
        ),
        type,
        Modifier.offset(GridLeft.dp, HeaderTop.dp).width(720.dp),
    )
    Box(
        Modifier
            .offset((GridLeft - GridMarginX).dp, (GridTop - GridMarginY).dp)
            .size(
                (HiddenAppsColumns * ColumnPitch - (ColumnPitch - CardWidth) + 2 * GridMarginX).dp,
                ((HiddenAppsVisibleRows - 1) * RowPitch + CardHeight + 2 * GridMarginY).dp,
            )
            .clipToBounds(),
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = -motion.appRow.position * RowPitch * density }) {
            for ((i, app) in apps.withIndex()) {
                key(app.id) {
                    AppCard(
                        app,
                        icons,
                        hidden = app.id in state.hiddenIds,
                        focused = i == state.appIndex,
                        type = type,
                        modifier = Modifier.offset(
                            (GridMarginX + i % HiddenAppsColumns * ColumnPitch).dp,
                            (GridMarginY + i / HiddenAppsColumns * RowPitch).dp,
                        ),
                    )
                }
            }
        }
    }
}

private val Greyed = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/**
 * The app's icon with its spine colour down the card's edge. A hidden app's card is greyed, its icon faded to grey
 * and its eye struck through; its text stays readable. Without an icon, a little spine stands in for it.
 */
@Composable
private fun AppCard(app: ShelfItem, icons: AppIcons, hidden: Boolean, focused: Boolean, type: WallType, modifier: Modifier) {
    val focus by animateFloatAsState(if (focused) 1f else 0f, spring(stiffness = Spring.StiffnessMedium))
    Box(
        modifier
            .size(CardWidth.dp, CardHeight.dp)
            .graphicsLayer {
                val s = 1f + CardFocusGrowth * focus
                scaleX = s
                scaleY = s
            }
            .drawWithCache {
                val u = density
                val eye = EyeIcon(34f * u, 3f * u)
                val corner = CornerRadius(8f * u)
                val paper = if (hidden) WallColors.PaperOff else WallColors.Paper
                val spineAlpha = if (hidden) 0.35f else 1f
                val inset = RingInset * u
                val ringEdge = Stroke((RingWidth + 4f) * u)
                val ring = Stroke(RingWidth * u)
                val ringCorner = CornerRadius(8f * u + inset)
                val hairline = Stroke(1.5f * u)
                val cardShape = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, corner)) }
                val iconTop = (size.height - IconSize * u) / 2
                val iconShape = Path().apply {
                    addRoundRect(RoundRect(IconLeft * u, iconTop, (IconLeft + IconSize) * u, iconTop + IconSize * u, CornerRadius(14f * u)))
                }
                val iconAt = IntOffset((IconLeft * u).roundToInt(), iconTop.roundToInt())
                val iconSize = IntSize((IconSize * u).roundToInt(), (IconSize * u).roundToInt())
                onDrawWithContent {
                    drawRoundRect(Color.Black.copy(alpha = 0.22f), Offset(0f, 5f * u), size, corner)
                    drawRoundRect(paper, cornerRadius = corner)
                    clipPath(cardShape) { drawRect(app.spineColor, size = Size(SpineStrip * u, size.height), alpha = spineAlpha) }
                    val icon = icons[app.id]
                    if (icon != null) {
                        clipPath(iconShape) {
                            drawImage(
                                icon,
                                srcOffset = IntOffset.Zero,
                                srcSize = IntSize(icon.width, icon.height),
                                dstOffset = iconAt,
                                dstSize = iconSize,
                                alpha = if (hidden) 0.45f else 1f,
                                colorFilter = if (hidden) Greyed else null,
                                filterQuality = FilterQuality.Medium,
                            )
                        }
                    } else {
                        val spineLeft = (IconLeft + IconSize / 2 - 18f) * u
                        drawRect(app.spineColor, Offset(spineLeft, iconTop), Size(36f * u, IconSize * u), spineAlpha)
                        drawRect(Color.Black, Offset(spineLeft, iconTop), Size(36f * u, IconSize * u), 0.16f * spineAlpha, hairline)
                        scale(u, u, pivot = Offset.Zero) { drawMark(app.mark, app.markColor, IconLeft + IconSize / 2, iconTop / u + 8f, spineAlpha) }
                    }
                    with(eye) {
                        drawEye(
                            Offset(size.width - 40f * u, size.height / 2),
                            if (hidden) WallColors.InkOff else WallColors.InkSoft,
                            crossed = hidden,
                            behind = paper,
                        )
                    }
                    drawContent()
                    val alpha = focus
                    if (alpha > 0f) {
                        val topLeft = Offset(-inset, -inset)
                        val box = Size(size.width + 2 * inset, size.height + 2 * inset)
                        drawRoundRect(Palette.FocusEdge, topLeft, box, ringCorner, ringEdge, alpha)
                        drawRoundRect(Palette.Cream, topLeft, box, ringCorner, ring, alpha)
                    }
                }
            },
    ) {
        Column(Modifier.padding(start = TextLeft.dp, top = 16.dp, end = 70.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText(app.title, style = if (hidden) type.cardTitleOff else type.cardTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(if (hidden) "Hidden" else "On the shelf", style = type.cardBody, maxLines = 1)
        }
    }
}
