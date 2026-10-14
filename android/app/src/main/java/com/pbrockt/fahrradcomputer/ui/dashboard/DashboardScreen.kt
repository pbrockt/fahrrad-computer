package com.pbrockt.fahrradcomputer.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pbrockt.fahrradcomputer.MainViewModel
import com.pbrockt.fahrradcomputer.recording.GpsStatus
import com.pbrockt.fahrradcomputer.recording.RidePhase
import com.pbrockt.fahrradcomputer.sensors.SensorReading
import com.pbrockt.fahrradcomputer.sensors.SensorConnection
import com.pbrockt.fahrradcomputer.ui.AppColors
import com.pbrockt.fahrradcomputer.ui.formatDecimal
import com.pbrockt.fahrradcomputer.ui.formatDuration

@Composable
fun DashboardScreen(vm: MainViewModel, onStartRequested: () -> Unit) {
    val stats by vm.ride.stats.collectAsStateWithLifecycle()
    val phase by vm.ride.phase.collectAsStateWithLifecycle()
    val gps by vm.ride.gps.collectAsStateWithLifecycle()
    val route by vm.ride.route.collectAsStateWithLifecycle()
    val finished by vm.ride.finished.collectAsStateWithLifecycle()
    val hr by vm.hr.collectAsStateWithLifecycle()
    val cadence by vm.cadence.collectAsStateWithLifecycle()
    val power by vm.power.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()

    // Display anlassen, solange das Dashboard zu sehen ist.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val layout = settings.dashboard
    val data = TileData(stats, hr, cadence, power, settings.zones, now, route, phase)
    val pager = rememberPagerState(pageCount = { layout.pages.size })
    var editing by rememberSaveable { mutableStateOf(false) }
    var confirmStop by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        StatusLine(phase, gps, listOf(hr, cadence, power))
        PageHeader(
            names = layout.pages.map { it.name },
            current = pager.currentPage,
            hint = if (editing) "Bearbeiten" else "lange drücken = anpassen",
        )
        HorizontalPager(
            state = pager,
            userScrollEnabled = !editing,
            pageSpacing = 8.dp,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { index ->
            val page = layout.pages.getOrNull(index) ?: return@HorizontalPager
            if (editing) {
                EditablePage(page, index, data, onEdit = vm::editDashboard)
            } else {
                PageView(page, data, onLongPress = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    editing = true
                })
            }
        }
        if (editing) {
            EditBar(layout, pager, vm, onDone = { editing = false })
        } else {
            Controls(
                phase = phase,
                onStart = onStartRequested,
                onPause = vm::pauseRide,
                onResume = vm::resumeRide,
                onStop = { confirmStop = true },
            )
        }
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
                        s.avgCadence?.let { appendLine("Ø Trittfrequenz $it U/min") }
                        s.avgPower?.let { appendLine("Ø Leistung $it W · max ${s.maxPower} W") }
                        if (s.calories > 0) appendLine("Kalorien: ${"%.0f".format(s.calories)} kcal (${s.calorieSource.label})")
                        if (f.file == null) append("Kein GPS-Signal - es wurde keine Datei gespeichert.")
                        f.healthNote?.let { append("\n$it") }
                    }
                )
            },
            confirmButton = { TextButton(onClick = vm::dismissFinished) { Text("OK") } },
        )
    }
}

/** Eine Seite im Fahrbetrieb: Zeilen nach Hoehe, Kacheln nach Breite verteilt. */
@Composable
private fun PageView(page: DashboardPage, data: TileData, onLongPress: () -> Unit) {
    val gestures = Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) }
    if (page.rows.isEmpty()) {
        Box(Modifier.fillMaxSize().then(gestures), contentAlignment = Alignment.Center) {
            Text("Leere Seite – lange drücken, um Kacheln hinzuzufügen.", color = AppColors.Dim, textAlign = TextAlign.Center)
        }
        return
    }
    Column(
        Modifier.fillMaxSize().then(gestures),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (row in page.rows) {
            Row(
                Modifier.weight(row.height.weight).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (slot in row.tiles) {
                    Tile(slot.type, data, Modifier.weight(slot.units.toFloat()).fillMaxSize())
                }
            }
        }
    }
}

/** Name der Seite und Punkte fuer die Seitenzahl. */
@Composable
private fun PageHeader(names: List<String>, current: Int, hint: String) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            names.getOrElse(current) { "" },
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f),
        )
        if (names.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                names.indices.forEach { i ->
                    Box(
                        Modifier.size(8.dp).clip(CircleShape)
                            .background(if (i == current) AppColors.Text else AppColors.Line)
                    )
                }
            }
        }
        Text(
            hint,
            color = AppColors.Dim,
            fontSize = 12.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatusLine(phase: RidePhase, gps: GpsStatus, sensors: List<SensorReading>) {
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
        // Ein Symbol je Sensorart: gruen = verbunden, gelb = verbindet, grau = keiner/Fehler.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            sensors.zip(listOf("♥", "⟳", "⚡")).forEach { (r, symbol) ->
                val color = when (r.connection) {
                    SensorConnection.CONNECTED -> AppColors.Go
                    SensorConnection.CONNECTING -> AppColors.Zones[3]
                    SensorConnection.NO_PERMISSION, SensorConnection.BLUETOOTH_OFF -> AppColors.Stop
                    SensorConnection.NONE -> AppColors.Line
                }
                Text(symbol, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
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
