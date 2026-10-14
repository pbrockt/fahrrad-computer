package com.pbrockt.fahrradcomputer.health

import android.content.Context
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.CyclingPedalingCadenceRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Power
import androidx.health.connect.client.units.Velocity
import com.pbrockt.fahrradcomputer.recording.RideSummary
import com.pbrockt.fahrradcomputer.recording.RideTracker
import com.pbrockt.fahrradcomputer.recording.TrackPoint
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

enum class HealthAvailability { AVAILABLE, NEEDS_UPDATE, NOT_INSTALLED }

/**
 * Schreibt Fahrten nach Health Connect und liest das Gewicht.
 *
 * Jede Fahrt wird als Trainingseinheit "Radfahren" mit Strecke, Puls, Geschwindigkeit,
 * Trittfrequenz, Leistung, Distanz, Hoehenmetern und Kalorien abgelegt. Alle Eintraege
 * tragen eine feste clientRecordId aus der Fahrt-ID - ein zweites Senden ueberschreibt
 * dann, statt die Fahrt doppelt anzulegen.
 *
 * Health Connect lehnt einen ganzen Schreibvorgang ab, sobald fuer eine einzige Datenart
 * die Berechtigung fehlt. Deshalb wird nur geschrieben, was erlaubt ist.
 */
class HealthConnect(private val context: Context) {

    val availability: HealthAvailability
        get() = when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.NEEDS_UPDATE
            else -> HealthAvailability.NOT_INSTALLED
        }

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    val permissions: Set<String> = setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.PERMISSION_WRITE_EXERCISE_ROUTE,
        HealthPermission.getWritePermission(HeartRateRecord::class),
        HealthPermission.getWritePermission(SpeedRecord::class),
        HealthPermission.getWritePermission(CyclingPedalingCadenceRecord::class),
        HealthPermission.getWritePermission(PowerRecord::class),
        HealthPermission.getWritePermission(DistanceRecord::class),
        HealthPermission.getWritePermission(ElevationGainedRecord::class),
        HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
    )

    val requestContract = PermissionController.createRequestPermissionResultContract()

    suspend fun granted(): Set<String> =
        if (availability != HealthAvailability.AVAILABLE) emptySet()
        else runCatching { client.permissionController.getGrantedPermissions() }.getOrDefault(emptySet())

    /** Ohne die Einheit selbst ist alles andere wertlos - daran haengt "verbunden". */
    suspend fun canWriteRides(): Boolean =
        HealthPermission.getWritePermission(ExerciseSessionRecord::class) in granted()

    /** Schreibt eine Fahrt. Gibt null zurueck oder eine Fehlermeldung. */
    suspend fun writeRide(summary: RideSummary, points: List<TrackPoint>): String? {
        if (availability != HealthAvailability.AVAILABLE) return "Health Connect ist nicht verfügbar."
        val granted = granted()
        fun allowed(perm: String) = perm in granted
        fun allowed(type: kotlin.reflect.KClass<out Record>) = allowed(HealthPermission.getWritePermission(type))
        if (!allowed(ExerciseSessionRecord::class)) return "Keine Berechtigung für Health Connect."
        if (points.size < 2) return "Die Fahrt hat keine Strecke."

        val start = Instant.ofEpochMilli(points.first().timeMs)
        val end = Instant.ofEpochMilli(points.last().timeMs).let { if (it.isAfter(start)) it else start.plusSeconds(1) }
        val zStart = offset(start)
        val zEnd = offset(end)
        val device = Device(type = Device.TYPE_PHONE, manufacturer = Build.MANUFACTURER, model = Build.MODEL)
        fun meta(kind: String) = Metadata.activelyRecorded(device, "fahrrad-${summary.id}-$kind", RECORD_VERSION)

        val records = mutableListOf<Record>()

        val route = if (allowed(HealthPermission.PERMISSION_WRITE_EXERCISE_ROUTE)) {
            val step = maxOf(1, points.size / MAX_ROUTE_POINTS + 1)
            ExerciseRoute(points.filterIndexed { i, _ -> i % step == 0 }.map {
                ExerciseRoute.Location(
                    time = Instant.ofEpochMilli(it.timeMs),
                    latitude = it.lat,
                    longitude = it.lon,
                    altitude = it.altitude?.let(Length::meters),
                )
            })
        } else null
        records += ExerciseSessionRecord(
            startTime = start, startZoneOffset = zStart, endTime = end, endZoneOffset = zEnd,
            metadata = meta("session"),
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
            title = "Radfahrt",
            exerciseRoute = route,
        )

        // Messreihen in Abschnitte teilen - sehr lange Einzel-Eintraege lehnt Health Connect ab.
        val chunks = chunkByTime(points)

        if (allowed(HeartRateRecord::class)) chunks.forEachIndexed { i, c ->
            val samples = c.mapNotNull { p ->
                p.heartRate?.takeIf { it in 1..300 }?.let { HeartRateRecord.Sample(Instant.ofEpochMilli(p.timeMs), it.toLong()) }
            }
            if (samples.isNotEmpty()) records += HeartRateRecord(
                samples.first().time, offset(samples.first().time), samples.last().time.plusMillis(1), offset(samples.last().time),
                samples, meta("hr-$i"),
            )
        }
        if (allowed(CyclingPedalingCadenceRecord::class)) chunks.forEachIndexed { i, c ->
            val samples = c.mapNotNull { p ->
                p.cadence?.let { CyclingPedalingCadenceRecord.Sample(Instant.ofEpochMilli(p.timeMs), it.toDouble()) }
            }
            if (samples.isNotEmpty()) records += CyclingPedalingCadenceRecord(
                samples.first().time, offset(samples.first().time), samples.last().time.plusMillis(1), offset(samples.last().time),
                samples, meta("cad-$i"),
            )
        }
        if (allowed(PowerRecord::class)) chunks.forEachIndexed { i, c ->
            val samples = c.mapNotNull { p ->
                p.power?.let { PowerRecord.Sample(Instant.ofEpochMilli(p.timeMs), Power.watts(it.toDouble())) }
            }
            if (samples.isNotEmpty()) records += PowerRecord(
                samples.first().time, offset(samples.first().time), samples.last().time.plusMillis(1), offset(samples.last().time),
                samples, meta("power-$i"),
            )
        }
        if (allowed(SpeedRecord::class)) chunks.forEachIndexed { i, c ->
            val samples = c.zipWithNext().mapNotNull { (a, b) ->
                val dt = (b.timeMs - a.timeMs) / 1000.0
                if (dt <= 0 || dt * 1000 > RideTracker.MAX_GAP_MS) return@mapNotNull null
                val mps = RideTracker.distanceMeters(a.lat, a.lon, b.lat, b.lon) / dt
                if (mps * 3.6 > RideTracker.MAX_PLAUSIBLE_KMH) return@mapNotNull null
                SpeedRecord.Sample(Instant.ofEpochMilli(b.timeMs), Velocity.metersPerSecond(mps))
            }
            if (samples.isNotEmpty()) records += SpeedRecord(
                samples.first().time, offset(samples.first().time), samples.last().time.plusMillis(1), offset(samples.last().time),
                samples, meta("speed-$i"),
            )
        }
        if (allowed(DistanceRecord::class) && summary.distanceM > 0) {
            records += DistanceRecord(start, zStart, end, zEnd, Length.meters(summary.distanceM), meta("distance"))
        }
        if (allowed(ElevationGainedRecord::class) && summary.elevationGainM > 0) {
            records += ElevationGainedRecord(start, zStart, end, zEnd, Length.meters(summary.elevationGainM), meta("elevation"))
        }
        if (allowed(ActiveCaloriesBurnedRecord::class) && summary.calories > 0) {
            records += ActiveCaloriesBurnedRecord(start, zStart, end, zEnd, Energy.kilocalories(summary.calories), meta("calories"))
        }

        return try {
            client.insertRecords(records)
            null
        } catch (e: Exception) {
            "Health Connect hat abgelehnt: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    /** Das neueste Gewicht aus Health Connect (z. B. von einer Waage-App), oder null. */
    suspend fun latestWeightKg(): Double? {
        if (HealthPermission.getReadPermission(WeightRecord::class) !in granted()) return null
        return runCatching {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.after(Instant.now().minus(Duration.ofDays(365))),
                    ascendingOrder = false,
                    pageSize = 1,
                )
            ).records.firstOrNull()?.weight?.inKilograms
        }.getOrNull()
    }

    private fun offset(t: Instant): ZoneOffset = ZoneId.systemDefault().rules.getOffset(t)

    private fun chunkByTime(points: List<TrackPoint>): List<List<TrackPoint>> {
        val out = mutableListOf<MutableList<TrackPoint>>()
        for (p in points) {
            val cur = out.lastOrNull()
            if (cur == null || p.timeMs - cur.first().timeMs >= CHUNK_MS) out += mutableListOf(p) else cur += p
        }
        return out
    }

    companion object {
        /** Hoeher setzen, wenn sich das Format aendert - dann ueberschreibt ein erneutes Senden. */
        private const val RECORD_VERSION = 1L
        private const val CHUNK_MS = 10 * 60_000L
        private const val MAX_ROUTE_POINTS = 5_000
    }
}
