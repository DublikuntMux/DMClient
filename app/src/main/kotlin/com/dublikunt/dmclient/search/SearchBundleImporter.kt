package com.dublikunt.dmclient.search

import com.dublikunt.dmclient.database.search.SearchCache
import com.dublikunt.dmclient.database.search.SearchCacheDao
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream

object SearchBundleImporter {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun parse(text: String): SearchDataBundle = json.decodeFromString(text)

    fun parse(stream: InputStream): SearchDataBundle =
        stream.bufferedReader().use { parse(it.readText()) }

    fun parse(file: File): SearchDataBundle = parse(file.readText())

    fun toCaches(
        bundle: SearchDataBundle,
        lastUpdated: Long = System.currentTimeMillis()
    ): List<SearchCache> =
        listOf(
            SearchCache(type = "tags", names = bundle.tags.distinct(), lastUpdated = lastUpdated),
            SearchCache(
                type = "artists",
                names = bundle.artists.distinct(),
                lastUpdated = lastUpdated
            ),
            SearchCache(
                type = "characters",
                names = bundle.characters.distinct(),
                lastUpdated = lastUpdated
            ),
            SearchCache(
                type = "parodies",
                names = bundle.parodies.distinct(),
                lastUpdated = lastUpdated
            )
        )

    suspend fun seed(dao: SearchCacheDao, bundle: SearchDataBundle) {
        toCaches(bundle).forEach { dao.insert(it) }
    }

    suspend fun seedFromFile(dao: SearchCacheDao, file: File): SearchDataBundle {
        val bundle = parse(file)
        seed(dao, bundle)
        return bundle
    }

    suspend fun seedFromStream(dao: SearchCacheDao, stream: InputStream): SearchDataBundle {
        val bundle = parse(stream)
        seed(dao, bundle)
        return bundle
    }
}
