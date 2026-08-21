package com.dublikunt.dmclient.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrefsSessionTokenStorage @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SessionTokenStorage {

    override suspend fun save(cookies: SessionCookies) {
        dataStore.edit { prefs ->
            if (cookies.sessionAffinity != null) prefs[SESSION_AFFINITY_KEY] = cookies.sessionAffinity
            else prefs.remove(SESSION_AFFINITY_KEY)
            if (cookies.csrfToken != null) prefs[CSRF_TOKEN_KEY] = cookies.csrfToken
            else prefs.remove(CSRF_TOKEN_KEY)
            if (cookies.cfClearance != null) prefs[CLEARANCE_KEY] = cookies.cfClearance
            else prefs.remove(CLEARANCE_KEY)
        }
    }

    override suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(SESSION_AFFINITY_KEY)
            prefs.remove(CSRF_TOKEN_KEY)
            prefs.remove(CLEARANCE_KEY)
        }
    }

    override suspend fun read(): SessionCookies? {
        val prefs = dataStore.data.first()
        val cookies = SessionCookies(
            cfClearance = prefs[CLEARANCE_KEY],
            csrfToken = prefs[CSRF_TOKEN_KEY],
            sessionAffinity = prefs[SESSION_AFFINITY_KEY]
        )
        return if (cookies.isEmpty) null else cookies
    }

    private companion object {
        val SESSION_AFFINITY_KEY = stringPreferencesKey("session_affinity")
        val CSRF_TOKEN_KEY = stringPreferencesKey("csrftoken")
        val CLEARANCE_KEY = stringPreferencesKey("cf_clearance")
    }
}
