package com.pbrockt.fahrradcomputer.sensors

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/** Die drei Bluetooth-Standardprofile, die die App versteht. */
enum class SensorKind(
    val label: String,
    val service: UUID,
    val measurement: UUID,
) {
    HEART_RATE("Puls", uuid16(0x180D), uuid16(0x2A37)),
    CADENCE_SPEED("Trittfrequenz / Speed", uuid16(0x1816), uuid16(0x2A5B)),
    POWER("Leistung", uuid16(0x1818), uuid16(0x2A63)),
}

internal fun uuid16(id: Int): UUID = UUID.fromString(String.format("0000%04x-0000-1000-8000-00805f9b34fb", id))

enum class SensorConnection { NONE, NO_PERMISSION, BLUETOOTH_OFF, CONNECTING, CONNECTED }

data class SensorLink(
    val connection: SensorConnection = SensorConnection.NONE,
    val deviceName: String? = null,
)

/**
 * Eine Bluetooth-LE-Verbindung zu einem Sensor eines [SensorKind]: verbinden,
 * Benachrichtigungen fuer den Messwert einschalten, jeden Messwert roh an [onMeasurement]
 * geben. Das Auslesen der Bytes macht [SensorHub] - hier geht es nur um die Verbindung.
 *
 * Reisst die Verbindung ab, wird mit autoConnect neu verbunden: Android wartet dann selbst,
 * bis der Sensor wieder in Reichweite ist - ohne Dauer-Scan, der den Akku leert.
 */
@SuppressLint("MissingPermission") // Rechte prueft SensorHub vor jedem Aufruf.
class BleSensor(
    private val kind: SensorKind,
    private val hub: SensorHub,
    private val scope: CoroutineScope,
    private val onMeasurement: (ByteArray, Long) -> Unit,
) {
    private val _link = MutableStateFlow(SensorLink())
    val link: StateFlow<SensorLink> = _link.asStateFlow()

    private var targetAddress: String? = null
    private var targetName: String? = null
    private var gatt: BluetoothGatt? = null
    private var reconnectJob: Job? = null

    val hasTarget: Boolean get() = targetAddress != null

    /** Legt fest, mit welchem Geraet verbunden sein soll (null = keins). */
    fun setTarget(address: String?, name: String?) {
        if (address == targetAddress && gatt != null) return
        disconnect()
        targetAddress = address
        targetName = name
        connect()
    }

    /** Erneuter Versuch, z. B. nachdem Rechte erteilt oder Bluetooth eingeschaltet wurde. */
    fun retry() {
        if (_link.value.connection != SensorConnection.CONNECTED) {
            closeGatt()
            connect()
        }
    }

    private fun connect(autoConnect: Boolean = false) {
        val address = targetAddress
        if (address == null) {
            _link.value = SensorLink()
            return
        }
        if (!hub.hasPermissions()) {
            _link.value = SensorLink(SensorConnection.NO_PERMISSION, targetName)
            return
        }
        val bt = hub.adapter
        if (bt == null || !bt.isEnabled) {
            _link.value = SensorLink(SensorConnection.BLUETOOTH_OFF, targetName)
            return
        }
        val device = runCatching { bt.getRemoteDevice(address) }.getOrNull() ?: return
        _link.value = SensorLink(SensorConnection.CONNECTING, targetName)
        gatt = device.connectGatt(hub.context, autoConnect, callback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        reconnectJob?.cancel()
        targetAddress = null
        closeGatt()
        _link.value = SensorLink()
    }

    private fun closeGatt() {
        gatt?.let {
            runCatching { it.disconnect() }
            runCatching { it.close() }
        }
        gatt = null
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
                    _link.value = SensorLink(SensorConnection.CONNECTING, targetName)
                    reconnectJob?.cancel()
                    reconnectJob = scope.launch {
                        delay(RECONNECT_DELAY_MS)
                        connect(autoConnect = true)
                    }
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val ch = g.getService(kind.service)?.getCharacteristic(kind.measurement)
            if (ch == null) {
                // Das Geraet bietet diesen Dienst nicht an - falsches Geraet gewaehlt.
                _link.value = SensorLink(SensorConnection.NONE, targetName)
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
            _link.value = SensorLink(SensorConnection.CONNECTED, targetName ?: g.device.address)
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
            if (characteristic.uuid != kind.measurement) return
            onMeasurement(value, System.currentTimeMillis())
        }
    }

    companion object {
        val CCCD: UUID = uuid16(0x2902)
        const val RECONNECT_DELAY_MS = 2_000L
    }
}
