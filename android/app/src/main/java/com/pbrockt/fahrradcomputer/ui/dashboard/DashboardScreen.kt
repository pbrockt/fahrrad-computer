package com.pbrockt.fahrradcomputer.ui.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Route
import androidx.compose.ui.input.pointer.pointerInput
import com.pbrockt.fahrradcomputer.ui.Condensed
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
    val climb by vm.ride.climb.collectAsStateWithLifecycle()
    val weather by vm.weather.collectAsStateWithLifecycle()

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
        powerZones = settings.powerZones,
        cadenceZones = settings.cadenceZones,
        speedZones = settings.speedZones,
        climb = climb,
        weather = weather,
        weightKg = settings.weightKg,
        ftp = settings.ftp,
        phoneBattery = remember(now / 60_000) { vm.phoneBattery() },
    )

    val names = listOf("Start") + layout.pages.map { it.name }
    Column(Modifier.fillMaxSize().background(AppColors.Bg)) {
        TopBar(
            phase = phase,
            gps = gps,
            title = if (editing) "Bearbeiten" else names.getOrElse(pager.currentPage) { "" },
            sensors = listOf(hr, cadence, power),
            onMenu = onMenu,
        )
        // Rotes Banner mit dem Seitennamen, kurz nach dem Blaettern.
        var banner by remember { mutableStateOf<Int?>(null) }
        var firstPage by remember { mutableStateOf(true) }
        LaunchedEffect(pager.settledPage) {
            if (firstPage) { firstPage = false; return@LaunchedEffect }
            banner = pager.settledPage
            kotlinx.coroutines.delay(1_500)
            banner = null
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
        HorizontalPager(
            state = pager,
            userScrollEnabled = !editing,
            modifier = Modifier.fillMaxSize(),
        ) { index ->
            if (index == 0) {
                Box(Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
                    HomePage(
                        vm,
                        onStart = onStartRequested,
                        onStop = { confirmStop = true },
                        onShowRide = { scope.launch { pager.animateScrollToPage(1) } },
                        onOpen = onOpen,
                    )
                }
                return@HorizontalPager
            }
            val pageIndex = index - 1
            val page = layout.pages.getOrNull(pageIndex) ?: return@HorizontalPager
            if (editing) {
                Box(Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
                    EditablePage(page, pageIndex, TileData(
                        stats, hr, cadence, power, settings.zones, now, route, phase, interactive = false,
                        powerZones = settings.powerZones, cadenceZones = settings.cadenceZones,
                        speedZones = settings.speedZones, climb = climb, weather = weather,
                    ), onEdit = vm::editDashboard)
                }
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
        banner?.takeIf { !editing }?.let { b -> PageBanner(names.getOrElse(b) { "" }, b + 1, names.size, Modifier.align(Alignment.TopCenter)) }
        }
        PageDashes(names.size, pager.currentPage)
        if (editing) {
            Box(Modifier.padding(horizontal = 8.dp)) {
                EditBar(
                    layout,
                    page = (pager.currentPage - 1).coerceAtLeast(0),
                    scrollToPage = { pager.scrollToPage(it + 1) },
                    vm = vm,
                    onDone = { editing = false },
                )
            }
        } else {
            RideBar(
                phase = phase,
                onStart = onStartRequested,
                onTrainer = { onOpen(AppScreen.TRAINER) },
                onRoute = { onOpen(AppScreen.MAPS) },
                onPause = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.pauseRide()
                },
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

/**
 * Eine Tacho-Seite: Kacheln ohne Abstand, nur durch feine Linien getrennt
 * (der Hintergrund in Linienfarbe scheint durch 1 dp breite Fugen).
 */
@Composable
internal fun PageView(page: DashboardPage, data: TileData, mapId: String, modifier: Modifier = Modifier) {
    if (page.rows.isEmpty()) {
        Box(Modifier.fillMaxSize().then(modifier), contentAlignment = Alignment.Center) {
            Text("Leere Seite – gedrückt halten, um Kacheln hinzuzufügen.", color = AppColors.Dim, textAlign = TextAlign.Center)
        }
        return
    }
    Column(
        Modifier.fillMaxSize().background(AppColors.Line).then(modifier),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        for (row in page.rows) {
            Row(
                Modifier.weight(row.height.weight).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                for (slot in row.tiles) {
                    Tile(slot.type, data, Modifier.weight(slot.units.toFloat()).fillMaxSize(), mapId, slot.options)
                }
            }
        }
    }
}

/** "FAHRT · Seite 2 von 6" in Rot oben ueber dem Tacho. */
@Composable
private fun PageBanner(name: String, number: Int, total: Int, modifier: Modifier) {
    Column(
        modifier.padding(top = 10.dp).clip(RoundedCornerShape(6.dp)).background(AppColors.Accent)
            .padding(horizontal = 28.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(name.uppercase(java.util.Locale.GERMANY), color = Color.White, fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, letterSpacing = 1.sp)
        Text("Seite $number von $total", color = Color.White.copy(alpha = 0.85f), fontFamily = Condensed, fontSize = 16.sp)
    }
}

/** Seitenanzeige als kurze Striche; der aktuelle ist hell. */
@Composable
internal fun PageDashes(count: Int, current: Int) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        for (i in 0 until count) {
            Box(
                Modifier.padding(horizontal = 2.dp).size(width = if (i == 0) 10.dp else 16.dp, height = 3.dp)
                    .background(if (i == current) AppColors.Text else AppColors.Line)
            )
        }
    }
}

/**
 * Die rote Leiste unten. Waehrend der Fahrt: "Halten zum Pausieren" -
 * ein versehentliches Antippen pausiert nicht. In der Pause: Weiter oder Beenden.
 */
@Composable
internal fun RideBar(
    phase: RidePhase,
    onStart: () -> Unit,
    onTrainer: () -> Unit = {},
    onRoute: () -> Unit = {},
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        when (phase) {
            // grosser Start, daneben dunklere Felder fuer Indoor und Route.
            RidePhase.IDLE -> {
                Box(
                    Modifier.weight(2.2f).fillMaxHeight().background(AppColors.Accent).clickable(onClick = onStart),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        BarText("Start")
                        Text(
                            "OUTDOOR-FAHRT", color = Color.White.copy(alpha = 0.8f), fontFamily = Condensed,
                            fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 1.sp,
                        )
                    }
                }
                SquareButton("Heimtrainer", Icons.Filled.FitnessCenter, Modifier.weight(1f), onTrainer)
                SquareButton("Route", Icons.Filled.Route, Modifier.weight(1f), onRoute)
            }
            RidePhase.RECORDING -> HoldButton("Halten zum Pausieren", AppColors.Accent, 700, Modifier.weight(1f), onPause)
            RidePhase.PAUSED -> {
                BarButton("Weiter", AppColors.Go, Modifier.weight(2f), onResume)
                BarButton("Beenden", AppColors.Accent, Modifier.weight(1f), onStop)
            }
        }
    }
}

/** Dunkelrotes Feld mit Symbol und Beschriftung - neben "Start". */
@Composable
private fun SquareButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.fillMaxHeight().background(AppColors.AccentDark).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
        Text(
            label.uppercase(java.util.Locale.GERMANY), color = Color.White.copy(alpha = 0.85f), fontFamily = Condensed,
            fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.6.sp, maxLines = 1,
        )
    }
}

@Composable
private fun BarButton(text: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.fillMaxHeight().background(color).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        BarText(text)
    }
}

@Composable
private fun BarText(text: String) {
    Text(
        text.uppercase(java.util.Locale.GERMANY),
        color = Color.White,
        fontFamily = Condensed,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        letterSpacing = 1.sp,
    )
}

/** Knopf, der erst nach [holdMs] gedrueckt halten ausloest; ein Balken zeigt den Fortschritt. */
@Composable
private fun HoldButton(text: String, color: Color, holdMs: Int, modifier: Modifier, onDone: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val done by rememberUpdatedState(onDone)
    Box(
        modifier.fillMaxHeight().background(color).pointerInput(Unit) {
            detectTapGestures(onPress = {
                val job = scope.launch {
                    progress.animateTo(1f, tween(holdMs, easing = LinearEasing))
                    done()
                    progress.snapTo(0f)
                }
                tryAwaitRelease()
                if (job.isActive) {
                    job.cancel()
                    scope.launch { progress.animateTo(0f, tween(150)) }
                }
            })
        },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.value).background(Color.Black.copy(alpha = 0.3f)))
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BarText(text) }
    }
}

/**
 * Schmale Leiste oben: Menue, Seitenname, GPS und Sensoren (mit Warnung bei schwachem
 * Akku). Start, Pause und Stopp liegen unten in der roten Leiste bzw. auf der Start-Seite.
 */
@Composable
internal fun TopBar(
    phase: RidePhase,
    gps: GpsStatus,
    title: String,
    sensors: List<SensorReading>,
    onMenu: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(44.dp).padding(end = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMenu, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Filled.Menu, contentDescription = "Menü", tint = AppColors.Text)
        }
        Text(
            title.uppercase(java.util.Locale.GERMANY),
            color = AppColors.Text,
            fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, letterSpacing = 0.8.sp,
            maxLines = 1,
        )
        if (phase == RidePhase.PAUSED) {
            Text("PAUSE", color = Color.White, fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                modifier = Modifier.background(AppColors.Zones[3]).padding(horizontal = 6.dp, vertical = 1.dp))
        }
        Box(Modifier.weight(1f))
        // Ein Symbol je Sensorart: gruen = verbunden, gelb = verbindet, rot = Fehler oder Akku < 20 %.
        sensors.zip(listOf("♥", "⟳", "⚡")).forEach { (r, symbol) ->
            val lowBattery = (r.battery ?: 100) < 20
            val color = when {
                r.connection == SensorConnection.CONNECTED && lowBattery -> AppColors.Stop
                r.connection == SensorConnection.CONNECTED -> AppColors.Go
                r.connection == SensorConnection.CONNECTING -> AppColors.Zones[3]
                r.connection == SensorConnection.NO_PERMISSION || r.connection == SensorConnection.BLUETOOTH_OFF -> AppColors.Stop
                else -> AppColors.Line
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(symbol, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (lowBattery && r.connection == SensorConnection.CONNECTED) {
                    Text("${r.battery}%", color = color, fontSize = 11.sp, fontFamily = Condensed)
                }
            }
        }
        // GPS-Kaestchen.
        val gpsColor = when {
            phase == RidePhase.IDLE -> AppColors.Line
            gps == GpsStatus.OK -> AppColors.Go
            gps == GpsStatus.SEARCHING -> AppColors.Zones[3]
            else -> AppColors.Stop
        }
        Text(
            "GPS", color = Color.White, fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(gpsColor).padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
