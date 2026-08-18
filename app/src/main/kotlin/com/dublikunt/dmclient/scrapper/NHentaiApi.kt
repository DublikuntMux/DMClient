package com.dublikunt.dmclient.scrapper

import android.net.Uri
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.IOException
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NHentaiApi @Inject constructor(
    private val client: OkHttpClient,
    private val cookieJar: EasyCookieJar
) {
    companion object {
        const val BASE_URL = "https://nhentai.net"
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Mobile Safari/537.36"

        private val COOKIE_HOSTS = listOf("nhentai.net", "t.nhentai.net", "i1.nhentai.net")
    }

    private val _authRequired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val authRequired: SharedFlow<Unit> = _authRequired

    fun setCookies(cookies: List<Pair<String, String>>) {
        val baseUrl = BASE_URL.toHttpUrl()
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

    fun clearCookies() = cookieJar.clear()

    private fun fetchData(url: String, retryCount: Int = 4, referer: String? = null): String? {
        var currentRetry = 0
        while (currentRetry < retryCount) {
            try {
                val request = Request.Builder().url(url)
                    .apply {
                        if (referer != null) setupApiHeaders(this, referer) else setupHeaders(this)
                    }
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.code == 403) {
                        _authRequired.tryEmit(Unit)
                        return null
                    }
                    if (response.code == 429) {
                        currentRetry++
                        val waitTime = 1000L * currentRetry
                        println("Rate limit exceeded. Waiting for $waitTime ms...")
                        Thread.sleep(waitTime)
                        return@use
                    }
                    if (!response.isSuccessful) throw IOException("HTTP Error: ${response.code}")
                    return response.body.string()
                }
            } catch (e: Exception) {
                if (currentRetry >= retryCount - 1) {
                    e.printStackTrace()
                    return null
                }
                currentRetry++
                val waitTime = 1000L * currentRetry
                println("Exception occurred. Waiting for $waitTime ms...")
                Thread.sleep(waitTime)
            }
        }
        return null
    }

    fun downloadImage(url: String, retryCount: Int = 4): InputStream? {
        var currentRetry = 0
        while (currentRetry < retryCount) {
            try {
                val request = Request.Builder().url(url).apply { setupHeaders(this) }.build()
                val response = client.newCall(request).execute()
                if (response.code == 403) {
                    response.close()
                    _authRequired.tryEmit(Unit)
                    return null
                }
                if (response.code == 429) {
                    response.close()
                    currentRetry++
                    val waitTime = 1000L * currentRetry
                    println("Rate limit exceeded. Waiting for $waitTime ms...")
                    Thread.sleep(waitTime)
                    continue
                }
                if (!response.isSuccessful) {
                    response.close()
                    return null
                }
                return response.body.byteStream()
            } catch (e: Exception) {
                if (currentRetry >= retryCount - 1) {
                    e.printStackTrace()
                    return null
                }
                currentRetry++
                val waitTime = 1000L * currentRetry
                println("Exception occurred. Waiting for $waitTime ms...")
                Thread.sleep(waitTime)
            }
        }
        return null
    }

    fun fetchMainPage(
        page: Int? = null,
        language: ContentLanguage = ContentLanguage.All
    ): List<GallerySimpleInfo> {
        val url = buildUrlForPage(page, language)
        val responseBody = fetchData(url) ?: return emptyList()
        return parseGallerySimpleInfo(responseBody)
    }

    fun fetchGallery(id: Int): GalleryFullInfo? {
        val url = "$BASE_URL/g/$id/"
        val responseBody = fetchData(url) ?: return null
        return parseGalleryFullInfo(responseBody, id)
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

    private fun parseGallerySimpleInfo(responseBody: String): List<GallerySimpleInfo> {
        val doc = Jsoup.parse(responseBody)

        val script = doc.select("script[data-sveltekit-fetched]")
            .firstOrNull {
                val url = it.attr("data-url")
                url.startsWith("/api/v2/galleries") || url.startsWith("/api/v2/search")
            } ?: return emptyList()

        return try {
            val data = script.data()
            val resultsArray: JSONArray

            val tryObject = try {
                JSONObject(data)
            } catch (_: Exception) {
                null
            }
            resultsArray = if (tryObject != null && tryObject.has("body")) {
                val bodyStr = tryObject.getString("body")
                val bodyObj = try {
                    JSONObject(bodyStr)
                } catch (_: Exception) {
                    null
                }
                if (bodyObj != null) bodyObj.getJSONArray("result") else JSONArray(bodyStr)
            } else {
                JSONArray(data)
            }

            val galleryList = mutableListOf<GallerySimpleInfo>()
            for (i in 0 until resultsArray.length()) {
                val item = resultsArray.getJSONObject(i)
                val id = item.getInt("id")
                val thumbPath = item.getString("thumbnail")
                val thumbUrl = "https://t.nhentai.net/$thumbPath"
                val name = item.optString("english_title").ifEmpty {
                    item.optString("japanese_title", "Unknown Title")
                }
                galleryList.add(GallerySimpleInfo(id, thumbUrl, name))
            }
            galleryList
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun parseGalleryFullInfo(responseBody: String, id: Int): GalleryFullInfo? {
        val doc = Jsoup.parse(responseBody)

        val script = doc.select("script[data-sveltekit-fetched]")
            .firstOrNull { it.attr("data-url").contains("/api/v2/galleries/$id") }
            ?: return null

        return try {
            val data = script.data()
            val innerJson: JSONObject

            val tryObject = try {
                JSONObject(data)
            } catch (_: Exception) {
                null
            }
            innerJson = if (tryObject != null && tryObject.has("body")) {
                JSONObject(tryObject.getString("body"))
            } else {
                JSONObject(data)
            }

            val mediaId = innerJson.getString("media_id")
            val titleObj = innerJson.getJSONObject("title")

            val name = titleObj.optString("english").ifEmpty {
                titleObj.optString("japanese", "Unknown Title")
            }

            val coverPath = innerJson.getJSONObject("cover").getString("path")
            val coverUrl = "https://t.nhentai.net/$coverPath"

            val pagesCount = innerJson.getInt("num_pages")

            val tagsArray = innerJson.getJSONArray("tags")
            val parodies = mutableListOf<String>()
            val tags = mutableListOf<String>()
            val artists = mutableListOf<String>()
            val characters = mutableListOf<String>()

            for (i in 0 until tagsArray.length()) {
                val tagItem = tagsArray.getJSONObject(i)
                val tagName = tagItem.getString("name")
                when (tagItem.getString("type")) {
                    "parody" -> parodies.add(tagName)
                    "tag" -> tags.add(tagName)
                    "artist" -> artists.add(tagName)
                    "character" -> characters.add(tagName)
                }
            }

            val pagesArray = innerJson.getJSONArray("pages")
            val imagesList = mutableListOf<ImageType>()

            for (i in 0 until pagesArray.length()) {
                val pagePath = pagesArray.getJSONObject(i).getString("path")
                val type = when {
                    pagePath.endsWith(".webp") -> ImageType.Webp
                    pagePath.endsWith(".png") -> ImageType.Png
                    else -> ImageType.Jpg
                }
                imagesList.add(type)
            }

            val numericMediaId = mediaId.toIntOrNull() ?: 0

            GalleryFullInfo(
                id,
                coverUrl,
                name,
                parodies,
                tags,
                artists,
                characters,
                pagesCount,
                numericMediaId,
                imagesList
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun fetchAllEntries(singularType: String, pagePath: String): List<String> {
        val entries = mutableListOf<String>()
        var currentPage = 1
        var maxPages = 1
        val referer = "$BASE_URL/$pagePath?sort=popular"

        do {
            val url = "$BASE_URL/api/v2/tags/$singularType?sort=popular&page=$currentPage"
            val responseBody = fetchData(url, referer = referer) ?: break

            try {
                val innerJson = JSONObject(responseBody)

                if (currentPage == 1) {
                    maxPages = innerJson.optInt("num_pages", 1)
                }

                val results = innerJson.getJSONArray("result")
                for (i in 0 until results.length()) {
                    val item = results.getJSONObject(i)
                    entries.add(item.getString("name"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                break
            }

            currentPage++
            if (currentPage > 1000) break
        } while (currentPage <= maxPages)

        return entries.distinct()
    }

    fun getAllTags(): List<String> = fetchAllEntries("tag", "tags")
    fun getAllArtists(): List<String> = fetchAllEntries("artist", "artists")
    fun getAllCharacters(): List<String> = fetchAllEntries("character", "characters")
    fun getAllParodies(): List<String> = fetchAllEntries("parody", "parodies")

    fun search(
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
        return parseGallerySimpleInfo(responseBody)
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
}
