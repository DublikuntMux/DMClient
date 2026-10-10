package com.dublikunt.dmclient.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.room.withTransaction
import com.dublikunt.dmclient.data.db.AppDatabase
import com.dublikunt.dmclient.data.db.entity.HistoryEntity
import com.dublikunt.dmclient.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepository @Inject constructor(
    private val db: AppDatabase,
    private val settings: SettingsRepository
) {
    /** Pages history newest first, filtered by case-insensitive title substring. */
    fun history(query: String): Flow<PagingData<HistoryItem>> =
        Pager(PagingConfig(30)) { db.history().paging(query) }.flow
            .map { data ->
                data.map {
                    HistoryItem(
                        it.gallery.summary(),
                        it.lastPage,
                        it.gallery.pageCount,
                        it.openedAt
                    )
                }
            }

    /** Observes the last saved one-based page, or null when the gallery has no history. */
    fun lastPage(id: Int): Flow<Int?> = db.history().lastPage(id)

    /** Saves progress for known galleries when history recording is enabled. */
    suspend fun saveProgress(id: Int, page: Int) {
        require(page > 0)
        if (!settings.read().recordHistory) return
        db.withTransaction {
            val gallery = db.galleries().get(id) ?: return@withTransaction
            db.history().upsert(
                HistoryEntity(
                    id,
                    if (gallery.pageCount > 0) page.coerceAtMost(gallery.pageCount) else page,
                    System.currentTimeMillis()
                )
            )
        }
    }

    /** Removes one gallery's history and saved page. */
    suspend fun remove(id: Int) = db.history().delete(id)

    /** Clears all history and saved reading progress. */
    suspend fun clear() = db.history().clear()
}
