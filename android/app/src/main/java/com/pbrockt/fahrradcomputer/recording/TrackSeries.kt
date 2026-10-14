package com.pbrockt.fahrradcomputer.recording

/**
 * Verlaeufe einer Fahrt fuers Diagramm, auf hoechstens [maxPoints] Werte verdichtet.
 * Jeder Wert ist der Mittelwert eines Zeitabschnitts; fehlen in einem Abschnitt Daten
 * (z. B. kein Pulsgurt), steht dort null - das Diagramm zeichnet dann eine Luecke.
 */
data class TrackSeries(
    /** Minuten seit Start, je Abschnitt. */
    val minutes: List<Float>,
    val speedKmh: List<Float?>,
    val heartRate: List<Float?>,
    val cadence: List<Float?>,
    val power: List<Float?>,
    val altitude: List<Float?>,
) {
    val hasHeartRate get() = heartRate.any { it != null }
    val hasCadence get() = cadence.any { it != null }
    val hasPower get() = power.any { it != null }
    val hasAltitude get() = altitude.any { it != null }

    companion object {

        fun from(points: List<TrackPoint>, maxPoints: Int = 300): TrackSeries {
            if (points.size < 2) return TrackSeries(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

            // Geschwindigkeit je Punkt aus Abstand und Zeit zum Vorgaenger.
            val speeds = FloatArray(points.size)
            for (i in 1 until points.size) {
                val a = points[i - 1]
                val b = points[i]
                val dt = (b.timeMs - a.timeMs) / 1000.0
                if (dt <= 0 || dt * 1000 > RideTracker.MAX_GAP_MS) continue
                val kmh = RideTracker.distanceMeters(a.lat, a.lon, b.lat, b.lon) / dt * 3.6
                speeds[i] = if (kmh <= RideTracker.MAX_PLAUSIBLE_KMH) kmh.toFloat() else speeds[i - 1]
            }

            val start = points.first().timeMs
            val bucket = maxOf(1, (points.size + maxPoints - 1) / maxPoints)
            val chunks = points.indices.chunked(bucket)

            fun avg(idx: List<Int>, value: (Int) -> Number?): Float? {
                val v = idx.mapNotNull { value(it)?.toFloat() }
                return if (v.isEmpty()) null else v.average().toFloat()
            }

            return TrackSeries(
                minutes = chunks.map { c -> (points[c.first()].timeMs - start) / 60_000f },
                speedKmh = chunks.map { c -> avg(c) { speeds[it] } },
                heartRate = chunks.map { c -> avg(c) { points[it].heartRate } },
                cadence = chunks.map { c -> avg(c) { points[it].cadence } },
                power = chunks.map { c -> avg(c) { points[it].power } },
                altitude = chunks.map { c -> avg(c) { points[it].altitude } },
            )
        }
    }
}
