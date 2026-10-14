package com.pbrockt.fahrradcomputer

import com.pbrockt.fahrradcomputer.data.HrZones
import com.pbrockt.fahrradcomputer.recording.BodyProfile
import com.pbrockt.fahrradcomputer.recording.CalorieSource
import com.pbrockt.fahrradcomputer.recording.GpxReader
import com.pbrockt.fahrradcomputer.recording.GpxWriter
import com.pbrockt.fahrradcomputer.recording.RideSummaries
import com.pbrockt.fahrradcomputer.recording.RideSummary
import com.pbrockt.fahrradcomputer.recording.Totals
import com.pbrockt.fahrradcomputer.recording.TrackPoint
import com.pbrockt.fahrradcomputer.recording.TrackSeries
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader
import java.io.StringWriter
import java.time.LocalDate
import java.time.ZoneOffset

class RideArchiveTest {

    private val t0 = 1_760_000_000_000L

    /** 10 Minuten, 1 Punkt/s, ~20 km/h nach Norden, Puls 130, Trittfrequenz 80, 150 W. */
    private fun ride(seconds: Int = 600, alt: (Int) -> Double? = { 10.0 }) = (0..seconds).map { i ->
        TrackPoint(t0 + i * 1000L, 53.0 + i * 0.00005, 7.5, alt(i), 130, 80, 150)
    }

    @Test
    fun gpxRoundTrip() {
        val points = ride(5)
        val out = StringWriter()
        GpxWriter.write(out, "x", points)
        val back = GpxReader.read(StringReader(out.toString()))
        assertEquals(points.size, back.size)
        assertEquals(points[3].copy(lat = back[3].lat, lon = back[3].lon), back[3])
        assertEquals(points[3].lat, back[3].lat, 1e-7)
    }

    @Test
    fun gpxWithoutSensorData() {
        val points = listOf(TrackPoint(t0, 53.0, 7.5, null, null), TrackPoint(t0 + 1000, 53.0001, 7.5, null, null))
        val out = StringWriter()
        GpxWriter.write(out, "x", points)
        assertEquals(points, GpxReader.read(StringReader(out.toString())))
    }

    @Test
    fun elevationGainIgnoresNoise() {
        // Rauschen +-2 m um 10 m, dann 30 m Anstieg in 1-m-Schritten
        val noise = (0 until 20).map { if (it % 2 == 0) 12.0 else 8.0 }
        val climb = (1..30).map { 10.0 + it }
        val pts = (noise + climb).mapIndexed { i, a -> TrackPoint(t0 + i * 1000L, 53.0, 7.5, a, null) }
        assertEquals(30.0, RideSummaries.elevationGain(pts), 5.0)
        assertTrue(RideSummaries.elevationGain(pts.take(20)) < 1.0)
    }

    @Test
    fun zoneSecondsFromPoints() {
        val z = HrZones(200) // Z3 ab 140, Z2 ab 120
        val pts = (0..10).map { TrackPoint(t0 + it * 1000L, 53.0, 7.5, null, if (it <= 5) 125 else 145) }
        assertEquals(listOf(0L, 5L, 5L, 0L, 0L), RideSummaries.zoneSeconds(pts, z))
        assertEquals(emptyList<Long>(), RideSummaries.zoneSeconds(pts.map { it.copy(heartRate = null) }, z))
    }

    @Test
    fun summaryFromTrackReplaysTracker() {
        val s = RideSummaries.fromTrack("r1", ride(), BodyProfile(80.0, 40, null), HrZones(200))
        assertEquals(600_000, s.movingTimeMs)
        assertEquals(3336.0, s.distanceM, 10.0) // 600 * 5,56 m
        assertEquals(130, s.avgHr)
        assertEquals(80, s.avgCadence)
        assertEquals(150, s.avgPower)
        assertEquals(CalorieSource.POWER, s.calorieSource)
        assertEquals(90.0, s.calories, 0.5) // 150 W * 600 s = 90 kJ
        assertEquals(600L, s.zoneSeconds[1])
    }

    @Test
    fun summaryJsonToleratesOlderFiles() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val s = RideSummaries.fromTrack("r1", ride(10), BodyProfile(), HrZones(200))
        assertEquals(s, json.decodeFromString<RideSummary>(json.encodeToString(s)))
        // Ein aelterer Stand ohne neuere Felder bleibt lesbar.
        val old = """{"id":"a","startMs":1,"endMs":2,"distanceM":3.0,"movingTimeMs":4,"avgSpeedKmh":5.0,"maxSpeedKmh":6.0,"kuenftigesFeld":1}"""
        val r = json.decodeFromString<RideSummary>(old)
        assertEquals(false, r.healthConnectSynced)
        assertNull(r.avgHr)
    }

    @Test
    fun weekAndMonthTotals() {
        val zone = ZoneOffset.UTC
        val wed = LocalDate.of(2026, 10, 14) // Mittwoch
        val (wStart, wEnd) = Totals.week(wed, zone)
        assertEquals(LocalDate.of(2026, 10, 12).atStartOfDay(zone).toInstant(), wStart)
        fun at(d: LocalDate, km: Double) = RideSummary(
            "x$d", d.atTime(9, 0).toInstant(zone).toEpochMilli(), 0, km * 1000, 3_600_000, 20.0, 30.0, calories = 300.0,
        )
        val rides = listOf(at(LocalDate.of(2026, 10, 11), 10.0), at(LocalDate.of(2026, 10, 12), 20.0), at(wed, 15.0))
        val week = Totals.of(rides, wStart, wEnd)
        assertEquals(2, week.rides)
        assertEquals(35_000.0, week.distanceM, 0.1)
        val (mStart, mEnd) = Totals.month(wed, zone)
        assertEquals(3, Totals.of(rides, mStart, mEnd).rides)
    }

    @Test
    fun seriesIsCondensed() {
        val s = TrackSeries.from(ride(1200), maxPoints = 300)
        assertTrue(s.minutes.size <= 300)
        assertEquals(20.0f, s.speedKmh[10]!!, 0.5f)
        assertEquals(130f, s.heartRate[10]!!, 0.01f)
        assertTrue(s.hasPower && s.hasCadence && s.hasAltitude)
    }
}
