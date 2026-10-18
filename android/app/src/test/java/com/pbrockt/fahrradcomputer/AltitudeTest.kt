package com.pbrockt.fahrradcomputer

import com.pbrockt.fahrradcomputer.recording.Altitude
import com.pbrockt.fahrradcomputer.recording.RideSummaries
import com.pbrockt.fahrradcomputer.recording.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AltitudeTest {

    private fun points(alts: List<Double?>) = alts.mapIndexed { i, a -> TrackPoint(i * 1000L, 50.0, 7.0, a, null) }

    /** Typisches GPS: wahre Hoehe plus Rauschen +-8 m und gelegentliche Ausreisser von 30 m. */
    private fun gps(trueAlt: (Int) -> Double, n: Int, seed: Int = 1): List<Double> {
        val r = Random(seed)
        return (0 until n).map { i ->
            trueAlt(i) + r.nextDouble(-8.0, 8.0) + if (r.nextInt(40) == 0) 30.0 else 0.0
        }
    }

    @Test
    fun flatRideHasAlmostNoClimb() {
        // 30 Minuten flach bei 100 m: roh zaehlt das Rauschen Dutzende Hoehenmeter zusammen.
        val raw = gps({ 100.0 }, 1800)
        val gain = RideSummaries.elevationGain(points(raw))
        assertTrue("flach: $gain Hm", gain < 15)
    }

    @Test
    fun realClimbIsKept() {
        // 10 Minuten gleichmaessig 120 m bergauf, dann 10 Minuten flach.
        val raw = gps({ i -> 100.0 + minOf(i, 600) * 0.2 }, 1200)
        val gain = RideSummaries.elevationGain(points(raw))
        assertEquals(120.0, gain, 20.0)
    }

    @Test
    fun smoothingRemovesSpikesAndKeepsGaps() {
        val alts = MutableList<Double?>(30) { 200.0 }
        alts[10] = 260.0 // einzelner Ausreisser
        alts[20] = null
        val s = Altitude.smooth(alts)
        assertEquals(200.0, s[10]!!, 0.5)
        assertNull(s[20])
    }

    @Test
    fun displayRangeHasMinimumSpan() {
        assertEquals(170.0 to 230.0, Altitude.displayRange(195.0, 205.0))
        assertEquals(100.0 to 400.0, Altitude.displayRange(100.0, 400.0))
    }
}
