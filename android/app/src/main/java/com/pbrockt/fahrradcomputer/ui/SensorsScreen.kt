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
import androidx.compose.material3.Button
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
import com.pbrockt.fahrradcomputer.sensors.SensorConnection

@Composable
fun SensorsScreen(vm: MainViewModel, onScanRequested: () -> Unit) {
    val hr by vm.hr.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val found by vm.scanResults.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()

    DisposableEffect(Unit) { onDispose { vm.stopScan() } }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Pulsgurt / Uhr", fontWeight = FontWeight.Bold)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                if (settings.hrDeviceAddress == null) {
                    Text("Kein Gerät gekoppelt.", color = AppColors.Dim)
                } else {
                    Text(settings.hrDeviceName ?: settings.hrDeviceAddress!!)
                    val status = when (hr.connection) {
                        SensorConnection.CONNECTED -> "verbunden" + (hr.bpm?.let { " · $it bpm" } ?: "")
                        SensorConnection.CONNECTING -> "verbinde … (Gurt anlegen, Kontakte anfeuchten)"
                        SensorConnection.NO_PERMISSION -> "Bluetooth-Berechtigung fehlt"
                        SensorConnection.BLUETOOTH_OFF -> "Bluetooth ist ausgeschaltet"
                        SensorConnection.NONE -> "nicht verbunden"
                    }
                    Text(status, color = if (hr.connection == SensorConnection.CONNECTED) AppColors.Go else AppColors.Dim)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = vm::retrySensors) { Text("Neu verbinden") }
                        OutlinedButton(onClick = vm::forgetHrDevice) { Text("Entfernen") }
                    }
                }
            }
        }

        Button(onClick = onScanRequested, enabled = !scanning, modifier = Modifier.fillMaxWidth()) {
            Text(if (scanning) "Suche läuft …" else "Pulsgurt suchen")
        }
        Text(
            "Garmin-Uhren: am Uhr-Menü „Herzfrequenz senden“ einschalten, dann taucht die Uhr hier auf.",
            color = AppColors.Dim,
        )

        if (scanning && found.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("  Suche nach Geräten mit Pulsdienst …", color = AppColors.Dim)
            }
        }
        for (device in found) {
            Card(Modifier.fillMaxWidth().clickable { vm.selectHrDevice(device) }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(device.name)
                        Text(device.address, color = AppColors.Dim)
                    }
                    Text("${device.rssi} dBm", color = AppColors.Dim)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("Trittfrequenz & Bosch", fontWeight = FontWeight.Bold)
        Text(
            "Kommt in Phase 3. Vorher bitte das Rad mit der App „nRF Connect“ scannen und " +
                "notieren, welche Dienste es anbietet.",
            color = AppColors.Dim,
        )
    }
}
