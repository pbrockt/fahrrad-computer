package com.pbrockt.fahrradcomputer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Abbiegepfeil wie im Navi: Stiel von unten zur Mitte, dann im Abbiegewinkel weiter.
 * [angle] in Grad, positiv = rechts. null = Ziel (Zielflagge).
 */
@Composable
fun TurnArrow(angle: Double?, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = min(size.width, size.height)
        val cx = size.width / 2
        val cy = size.height / 2
        val w = s * 0.11f
        val stroke = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)

        if (angle == null) {
            // Zielflagge: Mast und Fahne
            val top = Offset(cx - s * 0.2f, cy - s * 0.38f)
            drawLine(color, Offset(top.x, cy + s * 0.4f), top, strokeWidth = w * 0.7f, cap = StrokeCap.Round)
            drawPath(
                Path().apply {
                    moveTo(top.x, top.y)
                    lineTo(top.x + s * 0.5f, top.y + s * 0.15f)
                    lineTo(top.x, top.y + s * 0.3f)
                    close()
                },
                color,
            )
            return@Canvas
        }

        // Bei "wenden" waere der Pfeil zurueck auf dem Stiel - etwas versetzt zeichnen.
        val a = Math.toRadians(angle.coerceIn(-170.0, 170.0))
        val bottom = Offset(cx, cy + s * 0.4f)
        val mid = Offset(cx, cy)
        val len = s * 0.36f
        val tip = Offset(mid.x + (sin(a) * len).toFloat(), mid.y - (cos(a) * len).toFloat())
        drawPath(
            Path().apply {
                moveTo(bottom.x, bottom.y)
                lineTo(mid.x, mid.y)
                lineTo(tip.x, tip.y)
            },
            color, style = stroke,
        )
        // Pfeilspitze: zwei Schenkel, je 35 Grad gegen die Pfeilrichtung.
        val head = s * 0.17f
        for (side in listOf(-1, 1)) {
            val b = a + Math.PI + side * Math.toRadians(35.0)
            drawLine(
                color, tip,
                Offset(tip.x + (sin(b) * head).toFloat(), tip.y - (cos(b) * head).toFloat()),
                strokeWidth = w, cap = StrokeCap.Round,
            )
        }
    }
}
