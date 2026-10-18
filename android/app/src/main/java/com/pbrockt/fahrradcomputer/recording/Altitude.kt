package com.pbrockt.fahrradcomputer.recording

/**
 * GPS-Hoehe ist ungenau: Sie springt im Sekundentakt um 5-20 m. Roh gezeichnet wirkt
 * jedes Profil zackig, und aufsummiert ergeben die Spruenge viel zu viele Hoehenmeter.
 *
 * Zwei Schritte, beide ueber Punkt-Fenster (bei 1 Punkt pro Sekunde = Sekunden):
 * 1. Median ueber [MEDIAN_WINDOW] Punkte - entfernt einzelne Ausreisser.
 * 2. Gleitender Mittelwert ueber [MEAN_WINDOW] Punkte - glaettet das Rauschen.
 * Punkte ohne Hoehe bleiben null.
 */
object Altitude {

    const val MEDIAN_WINDOW = 7
    const val MEAN_WINDOW = 15

    /** Mindest-Hoehenspanne fuer Profile - sonst sieht eine flache Fahrt wie ein Gebirge aus. */
    const val MIN_PROFILE_RANGE_M = 60.0

    fun smooth(altitudes: List<Double?>): List<Double?> {
        val median = window(altitudes, MEDIAN_WINDOW) { v -> v.sorted()[v.size / 2] }
        return window(median, MEAN_WINDOW) { v -> v.average() }
    }

    fun smoothPoints(points: List<TrackPoint>): List<Double?> = smooth(points.map { it.altitude })

    private fun window(values: List<Double?>, size: Int, f: (List<Double>) -> Double): List<Double?> {
        val half = size / 2
        return values.indices.map { i ->
            if (values[i] == null) return@map null
            val from = (i - half).coerceAtLeast(0)
            val to = (i + half).coerceAtMost(values.lastIndex)
            f((from..to).mapNotNull { values[it] })
        }
    }

    /**
     * Unter- und Obergrenze fuer die Darstellung: die echte Spanne, aber mindestens
     * [MIN_PROFILE_RANGE_M], mittig um die gefahrenen Hoehen gelegt.
     */
    fun displayRange(lo: Double, hi: Double): Pair<Double, Double> {
        val span = hi - lo
        if (span >= MIN_PROFILE_RANGE_M) return lo to hi
        val mid = (lo + hi) / 2
        return (mid - MIN_PROFILE_RANGE_M / 2) to (mid + MIN_PROFILE_RANGE_M / 2)
    }
}
