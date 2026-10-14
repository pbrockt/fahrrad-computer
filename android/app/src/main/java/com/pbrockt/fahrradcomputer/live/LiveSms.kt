package com.pbrockt.fahrradcomputer.live

import com.pbrockt.fahrradcomputer.recording.RideStats
import com.pbrockt.fahrradcomputer.ui.formatDuration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Die Texte fuer den Live-Standort per SMS.
 *
 * Bewusst kurz und ohne Emojis: Ein einziges Emoji schaltet die SMS auf Unicode um, dann
 * passen nur noch 70 statt 160 Zeichen hinein und es werden schnell zwei SMS daraus.
 * Umlaute sind dagegen im normalen SMS-Zeichensatz enthalten.
 *
 * Der Link geht auf Google Maps, weil er kurz ist und sich beim Empfaenger direkt in der
 * Karten-App oeffnet.
 */
object LiveSms {

    private val TIME = DateTimeFormatter.ofPattern("HH:mm")

    /** Nummern aus einem Freitext: getrennt durch Komma, Semikolon, Leerzeichen oder Zeilen. */
    fun parseNumbers(text: String): List<String> =
        text.split(',', ';', '\n')
            .map { part ->
                val digits = part.filter(Char::isDigit)
                if (part.trim().startsWith("+")) "+$digits" else digits
            }
            .filter { n -> n.count { it.isDigit() } >= 5 }
            .distinct()

    fun mapsLink(lat: Double, lon: Double): String =
        String.format(Locale.ROOT, "https://maps.google.com/?q=%.5f,%.5f", lat, lon)

    private fun who(name: String) = name.trim().ifEmpty { "Radfahrt" }

    private fun time(ms: Long, zone: ZoneId) = Instant.ofEpochMilli(ms).atZone(zone).format(TIME)

    private fun km(m: Double) = String.format(Locale.GERMANY, "%.1f km", m / 1000)

    fun start(name: String, timeMs: Long, lat: Double?, lon: Double?, intervalMin: Int, zone: ZoneId = ZoneId.systemDefault()): String =
        "${who(name)}: um ${time(timeMs, zone)} losgefahren. Standort alle $intervalMin Min." +
            (if (lat != null && lon != null) " ${mapsLink(lat, lon)}" else "")

    fun position(
        name: String,
        timeMs: Long,
        stats: RideStats,
        lat: Double,
        lon: Double,
        batteryPercent: Int?,
        remainingM: Double?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = buildString {
        append("${who(name)} ${time(timeMs, zone)}: ${km(stats.distanceM)}")
        append(String.format(Locale.GERMANY, ", %.0f km/h", stats.speedKmh))
        remainingM?.let { append(", noch ${km(it)}") }
        batteryPercent?.let { append(", Akku $it %") }
        append(". ")
        append(mapsLink(lat, lon))
    }

    fun stop(name: String, timeMs: Long, stats: RideStats, lat: Double?, lon: Double?, zone: ZoneId = ZoneId.systemDefault()): String =
        "${who(name)}: um ${time(timeMs, zone)} angekommen, ${km(stats.distanceM)} in ${formatDuration(stats.movingTimeMs)}." +
            (if (lat != null && lon != null) " ${mapsLink(lat, lon)}" else "")
}
