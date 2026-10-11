package com.pbrockt.fahrradcomputer.ui.dashboard

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pbrockt.fahrradcomputer.data.HrZones
import com.pbrockt.fahrradcomputer.recording.RidePhase
import com.pbrockt.fahrradcomputer.recording.RideStats
import com.pbrockt.fahrradcomputer.recording.TrackPoint
import com.pbrockt.fahrradcomputer.sensors.HeartRateState
import com.pbrockt.fahrradcomputer.ui.AppColors
import com.pbrockt.fahrradcomputer.ui.formatDecimal
import com.pbrockt.fahrradcomputer.ui.formatDuration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min

/** Alles, was eine Kachel zum Zeichnen braucht. */
class TileData(
    val stats: RideStats,
    val hr: HeartRateState,
    val zones: HrZones,
    val now: Long,
    val route: List<TrackPoint>,
    val phase: RidePhase,
) {
    /** Pulswert nur, solange er frisch ist - sonst zeigt die Kachel "--" statt eines alten Werts. */
    val bpm: Int? get() = hr.bpm?.takeIf { now - hr.updatedAtMs <= 5_000 }
}

@Composable
fun Tile(type: TileType, d: TileData, modifier: Modifier) {
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
        // Trittfrequenz kommt mit Phase 3 (Bosch bzw. externer Sensor).
        TileType.CADENCE -> ValueTile("U/min", "--", modifier)
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
        TileType.ROUTE -> RouteTile(d.route, modifier)
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

/** Skizze der gefahrenen Strecke - Platzhalter fuer die Navi-Karte aus Phase 6. */
@Composable
private fun RouteTile(route: List<TrackPoint>, modifier: Modifier) {
    // Farben vorher holen: im Canvas-Block ist kein Composable-Zugriff moeglich.
    val line = AppColors.Accent
    val start = AppColors.Go
    val here = AppColors.Text
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(AppColors.Panel).padding(10.dp)) {
        Text("Strecke", color = AppColors.Dim, fontSize = 13.sp, modifier = Modifier.align(Alignment.TopStart))
        if (route.size < 2) {
            Text(
                "Hier erscheint deine Strecke.\nKarte und Navigation folgen.",
                color = AppColors.Dim,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center),
            )
            return@Box
        }
        Canvas(Modifier.fillMaxSize().padding(top = 16.dp)) {
            // Laengengrade schrumpfen zu den Polen hin - mit cos(Breite) bleibt die Form unverzerrt.
            val k = cos(Math.toRadians(route.first().lat))
            val xs = route.map { it.lon * k }
            val ys = route.map { it.lat }
            val minX = xs.min(); val maxX = xs.max()
            val minY = ys.min(); val maxY = ys.max()
            val span = maxOf(maxX - minX, maxY - minY).takeIf { it > 0 } ?: return@Canvas
            val scale = min(size.width, size.height) / span.toFloat()
            val offX = (size.width - (maxX - minX).toFloat() * scale) / 2
            val offY = (size.height - (maxY - minY).toFloat() * scale) / 2
            fun pt(i: Int) = Offset(
                offX + (xs[i] - minX).toFloat() * scale,
                size.height - offY - (ys[i] - minY).toFloat() * scale,
            )
            val path = Path().apply {
                moveTo(pt(0).x, pt(0).y)
                for (i in 1 until route.size) lineTo(pt(i).x, pt(i).y)
            }
            drawPath(path, line, style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(start, radius = 8f, center = pt(0))
            drawCircle(here, radius = 10f, center = pt(route.lastIndex))
        }
    }
}
