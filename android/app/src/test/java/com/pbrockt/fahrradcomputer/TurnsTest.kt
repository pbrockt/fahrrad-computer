package com.pbrockt.fahrradcomputer

import com.pbrockt.fahrradcomputer.routes.Guidance
import com.pbrockt.fahrradcomputer.routes.Navigator
import com.pbrockt.fahrradcomputer.routes.Route
import com.pbrockt.fahrradcomputer.routes.RoutePoint
import com.pbrockt.fahrradcomputer.routes.TurnAlert
import com.pbrockt.fahrradcomputer.routes.TurnAnnouncer
import com.pbrockt.fahrradcomputer.routes.TurnDetector
import com.pbrockt.fahrradcomputer.routes.TurnKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

class TurnsTest {

    private val lat0 = 50.0
    private val mLon = 111_320.0 * cos(Math.toRadians(lat0))

    /** Baut eine Route aus Abschnitten (Richtung in Grad, Laenge in m), Punkte alle 10 m. */
    private fun route(vararg legs: Pair<Double, Double>, jitter: Double = 0.0): Route {
        var x = 0.0
        var y = 0.0
        val pts = mutableListOf(RoutePoint(lat0, 7.0))
        var n = 0
        for ((dir, len) in legs) {
            val steps = (len / 10).toInt()
            repeat(steps) {
                x += 10 * kotlin.math.sin(Math.toRadians(dir))
                y += 10 * kotlin.math.cos(Math.toRadians(dir))
                // Zittern quer zur Fahrtrichtung, abwechselnd links/rechts
                val j = if (n++ % 2 == 0) jitter else -jitter
                val jx = j * kotlin.math.cos(Math.toRadians(dir))
                val jy = -j * kotlin.math.sin(Math.toRadians(dir))
                pts += RoutePoint(lat0 + (y + jy) / 111_320.0, 7.0 + (x + jx) / mLon)
            }
        }
        return Route("t", "t", pts)
    }

    @Test
    fun rightTurnAtCorner() {
        val turns = TurnDetector.detect(route(0.0 to 500.0, 90.0 to 500.0))
        assertEquals(1, turns.size)
        assertEquals(TurnKind.RIGHT, turns[0].kind)
        assertEquals(500.0, turns[0].atM, 15.0)
    }

    @Test
    fun leftSlightSharpAndUturn() {
        assertEquals(TurnKind.LEFT, TurnDetector.detect(route(0.0 to 300.0, 270.0 to 300.0)).single().kind)
        assertEquals(TurnKind.SLIGHT_RIGHT, TurnDetector.detect(route(0.0 to 300.0, 45.0 to 300.0)).single().kind)
        assertEquals(TurnKind.SHARP_LEFT, TurnDetector.detect(route(0.0 to 300.0, 220.0 to 300.0)).single().kind)
        assertEquals(TurnKind.U_TURN, TurnDetector.detect(route(0.0 to 300.0, 180.0 to 300.0)).single().kind)
    }

    @Test
    fun gpsJitterOnStraightRoadIsNoTurn() {
        assertTrue(TurnDetector.detect(route(0.0 to 2000.0, jitter = 3.0)).isEmpty())
    }

    @Test
    fun gentleBendIsNoTurn() {
        assertTrue(TurnDetector.detect(route(0.0 to 300.0, 20.0 to 300.0)).isEmpty())
    }

    @Test
    fun twoCloseBendsAreOneTurn() {
        // 45 Grad, 20 m geradeaus, nochmal 45 Grad = eine Rechtskurve von 90 Grad
        val turns = TurnDetector.detect(route(0.0 to 300.0, 45.0 to 20.0, 90.0 to 300.0))
        assertEquals(1, turns.size)
        assertEquals(TurnKind.RIGHT, turns[0].kind)
    }

    @Test
    fun navigatorPicksNextTurnThenDestination() {
        val r = route(0.0 to 500.0, 90.0 to 500.0, 0.0 to 300.0)
        val turns = TurnDetector.detect(r)
        assertEquals(2, turns.size)
        val g1 = Navigator.next(turns, 320.0, r.lengthM)
        assertEquals(TurnKind.RIGHT, g1.turn!!.kind)
        assertEquals(180.0, g1.distanceM, 15.0)
        assertTrue(g1.text, g1.text.matches(Regex("→ 1[6-9]0 m rechts")))
        // kurz nach der zweiten Abbiegung (5 m) gilt sie noch als "jetzt"
        assertEquals(1, Navigator.next(turns, turns[1].atM + 5, r.lengthM).turnIndex)
        val dest = Navigator.next(turns, turns[1].atM + 50, r.lengthM)
        assertTrue(dest.destination)
        assertEquals(r.lengthM - turns[1].atM - 50, dest.distanceM, 0.01)
    }

    @Test
    fun announcerOncePerStage() {
        val r = route(0.0 to 500.0, 90.0 to 500.0)
        val turns = TurnDetector.detect(r)
        val a = TurnAnnouncer()
        assertNull(a.check(Navigator.next(turns, 200.0, r.lengthM)))
        assertEquals(TurnAlert.FAR, a.check(Navigator.next(turns, turns[0].atM - 140, r.lengthM)))
        assertNull(a.check(Navigator.next(turns, turns[0].atM - 100, r.lengthM)))
        assertEquals(TurnAlert.NOW, a.check(Navigator.next(turns, turns[0].atM - 25, r.lengthM)))
        assertNull(a.check(Navigator.next(turns, turns[0].atM - 10, r.lengthM)))
        assertEquals(TurnAlert.ARRIVED, a.check(Navigator.next(turns, r.lengthM - 10, r.lengthM)))
        assertNull(a.check(Navigator.next(turns, r.lengthM - 5, r.lengthM)))
    }

    @Test
    fun lateArrivalOnlyGetsNow() {
        val r = route(0.0 to 500.0, 90.0 to 500.0)
        val turns = TurnDetector.detect(r)
        val a = TurnAnnouncer()
        assertEquals(TurnAlert.NOW, a.check(Navigator.next(turns, turns[0].atM - 20, r.lengthM)))
        assertNull(a.check(Navigator.next(turns, turns[0].atM - 15, r.lengthM)))
    }

    @Test
    fun meters() {
        assertEquals("180 m", Guidance.formatMeters(187.0))
        assertEquals("1,2 km", Guidance.formatMeters(1234.0))
    }
}
