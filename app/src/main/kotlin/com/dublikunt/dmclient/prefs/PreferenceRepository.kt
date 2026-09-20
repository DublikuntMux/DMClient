package com.dublikunt.dmclient.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferenceRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val PREFERRED_LANGUAGE_KEY = stringPreferencesKey("preferred_language")
    private val PIN_CODE_KEY = stringPreferencesKey("pin_code")
    private val MAX_IMAGE_CACHE_SIZE_KEY = stringPreferencesKey("max_image_cache_size")

    val preferredLanguage: Flow<String?> = dataStore.data.map { it[PREFERRED_LANGUAGE_KEY] }
    val pinCode: Flow<String?> = dataStore.data.map { it[PIN_CODE_KEY] }
    val maxImageCacheSize: Flow<Long?> = dataStore.data.map {
        it[MAX_IMAGE_CACHE_SIZE_KEY]?.toLongOrNull()
    }

    suspend fun savePreferredLanguage(language: String) {
        dataStore.edit { prefs ->
            prefs[PREFERRED_LANGUAGE_KEY] = language
        }
    }

    suspend fun savePinCode(pin: String) {
        dataStore.edit { prefs ->
            prefs[PIN_CODE_KEY] = pin
        }
    }

    suspend fun saveMaxImageCacheSize(size: Long) {
        dataStore.edit { prefs ->
            prefs[MAX_IMAGE_CACHE_SIZE_KEY] = size.toString()
        }
    }

    companion object {
        private const val MB = 1024L * 1024
        private const val GB = 1024L * MB

        const val DEFAULT_MAX_IMAGE_CACHE_SIZE = GB

        val IMAGE_CACHE_SIZE_OPTIONS = listOf(
            128 * MB,
            256 * MB,
            512 * MB,
            GB,
            2 * GB,
            4 * GB
        )

        fun coerceImageCacheSize(size: Long?): Long {
            if (size == null) return DEFAULT_MAX_IMAGE_CACHE_SIZE
            return IMAGE_CACHE_SIZE_OPTIONS.minByOrNull { kotlin.math.abs(it - size) }
                ?: DEFAULT_MAX_IMAGE_CACHE_SIZE
        }
    }
}
