package com.dublikunt.dmclient.auth

import com.dublikunt.dmclient.scrapper.EasyCookieJar
import com.dublikunt.dmclient.scrapper.NHentaiApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NhentaiSession @Inject constructor(
    private val cookieJar: EasyCookieJar,
    authRequired: Flow<Unit>,
    private val tokenStorage: SessionTokenStorage,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val _status = MutableStateFlow<SessionStatus>(SessionStatus.Checking)
    val status: StateFlow<SessionStatus> = _status.asStateFlow()

    init {
        scope.launch { restore() }
        scope.launch {
            authRequired.collect {
                if (_status.value == SessionStatus.Active) wipe()
            }
        }
    }

    suspend fun restore() {
        val cookies = tokenStorage.read()
        if (cookies == null || cookies.isEmpty) {
            _status.value = SessionStatus.NeedsChallenge
            return
        }
        setCookies(cookies.toPairs())
        _status.value = SessionStatus.Active
    }

    suspend fun adopt(rawCookies: List<Pair<String, String>>) {
        val cookies = SessionCookies.parse(rawCookies)
        if (!hasClearance(rawCookies)) {
            _status.value = SessionStatus.NeedsChallenge
            return
        }
        tokenStorage.save(cookies)
        setCookies(cookies.toPairs())
        _status.value = SessionStatus.Active
    }

    suspend fun wipe() {
        tokenStorage.clear()
        cookieJar.clear()
        _status.value = SessionStatus.NeedsChallenge
    }

    private fun setCookies(cookies: List<Pair<String, String>>) {
        val baseUrl = NHentaiApi.BASE_URL.toHttpUrl()
        cookies.forEach { (name, value) ->
            COOKIE_HOSTS.forEach { host ->
                cookieJar.setCookie(
                    baseUrl.newBuilder().host(host).build(),
                    name,
                    value,
                    secure = true
                )
            }
        }
    }
}

private val COOKIE_HOSTS = listOf("nhentai.net", "t.nhentai.net", "i1.nhentai.net")

private fun SessionCookies.toPairs(): List<Pair<String, String>> = listOfNotNull(
    sessionAffinity?.let { SessionCookies.SESSION_AFFINITY to it },
    csrfToken?.let { SessionCookies.CSRF_TOKEN to it },
    cfClearance?.let { SessionCookies.CLEARANCE to it }
)
