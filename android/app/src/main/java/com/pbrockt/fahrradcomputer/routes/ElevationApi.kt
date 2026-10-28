package com.pbrockt.fahrradcomputer.routes

import com.pbrockt.fahrradcomputer.BuildConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.double
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Hoehen fuer Routen ohne Hoehenangaben, aus dem Copernicus-Hoehenmodell (90 m) ueber
 * die kostenlose Elevation-API von Open-Meteo. Pro Anfrage hoechstens 100 Punkte.
 */
object ElevationApi {

    private const val BATCH = 100
    private const val SAMPLE_M = 100.0

    /** Laedt Hoehen fuer die Route (alle ~100 m) und baut daraus das Profil. Blockiert - im IO-Thread aufrufen. */
    fun profileFor(route: Route): RouteProfile? {
        val samples = samples(route)
        if (samples.size < 2) return null
        val elevations = ArrayList<Double>(samples.size)
        for (chunk in samples.chunked(BATCH)) {
            val lat = chunk.joinToString(",") { String.format(Locale.ROOT, "%.5f", it.second.lat) }
            val lon = chunk.joinToString(",") { String.format(Locale.ROOT, "%.5f", it.second.lon) }
            val conn = (URL("https://api.open-meteo.com/v1/elevation?latitude=$lat&longitude=$lon").openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("User-Agent", "Fahrrad-Computer/${BuildConfig.VERSION_NAME}")
            }
            try {
                check(conn.responseCode == 200) { "Höhendienst antwortet mit ${conn.responseCode}" }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                Json.parseToJsonElement(body).jsonObject.getValue("elevation").jsonArray
                    .forEach { elevations += it.jsonPrimitive.double }
            } finally {
                conn.disconnect()
            }
        }
        if (elevations.size != samples.size) return null
        return RouteProfile.resample(samples.map { it.first }.toDoubleArray(), elevations.toDoubleArray())
    }

    /** Punkte entlang der Route alle [SAMPLE_M] Meter (plus Ende), mit Abstand vom Start. */
    fun samples(route: Route): List<Pair<Double, RoutePoint>> {
        val c = route.cumulative
        val pts = route.points
        if (pts.size < 2) return emptyList()
        val out = mutableListOf<Pair<Double, RoutePoint>>()
        var j = 0
        var d = 0.0
        while (d < route.lengthM) {
            while (j < pts.size - 2 && c[j + 1] < d) j++
            val span = c[j + 1] - c[j]
            val t = if (span <= 0) 0.0 else ((d - c[j]) / span).coerceIn(0.0, 1.0)
            val a = pts[j]
            val b = pts[j + 1]
            out += d to RoutePoint(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
            d += SAMPLE_M
        }
        out += route.lengthM to pts.last()
        return out
    }
}
