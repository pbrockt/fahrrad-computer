package com.pbrockt.fahrradcomputer

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pbrockt.fahrradcomputer.sensors.SensorKind
import com.pbrockt.fahrradcomputer.ui.AppColors
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

private enum class Tab(val label: String, val icon: ImageVector) {
    RIDE("Fahrt", Icons.AutoMirrored.Filled.DirectionsBike),
    RIDES("Fahrten", Icons.AutoMirrored.Filled.List),
    MAPS("Karten", Icons.Filled.Map),
    SENSORS("Sensoren", Icons.Filled.Bluetooth),
    SETTINGS("Einstellungen", Icons.Filled.Settings),
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

@Composable
private fun AppRoot(vm: MainViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.RIDE) }
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                for (t in Tab.entries) {
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                Tab.RIDE -> DashboardScreen(vm, onStartRequested = { locationLauncher.launch(LOCATION_PERMISSIONS) })
                Tab.RIDES -> RidesScreen(vm)
                Tab.MAPS -> MapsScreen(vm)
                Tab.SENSORS -> SensorsScreen(vm, onScanRequested = { kind ->
                    pendingScan = kind
                    bluetoothLauncher.launch(BLUETOOTH_PERMISSIONS)
                })
                Tab.SETTINGS -> SettingsScreen(
                    vm,
                    onConnectHealth = { healthLauncher.launch(vm.healthPermissions) },
                    onEnableSms = { smsLauncher.launch(Manifest.permission.SEND_SMS) },
                )
            }
        }
    }

    UpdateDialog(vm)
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
