package com.pbrockt.fahrradcomputer

import com.anthropic.core.jsonMapper
import com.pbrockt.fahrradcomputer.coach.Coach
import com.pbrockt.fahrradcomputer.coach.CoachPrompt
import com.pbrockt.fahrradcomputer.coach.CoachRequest
import com.pbrockt.fahrradcomputer.coach.PlanMatching
import com.pbrockt.fahrradcomputer.coach.PlannedSession
import com.pbrockt.fahrradcomputer.coach.SessionKind
import com.pbrockt.fahrradcomputer.coach.SessionStatus
import com.pbrockt.fahrradcomputer.coach.StoredPlan
import com.pbrockt.fahrradcomputer.coach.TrainingPlan
import com.pbrockt.fahrradcomputer.recording.BodyProfile
import com.pbrockt.fahrradcomputer.recording.RideSummary
import com.pbrockt.fahrradcomputer.recording.Sex
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset

class CoachTest {

    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 14)

    private fun ride(date: LocalDate, minutes: Long) = RideSummary(
        id = "r$date", startMs = date.atTime(9, 0).toInstant(zone).toEpochMilli(), endMs = 0,
        distanceM = minutes * 300.0, movingTimeMs = minutes * 60_000, avgSpeedKmh = 18.0, maxSpeedKmh = 30.0,
        avgHr = 128, zoneSeconds = listOf(60L, 1800L, 600L, 0L, 0L),
    )

    @Test
    fun requestBuildsWithLocalSchemaValidation() {
        // Wirft, wenn das aus den Kotlin-Klassen abgeleitete Schema nicht zu den
        // Einschraenkungen der strukturierten Ausgabe passt.
        Coach.buildRequest("system", "user")
    }

    @Test
    fun responseJsonDeserializesWithSdkMapper() {
        val json = """{"assessment":"Gut.","focus":"Grundlage","tips":["Trinken"],
            "sessions":[{"date":"2026-10-14","kind":"ENDURANCE","title":"Grundlage","durationMin":60,
            "targetZone":2,"description":"Stufe Tour."}]}"""
        val plan = jsonMapper().readValue(json, TrainingPlan::class.java)
        assertEquals(SessionKind.ENDURANCE, plan.sessions.single().kind)
        assertEquals(60, plan.sessions.single().durationMin)
        // und zurueck ueber kotlinx (Speicherformat der App)
        val stored = StoredPlan(plan, 1, "2026-10-14")
        val k = Json { encodeDefaults = true }
        assertEquals(stored, k.decodeFromString<StoredPlan>(k.encodeToString(stored)))
    }

    @Test
    fun promptContainsTrainingDataButNoLocation() {
        val req = CoachRequest(
            goal = "100 km am Stück", days = setOf(DayOfWeek.TUESDAY, DayOfWeek.SATURDAY), notes = "Knie",
            body = BodyProfile(82.0, 45, Sex.MALE), maxHr = 180, startDate = today,
        )
        val text = CoachPrompt.user(req, listOf(ride(today.minusDays(3), 50), ride(today.minusDays(60), 99)), null, zone)
        assertTrue(text, text.contains("Startdatum: 2026-10-14 (Mittwoch)"))
        assertTrue(text.contains("Trainingstage: Dienstag, Samstag"))
        assertTrue(text.contains("Ziel: 100 km am Stück"))
        assertTrue(text.contains("Hinweise des Fahrers: Knie"))
        assertTrue(text.contains("Pulszonen: Z1 < 108, Z2 108-125"))
        assertTrue(text.contains("2026-10-11: 50 min"))
        assertTrue(text, text.contains("Zonen Z1 2% Z2 73% Z3 24%"))
        // aelter als 6 Wochen: nicht dabei
        assertFalse(text.contains("99 min"))
        // Keine Koordinaten: Zahlen mit 4+ Nachkommastellen kommen nur in GPS-Positionen vor.
        assertFalse(text, Regex("""\d+\.\d{4,}""").containsMatchIn(text))
    }

    @Test
    fun matching() {
        fun s(day: LocalDate, kind: SessionKind = SessionKind.ENDURANCE, min: Int = 60) =
            PlannedSession(day.toString(), kind, "x", min, 2, "")
        val rides = listOf(ride(today.minusDays(1), 35))
        assertEquals(SessionStatus.DONE, PlanMatching.status(s(today.minusDays(1)), rides, today, zone))
        assertEquals(SessionStatus.MISSED, PlanMatching.status(s(today.minusDays(2)), rides, today, zone))
        assertEquals(SessionStatus.OPEN, PlanMatching.status(s(today), rides, today, zone))
        assertEquals(SessionStatus.REST, PlanMatching.status(s(today, SessionKind.REST, 0), rides, today, zone))
        // 25 von 60 min ist weniger als die Haelfte
        assertEquals(SessionStatus.MISSED, PlanMatching.status(s(today.minusDays(3)), listOf(ride(today.minusDays(3), 25)), today, zone))
    }
}
