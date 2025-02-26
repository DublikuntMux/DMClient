package com.dublikunt.dmclient.scrapper

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class EasyCookieJar : CookieJar {
    private val cookieStore: MutableMap<String, List<Cookie>> = mutableMapOf()

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return cookieStore.getOrDefault(url.host, ArrayList())
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookieStore[url.host] = cookies
    }

    fun setCookie(url: HttpUrl, name: String, value: String) {
        val cookie: Cookie = Cookie.Builder()
            .domain(url.host)
            .path("/")
            .name(name)
            .value(value)
            .build()

        val cookies: MutableList<Cookie> = ArrayList()
        cookies.add(cookie)
        cookieStore[url.host] = cookies
    }

    fun setCookieSecure(url: HttpUrl, name: String, value: String) {
        val cookie: Cookie = Cookie.Builder()
            .domain(url.host)
            .path("/")
            .name(name)
            .value(value)
            .httpOnly()
            .secure()
            .build()

        val cookies: MutableList<Cookie> = ArrayList()
        cookies.add(cookie)
        cookieStore[url.host] = cookies
    }
}