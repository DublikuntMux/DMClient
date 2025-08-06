package com.dublikunt.dmclient.scrapper

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.IOException
import org.jsoup.Jsoup

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

    private fun fetchData(url: String): String? {
        return try {
            val request = Request.Builder().url(url).apply { setupHeaders(this) }.build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP Error: ${response.code}")
                response.body?.string()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun fetchMainPage(page: Int? = null): List<GallerySimpleInfo> {
        val url = buildUrlForPage(page)
        val responseBody = fetchData(url) ?: return emptyList()

        return parseGallerySimpleInfo(responseBody)
    }

    fun fetchGallery(id: Int): GalleryFullInfo? {
        val url = "$BASE_URL/g/$id/"
        val responseBody = fetchData(url) ?: return null

        return parseGalleryFullInfo(responseBody, id)
    }

    private fun buildUrlForPage(page: Int?): String {
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
        val container =
            doc.selectFirst(".container.index-container:not(.index-popular)") ?: return emptyList()

        return container.select("div.gallery").mapNotNull { gallery ->
            val a = gallery.selectFirst("a.cover") ?: return@mapNotNull null
            val id = a.attr("href").removePrefix("/g/").removeSuffix("/").toIntOrNull()
                ?: return@mapNotNull null
            val imgUrl = a.selectFirst("img.lazyload")?.attr("data-src") ?: return@mapNotNull null
            val name = a.selectFirst("div.caption")?.text().orEmpty()
            GallerySimpleInfo(id, "https:$imgUrl", name)
        }
    }

    private fun parseGalleryFullInfo(responseBody: String, id: Int): GalleryFullInfo? {
        val doc = Jsoup.parse(responseBody)
        val info = doc.getElementById("info") ?: return null

        val cover =
            "https:" + doc.getElementById("cover")?.selectFirst("a img.lazyload")?.attr("data-src")
                .orEmpty()
        val name =
            info.selectFirst("h1.title")?.select("span")?.joinToString(" ") { it.text() }.orEmpty()
        val pages =
            doc.select("div.tag-container:contains(Pages) .tags a span.name").text().toIntOrNull()
                ?: return null
        val imageUrl = doc.getElementById("thumbnail-container")?.select("img[data-src]")
            ?.firstNotNullOfOrNull { it.attr("data-src").split("/") } ?: return null

        val galleryId = imageUrl.getOrNull(4)?.toIntOrNull() ?: return null
        val imageTypeString = imageUrl.getOrNull(5) ?: return null

        val imageType = when {
            imageTypeString.endsWith(".webp") -> ImageType.Webp
            imageTypeString.endsWith(".jpg") -> ImageType.Jpg
            else -> return null
        }

        val tags = mutableListOf<String>()
        doc.select("div.tag-container:contains(Tags) .tags a span.name").forEach {
            tags.add(it.text())
        }

        val artists = mutableListOf<String>()
        doc.select("div.tag-container:contains(Artists) .tags a span.name").forEach {
            artists.add(it.text())
        }

        val characters = mutableListOf<String>()
        doc.select("div.tag-container:contains(Characters) .tags a span.name").forEach {
            characters.add(it.text())
        }

        return GalleryFullInfo(
            id,
            cover,
            name,
            tags,
            artists,
            characters,
            pages,
            galleryId,
            imageType
        )
    }

    private fun fetchAllEntries(endpoint: String): List<String> {
        val entries = mutableListOf<String>()
        val countResponse = fetchData("$BASE_URL/$endpoint") ?: return emptyList()
        val countDoc = Jsoup.parse(countResponse)
        val maxPages = countDoc.select(".alphabetical-pagination li a")
            .find { it.text() == "Z" }
            ?.attr("href")
            ?.removePrefix("/$endpoint/?page=")
            ?.removeSuffix("#Z")
            ?.toIntOrNull() ?: 1

        for (i in 0..maxPages) {
            fetchData("$BASE_URL/$endpoint?page=$i")?.let {
                Jsoup.parse(it).select("span.name").forEach { span -> entries.add(span.text()) }
            }
        }
        return entries.distinct()
    }

    fun getAllTags(): List<String> = fetchAllEntries("tags")
    fun getAllArtists(): List<String> = fetchAllEntries("artists")
    fun getAllCharacters(): List<String> = fetchAllEntries("characters")

    fun search(query: String, page: Int? = null): List<GallerySimpleInfo> {
        var url = "${BASE_URL}/search/?q=${query}"
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
