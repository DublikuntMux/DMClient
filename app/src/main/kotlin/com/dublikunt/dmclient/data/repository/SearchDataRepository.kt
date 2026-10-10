package com.dublikunt.dmclient.data.repository

import android.content.Context
import android.util.Log
import androidx.work.WorkManager
import com.dublikunt.dmclient.data.db.AppDatabase
import com.dublikunt.dmclient.data.search.SearchBundleImporter
import com.dublikunt.dmclient.data.search.SearchDataBundle
import com.dublikunt.dmclient.data.search.SearchDataStore
import com.dublikunt.dmclient.data.search.shouldRefreshSearchData
import com.dublikunt.dmclient.data.settings.SettingsRepository
import com.dublikunt.dmclient.di.ApplicationScope
import com.dublikunt.dmclient.network.ApiException
import com.dublikunt.dmclient.network.Tag
import com.dublikunt.dmclient.network.TagType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchDataRepository @Inject internal constructor(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val storage: SearchDataStore,
    private val client: OkHttpClient,
    @ApplicationScope private val scope: CoroutineScope,
    @ApplicationContext context: Context,
) {
    private enum class RefreshState { Idle, Refreshing, Failed }
    private val refreshState = MutableStateFlow(RefreshState.Idle)
    private val refreshLock = Mutex()

    init {
        WorkManager.getInstance(context).cancelUniqueWork("search_data_refresh")
    }

    /** Observes per-type row counts, the bundle timestamp, and download activity/failure. */
    val status: Flow<SearchDataStatus> = combine(db.searchEntries().counts(), settings.settings, refreshState) { counts, prefs, state ->
        SearchDataStatus(
            TagType.entries.associateWith { type -> counts.firstOrNull { it.type == type.key }?.count ?: 0 },
            prefs.searchDataUpdatedAt,
            state == RefreshState.Refreshing,
            state == RefreshState.Failed,
        )
    }

    /** Downloads suggestions when missing or older than seven days. */
    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        if (shouldRefreshSearchData(db.searchEntries().count(), settings.read().searchDataUpdatedAt,
                System.currentTimeMillis())) refresh()
    }

    /** Downloads one bundle at a time for the application's lifetime. */
    fun refresh() {
        if (!refreshLock.tryLock()) return
        refreshState.value = RefreshState.Refreshing
        scope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(SearchDataBundle.REMOTE_URL).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw ApiException.Http(response.code)
                    val bundle = SearchBundleImporter.parse(response.body.byteStream())
                    currentCoroutineContext().ensureActive()
                    if (bundle.generatedAt != settings.read().searchDataUpdatedAt) replaceBundle(bundle)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w("SearchDataRepository", "Tag list download failed", error)
                refreshState.value = RefreshState.Failed
            }
        }.invokeOnCompletion {
            refreshState.compareAndSet(RefreshState.Refreshing, RefreshState.Idle)
            refreshLock.unlock()
        }
    }

    /** Replaces the four bundle types atomically and returns the distinct imported row count; caller closes stream. */
    suspend fun importBundle(stream: InputStream): Int = withContext(Dispatchers.IO) {
        refreshLock.withLock {
            val count = replaceBundle(SearchBundleImporter.parse(stream))
            refreshState.value = RefreshState.Idle
            count
        }
    }

    private suspend fun replaceBundle(bundle: SearchDataBundle): Int {
        val entries = SearchBundleImporter.entries(bundle)
        storage.replace(entries, listOf(TagType.Tag, TagType.Artist, TagType.Character, TagType.Parody).map { it.key },
            bundle.generatedAt)
        return entries.size
    }

    /** Waits for any active refresh, then removes search rows and resets the timestamp. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        refreshLock.withLock {
            storage.clear()
            refreshState.value = RefreshState.Idle
        }
    }

    /** Returns SQL-limited suggestions across all types, prefix matches first; blank queries return no rows. */
    suspend fun suggest(query: String, limit: Int = 50): List<Tag> {
        require(limit > 0)
        if (query.isBlank()) return emptyList()
        return db.searchEntries().suggest(query.trim(), limit).map { row -> Tag(TagType.entries.first { it.key == row.type }, row.name) }
    }
}
