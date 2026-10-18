package com.pbrockt.fahrradcomputer.recording

import com.pbrockt.fahrradcomputer.data.HrZones
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Fahrtrichtung aus den letzten Punkten - fuer die Karte "Fahrtrichtung oben". */
object Heading {

    /** So weit zurueckschauen; zwei direkt aufeinanderfolgende Punkte wackeln zu stark. */
    const val LOOKBACK_M = 15.0

    /** Richtung in Grad (0 = Nord, im Uhrzeigersinn) oder null, solange man steht. */
    fun of(points: List<TrackPoint>): Float? {
        if (points.size < 2) return null
        val last = points.last()
        for (i in points.size - 2 downTo maxOf(0, points.size - 60)) {
            val p = points[i]
            if (RideTracker.distanceMeters(p.lat, p.lon, last.lat, last.lon) >= LOOKBACK_M) {
                return bearing(p.lat, p.lon, last.lat, last.lon)
            }
        }
        return null
    }

    fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lon2 - lon1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return ((Math.toDegrees(atan2(y, x)) + 360) % 360).toFloat()
    }
}

/** Ein Stueck der Strecke in einer Farbstufe: Punkte [from]..[to] (einschliesslich). */
data class ColorRun(val from: Int, val to: Int, val level: Int)

/**
 * Faerbt eine aufgezeichnete Strecke ein - nach Tempo (Stufe 0 langsam .. 4 schnell) oder
 * nach Pulszone (Stufe 0..4 = Z1..Z5). Aufeinanderfolgende Punkte gleicher Stufe werden zu
 * einem Stueck zusammengefasst, damit die Karte nicht tausende Einzellinien zeichnen muss.
 */
object TrackColoring {

    const val SPEED_LEVELS = 5

    /** Grenzen der Tempo-Stufen in km/h, aus der Fahrt selbst (10. bis 90. Perzentil). */
    fun speedRange(points: List<TrackPoint>): Pair<Double, Double>? {
        val speeds = speeds(points).filter { it >= RideTracker.MIN_MOVING_KMH }.sorted()
        if (speeds.size < 5) return null
        val lo = speeds[speeds.size / 10]
        val hi = speeds[speeds.size * 9 / 10]
        return if (hi - lo < 1) (lo - 1) to (hi + 1) else lo to hi
    }

    fun bySpeed(points: List<TrackPoint>): List<ColorRun> {
        val range = speedRange(points) ?: return emptyList()
        val (lo, hi) = range
        // Geschwindigkeit ueber ein paar Punkte mitteln - einzelne GPS-Spruenge sollen nicht leuchten.
        val raw = speeds(points)
        val smooth = raw.indices.map { i ->
            val from = maxOf(0, i - 3); val to = minOf(raw.lastIndex, i + 3)
            (from..to).map { raw[it] }.average()
        }
        val levels = smooth.map { v ->
            (((v - lo) / (hi - lo)) * SPEED_LEVELS).toInt().coerceIn(0, SPEED_LEVELS - 1)
        }
        return runs(levels)
    }

    /** Ohne Pulswert bekommt ein Punkt die Stufe des vorigen; am Anfang ohne Puls: keine Farbe. */
    fun byHeartRate(points: List<TrackPoint>, zones: HrZones): List<ColorRun> {
        var last: Int? = null
        val levels = points.map { p -> p.heartRate?.let { zones.zoneOf(it) - 1 }?.also { last = it } ?: last }
        if (levels.all { it == null }) return emptyList()
        return runs(levels)
    }

    private fun speeds(points: List<TrackPoint>): List<Double> = points.indices.map { i ->
        if (i == 0) return@map 0.0
        val a = points[i - 1]; val b = points[i]
        val dt = (b.timeMs - a.timeMs) / 1000.0
        if (dt <= 0 || dt * 1000 > RideTracker.MAX_GAP_MS) 0.0
        else (RideTracker.distanceMeters(a.lat, a.lon, b.lat, b.lon) / dt * 3.6).coerceAtMost(RideTracker.MAX_PLAUSIBLE_KMH)
    }

    /** Stufen zu Stuecken zusammenfassen. Stuecke ueberlappen um einen Punkt - sonst Luecken. */
    private fun runs(levels: List<Int?>): List<ColorRun> {
        val out = mutableListOf<ColorRun>()
        var start = -1
        var cur: Int? = null
        for (i in levels.indices) {
            val l = levels[i]
            if (l != cur) {
                if (cur != null && start >= 0) out += ColorRun(start, i, cur)
                start = i
                cur = l
            }
        }
        if (cur != null && start >= 0 && start < levels.lastIndex) out += ColorRun(start, levels.lastIndex, cur)
        return out
    }
}
