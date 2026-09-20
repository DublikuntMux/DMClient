package com.dublikunt.dmclient.searchexport

import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class SearchExporter(
    private val client: OkHttpClient = OkHttpClient.Builder().build(),
    private val maxPages: Int = 1000,
    private val retries: Int = 4,
    private val pageDelayMs: Long = 500L,
    private val log: (String) -> Unit = ::println
) {
    companion object {
        const val BASE_URL = "https://nhentai.net"
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Mobile Safari/537.36"

        /** Must stay in sync with SearchCacheWorker types in `:app`. */
        val TYPES: Map<String, Pair<String, String>> = mapOf(
            "tags" to ("tag" to "tags"),
            "artists" to ("artist" to "artists"),
            "characters" to ("character" to "characters"),
            "parodies" to ("parody" to "parodies")
        )
    }

    suspend fun export(types: Set<String> = TYPES.keys): SearchDataBundle {
        val fetched = mutableMapOf<String, List<String>>()
        for (type in types) {
            val (singular, plural) = TYPES[type]
                ?: error("Unknown type '$type'. Expected one of ${TYPES.keys}")
            log("Fetching $type ...")
            fetched[type] = fetchAllEntries(singular, plural)
            log("Fetched ${fetched[type]?.size} $type")
        }
        return SearchDataBundle(
            generatedAt = System.currentTimeMillis(),
            tags = fetched["tags"] ?: emptyList(),
            artists = fetched["artists"] ?: emptyList(),
            characters = fetched["characters"] ?: emptyList(),
            parodies = fetched["parodies"] ?: emptyList()
        )
    }

    private suspend fun fetchAllEntries(singularType: String, pagePath: String): List<String> {
        val entries = mutableListOf<String>()
        var currentPage = 1
        var maxPageCount = 1
        val referer = "$BASE_URL/$pagePath?sort=popular"

        do {
            val url = "$BASE_URL/api/v2/tags/$singularType?sort=popular&page=$currentPage"
            val body = fetchData(url, referer)
            if (body == null) {
                log("Stopping $singularType crawl at page $currentPage: no body")
                break
            }
            try {
                val innerJson = JSONObject(body)
                if (currentPage == 1) {
                    maxPageCount = innerJson.optInt("num_pages", 1)
                }
                val results = innerJson.getJSONArray("result")
                for (i in 0 until results.length()) {
                    entries.add(results.getJSONObject(i).getString("name"))
                }
            } catch (e: Exception) {
                log("Stopping $singularType crawl at page $currentPage: ${e.message}")
                break
            }
            currentPage++
            if (currentPage > maxPages) break
            if (pageDelayMs > 0) delay(pageDelayMs)
        } while (currentPage <= maxPageCount)

        return entries.distinct()
    }

    private suspend fun fetchData(url: String, referer: String): String? {
        var attempt = 0
        while (attempt < retries) {
            try {
                val request = Request.Builder().url(url).apply {
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
                }.build()
                client.newCall(request).execute().use { response ->
                    when {
                        response.code == 429 || response.code in 500..599 -> {
                            // retry below
                        }

                        !response.isSuccessful -> return null
                        else -> return response.body.string()
                    }
                }
            } catch (e: Exception) {
                if (attempt >= retries - 1) {
                    e.printStackTrace()
                    return null
                }
            }
            attempt++
            val waitTime = 1000L * attempt
            log("Retrying $url in $waitTime ms... (attempt ${attempt + 1}/$retries)")
            delay(waitTime)
        }
        return null
    }
}
