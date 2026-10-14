package com.pbrockt.fahrradcomputer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import com.pbrockt.fahrradcomputer.recording.TrackPoint
import kotlin.math.cos
import kotlin.math.min

/**
 * Die Strecke als Linie, auf die Flaeche skaliert - ohne Karte. Gruener Punkt = Start,
 * heller Punkt = aktuelle Position bzw. Ziel.
 */
@Composable
fun RouteSketch(route: List<TrackPoint>, modifier: Modifier) {
    // Farben vorher holen: im Canvas-Block ist kein Composable-Zugriff moeglich.
    val line = AppColors.Accent
    val start = AppColors.Go
    val here = AppColors.Text
    Canvas(modifier) {
        if (route.size < 2) return@Canvas
        // Laengengrade schrumpfen zu den Polen hin - mit cos(Breite) bleibt die Form unverzerrt.
        val k = cos(Math.toRadians(route.first().lat))
        val xs = route.map { it.lon * k }
        val ys = route.map { it.lat }
        val minX = xs.min(); val maxX = xs.max()
        val minY = ys.min(); val maxY = ys.max()
        val span = maxOf(maxX - minX, maxY - minY).takeIf { it > 0 } ?: return@Canvas
        val scale = min(size.width, size.height) / span.toFloat()
        val offX = (size.width - (maxX - minX).toFloat() * scale) / 2
        val offY = (size.height - (maxY - minY).toFloat() * scale) / 2
        fun pt(i: Int) = Offset(
            offX + (xs[i] - minX).toFloat() * scale,
            size.height - offY - (ys[i] - minY).toFloat() * scale,
        )
        val path = Path().apply {
            moveTo(pt(0).x, pt(0).y)
            for (i in 1 until route.size) lineTo(pt(i).x, pt(i).y)
        }
        drawPath(path, line, style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(start, radius = 8f, center = pt(0))
        drawCircle(here, radius = 10f, center = pt(route.lastIndex))
    }
}
