package com.pbrockt.fahrradcomputer.sensors

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Verbindungsstand und letzter Messwert eines Sensors. */
data class SensorReading(
    val connection: SensorConnection = SensorConnection.NONE,
    val deviceName: String? = null,
    /** Akkustand in Prozent, wenn der Sensor ihn meldet. */
    val battery: Int? = null,
    /** bpm, U/min oder Watt - je nach Sensor. */
    val value: Int? = null,
    /** Zeitpunkt des letzten Messwerts; aeltere Werte zeigt das Dashboard nicht mehr an. */
    val updatedAtMs: Long = 0,
) {
    fun fresh(nowMs: Long): Int? = value?.takeIf { nowMs - updatedAtMs <= MAX_AGE_MS }

    companion object {
        const val MAX_AGE_MS = 5_000L
    }
}

data class FoundDevice(val address: String, val name: String, val rssi: Int)

/**
 * Alle Rad-Sensoren an einer Stelle: Pulsgurt, Trittfrequenz/Speed und Leistungsmesser.
 *
 * Ein Bosch Smart System, das Fahrerleistung und Trittfrequenz per Bluetooth-Standard
 * sendet, erscheint hier als Leistungsmesser - dann kommt die Trittfrequenz aus dessen
 * Kurbeldaten, solange kein eigener Trittfrequenz-Sensor verbunden ist.
 */
@SuppressLint("MissingPermission") // geprueft ueber hasPermissions()
class SensorHub(
    val context: Context,
    private val scope: CoroutineScope,
) {
    val adapter: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter

    private val readings = SensorKind.entries.associateWith { MutableStateFlow(SensorReading()) }

    val heartRate: StateFlow<SensorReading> = readings.getValue(SensorKind.HEART_RATE).asStateFlow()
    val cadence: StateFlow<SensorReading> = readings.getValue(SensorKind.CADENCE_SPEED).asStateFlow()
    val power: StateFlow<SensorReading> = readings.getValue(SensorKind.POWER).asStateFlow()

    fun reading(kind: SensorKind): StateFlow<SensorReading> = readings.getValue(kind).asStateFlow()

    private val cscCadence = CadenceCalculator()
    private val powerCadence = CadenceCalculator()

    private val sensors: Map<SensorKind, BleSensor> = SensorKind.entries.associateWith { kind ->
        BleSensor(kind, this, scope) { bytes, now -> onMeasurement(kind, bytes, now) }
    }

    init {
        for ((kind, sensor) in sensors) {
            scope.launch {
                sensor.link.collect { l ->
                    readings.getValue(kind).update { it.copy(connection = l.connection, deviceName = l.deviceName, battery = l.battery) }
                }
            }
        }
    }

    private fun onMeasurement(kind: SensorKind, bytes: ByteArray, now: Long) {
        when (kind) {
            SensorKind.HEART_RATE -> HeartRateParser.parse(bytes)?.let { publish(kind, it, now) }
            SensorKind.CADENCE_SPEED -> CyclingParsers.parseCscCrank(bytes)?.let {
                publish(kind, cscCadence.update(it, now), now)
            }
            SensorKind.POWER -> CyclingParsers.parsePower(bytes)?.let { m ->
                publish(kind, m.watts, now)
                // Trittfrequenz aus dem Leistungsmesser nur, wenn kein eigener Sensor dafuer da ist.
                val cscActive = cadence.value.connection == SensorConnection.CONNECTED &&
                    sensors.getValue(SensorKind.CADENCE_SPEED).hasTarget
                if (m.crank != null && !cscActive) {
                    val rpm = powerCadence.update(m.crank, now)
                    readings.getValue(SensorKind.CADENCE_SPEED).update { it.copy(value = rpm, updatedAtMs = now) }
                }
            }
        }
    }

    private fun publish(kind: SensorKind, value: Int, now: Long) {
        readings.getValue(kind).update { it.copy(value = value, updatedAtMs = now) }
    }

    fun hasPermissions(): Boolean {
        val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return needed.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun setTarget(kind: SensorKind, address: String?, name: String?) {
        if (kind == SensorKind.CADENCE_SPEED) cscCadence.reset()
        if (kind == SensorKind.POWER) powerCadence.reset()
        sensors.getValue(kind).setTarget(address, name)
    }

    fun retryAll() = sensors.values.forEach { it.retry() }

    // --- Suche ---

    private val _scanResults = MutableStateFlow<List<FoundDevice>>(emptyList())
    val scanResults: StateFlow<List<FoundDevice>> = _scanResults.asStateFlow()

    /** Wonach gerade gesucht wird, null = keine Suche. */
    private val _scanning = MutableStateFlow<SensorKind?>(null)
    val scanning: StateFlow<SensorKind?> = _scanning.asStateFlow()

    private var scanStopJob: Job? = null

    fun startScan(kind: SensorKind) {
        if (!hasPermissions()) return
        val scanner = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner ?: return
        stopScan()
        _scanResults.value = emptyList()
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(kind.service)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(listOf(filter), settings, scanCallback)
        _scanning.value = kind
        scanStopJob = scope.launch {
            delay(SCAN_DURATION_MS)
            stopScan()
        }
    }

    fun stopScan() {
        scanStopJob?.cancel()
        if (_scanning.value != null && hasPermissions()) {
            runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        }
        _scanning.value = null
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val found = FoundDevice(
                address = result.device.address,
                name = result.scanRecord?.deviceName ?: result.device.name ?: result.device.address,
                rssi = result.rssi,
            )
            _scanResults.update { list ->
                (list.filterNot { it.address == found.address } + found).sortedByDescending { it.rssi }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _scanning.value = null
        }
    }

    companion object {
        const val SCAN_DURATION_MS = 15_000L
    }
}
