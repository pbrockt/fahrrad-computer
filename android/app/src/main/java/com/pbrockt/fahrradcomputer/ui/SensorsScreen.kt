package com.pbrockt.fahrradcomputer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pbrockt.fahrradcomputer.MainViewModel
import com.pbrockt.fahrradcomputer.data.SavedDevice
import com.pbrockt.fahrradcomputer.sensors.FoundDevice
import com.pbrockt.fahrradcomputer.sensors.SensorConnection
import com.pbrockt.fahrradcomputer.sensors.SensorKind
import com.pbrockt.fahrradcomputer.sensors.SensorReading

@Composable
fun SensorsScreen(vm: MainViewModel, onScanRequested: (SensorKind) -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val found by vm.scanResults.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()

    DisposableEffect(Unit) { onDispose { vm.stopScan() } }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        for (kind in SensorKind.entries) {
            val reading by vm.sensorReading(kind).collectAsStateWithLifecycle()
            SensorSection(
                kind = kind,
                saved = settings.device(kind),
                reading = reading,
                scanning = scanning == kind,
                otherScanning = scanning != null && scanning != kind,
                found = if (scanning == kind) found else emptyList(),
                onScan = { onScanRequested(kind) },
                onSelect = { vm.selectDevice(kind, it) },
                onRetry = vm::retrySensors,
                onForget = { vm.forgetDevice(kind) },
            )
        }
    }
}

@Composable
private fun SensorSection(
    kind: SensorKind,
    saved: SavedDevice?,
    reading: SensorReading,
    scanning: Boolean,
    otherScanning: Boolean,
    found: List<FoundDevice>,
    onScan: () -> Unit,
    onSelect: (FoundDevice) -> Unit,
    onRetry: () -> Unit,
    onForget: () -> Unit,
) {
    Text(kind.label, fontWeight = FontWeight.Bold)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            if (saved == null) {
                Text("Kein Gerät gekoppelt.", color = AppColors.Dim)
            } else {
                Text(saved.name)
                val unit = when (kind) {
                    SensorKind.HEART_RATE -> "bpm"
                    SensorKind.CADENCE_SPEED -> "U/min"
                    SensorKind.POWER -> "W"
                }
                val status = when (reading.connection) {
                    SensorConnection.CONNECTED -> "verbunden" + (reading.value?.let { " · $it $unit" } ?: "")
                    SensorConnection.CONNECTING -> "verbinde …"
                    SensorConnection.NO_PERMISSION -> "Bluetooth-Berechtigung fehlt"
                    SensorConnection.BLUETOOTH_OFF -> "Bluetooth ist ausgeschaltet"
                    SensorConnection.NONE -> "nicht verbunden (bietet das Gerät diesen Dienst?)"
                }
                Text(status, color = if (reading.connection == SensorConnection.CONNECTED) AppColors.Go else AppColors.Dim)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onScan, enabled = !scanning && !otherScanning) {
                    Text(if (scanning) "Suche …" else "Suchen")
                }
                if (saved != null) {
                    OutlinedButton(onClick = onRetry) { Text("Neu verbinden") }
                    OutlinedButton(onClick = onForget) { Text("Entfernen") }
                }
            }
        }
    }
    Text(hint(kind), color = AppColors.Dim)

    if (scanning && found.isEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text("  Suche nach Geräten …", color = AppColors.Dim)
        }
    }
    for (device in found) {
        Card(Modifier.fillMaxWidth().clickable { onSelect(device) }) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(device.name)
                    Text(device.address, color = AppColors.Dim)
                }
                Text("${device.rssi} dBm", color = AppColors.Dim)
            }
        }
    }
    Spacer(Modifier.height(4.dp))
}

private fun hint(kind: SensorKind): String = when (kind) {
    SensorKind.HEART_RATE ->
        "Brustgurt anlegen (Kontakte anfeuchten). Garmin-Uhren: „Herzfrequenz senden“ einschalten."
    SensorKind.CADENCE_SPEED ->
        "Externer Trittfrequenz-Sensor an der Kurbel. Zum Aufwecken einmal kurz treten."
    SensorKind.POWER ->
        "Leistungsmesser oder Bosch Smart System, falls es Fahrerleistung per Bluetooth sendet. " +
            "Liefert es auch Kurbeldaten, kommt die Trittfrequenz automatisch mit. " +
            "Erscheint das Rad nicht, ggf. am Display/Remote die Kopplung für Fitnessgeräte aktivieren."
}
