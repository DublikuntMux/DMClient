package com.dublikunt.dmclient.data.search

import kotlinx.serialization.Serializable

@Serializable
data class SearchDataBundle(
    val version: Int = CURRENT_VERSION,
    val generatedAt: Long = 0,
    val tags: List<String> = emptyList(),
    val artists: List<String> = emptyList(),
    val characters: List<String> = emptyList(),
    val parodies: List<String> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION = 1
        const val REMOTE_URL = "https://raw.githubusercontent.com/DublikuntMux/DMClient/master/search-data/search-data.json"
    }
}
