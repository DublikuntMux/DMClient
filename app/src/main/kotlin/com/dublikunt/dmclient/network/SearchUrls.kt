package com.dublikunt.dmclient.network

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

internal fun latestUrl(page: Int, language: ContentLanguage): HttpUrl {
    require(page > 0)
    return NHentaiApi.BASE_URL.toHttpUrl().newBuilder().apply {
        if (language != ContentLanguage.All) addPathSegment("language").addPathSegment(language.key)
        if (page > 1) addQueryParameter("page", page.toString())
    }.build()
}

internal fun searchUrl(request: SearchRequest, page: Int): HttpUrl {
    require(page > 0)
    require(request.text.isNotBlank() || request.include.isNotEmpty() || request.exclude.isNotEmpty()) {
        "Search requires text or a tag filter"
    }
    fun quoted(name: String): String = name.replace("\\", "\\\\").replace("\"", "\\\"")
    val query = buildList {
        request.text.trim().takeIf { it.isNotEmpty() }?.let { add(it) }
        request.include.forEach { add("${it.type.key}:\"${quoted(it.name)}\"") }
        request.exclude.forEach { add("-${it.type.key}:\"${quoted(it.name)}\"") }
        if (request.language != ContentLanguage.All) add("language:\"${request.language.key}\"")
    }.joinToString(" ")
    return NHentaiApi.BASE_URL.toHttpUrl().newBuilder()
        .addPathSegment("search").addPathSegment("")
        .addQueryParameter("q", query).apply {
            request.sort.key?.let { addQueryParameter("sort", it) }
            if (page > 1) addQueryParameter("page", page.toString())
        }.build()
}
