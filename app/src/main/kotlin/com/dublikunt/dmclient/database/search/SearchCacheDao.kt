package com.dublikunt.dmclient.database.search

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SearchCacheDao {
    @Query("SELECT * FROM search_cache WHERE type = :type")
    suspend fun getByType(type: String): SearchCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cache: SearchCache)

    @Query("DELETE FROM search_cache")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM search_cache")
    suspend fun count(): Int
}
