package com.dublikunt.dmclient.scrapper

import android.net.Uri
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NHentaiApi @Inject constructor(
    private val client: OkHttpClient
) {
    companion object {
        const val BASE_URL = "https://nhentai.net"
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Mobile Safari/537.36"
    }

    private val _authRequired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val authRequired: SharedFlow<Unit> = _authRequired

    suspend fun fetchMainPage(
        page: Int? = null,
        language: ContentLanguage = ContentLanguage.All
    ): List<GallerySimpleInfo> {
        val responseBody = fetchData(buildUrlForPage(page, language)) ?: return emptyList()
        return NhentaiParser.parseGalleryList(responseBody)
    }

    suspend fun fetchGallery(id: Int): GalleryFullInfo? {
        val responseBody = fetchData("$BASE_URL/g/$id/") ?: return null
        return NhentaiParser.parseGallery(responseBody, id)
    }

    suspend fun getAllTags(): List<String> = fetchAllEntries("tag", "tags")
    suspend fun getAllArtists(): List<String> = fetchAllEntries("artist", "artists")
    suspend fun getAllCharacters(): List<String> = fetchAllEntries("character", "characters")
    suspend fun getAllParodies(): List<String> = fetchAllEntries("parody", "parodies")

    suspend fun search(
        query: String,
        page: Int? = null,
        language: ContentLanguage = ContentLanguage.All
    ): List<GallerySimpleInfo> {
        var url = "${BASE_URL}/search/?q=${Uri.encode(query)}"
        url += when (language) {
            ContentLanguage.All -> ""
            ContentLanguage.English -> "+english"
            ContentLanguage.Japanese -> "+japanese"
            ContentLanguage.Chinese -> "+chinese"
        }
        if (page != null && page > 1) {
            url += "&page=$page"
        }

        val responseBody = fetchData(url) ?: return emptyList()
        return NhentaiParser.parseGalleryList(responseBody)
    }

    private suspend fun fetchData(url: String, referer: String? = null): String? =
        withRetries {
            val request = Request.Builder().url(url).apply {
                if (referer != null) setupApiHeaders(this, referer) else setupHeaders(this)
            }.build()
            client.newCall(request).execute().use { response ->
                classifyResponse(response) { it.body.string() }
            }
        }

    suspend fun downloadImage(url: String): InputStream? =
        withRetries {
            val request = Request.Builder().url(url).apply { setupHeaders(this) }.build()
            val response = client.newCall(request).execute()
            val result = classifyResponse(response) { it.body.byteStream() }
            if (result !is AttemptResult.Done || result.value == null) response.close()
            result
        }

    private fun <T> classifyResponse(
        response: Response,
        onSuccess: (Response) -> T
    ): AttemptResult<T?> = when {
        response.code == 403 -> {
            _authRequired.tryEmit(Unit)
            AttemptResult.Done(null)
        }

        response.code == 429 || response.code in 500..599 -> AttemptResult.Retry
        !response.isSuccessful -> AttemptResult.Done(null)
        else -> AttemptResult.Done(onSuccess(response))
    }

    private fun buildUrlForPage(page: Int?, language: ContentLanguage): String {
        var url = when (language) {
            ContentLanguage.All -> BASE_URL
            ContentLanguage.English -> "${BASE_URL}/language/english"
            ContentLanguage.Japanese -> "${BASE_URL}/language/japanese"
            ContentLanguage.Chinese -> "${BASE_URL}/language/chinese"
        }
        if (page != null && page > 1) {
            url += "?page=$page"
        }
        return url
    }

    private suspend fun fetchAllEntries(singularType: String, pagePath: String): List<String> {
        val entries = mutableListOf<String>()
        var currentPage = 1
        var maxPages = 1
        val referer = "$BASE_URL/$pagePath?sort=popular"

        do {
            val url = "$BASE_URL/api/v2/tags/$singularType?sort=popular&page=$currentPage"
            val responseBody = fetchData(url, referer = referer) ?: break

            try {
                val innerJson = org.json.JSONObject(responseBody)

                if (currentPage == 1) {
                    maxPages = innerJson.optInt("num_pages", 1)
                }

                val results = innerJson.getJSONArray("result")
                for (i in 0 until results.length()) {
                    entries.add(results.getJSONObject(i).getString("name"))
                }
            } catch (_: Exception) {
                break
            }

            currentPage++
            if (currentPage > MAX_TAG_CRAWL_PAGES) break
        } while (currentPage <= maxPages)

        return entries.distinct()
    }

    private fun setupApiHeaders(builder: Request.Builder, referer: String) {
        builder.apply {
            header("User-Agent", USER_AGENT)
            header("Accept", "*/*")
            header(
                "Accept-Language",
                "ru,uk;q=0.9,en-US;q=0.8,en;q=0.7,el;q=0.6,pl;q=0.5,sk;q=0.4,zh-Hans;q=0.3,zh;q=0.2"
            )
            header("DNT", "1")
            header("Priority", "u=1, i")
            header("Referer", referer)
            header("Sec-Fetch-Dest", "empty")
            header("Sec-Fetch-Mode", "cors")
            header("Sec-Fetch-Site", "same-origin")
            clientHints()
        }
    }

    private fun setupHeaders(builder: Request.Builder) {
        builder.apply {
            header("User-Agent", USER_AGENT)
            header(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7"
            )
            header("Accept-Language", "en;q=0.9")
            header("Sec-GPC", "1")
            header("Connection", "keep-alive")
            header("Upgrade-Insecure-Requests", "1")
            header("Sec-Fetch-Dest", "document")
            header("Sec-Fetch-Mode", "navigate")
            header("Sec-Fetch-Site", "same-origin")
            header("Sec-Fetch-User", "?1")
            header("Priority", "u=0, i")
            header("TE", "trailers")
            clientHints()
        }
    }

    private fun Request.Builder.clientHints() {
        header(
            "Sec-CH-UA",
            "\"Not;A=Brand\";v=\"8\", \"Chromium\";v=\"150\", \"Google Chrome\";v=\"150\""
        )
        header("Sec-CH-UA-Arch", "\"\"")
        header("Sec-CH-UA-Bitness", "\"\"")
        header("Sec-CH-UA-Full-Version", "\"150.0.7871.232\"")
        header(
            "Sec-CH-UA-Full-Version-List",
            "\"Not;A=Brand\";v=\"8.0.0.0\", \"Chromium\";v=\"150.0.7871.232\", \"Google Chrome\";v=\"150.0.7871.232\""
        )
        header("Sec-CH-UA-Mobile", "?1")
        header("Sec-CH-UA-Model", "\"\"")
        header("Sec-CH-UA-Platform", "\"Android\"")
        header("Sec-CH-UA-Platform-Version", "\"17.0.0\"")
    }
}

private const val MAX_TAG_CRAWL_PAGES = 1000
