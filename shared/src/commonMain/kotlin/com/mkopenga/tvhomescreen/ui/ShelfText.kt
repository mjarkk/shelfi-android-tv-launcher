package com.mkopenga.tvhomescreen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

/** The focused book's (or the bookend's) title and subtitle under the plank. */
@Composable
internal fun InfoLine(state: HomeState, modifier: Modifier = Modifier) {
    val label = state.focusedLabel ?: return
    val typography = LocalTypography.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(label.title, style = typography.infoTitle, maxLines = 1)
        BasicText(label.subtitle, style = typography.infoSubtitle, maxLines = 1)
    }
}

/**
 * Only reachable before the first app list arrives, on a device with nothing launchable, or with every app hidden;
 * calm, not blank.
 */
@Composable
internal fun EmptyShelf(modifier: Modifier = Modifier, everythingHidden: Boolean = false) {
    val typography = LocalTypography.current
    val plaque = RoundedCornerShape(20.dp)
    Column(
        modifier.background(Palette.Plaque, plaque).padding(horizontal = 64.dp, vertical = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(
            Modifier.size(132.dp, 104.dp).drawWithCache {
                val u = density
                val line = Stroke(3f * u)
                val ink = Palette.Muted.copy(alpha = 0.7f)
                onDrawBehind {
                    drawRect(ink, Offset(8f * u, 22f * u), Size(26f * u, 80f * u), style = line)
                    drawRect(ink, Offset(40f * u, 6f * u), Size(30f * u, 96f * u), style = line)
                    drawLine(ink, Offset(48f * u, 22f * u), Offset(62f * u, 22f * u), 3f * u, StrokeCap.Round)
                    rotate(18f, pivot = Offset(80f * u, 102f * u)) {
                        drawRect(ink, Offset(80f * u, 18f * u), Size(28f * u, 84f * u), style = line)
                    }
                }
            },
        )
        Spacer(Modifier.height(28.dp))
        BasicText(if (everythingHidden) "Every app is hidden" else "Nothing on the shelf yet", style = typography.emptyTitle)
        Spacer(Modifier.height(10.dp))
        BasicText(
            if (everythingHidden) "Bring them back from Shelf settings, on the duck." else "Installed games and apps will appear here.",
            style = typography.emptyBody,
        )
    }
}
