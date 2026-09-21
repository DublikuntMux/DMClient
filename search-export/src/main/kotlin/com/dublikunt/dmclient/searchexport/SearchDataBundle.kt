package com.dublikunt.dmclient.searchexport

import kotlinx.serialization.Serializable

@Serializable
data class SearchDataBundle(
    val version: Int = CURRENT_VERSION,
    val generatedAt: Long = System.currentTimeMillis(),
    val tags: List<String> = emptyList(),
    val artists: List<String> = emptyList(),
    val characters: List<String> = emptyList(),
    val parodies: List<String> = emptyList()
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}
