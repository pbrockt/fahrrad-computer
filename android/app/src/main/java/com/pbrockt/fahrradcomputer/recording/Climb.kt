package com.pbrockt.fahrradcomputer.recording

/** Live-Werte fuers Klettern: Hoehenmeter rauf und runter, Steigung und VAM. */
data class Climb(
    val ascentM: Double = 0.0,
    val gradePct: Double? = null,
    val descentM: Double = 0.0,
    /** Hoehenmeter pro Stunde ueber die letzten [Climbs.VAM_WINDOW_MS] - nur bergauf, sonst 0. */
    val vamMh: Double? = null,
)

object Climbs {

    /** Ueber so viel Strecke wird die Steigung gemessen - kuerzer zappelt das GPS zu sehr. */
    const val GRADE_SPAN_M = 200.0

    /**
     * Steigung aus den letzten [GRADE_SPAN_M] Metern der eigenen Strecke: Ausgleichsgerade
     * durch die rohen GPS-Hoehen ueber der Strecke. Anders als ein gleitender Mittelwert
     * haengt sie nicht hinterher, und einzelne Ausreisser fallen kaum ins Gewicht.
     * null, wenn die Strecke noch zu kurz ist.
     */
    fun liveGrade(points: List<TrackPoint>): Double? {
        if (points.size < 5) return null
        val xs = ArrayList<Double>()
        val ys = ArrayList<Double>()
        var dist = 0.0
        points.last().altitude?.let { xs += 0.0; ys += it }
        for (i in points.size - 1 downTo 1) {
            val a = points[i - 1]
            val b = points[i]
            dist += RideTracker.distanceMeters(a.lat, a.lon, b.lat, b.lon)
            a.altitude?.let { xs += -dist; ys += it }
            if (dist >= GRADE_SPAN_M) break
        }
        if (dist < GRADE_SPAN_M || xs.size < 5) return null
        val mx = xs.average()
        val my = ys.average()
        var num = 0.0
        var den = 0.0
        for (k in xs.indices) {
            num += (xs[k] - mx) * (ys[k] - my)
            den += (xs[k] - mx) * (xs[k] - mx)
        }
        if (den <= 0) return null
        return (num / den * 100).coerceIn(-30.0, 30.0)
    }

    fun of(points: List<TrackPoint>): Climb = Climb(
        ascentM = RideSummaries.elevationGain(points),
        gradePct = liveGrade(points.takeLast(GRADE_POINTS)),
        // Abstieg = Aufstieg der rueckwaerts gefahrenen Strecke - dieselbe Rechnung mit Schwelle.
        descentM = RideSummaries.elevationGain(points.asReversed()),
        vamMh = vam(points),
    )

    const val VAM_WINDOW_MS = 5 * 60_000L

    /** Vertikale Geschwindigkeit (m/h): Ausgleichsgerade der GPS-Hoehe ueber die letzten 5 Minuten. */
    fun vam(points: List<TrackPoint>): Double? {
        if (points.size < 10) return null
        val end = points.last().timeMs
        val recent = points.takeLastWhile { end - it.timeMs <= VAM_WINDOW_MS }.filter { it.altitude != null }
        if (recent.size < 10) return null
        val xs = recent.map { (it.timeMs - end) / 3_600_000.0 }
        val ys = recent.map { it.altitude!! }
        val mx = xs.average()
        val my = ys.average()
        var num = 0.0
        var den = 0.0
        for (k in xs.indices) {
            num += (xs[k] - mx) * (ys[k] - my)
            den += (xs[k] - mx) * (xs[k] - mx)
        }
        if (den <= 0) return null
        return (num / den).coerceAtLeast(0.0)
    }

    private const val GRADE_POINTS = 200
}
