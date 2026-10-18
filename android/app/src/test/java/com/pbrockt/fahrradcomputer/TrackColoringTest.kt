package com.pbrockt.fahrradcomputer

import com.pbrockt.fahrradcomputer.data.HrZones
import com.pbrockt.fahrradcomputer.recording.Heading
import com.pbrockt.fahrradcomputer.recording.TrackColoring
import com.pbrockt.fahrradcomputer.recording.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackColoringTest {

    @Test
    fun headingPointsWhereWeGo() {
        val north = (0..10).map { TrackPoint(it * 1000L, 50.0 + it * 0.00005, 7.0, null, null) }
        assertEquals(0f, Heading.of(north)!!, 1f)
        val east = (0..10).map { TrackPoint(it * 1000L, 50.0, 7.0 + it * 0.00008, null, null) }
        assertEquals(90f, Heading.of(east)!!, 1f)
        val west = east.reversed().mapIndexed { i, p -> p.copy(timeMs = i * 1000L) }
        assertEquals(270f, Heading.of(west)!!, 1f)
        // stehend (alles am selben Ort): keine Richtung
        assertNull(Heading.of(List(10) { TrackPoint(it * 1000L, 50.0, 7.0, null, null) }))
    }

    @Test
    fun speedColoringSlowThenFast() {
        // 100 Punkte langsam (~10 km/h), dann 100 Punkte schnell (~30 km/h)
        var lat = 50.0
        val pts = (0 until 200).map { i ->
            lat += if (i < 100) 0.000025 else 0.000075
            TrackPoint(i * 1000L, lat, 7.0, null, null)
        }
        val runs = TrackColoring.bySpeed(pts)
        assertTrue(runs.isNotEmpty())
        assertEquals(0, runs.first().level)
        assertEquals(TrackColoring.SPEED_LEVELS - 1, runs.last().level)
        // Stuecke schliessen lueckenlos aneinander an
        runs.zipWithNext().forEach { (a, b) -> assertEquals(a.to, b.from) }
        assertEquals(199, runs.last().to)
    }

    @Test
    fun heartRateColoringUsesZones() {
        val z = HrZones(200) // Z2 ab 120, Z4 ab 160
        val pts = (0 until 20).map { i -> TrackPoint(i * 1000L, 50.0 + i * 0.0001, 7.0, null, if (i < 10) 125 else 165) }
        val runs = TrackColoring.byHeartRate(pts, z)
        assertEquals(listOf(1, 3), runs.map { it.level })
        assertTrue(TrackColoring.byHeartRate(pts.map { it.copy(heartRate = null) }, z).isEmpty())
    }
}
