package com.pbrockt.fahrradcomputer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pbrockt.fahrradcomputer.data.Zones
import java.util.Locale
import kotlin.math.abs

/**
 * Gemeinsamer Ausschnitt aller Diagramme einer Fahrt (Anteil 0..1 der Fahrzeit):
 * Zoomt man in einem, zoomen alle mit - so bleiben Tempo, Puls und Hoehe uebereinander.
 */
@Stable
class ChartZoom {
    var start by mutableFloatStateOf(0f)
        private set
    var end by mutableFloatStateOf(1f)
        private set

    val zoomed: Boolean get() = end - start < 0.999f

    /** [factor] > 1 = hinein; [focus] = Fingermitte im Diagramm (0..1); [pan] = Verschiebung als Anteil der Breite. */
    fun transform(factor: Float, focus: Float, pan: Float) {
        val span = end - start
        val newSpan = (span / factor).coerceIn(MIN_SPAN, 1f)
        val anchor = start + focus * span
        var s = anchor - focus * newSpan - pan * newSpan
        s = s.coerceIn(0f, 1f - newSpan)
        start = s
        end = s + newSpan
    }

    fun reset() {
        start = 0f
        end = 1f
    }

    private companion object {
        const val MIN_SPAN = 0.02f
    }
}

/**
 * Zwei Finger zoomen, ein Finger verschiebt (nur wenn hineingezoomt und eher waagerecht -
 * sonst scrollt die Seite), doppelt tippen zeigt wieder alles.
 */
private fun Modifier.zoomable(zoom: ChartZoom) = this
    .pointerInput(zoom) { detectTapGestures(onDoubleTap = { zoom.reset() }) }
    .pointerInput(zoom) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do {
                val event = awaitPointerEvent()
                val pressed = event.changes.count { it.pressed }
                if (pressed >= 2) {
                    val c = event.calculateCentroid(useCurrent = true)
                    zoom.transform(event.calculateZoom(), (c.x / size.width).coerceIn(0f, 1f), event.calculatePan().x / size.width)
                    event.changes.forEach { it.consume() }
                } else if (pressed == 1 && zoom.zoomed) {
                    val pan = event.calculatePan()
                    if (abs(pan.x) > abs(pan.y)) {
                        zoom.transform(1f, 0.5f, pan.x / size.width)
                        event.changes.forEach { it.consume() }
                    }
                }
            } while (event.changes.any { it.pressed })
        }
    }

/**
 * Ein Verlauf ueber die Fahrzeit: Titel in Grossbuchstaben, Linie in der
 * Farbe der Messgroesse oder - mit [zones] - je Abschnitt in der Zonenfarbe, dahinter
 * blasse Zonenbaender. Fehlende Werte (null) unterbrechen die Linie.
 */
@Composable
fun LineChart(
    title: String,
    unit: String,
    minutes: List<Float>,
    values: List<Float?>,
    zoom: ChartZoom,
    modifier: Modifier = Modifier,
    color: Color = AppColors.Accent,
    zones: Zones? = null,
    /** Mindestens so viel Spanne zeigen (z. B. 60 m bei der Hoehe), damit Flaches flach bleibt. */
    minRange: Float = 0f,
) {
    if (values.none { it != null } || minutes.size != values.size || minutes.isEmpty()) return
    val grid = AppColors.Line
    val zoneColors = AppColors.Zones
    val endMin = minutes.last().takeIf { it > 0f } ?: 1f
    val fromMin = zoom.start * endMin
    val toMin = zoom.end * endMin
    // Wertebereich nur aus dem sichtbaren Ausschnitt - beim Zoomen wird der Verlauf deutlicher.
    val visible = values.filterIndexed { i, v -> v != null && minutes[i] in fromMin..toMin }.filterNotNull()
        .ifEmpty { values.filterNotNull() }
    val realLo = visible.min()
    val realHi = visible.max()
    val pad = ((minRange - (realHi - realLo)) / 2).coerceAtLeast(0f)
    val lo = realLo - pad
    val hi = realHi + pad
    val range = (hi - lo).takeIf { it > 0f } ?: 1f
    val all = values.filterNotNull()

    Column(modifier.background(AppColors.Bg).padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                title.uppercase(Locale.GERMANY), color = color, fontFamily = Condensed, fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp, letterSpacing = 0.6.sp, modifier = Modifier.weight(1f),
            )
            Text(
                "Ø ${fmt(all.average().toFloat())} · max ${fmt(all.max())} $unit",
                color = AppColors.Dim, fontSize = 13.sp,
            )
        }
        Canvas(Modifier.fillMaxWidth().height(130.dp).padding(top = 6.dp).zoomable(zoom)) {
            fun y(v: Float) = size.height - (v - lo) / range * size.height
            fun x(m: Float) = (m - fromMin) / (toMin - fromMin) * size.width
            if (zones != null) {
                val bounds = listOf(Float.NEGATIVE_INFINITY) + zones.thresholds.map { it.toFloat() } + Float.POSITIVE_INFINITY
                for (z in 0 until 5) {
                    val top = y(bounds[z + 1].coerceAtMost(hi)).coerceAtLeast(0f)
                    val bottom = y(bounds[z].coerceAtLeast(lo)).coerceAtMost(size.height)
                    if (bottom > top) drawRect(zoneColors[z].copy(alpha = 0.18f), Offset(0f, top), Size(size.width, bottom - top))
                }
            }
            for (f in listOf(0f, 0.5f, 1f)) {
                val yy = size.height * f
                drawLine(grid, Offset(0f, yy), Offset(size.width, yy), strokeWidth = 1f)
            }
            clipRect {
                for (i in 1 until values.size) {
                    if (minutes[i] < fromMin || minutes[i - 1] > toMin) continue
                    val a = values[i - 1] ?: continue
                    val b = values[i] ?: continue
                    drawLine(
                        zones?.let { zoneColors[it.zoneOf(b) - 1] } ?: color,
                        Offset(x(minutes[i - 1]), y(a)), Offset(x(minutes[i]), y(b)),
                        strokeWidth = 4f, cap = StrokeCap.Round,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            Text("${fmt(fromMin)} min", color = AppColors.Dim, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text("${fmt(realLo)}–${fmt(realHi)} $unit", color = AppColors.Dim, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text("${fmt(toMin)} min", color = AppColors.Dim, fontSize = 11.sp)
        }
    }
}

/** Zeit je Zone aus einem Verlauf; Abschnitte ohne Wert oder mit [skip] zaehlen nicht. */
fun zoneSeconds(minutes: List<Float>, values: List<Float?>, zones: Zones, skip: (Float) -> Boolean = { false }): List<Long> {
    val sec = LongArray(5)
    // Luecken (GPS weg, Pause) nicht mitzaehlen: deutlich laenger als ein normaler Abschnitt.
    val typical = if (minutes.size > 1) minutes.last() / (minutes.size - 1) else 0f
    val maxStep = maxOf(0.5f, typical * 3)
    for (i in 1 until values.size) {
        val v = values[i] ?: continue
        if (skip(v)) continue
        val dtMin = minutes[i] - minutes[i - 1]
        if (dtMin <= 0 || dtMin > maxStep) continue
        sec[zones.zoneOf(v) - 1] += (dtMin * 60).toLong()
    }
    return sec.toList()
}

/** Zeit in Zonen als gestapelter Balken mit Prozentangaben. */
@Composable
fun ZoneTimeBar(title: String, seconds: List<Long>, modifier: Modifier = Modifier) {
    val total = seconds.sum().toFloat()
    if (total <= 0f) return
    Column(modifier.background(AppColors.Bg).padding(vertical = 8.dp)) {
        Text(
            title.uppercase(Locale.GERMANY), fontFamily = Condensed, fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp, letterSpacing = 0.6.sp,
        )
        Row(Modifier.fillMaxWidth().height(22.dp).padding(top = 6.dp)) {
            seconds.forEachIndexed { i, s ->
                if (s > 0) Box(Modifier.weight(s / total).fillMaxHeight().background(AppColors.zone(i + 1)))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            seconds.forEachIndexed { i, s ->
                Text(
                    "Z${i + 1} ${String.format(Locale.GERMANY, "%.0f", s / total * 100)} %",
                    color = AppColors.zone(i + 1), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun fmt(v: Float) = String.format(Locale.GERMANY, if (abs(v) >= 100) "%.0f" else "%.1f", v)
