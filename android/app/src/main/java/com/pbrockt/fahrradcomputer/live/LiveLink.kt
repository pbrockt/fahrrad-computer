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

    /** Server-Adresse und Upload-Key, z. B. aus dem QR-Code der Live-Seite. */
    data class Pairing(val url: String, val token: String) {
        val host: String get() = runCatching { java.net.URI(url).host }.getOrNull() ?: url
    }

    /**
     * Liest den Link der Live-Seite: `fahrradcomputer://live?url=<adresse>&token=<key>`.
     * null, wenn es kein solcher Link ist oder die Adresse kein http(s) ist.
     */
    fun parsePairing(text: String?): Pairing? {
        val uri = runCatching { java.net.URI(text?.trim() ?: return null) }.getOrNull() ?: return null
        if (uri.scheme != "fahrradcomputer" || uri.host != "live") return null
        val params = (uri.rawQuery ?: return null).split('&').mapNotNull { part ->
            val i = part.indexOf('=')
            if (i <= 0) null
            else part.substring(0, i) to java.net.URLDecoder.decode(part.substring(i + 1), "UTF-8")
        }.toMap()
        val url = params["url"]?.trim().orEmpty()
        val token = params["token"]?.trim().orEmpty()
        if (token.isEmpty() || !(url.startsWith("https://") || url.startsWith("http://"))) return null
        return Pairing(url, token)
    }

    /**
     * "https://server/live" oder ".../live/api.php" -> Adresse von api.php. Ohne https://
     * davor oder mit http:// wird https:// genommen - unverschluesselt laesst Android nicht zu.
     */
    fun apiUrl(base: String): String {
        var b = base.trim()
        // Den Zuschauer-Link eingefuegt? Dann gilt der Ordner davor.
        b = b.substringBefore('?').substringBefore('#')
        b = when {
            b.startsWith("https://", ignoreCase = true) -> b
            b.startsWith("http://", ignoreCase = true) -> "https://" + b.substring(7)
            else -> "https://$b"
        }
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

    /** Neue Fahrt auf dem Server: Upload-Key fuer die App und Link fuer Zuschauer. */
    data class NewRide(val upload: String, val viewUrl: String)

    /**
     * Legt vom Handy aus eine Fahrt an - wie "Neue Fahrt starten" auf der Live-Seite, nur ohne
     * Webseite. Braucht das Passwort der Live-Seite. Blockiert - nur im IO-Thread aufrufen.
     */
    fun createRide(baseUrl: String, password: String): Result<NewRide> {
        val body = buildJsonObject { put("create", true); put("password", password) }.toString()
        return post(baseUrl, "", body).mapCatching { json ->
            val upload = json["upload"]?.jsonPrimitive?.content.orEmpty()
            val view = json["view"]?.jsonPrimitive?.content.orEmpty()
            check(upload.isNotEmpty() && view.isNotEmpty()) { "Server kennt das Anlegen noch nicht – bitte die neue api.php hochladen." }
            NewRide(upload, view)
        }.recoverCatching { e ->
            // Alte api.php: hält "create" fuer einen Upload ohne Key.
            if (e.message?.contains("Upload-Key") == true) error("Server kennt das Anlegen noch nicht – bitte die neue api.php hochladen.")
            throw e
        }
    }

    /**
     * Schickt [body] an den Server. Blockiert - nur im IO-Thread aufrufen. Gibt die Antwort
     * zurueck oder einen Fehler mit verstaendlicher Meldung.
     */
    fun post(baseUrl: String, token: String, body: String): Result<JsonObject> {
        var url = apiUrl(baseUrl)
        return runCatching {
            // Weiterleitungen (z. B. auf www. oder https) selbst folgen - sonst wird aus dem
            // POST ein GET und der Server meldet "Link ungueltig".
            repeat(MAX_REDIRECTS + 1) {
                val r = postOnce(url, token, body)
                if (r.redirect == null) return@runCatching r.json
                url = r.redirect.replaceFirst(Regex("^http://", RegexOption.IGNORE_CASE), "https://")
            }
            error("Zu viele Weiterleitungen – stimmt die Adresse?")
        }.recoverCatching { throw IllegalStateException(explain(it, url), it) }
    }

    private class Reply(val json: JsonObject, val redirect: String?)

    private fun postOnce(url: String, token: String, body: String): Reply {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            instanceFollowRedirects = false
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
            // Manche Server geben "Authorization" nicht an PHP weiter - der zweite Weg.
            setRequestProperty("X-Live-Token", token)
            setRequestProperty("User-Agent", "Fahrrad-Computer/${BuildConfig.VERSION_NAME}")
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location") ?: error("Server leitet weiter, aber ohne Ziel ($code).")
                return Reply(JsonObject(emptyMap()), URL(URL(url), loc).toString())
            }
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
            val error = json?.get("error")?.jsonPrimitive?.content
            check(code in 200..299 && json?.get("ok") == JsonPrimitive(true)) {
                error ?: when {
                    code == 404 -> "api.php nicht gefunden – Adresse prüfen (z. B. https://deine-domain.de/live)."
                    json == null -> "Server antwortet mit $code, aber nicht wie erwartet – stimmt die Adresse?"
                    else -> "Server antwortet mit $code."
                }
            }
            return Reply(json!!, null)
        } finally {
            conn.disconnect()
        }
    }

    /** Fehler in Worte fassen, die man ohne Netzwerk-Wissen versteht. */
    private fun explain(e: Throwable, url: String): String {
        val host = runCatching { URL(url).host }.getOrNull() ?: url
        return when (e) {
            is java.net.UnknownHostException -> "Server „$host“ nicht gefunden – Adresse prüfen oder kein Internet."
            is java.net.SocketTimeoutException -> "„$host“ antwortet nicht (Zeitüberschreitung)."
            is java.net.ConnectException -> "Keine Verbindung zu „$host“."
            is javax.net.ssl.SSLException -> "Sichere Verbindung (https) zu „$host“ klappt nicht – ist SSL für die Domain eingeschaltet?"
            is java.net.MalformedURLException -> "Die Server-Adresse ist ungültig."
            else -> e.message ?: e.javaClass.simpleName
        }
    }

    private const val MAX_REDIRECTS = 3

    private fun round1(v: Double) = (v * 10).roundToInt() / 10.0
    private fun round6(v: Double) = Math.round(v * 1e6) / 1e6
}
