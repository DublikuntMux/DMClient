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
    private val CF_CLEARANCE_KEY = stringPreferencesKey("cf_clearance")
    private val PREFERRED_LANGUAGE_KEY = stringPreferencesKey("preferred_language")
    private val PIN_CODE_KEY = stringPreferencesKey("pin_code")
    private val MAX_IMAGE_CACHE_SIZE_KEY = stringPreferencesKey("max_image_cache_size")

    val sessionAffinity: Flow<String?> = dataStore.data.map { it[SESSION_AFFINITY_KEY] }
    val csrfToken: Flow<String?> = dataStore.data.map { it[CSRF_TOKEN_KEY] }
    val cfClearance: Flow<String?> = dataStore.data.map { it[CF_CLEARANCE_KEY] }
    val preferredLanguage: Flow<String?> = dataStore.data.map { it[PREFERRED_LANGUAGE_KEY] }
    val pinCode: Flow<String?> = dataStore.data.map { it[PIN_CODE_KEY] }
    val maxImageCacheSize: Flow<Long?> = dataStore.data.map {
        it[MAX_IMAGE_CACHE_SIZE_KEY]?.toLongOrNull()
    }

    suspend fun saveTokens(cookies: List<Pair<String, String>>) {
        dataStore.edit { prefs ->
            val session = cookies.firstOrNull { it.first == "session-affinity" }?.second
            val token = cookies.firstOrNull { it.first == "csrftoken" }?.second
            val clearance = cookies.firstOrNull { it.first == "cf_clearance" }?.second
            if (session != null) prefs[SESSION_AFFINITY_KEY] = session else prefs.remove(
                SESSION_AFFINITY_KEY
            )
            if (token != null) prefs[CSRF_TOKEN_KEY] = token else prefs.remove(CSRF_TOKEN_KEY)
            if (clearance != null) prefs[CF_CLEARANCE_KEY] = clearance else prefs.remove(
                CF_CLEARANCE_KEY
            )
        }
    }

    suspend fun deleteTokens() {
        dataStore.edit { prefs ->
            prefs.remove(SESSION_AFFINITY_KEY)
            prefs.remove(CSRF_TOKEN_KEY)
            prefs.remove(CF_CLEARANCE_KEY)
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
