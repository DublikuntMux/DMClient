package com.dublikunt.dmclient

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.request.crossfade
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.dublikunt.dmclient.crash.CrashReporter
import com.dublikunt.dmclient.data.lock.AppLockManager
import com.dublikunt.dmclient.data.repository.DownloadRepository
import com.dublikunt.dmclient.data.settings.SettingsRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import javax.inject.Inject

@HiltAndroidApp
class DMClientApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var client: OkHttpClient
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var lock: AppLockManager
    @Inject lateinit var downloads: dagger.Lazy<DownloadRepository>

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(lock)
        downloads.get()
    }

    override fun newImageLoader(context: Context): ImageLoader {
        val cacheSize = runBlocking { settings.read().imageCacheSize }
        return ImageLoader.Builder(context)
            .crossfade(true)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
            .diskCache { DiskCache.Builder().directory(context.cacheDir.resolve("image_cache").toOkioPath()).maxSizeBytes(cacheSize).build() }
            .build()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
