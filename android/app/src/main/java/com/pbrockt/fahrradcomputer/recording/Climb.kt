package com.pbrockt.fahrradcomputer.recording

/** Live-Werte fuers Klettern: bisherige Hoehenmeter und aktuelle Steigung. */
data class Climb(val ascentM: Double = 0.0, val gradePct: Double? = null)

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

    fun of(points: List<TrackPoint>): Climb =
        Climb(RideSummaries.elevationGain(points), liveGrade(points.takeLast(GRADE_POINTS)))

    private const val GRADE_POINTS = 200
}
