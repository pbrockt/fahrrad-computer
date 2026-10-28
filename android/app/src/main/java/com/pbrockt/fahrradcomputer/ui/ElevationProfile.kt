package com.pbrockt.fahrradcomputer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.pbrockt.fahrradcomputer.routes.RouteProfile
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Hoehenprofil einer Route, nach Steigung eingefaerbt, mit Achsen und
 * - waehrend der Fahrt - einer Markierung, wo man gerade ist.
 *
 * [fromM]/[toM] begrenzen den gezeigten Ausschnitt (z. B. "die naechsten 5 km").
 */
@Composable
fun ElevationProfileChart(
    profile: RouteProfile,
    modifier: Modifier = Modifier,
    positionM: Double? = null,
    fromM: Double = 0.0,
    toM: Double = profile.lengthM,
    /** Schon gefahrenen Teil (vor [positionM]) grau zeichnen - farbig bleibt, was noch kommt. */
    dimDone: Boolean = false,
) {
    val doneColor = AppColors.Line
    val axis = AppColors.Dim
    val grid = AppColors.Line
    val marker = AppColors.Text
    val markerFill = AppColors.Accent
    val gradeColors = RouteProfile.GRADE_COLORS.map { Color(it) }
    Canvas(modifier) {
        val labelPaint = android.graphics.Paint().apply {
            color = axis.toArgb()
            textSize = 11.5f * density
            isAntiAlias = true
        }
        val left = 34f * density
        val bottom = size.height - 16f * density
        val top = 4f * density
        val w = size.width - left
        val h = bottom - top
        if (w <= 0 || h <= 0) return@Canvas
        val span = (toM - fromM).coerceAtLeast(1.0)

        // Hoehe nur im Ausschnitt, mindestens 60 m Spanne - sonst wirkt jede Welle wie ein Berg.
        val i0 = max(0, floor(fromM / RouteProfile.STEP_M).toInt())
        val i1 = min(profile.distanceM.size - 1, ceil(toM / RouteProfile.STEP_M).toInt())
        var lo = Double.MAX_VALUE
        var hi = -Double.MAX_VALUE
        for (i in i0..i1) { lo = min(lo, profile.elevationM[i]); hi = max(hi, profile.elevationM[i]) }
        val (yLo, yHi) = Altitude.displayRange(lo, hi)
        val range = (yHi - yLo).coerceAtLeast(1.0)
        fun x(d: Double) = left + ((d - fromM) / span * w).toFloat()
        fun y(e: Double) = bottom - ((e - yLo) / range * h).toFloat()

        // Gitter und Hoehen-Beschriftung
        for (f in listOf(0.0, 0.5, 1.0)) {
            val e = yLo + range * f
            val yy = y(e)
            drawLine(grid, Offset(left, yy), Offset(size.width, yy), strokeWidth = 1f)
            drawContext.canvas.nativeCanvas.drawText("${e.toInt()}", 0f, yy + labelPaint.textSize / 3, labelPaint)
        }

        // Flaechen je Abschnitt in der Farbe seiner Steigung
        val grades = profile.segmentGrades()
        for (i in i0 until i1) {
            val d0 = profile.distanceM[i]
            val d1 = profile.distanceM[i + 1]
            if (d1 < fromM || d0 > toM) continue
            val path = Path().apply {
                moveTo(x(d0), bottom)
                lineTo(x(d0), y(profile.elevationM[i]))
                lineTo(x(d1), y(profile.elevationM[i + 1]))
                lineTo(x(d1), bottom)
                close()
            }
            val done = dimDone && positionM != null && d1 <= positionM
            drawPath(path, if (done) doneColor else gradeColors[RouteProfile.gradeLevel(grades[i])])
        }

        // km-Beschriftung: 4-6 Striche, in runden Schritten
        val stepKm = listOf(0.5, 1.0, 2.0, 5.0, 10.0, 20.0, 50.0).first { span / 1000 / it <= 6 }
        var km = ceil(fromM / 1000 / stepKm) * stepKm
        while (km * 1000 <= toM) {
            val xx = x(km * 1000)
            drawLine(axis, Offset(xx, bottom), Offset(xx, bottom + 3f * density), strokeWidth = 1.5f)
            val text = if (stepKm < 1) String.format(java.util.Locale.GERMANY, "%.1f km", km) else "${km.toInt()} km"
            val tw = labelPaint.measureText(text)
            drawContext.canvas.nativeCanvas.drawText(
                text, (xx - tw / 2).coerceIn(left, size.width - tw), size.height - 2f * density, labelPaint,
            )
            km += stepKm
        }

        // Wo man gerade ist: senkrechte Linie und Punkt auf dem Profil
        positionM?.takeIf { it in fromM..toM }?.let { d ->
            val xx = x(d)
            val yy = y(profile.elevationAt(d))
            drawLine(marker, Offset(xx, top), Offset(xx, bottom), strokeWidth = 2f * density)
            drawCircle(markerFill, radius = 7f * density, center = Offset(xx, yy))
            drawCircle(marker, radius = 7f * density, center = Offset(xx, yy), style = Stroke(2f * density))
        }
    }
}

private typealias Altitude = com.pbrockt.fahrradcomputer.recording.Altitude
