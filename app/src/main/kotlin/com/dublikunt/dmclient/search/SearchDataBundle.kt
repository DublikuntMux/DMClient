package com.dublikunt.dmclient.search

import kotlinx.serialization.Serializable

@Serializable
data class SearchDataBundle(
    val version: Int = CURRENT_VERSION,
    val generatedAt: Long = 0L,
    val tags: List<String> = emptyList(),
    val artists: List<String> = emptyList(),
    val characters: List<String> = emptyList(),
    val parodies: List<String> = emptyList()
) {
    companion object {
        const val CURRENT_VERSION = 1
        const val BUNDLED_ASSET_NAME = "search-data.json"
        const val DOWNLOADED_FILE_NAME = "search-data.json"
    }
}
