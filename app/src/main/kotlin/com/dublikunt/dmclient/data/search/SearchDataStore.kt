package com.dublikunt.dmclient.data.search

import androidx.room.withTransaction
import com.dublikunt.dmclient.data.db.AppDatabase
import com.dublikunt.dmclient.data.db.entity.SearchEntryEntity
import com.dublikunt.dmclient.data.settings.SettingsRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class SearchDataStore @Inject constructor(
    private val db: AppDatabase,
    private val settings: SettingsRepository
) {
    private val lock = Mutex()

    suspend fun replace(entries: List<SearchEntryEntity>, types: List<String>, updatedAt: Long) =
        lock.withLock {
            currentCoroutineContext().ensureActive()
            db.withTransaction {
                types.forEach { db.searchEntries().clearType(it) }
                db.searchEntries().insert(entries)
            }
            settings.setSearchDataUpdatedAt(updatedAt)
        }

    suspend fun clear() = lock.withLock {
        db.searchEntries().clear()
        settings.setSearchDataUpdatedAt(null)
    }
}
