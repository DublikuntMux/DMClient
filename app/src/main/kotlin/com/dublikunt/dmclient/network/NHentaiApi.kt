package com.dublikunt.dmclient.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NHentaiApi @Inject constructor(private val client: OkHttpClient) {
    suspend fun latest(page: Int, language: ContentLanguage): PageResult<GallerySummary> =
        NhentaiParser.parseGalleryList(fetch(latestUrl(page, language)))

    suspend fun search(request: SearchRequest, page: Int): PageResult<GallerySummary> =
        NhentaiParser.parseGalleryList(fetch(searchUrl(request, page)))

    suspend fun gallery(id: Int): GalleryDetail {
        require(id > 0)
        return NhentaiParser.parseGallery(fetch("$BASE_URL/g/$id/".toHttpUrl()), id)
    }

    suspend fun openImage(url: String): ResponseBody = withContext(Dispatchers.IO) {
        withRetries {
            val response = client.newCall(request(url.toHttpUrl())).execute()
            if (!response.isSuccessful) {
                response.use { throwFailure(it, it.body.string()) }
            }
            response.body
        }
    }

    private suspend fun fetch(url: HttpUrl): String = withContext(Dispatchers.IO) {
        withRetries {
            client.newCall(request(url)).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) throwFailure(response, body)
                if (isChallenge(body) && !body.contains("data-sveltekit-fetched")) throw ApiException.Blocked()
                body
            }
        }
    }

    /** Browser-like headers; nhentai's anti-scraper rejects bare requests with 403. */
    private fun request(url: HttpUrl): Request =
        Request.Builder().url(url).apply {
            header("User-Agent", USER_AGENT)
            header(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8"
            )
            header("Accept-Language", "en;q=0.9")
            header("Upgrade-Insecure-Requests", "1")
            header("Priority", "u=0, i")
            header("Sec-Fetch-Dest", "document")
            header("Sec-Fetch-Mode", "navigate")
            header("Sec-Fetch-Site", "same-origin")
            header("Sec-Fetch-User", "?1")
            header("Sec-CH-UA", CLIENT_HINT_BRANDS)
            header("Sec-CH-UA-Mobile", "?1")
            header("Sec-CH-UA-Platform", "\"Android\"")
        }.build()

    private fun throwFailure(response: Response, body: String): Nothing = when (val code = response.code) {
        404 -> throw ApiException.NotFound()
        429 -> throw ApiException.RateLimited(response.header("Retry-After")?.toLongOrNull() ?: 60)
        403, 503 -> throw if (isChallenge(body)) ApiException.Blocked() else ApiException.Http(code)
        else -> throw ApiException.Http(code)
    }

    companion object {
        const val BASE_URL = "https://nhentai.net"
        private const val CLIENT_HINT_BRANDS =
            "\"Not;A=Brand\";v=\"8\", \"Chromium\";v=\"152\", \"Google Chrome\";v=\"152\""
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Mobile Safari/537.36"
    }
}
