package com.pbrockt.fahrradcomputer.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pbrockt.fahrradcomputer.coach.PlannedSession
import com.pbrockt.fahrradcomputer.coach.SessionKind
import com.pbrockt.fahrradcomputer.data.HrZones
import com.pbrockt.fahrradcomputer.data.Zones
import com.pbrockt.fahrradcomputer.recording.Climb
import com.pbrockt.fahrradcomputer.recording.RidePhase
import com.pbrockt.fahrradcomputer.recording.RideStats
import com.pbrockt.fahrradcomputer.recording.TrackPoint
import com.pbrockt.fahrradcomputer.routes.Guidance
import com.pbrockt.fahrradcomputer.routes.Route
import com.pbrockt.fahrradcomputer.routes.RouteProfile
import com.pbrockt.fahrradcomputer.routes.RouteProgress
import com.pbrockt.fahrradcomputer.routes.TurnAnnouncer
import com.pbrockt.fahrradcomputer.sensors.SensorReading
import com.pbrockt.fahrradcomputer.ui.AppColors
import com.pbrockt.fahrradcomputer.ui.Condensed
import com.pbrockt.fahrradcomputer.ui.ElevationProfileChart
import com.pbrockt.fahrradcomputer.ui.RouteSketch
import com.pbrockt.fahrradcomputer.ui.TurnArrow
import com.pbrockt.fahrradcomputer.ui.formatDecimal
import com.pbrockt.fahrradcomputer.ui.formatDuration
import com.pbrockt.fahrradcomputer.ui.map.BikeMap
import com.pbrockt.fahrradcomputer.ui.map.mapAvailable
import com.pbrockt.fahrradcomputer.weather.Weather
import com.pbrockt.fahrradcomputer.weather.Wind
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** Bedienknoepfe fuer die Kachel "Start / Pause / Stopp". */
class RideControls(
    val onStart: () -> Unit,
    val onPause: () -> Unit,
    val onResume: () -> Unit,
    val onStop: () -> Unit,
)

/** Alles, was eine Kachel zum Zeichnen braucht. */
class TileData(
    val stats: RideStats,
    val hr: SensorReading,
    val cadence: SensorReading,
    val power: SensorReading,
    val zones: HrZones,
    val now: Long,
    val route: List<TrackPoint>,
    val phase: RidePhase,
    val lastPoint: TrackPoint? = null,
    val activeRoute: Route? = null,
    val progress: RouteProgress? = null,
    val guidance: Guidance? = null,
    /** Geplante Einheit fuer heute aus dem Trainingsplan. */
    val todaySession: PlannedSession? = null,
    val controls: RideControls? = null,
    /** Karte: Fahrtrichtung in Grad, Ausrichtung und Umschalter. */
    val heading: Float? = null,
    val mapHeadingUp: Boolean = false,
    val onToggleMapOrientation: () -> Unit = {},
    val offlineMaps: List<File> = emptyList(),
    val mapOnline: Boolean = false,
    /** Im Bearbeiten-Modus keine echte Karte - sie wuerde die Fingertipps abfangen. */
    val interactive: Boolean = true,
    val powerZones: Zones = Zones.power(200.0),
    val cadenceZones: Zones = Zones.DEFAULT_CADENCE,
    val speedZones: Zones = Zones.DEFAULT_SPEED,
    val climb: Climb = Climb(),
    val weather: Weather? = null,
) {
    // Messwerte nur, solange sie frisch sind - sonst zeigt die Kachel "--" statt eines alten Werts.
    val bpm: Int? get() = hr.fresh(now)
    val rpm: Int? get() = cadence.fresh(now)
    val watts: Int? get() = power.fresh(now)

    /** Auf der Route (nicht abseits) und die Route hat ein Hoehenprofil. */
    val onRouteProfile: RouteProfile?
        get() = activeRoute?.profile?.takeIf { progress != null && progress.offRoute.not() }

    /** Steigung: auf der Route aus deren Profil (ruhiger), sonst aus der eigenen GPS-Hoehe. */
    val gradePct: Double?
        get() = onRouteProfile?.gradeAt(progress!!.doneM) ?: climb.gradePct
}

@Composable
fun Tile(type: TileType, d: TileData, modifier: Modifier, mapId: String = "dash") {
    val s = d.stats
    val c = AppColors
    val idle = d.phase == RidePhase.IDLE
    when (type) {
        TileType.SPEED -> ValueTile(
            "Tempo", if (idle) "--" else formatDecimal(s.speedKmh), modifier, "km/h", c.Speed,
            zone = if (idle) null else d.speedZones.position(s.speedKmh),
        )
        TileType.HEART_RATE -> {
            val bpm = d.bpm
            if (bpm == null) ValueTile("Puls", "--", modifier, "bpm", c.Stop)
            else {
                val zone = d.zones.zoneOf(bpm)
                ValueTile("Puls · Z$zone", bpm.toString(), modifier, "bpm", fill = c.zone(zone))
            }
        }
        TileType.CADENCE -> {
            val rpm = d.rpm
            ValueTile("Trittfrequenz", rpm?.toString() ?: "--", modifier, "U/min", c.Cadence,
                zone = rpm?.let { d.cadenceZones.position(it) })
        }
        TileType.POWER -> {
            val w = d.watts
            if (w == null) ValueTile("Leistung", "--", modifier, "W", c.Go)
            else {
                val zone = d.powerZones.zoneOf(w)
                ValueTile("Leistung · Z$zone", w.toString(), modifier, "W", fill = c.zone(zone))
            }
        }
        TileType.CALORIES -> ValueTile(
            "Kalorien · ${s.calorieSource.label}",
            if (idle && s.calories == 0.0) "--" else String.format(Locale.GERMANY, "%.0f", s.calories),
            modifier, "kcal", c.Calories,
        )
        TileType.AVG_CADENCE -> ValueTile("Ø Trittfrequenz", s.avgCadence?.toString() ?: "--", modifier, "U/min", c.Cadence)
        TileType.AVG_POWER -> ValueTile(
            "Ø Leistung", s.avgPower?.toString() ?: "--", modifier, "W",
            s.avgPower?.let { c.zone(d.powerZones.zoneOf(it)) } ?: c.Go,
        )
        TileType.DISTANCE -> ValueTile("Distanz", formatDecimal(s.distanceM / 1000, 2), modifier, "km", c.Distance)
        TileType.MOVING_TIME -> ValueTile("Fahrzeit", formatDuration(s.movingTimeMs), modifier)
        TileType.AVG_SPEED -> ValueTile("Ø Tempo", formatDecimal(s.avgSpeedKmh), modifier, "km/h", c.Speed)
        TileType.MAX_SPEED -> ValueTile("Max. Tempo", formatDecimal(s.maxSpeedKmh), modifier, "km/h", c.Speed)
        TileType.AVG_HR -> HrValueTile("Ø Puls", s.avgHr, d.zones, modifier)
        TileType.MAX_HR -> HrValueTile("Max. Puls", s.maxHr, d.zones, modifier)
        TileType.ALTITUDE -> ValueTile(
            "Höhe", s.altitude?.let { String.format(Locale.GERMANY, "%.0f", it) } ?: "--", modifier, "m", c.Altitude,
        )
        TileType.CLOCK -> ValueTile(
            "Uhrzeit", CLOCK.format(Instant.ofEpochMilli(d.now).atZone(ZoneId.systemDefault())), modifier,
        )
        TileType.GRADE -> {
            val g = if (idle && d.onRouteProfile == null) null else d.gradePct
            ValueTile(
                "Steigung", g?.let { String.format(Locale.GERMANY, "%.1f", it) } ?: "--", modifier, "%",
                g?.takeIf { abs(it) >= 2 }?.let { Color(RouteProfile.GRADE_COLORS[RouteProfile.gradeLevel(it)]) } ?: c.Grade,
            )
        }
        TileType.ASCENT -> ValueTile(
            "Höhenmeter", if (idle) "--" else d.climb.ascentM.roundToInt().toString(), modifier, "m", c.Distance,
        )
        TileType.ROUTE_ASCENT_LEFT -> {
            val profile = d.activeRoute?.profile
            val left = profile?.let { p -> p.ascentRemainingM(d.progress?.doneM ?: 0.0) }
            ValueTile(
                if (d.activeRoute == null) "Rest Hm · keine Route" else "Rest Höhenmeter",
                left?.roundToInt()?.toString() ?: "--", modifier, "m", c.Grade,
            )
        }
        TileType.ELEVATION_PROFILE -> ElevationTile(d, modifier)
        TileType.HR_CHART -> HrChartTile(d, modifier)
        TileType.WIND -> WindTile(d, modifier)
        TileType.TEMPERATURE -> {
            val w = d.weather
            ValueTile(
                if (w == null) "Temperatur" else "Temperatur · ${w.description}",
                w?.let { String.format(Locale.GERMANY, "%.0f°", it.temperatureC) } ?: "--", modifier, "C",
            )
        }
        TileType.ROUTE -> when {
            !d.interactive -> ValueTile("Karte", "🗺", modifier)
            mapAvailable(d.offlineMaps, d.mapOnline) -> MapTile(d, modifier, mapId)
            else -> RouteTile(d.route, modifier)
        }
        TileType.TURN -> TurnTile(d, modifier)
        TileType.CONTROLS -> {
            val ctl = d.controls
            if (ctl == null || !d.interactive) ValueTile("Start / Pause / Stopp", "▶", modifier)
            else ControlsTile(d.phase, ctl, modifier)
        }
        TileType.TRAINING_GOAL -> {
            val t = d.todaySession
            when {
                t == null -> ValueTile("Training · kein Plan", "--", modifier)
                t.kind == SessionKind.REST -> ValueTile("Heute", "Ruhetag", modifier)
                else -> {
                    val leftMin = t.durationMin - (s.movingTimeMs / 60_000).toInt()
                    ValueTile(
                        if (leftMin > 0) "${t.title} · noch $leftMin min" else "${t.title} · geschafft ✓",
                        "Z${t.targetZone}", modifier, fill = c.zone(t.targetZone),
                    )
                }
            }
        }
        TileType.ROUTE_REMAINING -> {
            val p = d.progress
            when {
                d.activeRoute == null -> ValueTile("Rest · keine Route", "--", modifier, "km", c.Distance)
                p == null -> ValueTile("Rest", formatDecimal(d.activeRoute.lengthM / 1000), modifier, "km", c.Distance)
                p.offRoute -> ValueTile(
                    "Abseits ${String.format(Locale.GERMANY, "%.0f", p.offRouteM)} m", formatDecimal(p.remainingM / 1000),
                    modifier, "km", fill = c.Stop,
                )
                else -> ValueTile("Rest", formatDecimal(p.remainingM / 1000), modifier, "km", c.Distance)
            }
        }
    }
}

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")

/** Ø/max Puls: der Wert in der Farbe seiner Zone. */
@Composable
private fun HrValueTile(label: String, bpm: Int?, zones: HrZones, modifier: Modifier) {
    if (bpm == null) ValueTile(label, "--", modifier, "bpm", AppColors.Stop)
    else {
        val zone = zones.zoneOf(bpm)
        ValueTile("$label · Z$zone", bpm.toString(), modifier, "bpm", AppColors.zone(zone))
    }
}

/** Ueberschrift einer Kachel: Grossbuchstaben, schmal, in der Farbe der Messgroesse. */
@Composable
fun TileLabel(label: String, unit: String, color: Color, size: TextUnit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(
            label.uppercase(Locale.GERMANY),
            color = color,
            fontSize = size,
            fontFamily = Condensed,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.6.sp,
            maxLines = 1,
        )
        if (unit.isNotEmpty()) {
            Text(
                " " + unit.uppercase(Locale.GERMANY),
                color = color.copy(alpha = 0.6f),
                fontSize = size * 0.85f,
                fontFamily = Condensed,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

private val TIGHT = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
)

/**
 * Eine Kachel: oben mittig die Bezeichnung, darunter der Wert so gross
 * wie moeglich - beides in der Farbe der Messgroesse. Mit [fill] wird die ganze Kachel
 * eingefaerbt (Zonen), die Schrift ist dann weiss. [zone] (0..1) zeigt unten eine Zonenleiste.
 */
@Composable
fun ValueTile(
    label: String,
    value: String,
    modifier: Modifier,
    unit: String = "",
    color: Color = AppColors.Text,
    fill: Color? = null,
    zone: Float? = null,
) {
    val fg = if (fill != null) Color.White else color
    BoxWithConstraints(modifier.background(fill ?: AppColors.Bg)) {
        val labelSize = (maxHeight.value * 0.13f).coerceIn(11f, 18f).sp
        Column(
            Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TileLabel(label, unit, fg, labelSize)
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val density = LocalDensity.current
                // Barlow Condensed: Ziffern sind etwa halb so breit wie hoch.
                val byHeight = maxHeight * 0.95f
                val byWidth = maxWidth * 0.96f / (value.length.coerceAtLeast(2) * 0.5f)
                val size = with(density) { min(byHeight.toPx(), byWidth.toPx()).toSp() }
                Text(
                    value,
                    color = fg,
                    fontSize = size,
                    lineHeight = size,
                    fontFamily = Condensed,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    style = TIGHT,
                )
            }
            if (zone != null) ZoneBar(zone, fg, Modifier.fillMaxWidth(0.85f).height(10.dp))
        }
    }
}

/** Fuenf Zonenfarben nebeneinander, ein Dreieck markiert den aktuellen Wert. */
@Composable
fun ZoneBar(position: Float, marker: Color, modifier: Modifier) {
    val zones = AppColors.Zones
    Canvas(modifier) {
        val gap = 2.dp.toPx()
        val barH = size.height * 0.5f
        val segW = (size.width - gap * 4) / 5
        zones.forEachIndexed { i, col ->
            val x = i * (segW + gap)
            drawRect(col, Offset(x, size.height - barH), androidx.compose.ui.geometry.Size(segW, barH))
        }
        val mx = position.coerceIn(0f, 1f) * size.width
        val tri = size.height - barH
        drawPath(
            Path().apply {
                moveTo(mx - tri * 0.8f, 0f)
                lineTo(mx + tri * 0.8f, 0f)
                lineTo(mx, tri)
                close()
            },
            marker,
        )
    }
}

/** Hoehenprofil der aktiven Route mit Position; Knopf schaltet "ganze Route" / "naechste 5 km". */
@Composable
private fun ElevationTile(d: TileData, modifier: Modifier) {
    val profile = d.activeRoute?.profile
    var all by rememberSaveable { mutableStateOf(true) }
    BoxWithConstraints(modifier.background(AppColors.Bg)) {
        val labelSize = (maxHeight.value * 0.09f).coerceIn(11f, 16f).sp
        Column(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val title = when {
                d.activeRoute == null -> "Höhenprofil · keine Route"
                profile == null -> "Höhenprofil · wird geladen"
                else -> "Höhenprofil · ${profile.totalAscentM.roundToInt()} Hm"
            }
            TileLabel(title, "", AppColors.Text, labelSize)
            if (profile != null) {
                val pos = d.progress?.doneM
                val (from, to) = if (all || pos == null) 0.0 to profile.lengthM
                else (pos - 500).coerceAtLeast(0.0).let { f -> f to (f + 5_000).coerceAtMost(profile.lengthM) }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    ElevationProfileChart(profile, Modifier.fillMaxSize(), positionM = pos, fromM = from, toM = to)
                    if (d.interactive) {
                        Text(
                            if (all) "ALLE" else "5 KM",
                            color = Color.White,
                            fontFamily = Condensed,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            modifier = Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(5.dp))
                                .background(AppColors.Accent).clickable { all = !all }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            } else {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        if (d.activeRoute == null) "Route unter „Karten & Routen“ aktivieren."
                        else "Die GPX-Datei hat keine Höhen – sie werden geladen, sobald Internet da ist.",
                        color = AppColors.Dim, textAlign = TextAlign.Center, fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

/** Puls der letzten 10 Minuten vor farbigen Zonenbaendern. */
@Composable
private fun HrChartTile(d: TileData, modifier: Modifier) {
    val zoneColors = AppColors.Zones
    val line = AppColors.Text
    val bpm = d.bpm
    BoxWithConstraints(modifier.background(AppColors.Bg)) {
        val labelSize = (maxHeight.value * 0.11f).coerceIn(11f, 17f).sp
        Column(Modifier.fillMaxSize().padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            TileLabel(if (bpm != null) "Puls · $bpm" else "Pulsverlauf", "bpm", AppColors.Text, labelSize)
            val from = d.now - 10 * 60_000L
            val pts = d.route.filter { it.timeMs >= from && it.heartRate != null }
            val t = d.zones.thresholds
            // Darstellung von Z1-Mitte bis Z5: Grenzen plus etwas Rand.
            val lo = (t[0] - (t[1] - t[0])).toFloat()
            val hi = (t[3] + (t[3] - t[2])).toFloat()
            Canvas(Modifier.weight(1f).fillMaxWidth()) {
                val bounds = listOf(lo) + t.map { it.toFloat() } + hi
                fun y(v: Float) = size.height - (v.coerceIn(lo, hi) - lo) / (hi - lo) * size.height
                for (z in 0 until 5) {
                    val top = y(bounds[z + 1])
                    val bottom = y(bounds[z])
                    drawRect(zoneColors[z].copy(alpha = 0.35f), Offset(0f, top), androidx.compose.ui.geometry.Size(size.width, bottom - top))
                }
                if (pts.size >= 2) {
                    val span = 10 * 60_000f
                    for (i in 1 until pts.size) {
                        val a = pts[i - 1]
                        val b = pts[i]
                        drawLine(
                            line,
                            Offset((a.timeMs - from) / span * size.width, y(a.heartRate!!.toFloat())),
                            Offset((b.timeMs - from) / span * size.width, y(b.heartRate!!.toFloat())),
                            strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round,
                        )
                    }
                }
            }
        }
    }
}

/** Wind: Pfeil (wohin er weht, relativ zur Fahrtrichtung), Staerke und Gegen-/Rueckenwind. */
@Composable
private fun WindTile(d: TileData, modifier: Modifier) {
    val w = d.weather
    if (w == null) {
        ValueTile("Wind", "--", modifier, "km/h", AppColors.Speed)
        return
    }
    val heading = d.heading?.toDouble()?.takeIf { d.phase != RidePhase.IDLE }
    val color = when {
        heading == null -> AppColors.Speed
        Wind.headwindKmh(w, heading) > w.windKmh * 0.35 -> AppColors.Stop
        Wind.headwindKmh(w, heading) < -w.windKmh * 0.35 -> AppColors.Go
        else -> AppColors.Speed
    }
    BoxWithConstraints(modifier.background(AppColors.Bg)) {
        val labelSize = (maxHeight.value * 0.13f).coerceIn(11f, 18f).sp
        Column(Modifier.fillMaxSize().padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            TileLabel("Wind · ${Wind.label(w, heading)}", "km/h", color, labelSize)
            Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                WindArrow(Wind.arrowDeg(w, heading), color, Modifier.weight(1f).fillMaxHeight())
                BoxWithConstraints(Modifier.weight(1.4f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    val value = w.windKmh.roundToInt().toString()
                    val size = with(LocalDensity.current) { min((maxHeight * 0.9f).toPx(), (maxWidth * 0.9f / (value.length * 0.5f)).toPx()).toSp() }
                    Text(value, color = color, fontSize = size, lineHeight = size, fontFamily = Condensed, fontWeight = FontWeight.SemiBold, style = TIGHT)
                }
            }
            Text(
                "Böen ${w.gustKmh.roundToInt()} · aus ${Wind.compass(w.windFromDeg)}",
                color = AppColors.Dim, fontSize = labelSize * 0.85f, fontFamily = Condensed, maxLines = 1,
            )
        }
    }
}

/** Pfeil nach oben, gedreht um [deg]: 0 = Wind von hinten (schiebt), 180 = von vorn. */
@Composable
fun WindArrow(deg: Float, color: Color, modifier: Modifier) {
    Canvas(modifier.rotate(deg)) {
        val s = min(size.width, size.height)
        val cx = size.width / 2
        val cy = size.height / 2
        val path = Path().apply {
            moveTo(cx, cy - s * 0.42f)
            lineTo(cx + s * 0.28f, cy + s * 0.05f)
            lineTo(cx + s * 0.1f, cy + s * 0.05f)
            lineTo(cx + s * 0.1f, cy + s * 0.42f)
            lineTo(cx - s * 0.1f, cy + s * 0.42f)
            lineTo(cx - s * 0.1f, cy + s * 0.05f)
            lineTo(cx - s * 0.28f, cy + s * 0.05f)
            close()
        }
        drawPath(path, color)
    }
}

/** Karte mit Route, gefahrener Strecke und Position; folgt der Position. */
@Composable
private fun MapTile(d: TileData, modifier: Modifier, mapId: String) {
    // Folgt der Position, bis man die Karte verschiebt; der Knopf holt sie zurueck.
    var following by remember { mutableStateOf(true) }
    Box(modifier) {
        BikeMap(
            offlineMaps = d.offlineMaps,
            online = d.mapOnline,
            cacheId = mapId,
            modifier = Modifier.fillMaxSize(),
            route = d.activeRoute?.points,
            track = d.route,
            position = d.lastPoint,
            follow = following,
            headingUp = if (d.mapHeadingUp) d.heading else null,
            onUserPan = { following = false },
        )
        // Knopf: rotes Quadrat mit weissem Symbol.
        Box(
            Modifier.align(Alignment.BottomEnd).padding(8.dp).size(44.dp).clip(RoundedCornerShape(6.dp))
                .background(AppColors.Accent)
                .clickable { if (!following) following = true else d.onToggleMapOrientation() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                when {
                    !following -> Icons.Filled.MyLocation
                    d.mapHeadingUp -> Icons.Filled.Navigation
                    else -> Icons.Filled.Explore
                },
                contentDescription = when {
                    !following -> "Zur eigenen Position"
                    d.mapHeadingUp -> "Fahrtrichtung oben – tippen für Norden oben"
                    else -> "Norden oben – tippen für Fahrtrichtung"
                },
                tint = Color.White,
            )
        }
        val p = d.progress
        if (p != null) {
            Text(
                when {
                    p.offRoute -> "Abseits der Route: ${String.format(Locale.GERMANY, "%.0f", p.offRouteM)} m"
                    d.guidance != null -> d.guidance.text
                    else -> "noch ${formatDecimal(p.remainingM / 1000)} km"
                },
                color = Color.White,
                fontFamily = Condensed,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (p.offRoute) AppColors.Stop else AppColors.Go)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

/**
 * Naechste Abbiegung als Navi-Banner: Pfeil links, Entfernung gross.
 * Kurz vor der Abbiegung (unter 30 m) wird die Kachel gruen.
 */
@Composable
private fun TurnTile(d: TileData, modifier: Modifier) {
    val g = d.guidance
    val p = d.progress
    when {
        d.activeRoute == null -> ValueTile("Abbiegen · keine Route", "--", modifier)
        p?.offRoute == true -> ValueTile(
            "Abseits der Route", "${String.format(Locale.GERMANY, "%.0f", p.offRouteM)} m", modifier, fill = AppColors.Stop,
        )
        g == null -> ValueTile("Abbiegen · warte auf GPS", "--", modifier)
        else -> {
            val now = g.distanceM <= TurnAnnouncer.NOW_M
            val bg = if (now) AppColors.Go else AppColors.Bg
            val fg = if (now) Color.White else AppColors.Text
            BoxWithConstraints(modifier.background(bg).padding(6.dp)) {
                val density = LocalDensity.current
                val value = Guidance.formatMeters(g.distanceM)
                val size = with(density) {
                    min((maxHeight * 0.6f).toPx(), (maxWidth * 0.62f / (value.length * 0.5f)).toPx()).toSp()
                }
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    TurnArrow(g.turn?.angle, fg, Modifier.weight(1f).fillMaxHeight())
                    Column(Modifier.weight(1.6f)) {
                        Text(value, color = fg, fontSize = size, lineHeight = size, fontFamily = Condensed, fontWeight = FontWeight.SemiBold, maxLines = 1, style = TIGHT)
                        Text(
                            (if (now && g.turn != null) "jetzt ${g.turn.kind.label}" else g.turn?.kind?.label ?: "Ziel").uppercase(Locale.GERMANY),
                            color = fg.copy(alpha = 0.8f), fontSize = 18.sp, fontFamily = Condensed, fontWeight = FontWeight.Medium, maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** Bedienung als Kachel - Stopp fragt wie ueberall noch einmal nach. */
@Composable
private fun ControlsTile(phase: RidePhase, c: RideControls, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        @Composable
        fun Btn(text: String, color: Color, weight: Float, onClick: () -> Unit) {
            Box(
                Modifier.weight(weight).fillMaxHeight().background(color).clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                Text(text.uppercase(Locale.GERMANY), color = Color.White, fontSize = 22.sp, fontFamily = Condensed, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
            }
        }
        when (phase) {
            RidePhase.IDLE -> Btn("Start", AppColors.Go, 1f, c.onStart)
            RidePhase.RECORDING -> {
                Btn("Pause", AppColors.Accent, 2f, c.onPause)
                Btn("Stopp", AppColors.Stop.copy(alpha = 0.75f), 1f, c.onStop)
            }
            RidePhase.PAUSED -> {
                Btn("Weiter", AppColors.Go, 2f, c.onResume)
                Btn("Stopp", AppColors.Stop, 1f, c.onStop)
            }
        }
    }
}

/** Ohne Karte: Skizze der gefahrenen Strecke. */
@Composable
private fun RouteTile(route: List<TrackPoint>, modifier: Modifier) {
    Box(modifier.background(AppColors.Bg).padding(10.dp)) {
        TileLabel("Strecke", "", AppColors.Text, 14.sp, Modifier.align(Alignment.TopCenter))
        if (route.size < 2) {
            Text(
                "Hier erscheint deine Strecke.\nFür eine Karte unter „Karten & Routen“ eine Offline-Karte laden.",
                color = AppColors.Dim,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            RouteSketch(route, Modifier.fillMaxSize().padding(top = 18.dp))
        }
    }
}
