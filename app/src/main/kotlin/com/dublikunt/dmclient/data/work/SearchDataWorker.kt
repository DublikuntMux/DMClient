package com.dublikunt.dmclient.data.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dublikunt.dmclient.data.db.entity.SearchEntryEntity
import com.dublikunt.dmclient.data.search.SearchDataStore
import com.dublikunt.dmclient.network.NHentaiApi
import com.dublikunt.dmclient.network.TagType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

@HiltWorker
class SearchDataWorker @AssistedInject internal constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val api: NHentaiApi,
    private val storage: SearchDataStore,
    private val notifications: Notifications,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val types = listOf(TagType.Tag, TagType.Artist, TagType.Character, TagType.Parody)
        val entries = mutableListOf<SearchEntryEntity>()
        types.forEachIndexed { index, type ->
            val title = "Downloading tag list (${index + 1}/${types.size}: ${type.key}s)"
            setForeground(notifications.foreground(NOTIFICATION_ID, "search_data", title))
            val names = api.tagNames(type) { page, totalPages ->
                setForeground(notifications.foreground(NOTIFICATION_ID, "search_data", title, page, totalPages))
            }
            currentCoroutineContext().ensureActive()
            entries.addAll(names.map { SearchEntryEntity(type.key, it) })
        }
        storage.replace(entries, types.map { it.key }, System.currentTimeMillis())
        Result.success()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Log.w(TAG, "Tag list download failed", error)
        Result.failure()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "search_data_refresh"
        private const val NOTIFICATION_ID = 1_000_001
        private const val TAG = "SearchDataWorker"
    }
}
