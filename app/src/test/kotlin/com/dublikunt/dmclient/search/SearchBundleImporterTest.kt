package com.dublikunt.dmclient.search

import com.dublikunt.dmclient.database.search.SearchCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchBundleImporterTest {

    @Test
    fun `bundle json parses and maps to four caches`() {
        val text = """
            {"version":1,"generatedAt":1720000000000,
             "tags":["a","b","a"],"artists":["artist1"],
             "characters":[],"parodies":["p1"]}
        """.trimIndent()

        val bundle = SearchBundleImporter.parse(text)

        assertEquals(1, bundle.version)
        assertEquals(listOf("a", "b", "a"), bundle.tags)

        val caches: List<SearchCache> = SearchBundleImporter.toCaches(bundle)
        assertEquals(4, caches.size)
        assertEquals("tags", caches[0].type)
        // distinct applied
        assertEquals(listOf("a", "b"), caches[0].names)
        assertEquals(listOf("artist1"), caches[1].names)
        assertTrue(caches[2].names.isEmpty())
        assertEquals(listOf("p1"), caches[3].names)
    }

    @Test
    fun `unknown fields are ignored for forward compatibility`() {
        val bundle = SearchBundleImporter.parse(
            """{"version":99,"tags":[],"artists":[],"characters":[],"parodies":[],"extra":{}}"""
        )
        assertEquals(99, bundle.version)
    }
}
