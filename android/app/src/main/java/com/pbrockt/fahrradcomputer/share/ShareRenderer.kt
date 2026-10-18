package com.pbrockt.fahrradcomputer.share

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import com.pbrockt.fahrradcomputer.recording.Altitude
import com.pbrockt.fahrradcomputer.recording.RideSummary
import com.pbrockt.fahrradcomputer.recording.TrackPoint
import com.pbrockt.fahrradcomputer.ui.formatDecimal
import com.pbrockt.fahrradcomputer.ui.formatDuration
import com.pbrockt.fahrradcomputer.ui.rideTitle
import java.util.Locale

/** Zusammengesetzte OSM-Kacheln fuer [ShareRenderer.mapTop]..[ShareRenderer.mapBottom]. */
class MapBackground(val bitmap: android.graphics.Bitmap, val window: MapWindow, val scale: Float)

/** Was auf Bild bzw. Video kommt. [points] ist die bereits gekuerzte Strecke. */
class ShareContent(
    val summary: RideSummary,
    val points: List<TrackPoint>,
    /** Wie viele Meter an Start und Ende ausgeblendet wurden (fuer den Hinweis unten). */
    val trimmedM: Int,
)

/**
 * Zeichnet die Teilen-Karte auf ein Canvas - fuer das Bild einmal, fuer das Video je
 * Einzelbild mit [draw]-Fortschritt. Laeuft auch auf dem Hardware-Canvas des
 * Video-Encoders, deshalb ohne Unschaerfe-Filter (der Leuchteffekt ist eine breite,
 * halbtransparente Linie).
 */
class ShareRenderer(private val content: ShareContent, private val w: Int, private val h: Int) {

    private val s = w / 1080f
    private val pad = 64 * s
    private val statsTop = h - 330 * s
    private val hasAltitude = content.points.count { it.altitude != null } > 1
    private val profileTop = statsTop - (if (hasAltitude) 150 else 0) * s
    private val routeTop = 230 * s
    private val routeBottom = profileTop - 40 * s

    // Fuer das Zeichnen ausduennen - mehr als ~2000 Punkte sieht man ohnehin nicht.
    private val drawIndices = content.points.let { p ->
        val step = maxOf(1, p.size / 2000)
        p.indices.filter { i -> i % step == 0 || i == p.lastIndex }
    }
    private val drawPoints = drawIndices.map { content.points[it] }
    // Geglaettet auf allen Punkten, dann ausgeduennt - sonst glaettet man zu grob.
    private val smoothAltitudes = Altitude.smoothPoints(content.points).let { a -> drawIndices.map { a[it] } }
    private val fitted = ProjectedRoute.fit(drawPoints, pad, routeTop, w - 2 * pad, routeBottom - routeTop)
    private var route = fitted
    private val fullPath = pathUpTo(route.size - 1, null)

    /** Bereich fuer den Kartenhintergrund: volle Breite zwischen Kopf und Hoehenprofil. */
    val mapTop = 205 * s
    val mapBottom = profileTop - 45 * s
    val mapWidth: Int get() = w
    val mapHeight: Int get() = (mapBottom - mapTop).toInt()
    /** Wieviel Rand die Strecke zum Kartenrand haben soll. */
    val mapPadding = 70 * s

    private val accent = Color.parseColor("#3FA7D6")
    private val bgPaint = Paint().apply {
        shader = LinearGradient(0f, 0f, 0f, h.toFloat(), Color.parseColor("#0B0F14"), Color.parseColor("#17232F"), Shader.TileMode.CLAMP)
    }
    private fun line(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = width * s
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val ghost = line(Color.argb(55, 63, 167, 214), 8f)
    private val glow = line(Color.argb(70, 63, 167, 214), 26f)
    private val main = line(accent, 9f)
    private fun text(size: Float, color: Int, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size * s
        this.color = color
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }
    private val white = Color.parseColor("#EEF2F5")
    private val dim = Color.parseColor("#8A97A3")
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    private val casing = line(Color.WHITE, 15f)
    private val onMapLine = line(Color.parseColor("#1C6FD6"), 8f)

    /**
     * [progress] null = Bild (alles fertig), sonst 0..1 fuer die Video-Animation.
     * Mit [map] liegt die Strecke auf der OSM-Karte statt auf dunklem Grund.
     */
    fun draw(canvas: Canvas, progress: Float?, map: MapBackground? = null) {
        val p = progress ?: 1f
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)
        route = if (map != null) ProjectedRoute.onMap(drawPoints, map.window, map.scale, 0f, mapTop) else fitted
        if (map != null && route.size >= 2) {
            canvas.drawBitmap(map.bitmap, 0f, mapTop, Paint(Paint.FILTER_BITMAP_FLAG))
            val path = pathUpTo(route.size - 1, null)
            canvas.drawPath(path, casing)
            canvas.drawPath(path, onMapLine)
            fill.color = Color.parseColor("#46B97A")
            canvas.drawCircle(route.xs[0], route.ys[0], 13 * s, fill)
            fill.color = Color.parseColor("#E5484D")
            canvas.drawCircle(route.xs.last(), route.ys.last(), 13 * s, fill)
            // Pflichtangabe der OSM-Lizenz, gut lesbar auf hellem Kasten.
            val attr = "© OpenStreetMap-Mitwirkende"
            val tp = text(24f, Color.parseColor("#333333"))
            val tw = tp.measureText(attr)
            fill.color = Color.argb(200, 255, 255, 255)
            canvas.drawRect(w - tw - 24 * s, mapBottom - 40 * s, w.toFloat(), mapBottom, fill)
            canvas.drawText(attr, w - tw - 12 * s, mapBottom - 12 * s, tp)
        }

        // Kopf
        canvas.drawText("Radfahrt", pad, 120 * s, text(66f, white, bold = true))
        canvas.drawText(rideTitle(content.summary), pad, 175 * s, text(34f, dim))

        // Strecke (ohne Karte)
        if (map == null && route.size >= 2) {
            if (progress != null) canvas.drawPath(fullPath, ghost)
            val (idx, x, y) = route.positionAt(p)
            val traveled = if (progress == null) fullPath else pathUpTo(idx, x to y)
            canvas.drawPath(traveled, glow)
            canvas.drawPath(traveled, main)
            fill.color = Color.parseColor("#46B97A")
            canvas.drawCircle(route.xs[0], route.ys[0], 11 * s, fill)
            fill.color = Color.WHITE
            canvas.drawCircle(x, y, 15 * s, fill)
            fill.color = accent
            canvas.drawCircle(x, y, 8 * s, fill)
        }

        // Hoehenprofil
        if (hasAltitude) drawProfile(canvas, p)

        // Kennzahlen - im Video zaehlen Distanz und Zeit mit.
        val r = content.summary
        val stats = listOf(
            "Distanz" to "${formatDecimal(r.distanceM / 1000 * p, 1)} km",
            "Fahrzeit" to formatDuration((r.movingTimeMs * p).toLong()),
            "Ø Tempo" to "${formatDecimal(r.avgSpeedKmh)} km/h",
            "Höhenmeter" to "${String.format(Locale.GERMANY, "%.0f", r.elevationGainM * p)} m",
            (if (r.avgHr != null) "Ø Puls" to "${r.avgHr} bpm" else "Max. Tempo" to "${formatDecimal(r.maxSpeedKmh)} km/h"),
            (if (r.calories > 0) "Kalorien" to "${String.format(Locale.GERMANY, "%.0f", r.calories * p)} kcal"
            else "Max. Tempo" to "${formatDecimal(r.maxSpeedKmh)} km/h"),
        )
        val colW = (w - 2 * pad) / 3
        stats.forEachIndexed { i, (label, value) ->
            val x = pad + (i % 3) * colW
            val y = statsTop + (i / 3) * 135 * s
            canvas.drawText(label, x, y + 40 * s, text(30f, dim))
            canvas.drawText(value, x, y + 100 * s, text(50f, white, bold = true))
        }

        // Fuss
        val foot = text(26f, dim)
        if (content.trimmedM > 0) {
            canvas.drawText("Start und Ende (je ${content.trimmedM} m) ausgeblendet", pad, h - 40 * s, foot)
        }
        val brand = "Fahrrad-Computer"
        canvas.drawText(brand, w - pad - foot.measureText(brand), h - 40 * s, foot)
    }

    private fun drawProfile(canvas: Canvas, progress: Float) {
        val alts = smoothAltitudes
        val present = alts.filterNotNull()
        val realLo = present.min()
        val realHi = present.max()
        // Mindestens 60 m Spanne zeigen - sonst wirkt eine flache Fahrt wie ein Gebirge.
        val (lo, hi) = Altitude.displayRange(realLo, realHi)
        val range = hi - lo
        val top = profileTop
        val height = 110 * s
        val left = pad
        val width = w - 2 * pad
        fun xAt(i: Int) = left + route.fraction[i] * width
        fun yAt(a: Double) = top + height - ((a - lo) / range * height).toFloat()

        val area = Path()
        var started = false
        var lastX = left
        alts.forEachIndexed { i, a ->
            if (a == null) return@forEachIndexed
            if (!started) { area.moveTo(xAt(i), top + height); started = true }
            area.lineTo(xAt(i), yAt(a))
            lastX = xAt(i)
        }
        area.lineTo(lastX, top + height)
        area.close()
        fill.color = Color.argb(70, 138, 151, 163)
        canvas.drawPath(area, fill)
        // Gefahrener Teil hervorgehoben
        canvas.save()
        canvas.clipRect(left, top, left + width * progress, top + height)
        fill.color = Color.argb(150, 63, 167, 214)
        canvas.drawPath(area, fill)
        canvas.restore()
        canvas.drawText("Höhe ${String.format(Locale.GERMANY, "%.0f–%.0f", realLo, realHi)} m", left, top - 10 * s, text(26f, dim))
    }

    /** Linie vom Start bis Punkt [index], optional mit Zwischenstueck bis [tip]. */
    private fun pathUpTo(index: Int, tip: Pair<Float, Float>?): Path {
        val path = Path()
        if (route.size == 0) return path
        path.moveTo(route.xs[0], route.ys[0])
        for (i in 1..index.coerceAtMost(route.size - 1)) path.lineTo(route.xs[i], route.ys[i])
        tip?.let { (x, y) -> path.lineTo(x, y) }
        return path
    }
}
