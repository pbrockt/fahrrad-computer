package com.pbrockt.fahrradcomputer.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.pbrockt.fahrradcomputer.BuildConfig
import com.pbrockt.fahrradcomputer.recording.TrackPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Kartenhintergrund aus den Standard-Kacheln von openstreetmap.org.
 *
 * Nach den OSM-Nutzungsregeln: erkennbare App-Kennung (User-Agent), keine Massen-
 * Downloads (pro Bild nur die 10-20 noetigen Kacheln, nacheinander) und ein lokaler
 * Zwischenspeicher, damit dieselben Kacheln nicht ständig neu geladen werden.
 */
object OsmTiles {

    /** Kacheln doppelt so gross zeichnen - sonst ist die Beschriftung auf dem Handy winzig. */
    private const val SCALE = 2.0
    private const val CACHE_DAYS = 14L

    /**
     * Kartenbild [width] x [height]; die Strecke liegt mittig im Bereich [routeArea]
     * (links, oben, rechts, unten in Bildpixeln).
     */
    suspend fun background(context: Context, points: List<TrackPoint>, width: Int, height: Int, routeArea: FloatArray): MapBackground =
        withContext(Dispatchers.IO) {
            val (l, t, r, b) = routeArea.map { it.toDouble() }
            val window = OsmMath.fitIn(points, width.toDouble(), height.toDouble(), l, t, r, b, SCALE)
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.parseColor("#E5E3DF")) // OSM-Hintergrundgrau, falls eine Kachel fehlt
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            val n = 1 shl window.zoom
            var loaded = 0
            val tiles = window.tiles()
            for ((x, y) in tiles) {
                val tile = load(context, window.zoom, ((x % n) + n) % n, y) ?: continue
                val left = ((x * OsmMath.TILE - window.originX) * SCALE).toFloat()
                val top = ((y * OsmMath.TILE - window.originY) * SCALE).toFloat()
                val size = (OsmMath.TILE * SCALE).toFloat()
                canvas.drawBitmap(tile, Rect(0, 0, tile.width, tile.height), RectF(left, top, left + size, top + size), paint)
                tile.recycle()
                loaded++
            }
            if (loaded == 0) {
                bmp.recycle()
                throw IOException("Karte konnte nicht geladen werden – keine Internetverbindung?")
            }
            MapBackground(bmp, window, SCALE.toFloat())
        }

    private fun load(context: Context, z: Int, x: Int, y: Int): Bitmap? {
        val file = File(context.cacheDir, "osm-kacheln/$z/$x/$y.png")
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < CACHE_DAYS * 86_400_000L
        if (!fresh) runCatching { download(z, x, y, file) }
        return if (file.exists()) BitmapFactory.decodeFile(file.path) else null
    }

    private fun download(z: Int, x: Int, y: Int, target: File) {
        val conn = (URL("https://tile.openstreetmap.org/$z/$x/$y.png").openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "Fahrrad-Computer/${BuildConfig.VERSION_NAME} (github.com/pbrockt/fahrrad-computer)")
        }
        try {
            if (conn.responseCode != 200) return
            target.parentFile?.mkdirs()
            val tmp = File(target.path + ".tmp")
            conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            tmp.renameTo(target)
        } finally {
            conn.disconnect()
        }
    }
}
