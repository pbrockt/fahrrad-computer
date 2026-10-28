package com.pbrockt.fahrradcomputer

import com.garmin.fit.Decode
import com.garmin.fit.MesgNum
import com.garmin.fit.SessionMesg
import com.garmin.fit.SubSport
import com.pbrockt.fahrradcomputer.recording.FitCodec
import com.pbrockt.fahrradcomputer.recording.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FitCodecTest {

    /** 10 Minuten nach Norden, ~5 m/s, mit Puls, Trittfrequenz, Leistung - ausgedachte Strecke. */
    private val points = (0 until 600).map { i ->
        TrackPoint(
            timeMs = 1_760_000_000_000L + i * 1000L,
            lat = 50.0 + i * 0.000045,
            lon = 7.0,
            altitude = 100.0 + i * 0.05,
            heartRate = 120 + i % 20,
            cadence = 80 + i % 10,
            power = 150 + i % 30,
        )
    }

    private fun temp() = File.createTempFile("fahrt", ".fit").apply { deleteOnExit() }

    @Test
    fun roundTripKeepsEveryPoint() {
        val file = temp()
        FitCodec.write(file, points, calories = 250.0, eBike = true)
        val back = file.inputStream().buffered().use(FitCodec::read)
        assertEquals(points.size, back.size)
        for ((a, b) in points.zip(back)) {
            assertEquals(a.timeMs, b.timeMs)
            assertEquals(a.lat, b.lat, 1e-6)
            assertEquals(a.lon, b.lon, 1e-6)
            assertEquals(a.altitude!!, b.altitude!!, 0.3)
            assertEquals(a.heartRate, b.heartRate)
            assertEquals(a.cadence, b.cadence)
            assertEquals(a.power, b.power)
        }
    }

    @Test
    fun fileIsValidAndHasSession() {
        val file = temp()
        FitCodec.write(file, points, calories = 250.0, eBike = false)
        assertTrue(file.inputStream().buffered().use { Decode().checkFileIntegrity(it) })
        var session: SessionMesg? = null
        file.inputStream().buffered().use { input ->
            Decode().read(input) { m -> if (m.num == MesgNum.SESSION) session = SessionMesg(m) }
        }
        val s = session
        assertNotNull(s)
        s!!
        assertEquals(SubSport.ROAD, s.subSport)
        assertEquals(250, s.totalCalories)
        // 599 Abstaende à ~5 m
        assertEquals(2996.0, s.totalDistance.toDouble(), 15.0)
        assertEquals(599f, s.totalTimerTime, 1f)
        // Mittel von 150..179 = 164,5 -> gerundet 165
        assertEquals(165, s.avgPower)
    }
}
