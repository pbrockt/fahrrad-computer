package com.pbrockt.fahrradcomputer.routes

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Hoehenprofil einer Route: alle [STEP_M] Meter eine Hoehe, leicht geglaettet. Daraus
 * Steigung, Gesamt- und Rest-Hoehenmeter und die Farben fuer das Profil
 * (je steiler, desto roeter; bergab blau).
 *
 * Die Hoehen kommen aus der GPX-Datei oder - fehlen sie dort - einmalig aus einem
 * Hoehenmodell ([ElevationApi]).
 */
class RouteProfile(
    /** Abstand vom Start je Stuetzpunkt, aufsteigend, Schritt [STEP_M]. */
    val distanceM: DoubleArray,
    val elevationM: DoubleArray,
) {
    init {
        require(distanceM.size == elevationM.size && distanceM.size >= 2)
    }

    val lengthM: Double get() = distanceM.last()
    val minM: Double = elevationM.min()
    val maxM: Double = elevationM.max()

    /** Aufstieg von Start bis zu jedem Stuetzpunkt (mit kleiner Schwelle gegen Rauschen). */
    private val ascentTo: DoubleArray = DoubleArray(elevationM.size).also { acc ->
        var gain = 0.0
        var ref = elevationM[0]
        for (i in 1 until elevationM.size) {
            val e = elevationM[i]
            if (e > ref + ASCENT_THRESHOLD_M) { gain += e - ref; ref = e }
            else if (e < ref) ref = e
            acc[i] = gain
        }
    }

    val totalAscentM: Double get() = ascentTo.last()

    /** Die Anstiege der Route (wie ClimbPro bei Garmin), einmal berechnet. */
    val climbs: List<Climb> by lazy { findClimbs() }

    /** Anstieg, auf dem man bei [d] gerade ist, sonst null. */
    fun climbAt(d: Double): Climb? = climbs.firstOrNull { d >= it.startM && d <= it.endM }

    /** Naechster Anstieg nach [d]. */
    fun nextClimb(d: Double): Climb? = climbs.firstOrNull { it.startM > d }

    /**
     * Sucht Anstiege: vom tiefsten Punkt seit dem letzten Anstieg bis zum Gipfel. Ein Anstieg
     * endet, wenn es danach deutlich bergab geht (mind. 10 m oder ein Fuenftel des Gewinns).
     * Zaehlt nur ab [MIN_CLIMB_GAIN_M] Hoehe, [MIN_CLIMB_LENGTH_M] Laenge und
     * [MIN_CLIMB_GRADE] Prozent Durchschnitt - kleine Wellen sind keine Anstiege.
     */
    private fun findClimbs(): List<Climb> {
        val e = elevationM
        val out = mutableListOf<Climb>()
        var lo = 0
        var peak = -1
        fun finish() {
            if (peak > lo) {
                val gain = e[peak] - e[lo]
                val length = distanceM[peak] - distanceM[lo]
                if (gain >= MIN_CLIMB_GAIN_M && length >= MIN_CLIMB_LENGTH_M && gain / length * 100 >= MIN_CLIMB_GRADE) {
                    var maxGrade = 0.0
                    var d = distanceM[lo]
                    while (d <= distanceM[peak]) { maxGrade = max(maxGrade, gradeAt(d)); d += STEP_M }
                    out += Climb(out.size + 1, distanceM[lo], distanceM[peak], gain, gain / length * 100, maxGrade)
                }
            }
        }
        for (i in 1 until e.size) {
            if (peak < 0) {
                // <=: bei flachem Stueck den letzten tiefsten Punkt nehmen - dort beginnt der Anstieg.
                if (e[i] <= e[lo]) lo = i
                else if (e[i] - e[lo] >= 10) peak = i
            } else {
                if (e[i] >= e[peak]) peak = i
                else if (e[peak] - e[i] >= max(10.0, (e[peak] - e[lo]) * 0.2)) {
                    finish()
                    lo = i
                    peak = -1
                }
            }
        }
        if (peak >= 0) finish()
        return out
    }

    fun ascentRemainingM(doneM: Double): Double = (totalAscentM - ascentAt(doneM)).coerceAtLeast(0.0)

    private fun ascentAt(d: Double): Double = interpolate(ascentTo, d)

    fun elevationAt(d: Double): Double = interpolate(elevationM, d)

    /** Steigung in Prozent um [d] herum (ueber [GRADE_SPAN_M]). */
    fun gradeAt(d: Double): Double {
        val a = (d - GRADE_SPAN_M / 2).coerceIn(0.0, lengthM)
        val b = (d + GRADE_SPAN_M / 2).coerceIn(0.0, lengthM)
        if (b - a < 1) return 0.0
        return (elevationAt(b) - elevationAt(a)) / (b - a) * 100
    }

    /** Steigung jedes Abschnitts i..i+1 in Prozent (ueber [GRADE_SPAN_M] gemittelt) - fuer die Farben. */
    fun segmentGrades(): DoubleArray = DoubleArray(distanceM.size - 1) { i ->
        gradeAt((distanceM[i] + distanceM[i + 1]) / 2)
    }

    private fun interpolate(values: DoubleArray, d: Double): Double {
        if (d <= 0) return values.first()
        if (d >= lengthM) return values.last()
        // Gleichmaessiger Schritt - der Index laesst sich direkt ausrechnen.
        val i = min((d / STEP_M).toInt(), distanceM.size - 2)
        val t = (d - distanceM[i]) / (distanceM[i + 1] - distanceM[i]).coerceAtLeast(1e-9)
        return values[i] + (values[i + 1] - values[i]) * t.coerceIn(0.0, 1.0)
    }

    /** Ein Anstieg auf der Route. [number] zaehlt ab 1. */
    data class Climb(
        val number: Int,
        val startM: Double,
        val endM: Double,
        val gainM: Double,
        val avgGrade: Double,
        val maxGrade: Double,
    ) {
        val lengthM: Double get() = endM - startM
    }

    companion object {
        const val STEP_M = 50.0
        const val MIN_CLIMB_GAIN_M = 25.0
        const val MIN_CLIMB_LENGTH_M = 400.0
        const val MIN_CLIMB_GRADE = 3.0
        private const val GRADE_SPAN_M = 200.0
        private const val ASCENT_THRESHOLD_M = 3.0

        /**
         * Aus Punkten mit Abstand ([cumulative]) und Hoehe; Punkte ohne Hoehe werden
         * ueberbrueckt. null, wenn weniger als 80 % der Punkte eine Hoehe haben.
         */
        fun fromPoints(cumulative: DoubleArray, elevations: List<Double?>): RouteProfile? {
            val known = elevations.indices.filter { elevations[it] != null }
            if (known.size < 2 || known.size < elevations.size * 0.8) return null
            val d = known.map { cumulative[it] }.toDoubleArray()
            val e = known.map { elevations[it]!! }.toDoubleArray()
            return resample(d, e)
        }

        /**
         * Gleichmaessig alle [STEP_M] Meter abtasten. Bewusst ohne Glaetten - das wuerde
         * Gipfel abschneiden und Hoehenmeter verschlucken; die Steigung mittelt ohnehin.
         */
        fun resample(d: DoubleArray, e: DoubleArray): RouteProfile? {
            if (d.size < 2 || d.last() <= 0) return null
            val n = (d.last() / STEP_M).toInt() + 1
            val dist = DoubleArray(n + 1) { min(it * STEP_M, d.last()) }
            val raw = DoubleArray(dist.size)
            var j = 0
            for (i in dist.indices) {
                val x = dist[i]
                while (j < d.size - 2 && d[j + 1] < x) j++
                val span = d[j + 1] - d[j]
                val t = if (span <= 0) 0.0 else ((x - d[j]) / span).coerceIn(0.0, 1.0)
                raw[i] = e[j] + (e[j + 1] - e[j]) * t
            }
            val smooth = raw
            // Doppelter Endpunkt (wenn die Laenge genau aufgeht) wuerde Steigung 0/0 geben.
            return if (dist.size >= 2 && abs(dist[dist.size - 1] - dist[dist.size - 2]) < 1e-6) {
                RouteProfile(dist.copyOf(dist.size - 1), smooth.copyOf(smooth.size - 1)).takeIf { dist.size > 2 }
            } else RouteProfile(dist, smooth)
        }

        /**
         * Farbstufe je Steigung, Index in [GRADE_COLORS]: steil bergab, bergab, flach,
         * leicht, mittel, steil, sehr steil, extrem.
         */
        fun gradeLevel(grade: Double): Int = when {
            grade < -6 -> 0
            grade < -2 -> 1
            grade < 2 -> 2
            grade < 4 -> 3
            grade < 6 -> 4
            grade < 9 -> 5
            grade < 12 -> 6
            else -> 7
        }

        /** ARGB je Stufe aus [gradeLevel]. */
        val GRADE_COLORS = longArrayOf(
            0xFF0D4C92, 0xFF56B6E8, 0xFF43A047, 0xFFF4D03F, 0xFFF39C12, 0xFFE53935, 0xFF8E0E12, 0xFF6A1B9A,
        )
        val GRADE_LABELS = listOf("< -6 %", "-6–-2 %", "±2 %", "2–4 %", "4–6 %", "6–9 %", "9–12 %", "> 12 %")
    }
}
