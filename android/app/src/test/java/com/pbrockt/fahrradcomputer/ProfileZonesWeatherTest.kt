package com.pbrockt.fahrradcomputer

import com.pbrockt.fahrradcomputer.data.Zones
import com.pbrockt.fahrradcomputer.recording.Climbs
import com.pbrockt.fahrradcomputer.recording.TrackPoint
import com.pbrockt.fahrradcomputer.routes.ElevationApi
import com.pbrockt.fahrradcomputer.routes.Route
import com.pbrockt.fahrradcomputer.routes.RoutePoint
import com.pbrockt.fahrradcomputer.routes.RouteProfile
import com.pbrockt.fahrradcomputer.weather.Weather
import com.pbrockt.fahrradcomputer.weather.WeatherRepository
import com.pbrockt.fahrradcomputer.weather.Wind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileZonesWeatherTest {

    // --- Zonen ---

    @Test
    fun powerZonesFromFtp() {
        val z = Zones.power(200.0)
        assertEquals(listOf(110, 150, 180, 210), z.thresholds)
        assertEquals(1, z.zoneOf(100))
        assertEquals(3, z.zoneOf(150))
        assertEquals(5, z.zoneOf(250))
    }

    @Test
    fun parseZones() {
        assertEquals(listOf(55, 70, 85, 100), Zones.parse("55, 70, 85, 100")!!.thresholds)
        assertNull(Zones.parse("60, 50, 90, 100")) // nicht aufsteigend
        assertNull(Zones.parse("60, 75, 90"))
    }

    @Test
    fun zoneBarPosition() {
        val z = Zones.DEFAULT_SPEED // 10, 18, 25, 32
        assertEquals(0.4f, z.position(18), 0.001f) // Anfang von Z3
        assertEquals(0.5f, z.position(21.5), 0.001f) // Mitte von Z3
        assertEquals(1.0f, z.position(80), 0.001f)
    }

    // --- Hoehenprofil ---

    /** 1 km flach, 1 km mit 5 % hinauf, 1 km mit 5 % hinunter. */
    private val hill: RouteProfile = RouteProfile.resample(
        doubleArrayOf(0.0, 1000.0, 2000.0, 3000.0),
        doubleArrayOf(100.0, 100.0, 150.0, 100.0),
    )!!

    @Test
    fun profileGradeAndAscent() {
        assertEquals(3000.0, hill.lengthM, 0.1)
        assertEquals(0.0, hill.gradeAt(500.0), 0.1)
        assertEquals(5.0, hill.gradeAt(1500.0), 0.2)
        assertEquals(-5.0, hill.gradeAt(2500.0), 0.2)
        assertEquals(50.0, hill.totalAscentM, 2.0)
        assertEquals(25.0, hill.ascentRemainingM(1500.0), 2.0)
        assertEquals(0.0, hill.ascentRemainingM(2500.0), 0.5)
    }

    @Test
    fun profileNeedsMostlyKnownElevation() {
        val c = doubleArrayOf(0.0, 100.0, 200.0, 300.0, 400.0)
        assertNull(RouteProfile.fromPoints(c, listOf(1.0, null, null, 4.0, 5.0)))
        assertNotNull(RouteProfile.fromPoints(c, listOf(1.0, 2.0, null, 4.0, 5.0)))
    }

    @Test
    fun gradeColorLevels() {
        assertEquals(0, RouteProfile.gradeLevel(-8.0))
        assertEquals(2, RouteProfile.gradeLevel(0.5))
        assertEquals(5, RouteProfile.gradeLevel(7.0))
        assertEquals(7, RouteProfile.gradeLevel(15.0))
    }

    @Test
    fun elevationSamplesEvery100m() {
        // ~1,1 km nach Norden (0,01 Grad)
        val r = Route("t", "Test", listOf(RoutePoint(50.0, 7.0), RoutePoint(50.01, 7.0)))
        val s = ElevationApi.samples(r)
        assertEquals(13, s.size) // 0, 100 ... 1100, Ende
        assertEquals(r.lengthM, s.last().first, 0.01)
        assertEquals(50.0045, s[5].second.lat, 0.0002)
    }

    // --- Steigung live ---

    @Test
    fun liveGradeFromTrack() {
        // 300 m mit 4 % Steigung, alle 10 m ein Punkt
        val pts = (0..30).map { i ->
            TrackPoint(i * 2000L, 50.0 + i * 10 / 111_320.0, 7.0, 200.0 + i * 0.4, null)
        }
        assertEquals(4.0, Climbs.liveGrade(pts)!!, 0.3)
        assertNull(Climbs.liveGrade(pts.take(3)))
    }

    // --- Wetter und Wind ---

    private val w = Weather(0, 50.0, 7.0, 12.0, windKmh = 20.0, windFromDeg = 270.0, gustKmh = 35.0, code = 3)

    @Test
    fun headAndTailwind() {
        // Wind aus Westen: nach Westen fahren = Gegenwind, nach Osten = Rueckenwind
        assertEquals(20.0, Wind.headwindKmh(w, 270.0), 0.01)
        assertEquals(-20.0, Wind.headwindKmh(w, 90.0), 0.01)
        assertEquals("Gegenwind", Wind.label(w, 270.0))
        assertEquals("Rückenwind", Wind.label(w, 90.0))
        assertEquals("Seitenwind", Wind.label(w, 0.0))
        // Pfeil: bei Rueckenwind zeigt er nach vorn (0 Grad)
        assertEquals(0f, Wind.arrowDeg(w, 90.0), 0.01f)
        assertEquals(180f, Wind.arrowDeg(w, 270.0), 0.01f)
        assertEquals("W", Wind.compass(270.0))
        assertEquals("NNO", Wind.compass(20.0))
    }

    @Test
    fun parseOpenMeteo() {
        val json = """{"latitude":50.35,"current":{"time":"2026-10-28T19:30","interval":900,"temperature_2m":10.2,
            "wind_speed_10m":3.2,"wind_direction_10m":191,"wind_gusts_10m":4.0,"weather_code":1}}"""
        val p = WeatherRepository.parse(json, 50.35, 7.6, 1L)
        assertEquals(10.2, p.temperatureC, 0.001)
        assertEquals(191.0, p.windFromDeg, 0.001)
        assertEquals("leicht bewölkt", p.description)
    }
}
