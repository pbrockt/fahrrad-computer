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

    // --- Poster mit Karte: Karte ueber das ganze Bild, Infos als Kaesten darueber ---
    private val topCardBottom = pad + 160 * s
    private val bottomCardHeight = (if (hasAltitude) 380 else 290) * s
    private val bottomCardTop = h - pad - bottomCardHeight

    /** Bildbereich, in den die Strecke auf dem Poster passen soll: zwischen den Kaesten. */
    val posterRouteArea: FloatArray
        get() = floatArrayOf(pad + 20 * s, topCardBottom + 60 * s, w - pad - 20 * s, bottomCardTop - 80 * s)

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
        if (map != null) {
            drawPoster(canvas, map)
            return
        }
        val p = progress ?: 1f
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)
        route = fitted

        // Kopf
        canvas.drawText("Radfahrt", pad, 120 * s, text(66f, white, bold = true))
        canvas.drawText(rideTitle(content.summary), pad, 175 * s, text(34f, dim))

        // Strecke
        if (route.size >= 2) {
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
        if (hasAltitude) drawProfile(canvas, p, pad, profileTop, w - 2 * pad, 110 * s, labelColor = dim)

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

    /**
     * Poster: die OSM-Karte ueber das ganze Bild, die Strecke kraeftig darauf, oben ein
     * Kasten mit Titel und Distanz, unten ein Kasten mit Hoehenprofil und Kennzahlen.
     */
    private fun drawPoster(canvas: Canvas, map: MapBackground) {
        canvas.drawBitmap(map.bitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        // Leichte Verlaeufe oben und unten, damit die Kaesten weich in die Karte uebergehen.
        val shade = Paint()
        shade.shader = LinearGradient(0f, 0f, 0f, 380 * s, Color.argb(120, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), 380 * s, shade)
        shade.shader = LinearGradient(0f, bottomCardTop - 200 * s, 0f, h.toFloat(), Color.TRANSPARENT, Color.argb(140, 0, 0, 0), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, bottomCardTop - 200 * s, w.toFloat(), h.toFloat(), shade)

        route = ProjectedRoute.onMap(drawPoints, map.window, map.scale, 0f, 0f)
        if (route.size >= 2) {
            val path = pathUpTo(route.size - 1, null)
            canvas.drawPath(path, casing)
            canvas.drawPath(path, onMapLine)
            fill.color = Color.WHITE
            canvas.drawCircle(route.xs[0], route.ys[0], 16 * s, fill)
            canvas.drawCircle(route.xs.last(), route.ys.last(), 16 * s, fill)
            fill.color = Color.parseColor("#46B97A")
            canvas.drawCircle(route.xs[0], route.ys[0], 11 * s, fill)
            fill.color = Color.parseColor("#E5484D")
            canvas.drawCircle(route.xs.last(), route.ys.last(), 11 * s, fill)
        }

        val card = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(215, 11, 15, 20) }
        val radius = 28 * s
        val r = content.summary

        // Kopf-Kasten: Titel, Datum, Distanz gross rechts
        canvas.drawRoundRect(pad, pad, w - pad, topCardBottom, radius, radius, card)
        canvas.drawText("Radfahrt", pad + 36 * s, pad + 78 * s, text(58f, white, bold = true))
        canvas.drawText(rideTitle(r), pad + 36 * s, pad + 128 * s, text(30f, dim))
        val dist = "${formatDecimal(r.distanceM / 1000, 1)} km"
        val distPaint = text(64f, Color.parseColor("#3FA7D6"), bold = true)
        canvas.drawText(dist, w - pad - 36 * s - distPaint.measureText(dist), pad + 100 * s, distPaint)

        // Lizenzhinweis direkt ueber dem unteren Kasten
        val attr = "© OpenStreetMap-Mitwirkende"
        val tp = text(22f, Color.parseColor("#333333"))
        val tw = tp.measureText(attr)
        fill.color = Color.argb(210, 255, 255, 255)
        canvas.drawRoundRect(w - pad - tw - 20 * s, bottomCardTop - 50 * s, w - pad, bottomCardTop - 14 * s, 8 * s, 8 * s, fill)
        canvas.drawText(attr, w - pad - tw - 10 * s, bottomCardTop - 24 * s, tp)

        // Unterer Kasten: Hoehenprofil und Kennzahlen
        canvas.drawRoundRect(pad, bottomCardTop, w - pad, h - pad, radius, radius, card)
        var y = bottomCardTop + 30 * s
        if (hasAltitude) {
            drawProfile(canvas, 1f, pad + 36 * s, y + 30 * s, w - 2 * pad - 72 * s, 70 * s, labelColor = dim)
            y += 120 * s
        }
        val stats = listOf(
            "Fahrzeit" to formatDuration(r.movingTimeMs),
            "Ø Tempo" to "${formatDecimal(r.avgSpeedKmh)} km/h",
            "Höhenmeter" to "${String.format(Locale.GERMANY, "%.0f", r.elevationGainM)} m",
            (r.avgHr?.let { "Ø Puls" to "$it bpm" } ?: ("Max. Tempo" to "${formatDecimal(r.maxSpeedKmh)} km/h")),
            (if (r.calories > 0) "Kalorien" to "${String.format(Locale.GERMANY, "%.0f", r.calories)} kcal" else "Ø Trittfrequenz" to (r.avgCadence?.let { "$it U/min" } ?: "–")),
            "Max. Tempo" to "${formatDecimal(r.maxSpeedKmh)} km/h",
        ).distinct()
        val colW = (w - 2 * pad - 72 * s) / 3
        stats.take(6).forEachIndexed { i, (label, value) ->
            val x = pad + 36 * s + (i % 3) * colW
            val rowY = y + (i / 3) * 100 * s
            canvas.drawText(label, x, rowY + 30 * s, text(26f, dim))
            canvas.drawText(value, x, rowY + 76 * s, text(42f, white, bold = true))
        }
        val foot = text(22f, dim)
        if (content.trimmedM > 0) {
            canvas.drawText("Start und Ende (je ${content.trimmedM} m) ausgeblendet", pad + 36 * s, h - pad - 22 * s, foot)
        }
        val brand = "Fahrrad-Computer"
        canvas.drawText(brand, w - pad - 36 * s - foot.measureText(brand), h - pad - 22 * s, foot)
    }

    private fun drawProfile(
        canvas: Canvas, progress: Float, left: Float, top: Float, width: Float, height: Float, labelColor: Int,
    ) {
        val alts = smoothAltitudes
        val present = alts.filterNotNull()
        val realLo = present.min()
        val realHi = present.max()
        // Mindestens 60 m Spanne zeigen - sonst wirkt eine flache Fahrt wie ein Gebirge.
        val (lo, hi) = Altitude.displayRange(realLo, realHi)
        val range = hi - lo
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
        canvas.drawText("Höhe ${String.format(Locale.GERMANY, "%.0f–%.0f", realLo, realHi)} m", left, top - 10 * s, text(24f, labelColor))
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
