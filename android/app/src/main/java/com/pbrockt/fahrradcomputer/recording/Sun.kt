package com.pbrockt.fahrradcomputer.recording

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

/**
 * Sonnenauf- und -untergang ohne Internet (Sonnengleichung nach NOAA, auf wenige Minuten
 * genau). null im Polarsommer bzw. -winter.
 */
object Sun {

    data class Times(val sunrise: Instant, val sunset: Instant)

    fun times(date: LocalDate, lat: Double, lon: Double): Times? {
        val jd = date.toEpochDay() + 2440587.5 + 0.5 // Julianischer Tag zur Mittagszeit UTC
        val n = jd - 2451545.0 + 0.0008
        val jStar = n - lon / 360.0
        val m = Math.toRadians((357.5291 + 0.98560028 * jStar) % 360)
        val c = 1.9148 * sin(m) + 0.02 * sin(2 * m) + 0.0003 * sin(3 * m)
        val lambda = Math.toRadians((Math.toDegrees(m) + c + 180 + 102.9372) % 360)
        val jTransit = 2451545.0 + jStar + 0.0053 * sin(m) - 0.0069 * sin(2 * lambda)
        val decl = asin(sin(lambda) * sin(Math.toRadians(23.4397)))
        val phi = Math.toRadians(lat)
        // -0,833 Grad: Lichtbrechung und Sonnenradius
        val cosW = (sin(Math.toRadians(-0.833)) - sin(phi) * sin(decl)) / (cos(phi) * cos(decl))
        if (cosW < -1 || cosW > 1) return null
        val w = Math.toDegrees(acos(cosW))
        fun instant(j: Double) = Instant.ofEpochMilli(((j - 2440587.5) * 86_400_000).toLong())
        return Times(instant(jTransit - w / 360), instant(jTransit + w / 360))
    }

    fun today(nowMs: Long, lat: Double, lon: Double): Times? =
        times(Instant.ofEpochMilli(nowMs).atOffset(ZoneOffset.UTC).toLocalDate(), lat, lon)
}
