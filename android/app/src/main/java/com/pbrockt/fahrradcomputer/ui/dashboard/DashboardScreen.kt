package com.pbrockt.fahrradcomputer.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pbrockt.fahrradcomputer.MainViewModel
import com.pbrockt.fahrradcomputer.coach.PlanMatching
import com.pbrockt.fahrradcomputer.recording.GpsStatus
import com.pbrockt.fahrradcomputer.recording.RidePhase
import com.pbrockt.fahrradcomputer.sensors.SensorConnection
import com.pbrockt.fahrradcomputer.sensors.SensorReading
import com.pbrockt.fahrradcomputer.ui.AppColors
import com.pbrockt.fahrradcomputer.ui.AppScreen
import com.pbrockt.fahrradcomputer.ui.HomePage
import com.pbrockt.fahrradcomputer.ui.formatDecimal
import com.pbrockt.fahrradcomputer.ui.formatDuration
import com.pbrockt.fahrradcomputer.ui.holdAndDoubleTap
import kotlinx.coroutines.launch

/**
 * Der Fahrt-Bildschirm: ganz links die Start-Seite (Ueberblick, Start/Pause/Stopp),
 * rechts davon die Tacho-Seiten. Nach "Start" blaettert er von selbst zum Tacho; zum
 * Beenden wischt man zurueck zur Start-Seite.
 */
@Composable
fun DashboardScreen(
    vm: MainViewModel,
    onStartRequested: () -> Unit,
    onMenu: () -> Unit,
    onOpen: (AppScreen) -> Unit,
) {
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
    val lastPoint by vm.lastPoint.collectAsStateWithLifecycle()
    val activeRoute by vm.activeRoute.collectAsStateWithLifecycle()
    val progress by vm.routeProgress.collectAsStateWithLifecycle()
    val guidance by vm.guidance.collectAsStateWithLifecycle()
    val plan by vm.plan.collectAsStateWithLifecycle()
    val offlineMaps by vm.offlineMaps.collectAsStateWithLifecycle()
    val heading by vm.heading.collectAsStateWithLifecycle()
    val health by vm.healthState.collectAsStateWithLifecycle()

    // Display anlassen, solange der Fahrt-Bildschirm zu sehen ist.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    var editing by rememberSaveable { mutableStateOf(false) }
    var confirmStop by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val layout = settings.dashboard
    // Seite 0 = Start-Seite, danach die Tacho-Seiten. Laeuft schon eine Fahrt (App neu
    // geoeffnet), gleich den Tacho zeigen.
    val pager = rememberPagerState(initialPage = if (phase != RidePhase.IDLE) 1 else 0) { layout.pages.size + 1 }

    // Nach dem Start von selbst zum Tacho blaettern.
    var lastPhase by remember { mutableStateOf(phase) }
    LaunchedEffect(phase) {
        if (lastPhase == RidePhase.IDLE && phase == RidePhase.RECORDING && pager.currentPage == 0) {
            pager.animateScrollToPage(1)
        }
        lastPhase = phase
    }

    val controls = RideControls(
        onStart = onStartRequested,
        onPause = vm::pauseRide,
        onResume = vm::resumeRide,
        onStop = { confirmStop = true },
    )
    val data = TileData(
        stats, hr, cadence, power, settings.zones, now, route, phase,
        lastPoint = lastPoint,
        activeRoute = activeRoute,
        progress = progress,
        guidance = guidance,
        todaySession = PlanMatching.today(plan?.plan, java.time.LocalDate.now()),
        offlineMaps = offlineMaps,
        mapOnline = settings.mapOnline,
        controls = controls,
        heading = heading,
        mapHeadingUp = settings.mapHeadingUp,
        onToggleMapOrientation = vm::toggleMapOrientation,
    )

    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        TopBar(
            phase = phase,
            gps = gps,
            sensors = listOf(hr, cadence, power),
            onMenu = onMenu,
            onPlayPause = {
                when (phase) {
                    RidePhase.IDLE -> onStartRequested()
                    RidePhase.RECORDING -> vm.pauseRide()
                    RidePhase.PAUSED -> vm.resumeRide()
                }
            },
        )
        PageHeader(
            names = listOf("Start") + layout.pages.map { it.name },
            current = pager.currentPage,
            hint = when {
                editing -> "Bearbeiten"
                pager.currentPage == 0 -> "→ Tacho"
                else -> "halten = anpassen"
            },
        )
        HorizontalPager(
            state = pager,
            userScrollEnabled = !editing,
            pageSpacing = 8.dp,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { index ->
            if (index == 0) {
                HomePage(
                    vm,
                    onStart = onStartRequested,
                    onStop = { confirmStop = true },
                    onShowRide = { scope.launch { pager.animateScrollToPage(1) } },
                    onOpen = onOpen,
                )
                return@HorizontalPager
            }
            val pageIndex = index - 1
            val page = layout.pages.getOrNull(pageIndex) ?: return@HorizontalPager
            if (editing) {
                EditablePage(page, pageIndex, TileData(
                    stats, hr, cadence, power, settings.zones, now, route, phase, interactive = false,
                ), onEdit = vm::editDashboard)
            } else {
                PageView(
                    page, data, mapId = "dash-$pageIndex",
                    modifier = Modifier.holdAndDoubleTap(
                        holdMs = settings.editHoldMs.toLong(),
                        onHold = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            editing = true
                        },
                        onDoubleTap = vm::ringBell,
                    ),
                )
            }
        }
        if (editing) {
            EditBar(
                layout,
                page = (pager.currentPage - 1).coerceAtLeast(0),
                scrollToPage = { pager.scrollToPage(it + 1) },
                vm = vm,
                onDone = { editing = false },
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
                    }
                )
            },
            confirmButton = { TextButton(onClick = vm::dismissFinished) { Text("OK") } },
            // Nach Health Connect nur auf Wunsch - hier direkt, ohne die Fahrt erst zu suchen.
            dismissButton = {
                val id = f.id
                if (id != null && health.connected) {
                    TextButton(onClick = { vm.syncRide(id); vm.dismissFinished() }) { Text("An Health Connect") }
                }
            },
        )
    }
}

/** Eine Tacho-Seite: Zeilen nach Hoehe, Kacheln nach Breite verteilt. */
@Composable
private fun PageView(page: DashboardPage, data: TileData, mapId: String, modifier: Modifier = Modifier) {
    if (page.rows.isEmpty()) {
        Box(Modifier.fillMaxSize().then(modifier), contentAlignment = Alignment.Center) {
            Text("Leere Seite – gedrückt halten, um Kacheln hinzuzufügen.", color = AppColors.Dim, textAlign = TextAlign.Center)
        }
        return
    }
    Column(
        Modifier.fillMaxSize().then(modifier),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (row in page.rows) {
            Row(
                Modifier.weight(row.height.weight).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (slot in row.tiles) {
                    Tile(slot.type, data, Modifier.weight(slot.units.toFloat()).fillMaxSize(), mapId)
                }
            }
        }
    }
}

/** Name der Seite und Punkte fuer die Seitenzahl. */
@Composable
private fun PageHeader(names: List<String>, current: Int, hint: String) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            names.getOrElse(current) { "" },
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            names.indices.forEach { i ->
                Box(
                    Modifier.size(if (i == 0) 6.dp else 8.dp).clip(CircleShape)
                        .background(if (i == current) AppColors.Text else AppColors.Line)
                )
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

/**
 * Schmale Leiste oben: Menue, GPS, Sensoren und ein kleiner Start/Pause-Knopf in der
 * Ecke. Stopp gibt es bewusst nur auf der Start-Seite und als Kachel - nicht aus Versehen.
 */
@Composable
private fun TopBar(
    phase: RidePhase,
    gps: GpsStatus,
    sensors: List<SensorReading>,
    onMenu: () -> Unit,
    onPlayPause: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMenu, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Filled.Menu, contentDescription = "Menü")
        }
        val (gpsText, gpsColor) = when {
            phase == RidePhase.IDLE -> "GPS ab Start" to AppColors.Dim
            gps == GpsStatus.OK -> "GPS ok" to AppColors.Go
            gps == GpsStatus.SEARCHING -> "GPS sucht …" to AppColors.Zones[3]
            else -> "GPS aus!" to AppColors.Stop
        }
        Text(gpsText, color = gpsColor, fontSize = 13.sp)
        if (phase == RidePhase.PAUSED) {
            Text("PAUSE", color = AppColors.Zones[3], fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Box(Modifier.weight(1f))
        // Ein Symbol je Sensorart: gruen = verbunden, gelb = verbindet, rot = Fehler, grau = keiner.
        sensors.zip(listOf("♥", "⟳", "⚡")).forEach { (r, symbol) ->
            val color = when (r.connection) {
                SensorConnection.CONNECTED -> AppColors.Go
                SensorConnection.CONNECTING -> AppColors.Zones[3]
                SensorConnection.NO_PERMISSION, SensorConnection.BLUETOOTH_OFF -> AppColors.Stop
                SensorConnection.NONE -> AppColors.Line
            }
            Text(symbol, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        FilledIconButton(
            onClick = onPlayPause,
            modifier = Modifier.size(40.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (phase == RidePhase.RECORDING) AppColors.Accent else AppColors.Go,
                contentColor = AppColors.OnZone,
            ),
        ) {
            Icon(
                if (phase == RidePhase.RECORDING) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = when (phase) {
                    RidePhase.IDLE -> "Fahrt starten"
                    RidePhase.RECORDING -> "Pause"
                    RidePhase.PAUSED -> "Weiter"
                },
            )
        }
    }
}
