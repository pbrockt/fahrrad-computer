package com.pbrockt.fahrradcomputer.share

import com.pbrockt.fahrradcomputer.recording.RideTracker
import com.pbrockt.fahrradcomputer.recording.TrackPoint
import kotlin.math.cos
import kotlin.math.min

/**
 * Schneidet Anfang und Ende einer Strecke ab, bevor sie geteilt wird - damit man auf Bild,
 * Video oder GPX nicht sieht, wo die Fahrt beginnt und endet (meist: zu Hause).
 */
object PrivacyTrim {

    /**
     * Entfernt die ersten [startM] und die letzten [endM] Meter (Luftlinie entlang der
     * Punkte). Bleibt danach zu wenig uebrig, kommt eine leere Liste zurueck.
     */
    fun trim(points: List<TrackPoint>, startM: Double, endM: Double): List<TrackPoint> {
        if (startM <= 0 && endM <= 0) return points
        if (points.size < 2) return emptyList()
        val cum = cumulative(points)
        val total = cum.last()
        val from = startM
        val to = total - endM
        if (to - from < MIN_REMAINING_M) return emptyList()
        return points.filterIndexed { i, _ -> cum[i] >= from && cum[i] <= to }
    }

    /** Unter dieser Laenge lohnt das Teilen nicht - es bliebe nur ein Strich. */
    const val MIN_REMAINING_M = 200.0

    fun cumulative(points: List<TrackPoint>): DoubleArray {
        val c = DoubleArray(points.size)
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            c[i] = c[i - 1] + RideTracker.distanceMeters(a.lat, a.lon, b.lat, b.lon)
        }
        return c
    }
}

/**
 * Die Strecke in Bildkoordinaten, dazu der Anteil der Strecke (0..1) bis zu jedem Punkt -
 * fuer die Animation im Video. Erzeugt ueber [fit] (eingepasst, ohne Karte) oder
 * [onMap] (exakt passend zu den OSM-Kacheln).
 */
class ProjectedRoute(val xs: FloatArray, val ys: FloatArray, points: List<TrackPoint>) {

    val fraction = FloatArray(points.size)

    init {
        if (points.isNotEmpty()) {
            val cum = PrivacyTrim.cumulative(points)
            val total = cum.last().takeIf { it > 0 } ?: 1.0
            for (i in points.indices) fraction[i] = (cum[i] / total).toFloat()
        }
    }

    companion object {
        /**
         * In ein Rechteck eingepasst, Seitenverhaeltnis erhalten (Laengengrade mit cos(Breite)
         * gestaucht), mittig. y waechst nach unten wie auf dem Bild.
         */
        fun fit(points: List<TrackPoint>, left: Float, top: Float, width: Float, height: Float): ProjectedRoute {
            val xs = FloatArray(points.size)
            val ys = FloatArray(points.size)
            if (points.isNotEmpty()) {
                val k = cos(Math.toRadians(points.first().lat))
                val mx = points.map { it.lon * k }
                val my = points.map { it.lat }
                val minX = mx.min(); val maxX = mx.max()
                val minY = my.min(); val maxY = my.max()
                val spanX = (maxX - minX).takeIf { it > 0 } ?: 1e-9
                val spanY = (maxY - minY).takeIf { it > 0 } ?: 1e-9
                val scale = min(width / spanX, height / spanY)
                val offX = left + (width - spanX * scale).toFloat() / 2
                val offY = top + (height - spanY * scale).toFloat() / 2
                for (i in points.indices) {
                    xs[i] = offX + ((mx[i] - minX) * scale).toFloat()
                    ys[i] = offY + ((maxY - my[i]) * scale).toFloat()
                }
            }
            return ProjectedRoute(xs, ys, points)
        }

        /** Auf den Kartenausschnitt [window], der mit [scale] vergroessert bei ([left], [top]) liegt. */
        fun onMap(points: List<TrackPoint>, window: MapWindow, scale: Float, left: Float, top: Float): ProjectedRoute {
            val xs = FloatArray(points.size) { left + ((OsmMath.worldX(points[it].lon, window.zoom) - window.originX) * scale).toFloat() }
            val ys = FloatArray(points.size) { top + ((OsmMath.worldY(points[it].lat, window.zoom) - window.originY) * scale).toFloat() }
            return ProjectedRoute(xs, ys, points)
        }
    }

    val size: Int get() = xs.size

    /**
     * Wo der Punkt bei [progress] (0..1) steht: Index des letzten erreichten Punkts und die
     * Position dazwischen - so faehrt der Punkt im Video gleichmaessig, auch wenn die
     * GPS-Punkte unterschiedlich dicht liegen.
     */
    fun positionAt(progress: Float): Triple<Int, Float, Float> {
        if (size == 0) return Triple(0, 0f, 0f)
        val p = progress.coerceIn(0f, 1f)
        var i = 0
        while (i + 1 < size && fraction[i + 1] <= p) i++
        if (i + 1 >= size) return Triple(size - 1, xs.last(), ys.last())
        val span = fraction[i + 1] - fraction[i]
        val t = if (span <= 0f) 0f else (p - fraction[i]) / span
        return Triple(i, xs[i] + (xs[i + 1] - xs[i]) * t, ys[i] + (ys[i + 1] - ys[i]) * t)
    }
}
