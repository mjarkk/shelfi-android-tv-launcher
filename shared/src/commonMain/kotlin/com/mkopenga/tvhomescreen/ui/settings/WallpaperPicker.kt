package com.mkopenga.tvhomescreen.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mkopenga.tvhomescreen.ui.CoverShadow
import com.mkopenga.tvhomescreen.ui.DesignWidth
import com.mkopenga.tvhomescreen.ui.HomeState
import com.mkopenga.tvhomescreen.ui.wallpaper.WallpaperThumbnail
import kotlin.math.roundToInt

private val MiniFrame = FrameStyle(360f, 234f, Finishes.Gilt, moulding = 16f, mat = 20f)
private val OnTheWallFrame = FrameStyle(360f, 234f, Finishes.Gilt, moulding = 16f, mat = 20f, matColor = WallColors.Claret)
private const val RailTop = 236f
private const val RailHeight = 20f
private const val MiniTop = 406f
private const val MiniPitch = 424f
private const val ViewLeft = 120f
private const val ViewWidth = 1680f
private const val LabelTop = 722f
private const val LabelWidth = 480f

/** Left edge of the row of miniatures: centred when it fits, else keeping the focused one in the middle. */
private fun rowLeft(count: Int, p: Float): Float {
    val total = count * MiniPitch - (MiniPitch - MiniFrame.width)
    if (total <= ViewWidth) return (DesignWidth - total) / 2
    return (DesignWidth / 2f - MiniFrame.width / 2 - p * MiniPitch).coerceIn(ViewLeft + ViewWidth - total, ViewLeft)
}

/**
 * Every wallpaper as a miniature hanging from a picture rail. The one on the wall now wears a check mark and a claret
 * mat: on a cream mat it read as a hole cut in the same wallpaper around it.
 */
@Composable
internal fun WallpaperPicker(state: HomeState, motion: SettingsMotion, type: WallType, shadow: CoverShadow) {
    val wallpapers = state.wallpapers
    val currentId = state.currentWallpaper?.id
    PictureRail(Modifier.offset(0.dp, RailTop.dp).size(DesignWidth.dp, RailHeight.dp))
    for ((i, wallpaper) in wallpapers.withIndex()) {
        key(wallpaper.id) {
            HangingFrame(
                if (wallpaper.id == currentId) OnTheWallFrame else MiniFrame,
                nailDrop = MiniTop - (RailTop + RailHeight / 2),
                tilt = if (i % 2 == 0) -1.5f else 1.2f,
                focus = { motion.wallpaper.focusOf(i) },
                shadow = shadow,
                modifier = Modifier.offset {
                    val x = rowLeft(wallpapers.size, motion.wallpaper.position) + i * MiniPitch
                    IntOffset((x * density).roundToInt(), (MiniTop * density).roundToInt())
                },
            ) {
                WallpaperThumbnail(wallpaper, Modifier.fillMaxSize())
                if (wallpaper.id == currentId) OnTheWallBadge(Modifier.align(Alignment.TopEnd).padding(10.dp))
            }
        }
    }
    val picked = wallpapers.getOrNull(state.pickerIndex) ?: return
    MuseumLabel(
        picked.name,
        listOfNotNull("On the wall now".takeIf { picked.id == currentId }, "Wallpapers change every hour"),
        type,
        Modifier
            .offset {
                val p = motion.wallpaper.position
                val under = rowLeft(wallpapers.size, p) + p * MiniPitch + (MiniFrame.width - LabelWidth) / 2
                val x = under.coerceIn(ViewLeft, ViewLeft + ViewWidth - LabelWidth)
                IntOffset((x * density).roundToInt(), (LabelTop * density).roundToInt())
            }
            .width(LabelWidth.dp),
    )
}

@Composable
private fun PictureRail(modifier: Modifier) {
    Spacer(
        modifier.drawBehind {
            val u = density
            val walnut = Finishes.Walnut
            drawRect(Color.Black.copy(alpha = 0.22f), Offset(0f, size.height), Size(size.width, 7f * u))
            drawRect(walnut.base)
            drawRect(walnut.light, size = Size(size.width, 3f * u))
            drawRect(walnut.dark, Offset(0f, size.height - 4f * u), Size(size.width, 4f * u))
        },
    )
}

@Composable
private fun OnTheWallBadge(modifier: Modifier) {
    Spacer(
        modifier.size(40.dp).drawWithCache {
            val u = density
            val check = Path().apply {
                moveTo(12f * u, 21f * u)
                lineTo(18f * u, 27f * u)
                lineTo(29f * u, 14f * u)
            }
            val stroke = Stroke(4f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
            onDrawBehind {
                drawCircle(Color.Black.copy(alpha = 0.3f), 19f * u, center + Offset(0f, 2f * u))
                drawCircle(WallColors.Paper, 19f * u)
                drawPath(check, WallColors.Ink, style = stroke)
            }
        },
    )
}
