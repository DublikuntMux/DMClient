package com.dublikunt.dmclient.data.search

import com.dublikunt.dmclient.data.db.entity.SearchEntryEntity
import com.dublikunt.dmclient.network.TagType
import kotlinx.serialization.json.Json
import java.io.InputStream

object SearchBundleImporter {
    private val json = Json { ignoreUnknownKeys = true }
    fun parse(text: String): SearchDataBundle = json.decodeFromString<SearchDataBundle>(text).also {
        require(it.version == SearchDataBundle.CURRENT_VERSION) { "Unsupported search bundle version" }
    }
    fun parse(stream: InputStream): SearchDataBundle = parse(stream.bufferedReader().readText())

    fun entries(bundle: SearchDataBundle): List<SearchEntryEntity> = listOf(
        TagType.Tag to bundle.tags, TagType.Artist to bundle.artists,
        TagType.Character to bundle.characters, TagType.Parody to bundle.parodies,
    ).flatMap { (type, names) -> names.filter(String::isNotBlank).distinct().map { SearchEntryEntity(type.key, it) } }
}
