package com.pbrockt.fahrradcomputer.data

import kotlin.math.roundToInt

/**
 * Fuenf Pulszonen nach Prozent der maximalen Herzfrequenz:
 * Z1 < 60 %, Z2 60–70 %, Z3 70–80 %, Z4 80–90 %, Z5 ab 90 %.
 */
class HrZones(val maxHr: Int) {

    /** Untergrenzen von Z2 bis Z5 in Schlaegen pro Minute. */
    val thresholds: List<Int> = listOf(0.60, 0.70, 0.80, 0.90).map { (maxHr * it).roundToInt() }

    /** Zone 1 bis 5. */
    fun zoneOf(bpm: Int): Int = 1 + thresholds.count { bpm >= it }

    /** Dieselben Grenzen als allgemeine [Zones] - fuer die Zonenleiste. */
    val asZones: Zones get() = Zones(thresholds)

    companion object {
        const val DEFAULT_MAX_HR = 180
    }
}
