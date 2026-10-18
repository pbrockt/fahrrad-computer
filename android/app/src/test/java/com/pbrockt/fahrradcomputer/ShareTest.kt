package com.pbrockt.fahrradcomputer

import com.pbrockt.fahrradcomputer.recording.RideTracker
import com.pbrockt.fahrradcomputer.recording.TrackPoint
import com.pbrockt.fahrradcomputer.share.PrivacyTrim
import com.pbrockt.fahrradcomputer.share.ProjectedRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareTest {

    // 5 km nach Norden, ein Punkt alle ~11 m
    private val ride = (0..450).map { TrackPoint(it * 1000L, 50.0 + it * 0.0001, 7.0, null, null) }

    private fun distanceBetween(a: TrackPoint, b: TrackPoint) = RideTracker.distanceMeters(a.lat, a.lon, b.lat, b.lon)

    @Test
    fun trimRemovesStartAndEnd() {
        val t = PrivacyTrim.trim(ride, 500.0, 500.0)
        assertTrue(distanceBetween(ride.first(), t.first()) >= 500.0)
        assertTrue(distanceBetween(t.last(), ride.last()) >= 500.0)
        // nicht mehr als noetig weg
        assertTrue(distanceBetween(ride.first(), t.first()) < 515.0)
        assertTrue(distanceBetween(t.last(), ride.last()) < 515.0)
    }

    @Test
    fun noTrimKeepsEverything() {
        assertEquals(ride, PrivacyTrim.trim(ride, 0.0, 0.0))
    }

    @Test
    fun tooShortGivesEmpty() {
        // 5 km Fahrt, je 2,5 km weg -> nichts bleibt
        assertTrue(PrivacyTrim.trim(ride, 2500.0, 2500.0).isEmpty())
        assertTrue(PrivacyTrim.trim(ride.take(50), 500.0, 500.0).isEmpty())
    }

    @Test
    fun projectionFitsBoxAndKeepsAspect() {
        // Nord-Sued-Strecke: schmal und hoch, muss die Hoehe ausfuellen und mittig stehen.
        val p = ProjectedRoute.fit(ride, 100f, 200f, 800f, 600f)
        assertEquals(200f, p.ys.min(), 0.5f)
        assertEquals(800f, p.ys.max(), 0.5f)
        // Norden ist oben: erster Punkt (Sueden) unten
        assertTrue(p.ys.first() > p.ys.last())
        assertEquals(500f, p.xs.first(), 0.5f) // horizontal mittig
    }

    @Test
    fun positionMovesEvenlyAlongRoute() {
        val p = ProjectedRoute.fit(ride, 0f, 0f, 1000f, 1000f)
        val (_, _, yStart) = p.positionAt(0f)
        val (_, _, yHalf) = p.positionAt(0.5f)
        val (last, _, yEnd) = p.positionAt(1f)
        assertEquals((yStart + yEnd) / 2, yHalf, 2f)
        assertEquals(p.size - 1, last)
    }
}
