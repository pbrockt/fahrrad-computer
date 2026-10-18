package com.pbrockt.fahrradcomputer

import com.pbrockt.fahrradcomputer.recording.TrackPoint
import com.pbrockt.fahrradcomputer.share.MapWindow
import com.pbrockt.fahrradcomputer.share.OsmMath
import com.pbrockt.fahrradcomputer.share.ProjectedRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.floor

class OsmMathTest {

    @Test
    fun knownTiles() {
        // Zoom 0: die ganze Welt ist eine 256-Pixel-Kachel, (0,0) liegt in der Mitte.
        assertEquals(128.0, OsmMath.worldX(0.0, 0), 1e-9)
        assertEquals(128.0, OsmMath.worldY(0.0, 0), 1e-9)
        // Berlin (Brandenburger Tor) auf Zoom 10: Kachel 550 / 335.
        assertEquals(550, floor(OsmMath.worldX(13.3777, 10) / 256).toInt())
        assertEquals(335, floor(OsmMath.worldY(52.5163, 10) / 256).toInt())
    }

    // ~5 km Tour bei Koblenz
    private val ride = (0..300).map { i ->
        TrackPoint(i * 1000L, 50.35 + i * 0.0001, 7.58 + (i % 100) * 0.0002, null, null)
    }

    @Test
    fun fitPicksLargestZoomThatFits() {
        val w = 1080.0; val h = 800.0; val pad = 70.0; val scale = 2.0
        val win = OsmMath.fit(ride, w, h, scale, pad)
        fun span(z: Int): Pair<Double, Double> {
            val xs = ride.map { OsmMath.worldX(it.lon, z) }
            val ys = ride.map { OsmMath.worldY(it.lat, z) }
            return (xs.max() - xs.min()) * scale to (ys.max() - ys.min()) * scale
        }
        val (sw, sh) = span(win.zoom)
        assertTrue(sw <= w - 2 * pad && sh <= h - 2 * pad)
        val (bw, bh) = span(win.zoom + 1)
        assertTrue("eine Stufe naeher passt nicht mehr", bw > w - 2 * pad || bh > h - 2 * pad)
        assertEquals(w / scale, win.width, 1e-9)
    }

    @Test
    fun routeIsCenteredAndInsideMap() {
        val win = OsmMath.fit(ride, 1080.0, 800.0, 2.0, 70.0)
        val p = ProjectedRoute.onMap(ride, win, 2f, 0f, 200f)
        assertTrue(p.xs.min() >= 70f - 1 && p.xs.max() <= 1080f - 70f + 1)
        assertTrue(p.ys.min() >= 200f + 70f - 1 && p.ys.max() <= 200f + 800f - 70f + 1)
        assertEquals(540f, (p.xs.min() + p.xs.max()) / 2, 1f)
    }

    @Test
    fun tilesCoverWindow() {
        val win = MapWindow(zoom = 15, originX = 1000.5, originY = 2000.5, width = 540.0, height = 400.0)
        val t = win.tiles()
        // x: 1000.5..1540.5 -> Kacheln 3..6, y: 2000.5..2400.5 -> 7..9
        assertEquals((7..9).flatMap { y -> (3..6).map { x -> x to y } }, t)
    }
}
