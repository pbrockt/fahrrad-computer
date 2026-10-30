package com.pbrockt.fahrradcomputer.routes

import com.pbrockt.fahrradcomputer.recording.RideTracker
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import kotlin.math.cos
import kotlin.math.sqrt

data class RoutePoint(val lat: Double, val lon: Double, val ele: Double? = null)

/** Eine geplante Strecke, z. B. aus Komoot, Strava oder BRouter als GPX exportiert. */
data class Route(
    val id: String,
    val name: String,
    val points: List<RoutePoint>,
    /** Nachgeladenes Hoehenprofil fuer GPX-Dateien ohne Hoehe ([ElevationApi]). */
    val externalProfile: RouteProfile? = null,
) {

    /** Strecke vom Start bis zu jedem Punkt in Metern. */
    val cumulative: DoubleArray by lazy {
        DoubleArray(points.size).also { c ->
            for (i in 1 until points.size) {
                val a = points[i - 1]
                val b = points[i]
                c[i] = c[i - 1] + RideTracker.distanceMeters(a.lat, a.lon, b.lat, b.lon)
            }
        }
    }

    val lengthM: Double get() = cumulative.lastOrNull() ?: 0.0

    /** Hoehenprofil aus der Datei, sonst das nachgeladene; null = (noch) keins. */
    val profile: RouteProfile? by lazy {
        externalProfile ?: RouteProfile.fromPoints(cumulative, points.map { it.ele })
    }

    /** Die Strecke rueckwaerts als GPX-Text - fuer "Route umkehren". */
    fun reversedGpx(name: String): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<gpx version=\"1.1\" creator=\"Fahrrad-Computer\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        append("<trk><name>").append(name.replace("&", "&amp;").replace("<", "&lt;")).append("</name><trkseg>\n")
        for (p in points.asReversed()) {
            append("<trkpt lat=\"").append(p.lat).append("\" lon=\"").append(p.lon).append("\">")
            p.ele?.let { append("<ele>").append(it).append("</ele>") }
            append("</trkpt>\n")
        }
        append("</trkseg></trk>\n</gpx>\n")
    }
}

/**
 * Liest GPX-Dateien beliebiger Herkunft: Tracks (<trkpt>) oder - falls keine da sind -
 * Routen (<rtept>). Mehrere Track-Segmente werden aneinandergehaengt.
 */
object GpxRouteParser {

    fun parse(input: InputStream, id: String, fallbackName: String): Route? {
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }.newPullParser()
        parser.setInput(input, null)

        val track = mutableListOf<RoutePoint>()
        val route = mutableListOf<RoutePoint>()
        var name: String? = null
        var current: MutableList<RoutePoint>? = null
        var lat = 0.0
        var lon = 0.0
        var ele: Double? = null
        var inPoint = false
        val path = ArrayDeque<String>()

        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name.substringAfter(':')
                    path.addLast(tag)
                    if (tag == "trkpt" || tag == "rtept") {
                        lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: Double.NaN
                        lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: Double.NaN
                        ele = null
                        inPoint = true
                        current = if (tag == "trkpt") track else route
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim().orEmpty()
                    if (text.isEmpty()) continue
                    val tag = path.lastOrNull()
                    if (inPoint && tag == "ele") ele = text.toDoubleOrNull()
                    // Der erste Name ausserhalb eines Punktes (metadata, trk oder rte) ist der Routenname.
                    if (!inPoint && tag == "name" && name == null) name = text
                }
                XmlPullParser.END_TAG -> {
                    val tag = path.removeLastOrNull()
                    if ((tag == "trkpt" || tag == "rtept") && inPoint) {
                        if (!lat.isNaN() && !lon.isNaN()) current?.add(RoutePoint(lat, lon, ele))
                        inPoint = false
                    }
                }
                XmlPullParser.END_DOCUMENT -> break
            }
        }
        val points = track.ifEmpty { route }
        if (points.size < 2) return null
        return Route(id, name?.takeIf { it.isNotBlank() } ?: fallbackName, points)
    }
}

/** Wo man gerade auf der Route ist. */
data class RouteProgress(
    /** Abstand zur Route; gross = falsch abgebogen. */
    val offRouteM: Double,
    val doneM: Double,
    val remainingM: Double,
) {
    val offRoute: Boolean get() = offRouteM > OFF_ROUTE_M

    companion object {
        const val OFF_ROUTE_M = 50.0
    }
}

/**
 * Ordnet die aktuelle Position der Route zu.
 *
 * Gesucht wird zuerst nur in der Naehe der letzten Zuordnung - sonst springt man bei
 * Rundkursen oder Hin- und Rueckweg auf derselben Strasse ploetzlich ans Ende der Route.
 * Liegt dort nichts Passendes, wird die ganze Route abgesucht (z. B. nach einer Abkuerzung).
 */
class RouteTracker(private val route: Route) {

    private var lastSegment: Int? = null

    fun update(lat: Double, lon: Double): RouteProgress {
        val pts = route.points
        if (pts.size < 2) {
            val d = pts.firstOrNull()?.let { RideTracker.distanceMeters(lat, lon, it.lat, it.lon) } ?: 0.0
            return RouteProgress(d, 0.0, d)
        }
        val last = lastSegment
        var best = if (last != null) {
            nearest(lat, lon, (last - BACK_WINDOW).coerceAtLeast(0), (last + AHEAD_WINDOW).coerceAtMost(pts.size - 2))
        } else null
        if (best == null || best.distance > RouteProgress.OFF_ROUTE_M * 2) {
            val global = nearest(lat, lon, 0, pts.size - 2)
            if (best == null || global.distance < best.distance) best = global
        }
        lastSegment = best.segment
        val segLen = route.cumulative[best.segment + 1] - route.cumulative[best.segment]
        val done = route.cumulative[best.segment] + best.t * segLen
        return RouteProgress(best.distance, done, (route.lengthM - done).coerceAtLeast(0.0))
    }

    private data class Hit(val segment: Int, val t: Double, val distance: Double)

    /** Naechster Punkt auf den Segmenten [from]..[to] - in einer lokalen Ebene in Metern gerechnet. */
    private fun nearest(lat: Double, lon: Double, from: Int, to: Int): Hit {
        val mPerDegLat = 111_320.0
        val mPerDegLon = 111_320.0 * cos(Math.toRadians(lat))
        var best = Hit(from, 0.0, Double.MAX_VALUE)
        for (i in from..to) {
            val a = route.points[i]
            val b = route.points[i + 1]
            val ax = (a.lon - lon) * mPerDegLon
            val ay = (a.lat - lat) * mPerDegLat
            val bx = (b.lon - lon) * mPerDegLon
            val by = (b.lat - lat) * mPerDegLat
            val dx = bx - ax
            val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
            val px = ax + t * dx
            val py = ay + t * dy
            val d = sqrt(px * px + py * py)
            if (d < best.distance) best = Hit(i, t, d)
        }
        return best
    }

    companion object {
        private const val BACK_WINDOW = 20
        private const val AHEAD_WINDOW = 400
    }
}
