package com.dublikunt.dmclient.scrapper

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup

object NhentaiParser {
    private const val THUMB_CDN = "https://t.nhentai.net"

    fun parseGalleryList(responseBody: String): List<GallerySimpleInfo> {
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
                val thumbUrl = THUMB_CDN + "/$thumbPath"
                val name = item.optString("english_title").ifEmpty {
                    item.optString("japanese_title").ifEmpty { "Unknown Title" }
                }
                galleryList.add(GallerySimpleInfo(id, thumbUrl, name))
            }
            galleryList
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun parseGallery(responseBody: String, id: Int): GalleryFullInfo? {
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
                titleObj.optString("japanese").ifEmpty { "Unknown Title" }
            }

            val coverPath = innerJson.getJSONObject("cover").getString("path")
            val coverUrl = THUMB_CDN + "/$coverPath"

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
}
