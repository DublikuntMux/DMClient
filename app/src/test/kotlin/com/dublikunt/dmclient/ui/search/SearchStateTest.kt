package com.dublikunt.dmclient.ui.search

import com.dublikunt.dmclient.network.ContentLanguage
import com.dublikunt.dmclient.network.SortOrder
import com.dublikunt.dmclient.network.Tag
import com.dublikunt.dmclient.network.TagType
import com.dublikunt.dmclient.ui.navigation.SearchRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchStateTest {
    private val tag = Tag(TagType.Tag, "example", 10)

    @Test fun `language sort and draft alone never produce a request`() {
        assertNull(SearchState(query = "draft", language = ContentLanguage.English,
            sort = SortOrder.PopularAllTime).request())
        assertNull(SearchState(text = "  ").request())
    }

    @Test fun `submission trims free text and preserves filters`() {
        val state = SearchState().addFilter(tag).submit("  a title  ")
        assertEquals("a title", state.text)
        assertEquals("a title", state.query)
        assertEquals(listOf(tag), state.request()!!.include)
    }

    @Test fun `adding an existing filter replaces its polarity and count in place`() {
        val second = Tag(TagType.Artist, "another")
        val state = SearchState().addFilter(tag).addFilter(second)
            .addFilter(tag.copy(count = 99), excluded = true)
        assertEquals(2, state.filters.size)
        assertEquals(SearchFilter(tag.copy(count = 99), true), state.filters.first())
        assertEquals(second, state.filters.last().tag)
    }

    @Test fun `same name in different types remains distinct`() {
        val artist = tag.copy(type = TagType.Artist)
        assertEquals(2, SearchState().addFilter(tag).addFilter(artist).filters.size)
    }

    @Test fun `adding a tag clears draft without losing the submitted title`() {
        val state = SearchState(text = "title", query = "draft").addFilter(tag)
        assertEquals("", state.query)
        assertEquals("title", state.request()!!.text)
    }

    @Test fun `toggle uses type and name rather than count and preserves other filters`() {
        val artist = tag.copy(type = TagType.Artist)
        val state = SearchState().addFilter(tag).addFilter(artist)
            .toggleFilter(tag.copy(count = 0))
        assertTrue(state.filters.first().excluded)
        assertFalse(state.filters.last().excluded)
        assertFalse(state.toggleFilter(tag).filters.first().excluded)
    }

    @Test fun `remove uses type and name and leaves other types intact`() {
        val artist = tag.copy(type = TagType.Artist)
        val state = SearchState().addFilter(tag).addFilter(artist)
            .removeFilter(tag.copy(count = 0))
        assertEquals(listOf(SearchFilter(artist)), state.filters)
    }

    @Test fun `exclusion only searches and removing last filter returns to idle`() {
        val state = SearchState().addFilter(tag, excluded = true)
        assertEquals(listOf(tag), state.request()!!.exclude)
        assertTrue(state.request()!!.include.isEmpty())
        assertNull(state.removeFilter(tag).request())
    }

    @Test fun `request includes current sort language and both filter polarities`() {
        val artist = Tag(TagType.Artist, "creator")
        val state = SearchState(sort = SortOrder.PopularMonth, language = ContentLanguage.Japanese)
            .addFilter(tag).addFilter(artist, excluded = true).submit("title")
        val request = state.request()!!
        assertEquals(SortOrder.PopularMonth, request.sort)
        assertEquals(ContentLanguage.Japanese, request.language)
        assertEquals(listOf(tag), request.include)
        assertEquals(listOf(artist), request.exclude)
    }

    @Test fun `removing free text preserves filters and clears input`() {
        val state = SearchState().submit("title").addFilter(tag).removeText()
        assertEquals("", state.text)
        assertEquals("", state.query)
        assertEquals(listOf(tag), state.request()!!.include)
        assertNull(state.removeFilter(tag).request())
    }

    @Test fun `route seeds text tag and default language immediately`() {
        val state = SearchState.fromRoute(
            SearchRoute(" title ", TagType.Parody, "series"), ContentLanguage.Chinese
        )
        assertEquals("title", state.request()!!.text)
        assertEquals("title", state.query)
        assertEquals(listOf(Tag(TagType.Parody, "series")), state.request()!!.include)
        assertEquals(ContentLanguage.Chinese, state.language)
    }

    @Test fun `partial or blank route tag stays idle`() {
        assertNull(SearchState.fromRoute(SearchRoute(tagType = TagType.Tag), ContentLanguage.All).request())
        assertNull(SearchState.fromRoute(SearchRoute(tagName = "name"), ContentLanguage.All).request())
        assertNull(SearchState.fromRoute(SearchRoute(tagType = TagType.Tag, tagName = " "), ContentLanguage.All).request())
    }
}
