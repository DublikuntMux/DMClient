package com.dublikunt.dmclient.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppCookieJar @Inject constructor() : CookieJar {
    private data class Key(val name: String, val domain: String, val path: String)

    private val cookies = mutableMapOf<Key, Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { this.cookies[Key(it.name, it.domain, it.path)] = it }
        dropExpired()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        dropExpired()
        return cookies.values.filter { it.matches(url) }
    }

    private fun dropExpired() {
        val now = System.currentTimeMillis()
        cookies.entries.removeAll { it.value.expiresAt <= now }
    }
}
