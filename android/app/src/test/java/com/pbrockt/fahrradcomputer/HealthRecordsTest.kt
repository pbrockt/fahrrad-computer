package com.pbrockt.fahrradcomputer

import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.metadata.Device
import com.pbrockt.fahrradcomputer.data.HrZones
import com.pbrockt.fahrradcomputer.health.HealthConnect
import com.pbrockt.fahrradcomputer.recording.BodyProfile
import com.pbrockt.fahrradcomputer.recording.RideSummaries
import com.pbrockt.fahrradcomputer.recording.Sex
import com.pbrockt.fahrradcomputer.recording.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class HealthRecordsTest {

    private val t0 = 1_760_700_000_000L

    /** 25 Minuten, 1 Punkt pro Sekunde, mit allen Messwerten - wie eine echte Fahrt. */
    private val points = (0..1500).map { i ->
        TrackPoint(t0 + i * 1000L, 50.35 + i * 0.00005, 7.6, 120.0 + (i % 40), 125 + i % 20, 80, 150)
    }
    private val summary = RideSummaries.fromTrack("2026-10-17_14-00", points, BodyProfile(80.0, 45, Sex.MALE), HrZones(180))
    private val device = Device(type = Device.TYPE_PHONE, manufacturer = "Test", model = "Test")

    @Test
    fun buildsAllRecordsWithoutValidationErrors() {
        // Genau hier ist 0.0.7/0.0.8 abgestuerzt: Der letzte Streckenpunkt lag nicht VOR dem Ende.
        val records = HealthConnect.buildRecords(summary, points, { true }, device, ZoneId.of("Europe/Berlin"))

        val session = records.filterIsInstance<ExerciseSessionRecord>().single()
        val route = (session.exerciseRouteResult as ExerciseRouteResult.Data).exerciseRoute.route
        assertTrue(route.last().time.isBefore(session.endTime))
        assertEquals(points.size, route.size)

        // 25 Minuten in 10-Minuten-Abschnitten = 3 Puls-Eintraege
        assertEquals(3, records.filterIsInstance<HeartRateRecord>().size)
        val kinds = records.map { it.javaClass.simpleName }.toSet()
        assertTrue(kinds.toString(), kinds.containsAll(listOf(
            "ExerciseSessionRecord", "HeartRateRecord", "SpeedRecord", "PowerRecord",
            "CyclingPedalingCadenceRecord", "DistanceRecord", "ElevationGainedRecord", "ActiveCaloriesBurnedRecord",
        )))
    }

    @Test
    fun onlyAllowedTypesAreBuilt() {
        val onlySession = HealthConnect.buildRecords(
            summary, points, { it == "android.permission.health.WRITE_EXERCISE" }, device,
        )
        // Ohne Routen-Recht keine Strecke, ohne Puls-Recht kein Puls - aber Trittfrequenz
        // (laeuft ueber WRITE_EXERCISE) ist dabei.
        val session = onlySession.filterIsInstance<ExerciseSessionRecord>().single()
        assertTrue(session.exerciseRouteResult !is ExerciseRouteResult.Data)
        assertEquals(setOf("ExerciseSessionRecord", "CyclingPedalingCadenceRecord"), onlySession.map { it.javaClass.simpleName }.toSet())
    }

    @Test
    fun shortRideWithoutSensors() {
        val two = listOf(TrackPoint(t0, 50.0, 7.0, null, null), TrackPoint(t0 + 1000, 50.0001, 7.0, null, null))
        val s = RideSummaries.fromTrack("kurz", two, BodyProfile(), HrZones(180))
        val records = HealthConnect.buildRecords(s, two, { true }, device)
        assertTrue(records.any { it is ExerciseSessionRecord })
    }
}
