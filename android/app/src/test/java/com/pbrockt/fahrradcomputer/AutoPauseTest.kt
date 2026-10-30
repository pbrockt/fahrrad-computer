package com.pbrockt.fahrradcomputer

import com.pbrockt.fahrradcomputer.recording.AutoPause
import com.pbrockt.fahrradcomputer.recording.LocationSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoPauseTest {

    private fun at(s: Int, kmh: Double?, accuracy: Float = 5f) =
        LocationSample(s * 1000L, 53.0, 7.5, accuracy = accuracy, speedMps = kmh?.let { (it / 3.6).toFloat() })

    @Test
    fun pausesAfterFiveSecondsStanding() {
        val a = AutoPause()
        assertFalse(a.shouldPause(at(0, 20.0)))
        val results = (1..7).map { a.shouldPause(at(it, 0.5)) }
        // Langsam ab Sekunde 1, also pausieren ab Sekunde 6.
        assertEquals(listOf(false, false, false, false, false, true, true), results)
    }

    @Test
    fun shortSlowdownDoesNotPause() {
        val a = AutoPause()
        for (t in 0..3) assertFalse(a.shouldPause(at(t, 1.0)))
        assertFalse(a.shouldPause(at(4, 12.0)))
        for (t in 5..8) assertFalse(a.shouldPause(at(t, 1.0)))
    }

    @Test
    fun inaccuratePointsAreIgnored() {
        val a = AutoPause()
        for (t in 0..10) assertFalse(a.shouldPause(at(t, 0.0, accuracy = 80f)))
    }

    @Test
    fun resumesOnlyAfterTwoFastPoints() {
        val a = AutoPause()
        assertFalse(a.shouldResume(at(0, 15.0)))
        assertFalse(a.shouldResume(at(1, 1.0)))
        assertFalse(a.shouldResume(at(2, 8.0)))
        assertTrue(a.shouldResume(at(3, 9.0)))
    }

    @Test
    fun speedFromDistanceWithoutGpsSpeed() {
        val a = AutoPause()
        assertEquals(null, a.speedKmh(LocationSample(0, 53.0, 7.5)))
        // ~111 m in 10 s = ~40 km/h
        val kmh = a.speedKmh(LocationSample(10_000, 53.001, 7.5))!!
        assertEquals(40.0, kmh, 1.0)
    }
}
