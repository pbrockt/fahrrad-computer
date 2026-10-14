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
import com.pbrockt.fahrradcomputer.ui.formatDecimal
import com.pbrockt.fahrradcomputer.ui.formatDuration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min

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
) {
    // Messwerte nur, solange sie frisch sind - sonst zeigt die Kachel "--" statt eines alten Werts.
    val bpm: Int? get() = hr.fresh(now)
    val rpm: Int? get() = cadence.fresh(now)
    val watts: Int? get() = power.fresh(now)
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
