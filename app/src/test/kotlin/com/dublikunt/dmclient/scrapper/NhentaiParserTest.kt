package com.dublikunt.dmclient.scrapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NhentaiParserTest {

    private fun sveltekitPage(dataUrl: String, payload: String): String = """
        <html><head>
        <script data-sveltekit-fetched data-url="$dataUrl" type="application/json">$payload</script>
        </head><body></body></html>
    """.trimIndent()

    private fun galleryJson(
        id: Int = 1770001,
        thumb: String = "galleries/111/cover.jpg",
        english: String = "English Title",
        japanese: String = ""
    ): String =
        """{"id":$id,"thumbnail":"$thumb","english_title":"$english","japanese_title":"$japanese"}"""

    @Test
    fun `bare array body parses into simple galleries`() {
        val html = sveltekitPage(
            "/api/v2/galleries?page=1",
            "[${galleryJson(id = 1)},${galleryJson(id = 2)}]"
        )

        val result = NhentaiParser.parseGalleryList(html)

        assertEquals(2, result.size)
        assertEquals(1, result[0].id)
        assertEquals("https://t.nhentai.net/galleries/111/cover.jpg", result[0].thumb)
        assertEquals("English Title", result[0].name)
    }

    @Test
    fun `envelope with body object and result array parses`() {
        val inner = """{"result":[${galleryJson(id = 9)}],"num_pages":3}"""
        val html = sveltekitPage(
            "/api/v2/search?query=x",
            """{"status":200,"statusText":"","headers":{},"body":"${inner.replace("\"", "\\\"")}"}"""
        )

        val result = NhentaiParser.parseGalleryList(html)

        assertEquals(1, result.size)
        assertEquals(9, result[0].id)
    }

    @Test
    fun `title falls back to japanese then unknown`() {
        val html = sveltekitPage(
            "/api/v2/galleries",
            "[${galleryJson(id = 3, english = "", japanese = "日本語")},${galleryJson(id = 4, english = "", japanese = "")}]"
        )

        val result = NhentaiParser.parseGalleryList(html)

        assertEquals("日本語", result[0].name)
        assertEquals("Unknown Title", result[1].name)
    }

    @Test
    fun `page without sveltekit script yields empty list`() {
        val html = "<html><head></head><body>cloudflare challenge</body></html>"

        assertTrue(NhentaiParser.parseGalleryList(html).isEmpty())
    }

    @Test
    fun `script with unrelated data-url is ignored`() {
        val html = sveltekitPage("/api/v2/users/me", "[${galleryJson()}]")

        assertTrue(NhentaiParser.parseGalleryList(html).isEmpty())
    }

    private fun fullGalleryJson(mediaId: String = "555001"): String = """
        {
          "media_id": "$mediaId",
          "title": {"english": "Full Gallery", "japanese": "完全なギャラリー"},
          "cover": {"path": "galleries/999/cover.webp"},
          "num_pages": 3,
          "tags": [
            {"name": "Naruto", "type": "parody"},
            {"name": "Vanilla", "type": "tag"},
            {"name": "Author X", "type": "artist"},
            {"name": "Heroine", "type": "character"}
          ],
          "pages": [
            {"path": "galleries/999/1.jpg"},
            {"path": "galleries/999/2.webp"},
            {"path": "galleries/999/3.png"}
          ]
        }
    """.trimIndent()

    @Test
    fun `full gallery parses media id title tags buckets and page types`() {
        val html = sveltekitPage(
            "/api/v2/galleries/1770002?lang=english",
            "{\"body\":\"${fullGalleryJson().replace("\"", "\\\"").replace("\n", "")}\"}"
        )

        val gallery = NhentaiParser.parseGallery(html, id = 1770002)

        checkNotNull(gallery)
        assertEquals(1770002, gallery.id)
        assertEquals(555001, gallery.pagesId)
        assertEquals("Full Gallery", gallery.name)
        assertEquals("https://t.nhentai.net/galleries/999/cover.webp", gallery.thumb)
        assertEquals(listOf("Naruto"), gallery.parodies)
        assertEquals(listOf("Vanilla"), gallery.tags)
        assertEquals(listOf("Author X"), gallery.artists)
        assertEquals(listOf("Heroine"), gallery.characters)
        assertEquals(3, gallery.pages)
        assertEquals(
            listOf(ImageType.Jpg, ImageType.Webp, ImageType.Png),
            gallery.images
        )
    }

    @Test
    fun `japanese title used when english missing in full gallery`() {
        val json = fullGalleryJson()
            .replace("\"english\": \"Full Gallery\"", "\"english\": \"\"")
        val html = sveltekitPage(
            "/api/v2/galleries/1770002",
            json
        )

        val gallery = NhentaiParser.parseGallery(html, id = 1770002)

        assertEquals("完全なギャラリー", checkNotNull(gallery).name)
    }

    @Test
    fun `gallery page without matching script returns null`() {
        assertNull(NhentaiParser.parseGallery("<html></html>", id = 42))
    }
}
