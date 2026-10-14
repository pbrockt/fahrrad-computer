package com.pbrockt.fahrradcomputer.recording

import com.pbrockt.fahrradcomputer.data.HrZones
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Die Kennzahlen einer Fahrt. Liegt als `<id>.json` neben der GPX-Datei, damit die
 * Fahrtenliste nicht bei jedem Oeffnen alle Strecken neu einlesen muss.
 */
@Serializable
data class RideSummary(
    /** Dateiname ohne Endung, z. B. "2026-10-14_08-30". */
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val distanceM: Double,
    val movingTimeMs: Long,
    val avgSpeedKmh: Double,
    val maxSpeedKmh: Double,
    val avgHr: Int? = null,
    val maxHr: Int? = null,
    val avgCadence: Int? = null,
    val avgPower: Int? = null,
    val maxPower: Int? = null,
    val calories: Double = 0.0,
    val calorieSource: CalorieSource = CalorieSource.NONE,
    val elevationGainM: Double = 0.0,
    /** Sekunden je Pulszone Z1..Z5; leer ohne Pulsdaten. */
    val zoneSeconds: List<Long> = emptyList(),
    val healthConnectSynced: Boolean = false,
)

object RideSummaries {

    /** Hoehenmeter erst ab dieser Aenderung zaehlen - GPS-Hoehe schwankt um einige Meter. */
    private const val ELEVATION_HYSTERESIS_M = 5.0

    fun build(id: String, points: List<TrackPoint>, stats: RideStats, zones: HrZones): RideSummary =
        RideSummary(
            id = id,
            startMs = points.firstOrNull()?.timeMs ?: 0,
            endMs = points.lastOrNull()?.timeMs ?: 0,
            distanceM = stats.distanceM,
            movingTimeMs = stats.movingTimeMs,
            avgSpeedKmh = stats.avgSpeedKmh,
            maxSpeedKmh = stats.maxSpeedKmh,
            avgHr = stats.avgHr,
            maxHr = stats.maxHr,
            avgCadence = stats.avgCadence,
            avgPower = stats.avgPower,
            maxPower = stats.maxPower,
            calories = stats.calories,
            calorieSource = stats.calorieSource,
            elevationGainM = elevationGain(points),
            zoneSeconds = zoneSeconds(points, zones),
        )

    /**
     * Fuer Fahrten, die nur als GPX vorliegen (aus Version 0.0.3 und frueher): die Punkte
     * noch einmal durch den [RideTracker] schicken - dieselbe Rechnung wie live.
     */
    fun fromTrack(id: String, points: List<TrackPoint>, body: BodyProfile, zones: HrZones): RideSummary {
        val t = RideTracker(body)
        for (p in points) {
            p.heartRate?.let { t.onHeartRate(it, p.timeMs) }
            p.cadence?.let { t.onCadence(it, p.timeMs) }
            p.power?.let { t.onPower(it, p.timeMs) }
            t.onLocation(LocationSample(p.timeMs, p.lat, p.lon, p.altitude))
        }
        return build(id, points, t.snapshot, zones)
    }

    fun elevationGain(points: List<TrackPoint>): Double {
        var base: Double? = null
        var gain = 0.0
        for (p in points) {
            val alt = p.altitude ?: continue
            val b = base
            when {
                b == null -> base = alt
                alt >= b + ELEVATION_HYSTERESIS_M -> { gain += alt - b; base = alt }
                alt <= b - ELEVATION_HYSTERESIS_M -> base = alt
            }
        }
        return gain
    }

    /** Zeit je Zone aus den Abstaenden der Punkte; Luecken (GPS weg) zaehlen nicht. */
    fun zoneSeconds(points: List<TrackPoint>, zones: HrZones): List<Long> {
        val ms = LongArray(5)
        var any = false
        for (i in 1 until points.size) {
            val hr = points[i].heartRate ?: continue
            val dt = points[i].timeMs - points[i - 1].timeMs
            if (dt <= 0 || dt > RideTracker.MAX_GAP_MS) continue
            ms[zones.zoneOf(hr) - 1] += dt
            any = true
        }
        return if (any) ms.map { it / 1000 } else emptyList()
    }
}

/** Summen ueber einen Zeitraum - fuer "diese Woche" und "dieser Monat". */
data class Totals(val rides: Int, val distanceM: Double, val movingTimeMs: Long, val calories: Double) {

    companion object {
        fun of(rides: List<RideSummary>, from: Instant, to: Instant): Totals {
            val r = rides.filter { Instant.ofEpochMilli(it.startMs).let { s -> !s.isBefore(from) && s.isBefore(to) } }
            return Totals(r.size, r.sumOf { it.distanceM }, r.sumOf { it.movingTimeMs }, r.sumOf { it.calories })
        }

        /** Montag 0 Uhr der Woche von [today] bis Montag darauf. */
        fun week(today: LocalDate, zone: ZoneId): Pair<Instant, Instant> {
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            return monday.atStartOfDay(zone).toInstant() to monday.plusWeeks(1).atStartOfDay(zone).toInstant()
        }

        fun month(today: LocalDate, zone: ZoneId): Pair<Instant, Instant> {
            val first = today.withDayOfMonth(1)
            return first.atStartOfDay(zone).toInstant() to first.plusMonths(1).atStartOfDay(zone).toInstant()
        }
    }
}
