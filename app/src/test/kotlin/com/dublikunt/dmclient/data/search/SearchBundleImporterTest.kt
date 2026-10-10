package com.dublikunt.dmclient.data.search

import com.dublikunt.dmclient.data.db.entity.SearchEntryEntity
import com.dublikunt.dmclient.network.ApiException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SearchBundleImporterTest {
    @Test
    fun `v1 bundle parses into distinct rows with singular type names`() {
        val bundle =
            SearchBundleImporter.parse("""{"version":1,"generatedAt":1720000000000,"tags":["a","b","a"],"artists":["artist1"],"characters":[],"parodies":["p1"]}""")
        assertEquals(listOf("a", "b", "a"), bundle.tags)
        val rows = SearchBundleImporter.entries(bundle)
        assertEquals(4, rows.size)
        assertEquals(listOf("tag", "tag", "artist", "parody"), rows.map { it.type })
        assertEquals(listOf("a", "b", "artist1", "p1"), rows.map { it.name })
    }

    @Test
    fun `unknown fields are ignored and unknown versions are rejected`() {
        assertEquals(
            1,
            SearchBundleImporter.parse("""{"version":1,"tags":[],"extra":{}}""").version
        )
        assertThrows(ApiException.Parse::class.java) { SearchBundleImporter.parse("""{"version":99}""") }
    }

    @Test
    fun `stream parses unicode names and bundle timestamp`() {
        val bundle = """{"version":1,"generatedAt":1720000000000,"tags":["日本語","café"]}"""
            .byteInputStream().use { SearchBundleImporter.parse(it) }
        assertEquals(1720000000000L, bundle.generatedAt)
        assertEquals(listOf("日本語", "café"), bundle.tags)
    }

    @Test
    fun `malformed streams and unsupported versions are parse errors`() {
        listOf("{", """{"tags":[42]}""", """{"version":99}""").forEach { text ->
            assertThrows(ApiException.Parse::class.java) {
                text.byteInputStream().use { SearchBundleImporter.parse(it) }
            }
        }
    }

    @Test
    fun `entries preserve popularity order within every type while removing blanks and duplicates`() {
        val bundle = SearchDataBundle(
            tags = listOf("z tag", "", "a tag", "z tag", "  ", "m tag"),
            artists = listOf("z artist", "a artist", "z artist"),
            characters = listOf("z character", "a character"),
            parodies = listOf("z parody", "a parody"),
        )
        assertEquals(
            listOf(
                SearchEntryEntity("tag", "z tag"),
                SearchEntryEntity("tag", "a tag"),
                SearchEntryEntity("tag", "m tag"),
                SearchEntryEntity("artist", "z artist"),
                SearchEntryEntity("artist", "a artist"),
                SearchEntryEntity("character", "z character"),
                SearchEntryEntity("character", "a character"),
                SearchEntryEntity("parody", "z parody"),
                SearchEntryEntity("parody", "a parody"),
            ), SearchBundleImporter.entries(bundle)
        )
    }
}
