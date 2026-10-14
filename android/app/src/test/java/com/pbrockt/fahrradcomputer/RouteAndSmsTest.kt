package com.pbrockt.fahrradcomputer

import com.pbrockt.fahrradcomputer.live.LiveSms
import com.pbrockt.fahrradcomputer.recording.RideStats
import com.pbrockt.fahrradcomputer.routes.GpxRouteParser
import com.pbrockt.fahrradcomputer.routes.Route
import com.pbrockt.fahrradcomputer.routes.RoutePoint
import com.pbrockt.fahrradcomputer.routes.RouteTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class RouteAndSmsTest {

    private fun parse(xml: String) = GpxRouteParser.parse(xml.byteInputStream(), "id", "Datei")

    @Test
    fun komootStyleTrackWithNamespaces() {
        val r = parse(
            """<?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="komoot" xmlns="http://www.topografix.com/GPX/1/1">
              <metadata><name>Moseltour</name></metadata>
              <trk><name>Moseltour Track</name>
                <trkseg>
                  <trkpt lat="50.0" lon="7.0"><ele>100.5</ele><time>2026-01-01T00:00:00Z</time></trkpt>
                  <trkpt lat="50.001" lon="7.0"><ele>101</ele></trkpt>
                </trkseg>
                <trkseg>
                  <trkpt lat="50.002" lon="7.0"/>
                </trkseg>
              </trk>
            </gpx>"""
        )!!
        assertEquals("Moseltour", r.name)
        assertEquals(3, r.points.size)
        assertEquals(100.5, r.points[0].ele!!, 0.0)
        assertNull(r.points[2].ele)
        assertEquals(222.4, r.lengthM, 1.0)
    }

    @Test
    fun routePointsWhenNoTrack() {
        val r = parse(
            """<gpx><rte><name>R</name>
               <rtept lat="49.0" lon="8.0"><name>Start</name></rtept>
               <rtept lat="49.0" lon="8.01"><name>Ziel</name></rtept></rte></gpx>"""
        )!!
        assertEquals("R", r.name)
        assertEquals(2, r.points.size)
    }

    @Test
    fun withoutPointsIsNull() {
        assertNull(parse("""<gpx><wpt lat="1" lon="2"/></gpx>"""))
        assertEquals("Datei", parse("""<gpx><trk><trkseg><trkpt lat="1" lon="2"/><trkpt lat="1" lon="2.1"/></trkseg></trk></gpx>""")!!.name)
    }

    // Gerade Strecke nach Norden, 10 Punkte im Abstand von ~111 m.
    private val straight = Route("s", "s", (0..10).map { RoutePoint(50.0 + it * 0.001, 7.0) })

    @Test
    fun progressOnRoute() {
        val t = RouteTracker(straight)
        val p = t.update(50.0035, 7.0)
        assertEquals(389.2, p.doneM, 1.0)
        assertEquals(straight.lengthM - p.doneM, p.remainingM, 0.01)
        assertTrue(p.offRouteM < 1.0)
        assertFalse(p.offRoute)
    }

    @Test
    fun offRouteDetected() {
        val t = RouteTracker(straight)
        // ~143 m oestlich der Linie
        val p = t.update(50.005, 7.002)
        assertEquals(143.0, p.offRouteM, 2.0)
        assertTrue(p.offRoute)
    }

    @Test
    fun outAndBackStaysOnCurrentLeg() {
        // Hin und zurueck auf derselben Linie: 0..10 nach Norden, dann 10..0 zurueck.
        val pts = (0..10).map { RoutePoint(50.0 + it * 0.001, 7.0) } + (9 downTo 0).map { RoutePoint(50.0 + it * 0.001, 7.0) }
        val route = Route("ob", "ob", pts)
        val t = RouteTracker(route)
        t.update(50.0, 7.0)
        // Auf dem Hinweg bei Punkt 3 - darf nicht dem Rueckweg zugeordnet werden.
        for (i in 1..3) t.update(50.0 + i * 0.001, 7.0)
        val p = t.update(50.0035, 7.0)
        assertTrue("noch auf dem Hinweg", p.doneM < route.lengthM / 2)
    }

    @Test
    fun smsNumbers() {
        assertEquals(
            listOf("+491701234567", "01511234567"),
            LiveSms.parseNumbers("+49 170 1234567, 0151/123 45 67;\n12, +49 170 1234567"),
        )
    }

    @Test
    fun smsTextsFitInOneSms() {
        val stats = RideStats(distanceM = 23_456.0, movingTimeMs = 4_000_000, speedKmh = 18.4)
        val time = 1_760_000_000_000L // 08:53 UTC
        val pos = LiveSms.position("Phillipp", time, stats, 50.123456, 7.654321, 76, 12_345.0, ZoneOffset.UTC)
        assertEquals(
            "Phillipp 08:53: 23,5 km, 18 km/h, noch 12,3 km, Akku 76 %. https://maps.google.com/?q=50.12346,7.65432",
            pos,
        )
        assertTrue(pos.length <= 160)
        val start = LiveSms.start("", time, null, null, 10, ZoneOffset.UTC)
        assertEquals("Radfahrt: um 08:53 losgefahren. Standort alle 10 Min.", start)
        val stop = LiveSms.stop("Phillipp", time, stats, 50.0, 7.0, ZoneOffset.UTC)
        assertTrue(stop, stop.startsWith("Phillipp: um 08:53 angekommen, 23,5 km in 1:06:40."))
        assertTrue(stop.length <= 160)
    }
}
