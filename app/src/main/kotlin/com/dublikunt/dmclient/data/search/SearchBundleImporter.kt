package com.dublikunt.dmclient.data.search

import com.dublikunt.dmclient.data.db.entity.SearchEntryEntity
import com.dublikunt.dmclient.network.ApiException
import com.dublikunt.dmclient.network.TagType
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.InputStream

object SearchBundleImporter {
    private val json = Json { ignoreUnknownKeys = true }
    fun parse(text: String): SearchDataBundle = parsing { json.decodeFromString<SearchDataBundle>(text) }

    @OptIn(ExperimentalSerializationApi::class)
    fun parse(stream: InputStream): SearchDataBundle = parsing { json.decodeFromStream<SearchDataBundle>(stream) }

    private inline fun parsing(decode: () -> SearchDataBundle): SearchDataBundle = try {
        decode().also {
            require(it.version == SearchDataBundle.CURRENT_VERSION) { "Unsupported search bundle version" }
        }
    } catch (error: IllegalArgumentException) {
        throw ApiException.Parse(error)
    }

    fun entries(bundle: SearchDataBundle): List<SearchEntryEntity> = listOf(
        TagType.Tag to bundle.tags, TagType.Artist to bundle.artists,
        TagType.Character to bundle.characters, TagType.Parody to bundle.parodies,
    ).flatMap { (type, names) -> names.filter(String::isNotBlank).distinct().map { SearchEntryEntity(type.key, it) } }
}
