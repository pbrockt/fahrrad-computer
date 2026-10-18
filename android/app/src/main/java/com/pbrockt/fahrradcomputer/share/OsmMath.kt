package com.pbrockt.fahrradcomputer.share

import com.pbrockt.fahrradcomputer.recording.TrackPoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

/** Ein Kartenausschnitt: Zoomstufe und linke obere Ecke in Welt-Pixeln dieser Stufe. */
data class MapWindow(
    val zoom: Int,
    val originX: Double,
    val originY: Double,
    /** Groesse des Ausschnitts in Welt-Pixeln (Bildgroesse / Vergroesserung). */
    val width: Double,
    val height: Double,
) {
    /** Kacheln (x, y), die den Ausschnitt abdecken. */
    fun tiles(): List<Pair<Int, Int>> {
        val n = 1 shl zoom
        val x0 = floor(originX / OsmMath.TILE).toInt()
        val x1 = floor((originX + width) / OsmMath.TILE).toInt()
        val y0 = floor(originY / OsmMath.TILE).toInt().coerceAtLeast(0)
        val y1 = floor((originY + height) / OsmMath.TILE).toInt().coerceAtMost(n - 1)
        return (y0..y1).flatMap { y -> (x0..x1).map { x -> x to y } }
    }
}

/**
 * Rechnen im Kachelsystem von OpenStreetMap (Web-Mercator): Auf Zoomstufe z ist die Welt
 * 256 * 2^z Pixel breit; Kachel (x, y) deckt die Pixel x*256 .. x*256+255 ab.
 */
object OsmMath {

    const val TILE = 256
    const val MAX_ZOOM = 17
    const val MIN_ZOOM = 3

    private fun worldSize(zoom: Int) = TILE * 2.0.pow(zoom)

    fun worldX(lon: Double, zoom: Int): Double = (lon + 180.0) / 360.0 * worldSize(zoom)

    fun worldY(lat: Double, zoom: Int): Double {
        val r = Math.toRadians(lat)
        return (1 - ln(tan(r) + 1 / cos(r)) / PI) / 2 * worldSize(zoom)
    }

    /**
     * Wie [fit], aber die Strecke soll in den Bildbereich [left]..[right] x [top]..[bottom]
     * eines [width] x [height] grossen Kartenbildes passen - z. B. zwischen zwei Info-Kaesten.
     */
    fun fitIn(
        points: List<TrackPoint>, width: Double, height: Double,
        left: Double, top: Double, right: Double, bottom: Double, scale: Double,
    ): MapWindow {
        val inner = fit(points, right - left, bottom - top, scale, 0.0)
        val xs = points.map { worldX(it.lon, inner.zoom) }
        val ys = points.map { worldY(it.lat, inner.zoom) }
        val cx = (xs.min() + xs.max()) / 2
        val cy = (ys.min() + ys.max()) / 2
        // Mitte der Strecke auf die Mitte des Zielbereichs legen.
        val originX = cx - (left + right) / 2 / scale
        val originY = cy - (top + bottom) / 2 / scale
        return MapWindow(inner.zoom, originX, originY, width / scale, height / scale)
    }

    /**
     * Groesste Zoomstufe, bei der die Strecke - mit [scale] vergroessert - in [width] x
     * [height] Bildpixel passt, und der Ausschnitt dazu, mittig auf die Strecke gelegt.
     */
    fun fit(points: List<TrackPoint>, width: Double, height: Double, scale: Double, padding: Double): MapWindow {
        val usableW = (width - 2 * padding).coerceAtLeast(1.0)
        val usableH = (height - 2 * padding).coerceAtLeast(1.0)
        var zoom = MAX_ZOOM
        while (zoom > MIN_ZOOM) {
            val xs = points.map { worldX(it.lon, zoom) }
            val ys = points.map { worldY(it.lat, zoom) }
            if ((xs.max() - xs.min()) * scale <= usableW && (ys.max() - ys.min()) * scale <= usableH) break
            zoom--
        }
        val xs = points.map { worldX(it.lon, zoom) }
        val ys = points.map { worldY(it.lat, zoom) }
        val cx = (xs.min() + xs.max()) / 2
        val cy = (ys.min() + ys.max()) / 2
        val w = width / scale
        val h = height / scale
        return MapWindow(zoom, cx - w / 2, cy - h / 2, w, h)
    }
}
