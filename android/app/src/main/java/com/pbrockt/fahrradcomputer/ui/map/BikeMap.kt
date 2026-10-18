package com.pbrockt.fahrradcomputer.ui.map

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.pbrockt.fahrradcomputer.BuildConfig
import android.annotation.SuppressLint
import android.view.MotionEvent
import com.pbrockt.fahrradcomputer.recording.ColorRun
import com.pbrockt.fahrradcomputer.recording.TrackPoint
import com.pbrockt.fahrradcomputer.routes.RoutePoint
import org.mapsforge.core.graphics.Cap
import org.mapsforge.core.graphics.Join
import org.mapsforge.core.graphics.Style
import org.mapsforge.core.model.BoundingBox
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.model.MapPosition
import org.mapsforge.core.model.Rotation
import org.mapsforge.core.util.LatLongUtils
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.datastore.MultiMapDataStore
import org.mapsforge.map.layer.download.TileDownloadLayer
import org.mapsforge.map.layer.download.tilesource.OpenStreetMapMapnik
import org.mapsforge.map.layer.overlay.FixedPixelCircle
import org.mapsforge.map.layer.overlay.Polyline
import org.mapsforge.map.layer.renderer.TileRendererLayer
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes
import java.io.File

/** Gibt es ueberhaupt eine Karte zu zeigen? Sonst zeigen die Aufrufer die Streckenskizze. */
fun mapAvailable(offlineMaps: List<File>, online: Boolean) = offlineMaps.isNotEmpty() || online

/**
 * Karte mit Route (lila), gefahrener Strecke (blau) und eigener Position.
 *
 * - Mit Offline-Karten: Mapsforge zeichnet sie auf dem Handy, im Fahrrad-Stil "Biker".
 * - Ohne: die Kartenbilder von openstreetmap.org (braucht Internet).
 *
 * [cacheId] trennt die Kachel-Zwischenspeicher, wenn mehrere Karten gleichzeitig leben
 * (Dashboard-Seiten, Detailansicht).
 */
@Composable
fun BikeMap(
    offlineMaps: List<File>,
    online: Boolean,
    cacheId: String,
    modifier: Modifier = Modifier,
    route: List<RoutePoint>? = null,
    track: List<TrackPoint> = emptyList(),
    /** Eingefaerbte Strecke (Tempo/Puls): Stuecke von [track] mit Farbe (ARGB). Ersetzt die blaue Linie. */
    trackColors: List<Pair<ColorRun, Int>>? = null,
    position: TrackPoint? = null,
    follow: Boolean = false,
    /** Fahrtrichtung in Grad, wenn die Karte in Fahrtrichtung gedreht werden soll; null = Norden oben. */
    headingUp: Float? = null,
    /** Wird gerufen, sobald der Nutzer die Karte verschiebt - dann soll sie nicht mehr folgen. */
    onUserPan: (() -> Unit)? = null,
    controls: Boolean = false,
) {
    // Aendern sich die Kartendateien (Download fertig), wird die Karte neu aufgebaut.
    key(offlineMaps, online) {
        val context = LocalContext.current
        val holder = remember { MapHolder(context, offlineMaps, cacheId, controls) }
        DisposableEffect(holder) { onDispose { holder.destroy() } }
        Box(modifier) {
            AndroidView(
                factory = { holder.mapView },
                update = {
                    holder.onPan = onUserPan
                    holder.update(route, track, trackColors, position, follow, headingUp)
                },
            )
            // Pflichtangabe der OSM-Lizenz.
            Text(
                "© OpenStreetMap-Mitwirkende",
                fontSize = 9.sp,
                color = Color(0xFF333333),
                modifier = Modifier.align(Alignment.BottomEnd)
                    .background(Color(0xB3FFFFFF)).padding(horizontal = 3.dp),
            )
        }
    }
}

@SuppressLint("ClickableViewAccessibility") // nur Beobachten, kein eigener Klick
private class MapHolder(context: Context, maps: List<File>, cacheId: String, controls: Boolean) {

    val mapView = MapView(context).apply {
        isClickable = true
        mapScaleBar.isVisible = controls
        setBuiltInZoomControls(controls)
        setZoomLevelMin(6)
        setZoomLevelMax(19)
    }

    private val tileCache = AndroidUtil.createTileCache(
        context, cacheId, mapView.model.displayModel.tileSize, 1f, mapView.model.frameBufferModel.overdrawFactor,
    )
    private var download: TileDownloadLayer? = null
    private val routeLine = Polyline(stroke(ROUTE_COLOR, 12f), AndroidGraphicFactory.INSTANCE)
    private val trackLine = Polyline(stroke(TRACK_COLOR, 8f), AndroidGraphicFactory.INSTANCE)
    private val marker = FixedPixelCircle(
        LatLong(0.0, 0.0), 10f,
        AndroidGraphicFactory.INSTANCE.createPaint().apply { setColor(0xFFFFFFFF.toInt()); setStyle(Style.FILL) },
        stroke(TRACK_COLOR, 6f),
    )

    private var lastRoute: List<RoutePoint>? = null
    private var lastTrack: List<TrackPoint>? = null
    private var lastColors: List<Pair<ColorRun, Int>>? = null
    private val colorLines = mutableListOf<Polyline>()
    private var fitted = false
    private var rotation = 0f

    var onPan: (() -> Unit)? = null

    init {
        val layers = mapView.layerManager.layers
        if (maps.isNotEmpty()) {
            val store = MultiMapDataStore(MultiMapDataStore.DataPolicy.RETURN_ALL)
            maps.forEach { store.addMapDataStore(MapFile(it), false, false) }
            val layer = TileRendererLayer(tileCache, store, mapView.model.mapViewPosition, AndroidGraphicFactory.INSTANCE)
            layer.setXmlRenderTheme(MapsforgeThemes.BIKER)
            layers.add(layer)
        } else {
            // Die OSM-Nutzungsregeln verlangen eine erkennbare App-Kennung.
            val source = OpenStreetMapMapnik.INSTANCE.apply {
                userAgent = "Fahrrad-Computer/${BuildConfig.VERSION_NAME} (github.com/pbrockt/fahrrad-computer)"
            }
            download = TileDownloadLayer(tileCache, mapView.model.mapViewPosition, source, AndroidGraphicFactory.INSTANCE)
                .also { layers.add(it); it.onResume() }
        }
        layers.add(routeLine)
        layers.add(trackLine)
        layers.add(marker)
        marker.isVisible = false
        // Startansicht: Rheinland-Pfalz, bis Route oder Position bekannt sind.
        mapView.setCenter(LatLong(49.95, 7.3))
        mapView.setZoomLevel(8)
        // Verschieben erkennen, ohne der Karte die Bewegung wegzunehmen (false = weiterreichen).
        mapView.setOnTouchListener { _, e ->
            if (e.actionMasked == MotionEvent.ACTION_MOVE) onPan?.invoke()
            false
        }
    }

    fun update(
        route: List<RoutePoint>?,
        track: List<TrackPoint>,
        colors: List<Pair<ColorRun, Int>>?,
        position: TrackPoint?,
        follow: Boolean,
        headingUp: Float?,
    ) {
        if (route !== lastRoute) {
            lastRoute = route
            routeLine.setPoints(route?.map { LatLong(it.lat, it.lon) }.orEmpty())
            fitted = false
        }
        if (track !== lastTrack || colors !== lastColors) {
            lastTrack = track
            lastColors = colors
            val layers = mapView.layerManager.layers
            colorLines.forEach { layers.remove(it) }
            colorLines.clear()
            if (colors.isNullOrEmpty()) {
                trackLine.setPoints(track.map { LatLong(it.lat, it.lon) })
            } else {
                trackLine.setPoints(emptyList())
                // Farbige Stuecke unter die Positionsmarke legen.
                val at = layers.indexOf(marker)
                colors.forEach { (run, color) ->
                    val line = Polyline(stroke(color, 9f), AndroidGraphicFactory.INSTANCE)
                    line.setPoints((run.from..run.to.coerceAtMost(track.lastIndex)).map { LatLong(track[it].lat, track[it].lon) })
                    layers.add(at.coerceAtLeast(0), line)
                    colorLines += line
                }
            }
        }
        if (position != null) {
            val here = LatLong(position.lat, position.lon)
            marker.setLatLong(here)
            marker.isVisible = true
            if (follow) {
                if (!fitted) mapView.setZoomLevel(FOLLOW_ZOOM)
                mapView.setCenter(here)
                fitted = true
            }
        } else {
            marker.isVisible = false
        }
        // In Fahrtrichtung drehen (Karte gegen den Uhrzeigersinn um die Richtung), sonst Norden oben.
        val wanted = if (follow && headingUp != null) -headingUp else 0f
        if (kotlin.math.abs(wanted - rotation) > 2f && mapView.width > 0) {
            rotation = wanted
            mapView.rotate(Rotation(wanted, mapView.width / 2f, mapView.height / 2f))
        }
        if (!fitted) {
            val pts = route?.map { LatLong(it.lat, it.lon) } ?: track.map { LatLong(it.lat, it.lon) }
            if (pts.size >= 2) fitted = fit(pts)
        }
        mapView.layerManager.redrawLayers()
    }

    /** Auf die Strecke zoomen. Klappt erst, wenn die Karte ihre Groesse kennt. */
    private fun fit(points: List<LatLong>): Boolean {
        val dim = mapView.model.mapViewDimension.dimension
        if (dim == null) {
            mapView.post { if (!fitted) fitted = fit(points) }
            return false
        }
        val box = BoundingBox(points)
        // Begrenzen: Eine Strecke, die nur aus einem Punkt besteht, ergaebe "unendlich nah".
        val zoom = LatLongUtils.zoomForBounds(dim, box, mapView.model.displayModel.tileSize).coerceIn(6, 17).toByte()
        mapView.model.mapViewPosition.setMapPosition(MapPosition(box.centerPoint, zoom))
        return true
    }

    fun destroy() {
        download?.onPause()
        mapView.destroyAll()
        AndroidGraphicFactory.clearResourceMemoryCache()
    }

    private fun stroke(color: Int, width: Float) = AndroidGraphicFactory.INSTANCE.createPaint().apply {
        setColor(color)
        setStrokeWidth(width)
        setStyle(Style.STROKE)
        setStrokeCap(Cap.ROUND)
        setStrokeJoin(Join.ROUND)
    }

    companion object {
        private const val ROUTE_COLOR = 0xCC8E44AD.toInt()
        private const val TRACK_COLOR = 0xFF1C7FB0.toInt()
        private const val FOLLOW_ZOOM: Byte = 16
    }
}
