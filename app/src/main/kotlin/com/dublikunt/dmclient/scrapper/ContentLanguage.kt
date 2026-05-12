package com.dublikunt.dmclient.scrapper

enum class ContentLanguage(val value: String) {
    All("all"),
    English("english"),
    Japanese("japanese"),
    Chinese("chinese");

    companion object {
        fun fromString(value: String): ContentLanguage = entries
            .firstOrNull { it.value == value } ?: All
    }
}
