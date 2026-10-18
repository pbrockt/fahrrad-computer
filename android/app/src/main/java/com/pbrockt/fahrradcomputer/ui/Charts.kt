package com.pbrockt.fahrradcomputer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * Ein Verlauf ueber die Fahrzeit. Fehlende Werte (null) unterbrechen die Linie.
 * [colorOf] faerbt jedes Teilstueck einzeln - so wird die Pulslinie in Zonenfarben gemalt.
 */
@Composable
fun LineChart(
    title: String,
    unit: String,
    minutes: List<Float>,
    values: List<Float?>,
    modifier: Modifier = Modifier,
    /** Mindestens so viel Spanne zeigen (z. B. 60 m bei der Hoehe), damit Flaches flach bleibt. */
    minRange: Float = 0f,
    colorOf: (Float) -> Color? = { null },
) {
    val present = values.filterNotNull()
    if (present.isEmpty() || minutes.size != values.size) return
    val lineDefault = AppColors.Accent
    val grid = AppColors.Line
    val realLo = present.min()
    val realHi = present.max()
    val pad = ((minRange - (realHi - realLo)) / 2).coerceAtLeast(0f)
    val lo = realLo - pad
    val hi = realHi + pad
    val range = (hi - lo).takeIf { it > 0f } ?: 1f
    val endMin = minutes.last().takeIf { it > 0f } ?: 1f

    Column(modifier.clip(RoundedCornerShape(10.dp)).background(AppColors.Panel).padding(12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                "Ø ${fmt(present.average().toFloat())} · max ${fmt(realHi)} $unit",
                color = AppColors.Dim, fontSize = 13.sp,
            )
        }
        Canvas(Modifier.fillMaxWidth().height(120.dp).padding(top = 8.dp)) {
            for (f in listOf(0f, 0.5f, 1f)) {
                val y = size.height * f
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            fun pt(i: Int, v: Float) = Offset(
                minutes[i] / endMin * size.width,
                size.height - (v - lo) / range * size.height,
            )
            for (i in 1 until values.size) {
                val a = values[i - 1] ?: continue
                val b = values[i] ?: continue
                drawLine(
                    colorOf(b) ?: lineDefault, pt(i - 1, a), pt(i, b),
                    strokeWidth = 4f, cap = StrokeCap.Round,
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            Text("${fmt(realLo)} $unit", color = AppColors.Dim, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text("${fmt(endMin)} min", color = AppColors.Dim, fontSize = 11.sp)
        }
    }
}

private fun fmt(v: Float) = String.format(Locale.GERMANY, if (v >= 100) "%.0f" else "%.1f", v)
