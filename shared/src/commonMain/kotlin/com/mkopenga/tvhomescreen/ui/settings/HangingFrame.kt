package com.mkopenga.tvhomescreen.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mkopenga.tvhomescreen.ui.CoverShadow
import com.mkopenga.tvhomescreen.ui.FocusMotion
import com.mkopenga.tvhomescreen.ui.Palette
import com.mkopenga.tvhomescreen.ui.smoothstep
import kotlin.math.abs
import kotlin.math.roundToInt

internal enum class FrameShape { Rect, Round }

/** A picture frame's outer size, moulding and mat, in design px; the picture fills what the mat leaves open. */
@Immutable
internal class FrameStyle(
    val width: Float,
    val height: Float,
    val finish: FrameFinish,
    val shape: FrameShape = FrameShape.Rect,
    val moulding: Float = 22f,
    val mat: Float = 28f,
    val matColor: Color = WallColors.Paper,
)

/** How much a focused frame grows. It also straightens: frames hang a little crooked until you look at them. */
private const val FocusGrowth = 0.06f

// The books' focus outline: 5 px wide, 8 px outside the edge.
private const val RingWidth = 5f
private const val RingInset = 8f + RingWidth / 2

/** Focus of item [i] (0..1) while this spring sits at or between items, as the shelf's books get theirs. */
internal fun FocusMotion.focusOf(i: Int): Float = (1f - abs(position - i)).coerceIn(0f, 1f)

/**
 * A framed picture hanging on a cord from a nail [nailDrop] design px above its top edge. It swings on the nail:
 * crooked by [tilt] degrees at rest, straight and a little bigger when focused. [focus] is read only in the layer
 * and draw phases, so a moving focus spring never recomposes it.
 */
@Composable
internal fun HangingFrame(
    style: FrameStyle,
    nailDrop: Float,
    tilt: Float,
    focus: () -> Float,
    shadow: CoverShadow,
    modifier: Modifier = Modifier,
    picture: @Composable BoxScope.() -> Unit,
) {
    val opening = style.moulding + style.mat
    val round = style.shape == FrameShape.Round
    Box(
        modifier
            .size(style.width.dp, style.height.dp)
            .graphicsLayer {
                val e = focus()
                transformOrigin = TransformOrigin(0.5f, -nailDrop / style.height)
                rotationZ = tilt * (1f - e)
                val s = 1f + FocusGrowth * e
                scaleX = s
                scaleY = s
            }
            .drawWithCache {
                val u = density
                val w = size.width
                val h = size.height
                val m = style.moulding * u
                val o = opening * u
                val finish = style.finish
                val nail = Offset(w / 2, -nailDrop * u)
                val leftCord = if (round) Offset(w * 0.3f, h * 0.14f) else Offset(w * 0.24f, m / 2)
                val rightCord = Offset(w - leftCord.x, leftCord.y)
                val ridge = Stroke(2.5f * u)
                val rabbet = Stroke(4f * u)
                val ringEdge = Stroke((RingWidth + 4f) * u)
                val ring = Stroke(RingWidth * u)
                val inset = RingInset * u
                val shadowCentre = Offset(w / 2, h / 2 + 18f * u)
                val roundShadow = Brush.radialGradient(
                    listOf(Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.25f), Color.Transparent),
                    center = shadowCentre,
                    radius = w * 0.62f,
                )
                onDrawWithContent {
                    val e = focus()
                    if (round) {
                        drawCircle(roundShadow, w * 0.62f, shadowCentre, alpha = 0.7f + 0.3f * e)
                    } else {
                        with(shadow) { drawShadow(0, 0, w.roundToInt(), h.roundToInt(), 0.7f + 0.3f * e) }
                    }
                    drawCord(nail, leftCord, u)
                    drawCord(nail, rightCord, u)
                    drawNail(nail, u)
                    if (round) {
                        val r = w / 2
                        drawCircle(finish.base, r, center)
                        drawCircle(finish.light, r - m * 0.35f, center, style = ridge)
                        drawCircle(style.matColor, r - m, center)
                        drawCircle(finish.dark, r - m + 2f * u, center, style = rabbet)
                        drawCircle(WallColors.MatBevel, r - o + 4f * u, center)
                    } else {
                        drawRoundRect(finish.base, cornerRadius = CornerRadius(3f * u))
                        drawRect(finish.light, Offset(m * 0.35f, m * 0.35f), Size(w - m * 0.7f, h - m * 0.7f), style = ridge)
                        drawRect(style.matColor, Offset(m, m), Size(w - 2 * m, h - 2 * m))
                        drawRect(finish.dark, Offset(m - 2f * u, m - 2f * u), Size(w - 2 * m + 4f * u, h - 2 * m + 4f * u), style = rabbet)
                        drawRect(WallColors.MatBevel, Offset(o - 4f * u, o - 4f * u), Size(w - 2 * o + 8f * u, h - 2 * o + 8f * u))
                    }
                    drawContent()
                    val alpha = smoothstep(0.55f, 1f, e)
                    if (alpha > 0f) {
                        if (round) {
                            drawCircle(Palette.FocusEdge, w / 2 + inset, center, alpha, style = ringEdge)
                            drawCircle(Palette.Cream, w / 2 + inset, center, alpha, style = ring)
                        } else {
                            val topLeft = Offset(-inset, -inset)
                            val box = Size(w + 2 * inset, h + 2 * inset)
                            val corner = CornerRadius(3f * u + inset)
                            drawRoundRect(Palette.FocusEdge, topLeft, box, corner, ringEdge, alpha)
                            drawRoundRect(Palette.Cream, topLeft, box, corner, ring, alpha)
                        }
                    }
                }
            }
            .padding(opening.dp)
            .clip(if (round) CircleShape else RectangleShape),
        content = picture,
    )
}

/** A gallery label: dark text on its own paper card, readable on any wallpaper. */
@Composable
internal fun MuseumLabel(title: String, lines: List<String>, type: WallType, modifier: Modifier = Modifier) {
    Column(
        modifier
            .drawBehind {
                val u = density
                val corner = CornerRadius(5f * u)
                drawRoundRect(Color.Black.copy(alpha = 0.22f), Offset(0f, 5f * u), size, corner)
                drawRoundRect(WallColors.Paper, cornerRadius = corner)
                drawRoundRect(WallColors.PaperEdge, cornerRadius = corner, style = Stroke(1.5f * u))
            }
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BasicText(title, style = type.labelTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        for (line in lines) BasicText(line, style = type.labelBody, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
