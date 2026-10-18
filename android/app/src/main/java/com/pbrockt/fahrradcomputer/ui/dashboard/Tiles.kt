package com.pbrockt.fahrradcomputer.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pbrockt.fahrradcomputer.data.HrZones
import com.pbrockt.fahrradcomputer.recording.RidePhase
import com.pbrockt.fahrradcomputer.recording.RideStats
import com.pbrockt.fahrradcomputer.recording.TrackPoint
import com.pbrockt.fahrradcomputer.sensors.SensorReading
import com.pbrockt.fahrradcomputer.ui.AppColors
import com.pbrockt.fahrradcomputer.ui.RouteSketch
import com.pbrockt.fahrradcomputer.ui.map.BikeMap
import com.pbrockt.fahrradcomputer.ui.map.mapAvailable
import com.pbrockt.fahrradcomputer.routes.Route
import com.pbrockt.fahrradcomputer.routes.RouteProgress
import com.pbrockt.fahrradcomputer.routes.Guidance
import com.pbrockt.fahrradcomputer.coach.PlannedSession
import com.pbrockt.fahrradcomputer.coach.SessionKind
import com.pbrockt.fahrradcomputer.routes.TurnAnnouncer
import com.pbrockt.fahrradcomputer.ui.TurnArrow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import java.io.File
import com.pbrockt.fahrradcomputer.ui.formatDecimal
import com.pbrockt.fahrradcomputer.ui.formatDuration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min

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
) {
    // Messwerte nur, solange sie frisch sind - sonst zeigt die Kachel "--" statt eines alten Werts.
    val bpm: Int? get() = hr.fresh(now)
    val rpm: Int? get() = cadence.fresh(now)
    val watts: Int? get() = power.fresh(now)
}

@Composable
fun Tile(type: TileType, d: TileData, modifier: Modifier, mapId: String = "dash") {
    val s = d.stats
    when (type) {
        TileType.SPEED -> ValueTile(
            "km/h", if (d.phase == RidePhase.IDLE) "--" else formatDecimal(s.speedKmh), modifier,
        )
        TileType.HEART_RATE -> {
            val bpm = d.bpm
            if (bpm == null) {
                ValueTile("Puls", "--", modifier)
            } else {
                val zone = d.zones.zoneOf(bpm)
                ValueTile(
                    "Puls · Z$zone", bpm.toString(), modifier,
                    background = AppColors.zone(zone), content = AppColors.OnZone,
                )
            }
        }
        TileType.CADENCE -> ValueTile("U/min", d.rpm?.toString() ?: "--", modifier)
        TileType.POWER -> ValueTile("Watt", d.watts?.toString() ?: "--", modifier)
        TileType.CALORIES -> ValueTile(
            "kcal · ${s.calorieSource.label}",
            if (d.phase == RidePhase.IDLE && s.calories == 0.0) "--" else String.format(Locale.GERMANY, "%.0f", s.calories),
            modifier,
        )
        TileType.AVG_CADENCE -> ValueTile("Ø U/min", s.avgCadence?.toString() ?: "--", modifier)
        TileType.AVG_POWER -> ValueTile("Ø Watt", s.avgPower?.toString() ?: "--", modifier)
        TileType.DISTANCE -> ValueTile("km", formatDecimal(s.distanceM / 1000, 2), modifier)
        TileType.MOVING_TIME -> ValueTile("Fahrzeit", formatDuration(s.movingTimeMs), modifier)
        TileType.AVG_SPEED -> ValueTile("Ø km/h", formatDecimal(s.avgSpeedKmh), modifier)
        TileType.MAX_SPEED -> ValueTile("max km/h", formatDecimal(s.maxSpeedKmh), modifier)
        TileType.AVG_HR -> HrValueTile("Ø Puls", s.avgHr, d.zones, modifier)
        TileType.MAX_HR -> HrValueTile("max Puls", s.maxHr, d.zones, modifier)
        TileType.ALTITUDE -> ValueTile(
            "Höhe m", s.altitude?.let { String.format(Locale.GERMANY, "%.0f", it) } ?: "--", modifier,
        )
        TileType.CLOCK -> ValueTile(
            "Uhrzeit", CLOCK.format(Instant.ofEpochMilli(d.now).atZone(ZoneId.systemDefault())), modifier,
        )
        TileType.ROUTE -> when {
            !d.interactive -> ValueTile("Karte", "🗺", modifier)
            mapAvailable(d.offlineMaps, d.mapOnline) -> MapTile(d, modifier, mapId)
            else -> RouteTile(d.route, modifier)
        }
        TileType.TURN -> TurnTile(d, modifier)
        TileType.CONTROLS -> {
            val c = d.controls
            if (c == null || !d.interactive) ValueTile("Start / Pause / Stopp", "▶", modifier)
            else ControlsTile(d.phase, c, modifier)
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
                        "Z${t.targetZone}", modifier,
                        background = AppColors.zone(t.targetZone), content = AppColors.OnZone,
                    )
                }
            }
        }
        TileType.ROUTE_REMAINING -> {
            val p = d.progress
            when {
                d.activeRoute == null -> ValueTile("Rest km · keine Route", "--", modifier)
                p == null -> ValueTile("Rest km", formatDecimal(d.activeRoute.lengthM / 1000), modifier)
                p.offRoute -> ValueTile(
                    "abseits ${String.format(Locale.GERMANY, "%.0f", p.offRouteM)} m", formatDecimal(p.remainingM / 1000), modifier,
                    background = AppColors.Stop, content = AppColors.OnZone,
                )
                else -> ValueTile("Rest km", formatDecimal(p.remainingM / 1000), modifier)
            }
        }
    }
}

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")

/** Ø/max Puls: der Wert in der Farbe seiner Zone, auf normalem Grund. */
@Composable
private fun HrValueTile(label: String, bpm: Int?, zones: HrZones, modifier: Modifier) {
    if (bpm == null) {
        ValueTile(label, "--", modifier)
    } else {
        val zone = zones.zoneOf(bpm)
        ValueTile("$label · Z$zone", bpm.toString(), modifier, valueColor = AppColors.zone(zone))
    }
}

/**
 * Wert gross, Bezeichnung klein. Die Schriftgroesse richtet sich nach dem Platz der Kachel,
 * damit derselbe Baustein im grossen und im kleinen Feld lesbar bleibt.
 */
@Composable
fun ValueTile(
    label: String,
    value: String,
    modifier: Modifier,
    background: Color = AppColors.Panel,
    content: Color = AppColors.Text,
    valueColor: Color = content,
) {
    BoxWithConstraints(
        modifier.clip(RoundedCornerShape(10.dp)).background(background).padding(6.dp),
    ) {
        val density = LocalDensity.current
        val byHeight = maxHeight * 0.62f
        val byWidth = maxWidth / (value.length.coerceAtLeast(2) * 0.6f)
        val size = with(density) { min(byHeight.toPx(), byWidth.toPx()).toSp() }
        Text(
            label,
            color = content.copy(alpha = 0.75f),
            fontSize = 13.sp,
            maxLines = 1,
            modifier = Modifier.align(Alignment.TopStart),
        )
        Text(
            value,
            color = valueColor,
            fontSize = size,
            lineHeight = size,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.align(Alignment.Center).padding(top = 8.dp),
        )
    }
}

/** Karte mit Route, gefahrener Strecke und Position; folgt der Position. */
@Composable
private fun MapTile(d: TileData, modifier: Modifier, mapId: String) {
    // Folgt der Position, bis man die Karte verschiebt; der Knopf holt sie zurueck.
    var following by remember { mutableStateOf(true) }
    Box(modifier.clip(RoundedCornerShape(10.dp))) {
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
        FilledIconButton(
            onClick = { if (!following) following = true else d.onToggleMapOrientation() },
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(40.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = AppColors.Panel, contentColor = AppColors.Text),
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
                color = AppColors.OnZone,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (p.offRoute) AppColors.Stop else AppColors.Go)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

/**
 * Naechste Abbiegung: Pfeil links, Entfernung gross rechts. Kurz vor der Abbiegung
 * (unter 30 m) wird die Kachel hell - so faellt es auch aus dem Augenwinkel auf.
 */
@Composable
private fun TurnTile(d: TileData, modifier: Modifier) {
    val g = d.guidance
    val p = d.progress
    when {
        d.activeRoute == null -> ValueTile("Abbiegen · keine Route", "--", modifier)
        p?.offRoute == true -> ValueTile(
            "abseits der Route", "${String.format(Locale.GERMANY, "%.0f", p.offRouteM)} m", modifier,
            background = AppColors.Stop, content = AppColors.OnZone,
        )
        g == null -> ValueTile("Abbiegen · warte auf GPS", "--", modifier)
        else -> {
            val now = g.distanceM <= TurnAnnouncer.NOW_M
            val bg = if (now) AppColors.Go else AppColors.Panel
            val fg = if (now) AppColors.OnZone else AppColors.Text
            BoxWithConstraints(modifier.clip(RoundedCornerShape(10.dp)).background(bg).padding(6.dp)) {
                val density = LocalDensity.current
                val value = Guidance.formatMeters(g.distanceM)
                val size = with(density) {
                    min((maxHeight * 0.45f).toPx(), (maxWidth * 0.6f / (value.length * 0.6f)).toPx()).toSp()
                }
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    TurnArrow(g.turn?.angle, fg, Modifier.weight(1f).fillMaxHeight())
                    Column(Modifier.weight(1.5f)) {
                        Text(value, color = fg, fontSize = size, lineHeight = size, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(
                            if (now && g.turn != null) "jetzt ${g.turn.kind.label}" else g.turn?.kind?.label ?: "Ziel",
                            color = fg.copy(alpha = 0.8f), fontSize = 16.sp, maxLines = 1,
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
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        @Composable
        fun Btn(text: String, color: Color, weight: Float, onClick: () -> Unit) {
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = AppColors.OnZone),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(weight).fillMaxHeight(),
            ) { Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        }
        when (phase) {
            RidePhase.IDLE -> Btn("Start", AppColors.Go, 1f, c.onStart)
            RidePhase.RECORDING -> {
                Btn("Pause", AppColors.Accent, 2f, c.onPause)
                Btn("Stopp", AppColors.Stop, 1f, c.onStop)
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
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(AppColors.Panel).padding(10.dp)) {
        Text("Strecke", color = AppColors.Dim, fontSize = 13.sp, modifier = Modifier.align(Alignment.TopStart))
        if (route.size < 2) {
            Text(
                "Hier erscheint deine Strecke.\nKarte und Navigation folgen.",
                color = AppColors.Dim,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            RouteSketch(route, Modifier.fillMaxSize().padding(top = 16.dp))
        }
    }
}
