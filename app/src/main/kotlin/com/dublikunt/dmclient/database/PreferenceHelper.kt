package com.dublikunt.dmclient.database

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_prefs")

object PreferenceHelper {
    private val SESSION_AFFINITY_KEY = stringPreferencesKey("session_affinity")
    private val CSRF_TOKEN_KEY = stringPreferencesKey("csrftoken")
    private val PREFERRED_LANGUAGE_KEY = stringPreferencesKey("preferred_language")
    private val PIN_CODE_KEY = stringPreferencesKey("pin_code")

    suspend fun saveTokens(context: Context, session: String, token: String) {
        context.dataStore.edit { preferences ->
            preferences[SESSION_AFFINITY_KEY] = session
            preferences[CSRF_TOKEN_KEY] = token
        }
    }

    suspend fun deleteTokens(context: Context) {
        context.dataStore.edit { preferences ->
            preferences.remove(SESSION_AFFINITY_KEY)
            preferences.remove(CSRF_TOKEN_KEY)
        }
    }

    fun getSessionAffinity(context: Context): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            preferences[SESSION_AFFINITY_KEY]
        }
    }

    fun getCsrfToken(context: Context): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            preferences[CSRF_TOKEN_KEY]
        }
    }

    suspend fun savePreferredLanguage(context: Context, language: String) {
        context.dataStore.edit { preferences ->
            preferences[PREFERRED_LANGUAGE_KEY] = language
        }
    }

    fun getPreferredLanguage(context: Context): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            preferences[PREFERRED_LANGUAGE_KEY]
        }
    }

    suspend fun savePinCode(context: Context, pin: String) {
        context.dataStore.edit { preferences ->
            preferences[PIN_CODE_KEY] = pin
        }
    }

    fun getPinCode(context: Context): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            preferences[PIN_CODE_KEY]
        }
    }
}
