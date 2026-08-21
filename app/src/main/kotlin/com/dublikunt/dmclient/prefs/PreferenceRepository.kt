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
}
