package com.pbrockt.fahrradcomputer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pbrockt.fahrradcomputer.data.AppSettings
import com.pbrockt.fahrradcomputer.health.HealthAvailability
import com.pbrockt.fahrradcomputer.recording.RideSummary
import com.pbrockt.fahrradcomputer.recording.Sex
import com.pbrockt.fahrradcomputer.recording.TrackPoint
import com.pbrockt.fahrradcomputer.ui.formatDecimal
import com.pbrockt.fahrradcomputer.sensors.FoundDevice
import com.pbrockt.fahrradcomputer.sensors.SensorKind
import com.pbrockt.fahrradcomputer.ui.ThemeMode
import com.pbrockt.fahrradcomputer.ui.dashboard.DashboardLayout
import com.pbrockt.fahrradcomputer.update.ReleaseInfo
import com.pbrockt.fahrradcomputer.update.UpdateChannel
import com.pbrockt.fahrradcomputer.update.UpdateCheck
import com.pbrockt.fahrradcomputer.update.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class HealthState(val availability: HealthAvailability, val granted: Set<String>) {
    val connected: Boolean get() = granted.isNotEmpty()
}

/** Zustand der Update-Pruefung fuer die Oberflaeche. */
sealed interface UpdateUi {
    data object Checking : UpdateUi
    data class Available(val release: ReleaseInfo) : UpdateUi
    data object Downloading : UpdateUi
    data class Message(val text: String) : UpdateUi
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val fahrradApp = app as FahrradApp
    private val store = fahrradApp.settings
    private val sensors = fahrradApp.sensors
    val ride = fahrradApp.ride
    private val updater = UpdateChecker(app)

    val settings: StateFlow<AppSettings> =
        store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val hr = sensors.heartRate
    val cadence = sensors.cadence
    val power = sensors.power
    val scanResults = sensors.scanResults
    val scanning = sensors.scanning

    /** Sekundentakt fuer Uhrzeit und das Ausblenden veralteter Pulswerte. */
    val now: StateFlow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis())

    private val repository = fahrradApp.rides
    private val health = fahrradApp.health

    /** Alle Fahrten, neueste zuerst. */
    val rides: StateFlow<List<RideSummary>> = repository.rides

    private val _healthState = MutableStateFlow(HealthState(health.availability, emptySet()))
    val healthState: StateFlow<HealthState> = _healthState.asStateFlow()
    val healthPermissions: Set<String> get() = health.permissions
    val healthContract get() = health.requestContract

    /** Kurze Rueckmeldung fuer die Snackbar (Health Connect, Gewicht ...). */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _update = MutableStateFlow<UpdateUi?>(null)
    val update: StateFlow<UpdateUi?> = _update.asStateFlow()

    init {
        refreshHealth()
        viewModelScope.launch {
            val s = store.settings.first()
            // Das Repo ist oeffentlich - die Pruefung braucht keinen Token mehr.
            if (s.autoUpdateCheck) checkForUpdates(manual = false)
        }
    }

    // --- Fahrt ---

    fun startRide() = ride.start()
    fun pauseRide() = ride.pause()
    fun resumeRide() = ride.resume()
    fun stopRide() = ride.stop()
    fun dismissFinished() = ride.dismissFinished()

    // --- Fahrten ---

    fun gpxFile(id: String): File = repository.gpxFile(id)

    suspend fun loadTrack(id: String): List<TrackPoint> = repository.readTrack(id)

    fun deleteRide(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun syncRide(id: String) {
        viewModelScope.launch {
            _message.value = repository.sync(id) ?: "In Health Connect gespeichert."
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    // --- Health Connect ---

    fun refreshHealth() {
        viewModelScope.launch {
            _healthState.value = HealthState(health.availability, health.granted())
        }
    }

    fun onHealthPermissionsResult() {
        refreshHealth()
        viewModelScope.launch { fahrradApp.importWeight() }
    }

    fun importWeightNow() {
        viewModelScope.launch {
            val kg = fahrradApp.importWeight(force = true)
            _message.value = kg?.let { "Gewicht übernommen: ${formatDecimal(it)} kg" }
                ?: "Kein Gewicht in Health Connect gefunden (oder keine Leseberechtigung)."
        }
    }

    fun setHealthAutoSync(value: Boolean) = viewModelScope.launch { store.setHealthAutoSync(value) }
    fun setHealthWeightSync(value: Boolean) = viewModelScope.launch { store.setHealthWeightSync(value) }

    // --- Sensoren ---

    fun sensorReading(kind: SensorKind) = sensors.reading(kind)
    fun startScan(kind: SensorKind) = sensors.startScan(kind)
    fun stopScan() = sensors.stopScan()
    fun retrySensors() = sensors.retryAll()

    fun selectDevice(kind: SensorKind, device: FoundDevice) {
        sensors.stopScan()
        viewModelScope.launch { store.setDevice(kind, device.address, device.name) }
    }

    fun forgetDevice(kind: SensorKind) {
        viewModelScope.launch { store.setDevice(kind, null, null) }
    }

    // --- Einstellungen ---

    fun setMaxHr(value: Int) = viewModelScope.launch { store.setMaxHr(value) }
    fun setWeight(value: Double) = viewModelScope.launch { store.setWeight(value) }
    fun setBirthYear(value: Int) = viewModelScope.launch { store.setBirthYear(value) }
    fun setSex(value: Sex) = viewModelScope.launch { store.setSex(value) }
    fun setUpdateChannel(value: UpdateChannel) = viewModelScope.launch { store.setUpdateChannel(value) }
    fun setAutoUpdateCheck(value: Boolean) = viewModelScope.launch { store.setAutoUpdateCheck(value) }
    fun setUpdateToken(value: String) = viewModelScope.launch { store.setUpdateToken(value) }
    fun setThemeMode(value: ThemeMode) = viewModelScope.launch { store.setThemeMode(value) }

    // --- Dashboard ---

    fun editDashboard(change: (DashboardLayout) -> DashboardLayout) = viewModelScope.launch { store.editDashboard(change) }
    fun resetDashboard() = viewModelScope.launch { store.resetDashboard() }

    // --- Updates ---

    fun checkForUpdates(manual: Boolean = true) {
        viewModelScope.launch {
            if (manual) _update.value = UpdateUi.Checking
            val s = store.settings.first()
            _update.value = when (val r = updater.check(s.updateChannel, s.updateToken)) {
                is UpdateCheck.Available -> UpdateUi.Available(r.release)
                UpdateCheck.UpToDate -> if (manual) UpdateUi.Message("Du hast die neueste Version (${s.updateChannel.label}-Kanal).") else null
                is UpdateCheck.Failed -> if (manual) UpdateUi.Message(r.reason) else null
            }
        }
    }

    fun installUpdate(release: ReleaseInfo) {
        viewModelScope.launch {
            _update.value = UpdateUi.Downloading
            val error = updater.downloadAndInstall(release, settings.value.updateToken)
            _update.value = error?.let { UpdateUi.Message(it) }
        }
    }

    fun dismissUpdate() {
        _update.value = null
    }
}
