package com.pbrockt.fahrradcomputer.coach

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Speichert den aktuellen Trainingsplan und den API-Key.
 *
 * Der Key wird mit einem Schluessel aus dem Android Keystore verschluesselt. Der liegt in
 * der Hardware des Handys und laesst sich nicht auslesen oder mitsichern - der Key
 * landet also weder im Klartext auf dem Speicher noch in einem Cloud-Backup.
 */
class CoachStore(private val context: Context) {

    private val prefs = context.getSharedPreferences("coach_geheim", Context.MODE_PRIVATE)
    private val planFile = File(context.filesDir, "training/plan.json").apply { parentFile?.mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _plan = MutableStateFlow<StoredPlan?>(null)
    val plan: StateFlow<StoredPlan?> = _plan.asStateFlow()

    private val _hasKey = MutableStateFlow(prefs.contains(KEY_PREF))
    val hasKey: StateFlow<Boolean> = _hasKey.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        _plan.value = planFile.takeIf { it.exists() }
            ?.let { runCatching { json.decodeFromString<StoredPlan>(it.readText()) }.getOrNull() }
    }

    suspend fun save(plan: StoredPlan) = withContext(Dispatchers.IO) {
        val tmp = File(planFile.parentFile, "plan.json.tmp")
        tmp.writeText(json.encodeToString(plan))
        tmp.renameTo(planFile)
        _plan.value = plan
    }

    suspend fun clearPlan() = withContext(Dispatchers.IO) {
        planFile.delete()
        _plan.value = null
    }

    fun setApiKey(key: String?) {
        val k = key?.trim().orEmpty()
        if (k.isEmpty()) {
            prefs.edit().remove(KEY_PREF).apply()
        } else {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
            val data = cipher.iv + cipher.doFinal(k.toByteArray(Charsets.UTF_8))
            prefs.edit().putString(KEY_PREF, Base64.encodeToString(data, Base64.NO_WRAP)).apply()
        }
        _hasKey.value = k.isNotEmpty()
    }

    fun apiKey(): String? {
        val stored = prefs.getString(KEY_PREF, null) ?: return null
        return runCatching {
            val data = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, data, 0, IV_BYTES))
            String(cipher.doFinal(data, IV_BYTES, data.size - IV_BYTES), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    companion object {
        private const val ALIAS = "coach_api_key"
        private const val KEY_PREF = "api_key_enc"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
    }
}
