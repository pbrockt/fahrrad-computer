package com.pbrockt.fahrradcomputer.sensors

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
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
import java.util.UUID

enum class SensorConnection { NONE, NO_PERMISSION, BLUETOOTH_OFF, CONNECTING, CONNECTED }

data class HeartRateState(
    val connection: SensorConnection = SensorConnection.NONE,
    val deviceName: String? = null,
    val bpm: Int? = null,
    /** Zeitpunkt des letzten Messwerts; aeltere Werte zeigt das Dashboard nicht mehr an. */
    val updatedAtMs: Long = 0,
)

data class FoundDevice(val address: String, val name: String, val rssi: Int)

/**
 * Pulsgurt ueber das Bluetooth-Standardprofil "Heart Rate" (Service 0x180D).
 *
 * Funktioniert mit jedem Brustgurt (Polar, Garmin HRM, Wahoo, ...) und mit Uhren, die den
 * Puls per Bluetooth senden (Garmin: "Herzfrequenz senden").
 *
 * Reisst die Verbindung ab, wird mit autoConnect neu verbunden: dann wartet Android selbst,
 * bis der Gurt wieder in Reichweite ist - ohne Dauer-Scan, der den Akku leert.
 */
@SuppressLint("MissingPermission") // Rechte werden in hasPermissions() geprueft.
class HeartRateSensor(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(HeartRateState())
    val state: StateFlow<HeartRateState> = _state.asStateFlow()

    private val _scanResults = MutableStateFlow<List<FoundDevice>>(emptyList())
    val scanResults: StateFlow<List<FoundDevice>> = _scanResults.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val adapter get() =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter

    private var targetAddress: String? = null
    private var targetName: String? = null
    private var gatt: BluetoothGatt? = null
    private var reconnectJob: Job? = null
    private var scanStopJob: Job? = null

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

    /** Legt fest, mit welchem Gurt verbunden sein soll (null = keiner). */
    fun setTarget(address: String?, name: String?) {
        if (address == targetAddress && gatt != null) return
        disconnect()
        targetAddress = address
        targetName = name
        connect()
    }

    /** Erneuter Versuch, z. B. nachdem Rechte erteilt oder Bluetooth eingeschaltet wurde. */
    fun retry() {
        if (_state.value.connection != SensorConnection.CONNECTED) {
            closeGatt()
            connect()
        }
    }

    private fun connect(autoConnect: Boolean = false) {
        val address = targetAddress
        if (address == null) {
            _state.value = HeartRateState()
            return
        }
        if (!hasPermissions()) {
            _state.value = HeartRateState(SensorConnection.NO_PERMISSION, targetName)
            return
        }
        val bt = adapter
        if (bt == null || !bt.isEnabled) {
            _state.value = HeartRateState(SensorConnection.BLUETOOTH_OFF, targetName)
            return
        }
        val device = runCatching { bt.getRemoteDevice(address) }.getOrNull() ?: return
        _state.value = HeartRateState(SensorConnection.CONNECTING, targetName)
        gatt = device.connectGatt(context, autoConnect, callback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        reconnectJob?.cancel()
        targetAddress = null
        closeGatt()
        _state.value = HeartRateState()
    }

    private fun closeGatt() {
        gatt?.let {
            runCatching { it.disconnect() }
            runCatching { it.close() }
        }
        gatt = null
    }

    fun startScan() {
        if (!hasPermissions()) {
            _state.update { it.copy(connection = SensorConnection.NO_PERMISSION) }
            return
        }
        val scanner = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner ?: run {
            _state.update { it.copy(connection = SensorConnection.BLUETOOTH_OFF) }
            return
        }
        stopScan()
        _scanResults.value = emptyList()
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(HR_SERVICE)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(listOf(filter), settings, scanCallback)
        _scanning.value = true
        scanStopJob = scope.launch {
            delay(SCAN_DURATION_MS)
            stopScan()
        }
    }

    fun stopScan() {
        scanStopJob?.cancel()
        if (_scanning.value && hasPermissions()) {
            runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        }
        _scanning.value = false
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
            _scanning.value = false
        }
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (g != gatt) return
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> g.discoverServices()
                BluetoothProfile.STATE_DISCONNECTED -> {
                    runCatching { g.close() }
                    gatt = null
                    if (targetAddress == null) return
                    _state.value = HeartRateState(SensorConnection.CONNECTING, targetName)
                    reconnectJob?.cancel()
                    reconnectJob = scope.launch {
                        delay(RECONNECT_DELAY_MS)
                        connect(autoConnect = true)
                    }
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val ch = g.getService(HR_SERVICE)?.getCharacteristic(HR_MEASUREMENT)
            if (ch == null) {
                // Kein Pulsdienst - falsches Geraet gewaehlt.
                _state.value = HeartRateState(SensorConnection.NONE, targetName)
                return
            }
            g.setCharacteristicNotification(ch, true)
            val cccd = ch.getDescriptor(CCCD) ?: return
            val enable = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(cccd, enable)
            } else {
                @Suppress("DEPRECATION")
                cccd.value = enable
                @Suppress("DEPRECATION")
                g.writeDescriptor(cccd)
            }
            _state.value = HeartRateState(SensorConnection.CONNECTED, targetName ?: g.device.address)
        }

        // Ab Android 13 kommt der Wert als Parameter. Nicht super aufrufen - das wuerde
        // zusaetzlich die alte Variante unten ausloesen und jeden Wert doppelt melden.
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) = onValue(characteristic, value)

        @Deprecated("Bis Android 12")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            onValue(characteristic, characteristic.value ?: return)
        }

        private fun onValue(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (characteristic.uuid != HR_MEASUREMENT) return
            val bpm = HeartRateParser.parse(value) ?: return
            _state.update {
                it.copy(connection = SensorConnection.CONNECTED, bpm = bpm, updatedAtMs = System.currentTimeMillis())
            }
        }
    }

    companion object {
        val HR_SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HR_MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        const val SCAN_DURATION_MS = 15_000L
        const val RECONNECT_DELAY_MS = 2_000L
    }
}
