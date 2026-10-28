package com.pbrockt.fahrradcomputer

import com.pbrockt.fahrradcomputer.recording.Climbs
import com.pbrockt.fahrradcomputer.recording.Sun
import com.pbrockt.fahrradcomputer.recording.TrackPoint
import com.pbrockt.fahrradcomputer.routes.RouteProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ClimbsSunTest {

    /** Ausgedachte Strecke: flach, 2 km mit 6 % rauf, Abfahrt, kleine Welle (15 m), 1 km mit 4 %. */
    private val profile = RouteProfile.resample(
        doubleArrayOf(0.0, 1000.0, 3000.0, 4000.0, 4500.0, 5000.0, 6000.0, 7000.0),
        doubleArrayOf(100.0, 100.0, 220.0, 120.0, 135.0, 120.0, 120.0, 160.0),
    )!!

    @Test
    fun findsRealClimbsOnly() {
        val c = profile.climbs
        assertEquals(2, c.size)
        assertEquals(1000.0, c[0].startM, 60.0)
        assertEquals(3000.0, c[0].endM, 60.0)
        assertEquals(120.0, c[0].gainM, 3.0)
        assertEquals(6.0, c[0].avgGrade, 0.4)
        assertEquals(2, c[1].number)
        assertEquals(40.0, c[1].gainM, 3.0)
    }

    @Test
    fun climbAtAndNext() {
        assertEquals(1, profile.climbAt(2000.0)!!.number)
        assertNull(profile.climbAt(3500.0))
        assertEquals(2, profile.nextClimb(3500.0)!!.number)
        assertNull(profile.nextClimb(6500.0))
    }

    @Test
    fun descentAndVam() {
        // 10 Minuten, alle 5 s ein Punkt: 5 min hinauf (+60 m), 5 min hinunter (-60 m)
        val pts = (0..120).map { i ->
            val alt = if (i <= 60) 100.0 + i else 220.0 - i
            TrackPoint(i * 5_000L, 50.0 + i * 0.0002, 7.0, alt, null)
        }
        val c = Climbs.of(pts)
        assertEquals(60.0, c.ascentM, 8.0)
        assertEquals(60.0, c.descentM, 8.0)
        assertEquals(0.0, c.vamMh!!, 0.1) // zuletzt bergab
        val up = Climbs.vam(pts.take(61))!!
        assertEquals(720.0, up, 60.0) // 60 m in 5 min = 720 m/h
    }

    @Test
    fun sunsetKoblenzMidsummer() {
        val t = Sun.times(LocalDate.of(2026, 6, 21), 50.35, 7.6)
        assertNotNull(t)
        val zone = ZoneId.of("Europe/Berlin")
        val set = t!!.sunset.atZone(zone).toLocalTime()
        val rise = t.sunrise.atZone(zone).toLocalTime()
        // Koblenz: Aufgang ~5:18, Untergang ~21:39 MESZ
        assertEquals(21 * 60 + 39.0, set.hour * 60.0 + set.minute, 6.0)
        assertEquals(5 * 60 + 18.0, rise.hour * 60.0 + rise.minute, 6.0)
    }

    @Test
    fun polarNight() {
        assertNull(Sun.times(LocalDate.of(2026, 12, 21), 80.0, 15.0))
    }
}
