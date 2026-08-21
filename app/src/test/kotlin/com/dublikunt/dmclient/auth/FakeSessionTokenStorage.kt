package com.dublikunt.dmclient.auth

class FakeSessionTokenStorage : SessionTokenStorage {
    var saved: SessionCookies? = null

    override suspend fun save(cookies: SessionCookies) {
        saved = cookies
    }

    override suspend fun clear() {
        saved = null
    }

    override suspend fun read(): SessionCookies? = saved
}
