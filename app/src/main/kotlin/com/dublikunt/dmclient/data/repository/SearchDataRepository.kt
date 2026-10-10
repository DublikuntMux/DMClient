package com.dublikunt.dmclient.data.repository

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.dublikunt.dmclient.data.db.AppDatabase
import com.dublikunt.dmclient.data.search.SearchBundleImporter
import com.dublikunt.dmclient.data.search.SearchDataBundle
import com.dublikunt.dmclient.data.search.SearchDataStore
import com.dublikunt.dmclient.data.settings.SettingsRepository
import com.dublikunt.dmclient.data.work.SearchDataWorker
import com.dublikunt.dmclient.network.Tag
import com.dublikunt.dmclient.network.TagType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchDataRepository @Inject internal constructor(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val storage: SearchDataStore,
    @ApplicationContext private val context: Context,
) {
    private val work = WorkManager.getInstance(context)
    private val seedLock = Mutex()
    /** Observes per-type row counts, the last refresh timestamp, and worker activity/failure. */
    val status: Flow<SearchDataStatus> = combine(db.searchEntries().counts(), settings.settings, work.getWorkInfosForUniqueWorkFlow(SearchDataWorker.UNIQUE_WORK_NAME)) { counts, prefs, infos ->
        SearchDataStatus(
            TagType.entries.associateWith { type -> counts.firstOrNull { it.type == type.key }?.count ?: 0 },
            prefs.searchDataUpdatedAt,
            infos.any { !it.state.isFinished },
            infos.firstOrNull()?.state == WorkInfo.State.FAILED,
        )
    }

    /** Imports the previously downloaded v1 bundle only when the table is empty. */
    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        seedLock.withLock {
            val file = File(context.filesDir, SearchDataBundle.DOWNLOADED_FILE_NAME)
            if (db.searchEntries().count() == 0 && file.isFile) file.inputStream().use { importBundle(it) }
        }
    }

    /** Enqueues unique search-data crawling work with a connected-network constraint. */
    fun refresh() {
        val request = OneTimeWorkRequestBuilder<SearchDataWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        work.enqueueUniqueWork(SearchDataWorker.UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    /** Replaces the four bundle types atomically and returns the distinct imported row count; caller closes stream. */
    suspend fun importBundle(stream: InputStream): Int = withContext(Dispatchers.IO) {
        val bundle = SearchBundleImporter.parse(stream)
        val entries = SearchBundleImporter.entries(bundle)
        storage.replace(entries, listOf(TagType.Tag, TagType.Artist, TagType.Character, TagType.Parody).map { it.key },
            bundle.generatedAt.takeIf { it > 0 } ?: System.currentTimeMillis())
        entries.size
    }

    /** Cancels refresh work, removes search rows and downloaded seed files, and resets the timestamp. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        work.cancelUniqueWork(SearchDataWorker.UNIQUE_WORK_NAME).result.get()
        storage.clear()
        listOf("search-data.json", "tags.json", "artists.json", "characters.json", "parodies.json").forEach { File(context.filesDir, it).delete() }
    }

    /** Returns SQL-limited suggestions across all types, prefix matches first; blank queries return no rows. */
    suspend fun suggest(query: String, limit: Int = 50): List<Tag> {
        require(limit > 0)
        if (query.isBlank()) return emptyList()
        return db.searchEntries().suggest(query.trim(), limit).map { row -> Tag(TagType.entries.first { it.key == row.type }, row.name) }
    }
}
