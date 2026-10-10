package com.dublikunt.dmclient.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import org.json.JSONObject
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

    suspend fun tagNames(type: TagType): List<String> = withContext(Dispatchers.IO) {
        val names = mutableSetOf<String>()
        var page = 1
        var totalPages: Int
        do {
            val url = "$BASE_URL/api/v2/tags/${type.key}".toHttpUrl().newBuilder()
                .addQueryParameter("sort", "popular").addQueryParameter("page", page.toString()).build()
            val body = fetch(url, "$BASE_URL/${type.key}s?sort=popular")
            try {
                val data = JSONObject(body)
                totalPages = data.optInt("num_pages", 1)
                val result = data.getJSONArray("result")
                for (index in 0 until result.length()) names.add(result.getJSONObject(index).getString("name"))
            } catch (error: Exception) {
                throw ApiException.Parse(error)
            }
            page++
        } while (page <= totalPages)
        names.toList()
    }

    suspend fun openImage(url: String): ResponseBody = withContext(Dispatchers.IO) {
        withRetries {
            val response = client.newCall(request(url.toHttpUrl())).execute()
            if (!response.isSuccessful) {
                response.use { throwFailure(it.code, it.body.string()) }
            }
            response.body
        }
    }

    private suspend fun fetch(url: HttpUrl, referer: String = BASE_URL): String = withContext(Dispatchers.IO) {
        withRetries {
            client.newCall(request(url, referer)).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) throwFailure(response.code, body)
                if (isChallenge(body) && !body.contains("data-sveltekit-fetched")) throw ApiException.Blocked()
                body
            }
        }
    }

    private fun request(url: HttpUrl, referer: String = BASE_URL): Request = Request.Builder()
        .url(url).header("User-Agent", USER_AGENT).header("Referer", referer)
        .header("Accept-Language", "en;q=0.9").build()

    private fun throwFailure(code: Int, body: String): Nothing = when {
        code == 404 -> throw ApiException.NotFound()
        (code == 403 || code == 503) && isChallenge(body) -> throw ApiException.Blocked()
        else -> throw ApiException.Http(code)
    }

    companion object {
        const val BASE_URL = "https://nhentai.net"
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Mobile Safari/537.36"
    }
}
