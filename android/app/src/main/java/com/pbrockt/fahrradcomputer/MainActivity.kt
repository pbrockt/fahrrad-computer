package com.pbrockt.fahrradcomputer

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pbrockt.fahrradcomputer.ui.FahrradTheme
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
            FahrradTheme {
                AppRoot()
            }
        }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    RIDE("Fahrt", Icons.AutoMirrored.Filled.DirectionsBike),
    RIDES("Fahrten", Icons.AutoMirrored.Filled.List),
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
private fun AppRoot(vm: MainViewModel = viewModel()) {
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

    val bluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            vm.retrySensors()
            vm.startScan()
        } else {
            scope.launch { snackbar.showSnackbar("Ohne Bluetooth-Berechtigung lässt sich kein Gurt verbinden.") }
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
                Tab.SENSORS -> SensorsScreen(vm, onScanRequested = { bluetoothLauncher.launch(BLUETOOTH_PERMISSIONS) })
                Tab.SETTINGS -> SettingsScreen(vm)
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
