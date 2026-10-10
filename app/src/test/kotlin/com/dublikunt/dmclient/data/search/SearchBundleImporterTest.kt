package com.dublikunt.dmclient.data.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SearchBundleImporterTest {
    @Test fun `v1 bundle parses into distinct rows with singular type names`() {
        val bundle = SearchBundleImporter.parse("""{"version":1,"generatedAt":1720000000000,"tags":["a","b","a"],"artists":["artist1"],"characters":[],"parodies":["p1"]}""")
        assertEquals(listOf("a", "b", "a"), bundle.tags)
        val rows = SearchBundleImporter.entries(bundle)
        assertEquals(4, rows.size)
        assertEquals(listOf("tag", "tag", "artist", "parody"), rows.map { it.type })
        assertEquals(listOf("a", "b", "artist1", "p1"), rows.map { it.name })
    }

    @Test fun `unknown fields are ignored and unknown versions are rejected`() {
        assertEquals(1, SearchBundleImporter.parse("""{"version":1,"tags":[],"extra":{}}""").version)
        assertThrows(IllegalArgumentException::class.java) { SearchBundleImporter.parse("""{"version":99}""") }
    }
}
