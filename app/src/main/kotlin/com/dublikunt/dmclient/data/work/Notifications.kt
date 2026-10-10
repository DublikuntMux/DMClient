package com.dublikunt.dmclient.data.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.dublikunt.dmclient.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Notifications @Inject constructor(@ApplicationContext private val context: Context) {
    init {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(listOf(
            NotificationChannel("downloads", "Downloads", NotificationManager.IMPORTANCE_LOW),
            NotificationChannel("archives", "Archiving", NotificationManager.IMPORTANCE_LOW),
            NotificationChannel("search_data", "Search data", NotificationManager.IMPORTANCE_LOW),
        ).onEach { it.setShowBadge(false) })
    }

    fun foreground(id: Int, channel: String, title: String, progress: Int = 0, total: Int = 0): ForegroundInfo {
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setOngoing(true).setSilent(true)
            .apply { if (total > 0) setContentText("$progress/$total").setProgress(total, progress, false) }.build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(id, notification)
    }
}
