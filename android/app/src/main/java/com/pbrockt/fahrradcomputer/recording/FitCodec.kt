package com.pbrockt.fahrradcomputer.recording

import com.garmin.fit.Activity
import com.garmin.fit.ActivityMesg
import com.garmin.fit.DateTime
import com.garmin.fit.Decode
import com.garmin.fit.Event
import com.garmin.fit.EventMesg
import com.garmin.fit.EventType
import com.garmin.fit.FileEncoder
import com.garmin.fit.FileIdMesg
import com.garmin.fit.Fit
import com.garmin.fit.LapMesg
import com.garmin.fit.Manufacturer
import com.garmin.fit.MesgNum
import com.garmin.fit.RecordMesg
import com.garmin.fit.SessionMesg
import com.garmin.fit.Sport
import com.garmin.fit.SubSport
import java.io.File
import java.io.InputStream
import java.time.Instant
import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * Fahrten als FIT-Datei - das Format von Garmin, das Strava, Intervals.icu, Garmin Connect
 * und Zwift verstehen. Geschrieben und gelesen wird mit Garmins FIT-SDK.
 *
 * Export: ein Datensatz pro Punkt (Position, Hoehe, Distanz, Tempo, Puls, Trittfrequenz,
 * Leistung), dazu eine Runde, eine Sitzung mit den Summen und die Aktivitaet.
 * Import: nur die Datensaetze mit Position - aus ihnen rechnet die App die Kennzahlen selbst,
 * genau wie bei einer eigenen Fahrt.
 */
object FitCodec {

    private const val SEMICIRCLES = 2147483648.0 / 180.0

    /** Kennzahlen, aus den Punkten selbst berechnet - die Strecke kann fuers Teilen gekuerzt sein. */
    data class Totals(
        val distanceM: Double,
        val elapsedS: Double,
        val movingS: Double,
        val maxSpeedMps: Double,
        val avgHr: Int?,
        val maxHr: Int?,
        val avgCadence: Int?,
        val avgPower: Int?,
        val maxPower: Int?,
    )

    fun write(file: File, points: List<TrackPoint>, calories: Double, eBike: Boolean) {
        require(points.size >= 2) { "Zu wenige Punkte für eine FIT-Datei." }
        val start = dt(points.first().timeMs)
        val end = dt(points.last().timeMs)
        val totals = totals(points)
        val enc = FileEncoder(file, Fit.ProtocolVersion.V2_0)
        try {
            enc.write(FileIdMesg().apply {
                setType(com.garmin.fit.File.ACTIVITY)
                setManufacturer(Manufacturer.DEVELOPMENT)
                setProduct(1)
                setSerialNumber(1L)
                setTimeCreated(start)
                setProductName("Fahrrad-Computer")
            })
            enc.write(EventMesg().apply {
                setTimestamp(start)
                setEvent(Event.TIMER)
                setEventType(EventType.START)
            })
            var distance = 0.0
            for ((i, p) in points.withIndex()) {
                var speed: Double? = null
                if (i > 0) {
                    val prev = points[i - 1]
                    val d = RideTracker.distanceMeters(prev.lat, prev.lon, p.lat, p.lon)
                    distance += d
                    val dtS = (p.timeMs - prev.timeMs) / 1000.0
                    if (dtS > 0) speed = d / dtS
                }
                enc.write(RecordMesg().apply {
                    setTimestamp(dt(p.timeMs))
                    setPositionLat((p.lat * SEMICIRCLES).roundToInt())
                    setPositionLong((p.lon * SEMICIRCLES).roundToInt())
                    p.altitude?.let { setEnhancedAltitude(it.toFloat()) }
                    setDistance(distance.toFloat())
                    speed?.let { setEnhancedSpeed(it.toFloat()) }
                    p.heartRate?.let { setHeartRate(it.toShort()) }
                    p.cadence?.let { setCadence(it.toShort()) }
                    p.power?.let { setPower(it) }
                })
            }
            enc.write(EventMesg().apply {
                setTimestamp(end)
                setEvent(Event.TIMER)
                setEventType(EventType.STOP_ALL)
            })
            val ascent = RideSummaries.elevationGain(points).roundToInt()
            val avgSpeed = if (totals.movingS > 0) (totals.distanceM / totals.movingS).toFloat() else 0f
            val kcal = calories.roundToInt()
            enc.write(LapMesg().apply {
                setMessageIndex(0)
                setTimestamp(end)
                setStartTime(start)
                setEvent(Event.LAP)
                setEventType(EventType.STOP)
                setSport(Sport.CYCLING)
                setTotalElapsedTime(totals.elapsedS.toFloat())
                setTotalTimerTime(totals.movingS.toFloat())
                setTotalDistance(totals.distanceM.toFloat())
                setTotalCalories(kcal)
                setTotalAscent(ascent)
                setEnhancedAvgSpeed(avgSpeed)
                setEnhancedMaxSpeed(totals.maxSpeedMps.toFloat())
                totals.avgHr?.let { setAvgHeartRate(it.toShort()) }
                totals.maxHr?.let { setMaxHeartRate(it.toShort()) }
                totals.avgCadence?.let { setAvgCadence(it.toShort()) }
                totals.avgPower?.let { setAvgPower(it) }
                totals.maxPower?.let { setMaxPower(it) }
            })
            enc.write(SessionMesg().apply {
                setMessageIndex(0)
                setTimestamp(end)
                setStartTime(start)
                setEvent(Event.SESSION)
                setEventType(EventType.STOP)
                setSport(Sport.CYCLING)
                setSubSport(if (eBike) SubSport.E_BIKE_FITNESS else SubSport.ROAD)
                setTotalElapsedTime(totals.elapsedS.toFloat())
                setTotalTimerTime(totals.movingS.toFloat())
                setTotalDistance(totals.distanceM.toFloat())
                setTotalCalories(kcal)
                setTotalAscent(ascent)
                setEnhancedAvgSpeed(avgSpeed)
                setEnhancedMaxSpeed(totals.maxSpeedMps.toFloat())
                totals.avgHr?.let { setAvgHeartRate(it.toShort()) }
                totals.maxHr?.let { setMaxHeartRate(it.toShort()) }
                totals.avgCadence?.let { setAvgCadence(it.toShort()) }
                totals.avgPower?.let { setAvgPower(it) }
                totals.maxPower?.let { setMaxPower(it) }
                setFirstLapIndex(0)
                setNumLaps(1)
            })
            enc.write(ActivityMesg().apply {
                setTimestamp(end)
                setTotalTimerTime(totals.movingS.toFloat())
                setNumSessions(1)
                setType(Activity.MANUAL)
                setEvent(Event.ACTIVITY)
                setEventType(EventType.STOP)
                // Ortszeit = UTC + Abstand der Zeitzone, so zeigen Garmin & Co. die richtige Uhrzeit.
                val offsetS = TimeZone.getDefault().getOffset(points.last().timeMs) / 1000
                setLocalTimestamp(end.timestamp + offsetS)
            })
        } finally {
            enc.close()
        }
    }

    /**
     * Liest die Punkte einer FIT-Aktivitaet. Datensaetze ohne Position (Pausen, Indoor) und
     * ohne Zeit werden uebersprungen. Wirft eine Exception, wenn die Datei kaputt ist.
     */
    fun read(input: InputStream): List<TrackPoint> {
        val points = mutableListOf<TrackPoint>()
        val decode = Decode()
        val ok = decode.read(input) { mesg ->
            if (mesg.num != MesgNum.RECORD) return@read
            val r = RecordMesg(mesg)
            val time = r.timestamp?.date?.time ?: return@read
            val lat = r.positionLat ?: return@read
            val lon = r.positionLong ?: return@read
            points += TrackPoint(
                timeMs = time,
                lat = lat / SEMICIRCLES,
                lon = lon / SEMICIRCLES,
                altitude = (r.enhancedAltitude ?: r.altitude)?.toDouble(),
                heartRate = r.heartRate?.toInt()?.takeIf { it in 1..250 },
                cadence = r.cadence?.toInt()?.takeIf { it in 0..250 },
                power = r.power?.takeIf { it in 0..3000 },
            )
        }
        require(ok) { "Die FIT-Datei ist unvollständig oder beschädigt." }
        // Doppelte Zeitstempel (manche Geraete schreiben zwei Datensaetze pro Sekunde) zusammenfassen.
        return points.sortedBy { it.timeMs }.distinctBy { it.timeMs }
    }

    fun totals(points: List<TrackPoint>): Totals {
        var distance = 0.0
        var moving = 0.0
        var maxSpeed = 0.0
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val d = RideTracker.distanceMeters(a.lat, a.lon, b.lat, b.lon)
            val dtMs = b.timeMs - a.timeMs
            if (dtMs <= 0) continue
            distance += d
            val mps = d / (dtMs / 1000.0)
            if (mps * 3.6 >= RideTracker.MIN_MOVING_KMH && dtMs <= RideTracker.MAX_GAP_MS) {
                moving += dtMs / 1000.0
                maxSpeed = maxOf(maxSpeed, mps)
            }
        }
        val hr = points.mapNotNull { it.heartRate }
        val cad = points.mapNotNull { it.cadence }.filter { it > 0 }
        val power = points.mapNotNull { it.power }
        return Totals(
            distanceM = distance,
            elapsedS = (points.last().timeMs - points.first().timeMs) / 1000.0,
            movingS = moving,
            maxSpeedMps = maxSpeed,
            avgHr = hr.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            maxHr = hr.maxOrNull(),
            avgCadence = cad.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            avgPower = power.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            maxPower = power.maxOrNull(),
        )
    }

    private fun dt(ms: Long) = DateTime(Instant.ofEpochMilli(ms))
}
