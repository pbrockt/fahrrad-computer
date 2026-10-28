package com.pbrockt.fahrradcomputer.routes

import com.pbrockt.fahrradcomputer.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Fahrprofile von BRouter - wie die Profile bei Komoot. */
enum class BikeProfile(val label: String, val id: String) {
    TOUR("Tour", "trekking"),
    FAST("Schnell (Straße)", "fastbike"),
    SAFE("Ruhig & sicher", "safety"),
    MTB("Mountainbike", "mtb"),
    SHORT("Kürzeste", "shortest"),
}

/**
 * Plant Fahrradrouten ueber den oeffentlichen BRouter-Server (brouter.de, OpenStreetMap,
 * ohne Konto). Die Antwort ist eine GPX-Datei mit Hoehen - sie wird wie eine importierte
 * Route gespeichert, mit Hoehenprofil, Anstiegen und Abbiegehinweisen.
 */
object Routing {

    /** Wegpunkte als (Breite, Laenge). Blockiert - im IO-Thread aufrufen. */
    fun route(points: List<Pair<Double, Double>>, profile: BikeProfile): String {
        require(points.size >= 2) { "Start und Ziel nötig." }
        val lonlats = points.joinToString("|") { (lat, lon) -> String.format(Locale.ROOT, "%.6f,%.6f", lon, lat) }
        val url = "https://brouter.de/brouter?lonlats=$lonlats&profile=${profile.id}&alternativeidx=0&format=gpx"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", "Fahrrad-Computer/${BuildConfig.VERSION_NAME}")
        }
        try {
            val ok = conn.responseCode == 200
            val text = (if (ok) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            check(ok && text.contains("<trkpt")) {
                // BRouter meldet Fehler als Klartext, z. B. "position not mapped" weitab von Wegen.
                "Keine Route gefunden" + text.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() && !it.startsWith("<") }?.let { ": $it" }.orEmpty()
            }
            return text
        } finally {
            conn.disconnect()
        }
    }
}
