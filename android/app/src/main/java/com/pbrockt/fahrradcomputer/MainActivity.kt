package com.pbrockt.fahrradcomputer

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pbrockt.fahrradcomputer.sensors.SensorKind
import com.pbrockt.fahrradcomputer.ui.AppColors
import com.pbrockt.fahrradcomputer.ui.AppScreen
import com.pbrockt.fahrradcomputer.ui.Condensed
import com.pbrockt.fahrradcomputer.ui.TrainerScreen
import com.pbrockt.fahrradcomputer.recording.RidePhase
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.Alignment
import com.pbrockt.fahrradcomputer.ui.FahrradTheme
import com.pbrockt.fahrradcomputer.ui.MapsScreen
import com.pbrockt.fahrradcomputer.ui.RidesScreen
import com.pbrockt.fahrradcomputer.ui.SensorsScreen
import com.pbrockt.fahrradcomputer.ui.SettingsScreen
import com.pbrockt.fahrradcomputer.ui.dashboard.DashboardScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val vm: MainViewModel = viewModel()
            val settings by vm.settings.collectAsStateWithLifecycle()
            FahrradTheme(settings.themeMode) {
                // Statusleisten-Symbole passend zum Modus: dunkle Symbole auf hellem Grund.
                val dark = AppColors.isDark
                LaunchedEffect(dark) {
                    val transparent = android.graphics.Color.TRANSPARENT
                    val style = if (dark) SystemBarStyle.dark(transparent)
                    else SystemBarStyle.light(transparent, transparent)
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                AppRoot(vm)
            }
        }
    }
}


private val LOCATION_PERMISSIONS = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    // Ohne Benachrichtigungsrecht laeuft die Aufzeichnung trotzdem - man sieht sie nur nicht.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

private val BLUETOOTH_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
} else {
    // Bis Android 11 ist der BLE-Scan an die Standort-Berechtigung gekoppelt.
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
}

/** Heimtrainer: Sensoren lesen, sich Zwift anbieten und die Benachrichtigung zeigen. */
private val TRAINER_PERMISSIONS = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_SCAN)
        add(Manifest.permission.BLUETOOTH_CONNECT)
        add(Manifest.permission.BLUETOOTH_ADVERTISE)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(vm: MainViewModel) {
    var screen by rememberSaveable { mutableStateOf(AppScreen.RIDE) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            vm.startRide()
        } else {
            scope.launch { snackbar.showSnackbar("Ohne genauen Standort kann keine Strecke aufgezeichnet werden.") }
        }
    }

    // Fuer welche Sensorart nach der Rechte-Abfrage gesucht werden soll.
    var pendingScan by rememberSaveable { mutableStateOf<SensorKind?>(null) }
    val bluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            vm.retrySensors()
            pendingScan?.let(vm::startScan)
        } else {
            scope.launch { snackbar.showSnackbar("Ohne Bluetooth-Berechtigung lässt sich kein Gurt verbinden.") }
        }
    }

    val trainerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val bluetooth = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            (result[Manifest.permission.BLUETOOTH_CONNECT] == true && result[Manifest.permission.BLUETOOTH_ADVERTISE] == true)
        if (bluetooth) {
            vm.retrySensors()
            vm.startTrainer()
        } else {
            scope.launch { snackbar.showSnackbar("Ohne Bluetooth-Berechtigung kann nichts an Zwift gesendet werden.") }
        }
    }

    val healthLauncher = rememberLauncherForActivityResult(vm.healthContract) { vm.onHealthPermissionsResult() }

    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            vm.setLiveSmsEnabled(true)
        } else {
            scope.launch { snackbar.showSnackbar("Ohne SMS-Berechtigung kann kein Live-Standort verschickt werden.") }
        }
    }

    // Berechtigungen koennen auch in der Health-Connect-App geaendert werden - beim
    // Zurueckkommen neu nachsehen.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshHealth() }

    val message by vm.message.collectAsStateWithLifecycle()
    LaunchedEffect(message) {
        message?.let {
            vm.consumeMessage()
            snackbar.showSnackbar(it)
        }
    }

    val phase by vm.ride.phase.collectAsStateWithLifecycle()
    // Unterseiten fuehren zurueck zu ihrem Bereich, die Reiter zurueck zum Computer.
    BackHandler(enabled = screen != AppScreen.RIDE) {
        screen = screen.parent ?: AppScreen.RIDE
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            // Der Computer und die Einstellungen zeichnen ihre Kopfzeile selbst.
            if (screen != AppScreen.RIDE && screen != AppScreen.SETTINGS) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            screen.label.uppercase(java.util.Locale.GERMANY),
                            fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, letterSpacing = 0.8.sp,
                        )
                    },
                    navigationIcon = {
                        screen.parent?.let { parent ->
                            IconButton(onClick = { screen = parent }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = AppColors.Bg),
                )
            }
        },
        bottomBar = {
            // Waehrend der Fahrt gehoert der Platz dem Tacho.
            if (!(screen == AppScreen.RIDE && phase != RidePhase.IDLE)) {
                TabBar(current = screen.parent ?: screen, onSelect = { screen = it })
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (screen) {
                AppScreen.RIDE -> DashboardScreen(
                    vm,
                    onStartRequested = { locationLauncher.launch(LOCATION_PERMISSIONS) },
                    onMenu = {},
                    onOpen = { screen = it },
                )
                AppScreen.TRAINER -> TrainerScreen(vm, onStart = {
                    if (TRAINER_PERMISSIONS.isEmpty()) vm.startTrainer() else trainerLauncher.launch(TRAINER_PERMISSIONS)
                })
                AppScreen.RIDES -> RidesScreen(vm)
                AppScreen.MAPS -> MapsScreen(vm)
                AppScreen.SENSORS -> SensorsScreen(vm, onScanRequested = { kind ->
                    pendingScan = kind
                    bluetoothLauncher.launch(BLUETOOTH_PERMISSIONS)
                })
                AppScreen.SETTINGS -> SettingsScreen(
                    vm,
                    onConnectHealth = { healthLauncher.launch(vm.healthPermissions) },
                    onEnableSms = { smsLauncher.launch(Manifest.permission.SEND_SMS) },
                    onOpenSensors = { screen = AppScreen.SENSORS },
                    onOpenTrainer = { screen = AppScreen.TRAINER },
                )
            }
        }
    }

    UpdateDialog(vm)
}

/** Reiter unten: Symbol und Name in Grossbuchstaben, aktiver Reiter kraeftig. */
@Composable
private fun TabBar(current: AppScreen, onSelect: (AppScreen) -> Unit) {
    Column(Modifier.background(AppColors.Bg)) {
        HorizontalDivider(color = AppColors.Line)
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(62.dp)) {
            for (tab in AppScreen.entries.filter { it.tab }) {
                val selected = tab == current
                val color = if (selected) AppColors.Text else AppColors.Dim
                Column(
                    Modifier.weight(1f).fillMaxHeight().clickable { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(tab.icon, contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
                    Text(
                        tab.label.uppercase(java.util.Locale.GERMANY), color = color, fontSize = 13.sp,
                        fontFamily = Condensed, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        letterSpacing = 0.6.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdateDialog(vm: MainViewModel) {
    val update by vm.update.collectAsStateWithLifecycle()
    when (val u = update) {
        null -> Unit
        UpdateUi.Checking -> AlertDialog(
            onDismissRequest = vm::dismissUpdate,
            text = { Text("Suche nach Updates …") },
            confirmButton = {},
        )
        UpdateUi.Downloading -> AlertDialog(
            onDismissRequest = {},
            text = { Text("Update wird heruntergeladen …") },
            confirmButton = {},
        )
        is UpdateUi.Available -> AlertDialog(
            onDismissRequest = vm::dismissUpdate,
            title = {
                Text("Update ${u.release.version}" + if (u.release.version.isAlpha) " (Alpha)" else "")
            },
            text = { Text(u.release.notes.ifBlank { "Neue Version verfügbar." }) },
            confirmButton = { TextButton(onClick = { vm.installUpdate(u.release) }) { Text("Installieren") } },
            dismissButton = { TextButton(onClick = vm::dismissUpdate) { Text("Später") } },
        )
        is UpdateUi.Message -> AlertDialog(
            onDismissRequest = vm::dismissUpdate,
            text = { Text(u.text) },
            confirmButton = { TextButton(onClick = vm::dismissUpdate) { Text("OK") } },
        )
    }
}
