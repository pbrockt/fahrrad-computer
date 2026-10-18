package com.pbrockt.fahrradcomputer.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import androidx.core.content.FileProvider
import com.pbrockt.fahrradcomputer.recording.GpxWriter
import com.pbrockt.fahrradcomputer.recording.TrackPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Erzeugt Bild, Video oder GPX zum Teilen - immer aus der bereits gekuerzten Strecke. */
object ShareExporter {

    private const val FPS = 30
    private const val ANIMATION_S = 6
    private const val HOLD_S = 1.5

    private fun dir(context: Context) = File(context.cacheDir, "teilen").apply {
        // Alte Dateien weg - geteilt ist geteilt, der Cache soll nicht wachsen.
        listFiles()?.forEach { it.delete() }
        mkdirs()
    }

    /**
     * Bild zum Teilen. Mit [withMap] liegt die Strecke auf einer OpenStreetMap-Karte -
     * dafuer werden die passenden Kacheln geladen (Internet noetig). Klappt das nicht,
     * gibt es eine Exception mit verstaendlicher Meldung; der Dialog bietet dann "ohne Karte".
     */
    suspend fun image(context: Context, content: ShareContent, withMap: Boolean = false): File = withContext(Dispatchers.Default) {
        val w = 1080
        val h = 1350
        val renderer = ShareRenderer(content, w, h)
        val map = if (withMap) OsmTiles.background(context, content.points, w, h, renderer.posterRouteArea) else null
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        renderer.draw(Canvas(bmp), null, map)
        map?.bitmap?.recycle()
        val file = File(dir(context), "radfahrt-${content.summary.id}.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        file
    }

    suspend fun gpx(context: Context, id: String, points: List<TrackPoint>): File = withContext(Dispatchers.IO) {
        val file = File(dir(context), "radfahrt-$id.gpx")
        file.bufferedWriter().use { GpxWriter.write(it, "Radfahrt $id", points) }
        file
    }

    /**
     * Video: der Punkt faehrt die Strecke in [ANIMATION_S] Sekunden ab, danach bleibt das
     * fertige Bild kurz stehen. Gezeichnet wird direkt in die Eingabe-Surface des
     * H.264-Encoders, das MP4 baut der MediaMuxer.
     */
    suspend fun video(context: Context, content: ShareContent, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.Default) {
            val file = File(dir(context), "radfahrt-${content.summary.id}.mp4")
            // 1080 x 1080 koennen fast alle Encoder; falls nicht, kleiner versuchen.
            runCatching { encode(file, content, 1080, onProgress) }
                .recoverCatching { encode(file, content, 720, onProgress) }
                .getOrThrow()
            file
        }

    private fun encode(file: File, content: ShareContent, size: Int, onProgress: (Float) -> Unit) {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, size, size).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 8_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        var muxer: MediaMuxer? = null
        val surface = try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.createInputSurface()
        } catch (e: Exception) {
            codec.release()
            throw e
        }
        try {
            codec.start()
            muxer = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val renderer = ShareRenderer(content, size, size)
            val drain = Drainer(codec, muxer)
            val animFrames = ANIMATION_S * FPS
            val total = animFrames + (HOLD_S * FPS).toInt()
            for (f in 0 until total) {
                val canvas = surface.lockHardwareCanvas()
                try {
                    renderer.draw(canvas, (f.toFloat() / animFrames).coerceAtMost(1f))
                } finally {
                    surface.unlockCanvasAndPost(canvas)
                }
                drain.drain(endOfStream = false)
                onProgress((f + 1).toFloat() / total)
            }
            codec.signalEndOfInputStream()
            drain.drain(endOfStream = true)
            muxer.stop()
        } finally {
            runCatching { codec.stop() }
            codec.release()
            surface.release()
            runCatching { muxer?.release() }
        }
    }

    /**
     * Holt fertige Bilder aus dem Encoder und schreibt sie ins MP4.
     *
     * Beim Zeichnen ueber eine Surface stempelt Android die Bilder mit der Uhrzeit des
     * Zeichnens - wir zeichnen aber schneller als in Echtzeit. Deshalb bekommt jedes Bild
     * hier seinen Zeitstempel aus der laufenden Nummer: Bild n liegt bei n / FPS Sekunden.
     */
    private class Drainer(private val codec: MediaCodec, private val muxer: MediaMuxer) {
        private val info = MediaCodec.BufferInfo()
        private var track = -1
        private var frames = 0L

        fun drain(endOfStream: Boolean) {
            var idle = 0
            while (true) {
                val index = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (!endOfStream) return
                        // Sicherheitsnetz: kommt das Ende nie, nicht ewig haengen.
                        if (++idle > 300) return
                    }
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                    }
                    index >= 0 -> {
                        val buf = codec.getOutputBuffer(index)
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (buf != null && info.size > 0 && track >= 0) {
                            info.presentationTimeUs = frames * 1_000_000L / FPS
                            frames++
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            muxer.writeSampleData(track, buf, info)
                        }
                        codec.releaseOutputBuffer(index, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    }
                }
            }
        }
    }

    fun share(context: Context, file: File, mime: String, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
