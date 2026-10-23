package com.mkopenga.tvhomescreen.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.mkopenga.tvhomescreen.Backdrop
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap

class ArtworkCoverTest {
    @Test
    fun solidBannerMeetsTheCoverWithoutASeam() {
        val app = analysedApp("Solid", banner = FixtureArt.soundCloudLike)
        assertIs<Backdrop.Solid>(app.artwork.backdrop)
        assertSeamless(app)
    }

    @Test
    fun gradientBannersMeetTheCoverWithoutASeam() {
        for (art in listOf(FixtureArt.verticalGradient, FixtureArt.diagonalSunset, FixtureArt.kijkLike)) {
            val app = analysedApp("Gradient", banner = art)
            assertIs<Backdrop.Linear>(app.artwork.backdrop)
            assertSeamless(app)
        }
    }

    @Test
    fun busyAndUnreadableBannersAreFramed() {
        for (art in listOf(FixtureArt.photo, FixtureArt.logoOffTheEdge, FixtureArt.steepGradient)) {
            assertIs<Backdrop.Busy>(analysedApp("Framed", banner = art).artwork.backdrop)
        }
    }

    /**
     * 4 in 0-255 RGB is under a just noticeable difference. Where a shape in the art crosses its edge, the cover's
     * clamped edge and the scaled image antialias the crossing differently, so those few pixels (where the art itself
     * changes by more than 12 between neighbours) are left out.
     */
    private fun assertSeamless(app: AnalysedApp) {
        val banner = app.artwork.banner!!
        val rect = ArtworkCoverLayout.artRect(banner.width.toFloat() / banner.height, banner = true)
        val cover = ArtworkCover(app.artwork, app.item.spineColor, app.item.inkColor)
        val pixels = render { cover.draw(this) }
        val left = rect.left.toInt()
        val top = rect.top.toInt()
        val right = rect.right.toInt()
        val bottom = rect.bottom.toInt()
        var worst = 0f
        var checked = 0
        fun check(x: Int, y: Int, dx: Int, dy: Int, outX: Int, outY: Int) {
            val inside = pixels.getColor(x, y)
            if (distance(pixels.getColor(x - dx, y - dy), pixels.getColor(x + dx, y + dy)) > 12f) return
            worst = maxOf(worst, distance(inside, pixels.getColor(outX, outY)))
            checked++
        }
        for (y in top + 1 until bottom - 1) {
            check(left, y, 0, 1, left - 1, y)
            check(right - 1, y, 0, 1, right, y)
        }
        for (x in left + 1 until right - 1) {
            check(x, top, 1, 0, x, top - 1)
            check(x, bottom - 1, 1, 0, x, bottom)
        }
        assertTrue(checked > (right - left + bottom - top) * 2 * 9 / 10, "only $checked edge pixels are smooth enough to check")
        assertTrue(worst <= 4f, "${app.artwork.backdrop}: the cover steps by $worst where it meets the art")
    }

    private fun render(draw: DrawScope.() -> Unit): Bitmap =
        SceneDriver(width = CoverWidth.toInt(), height = CoverHeight.toInt(), content = {
            Spacer(Modifier.fillMaxSize().drawBehind(draw))
        }).use { Bitmap.makeFromImage(it.frame()) }

    private fun distance(a: Int, b: Int): Float {
        val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
        val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return sqrt((dr * dr + dg * dg + db * db).toFloat())
    }
}
