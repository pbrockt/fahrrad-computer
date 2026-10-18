package com.pbrockt.fahrradcomputer.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pbrockt.fahrradcomputer.recording.BodyProfile
import com.pbrockt.fahrradcomputer.recording.Sex
import com.pbrockt.fahrradcomputer.sensors.SensorKind
import com.pbrockt.fahrradcomputer.ui.ThemeMode
import com.pbrockt.fahrradcomputer.ui.dashboard.DashboardLayout
import com.pbrockt.fahrradcomputer.ui.dashboard.LayoutCodec
import com.pbrockt.fahrradcomputer.update.UpdateChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate

private val Context.dataStore by preferencesDataStore(name = "einstellungen")

data class AppSettings(
    val maxHr: Int = HrZones.DEFAULT_MAX_HR,
    /** Koerpergewicht in kg - fuer die Kalorien. */
    val weightKg: Double = 0.0,
    /** 0 = nicht angegeben. Alter und Geschlecht braucht die Puls-Kalorienformel. */
    val birthYear: Int = 0,
    val sex: Sex? = null,
    /** Gekoppelte Sensoren je Art. */
    val devices: Map<SensorKind, SavedDevice> = emptyMap(),
    val updateChannel: UpdateChannel = UpdateChannel.RELEASE,
    val autoUpdateCheck: Boolean = true,
    /**
     * GitHub-Token mit Leserecht auf das Repo. Nur noetig, falls es privat ist - dann gibt
     * die API die Releases ohne ihn nicht heraus. Liegt nur auf diesem Geraet.
     */
    val updateToken: String = "",
    val themeMode: ThemeMode = ThemeMode.DARK,
    /** Beim Start das neueste Gewicht aus Health Connect uebernehmen. */
    val healthWeightSync: Boolean = true,
    /** Live-Standort per SMS waehrend der Fahrt. */
    val liveSmsEnabled: Boolean = false,
    /** Empfaenger als Freitext; gelesen ueber [com.pbrockt.fahrradcomputer.live.LiveSms.parseNumbers]. */
    val liveSmsNumbers: String = "",
    val liveSmsIntervalMin: Int = 10,
    /** Zusaetzlich "losgefahren" und "angekommen" melden. */
    val liveSmsStartStop: Boolean = true,
    /** Name am Anfang jeder SMS, damit der Empfaenger weiss, von wem sie ist. */
    val riderName: String = "",
    /** Id der Route, die gerade nachgefahren wird; null = keine. */
    val activeRouteId: String? = null,
    /** Offline-Karten nur im WLAN laden - sie sind mehrere hundert MB gross. */
    val mapsWifiOnly: Boolean = true,
    /** Ohne Offline-Karte die Kartenbilder von openstreetmap.org laden. */
    val mapOnline: Boolean = true,
    /** Abbiegehinweise per Vibration bzw. Ton ankuendigen. */
    val navVibrate: Boolean = true,
    val navSound: Boolean = false,
    /** Tacho-Karte in Fahrtrichtung drehen statt Norden oben. */
    val mapHeadingUp: Boolean = false,
    /** Trainingsplan: Ziel, Wochentage, freie Hinweise (z. B. Beschwerden). */
    val coachGoal: String = "",
    val coachDays: Set<DayOfWeek> = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY),
    val coachNotes: String = "",
    /** So lange muss man auf dem Tacho halten, bis der Editor aufgeht. */
    val editHoldMs: Int = 1000,
    /** Doppeltippen auf den Tacho laesst die Klingel ertoenen. */
    val bellOnDoubleTap: Boolean = true,
    val dashboard: DashboardLayout = DashboardLayout.DEFAULT,
) {
    val zones: HrZones get() = HrZones(maxHr)

    fun device(kind: SensorKind): SavedDevice? = devices[kind]

    val body: BodyProfile
        get() = BodyProfile(
            weightKg = weightKg,
            age = birthYear.takeIf { it > 0 }?.let { LocalDate.now().year - it },
            sex = sex,
        )
}

data class SavedDevice(val address: String, val name: String)

class SettingsStore(private val context: Context) {

    private object Keys {
        val MAX_HR = intPreferencesKey("max_hr")
        val WEIGHT = doublePreferencesKey("weight_kg")
        val BIRTH_YEAR = intPreferencesKey("birth_year")
        val SEX = stringPreferencesKey("sex")
        // Der Pulsgurt behaelt seine alten Schluessel aus Phase 1, damit er gekoppelt bleibt.
        fun address(kind: SensorKind) = stringPreferencesKey(prefix(kind) + "_address")
        fun name(kind: SensorKind) = stringPreferencesKey(prefix(kind) + "_name")
        private fun prefix(kind: SensorKind) = when (kind) {
            SensorKind.HEART_RATE -> "hr"
            SensorKind.CADENCE_SPEED -> "cad"
            SensorKind.POWER -> "power"
        }
        val UPDATE_CHANNEL = stringPreferencesKey("update_channel")
        val AUTO_UPDATE_CHECK = booleanPreferencesKey("auto_update_check")
        val UPDATE_TOKEN = stringPreferencesKey("update_token")
        val THEME = stringPreferencesKey("theme")
        val HEALTH_WEIGHT_SYNC = booleanPreferencesKey("health_weight_sync")
        val LIVE_SMS = booleanPreferencesKey("live_sms")
        val LIVE_SMS_NUMBERS = stringPreferencesKey("live_sms_numbers")
        val LIVE_SMS_INTERVAL = intPreferencesKey("live_sms_interval")
        val LIVE_SMS_START_STOP = booleanPreferencesKey("live_sms_start_stop")
        val RIDER_NAME = stringPreferencesKey("rider_name")
        val ACTIVE_ROUTE = stringPreferencesKey("active_route")
        val MAPS_WIFI_ONLY = booleanPreferencesKey("maps_wifi_only")
        val MAP_ONLINE = booleanPreferencesKey("map_online")
        val NAV_VIBRATE = booleanPreferencesKey("nav_vibrate")
        val NAV_SOUND = booleanPreferencesKey("nav_sound")
        val MAP_HEADING_UP = booleanPreferencesKey("map_heading_up")
        val COACH_GOAL = stringPreferencesKey("coach_goal")
        val COACH_DAYS = stringPreferencesKey("coach_days")
        val COACH_NOTES = stringPreferencesKey("coach_notes")
        val EDIT_HOLD_MS = intPreferencesKey("edit_hold_ms")
        val BELL = booleanPreferencesKey("bell_double_tap")
        val DASHBOARD = stringPreferencesKey("dashboard_layout")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            maxHr = p[Keys.MAX_HR] ?: HrZones.DEFAULT_MAX_HR,
            weightKg = p[Keys.WEIGHT] ?: 0.0,
            birthYear = p[Keys.BIRTH_YEAR] ?: 0,
            sex = p[Keys.SEX]?.let { runCatching { Sex.valueOf(it) }.getOrNull() },
            devices = SensorKind.entries.mapNotNull { kind ->
                p[Keys.address(kind)]?.let { kind to SavedDevice(it, p[Keys.name(kind)] ?: it) }
            }.toMap(),
            updateChannel = p[Keys.UPDATE_CHANNEL]
                ?.let { runCatching { UpdateChannel.valueOf(it) }.getOrNull() }
                ?: UpdateChannel.RELEASE,
            autoUpdateCheck = p[Keys.AUTO_UPDATE_CHECK] ?: true,
            updateToken = p[Keys.UPDATE_TOKEN] ?: "",
            healthWeightSync = p[Keys.HEALTH_WEIGHT_SYNC] ?: true,
            liveSmsEnabled = p[Keys.LIVE_SMS] ?: false,
            liveSmsNumbers = p[Keys.LIVE_SMS_NUMBERS] ?: "",
            liveSmsIntervalMin = p[Keys.LIVE_SMS_INTERVAL] ?: 10,
            liveSmsStartStop = p[Keys.LIVE_SMS_START_STOP] ?: true,
            riderName = p[Keys.RIDER_NAME] ?: "",
            activeRouteId = p[Keys.ACTIVE_ROUTE],
            mapsWifiOnly = p[Keys.MAPS_WIFI_ONLY] ?: true,
            mapOnline = p[Keys.MAP_ONLINE] ?: true,
            navVibrate = p[Keys.NAV_VIBRATE] ?: true,
            navSound = p[Keys.NAV_SOUND] ?: false,
            mapHeadingUp = p[Keys.MAP_HEADING_UP] ?: false,
            coachGoal = p[Keys.COACH_GOAL] ?: "",
            coachDays = p[Keys.COACH_DAYS]
                ?.split(',')?.mapNotNull { n -> runCatching { DayOfWeek.valueOf(n) }.getOrNull() }?.toSet()
                ?: AppSettings().coachDays,
            coachNotes = p[Keys.COACH_NOTES] ?: "",
            editHoldMs = p[Keys.EDIT_HOLD_MS] ?: 1000,
            bellOnDoubleTap = p[Keys.BELL] ?: true,
            themeMode = p[Keys.THEME]
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.DARK,
            // Unlesbares oder fehlendes Layout -> Standard, statt eines leeren Bildschirms.
            dashboard = LayoutCodec.decode(p[Keys.DASHBOARD]) ?: DashboardLayout.DEFAULT,
        )
    }

    suspend fun setMaxHr(value: Int) = set(Keys.MAX_HR, value.coerceIn(120, 230))

    suspend fun setWeight(value: Double) = set(Keys.WEIGHT, value.coerceIn(0.0, 300.0))

    suspend fun setBirthYear(value: Int) = set(Keys.BIRTH_YEAR, value.coerceIn(0, LocalDate.now().year))

    suspend fun setSex(value: Sex) = set(Keys.SEX, value.name)

    suspend fun setDevice(kind: SensorKind, address: String?, name: String?) {
        context.dataStore.edit {
            if (address == null) {
                it.remove(Keys.address(kind))
                it.remove(Keys.name(kind))
            } else {
                it[Keys.address(kind)] = address
                it[Keys.name(kind)] = name ?: address
            }
        }
    }

    suspend fun setUpdateChannel(value: UpdateChannel) = set(Keys.UPDATE_CHANNEL, value.name)

    suspend fun setAutoUpdateCheck(value: Boolean) = set(Keys.AUTO_UPDATE_CHECK, value)

    suspend fun setUpdateToken(value: String) = set(Keys.UPDATE_TOKEN, value.trim())

    suspend fun setThemeMode(value: ThemeMode) = set(Keys.THEME, value.name)


    suspend fun setHealthWeightSync(value: Boolean) = set(Keys.HEALTH_WEIGHT_SYNC, value)

    suspend fun setLiveSmsEnabled(value: Boolean) = set(Keys.LIVE_SMS, value)
    suspend fun setLiveSmsNumbers(value: String) = set(Keys.LIVE_SMS_NUMBERS, value)
    suspend fun setLiveSmsInterval(value: Int) = set(Keys.LIVE_SMS_INTERVAL, value.coerceIn(1, 60))
    suspend fun setLiveSmsStartStop(value: Boolean) = set(Keys.LIVE_SMS_START_STOP, value)
    suspend fun setRiderName(value: String) = set(Keys.RIDER_NAME, value.trim().take(30))
    suspend fun setMapsWifiOnly(value: Boolean) = set(Keys.MAPS_WIFI_ONLY, value)
    suspend fun setMapOnline(value: Boolean) = set(Keys.MAP_ONLINE, value)
    suspend fun setNavVibrate(value: Boolean) = set(Keys.NAV_VIBRATE, value)
    suspend fun setNavSound(value: Boolean) = set(Keys.NAV_SOUND, value)
    suspend fun setMapHeadingUp(value: Boolean) = set(Keys.MAP_HEADING_UP, value)
    suspend fun setEditHoldMs(value: Int) = set(Keys.EDIT_HOLD_MS, value.coerceIn(300, 5000))
    suspend fun setBellOnDoubleTap(value: Boolean) = set(Keys.BELL, value)
    suspend fun setCoachGoal(value: String) = set(Keys.COACH_GOAL, value.take(300))
    suspend fun setCoachNotes(value: String) = set(Keys.COACH_NOTES, value.take(500))
    suspend fun setCoachDays(value: Set<DayOfWeek>) = set(Keys.COACH_DAYS, value.joinToString(",") { it.name })

    suspend fun setActiveRoute(id: String?) {
        context.dataStore.edit { if (id == null) it.remove(Keys.ACTIVE_ROUTE) else it[Keys.ACTIVE_ROUTE] = id }
    }

    /**
     * Wendet eine Aenderung auf das *gespeicherte* Layout an - innerhalb einer Transaktion.
     * So gehen zwei schnelle Tipps im Editor nicht verloren, weil der zweite noch auf
     * dem alten Stand aufgesetzt haette.
     */
    suspend fun editDashboard(change: (DashboardLayout) -> DashboardLayout) {
        context.dataStore.edit { p ->
            val current = LayoutCodec.decode(p[Keys.DASHBOARD]) ?: DashboardLayout.DEFAULT
            p[Keys.DASHBOARD] = LayoutCodec.encode(change(current))
        }
    }

    suspend fun resetDashboard() {
        context.dataStore.edit { it.remove(Keys.DASHBOARD) }
    }

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }
}
