package com.dublikunt.dmclient.scrapper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.IOException
import org.jsoup.Jsoup

enum class ContentLanguage {
    All,
    English,
    Japanese,
    Chinese,
}

object NHentaiApi {
    const val BASE_URL = "https://nhentai.net"
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36"

    private val cookieJar = EasyCookieJar()
    private val client = OkHttpClient.Builder().cookieJar(cookieJar).build()
    private var language: ContentLanguage = ContentLanguage.All

    fun setTokens(session: String, token: String) {
        val url = BASE_URL.toHttpUrl()
        cookieJar.setCookieSecure(url, "session-affinity", session)
        cookieJar.setCookie(url, "csrftoken", token)
    }

    fun setLanguage(langString: String) {
        language = when (langString) {
            "english" -> ContentLanguage.English
            "japanese" -> ContentLanguage.Japanese
            "chinese" -> ContentLanguage.Chinese
            else -> ContentLanguage.All
        }
    }

    suspend fun fetchMainPage(page: Int? = null): List<GallerySimpleInfo> =
        withContext(Dispatchers.IO) {
            var url = when (language) {
                ContentLanguage.All -> BASE_URL
                ContentLanguage.English -> "${BASE_URL}/language/english"
                ContentLanguage.Japanese -> "${BASE_URL}/language/japanese"
                ContentLanguage.Chinese -> "${BASE_URL}/language/chinese"
            }
            if (page != null) {
                if (page > 1) {
                    url += "?page=$page"
                }
            }
            try {
                val request = Request.Builder().url(url).apply { setupHeaders(this) }.build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP Error: ${response.code}")

                    val doc = Jsoup.parse(response.body?.string().orEmpty())
                    val container =
                        doc.selectFirst(".container.index-container:not(.index-popular)")
                            ?: return@withContext emptyList()

                    return@withContext container.select("div.gallery").mapNotNull { gallery ->
                        val a = gallery.selectFirst("a.cover") ?: return@mapNotNull null
                        val id = a.attr("href").removePrefix("/g/").removeSuffix("/").toIntOrNull()
                            ?: return@mapNotNull null
                        val imgUrl = a.selectFirst("img.lazyload")?.attr("data-src")
                            ?: return@mapNotNull null
                        val name = a.selectFirst("div.caption")?.text().orEmpty()

                        GallerySimpleInfo(id, imgUrl, name)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }

    suspend fun fetchGallery(id: Int): GalleryFullInfo? = withContext(Dispatchers.IO) {
        try {
            val request =
                Request.Builder().url("$BASE_URL/g/$id/").apply { setupHeaders(this) }.build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP Error: ${response.code}")

                val doc = Jsoup.parse(response.body?.string().orEmpty())
                val info = doc.getElementById("info") ?: return@withContext null

                val cover = doc.getElementById("cover")
                    ?.selectFirst("a img.lazyload")
                    ?.attr("data-src").orEmpty()

                val name = info.selectFirst("h1.title")
                    ?.select("span")
                    ?.joinToString(" ") { it.text() }
                    .orEmpty()

                val pages = doc.select("div.tag-container:contains(Pages) .tags a span.name")
                    .text().toIntOrNull() ?: return@withContext null

                val imageUrl = doc.getElementById("thumbnail-container")
                    ?.select("img[data-src]")?.firstNotNullOfOrNull {
                        it.attr("data-src").split("/")
                    } ?: return@withContext null

                val galleryId = imageUrl.getOrNull(4)?.toIntOrNull() ?: return@withContext null
                val imageTypeString = imageUrl.getOrNull(5) ?: return@withContext null

                val imageType = if (imageTypeString.endsWith(".webp")) {
                    ImageType.Webp
                } else if (imageTypeString.endsWith(".jpg")) {
                    ImageType.Jpg
                } else {
                    return@withContext null
                }

                GalleryFullInfo(id, cover, name, pages, galleryId, imageType)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getAllTags(): List<String> = withContext(Dispatchers.IO) {
        try {
            val tags = mutableListOf<String>()
            var maxPages = 1
            val countRequest =
                Request.Builder().url("$BASE_URL/tags").apply { setupHeaders(this) }.build()
            client.newCall(countRequest).execute().use { countResponse ->
                if (!countResponse.isSuccessful) throw IOException("HTTP Error: ${countResponse.code}")

                val countDoc = Jsoup.parse(countResponse.body?.string().orEmpty())
                countDoc.select(".alphabetical-pagination li a").forEach {
                    if (it.text() == "Z") {
                        val url = it.attr("href")
                        maxPages = url.removePrefix("/tags/?page=").removeSuffix("#Z").toInt()
                    }
                }
            }

            for (i in 0 until maxPages + 1) {
                val pageRequest =
                    Request.Builder().url("$BASE_URL/tags?page=${i}").apply { setupHeaders(this) }
                        .build()
                client.newCall(pageRequest).execute().use { pageResponse ->
                    val pageDoc = Jsoup.parse(pageResponse.body?.string().orEmpty())
                    pageDoc.select("span.name").forEach {
                        tags.add(it.text())
                    }
                }
            }
            tags.distinct()
        } catch (e: Exception) {
            e.printStackTrace()
            listOf()
        }
    }

    suspend fun search(query: String, page: Int? = null): List<GallerySimpleInfo> =
        withContext(Dispatchers.IO) {
            var url = "${BASE_URL}/search/?q=${query}"
            url += when (language) {
                ContentLanguage.All -> ""
                ContentLanguage.English -> "+english"
                ContentLanguage.Japanese -> "+japanese"
                ContentLanguage.Chinese -> "+chinese"
            }
            if (page != null) {
                if (page > 1) {
                    url += "&page=$page"
                }
            }

            try {
                val request = Request.Builder().url(url).apply { setupHeaders(this) }.build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP Error: ${response.code}")

                    val doc = Jsoup.parse(response.body?.string().orEmpty())
                    val container =
                        doc.selectFirst(".container.index-container")
                            ?: return@withContext emptyList()

                    return@withContext container.select("div.gallery").mapNotNull { gallery ->
                        val a = gallery.selectFirst("a.cover") ?: return@mapNotNull null
                        val id = a.attr("href").removePrefix("/g/").removeSuffix("/").toIntOrNull()
                            ?: return@mapNotNull null
                        val imgUrl = a.selectFirst("img.lazyload")?.attr("data-src")
                            ?: return@mapNotNull null
                        val name = a.selectFirst("div.caption")?.text().orEmpty()

                        GallerySimpleInfo(id, imgUrl, name)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }

    private fun setupHeaders(builder: Request.Builder) {
        builder.apply {
            header("User-Agent", USER_AGENT)
            header(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7"
            )
            header("Accept-Language", "en-US;q=0.8,en;q=0.7")
            header("Dnt", "1")
            header("Sec-Fetch-Dest", "document")
            header("Sec-Fetch-Mode", "navigate")
            header("Sec-Fetch-Site", "none")
            header("Sec-Fetch-User", "?1")
            header("Upgrade-Insecure-Requests", "1")
        }
    }
}
