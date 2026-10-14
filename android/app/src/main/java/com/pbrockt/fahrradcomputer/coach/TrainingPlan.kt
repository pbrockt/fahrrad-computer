package com.pbrockt.fahrradcomputer.coach

import com.fasterxml.jackson.annotation.JsonPropertyDescription
import com.pbrockt.fahrradcomputer.recording.RideSummary
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Art einer Einheit. Die Namen sind Teil des JSON-Schemas fuer Claude - nicht umbenennen. */
@Serializable
enum class SessionKind(val label: String) {
    REST("Ruhetag"),
    EASY("Locker"),
    ENDURANCE("Grundlage"),
    TEMPO("Tempo"),
    INTERVALS("Intervalle"),
    LONG("Lange Ausfahrt"),
}

/**
 * Der Wochenplan, wie Claude ihn liefert. Die Klassen dienen doppelt: Das Anthropic-SDK
 * leitet daraus das JSON-Schema fuer die Antwort ab (Beschreibungen ueber
 * [JsonPropertyDescription]), und die App speichert den Plan mit kotlinx.serialization.
 */
@Serializable
data class TrainingPlan(
    @field:JsonPropertyDescription("Kurze Einschätzung des aktuellen Trainingsstands auf Deutsch, höchstens 3 Sätze, du-Form.")
    val assessment: String,
    @field:JsonPropertyDescription("Schwerpunkt dieser Woche in einem Satz.")
    val focus: String,
    @field:JsonPropertyDescription("Genau ein Eintrag pro Tag, 7 Tage in Folge ab dem vorgegebenen Startdatum. Ruhetage als REST.")
    val sessions: List<PlannedSession>,
    @field:JsonPropertyDescription("2 bis 4 kurze Hinweise, z. B. Erholung, Trinken, Unterstützungsstufe.")
    val tips: List<String>,
)

@Serializable
data class PlannedSession(
    @field:JsonPropertyDescription("Datum im Format YYYY-MM-DD.")
    val date: String,
    val kind: SessionKind,
    @field:JsonPropertyDescription("Kurzer Titel, z. B. 'Lockere Grundlage'. Bei REST: 'Ruhetag'.")
    val title: String,
    @field:JsonPropertyDescription("Dauer in Minuten, bei REST 0.")
    val durationMin: Int,
    @field:JsonPropertyDescription("Ziel-Pulszone 1 bis 5 für den Hauptteil, bei REST 0.")
    val targetZone: Int,
    @field:JsonPropertyDescription("Ablauf in 1-3 Sätzen, inkl. Empfehlung zur E-Bike-Unterstützungsstufe.")
    val description: String,
) {
    val localDate: LocalDate? get() = runCatching { LocalDate.parse(date) }.getOrNull()
}

/** Ein gespeicherter Plan mit Zeitpunkt der Erstellung und den Kosten der Anfrage. */
@Serializable
data class StoredPlan(
    val plan: TrainingPlan,
    val createdAtMs: Long,
    val startDate: String,
    val costUsd: Double? = null,
)

enum class SessionStatus { DONE, MISSED, OPEN, REST }

/**
 * Ordnet Fahrten den geplanten Einheiten zu: Eine Einheit gilt als erledigt, wenn am selben
 * Tag mindestens die Haelfte der geplanten Zeit gefahren wurde.
 */
object PlanMatching {

    fun status(
        session: PlannedSession,
        rides: List<RideSummary>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): SessionStatus {
        if (session.kind == SessionKind.REST) return SessionStatus.REST
        val day = session.localDate ?: return SessionStatus.OPEN
        val minutes = rides
            .filter { Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate() == day }
            .sumOf { it.movingTimeMs } / 60_000
        return when {
            minutes * 2 >= session.durationMin && minutes > 0 -> SessionStatus.DONE
            day.isBefore(today) -> SessionStatus.MISSED
            else -> SessionStatus.OPEN
        }
    }

    fun today(plan: TrainingPlan?, today: LocalDate): PlannedSession? =
        plan?.sessions?.firstOrNull { it.localDate == today }
}
