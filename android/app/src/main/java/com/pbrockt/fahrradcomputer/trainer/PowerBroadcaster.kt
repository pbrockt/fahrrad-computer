package com.pbrockt.fahrradcomputer.trainer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import com.pbrockt.fahrradcomputer.sensors.uuid16
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

enum class BroadcastStatus { OFF, STARTING, ADVERTISING, ERROR }

data class BroadcastState(
    val status: BroadcastStatus = BroadcastStatus.OFF,
    /** Unter diesem Namen taucht das Handy in Zwift auf (der Bluetooth-Name des Handys). */
    val advertisedName: String? = null,
    /** Geraete, die gerade verbunden sind (PC, Tablet ...). */
    val clients: List<String> = emptyList(),
    val error: String? = null,
)

/**
 * Das Handy als Bluetooth-Sensor: Es bietet einen Leistungsmesser (Cycling Power `0x1818`)
 * und einen Pulsgurt (`0x180D`) an, damit Zwift, Rouvy oder MyWhoosh auf einem PC oder
 * Tablet es wie echte Sensoren koppeln. Den echten Brustgurt braucht man dort dann nicht -
 * viele Gurte erlauben ohnehin nur eine Verbindung, und die hat schon die App.
 *
 * Rechte (BLUETOOTH_ADVERTISE/CONNECT) prueft der Aufrufer vor [start].
 */
@SuppressLint("MissingPermission")
class PowerBroadcaster(private val context: Context) {

    private val _state = MutableStateFlow(BroadcastState())
    val state: StateFlow<BroadcastState> = _state.asStateFlow()

    private val manager get() = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
    private var server: BluetoothGattServer? = null
    private var powerChar: BluetoothGattCharacteristic? = null
    private var hrChar: BluetoothGattCharacteristic? = null
    private var advertising = false
    private var withName = true

    /** Wer welche Messwerte abonniert hat (CCCD geschrieben). */
    private val subscribed = ConcurrentHashMap<String, MutableSet<java.util.UUID>>()
    private val connected = ConcurrentHashMap<String, BluetoothDevice>()

    fun start() {
        if (server != null) return
        val adapter = manager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            _state.value = BroadcastState(BroadcastStatus.ERROR, error = "Bluetooth ist ausgeschaltet.")
            return
        }
        if (adapter.bluetoothLeAdvertiser == null || !adapter.isMultipleAdvertisementSupported) {
            _state.value = BroadcastState(
                BroadcastStatus.ERROR,
                error = "Dieses Handy kann sich nicht als Bluetooth-Sensor anbieten.",
            )
            return
        }
        _state.value = BroadcastState(BroadcastStatus.STARTING)
        val s = manager?.openGattServer(context, callback)
        if (s == null) {
            _state.value = BroadcastState(BroadcastStatus.ERROR, error = "Bluetooth-Server ließ sich nicht öffnen.")
            return
        }
        server = s
        withName = true
        // Dienste nacheinander - der zweite erst, wenn der erste angekommen ist (onServiceAdded).
        s.addService(powerService())
    }

    fun stop() {
        stopAdvertising()
        server?.let { s ->
            for (d in connected.values) runCatching { s.cancelConnection(d) }
            runCatching { s.close() }
        }
        server = null
        powerChar = null
        hrChar = null
        subscribed.clear()
        connected.clear()
        _state.value = BroadcastState()
    }

    fun sendPower(value: ByteArray) = notify(powerChar, value)

    fun sendHeartRate(value: ByteArray) = notify(hrChar, value)

    private fun notify(ch: BluetoothGattCharacteristic?, value: ByteArray) {
        val s = server ?: return
        ch ?: return
        ch.setValueCompat(value)
        for ((address, uuids) in subscribed) {
            if (ch.uuid !in uuids) continue
            val device = connected[address] ?: continue
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    s.notifyCharacteristicChanged(device, ch, false, value)
                } else {
                    @Suppress("DEPRECATION")
                    s.notifyCharacteristicChanged(device, ch, false)
                }
            }
        }
    }

    private fun powerService() = BluetoothGattService(CPS, BluetoothGattService.SERVICE_TYPE_PRIMARY).apply {
        val measurement = notifyCharacteristic(CPS_MEASUREMENT)
        powerChar = measurement
        addCharacteristic(measurement)
        addCharacteristic(readCharacteristic(CPS_FEATURE, GattEncoders.powerFeature))
        addCharacteristic(readCharacteristic(SENSOR_LOCATION, byteArrayOf(SENSOR_LOCATION_REAR_HUB)))
    }

    private fun heartRateService() = BluetoothGattService(HRS, BluetoothGattService.SERVICE_TYPE_PRIMARY).apply {
        val measurement = notifyCharacteristic(HR_MEASUREMENT)
        hrChar = measurement
        addCharacteristic(measurement)
        addCharacteristic(readCharacteristic(BODY_SENSOR_LOCATION, byteArrayOf(BODY_LOCATION_CHEST)))
    }

    private fun notifyCharacteristic(uuid: java.util.UUID) = BluetoothGattCharacteristic(
        uuid,
        BluetoothGattCharacteristic.PROPERTY_NOTIFY,
        0,
    ).apply {
        addDescriptor(
            BluetoothGattDescriptor(
                CCCD,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
            )
        )
    }

    private fun readCharacteristic(uuid: java.util.UUID, value: ByteArray) = BluetoothGattCharacteristic(
        uuid,
        BluetoothGattCharacteristic.PROPERTY_READ,
        BluetoothGattCharacteristic.PERMISSION_READ,
    ).apply { setValueCompat(value) }

    // --- Werbung (Advertising) ---

    private fun startAdvertising() {
        val advertiser = manager?.adapter?.bluetoothLeAdvertiser ?: return
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .setTimeout(0)
            .build()
        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(CPS))
            .addServiceUuid(ParcelUuid(HRS))
            .setIncludeDeviceName(false)
            .build()
        // Der Name kommt in die Scan-Antwort; ist er zu lang, geht es auch ohne.
        val response = AdvertiseData.Builder().setIncludeDeviceName(withName).build()
        runCatching { advertiser.startAdvertising(settings, data, response, advertiseCallback) }
            .onFailure { fail("Werbung ließ sich nicht starten: ${it.message}") }
    }

    private fun stopAdvertising() {
        if (!advertising) return
        runCatching { manager?.adapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback) }
        advertising = false
    }

    /** Nach einer Trennung wieder sichtbar werden - manche Handys hoeren beim Verbinden auf. */
    private fun restartAdvertising() {
        if (server == null) return
        stopAdvertising()
        startAdvertising()
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            advertising = true
            val name = if (withName) runCatching { manager?.adapter?.name }.getOrNull() else null
            _state.update { it.copy(status = BroadcastStatus.ADVERTISING, advertisedName = name, error = null) }
        }

        override fun onStartFailure(errorCode: Int) {
            when (errorCode) {
                ADVERTISE_FAILED_ALREADY_STARTED -> advertising = true
                ADVERTISE_FAILED_DATA_TOO_LARGE -> if (withName) {
                    withName = false
                    startAdvertising()
                } else {
                    fail("Werbedaten zu groß.")
                }
                else -> fail("Bluetooth-Werbung fehlgeschlagen (Code $errorCode).")
            }
        }
    }

    private fun fail(message: String) {
        _state.update { it.copy(status = BroadcastStatus.ERROR, error = message) }
    }

    private fun publishClients() {
        val names = connected.values.map { d -> runCatching { d.name }.getOrNull() ?: d.address }
        _state.update { it.copy(clients = names) }
    }

    private val callback = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                fail("Bluetooth-Dienst ließ sich nicht anlegen (Code $status).")
                return
            }
            if (service.uuid == CPS) server?.addService(heartRateService()) else startAdvertising()
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connected[device.address] = device
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connected.remove(device.address)
                subscribed.remove(device.address)
                restartAdvertising()
            }
            publishClients()
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic,
        ) {
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: ByteArray(0)
            val part = if (offset < value.size) value.copyOfRange(offset, value.size) else ByteArray(0)
            server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, part)
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor,
        ) {
            val on = descriptor.characteristic.uuid in subscribed[device.address].orEmpty()
            val value = if (on) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value)
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?,
        ) {
            if (descriptor.uuid == CCCD) {
                val uuids = subscribed.getOrPut(device.address) { ConcurrentHashMap.newKeySet() }
                val enable = value != null && value.isNotEmpty() && value[0].toInt() and 0x01 != 0
                if (enable) uuids += descriptor.characteristic.uuid else uuids -= descriptor.characteristic.uuid
                // Manche Zentralen schreiben das CCCD, ohne vorher als verbunden gemeldet zu sein.
                connected.putIfAbsent(device.address, device)
            }
            if (responseNeeded) server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value)
        }
    }

    companion object {
        val CPS = uuid16(0x1818)
        val CPS_MEASUREMENT = uuid16(0x2A63)
        val CPS_FEATURE = uuid16(0x2A65)
        val SENSOR_LOCATION = uuid16(0x2A5D)
        val HRS = uuid16(0x180D)
        val HR_MEASUREMENT = uuid16(0x2A37)
        val BODY_SENSOR_LOCATION = uuid16(0x2A38)
        val CCCD = uuid16(0x2902)
        private const val SENSOR_LOCATION_REAR_HUB: Byte = 13
        private const val BODY_LOCATION_CHEST: Byte = 1
    }
}

@Suppress("DEPRECATION")
private fun BluetoothGattCharacteristic.setValueCompat(value: ByteArray) {
    // Lesen ueber onCharacteristicReadRequest nimmt den hier gespeicherten Wert.
    this.value = value
}
