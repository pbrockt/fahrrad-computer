package com.pbrockt.fahrradcomputer.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pbrockt.fahrradcomputer.MainViewModel
import com.pbrockt.fahrradcomputer.recording.GpsStatus
import com.pbrockt.fahrradcomputer.recording.RidePhase
import com.pbrockt.fahrradcomputer.recording.RideStats
import com.pbrockt.fahrradcomputer.recording.TrackPoint
import com.pbrockt.fahrradcomputer.sensors.HeartRateState
import com.pbrockt.fahrradcomputer.sensors.SensorConnection
import com.pbrockt.fahrradcomputer.data.HrZones
import com.pbrockt.fahrradcomputer.ui.AppColors
import com.pbrockt.fahrradcomputer.ui.formatDecimal
import com.pbrockt.fahrradcomputer.ui.formatDuration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.min

/** Alles, was eine Kachel zum Zeichnen braucht. */
private class TileData(
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
fun DashboardScreen(vm: MainViewModel, onStartRequested: () -> Unit) {
    val stats by vm.ride.stats.collectAsStateWithLifecycle()
    val phase by vm.ride.phase.collectAsStateWithLifecycle()
    val gps by vm.ride.gps.collectAsStateWithLifecycle()
    val route by vm.ride.route.collectAsStateWithLifecycle()
    val finished by vm.ride.finished.collectAsStateWithLifecycle()
    val hr by vm.hr.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()

    // Display anlassen, solange das Dashboard zu sehen ist.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val data = TileData(stats, hr, settings.zones, now, route, phase)
    var confirmStop by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        StatusLine(phase, gps, hr)
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (row in DashboardLayout.DEFAULT.rows) {
                Row(
                    Modifier.weight(row.height).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (slot in row.tiles) {
                        Tile(slot.type, data, Modifier.weight(slot.weight).fillMaxSize())
                    }
                }
            }
        }
        Controls(
            phase = phase,
            onStart = onStartRequested,
            onPause = vm::pauseRide,
            onResume = vm::resumeRide,
            onStop = { confirmStop = true },
        )
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("Fahrt beenden?") },
            text = { Text("Die Fahrt wird gespeichert und die Aufzeichnung beendet.") },
            confirmButton = {
                TextButton(onClick = { confirmStop = false; vm.stopRide() }) { Text("Beenden") }
            },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("Weiterfahren") } },
        )
    }

    finished?.let { f ->
        AlertDialog(
            onDismissRequest = vm::dismissFinished,
            title = { Text("Fahrt gespeichert") },
            text = {
                val s = f.stats
                Text(
                    buildString {
                        appendLine("Distanz: ${formatDecimal(s.distanceM / 1000, 2)} km")
                        appendLine("Fahrzeit: ${formatDuration(s.movingTimeMs)}")
                        appendLine("Ø ${formatDecimal(s.avgSpeedKmh)} km/h · max ${formatDecimal(s.maxSpeedKmh)} km/h")
                        s.avgHr?.let { appendLine("Ø Puls $it · max ${s.maxHr}") }
                        if (f.file == null) append("Kein GPS-Signal - es wurde keine Datei gespeichert.")
                    }
                )
            },
            confirmButton = { TextButton(onClick = vm::dismissFinished) { Text("OK") } },
        )
    }
}

@Composable
private fun StatusLine(phase: RidePhase, gps: GpsStatus, hr: HeartRateState) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val gpsText = when {
            phase == RidePhase.IDLE -> "GPS startet mit der Fahrt"
            gps == GpsStatus.OK -> "GPS ok"
            gps == GpsStatus.SEARCHING -> "GPS sucht …"
            else -> "GPS aus!"
        }
        val gpsColor = when {
            phase == RidePhase.IDLE -> AppColors.Dim
            gps == GpsStatus.OK -> AppColors.Go
            gps == GpsStatus.SEARCHING -> AppColors.Zones[3]
            else -> AppColors.Stop
        }
        Text(gpsText, color = gpsColor, fontSize = 14.sp)
        if (phase == RidePhase.PAUSED) {
            Text("PAUSE", color = AppColors.Zones[3], fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        val hrText = when (hr.connection) {
            SensorConnection.CONNECTED -> "♥ ${hr.deviceName ?: "Gurt"}"
            SensorConnection.CONNECTING -> "♥ verbinde …"
            SensorConnection.NO_PERMISSION -> "♥ Bluetooth-Recht fehlt"
            SensorConnection.BLUETOOTH_OFF -> "♥ Bluetooth aus"
            SensorConnection.NONE -> "♥ kein Gurt"
        }
        Text(
            hrText,
            color = if (hr.connection == SensorConnection.CONNECTED) AppColors.Go else AppColors.Dim,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun Tile(type: TileType, d: TileData, modifier: Modifier) {
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
        TileType.AVG_HR -> ValueTile("Ø Puls", s.avgHr?.toString() ?: "--", modifier)
        TileType.ALTITUDE -> ValueTile("Höhe m", s.altitude?.let { "%.0f".format(it) } ?: "--", modifier)
        TileType.CLOCK -> ValueTile("Uhrzeit", LocalTime.now().format(CLOCK), modifier)
        TileType.ROUTE -> RouteTile(d.route, modifier)
    }
}

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Wert gross, Bezeichnung klein. Die Schriftgroesse richtet sich nach dem Platz der Kachel,
 * damit derselbe Baustein im grossen und im kleinen Feld lesbar bleibt.
 */
@Composable
private fun ValueTile(
    label: String,
    value: String,
    modifier: Modifier,
    background: Color = AppColors.Panel,
    content: Color = AppColors.Text,
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
            modifier = Modifier.align(Alignment.TopStart),
        )
        Text(
            value,
            color = content,
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
            drawPath(path, AppColors.Accent, style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(AppColors.Go, radius = 8f, center = pt(0))
            drawCircle(AppColors.Text, radius = 10f, center = pt(route.lastIndex))
        }
    }
}

@Composable
private fun Controls(
    phase: RidePhase,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        @Composable
        fun Big(text: String, color: Color, onClick: () -> Unit, weight: Float = 1f) {
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = AppColors.OnZone),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(weight).height(60.dp),
            ) { Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        }
        when (phase) {
            RidePhase.IDLE -> Big("Start", AppColors.Go, onStart)
            RidePhase.RECORDING -> {
                Big("Pause", AppColors.Accent, onPause, 2f)
                Big("Stopp", AppColors.Stop, onStop)
            }
            RidePhase.PAUSED -> {
                Big("Weiter", AppColors.Go, onResume, 2f)
                Big("Stopp", AppColors.Stop, onStop)
            }
        }
    }
}
