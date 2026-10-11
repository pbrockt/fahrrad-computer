package com.pbrockt.fahrradcomputer.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pbrockt.fahrradcomputer.update.UpdateChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "einstellungen")

data class AppSettings(
    val maxHr: Int = HrZones.DEFAULT_MAX_HR,
    /** Koerpergewicht in kg - fuer die Kalorien (Phase 3). */
    val weightKg: Double = 0.0,
    /** MAC-Adresse und Name des gekoppelten Pulsgurts. */
    val hrDeviceAddress: String? = null,
    val hrDeviceName: String? = null,
    val updateChannel: UpdateChannel = UpdateChannel.RELEASE,
    val autoUpdateCheck: Boolean = true,
    /**
     * GitHub-Token mit Leserecht auf das Repo. Noetig, solange es privat ist - ohne ihn
     * gibt die API die Releases nicht heraus. Liegt nur auf diesem Geraet.
     */
    val updateToken: String = "",
) {
    val zones: HrZones get() = HrZones(maxHr)
}

class SettingsStore(private val context: Context) {

    private object Keys {
        val MAX_HR = intPreferencesKey("max_hr")
        val WEIGHT = doublePreferencesKey("weight_kg")
        val HR_ADDRESS = stringPreferencesKey("hr_address")
        val HR_NAME = stringPreferencesKey("hr_name")
        val UPDATE_CHANNEL = stringPreferencesKey("update_channel")
        val AUTO_UPDATE_CHECK = booleanPreferencesKey("auto_update_check")
        val UPDATE_TOKEN = stringPreferencesKey("update_token")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            maxHr = p[Keys.MAX_HR] ?: HrZones.DEFAULT_MAX_HR,
            weightKg = p[Keys.WEIGHT] ?: 0.0,
            hrDeviceAddress = p[Keys.HR_ADDRESS],
            hrDeviceName = p[Keys.HR_NAME],
            updateChannel = p[Keys.UPDATE_CHANNEL]
                ?.let { runCatching { UpdateChannel.valueOf(it) }.getOrNull() }
                ?: UpdateChannel.RELEASE,
            autoUpdateCheck = p[Keys.AUTO_UPDATE_CHECK] ?: true,
            updateToken = p[Keys.UPDATE_TOKEN] ?: "",
        )
    }

    suspend fun setMaxHr(value: Int) = set(Keys.MAX_HR, value.coerceIn(120, 230))

    suspend fun setWeight(value: Double) = set(Keys.WEIGHT, value.coerceIn(0.0, 300.0))

    suspend fun setHrDevice(address: String?, name: String?) {
        context.dataStore.edit {
            if (address == null) {
                it.remove(Keys.HR_ADDRESS)
                it.remove(Keys.HR_NAME)
            } else {
                it[Keys.HR_ADDRESS] = address
                it[Keys.HR_NAME] = name ?: address
            }
        }
    }

    suspend fun setUpdateChannel(value: UpdateChannel) = set(Keys.UPDATE_CHANNEL, value.name)

    suspend fun setAutoUpdateCheck(value: Boolean) = set(Keys.AUTO_UPDATE_CHECK, value)

    suspend fun setUpdateToken(value: String) = set(Keys.UPDATE_TOKEN, value.trim())

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }
}
