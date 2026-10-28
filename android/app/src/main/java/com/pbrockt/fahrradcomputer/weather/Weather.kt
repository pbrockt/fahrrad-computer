package com.pbrockt.fahrradcomputer.weather

import com.pbrockt.fahrradcomputer.BuildConfig
import com.pbrockt.fahrradcomputer.recording.RideTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt

/** Aktuelles Wetter am Standort. Windrichtung wie im Wetterbericht: woher der Wind kommt. */
data class Weather(
    val timeMs: Long,
    val lat: Double,
    val lon: Double,
    val temperatureC: Double,
    val windKmh: Double,
    val windFromDeg: Double,
    val gustKmh: Double,
    val code: Int,
) {
    val description: String get() = Wind.describe(code)
}

object Wind {

    private val COMPASS = listOf("N", "NNO", "NO", "ONO", "O", "OSO", "SO", "SSO", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")

    /** "SW" fuer 225 Grad. */
    fun compass(deg: Double): String = COMPASS[(((deg % 360 + 360) % 360) / 22.5).roundToInt() % 16]

    /**
     * Wind in Fahrtrichtung: positiv = Gegenwind, negativ = Rueckenwind (km/h).
     * [headingDeg] ist die Fahrtrichtung (0 = Norden).
     */
    fun headwindKmh(w: Weather, headingDeg: Double): Double =
        w.windKmh * cos(Math.toRadians(w.windFromDeg - headingDeg))

    /**
     * Drehung fuer einen Pfeil, der zeigt, wohin der Wind weht - relativ zur Fahrtrichtung
     * (0 = von hinten, schiebt; 180 = von vorn). Ohne Fahrtrichtung: Norden oben.
     */
    fun arrowDeg(w: Weather, headingDeg: Double?): Float =
        (((w.windFromDeg + 180 - (headingDeg ?: 0.0)) % 360 + 360) % 360).toFloat()

    fun label(w: Weather, headingDeg: Double?): String {
        if (headingDeg == null) return "aus ${compass(w.windFromDeg)}"
        val h = headwindKmh(w, headingDeg)
        return when {
            h > w.windKmh * 0.35 -> "Gegenwind"
            h < -w.windKmh * 0.35 -> "Rückenwind"
            else -> "Seitenwind"
        }
    }

    /** Windstaerke nach Beaufort (0-12) aus km/h. */
    fun beaufort(kmh: Double): Int {
        val limits = doubleArrayOf(1.0, 6.0, 12.0, 20.0, 29.0, 39.0, 50.0, 62.0, 75.0, 89.0, 103.0, 118.0)
        return limits.count { kmh >= it }
    }

    /** WMO-Wettercode in kurzen Worten. */
    fun describe(code: Int): String = when (code) {
        0 -> "klar"
        1, 2 -> "leicht bewölkt"
        3 -> "bedeckt"
        45, 48 -> "Nebel"
        in 51..57 -> "Niesel"
        in 61..67 -> "Regen"
        in 71..77 -> "Schnee"
        in 80..82 -> "Schauer"
        85, 86 -> "Schneeschauer"
        in 95..99 -> "Gewitter"
        else -> ""
    }
}

/**
 * Holt das Wetter bei Open-Meteo (kostenlos, ohne Konto). Der Standort wird dabei auf
 * ~1 km gerundet. Neu geladen wird erst nach [MAX_AGE_MS] oder [MAX_MOVE_M] Weg.
 */
class WeatherRepository {

    private val _weather = MutableStateFlow<Weather?>(null)
    val weather: StateFlow<Weather?> = _weather.asStateFlow()
    private val mutex = Mutex()

    suspend fun update(lat: Double, lon: Double, nowMs: Long = System.currentTimeMillis()) = mutex.withLock {
        val w = _weather.value
        if (w != null && nowMs - w.timeMs < MAX_AGE_MS &&
            RideTracker.distanceMeters(w.lat, w.lon, lat, lon) < MAX_MOVE_M
        ) return@withLock
        runCatching { fetch(lat, lon, nowMs) }.getOrNull()?.let { _weather.value = it }
    }

    private suspend fun fetch(lat: Double, lon: Double, nowMs: Long): Weather = withContext(Dispatchers.IO) {
        val la = String.format(Locale.ROOT, "%.2f", lat)
        val lo = String.format(Locale.ROOT, "%.2f", lon)
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$la&longitude=$lo" +
            "&current=temperature_2m,wind_speed_10m,wind_direction_10m,wind_gusts_10m,weather_code&wind_speed_unit=kmh"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "Fahrrad-Computer/${BuildConfig.VERSION_NAME}")
        }
        try {
            check(conn.responseCode == 200) { "Wetterdienst antwortet mit ${conn.responseCode}" }
            parse(conn.inputStream.bufferedReader().use { it.readText() }, lat, lon, nowMs)
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val MAX_AGE_MS = 15 * 60_000L
        const val MAX_MOVE_M = 5_000.0

        fun parse(json: String, lat: Double, lon: Double, nowMs: Long): Weather {
            val c = Json.parseToJsonElement(json).jsonObject.getValue("current").jsonObject
            fun d(key: String) = c.getValue(key).jsonPrimitive.double
            return Weather(
                timeMs = nowMs, lat = lat, lon = lon,
                temperatureC = d("temperature_2m"),
                windKmh = d("wind_speed_10m"),
                windFromDeg = d("wind_direction_10m"),
                gustKmh = d("wind_gusts_10m"),
                code = c["weather_code"]?.jsonPrimitive?.int ?: -1,
            )
        }
    }
}
