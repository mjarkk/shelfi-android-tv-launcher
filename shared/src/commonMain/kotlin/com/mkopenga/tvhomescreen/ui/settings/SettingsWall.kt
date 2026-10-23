package com.mkopenga.tvhomescreen.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.mkopenga.tvhomescreen.LauncherRepository
import com.mkopenga.tvhomescreen.ShelfItem
import com.mkopenga.tvhomescreen.ui.CoverShadow
import com.mkopenga.tvhomescreen.ui.HomeState
import com.mkopenga.tvhomescreen.ui.LocalTypography
import com.mkopenga.tvhomescreen.ui.Palette
import com.mkopenga.tvhomescreen.ui.Screen
import com.mkopenga.tvhomescreen.ui.wallpaper.Wallpaper
import com.mkopenga.tvhomescreen.ui.wallpaper.WallpaperThumbnail
import kotlin.math.roundToInt

/**
 * Settings as small framed pictures on the wall above the shelf, and the pages they open hung higher still. Nothing
 * here is opaque edge to edge: the wall is drawn behind everything and moves with it, as one room seen from a
 * camera tilting up and down. Composed only while it is in view.
 */
@Composable
internal fun SettingsWall(state: HomeState, motion: SettingsMotion, repository: LauncherRepository, ducks: Ducks) {
    if (state.screen != Screen.Shelf || motion.reach >= 1) Wall(state, motion, repository, ducks)
}

@Composable
private fun Wall(state: HomeState, motion: SettingsMotion, repository: LauncherRepository, ducks: Ducks) {
    val typography = LocalTypography.current
    val type = remember(typography) { WallType(typography.infoTitle.fontFamily) }
    val shadow = remember { CoverShadow.create() }
    Box(Modifier.fillMaxSize().graphicsLayer { translationY = (motion.shelfDrop - PanDistance) * density }) {
        Frames(state, motion, type, shadow, ducks)
    }
    if (state.screen.isPage || motion.reach >= 2) {
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = (motion.shelfDrop - 2 * PanDistance) * density }) {
            when (state.lastPage) {
                Screen.HiddenApps -> HiddenAppsPage(state, motion, type, repository)
                else -> WallpaperPicker(state, motion, type, shadow)
            }
        }
    }
}

private class Hung(val frame: SettingsFrame, val style: FrameStyle, val centreX: Float, val tilt: Float)

// The room pans a whole screen, so the wall here is all wallpaper; the frames and labels are centred on it.
private const val NailY = 200f
private const val FramesMiddle = 484f
private const val LabelsTop = 722f
private const val MinLabelWidth = 340f

private val WallFrames = listOf(
    Hung(SettingsFrame.Wallpaper, FrameStyle(440f, 290f, Finishes.Gilt), centreX = 340f, tilt = -1.6f),
    Hung(SettingsFrame.HiddenApps, FrameStyle(300f, 380f, Finishes.Walnut), centreX = 803f, tilt = 1.3f),
    Hung(SettingsFrame.Duck, FrameStyle(340f, 290f, Finishes.Sage), centreX = 1216f, tilt = -1.1f),
    Hung(SettingsFrame.System, FrameStyle(330f, 330f, Finishes.Oak, FrameShape.Round), centreX = 1629f, tilt = 2f),
)

@Composable
private fun Frames(state: HomeState, motion: SettingsMotion, type: WallType, shadow: CoverShadow, ducks: Ducks) {
    for (hung in WallFrames) {
        val style = hung.style
        val i = hung.frame.ordinal
        val top = FramesMiddle - style.height / 2
        val focus = { motion.frame.focusOf(i) }
        HangingFrame(style, top - NailY, hung.tilt, focus, shadow, Modifier.offset((hung.centreX - style.width / 2).dp, top.dp)) {
            when (hung.frame) {
                SettingsFrame.Wallpaper -> SampleFan(state.wallpaperSamples, shadow, Modifier.fillMaxSize())
                SettingsFrame.HiddenApps -> HiddenAppsPicture(state.items, Modifier.fillMaxSize())
                SettingsFrame.Duck -> DuckPicture(ducks, { state.duckColours }, Modifier.fillMaxSize())
                SettingsFrame.System -> SystemPicture(focus, Modifier.fillMaxSize())
            }
        }
        val width = maxOf(style.width, MinLabelWidth)
        val label = labelOf(hung.frame, state)
        MuseumLabel(label.first, label.second, type, Modifier.offset((hung.centreX - width / 2).dp, LabelsTop.dp).width(width.dp))
    }
}

private fun labelOf(frame: SettingsFrame, state: HomeState): Pair<String, List<String>> = when (frame) {
    SettingsFrame.Wallpaper -> "Wallpaper" to listOf(state.currentWallpaper?.name ?: "Plain", "Changes every hour")
    SettingsFrame.HiddenApps -> {
        val hidden = state.allItems.size - state.items.size
        "Hidden apps" to listOf(if (hidden == 0) "None hidden" else "$hidden hidden", "${state.items.size} on the shelf")
    }
    SettingsFrame.Duck -> "Duck" to listOf(state.duckColours.label)
    SettingsFrame.System -> "System settings" to listOf("Network, display, sound", "and everything else")
}

private const val SwatchWidth = 118f
// Inside its edges a swatch is 162 high, as the picker's miniatures are, so they share cached thumbnail tiles.
private const val SwatchHeight = 172f
private const val SwatchEdge = 5f
private const val SwatchSink = 4f
private const val RivetInset = 16f
private const val RivetRadius = 7f
private val SwatchTilts = floatArrayOf(0f, -26f, 26f)

/** [samples] fanned out like a sample book's swatches on its cloth cover, riveted at the foot, the first in front. */
@Composable
private fun SampleFan(samples: List<Wallpaper>, shadow: CoverShadow, modifier: Modifier) {
    Box(modifier.drawBehind { drawRect(WallColors.Claret) }, contentAlignment = Alignment.BottomCenter) {
        for (k in samples.indices.reversed()) {
            key(samples[k].id) {
                Box(
                    Modifier
                        .offset(y = SwatchSink.dp)
                        .size(SwatchWidth.dp, SwatchHeight.dp)
                        .graphicsLayer {
                            rotationZ = SwatchTilts[k]
                            transformOrigin = TransformOrigin(0.5f, 1f - RivetInset / SwatchHeight)
                        }
                        .drawBehind {
                            with(shadow) { drawShadow(0, 0, size.width.roundToInt(), size.height.roundToInt(), 0.9f) }
                            drawRect(WallColors.Paper)
                        }
                        .padding(SwatchEdge.dp),
                ) {
                    WallpaperThumbnail(samples[k], Modifier.fillMaxSize())
                }
            }
        }
        Spacer(
            Modifier
                .offset(y = (SwatchSink - RivetInset + RivetRadius).dp)
                .size((2 * RivetRadius).dp)
                .drawBehind {
                    val u = density
                    drawCircle(Color.Black.copy(alpha = 0.3f), RivetRadius * u, center + Offset(0f, 1.5f * u))
                    drawCircle(WallColors.BrassShade, RivetRadius * u)
                    drawCircle(WallColors.Brass, (RivetRadius - 1.5f) * u)
                    drawCircle(WallColors.MatBevel.copy(alpha = 0.7f), 2f * u, center - Offset(2f * u, 2f * u))
                },
        )
    }
}

private val PictureWall = Color(0xFFEDE3CF)
private val GhostSpine = Color(0xFF8F8573)
private val SystemBackdrop = Color(0xFF2C4F49)
private val FallbackSpines = listOf(0xFF8A2C2C, 0xFF2F4B6E, 0xFFE3B23C, 0xFF3F6E4E, 0xFF5A3A7A, 0xFFC8693B).map(::Color)
private val MiniSpineWidths = floatArrayOf(20f, 24f, 18f, 24f, 20f, 22f, 18f)
private val MiniSpineHeights = floatArrayOf(128f, 150f, 120f, 138f, 146f, 124f, 140f)
private const val GhostSpineIndex = 3

/** A little shelf in the shelf's own colours with one book missing, its gap struck from view. */
@Composable
private fun HiddenAppsPicture(items: List<ShelfItem>, modifier: Modifier) {
    Spacer(
        modifier.drawWithCache {
            val u = density
            val colours = (items.map { it.spineColor } + FallbackSpines).take(MiniSpineWidths.size - 1)
            val eye = EyeIcon(64f * u, 5f * u)
            val dashed = Stroke(2.5f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f * u, 6f * u)))
            val plankTop = size.height - 44f * u
            onDrawBehind {
                drawRect(PictureWall)
                var x = 15f * u
                for (k in MiniSpineWidths.indices) {
                    val w = MiniSpineWidths[k] * u
                    val h = MiniSpineHeights[k] * u
                    if (k == GhostSpineIndex) {
                        drawRect(GhostSpine, Offset(x, plankTop - h), Size(w, h), style = dashed)
                    } else {
                        drawRect(colours[if (k < GhostSpineIndex) k else k - 1], Offset(x, plankTop - h), Size(w, h))
                    }
                    x += w + 4f * u
                }
                drawRect(Palette.Plank, Offset(0f, plankTop), Size(size.width, 12f * u))
                with(eye) { drawEye(Offset(size.width / 2, 50f * u), WallColors.Ink, crossed = true, behind = PictureWall) }
            }
        },
    )
}

// The duck at half the shelf's size, hugging a book of its own.
private const val PictureDuck = 0.5f
private const val PictureSpineWidth = 40f
private const val PictureSpineHeight = 160f
private const val PicturePlank = 30f
private val PictureSpine = Color(0xFF2F4B6E)

/** The ducks as they look now, one of them hugging a book on a little shelf. */
@Composable
private fun DuckPicture(ducks: Ducks, colours: () -> DuckColours, modifier: Modifier) {
    Spacer(
        modifier.drawWithCache {
            val u = density
            // Read here, in the cache, so a new colour bakes this duck once without recomposing the frame.
            val duck = ducks.picture(u * PictureDuck, colours())
            val plankTop = size.height - PicturePlank * u
            val spineWidth = PictureSpineWidth * u
            val duckLeft = ((size.width - spineWidth - BookendWidth * PictureDuck * u) / 2 + spineWidth).roundToInt().toFloat()
            val duckTop = (plankTop - BookendHeight * PictureDuck * u).roundToInt().toFloat()
            val spineLeft = duckLeft - spineWidth
            onDrawBehind {
                drawRect(PictureWall)
                with(duck) { draw(duckLeft, duckTop, focus = 0f) }
                drawRect(PictureSpine, Offset(spineLeft, plankTop - PictureSpineHeight * u), Size(spineWidth, PictureSpineHeight * u))
                with(duck) { drawWingTip(duckLeft, duckTop, spineLeft, duckLeft) }
                drawRect(Palette.Plank, Offset(0f, plankTop), Size(size.width, 12f * u))
            }
        },
    )
}

/** Two meshed gears that turn a little as the frame takes focus, on the frame's own focus spring. */
@Composable
private fun SystemPicture(focus: () -> Float, modifier: Modifier) {
    Spacer(
        modifier.drawWithCache {
            val u = density
            val big = gearPath(teeth = 10, outer = 64f * u, inner = 50f * u, hole = 19f * u)
            val small = gearPath(teeth = 8, outer = 38f * u, inner = 29f * u, hole = 11f * u)
            val edge = Stroke(2f * u)
            val bigAt = Offset(98f * u, 136f * u)
            val smallAt = Offset(166f * u, 74f * u)
            onDrawBehind {
                drawRect(SystemBackdrop)
                val e = focus()
                rotate(18f * e, bigAt) {
                    translate(bigAt.x, bigAt.y) { drawPath(big, WallColors.Paper) }
                }
                rotate(11f - 22.5f * e, smallAt) {
                    translate(smallAt.x, smallAt.y) {
                        drawPath(small, WallColors.Brass)
                        drawPath(small, WallColors.BrassShade, style = edge)
                    }
                }
            }
        },
    )
}
