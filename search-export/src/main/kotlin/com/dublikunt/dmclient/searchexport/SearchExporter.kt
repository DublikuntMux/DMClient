package com.dublikunt.dmclient.searchexport

import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

class SearchExporter(
    private val client: OkHttpClient = OkHttpClient.Builder().build(),
    private val maxPages: Int = 1000,
    private val retries: Int = 4,
    private val pageDelayMs: Long = 0L,
    private val log: (String) -> Unit = ::println
) {
    companion object {
        const val BASE_URL = "https://nhentai.net"
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Mobile Safari/537.36"
        private const val MAX_RATE_LIMIT_WAITS = 20

        /** Search data types included in the bundle downloaded by the app. */
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
        var reportedTotal = 0
        val referer = "$BASE_URL/$pagePath?sort=popular"

        do {
            val url = "$BASE_URL/api/v2/tags/$singularType?sort=popular&page=$currentPage"
            val body = fetchData(url, referer)
            val innerJson = try {
                JSONObject(body)
            } catch (e: Exception) {
                throw IOException("Failed to parse $singularType page $currentPage", e)
            }
            if (currentPage == 1) {
                maxPageCount = innerJson.getInt("num_pages")
                reportedTotal = innerJson.getInt("total")
                require(maxPageCount >= 1) { "Invalid num_pages for $singularType: $maxPageCount" }
                require(reportedTotal >= 0) { "Invalid total for $singularType: $reportedTotal" }
            }
            val results = try {
                innerJson.getJSONArray("result")
            } catch (e: Exception) {
                throw IOException("Failed to parse results for $singularType page $currentPage", e)
            }
            for (i in 0 until results.length()) {
                try {
                    entries.add(results.getJSONObject(i).getString("name"))
                } catch (e: Exception) {
                    throw IOException("Failed to parse item $i on $singularType page $currentPage", e)
                }
            }
            currentPage++
            if (currentPage > maxPages) break
            if (pageDelayMs > 0) delay(pageDelayMs)
        } while (currentPage <= maxPageCount)

        val uniqueEntries = entries.distinct()
        SearchExportPolicy.requireComplete(singularType, uniqueEntries.size, reportedTotal)
        return uniqueEntries
    }

    private suspend fun fetchData(url: String, referer: String): String {
        var attempt = 0
        var rateLimitWaits = 0
        while (attempt < retries) {
            try {
                val request = Request.Builder().url(url).apply {
                    header("User-Agent", USER_AGENT)
                    header("Accept", "*/*")
                    header("Accept-Language", "en-US,en;q=0.9")
                    header("DNT", "1")
                    header("Priority", "u=1, i")
                    header("Referer", referer)
                    header("Sec-Fetch-Dest", "empty")
                    header("Sec-Fetch-Mode", "cors")
                    header("Sec-Fetch-Site", "same-origin")
                }.build()
                client.newCall(request).execute().use { response ->
                    when {
                        response.code == 429 -> throw RateLimitException(
                            SearchExportPolicy.retryAfterSeconds(response.header("Retry-After"))
                        )
                        response.code in 500..599 -> throw RetryableRequestException("HTTP ${response.code} for $url")
                        !response.isSuccessful -> throw NonRetryableRequestException("HTTP ${response.code} for $url")
                        else -> return response.body.string()
                    }
                }
            } catch (e: RateLimitException) {
                rateLimitWaits++
                if (rateLimitWaits > MAX_RATE_LIMIT_WAITS) {
                    throw IOException("Too many consecutive HTTP 429 responses for $url", e)
                }
                log("Rate limited fetching $url; retrying in ${e.waitSeconds}s (wait $rateLimitWaits/$MAX_RATE_LIMIT_WAITS)")
                delay(e.waitSeconds * 1000)
                continue
            } catch (e: NonRetryableRequestException) {
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: RetryableRequestException) {
                attempt++
                if (attempt >= retries) throw e
                val waitTime = 1000L * attempt
                log("Retrying $url in ${waitTime}ms... (attempt ${attempt + 1}/$retries)")
                delay(waitTime)
            } catch (e: Exception) {
                attempt++
                if (attempt >= retries) throw IOException("Request failed for $url after $retries attempts", e)
                val waitTime = 1000L * attempt
                log("Retrying $url in ${waitTime}ms... (attempt ${attempt + 1}/$retries)")
                delay(waitTime)
            }
        }
        throw IOException("Request failed for $url after $retries attempts")
    }

    private class RetryableRequestException(message: String) : IOException(message)
    private class NonRetryableRequestException(message: String) : IOException(message)
    private class RateLimitException(val waitSeconds: Long) : IOException()
}
