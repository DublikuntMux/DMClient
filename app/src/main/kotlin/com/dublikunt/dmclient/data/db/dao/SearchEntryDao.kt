package com.dublikunt.dmclient.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dublikunt.dmclient.data.db.entity.SearchEntryEntity
import kotlinx.coroutines.flow.Flow

data class SearchCount(val type: String, val count: Int)

@Dao
interface SearchEntryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(entries: List<SearchEntryEntity>)
    @Query("DELETE FROM search_entries WHERE type = :type") suspend fun clearType(type: String)
    @Query("DELETE FROM search_entries") suspend fun clear()
    @Query("SELECT COUNT(*) FROM search_entries") suspend fun count(): Int
    @Query("SELECT type, COUNT(*) AS count FROM search_entries GROUP BY type") fun counts(): Flow<List<SearchCount>>
    @Query("SELECT * FROM search_entries WHERE instr(lower(name), lower(:query)) > 0 ORDER BY CASE WHEN instr(lower(name), lower(:query)) = 1 THEN 0 ELSE 1 END, rowid LIMIT :limit")
    suspend fun suggest(query: String, limit: Int): List<SearchEntryEntity>
}
