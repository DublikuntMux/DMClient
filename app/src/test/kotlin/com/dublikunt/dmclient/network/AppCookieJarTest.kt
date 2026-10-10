package com.dublikunt.dmclient.network

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCookieJarTest {
    private val url = "https://nhentai.net/".toHttpUrl()
    private fun cookie(name: String, value: String, path: String = "/", expires: Long = Long.MAX_VALUE): Cookie = Cookie.Builder()
        .name(name).value(value).domain("nhentai.net").path(path).expiresAt(expires).build()

    @Test fun `responses merge and overwrite only matching identity`() {
        val jar = AppCookieJar()
        jar.saveFromResponse(url, listOf(cookie("clearance", "a"), cookie("session", "b")))
        jar.saveFromResponse(url, listOf(cookie("session", "c")))
        assertEquals(mapOf("clearance" to "a", "session" to "c"), jar.loadForRequest(url).associate { it.name to it.value })
    }

    @Test fun `same name on different paths survives and is scoped`() {
        val jar = AppCookieJar()
        jar.saveFromResponse(url, listOf(cookie("session", "root"), cookie("session", "gallery", "/g")))
        assertEquals(listOf("root"), jar.loadForRequest(url).map { it.value })
        assertEquals(setOf("root", "gallery"), jar.loadForRequest("https://nhentai.net/g/42".toHttpUrl()).map { it.value }.toSet())
        assertTrue(jar.loadForRequest("https://example.org/".toHttpUrl()).isEmpty())
    }

    @Test fun `expired response deletes matching cookie without removing others`() {
        val jar = AppCookieJar()
        jar.saveFromResponse(url, listOf(cookie("a", "1"), cookie("b", "2")))
        jar.saveFromResponse(url, listOf(cookie("a", "", expires = 1)))
        assertEquals(listOf("b"), jar.loadForRequest(url).map { it.name })
    }

    @Test fun `domain cookies share with subdomains but secure cookies require https`() {
        val jar = AppCookieJar()
        jar.saveFromResponse(url, listOf(Cookie.Builder().name("secure").value("yes").domain("nhentai.net").secure().build()))
        assertEquals(1, jar.loadForRequest("https://t.nhentai.net/".toHttpUrl()).size)
        assertTrue(jar.loadForRequest("http://nhentai.net/".toHttpUrl()).isEmpty())
    }
}
