package com.dublikunt.dmclient.network

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.Jsoup

object NhentaiParser {
    private const val THUMB_CDN = "https://t.nhentai.net"

    fun parseGalleryList(body: String): PageResult<GallerySummary> = parsing {
        val payload =
            payload(body) { it.startsWith("/api/v2/galleries") || it.startsWith("/api/v2/search") }
        val array =
            if (payload is JSONObject) payload.getJSONArray("result") else payload as JSONArray
        val items = (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            GallerySummary(
                item.getInt("id"),
                item.optString("english_title").ifBlank {
                    item.optString("japanese_title").ifBlank { "Unknown Title" }
                },
                imageUrl(item.getString("thumbnail")),
            )
        }
        PageResult(items, (payload as? JSONObject)?.nullableInt("num_pages"))
    }

    fun parseGallery(body: String, id: Int): GalleryDetail = parsing {
        val data = payload(body) {
            it.substringBefore('?').trimEnd('/') == "/api/v2/galleries/$id"
        } as JSONObject
        val titles = data.getJSONObject("title")
        val title = listOf("english", "japanese", "pretty").firstNotNullOfOrNull {
            titles.optString(it).takeIf(String::isNotBlank)
        } ?: "Unknown Title"
        val subtitle = listOf("japanese", "pretty").firstNotNullOfOrNull {
            titles.optString(it)
                .takeIf { alternative -> alternative.isNotBlank() && alternative != title }
        }
        val pages = data.getJSONArray("pages")
        val pageTypes = (0 until pages.length()).map { index ->
            when (pages.getJSONObject(index).getString("path").substringAfterLast('.')
                .lowercase()) {
                "jpg", "jpeg" -> ImageType.Jpg
                "webp" -> ImageType.Webp
                "png" -> ImageType.Png
                else -> error("Unknown image type")
            }
        }
        val tags = data.getJSONArray("tags")
        val pageCount = data.getInt("num_pages")
        require(pageCount > 0 && pageCount == pageTypes.size) { "Invalid page count" }
        GalleryDetail(
            id, title, subtitle, imageUrl(data.getJSONObject("cover").getString("path")),
            data.getString("media_id").toInt().also { require(it > 0) }, pageCount, pageTypes,
            (0 until tags.length()).map { index ->
                val tag = tags.getJSONObject(index)
                Tag(
                    TagType.entries.first { it.key == tag.getString("type") },
                    tag.getString("name"),
                    tag.optInt("count")
                )
            },
            if (data.has("upload_date") && !data.isNull("upload_date")) data.getLong("upload_date") else null,
            data.nullableInt("num_favorites"),
        )
    }

    private fun payload(body: String, matches: (String) -> Boolean): Any {
        val raw =
            if (body.trimStart().startsWith('{') || body.trimStart().startsWith('[')) body else {
                val script = Jsoup.parse(body).select("script[data-sveltekit-fetched]")
                    .firstOrNull { matches(it.attr("data-url")) }
                if (script == null) {
                    if (isChallenge(body)) throw ApiException.Blocked()
                    error("Missing gallery API data")
                }
                script.data()
            }
        val value = JSONTokener(raw).nextValue()
        if (value is JSONObject && value.has("body")) {
            when (val status = value.optInt("status", 200)) {
                404 -> throw ApiException.NotFound()
                in 200..299 -> Unit
                else -> {
                    if ((status == 403 || status == 503) && isChallenge(value.getString("body"))) throw ApiException.Blocked()
                    throw ApiException.Http(status)
                }
            }
            return JSONTokener(value.getString("body")).nextValue()
        }
        return value
    }

    private fun JSONObject.nullableInt(key: String): Int? =
        if (has(key) && !isNull(key)) getInt(key) else null

    private fun imageUrl(path: String): String =
        if (path.startsWith("https://")) path else "$THUMB_CDN/${path.trimStart('/')}"

    private inline fun <T> parsing(block: () -> T): T = try {
        block()
    } catch (error: ApiException) {
        throw error
    } catch (error: Exception) {
        throw ApiException.Parse(error)
    }
}
