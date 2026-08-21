package com.dublikunt.dmclient.auth

import kotlinx.coroutines.flow.Flow

data class SessionCookies(
    val cfClearance: String?,
    val csrfToken: String?,
    val sessionAffinity: String?
) {
    val isEmpty: Boolean
        get() = cfClearance == null && csrfToken == null && sessionAffinity == null

    companion object {
        const val CLEARANCE = "cf_clearance"
        const val CSRF_TOKEN = "csrftoken"
        const val SESSION_AFFINITY = "session-affinity"

        fun parse(raw: List<Pair<String, String>>): SessionCookies = SessionCookies(
            cfClearance = raw.firstOrNull { it.first == CLEARANCE }?.second,
            csrfToken = raw.firstOrNull { it.first == CSRF_TOKEN }?.second,
            sessionAffinity = raw.firstOrNull { it.first == SESSION_AFFINITY }?.second
        )
    }
}

fun hasClearance(raw: List<Pair<String, String>>): Boolean =
    raw.any { it.first == SessionCookies.CLEARANCE }

sealed interface SessionStatus {
    data object Checking : SessionStatus
    data object Active : SessionStatus
    data object NeedsChallenge : SessionStatus
}

interface SessionTokenStorage {
    suspend fun save(cookies: SessionCookies)
    suspend fun clear()
    suspend fun read(): SessionCookies?
}
