package com.pbrockt.fahrradcomputer.recording

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Ein GPS-Messpunkt, unabhaengig von android.location.Location (fuer Tests). */
data class LocationSample(
    val timeMs: Long,
    val lat: Double,
    val lon: Double,
    val altitude: Double? = null,
    /** Horizontale Genauigkeit in Metern. */
    val accuracy: Float? = null,
    /** Vom GPS gemeldete Geschwindigkeit in m/s. */
    val speedMps: Float? = null,
)

data class TrackPoint(
    val timeMs: Long,
    val lat: Double,
    val lon: Double,
    val altitude: Double?,
    val heartRate: Int?,
    val cadence: Int? = null,
    val power: Int? = null,
)

/** Momentaufnahme fuer das Dashboard. */
data class RideStats(
    val distanceM: Double = 0.0,
    /** Zeit in Bewegung - Standzeiten (Ampel) zaehlen nicht. */
    val movingTimeMs: Long = 0,
    val speedKmh: Double = 0.0,
    val maxSpeedKmh: Double = 0.0,
    val avgHr: Int? = null,
    val maxHr: Int? = null,
    val altitude: Double? = null,
    /** Ø Trittfrequenz nur ueber die Zeit, in der getreten wurde (ohne Rollphasen). */
    val avgCadence: Int? = null,
    /** Ø Leistung inklusive Rollphasen mit 0 W - so rechnen auch Garmin und Strava. */
    val avgPower: Int? = null,
    val maxPower: Int? = null,
    val calories: Double = 0.0,
    val calorieSource: CalorieSource = CalorieSource.NONE,
) {
    val avgSpeedKmh: Double
        get() = if (movingTimeMs <= 0) 0.0 else distanceM / (movingTimeMs / 1000.0) * 3.6
}

/**
 * Rechnet aus GPS-Punkten und Sensorwerten (Puls, Trittfrequenz, Leistung) die Fahrtwerte.
 *
 * Absichtlich ohne Android-Abhaengigkeiten, damit die Regeln als Unit-Test pruefbar sind:
 * - Punkte mit schlechter Genauigkeit werden verworfen (GPS-Spruenge).
 * - Unter [MIN_MOVING_KMH] gilt das Rad als stehend: keine Fahrzeit, keine Strecke -
 *   sonst summiert das GPS-Zittern an der Ampel Meter auf.
 * - Waehrend einer Pause wird nichts gezaehlt; der erste Punkt danach ist nur Startpunkt.
 * - Kalorien laufen mit jedem GPS-Punkt mit, aus der besten gerade frischen Quelle ([Calories]).
 */
class RideTracker(private val body: BodyProfile = BodyProfile()) {

    private var last: LocationSample? = null
    private var stats = RideStats()
    private val points = mutableListOf<TrackPoint>()

    /** Letzter Wert eines Sensors mit Zeitstempel - aeltere Werte gelten nicht mehr. */
    private class Latest {
        var value: Int? = null
        var timeMs = 0L
        fun set(v: Int, t: Long) { value = v; timeMs = t }
        fun fresh(t: Long): Int? = value?.takeIf { t - timeMs <= SENSOR_MAX_AGE_MS }
    }

    private val hr = Latest()
    private val cadence = Latest()
    private val power = Latest()

    private var hrSum = 0L
    private var hrCount = 0
    private var cadSum = 0L
    private var cadCount = 0
    private var powerSum = 0L
    private var powerCount = 0
    private var kcal = 0.0

    var paused = false
        private set

    val snapshot: RideStats get() = stats
    val trackPoints: List<TrackPoint> get() = points

    fun onLocation(s: LocationSample) {
        if (paused) return
        if (s.accuracy != null && s.accuracy > MAX_ACCURACY_M) return

        val prev = last
        last = s
        val currentHr = hr.fresh(s.timeMs)
        val currentPower = power.fresh(s.timeMs)
        points += TrackPoint(s.timeMs, s.lat, s.lon, s.altitude, currentHr, cadence.fresh(s.timeMs), currentPower)

        if (prev == null) {
            stats = stats.copy(altitude = s.altitude ?: stats.altitude)
            return
        }

        val dtMs = s.timeMs - prev.timeMs
        if (dtMs <= 0) return
        val dist = distanceMeters(prev.lat, prev.lon, s.lat, s.lon)
        val speedKmh = (s.speedMps?.toDouble() ?: (dist / (dtMs / 1000.0))) * 3.6
        val moving = speedKmh >= MIN_MOVING_KMH
        // Grosse Luecken (Tunnel, GPS weg) zaehlen nicht als Fahrzeit - die Strecke schon.
        val countTime = moving && dtMs <= MAX_GAP_MS

        // Ueber Luecken (GPS weg) wird nicht hochgerechnet - lieber zu wenig als erfunden.
        var source = stats.calorieSource
        if (dtMs <= MAX_GAP_MS) {
            val (rate, src) = Calories.perSecond(currentPower, currentHr, speedKmh, moving, body)
            kcal += rate * dtMs / 1000.0
            source = src
        }

        stats = stats.copy(
            calories = kcal,
            calorieSource = source,
            distanceM = stats.distanceM + if (moving) dist else 0.0,
            movingTimeMs = stats.movingTimeMs + if (countTime) dtMs else 0,
            speedKmh = if (moving) speedKmh else 0.0,
            maxSpeedKmh = if (moving && speedKmh <= MAX_PLAUSIBLE_KMH) {
                maxOf(stats.maxSpeedKmh, speedKmh)
            } else stats.maxSpeedKmh,
            altitude = s.altitude ?: stats.altitude,
        )
    }

    fun onHeartRate(bpm: Int, timeMs: Long) {
        hr.set(bpm, timeMs)
        if (paused) return
        hrSum += bpm
        hrCount++
        stats = stats.copy(
            avgHr = (hrSum / hrCount).toInt(),
            maxHr = maxOf(stats.maxHr ?: 0, bpm),
        )
    }

    fun onCadence(rpm: Int, timeMs: Long) {
        cadence.set(rpm, timeMs)
        if (paused || rpm <= 0) return
        cadSum += rpm
        cadCount++
        stats = stats.copy(avgCadence = (cadSum / cadCount).toInt())
    }

    fun onPower(watts: Int, timeMs: Long) {
        power.set(watts, timeMs)
        if (paused) return
        powerSum += watts
        powerCount++
        stats = stats.copy(
            avgPower = (powerSum / powerCount).toInt(),
            maxPower = maxOf(stats.maxPower ?: 0, watts),
        )
    }

    fun pause() {
        paused = true
        stats = stats.copy(speedKmh = 0.0)
    }

    fun resume() {
        paused = false
        last = null
    }

    companion object {
        const val MAX_ACCURACY_M = 30f
        const val MIN_MOVING_KMH = 3.0
        const val MAX_GAP_MS = 30_000L
        const val MAX_PLAUSIBLE_KMH = 90.0
        const val SENSOR_MAX_AGE_MS = 5_000L

        fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6_371_000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
            return 2 * r * asin(sqrt(a))
        }
    }
}
