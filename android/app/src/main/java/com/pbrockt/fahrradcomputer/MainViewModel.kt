package com.pbrockt.fahrradcomputer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pbrockt.fahrradcomputer.data.AppSettings
import com.pbrockt.fahrradcomputer.sensors.FoundDevice
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
    private val heartRate = fahrradApp.heartRate
    val ride = fahrradApp.ride
    private val updater = UpdateChecker(app)

    val settings: StateFlow<AppSettings> =
        store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val hr = heartRate.state
    val scanResults = heartRate.scanResults
    val scanning = heartRate.scanning

    /** Sekundentakt fuer Uhrzeit und das Ausblenden veralteter Pulswerte. */
    val now: StateFlow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis())

    private val _rides = MutableStateFlow<List<File>>(emptyList())
    val rides: StateFlow<List<File>> = _rides.asStateFlow()

    private val _update = MutableStateFlow<UpdateUi?>(null)
    val update: StateFlow<UpdateUi?> = _update.asStateFlow()

    init {
        refreshRides()
        viewModelScope.launch {
            val s = store.settings.first()
            if (s.autoUpdateCheck && s.updateToken.isNotBlank()) checkForUpdates(manual = false)
        }
        viewModelScope.launch {
            // Nach dem Speichern einer Fahrt die Liste auffrischen.
            ride.finished.collect { if (it != null) refreshRides() }
        }
    }

    // --- Fahrt ---

    fun startRide() = ride.start()
    fun pauseRide() = ride.pause()
    fun resumeRide() = ride.resume()
    fun stopRide() = ride.stop()
    fun dismissFinished() = ride.dismissFinished()

    // --- Fahrten ---

    fun refreshRides() {
        viewModelScope.launch {
            _rides.value = withContext(Dispatchers.IO) {
                ride.ridesDir.listFiles { f -> f.extension == "gpx" }
                    ?.sortedByDescending { it.name }
                    .orEmpty()
            }
        }
    }

    fun deleteRide(file: File) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { file.delete() }
            refreshRides()
        }
    }

    // --- Sensoren ---

    fun startScan() = heartRate.startScan()
    fun stopScan() = heartRate.stopScan()
    fun retrySensors() = heartRate.retry()

    fun selectHrDevice(device: FoundDevice) {
        heartRate.stopScan()
        viewModelScope.launch { store.setHrDevice(device.address, device.name) }
    }

    fun forgetHrDevice() {
        viewModelScope.launch { store.setHrDevice(null, null) }
    }

    // --- Einstellungen ---

    fun setMaxHr(value: Int) = viewModelScope.launch { store.setMaxHr(value) }
    fun setWeight(value: Double) = viewModelScope.launch { store.setWeight(value) }
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
