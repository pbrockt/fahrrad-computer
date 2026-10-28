package com.pbrockt.fahrradcomputer.live

import com.pbrockt.fahrradcomputer.BuildConfig
import com.pbrockt.fahrradcomputer.recording.RideTracker
import com.pbrockt.fahrradcomputer.recording.TrackPoint
import com.pbrockt.fahrradcomputer.routes.RoutePoint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/**
 * Live-Link zum eigenen Server (server/live im Repo): Die App schickt waehrend der Fahrt
 * regelmaessig neue Punkte und Kennzahlen an `api.php`, Zuschauer sehen sie auf einer
 * Karte. Hier steht nur, *was* gesendet wird - das *Wann* macht der RideService.
 */
object LiveLink {

    /** Start ausblenden: erst ab so viel Abstand vom Startpunkt wird gesendet. */
    const val HIDE_START_M = 500.0
    private const val MAX_ROUTE_POINTS = 2000

    /** "https://server/live" oder ".../live/api.php" -> Adresse von api.php. */
    fun apiUrl(base: String): String {
        val b = base.trim()
        return if (b.endsWith(".php")) b else b.trimEnd('/') + "/api.php"
    }

    /**
     * Ab welchem Punkt gesendet werden darf: der erste, der weiter als [radiusM] vom Start
     * entfernt ist. null = noch keiner (man ist noch in der Naehe der Haustuer).
     */
    fun firstVisible(track: List<TrackPoint>, radiusM: Double = HIDE_START_M): Int? {
        val start = track.firstOrNull() ?: return null
        val i = track.indexOfFirst { RideTracker.distanceMeters(start.lat, start.lon, it.lat, it.lon) > radiusM }
        return i.takeIf { it >= 0 }
    }

    /** Route fuer die Live-Seite ausduennen - jeder n-te Punkt, Ende immer dabei. */
    fun thinRoute(route: List<RoutePoint>, max: Int = MAX_ROUTE_POINTS): List<RoutePoint> {
        if (route.size <= max) return route
        val step = (route.size + max - 1) / max
        return route.filterIndexed { i, _ -> i % step == 0 } + route.last()
    }

    data class Stats(
        val distanceM: Double,
        val movingMs: Long,
        val speedKmh: Double,
        val avgKmh: Double,
        val ascentM: Double,
        val remainingM: Double?,
        val battery: Int?,
        val heartRate: Int?,
    )

    fun payload(
        ride: String,
        name: String,
        state: String,
        stats: Stats,
        points: List<TrackPoint>,
        route: List<RoutePoint>?,
        includeHeartRate: Boolean,
    ): String = buildJsonObject {
        put("ride", ride)
        put("name", name)
        put("state", state)
        putJsonObject("stats") {
            put("distanceM", stats.distanceM.roundToInt())
            put("movingMs", stats.movingMs)
            put("speedKmh", round1(stats.speedKmh))
            put("avgKmh", round1(stats.avgKmh))
            put("ascentM", stats.ascentM.roundToInt())
            stats.remainingM?.let { put("remainingM", it.roundToInt()) }
            stats.battery?.let { put("battery", it) }
            if (includeHeartRate) stats.heartRate?.let { put("hr", it) }
        }
        putJsonArray("points") {
            for (p in points) {
                add(buildJsonObject {
                    put("t", p.timeMs)
                    put("lat", round6(p.lat))
                    put("lon", round6(p.lon))
                    p.altitude?.let { put("ele", round1(it)) }
                    if (includeHeartRate) p.heartRate?.let { put("hr", it) }
                })
            }
        }
        if (route != null) {
            putJsonArray("route") {
                for (p in thinRoute(route)) addJsonArray { add(round6(p.lat)); add(round6(p.lon)) }
            }
        }
    }.toString()

    fun ping(): String = """{"ping":true}"""

    /**
     * Schickt [body] an den Server. Blockiert - nur im IO-Thread aufrufen. Gibt die Antwort
     * zurueck oder einen Fehler mit verstaendlicher Meldung.
     */
    fun post(baseUrl: String, token: String, body: String): Result<JsonObject> = runCatching {
        val conn = (URL(apiUrl(baseUrl)).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer $token")
            // Manche Server geben "Authorization" nicht an PHP weiter - der zweite Weg.
            setRequestProperty("X-Live-Token", token)
            setRequestProperty("User-Agent", "Fahrrad-Computer/${BuildConfig.VERSION_NAME}")
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
            val error = json?.get("error")?.jsonPrimitive?.content
            check(code in 200..299 && json?.get("ok") == JsonPrimitive(true)) {
                error ?: "Server antwortet mit $code" + if (json == null) " (kein JSON – stimmt die Adresse?)" else ""
            }
            json!!
        } finally {
            conn.disconnect()
        }
    }

    private fun round1(v: Double) = (v * 10).roundToInt() / 10.0
    private fun round6(v: Double) = Math.round(v * 1e6) / 1e6
}
