package com.pbrockt.fahrradcomputer

import com.pbrockt.fahrradcomputer.live.LiveLink
import com.pbrockt.fahrradcomputer.recording.TrackPoint
import com.pbrockt.fahrradcomputer.routes.RoutePoint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveLinkTest {

    /** Alle 100 m ein Punkt nach Norden - ausgedachte Strecke. */
    private val track = (0..10).map { i -> TrackPoint(i * 20_000L, 50.0 + i * 100 / 111_320.0, 7.0, 100.0, 130 + i) }
    private val stats = LiveLink.Stats(1234.4, 600_000, 21.26, 19.94, 42.6, 5000.0, 80, 150)

    @Test
    fun apiUrl() {
        assertEquals("https://x.de/live/api.php", LiveLink.apiUrl("https://x.de/live/"))
        assertEquals("https://x.de/live/api.php", LiveLink.apiUrl(" https://x.de/live "))
        assertEquals("https://x.de/l/api.php", LiveLink.apiUrl("https://x.de/l/api.php"))
    }

    @Test
    fun hideStart() {
        assertEquals(6, LiveLink.firstVisible(track)) // 600 m > 500 m
        assertNull(LiveLink.firstVisible(track.take(5)))
        assertEquals(1, LiveLink.firstVisible(track, radiusM = 50.0))
    }

    @Test
    fun payloadWithoutHeartRate() {
        val json = Json.parseToJsonElement(
            LiveLink.payload("r1", "Max", "riding", stats, track.take(2), listOf(RoutePoint(50.0, 7.0), RoutePoint(50.1, 7.1)), false)
        ).jsonObject
        assertEquals("r1", json["ride"]!!.jsonPrimitive.content)
        val s = json["stats"]!!.jsonObject
        assertEquals(1234, s["distanceM"]!!.jsonPrimitive.int)
        assertEquals("21.3", s["speedKmh"]!!.jsonPrimitive.content)
        assertFalse(s.containsKey("hr"))
        val p = json["points"]!!.jsonArray
        assertEquals(2, p.size)
        assertFalse(p[0].jsonObject.containsKey("hr"))
        assertEquals(2, json["route"]!!.jsonArray.size)
    }

    @Test
    fun payloadWithHeartRateNoRoute() {
        val json = Json.parseToJsonElement(LiveLink.payload("r1", "", "paused", stats, track.take(1), null, true)).jsonObject
        assertEquals(150, json["stats"]!!.jsonObject["hr"]!!.jsonPrimitive.int)
        assertEquals(130, json["points"]!!.jsonArray[0].jsonObject["hr"]!!.jsonPrimitive.int)
        assertTrue(!json.containsKey("route"))
    }

    @Test
    fun thinRouteKeepsEnd() {
        val r = (0 until 5000).map { RoutePoint(it.toDouble(), 0.0) }
        val t = LiveLink.thinRoute(r, 2000)
        assertTrue(t.size <= 2001)
        assertEquals(4999.0, t.last().lat, 0.0)
    }

    @Test
    fun readsPairingLinkFromLivePage() {
        val p = LiveLink.parsePairing("fahrradcomputer://live?url=https%3A%2F%2Fbeispiel.de%2Flive&token=1a2b3c4d-00ff")!!
        assertEquals("https://beispiel.de/live", p.url)
        assertEquals("1a2b3c4d-00ff", p.token)
        assertEquals("beispiel.de", p.host)
    }

    @Test
    fun rejectsForeignCodes() {
        assertNull(LiveLink.parsePairing("https://beispiel.de/live?k=abc"))
        assertNull(LiveLink.parsePairing("fahrradcomputer://live?url=javascript%3Aalert(1)&token=x"))
        assertNull(LiveLink.parsePairing("fahrradcomputer://live?url=https%3A%2F%2Fa.de"))
        assertNull(LiveLink.parsePairing("Hallo"))
        assertNull(LiveLink.parsePairing(null))
    }
}
