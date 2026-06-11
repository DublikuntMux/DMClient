package com.dublikunt.dmclient.database.search

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "search_cache")
data class SearchCache(
    @PrimaryKey val type: String,
    val names: List<String>,
    val lastUpdated: Long = System.currentTimeMillis()
)
