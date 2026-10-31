package com.pbrockt.fahrradcomputer.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pbrockt.fahrradcomputer.recording.Altitude
import com.pbrockt.fahrradcomputer.routes.RouteProfile
import com.pbrockt.fahrradcomputer.routes.Section
import com.pbrockt.fahrradcomputer.routes.Trend
import com.pbrockt.fahrradcomputer.ui.AppColors
import com.pbrockt.fahrradcomputer.ui.Condensed
import com.pbrockt.fahrradcomputer.ui.formatDecimal
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Dieser und der naechste Abschnitt (Anstieg, Abfahrt, flach) als Bild: Rampen in der
 * Farbe ihrer Steigung, mit Prozent drin. Ein Anstieg wird ganz gezeigt - man sieht,
 * was geschafft ist und was noch kommt.
 */
@Composable
internal fun SectionsTile(d: TileData, modifier: Modifier) {
    val profile = d.activeRoute?.profile
    val pos = d.progress?.doneM ?: 0.0
    val idx = profile?.sectionIndexAt(pos) ?: -1
    if (profile == null || idx < 0) {
        ValueTile(
            when {
                d.activeRoute == null -> "Anstiege · keine Route"
                profile == null -> "Anstiege · Profil fehlt"
                else -> "Ziel erreicht"
            },
            "--", modifier, "", AppColors.Grade,
        )
        return
    }
    val sections = profile.sections
    val cur = sections[idx]
    // Nach einem kurzen flachen Stueck den Abschnitt dahinter mitzeigen - der ist das Spannende.
    val shown = buildList {
        add(cur)
        sections.getOrNull(idx + 1)?.let { n ->
            add(n)
            val after = sections.getOrNull(idx + 2)
            if (n.trend == Trend.FLAT && n.lengthM <= SHORT_FLAT_M && after != null) add(after)
        }
    }
    val next = shown.drop(1).lastOrNull { it.trend != Trend.FLAT } ?: shown.getOrNull(1)
    // Langes Flachstueck: nicht alles schon Gefahrene zeigen.
    val from = if (cur.trend == Trend.FLAT) max(cur.startM, pos - FLAT_BACK_M) else cur.startM
    val to = shown.last().endM

    BoxWithConstraints(modifier.background(AppColors.Card)) {
        val tall = maxHeight > 110.dp
        val labelSize = (maxHeight.value * (if (tall) 0.075f else 0.12f)).coerceIn(11f, 17f).sp
        Column(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp)) {
            Text(
                currentText(profile, cur, pos), color = AppColors.Text, fontSize = labelSize,
                fontFamily = Condensed, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (tall) {
                Text(
                    next?.let { "danach ${describe(it)}" } ?: "danach: Ziel", color = AppColors.Dim, fontSize = labelSize * 0.85f,
                    fontFamily = Condensed, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            SectionsChart(profile, shown, pos, from, to, Modifier.weight(1f).fillMaxWidth())
        }
    }
}

/** "Anstieg 2/4 · noch 1,8 km · 124 Hm · 40 % geschafft" */
private fun currentText(p: RouteProfile, s: Section, pos: Double): String {
    val leftM = (s.endM - max(pos, s.startM)).coerceAtLeast(0.0)
    val km = "noch ${formatDecimal(leftM / 1000)} km"
    val done = ((pos - s.startM) / s.lengthM * 100).roundToInt()
    return when (s.trend) {
        Trend.UP -> {
            val hm = (p.elevationAt(s.endM) - p.elevationAt(max(pos, s.startM))).coerceAtLeast(0.0).roundToInt()
            val name = s.climb?.let { "Anstieg ${it.number}/${p.climbs.size}" } ?: "Anstieg"
            "$name · $km · $hm Hm" + if (done in 5..99) " · $done % geschafft" else ""
        }
        Trend.DOWN -> {
            val hm = (p.elevationAt(max(pos, s.startM)) - p.elevationAt(s.endM)).coerceAtLeast(0.0).roundToInt()
            "Abfahrt · $km · −$hm Hm"
        }
        Trend.FLAT -> "Flach · $km"
    }
}

private fun describe(s: Section): String {
    val km = "${formatDecimal(s.lengthM / 1000)} km"
    return when (s.trend) {
        Trend.UP -> "Anstieg $km · ${s.gainM.roundToInt()} Hm · Ø ${formatDecimal(s.grade)} %"
        Trend.DOWN -> "Abfahrt $km · −${formatDecimal(-s.grade)} %"
        Trend.FLAT -> "flach $km"
    }
}

@Composable
private fun SectionsChart(
    profile: RouteProfile, shown: List<Section>, pos: Double, from: Double, to: Double, modifier: Modifier,
) {
    val doneColor = AppColors.Line
    val divider = AppColors.Dim
    val card = AppColors.Card
    val text = AppColors.Text
    val markerFill = AppColors.Accent
    val ramps = shown.flatMap { it.ramps }.filter { it.endM > from }
    Canvas(modifier) {
        val labelPx = 13f * density
        val paint = android.graphics.Paint().apply {
            textSize = labelPx
            isAntiAlias = true
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        val top = labelPx * 1.6f
        val bottom = size.height
        val h = bottom - top
        val span = (to - from).coerceAtLeast(1.0)
        if (h <= 0 || size.width <= 0) return@Canvas

        // Hoehen im Ausschnitt; unten Platz lassen, damit auch flache Bloecke sichtbar sind.
        var lo = Double.MAX_VALUE
        var hi = -Double.MAX_VALUE
        var dd = from
        while (dd <= to) { val e = profile.elevationAt(dd); lo = min(lo, e); hi = max(hi, e); dd += RouteProfile.STEP_M / 2 }
        lo = min(lo, profile.elevationAt(to)); hi = max(hi, profile.elevationAt(to))
        val (yLo0, yHi) = Altitude.displayRange(lo, hi)
        val yLo = yLo0 - (yHi - yLo0) * 0.25
        fun x(m: Double) = ((m - from) / span * size.width).toFloat()
        fun y(e: Double) = bottom - ((e - yLo) / (yHi - yLo) * h).toFloat()

        // Flaechen: je Rampe ihre Farbe, schon Gefahrenes grau.
        for (r in ramps) {
            val a = max(r.startM, from)
            val b = r.endM
            val color = Color(RouteProfile.GRADE_COLORS[RouteProfile.gradeLevel(r.grade)])
            // Eine Flaeche je Teil - viele kleine Flaechen haetten sichtbare Kanten.
            fun area(m0: Double, m1: Double, c: Color) {
                if (m1 - m0 < 0.5) return
                val path = Path().apply {
                    moveTo(x(m0), bottom)
                    lineTo(x(m0), y(profile.elevationAt(m0)))
                    var m = (m0 / RouteProfile.STEP_M).toInt() * RouteProfile.STEP_M + RouteProfile.STEP_M
                    while (m < m1) { lineTo(x(m), y(profile.elevationAt(m))); m += RouteProfile.STEP_M }
                    lineTo(x(m1), y(profile.elevationAt(m1)))
                    lineTo(x(m1), bottom)
                    close()
                }
                drawPath(path, c)
            }
            val split = pos.coerceIn(a, b)
            area(a, split, doneColor)
            area(split, b, color)
            // Fuge zwischen den Rampen
            if (r.startM > from) {
                val xx = x(r.startM)
                drawLine(card, Offset(xx, bottom), Offset(xx, y(profile.elevationAt(r.startM))), strokeWidth = 1.5f * density)
            }
        }

        // Prozent in jede Rampe, die breit genug ist - in den Block, sonst darueber.
        for (r in ramps) {
            val a = max(r.startM, from)
            val g = r.grade
            if (abs(g) < 1) continue
            val label = "${if (g < 0) "−" else ""}${abs(g).roundToInt()} %"
            val tw = paint.measureText(label)
            val x0 = x(a)
            val x1 = x(r.endM)
            if (x1 - x0 < tw + 6 * density) continue
            val cx = (x0 + x1) / 2 - tw / 2
            val lowY = y(min(profile.elevationAt(a), r.endEle))
            val highY = y(max(profile.elevationAt(a), r.endEle))
            val inside = bottom - lowY >= labelPx * 1.8f
            paint.color = if (inside) android.graphics.Color.WHITE else text.toArgb()
            val baseline = if (inside) bottom - labelPx * 0.6f else highY - labelPx * 0.4f
            drawContext.canvas.nativeCanvas.drawText(label, cx, max(baseline, labelPx), paint)
        }

        // Grenze zum naechsten Abschnitt
        for (s in shown.drop(1)) {
            val xx = x(s.startM)
            drawLine(
                divider, Offset(xx, top - labelPx * 0.5f), Offset(xx, bottom), strokeWidth = 1.5f * density,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f * density, 4f * density)),
            )
        }

        // Gipfel-Faehnchen am Ende eines Anstiegs
        for (s in shown) {
            if (s.trend != Trend.UP) continue
            val xx = x(s.endM).coerceIn(2f * density, size.width - 12f * density)
            val yy = y(profile.elevationAt(s.endM))
            val poleTop = yy - labelPx * 1.3f
            drawLine(text, Offset(xx, yy), Offset(xx, poleTop), strokeWidth = 1.5f * density)
            drawPath(
                Path().apply {
                    moveTo(xx, poleTop); lineTo(xx + 10f * density, poleTop + 4f * density); lineTo(xx, poleTop + 8f * density); close()
                },
                markerFill,
            )
        }

        // Wo man gerade ist
        if (pos in from..to) {
            val xx = x(pos)
            val yy = y(profile.elevationAt(pos))
            drawCircle(markerFill, radius = 6f * density, center = Offset(xx, yy))
            drawCircle(text, radius = 6f * density, center = Offset(xx, yy), style = Stroke(2f * density))
        }
    }
}

private const val SHORT_FLAT_M = 1_500.0
private const val FLAT_BACK_M = 300.0
