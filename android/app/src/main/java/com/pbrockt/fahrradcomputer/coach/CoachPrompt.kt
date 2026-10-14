package com.pbrockt.fahrradcomputer.coach

import com.pbrockt.fahrradcomputer.data.HrZones
import com.pbrockt.fahrradcomputer.recording.BodyProfile
import com.pbrockt.fahrradcomputer.recording.RideSummary
import com.pbrockt.fahrradcomputer.recording.Sex
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Was der Fahrer fuer den Plan vorgibt. */
data class CoachRequest(
    val goal: String,
    val days: Set<DayOfWeek>,
    val notes: String,
    val body: BodyProfile,
    val maxHr: Int,
    val startDate: LocalDate,
)

/**
 * Baut die Texte fuer die Anfrage an Claude. Getrennt von der Netzwerk-Logik, damit sich
 * pruefen und anzeigen laesst, was genau gesendet wird ("Was wird gesendet?").
 *
 * Bewusst nicht enthalten: Name, Standorte, Strecken, Geraete - nur Trainingswerte.
 */
object CoachPrompt {

    /** So viele Wochen Vorgeschichte gehen mit - genug fuer einen Trend, nicht mehr. */
    const val HISTORY_WEEKS = 6L

    val system: String = """
        Du bist ein erfahrener Radsport-Trainer. Du erstellst Wochenpläne für einen Freizeitfahrer
        auf einem E-Bike mit Mittelmotor (Bosch). Die Unterstützungsstufe ist ein Trainingswerkzeug:
        viel Unterstützung hält den Puls in den unteren Zonen, wenig oder keine Unterstützung
        macht Intervalle und Tempo möglich. Empfiehl in jeder Einheit eine passende Stufe.

        Regeln:
        - Plane genau 7 Tage ab dem Startdatum, ein Eintrag pro Tag; Tage ohne Training sind REST.
        - Trainiere nur an den angegebenen Wochentagen.
        - Halte dich an die angegebenen Pulszonen des Fahrers.
        - Steigere den Gesamtumfang höchstens um etwa 10 % gegenüber dem Schnitt der letzten Wochen.
          Nach Pausen oder bei wenig Vorgeschichte vorsichtig einsteigen.
        - Höchstens zwei intensive Einheiten (TEMPO, INTERVALS) pro Woche, nie an zwei Tagen hintereinander.
        - Keine medizinischen Diagnosen. Wenn der Fahrer Beschwerden erwähnt, plane schonend und
          rate zur ärztlichen Abklärung.
        - Schreibe auf Deutsch, in der du-Form, knapp und konkret.
    """.trimIndent()

    fun user(request: CoachRequest, rides: List<RideSummary>, previous: StoredPlan?, zone: ZoneId = ZoneId.systemDefault()): String =
        buildString {
            val z = HrZones(request.maxHr).thresholds
            appendLine("Startdatum: ${request.startDate} (${dayName(request.startDate.dayOfWeek)})")
            appendLine("Trainingstage: ${request.days.sorted().joinToString { dayName(it) }}")
            appendLine("Ziel: ${request.goal.ifBlank { "allgemeine Ausdauer verbessern" }}")
            if (request.notes.isNotBlank()) appendLine("Hinweise des Fahrers: ${request.notes}")
            appendLine()
            appendLine("Fahrer:")
            request.body.age?.let { appendLine("- Alter: $it") }
            request.body.sex?.let { appendLine("- Geschlecht: ${if (it == Sex.MALE) "männlich" else "weiblich"}") }
            if (request.body.weightKg > 0) appendLine("- Gewicht: ${"%.0f".format(Locale.ROOT, request.body.weightKg)} kg")
            appendLine("- Maximale Herzfrequenz: ${request.maxHr}")
            appendLine("- Pulszonen: Z1 < ${z[0]}, Z2 ${z[0]}-${z[1] - 1}, Z3 ${z[1]}-${z[2] - 1}, Z4 ${z[2]}-${z[3] - 1}, Z5 ab ${z[3]}")
            appendLine()

            val since = request.startDate.minusWeeks(HISTORY_WEEKS)
            val recent = rides
                .filter { !date(it, zone).isBefore(since) && date(it, zone).isBefore(request.startDate) }
                .sortedBy { it.startMs }
            appendLine("Fahrten der letzten $HISTORY_WEEKS Wochen (${recent.size}):")
            if (recent.isEmpty()) appendLine("- keine aufgezeichnet")
            for (r in recent) appendLine("- " + rideLine(r, zone))
            appendLine()

            appendLine("Wochensummen (Montag bis Sonntag):")
            val firstMonday = since.with(DayOfWeek.MONDAY)
            var week = firstMonday
            while (week.isBefore(request.startDate)) {
                val inWeek = recent.filter { val d = date(it, zone); !d.isBefore(week) && d.isBefore(week.plusWeeks(1)) }
                val min = inWeek.sumOf { it.movingTimeMs } / 60_000
                val km = inWeek.sumOf { it.distanceM } / 1000
                appendLine("- ab $week: ${inWeek.size} Fahrten, $min min, ${"%.0f".format(Locale.ROOT, km)} km")
                week = week.plusWeeks(1)
            }

            if (previous != null) {
                appendLine()
                appendLine("Vorheriger Plan (ab ${previous.startDate}) und was davon umgesetzt wurde:")
                val today = request.startDate
                for (s in previous.plan.sessions) {
                    val status = when (PlanMatching.status(s, rides, today, zone)) {
                        SessionStatus.DONE -> "erledigt"
                        SessionStatus.MISSED -> "ausgelassen"
                        SessionStatus.OPEN -> "offen"
                        SessionStatus.REST -> "Ruhetag"
                    }
                    if (s.kind != SessionKind.REST) appendLine("- ${s.date} ${s.kind.name} ${s.durationMin} min Z${s.targetZone}: $status")
                }
            }
            appendLine()
            append("Erstelle den Plan für die 7 Tage ab ${request.startDate}.")
        }

    private fun date(r: RideSummary, zone: ZoneId): LocalDate = Instant.ofEpochMilli(r.startMs).atZone(zone).toLocalDate()

    private fun rideLine(r: RideSummary, zone: ZoneId): String = buildString {
        append(date(r, zone))
        append(": ${r.movingTimeMs / 60_000} min, ${"%.1f".format(Locale.ROOT, r.distanceM / 1000)} km")
        append(", Ø ${"%.0f".format(Locale.ROOT, r.avgSpeedKmh)} km/h, ${"%.0f".format(Locale.ROOT, r.elevationGainM)} Hm")
        r.avgHr?.let { append(", Ø Puls $it") }
        if (r.zoneSeconds.size == 5 && r.zoneSeconds.sum() > 0) {
            val total = r.zoneSeconds.sum().toDouble()
            append(", Zonen " + r.zoneSeconds.mapIndexed { i, s -> "Z${i + 1} ${(s / total * 100).toInt()}%" }.joinToString(" "))
        }
        r.avgPower?.let { append(", Ø $it W") }
        r.avgCadence?.let { append(", Ø $it U/min") }
    }

    private fun dayName(d: DayOfWeek) = d.getDisplayName(TextStyle.FULL, Locale.GERMANY)

    /** Fuer die Anzeige "Plan gilt noch x Tage". */
    fun daysLeft(plan: StoredPlan, today: LocalDate): Long =
        ChronoUnit.DAYS.between(today, LocalDate.parse(plan.startDate).plusDays(7)).coerceAtLeast(0)
}
