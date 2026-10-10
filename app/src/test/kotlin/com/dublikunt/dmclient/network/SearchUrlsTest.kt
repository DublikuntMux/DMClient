package com.dublikunt.dmclient.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SearchUrlsTest {
    @Test fun `search includes typed filters language sort and encoded text`() {
        val request = SearchRequest("a+b & 日本語", listOf(Tag(TagType.Artist, "a name")), listOf(Tag(TagType.Tag, "x\"y")), ContentLanguage.English, SortOrder.PopularWeek)
        val url = searchUrl(request, 3)
        assertEquals("a+b & 日本語 artist:\"a name\" -tag:\"x\\\"y\" language:\"english\"", url.queryParameter("q"))
        assertEquals("popular-week", url.queryParameter("sort"))
        assertEquals("3", url.queryParameter("page"))
        assertFalse(url.toString().contains("日本語"))
        assertFalse(url.toString().contains("a+b"))
    }

    @Test fun `recent first page omits sort and page parameters`() {
        val url = searchUrl(SearchRequest(include = listOf(Tag(TagType.Group, "team"))), 1)
        assertEquals("group:\"team\"", url.queryParameter("q"))
        assertNull(url.queryParameter("sort"))
        assertNull(url.queryParameter("page"))
    }

    @Test fun `exclusion alone is a valid query`() {
        assertEquals("-parody:\"test\"", searchUrl(SearchRequest(exclude = listOf(Tag(TagType.Parody, "test"))), 1).queryParameter("q"))
    }

    @Test fun `empty query and invalid pages are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { searchUrl(SearchRequest(language = ContentLanguage.Chinese, sort = SortOrder.PopularAllTime), 1) }
        assertThrows(IllegalArgumentException::class.java) { searchUrl(SearchRequest("text"), 0) }
    }

    @Test fun `latest uses language path and page parameter`() {
        assertEquals("/language/japanese", latestUrl(2, ContentLanguage.Japanese).encodedPath)
        assertEquals("2", latestUrl(2, ContentLanguage.Japanese).queryParameter("page"))
        assertEquals("https://nhentai.net/", latestUrl(1, ContentLanguage.All).toString())
    }
}
