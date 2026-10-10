package com.dublikunt.dmclient.network

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NhentaiParserTest {
    private fun html(path: String, data: String): String =
        "<script data-sveltekit-fetched data-url=\"$path\">$data</script>"

    private fun summary(id: Int, english: String = "Title", japanese: String = ""): String =
        """{"id":$id,"thumbnail":"galleries/11/cover.jpg","english_title":"$english","japanese_title":"$japanese"}"""

    private val detail = """{
        "media_id":"555001", "title":{"english":"Full Gallery","japanese":"日本語","pretty":"Pretty"},
        "cover":{"path":"galleries/999/cover.webp"}, "num_pages":3,
        "upload_date":1720000000,"num_favorites":123,
        "tags":[{"type":"tag","name":"Vanilla","count":9},{"type":"artist","name":"Artist"},
        {"type":"character","name":"Hero"},{"type":"parody","name":"Story"},{"type":"group","name":"Team"},
        {"type":"language","name":"english"},{"type":"category","name":"manga"}],
        "pages":[{"path":"1.jpg"},{"path":"2.webp"},{"path":"3.png"}]
    }"""

    @Test
    fun `bare list parses galleries and no total page count`() {
        val result = NhentaiParser.parseGalleryList(
            html(
                "/api/v2/galleries?page=1",
                "[${summary(1)},${summary(2)}]"
            )
        )
        assertEquals(listOf(1, 2), result.items.map { it.id })
        assertEquals("https://t.nhentai.net/galleries/11/cover.jpg", result.items[0].coverUrl)
        assertEquals("Title", result.items[0].title)
        assertNull(result.totalPages)
    }

    @Test
    fun `enveloped result retains total pages and title fallbacks`() {
        val payload =
            """{"result":[${summary(3, "", "日本語")},${summary(4, "", "")}],"num_pages":3}"""
        val envelope = JSONObject().put("status", 200).put("body", payload).toString()
        val result = NhentaiParser.parseGalleryList(html("/api/v2/search?q=x", envelope))
        assertEquals(3, result.totalPages)
        assertEquals(listOf("日本語", "Unknown Title"), result.items.map { it.title })
    }

    @Test
    fun `valid empty result is a successful empty page`() {
        assertTrue(NhentaiParser.parseGalleryList(html("/api/v2/search", "[]")).items.isEmpty())
    }

    @Test
    fun `detail parses alternate title all tag types metadata and page types`() {
        val envelope = JSONObject().put("body", detail).toString()
        val gallery =
            NhentaiParser.parseGallery(html("/api/v2/galleries/42?lang=english", envelope), 42)
        assertEquals(42, gallery.id)
        assertEquals(555001, gallery.mediaId)
        assertEquals("Full Gallery", gallery.title)
        assertEquals("日本語", gallery.subtitle)
        assertEquals("https://t.nhentai.net/galleries/999/cover.webp", gallery.coverUrl)
        assertEquals(TagType.entries.toSet(), gallery.tags.map { it.type }.toSet())
        assertEquals(9, gallery.tags.first().count)
        assertEquals(3, gallery.pageCount)
        assertEquals(listOf(ImageType.Jpg, ImageType.Webp, ImageType.Png), gallery.pageTypes)
        assertEquals(1720000000L, gallery.uploadDate)
        assertEquals(123, gallery.favorites)
    }

    @Test
    fun `optional fields may be absent and identical alternate title is omitted`() {
        val data = JSONObject(detail).apply {
            remove("upload_date"); remove("num_favorites")
            getJSONObject("title").put("english", "日本語").put("pretty", "日本語")
        }
        val gallery = NhentaiParser.parseGallery(html("/api/v2/galleries/42", data.toString()), 42)
        assertEquals("日本語", gallery.title)
        assertNull(gallery.subtitle)
        assertNull(gallery.uploadDate)
        assertNull(gallery.favorites)
    }

    @Test
    fun `missing malformed unrelated and nonnumeric media data throw parse errors`() {
        assertThrows(ApiException.Parse::class.java) { NhentaiParser.parseGalleryList("<html></html>") }
        assertThrows(ApiException.Parse::class.java) {
            NhentaiParser.parseGalleryList(
                html(
                    "/api/v2/users/me",
                    "[]"
                )
            )
        }
        assertThrows(ApiException.Parse::class.java) {
            NhentaiParser.parseGalleryList(
                html(
                    "/api/v2/search",
                    "invalid"
                )
            )
        }
        assertThrows(ApiException.Parse::class.java) {
            NhentaiParser.parseGallery(
                "<html></html>",
                42
            )
        }
        assertThrows(ApiException.Parse::class.java) {
            NhentaiParser.parseGallery(
                html(
                    "/api/v2/galleries/42",
                    detail.replace("555001", "invalid")
                ), 42
            )
        }
    }

    @Test
    fun `challenge and HTTP envelope errors preserve exception types`() {
        assertThrows(ApiException.Blocked::class.java) { NhentaiParser.parseGalleryList("<html>Cloudflare challenge</html>") }
        assertThrows(ApiException.NotFound::class.java) {
            NhentaiParser.parseGallery(
                html(
                    "/api/v2/galleries/42",
                    """{"status":404,"body":"{}"}"""
                ), 42
            )
        }
        assertThrows(ApiException.Http::class.java) {
            NhentaiParser.parseGalleryList(
                html(
                    "/api/v2/search",
                    """{"status":500,"body":"{}"}"""
                )
            )
        }
    }
}
