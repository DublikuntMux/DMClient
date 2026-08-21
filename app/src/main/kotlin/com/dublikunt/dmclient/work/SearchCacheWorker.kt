package com.dublikunt.dmclient.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.dublikunt.dmclient.R
import com.dublikunt.dmclient.database.search.SearchCache
import com.dublikunt.dmclient.database.search.SearchCacheDao
import com.dublikunt.dmclient.scrapper.NHentaiApi
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SearchCacheWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val nHentaiApi: NHentaiApi,
    private val searchCacheDao: SearchCacheDao
) : CoroutineWorker(context, workerParams) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result {
        setForeground(createForegroundInfo("Fetching tags...", 0, 4))

        val steps = listOf(
            "tags" to nHentaiApi::getAllTags,
            "artists" to nHentaiApi::getAllArtists,
            "characters" to nHentaiApi::getAllCharacters,
            "parodies" to nHentaiApi::getAllParodies
        )

        for ((index, step) in steps.withIndex()) {
            val (type, fetch) = step
            if (isStopped) return Result.failure()

            val progress = index + 1
            setForeground(createForegroundInfo("Fetching $type...", progress, 4))
            searchCacheDao.insert(SearchCache(type = type, names = fetch()))
        }

        return Result.success()
    }

    private fun createForegroundInfo(
        text: String,
        progress: Int,
        max: Int
    ): ForegroundInfo {
        val channelId = "search_cache_channel"
        val channelName = "Search Cache"

        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        notificationManager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Preparing search data")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setProgress(max, progress, false)
            .setSilent(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                SEARCH_CACHE_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(SEARCH_CACHE_NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val SEARCH_CACHE_NOTIFICATION_ID = 1001
        const val UNIQUE_WORK_NAME = "search_cache_fetch"
    }
}
