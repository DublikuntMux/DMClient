package com.dublikunt.dmclient.repository

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
    private val SESSION_AFFINITY_KEY = stringPreferencesKey("session_affinity")
    private val CSRF_TOKEN_KEY = stringPreferencesKey("csrftoken")
    private val PREFERRED_LANGUAGE_KEY = stringPreferencesKey("preferred_language")
    private val PIN_CODE_KEY = stringPreferencesKey("pin_code")
    private val MAX_IMAGE_CACHE_SIZE_KEY = stringPreferencesKey("max_image_cache_size")

    val sessionAffinity: Flow<String?> = dataStore.data.map { it[SESSION_AFFINITY_KEY] }
    val csrfToken: Flow<String?> = dataStore.data.map { it[CSRF_TOKEN_KEY] }
    val preferredLanguage: Flow<String?> = dataStore.data.map { it[PREFERRED_LANGUAGE_KEY] }
    val pinCode: Flow<String?> = dataStore.data.map { it[PIN_CODE_KEY] }
    val maxImageCacheSize: Flow<Long?> = dataStore.data.map {
        it[MAX_IMAGE_CACHE_SIZE_KEY]?.toLongOrNull()
    }

    suspend fun saveTokens(session: String, token: String) {
        dataStore.edit { prefs ->
            prefs[SESSION_AFFINITY_KEY] = session
            prefs[CSRF_TOKEN_KEY] = token
        }
    }

    suspend fun deleteTokens() {
        dataStore.edit { prefs ->
            prefs.remove(SESSION_AFFINITY_KEY)
            prefs.remove(CSRF_TOKEN_KEY)
        }
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
