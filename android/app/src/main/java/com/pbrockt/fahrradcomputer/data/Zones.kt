package com.pbrockt.fahrradcomputer.data

import kotlin.math.roundToInt

/** Fuenf Zonen aus vier Untergrenzen (Z2 bis Z5) - fuer Leistung, Trittfrequenz und Tempo. */
data class Zones(val thresholds: List<Int>) {
    init {
        require(thresholds.size == 4) { "Vier Grenzen noetig" }
    }

    /** Zone 1 bis 5. */
    fun zoneOf(value: Number): Int = 1 + thresholds.count { value.toDouble() >= it }

    /** Fuer die Zonenleiste: wo der Wert innerhalb von Z1..Z5 liegt, 0..1. */
    fun position(value: Number): Float {
        val v = value.toDouble()
        val z = zoneOf(v) - 1
        val lo = if (z == 0) thresholds[0] * 0.5 else thresholds[z - 1].toDouble()
        val hi = if (z == 4) thresholds[3] * 1.25 else thresholds[z].toDouble()
        val inZone = ((v - lo) / (hi - lo).coerceAtLeast(1.0)).coerceIn(0.0, 1.0)
        return ((z + inZone) / 5).toFloat()
    }

    fun format(unit: String): String {
        val t = thresholds
        return "Z1 < ${t[0]} · Z2 ${t[0]}–${t[1] - 1} · Z3 ${t[1]}–${t[2] - 1} · Z4 ${t[2]}–${t[3] - 1} · Z5 ab ${t[3]} $unit"
    }

    companion object {
        /** Leistungszonen nach Prozent der FTP (angelehnt an Coggan, auf 5 Zonen zusammengefasst). */
        fun power(ftp: Double): Zones = Zones(listOf(0.55, 0.75, 0.90, 1.05).map { (ftp * it).roundToInt() })

        val DEFAULT_CADENCE = Zones(listOf(60, 75, 90, 105))
        /** Fuers E-Bike: ab 25 km/h hoert der Motor auf zu helfen - das ist Z4. */
        val DEFAULT_SPEED = Zones(listOf(10, 18, 25, 32))

        /** "60, 75, 90, 105" -> Zonen; null, wenn es nicht vier aufsteigende Zahlen sind. */
        fun parse(text: String): Zones? {
            val n = text.split(',', ';', ' ', '/').mapNotNull { it.trim().toIntOrNull() }
            if (n.size != 4 || n.zipWithNext().any { (a, b) -> b <= a } || n[0] <= 0) return null
            return Zones(n)
        }
    }
}
