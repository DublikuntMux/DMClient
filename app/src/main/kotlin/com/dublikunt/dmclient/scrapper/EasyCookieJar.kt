package com.dublikunt.dmclient.scrapper

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class EasyCookieJar : CookieJar {
    private val lock = Any()
    private val cookieStore: MutableMap<String, List<Cookie>> = mutableMapOf()

    override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(lock) {
        cookieStore.getOrDefault(url.host, emptyList())
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(lock) { cookieStore[url.host] = cookies }
    }

    fun setCookie(url: HttpUrl, name: String, value: String, secure: Boolean = false) {
        val cookie = Cookie.Builder()
            .domain(url.host)
            .path("/")
            .name(name)
            .value(value)
            .apply { if (secure) httpOnly().secure() }
            .build()

        synchronized(lock) {
            val existing = cookieStore[url.host].orEmpty()
            cookieStore[url.host] = existing.filterNot { it.name == name } + cookie
        }
    }

    fun clear() {
        synchronized(lock) { cookieStore.clear() }
    }
}