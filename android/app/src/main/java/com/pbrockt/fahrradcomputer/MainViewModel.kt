package com.pbrockt.fahrradcomputer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pbrockt.fahrradcomputer.data.AppSettings
import android.net.Uri
import com.pbrockt.fahrradcomputer.coach.Coach
import com.pbrockt.fahrradcomputer.coach.CoachPrompt
import com.pbrockt.fahrradcomputer.coach.CoachRequest
import com.pbrockt.fahrradcomputer.coach.StoredPlan
import java.time.DayOfWeek
import java.time.LocalDate
import com.pbrockt.fahrradcomputer.health.HealthAvailability
import com.pbrockt.fahrradcomputer.live.LiveSms
import com.pbrockt.fahrradcomputer.live.SmsSender
import com.pbrockt.fahrradcomputer.maps.MapRegion
import com.pbrockt.fahrradcomputer.maps.MapState
import com.pbrockt.fahrradcomputer.routes.Route
import com.pbrockt.fahrradcomputer.recording.RideSummary
import com.pbrockt.fahrradcomputer.recording.Sex
import com.pbrockt.fahrradcomputer.recording.TrackPoint
import com.pbrockt.fahrradcomputer.ui.BellPlayer
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

    private val routeStore = fahrradApp.routes
    private val maps = fahrradApp.offlineMaps
    private val sms = SmsSender(app)

    val routes: StateFlow<List<Route>> = routeStore.routes
    val activeRoute = ride.activeRoute
    val routeProgress = ride.progress
    val lastPoint = ride.lastPoint
    val offlineMaps: StateFlow<List<File>> = maps.installed
    val mapStates: StateFlow<Map<String, MapState>> = maps.states

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

    // --- Routen & Karten ---

    fun importRoute(uri: Uri) {
        viewModelScope.launch {
            routeStore.import(uri)
                .onSuccess { r ->
                    store.setActiveRoute(r.id)
                    _message.value = "Route „${r.name}“ importiert (${formatDecimal(r.lengthM / 1000)} km) und aktiviert."
                }
                .onFailure { _message.value = it.message ?: "Import fehlgeschlagen." }
        }
    }

    fun activateRoute(id: String?) = viewModelScope.launch { store.setActiveRoute(id) }

    fun deleteRoute(id: String) {
        viewModelScope.launch {
            if (settings.value.activeRouteId == id) store.setActiveRoute(null)
            routeStore.delete(id)
        }
    }

    fun refreshMaps() = viewModelScope.launch { maps.refresh() }
    fun downloadMap(region: MapRegion) {
        maps.start(region, settings.value.mapsWifiOnly)
        if (settings.value.mapsWifiOnly) _message.value = "Download startet, sobald WLAN verbunden ist."
    }
    fun cancelMap(region: MapRegion) = maps.cancel(region)
    fun deleteMap(region: MapRegion) = maps.delete(region)
    fun setMapsWifiOnly(value: Boolean) = viewModelScope.launch { store.setMapsWifiOnly(value) }
    fun setMapOnline(value: Boolean) = viewModelScope.launch { store.setMapOnline(value) }
    fun setNavVibrate(value: Boolean) = viewModelScope.launch { store.setNavVibrate(value) }
    fun setNavSound(value: Boolean) = viewModelScope.launch { store.setNavSound(value) }
    val guidance = ride.guidance
    val heading = ride.heading
    fun toggleMapOrientation() = viewModelScope.launch { store.setMapHeadingUp(!settings.value.mapHeadingUp) }
    val turns = ride.turns

    // --- Bedienung ---

    private val bell = BellPlayer()

    fun ringBell() {
        if (settings.value.bellOnDoubleTap) bell.ring()
    }

    /** Zum Ausprobieren in den Einstellungen - auch wenn Doppeltippen aus ist. */
    fun ringBellNow() = bell.ring()

    fun setEditHoldMs(value: Int) = viewModelScope.launch { store.setEditHoldMs(value) }
    fun setBellOnDoubleTap(value: Boolean) = viewModelScope.launch { store.setBellOnDoubleTap(value) }

    override fun onCleared() {
        bell.release()
    }

    // --- Trainingsplan ---

    private val coachStore = fahrradApp.coach
    val plan: StateFlow<StoredPlan?> = coachStore.plan
    val hasCoachKey: StateFlow<Boolean> = coachStore.hasKey

    private val _coachBusy = MutableStateFlow(false)
    val coachBusy: StateFlow<Boolean> = _coachBusy.asStateFlow()

    fun setCoachGoal(value: String) = viewModelScope.launch { store.setCoachGoal(value) }
    fun setCoachNotes(value: String) = viewModelScope.launch { store.setCoachNotes(value) }
    fun toggleCoachDay(day: DayOfWeek) = viewModelScope.launch {
        val days = settings.value.coachDays
        store.setCoachDays(if (day in days) days - day else days + day)
    }

    private fun coachRequest(): CoachRequest {
        val s = settings.value
        return CoachRequest(s.coachGoal, s.coachDays, s.coachNotes, s.body, s.maxHr, LocalDate.now())
    }

    /** Genau der Text, der an Claude geht - zum Anzeigen vor dem Senden. */
    fun coachPreview(): String = CoachPrompt.user(coachRequest(), rides.value, plan.value)

    fun setCoachKey(key: String) {
        coachStore.setApiKey(key)
        if (key.isNotBlank()) checkCoachKey()
    }

    fun checkCoachKey() {
        val key = coachStore.apiKey() ?: run { _message.value = "Noch kein API-Key gespeichert."; return }
        viewModelScope.launch {
            _coachBusy.value = true
            _message.value = Coach.checkKey(key) ?: "API-Key funktioniert."
            _coachBusy.value = false
        }
    }

    fun createPlan() {
        val key = coachStore.apiKey() ?: run { _message.value = "Bitte zuerst einen API-Key eintragen."; return }
        if (settings.value.coachDays.isEmpty()) { _message.value = "Bitte mindestens einen Trainingstag wählen."; return }
        val request = coachRequest()
        viewModelScope.launch {
            _coachBusy.value = true
            when (val r = Coach.createPlan(key, CoachPrompt.system, CoachPrompt.user(request, rides.value, plan.value))) {
                is Coach.Result.Ok -> {
                    coachStore.save(StoredPlan(r.plan, System.currentTimeMillis(), request.startDate.toString(), r.costUsd))
                    _message.value = "Neuer Plan erstellt (Kosten ca. ${String.format(java.util.Locale.GERMANY, "%.2f", r.costUsd)} \$)."
                }
                is Coach.Result.Error -> _message.value = r.message
            }
            _coachBusy.value = false
        }
    }

    fun deletePlan() = viewModelScope.launch { coachStore.clearPlan() }

    // --- Live-SMS ---

    fun setLiveSmsEnabled(value: Boolean) = viewModelScope.launch { store.setLiveSmsEnabled(value) }
    fun setLiveSmsNumbers(value: String) = viewModelScope.launch { store.setLiveSmsNumbers(value) }
    fun setLiveSmsInterval(value: Int) = viewModelScope.launch { store.setLiveSmsInterval(value) }
    fun setLiveSmsStartStop(value: Boolean) = viewModelScope.launch { store.setLiveSmsStartStop(value) }
    fun setRiderName(value: String) = viewModelScope.launch { store.setRiderName(value) }

    fun sendTestSms() {
        val s = settings.value
        val numbers = LiveSms.parseNumbers(s.liveSmsNumbers)
        _message.value = when {
            numbers.isEmpty() -> "Bitte zuerst mindestens eine Telefonnummer eintragen."
            !sms.canSend() -> "SMS-Berechtigung fehlt oder das Handy kann keine SMS senden."
            else -> {
                val sent = sms.send(numbers, "${s.riderName.ifBlank { "Fahrrad-Computer" }}: Test der Live-SMS. So sieht ein Standort aus: ${LiveSms.mapsLink(50.35, 7.6)}")
                "Test-SMS an $sent von ${numbers.size} Nummer(n) übergeben."
            }
        }
    }

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
