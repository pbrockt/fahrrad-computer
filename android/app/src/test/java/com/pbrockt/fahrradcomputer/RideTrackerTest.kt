package com.pbrockt.fahrradcomputer

import com.pbrockt.fahrradcomputer.recording.LocationSample
import com.pbrockt.fahrradcomputer.recording.RideTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RideTrackerTest {

    // ~0,0001 Grad Breite = 11,1 m
    private fun sample(sec: Int, lat: Double, speed: Float? = null, acc: Float? = 5f) =
        LocationSample(sec * 1000L, lat, 7.5, 10.0, acc, speed)

    @Test
    fun distanceAndMovingTime() {
        val t = RideTracker()
        for (i in 0..10) t.onLocation(sample(i, 53.0 + i * 0.0001, speed = 6f))
        val s = t.snapshot
        assertEquals(111.2, s.distanceM, 1.0)
        assertEquals(10_000, s.movingTimeMs)
        assertEquals(21.6, s.speedKmh, 0.01)
        assertEquals(40.0, s.avgSpeedKmh, 1.0) // 111 m in 10 s
    }

    @Test
    fun standingStillCountsNothing() {
        val t = RideTracker()
        // GPS-Zittern an der Ampel: Speed ~0, Position springt um ein paar Meter.
        for (i in 0..20) t.onLocation(sample(i, 53.0 + (i % 2) * 0.00003, speed = 0.3f))
        assertEquals(0.0, t.snapshot.distanceM, 0.0)
        assertEquals(0, t.snapshot.movingTimeMs)
    }

    @Test
    fun inaccuratePointsAreIgnored() {
        val t = RideTracker()
        t.onLocation(sample(0, 53.0, speed = 6f))
        t.onLocation(sample(1, 53.01, speed = 6f, acc = 80f)) // 1 km Sprung, ungenau
        t.onLocation(sample(2, 53.0001, speed = 6f))
        assertEquals(11.1, t.snapshot.distanceM, 0.5)
        assertEquals(2, t.trackPoints.size)
    }

    @Test
    fun pauseStopsCountingAndResumeDoesNotBridgeGap() {
        val t = RideTracker()
        t.onLocation(sample(0, 53.0, speed = 6f))
        t.onLocation(sample(1, 53.0001, speed = 6f))
        t.pause()
        t.onLocation(sample(2, 53.0005, speed = 6f))
        t.resume()
        // Der erste Punkt nach der Pause ist nur Startpunkt - die Luecke zaehlt nicht.
        t.onLocation(sample(60, 53.001, speed = 6f))
        t.onLocation(sample(61, 53.0011, speed = 6f))
        assertEquals(22.2, t.snapshot.distanceM, 0.5)
        assertEquals(2_000, t.snapshot.movingTimeMs)
    }

    @Test
    fun heartRateAveragesAndAttachesToPoints() {
        val t = RideTracker()
        t.onHeartRate(100, 0)
        t.onLocation(sample(1, 53.0, speed = 6f))
        t.onHeartRate(140, 2_000)
        t.onLocation(sample(2, 53.0001, speed = 6f))
        t.onLocation(sample(10, 53.0002, speed = 6f)) // Pulswert 8 s alt -> nicht mehr anhaengen
        assertEquals(120, t.snapshot.avgHr)
        assertEquals(140, t.snapshot.maxHr)
        assertEquals(100, t.trackPoints[0].heartRate)
        assertEquals(140, t.trackPoints[1].heartRate)
        assertNull(t.trackPoints[2].heartRate)
    }

    @Test
    fun implausibleSpeedDoesNotSetMax() {
        val t = RideTracker()
        t.onLocation(sample(0, 53.0, speed = 8f))
        t.onLocation(sample(1, 53.0001, speed = 8f))
        t.onLocation(sample(2, 53.0002, speed = 40f)) // 144 km/h
        assertEquals(28.8, t.snapshot.maxSpeedKmh, 0.01)
    }
}
