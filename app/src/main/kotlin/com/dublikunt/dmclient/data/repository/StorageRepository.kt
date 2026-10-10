package com.dublikunt.dmclient.data.repository

import android.content.Context
import coil3.SingletonImageLoader
import com.dublikunt.dmclient.data.db.AppDatabase
import com.dublikunt.dmclient.data.download.GalleryContentLocator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageRepository @Inject constructor(private val db: AppDatabase, @ApplicationContext private val context: Context) {
    /** Calculates disk bytes and persisted download/history/search row counts. */
    suspend fun usage(): StorageUsage = withContext(Dispatchers.IO) {
        StorageUsage(File(context.cacheDir, "image_cache").bytes(), File(context.filesDir, GalleryContentLocator.ROOT_DIR).bytes(),
            db.downloads().count(), db.history().count(), db.searchEntries().count())
    }
    /** Clears the shared Coil image loader's memory and disk caches. */
    suspend fun clearImageCache() = withContext(Dispatchers.IO) {
        val loader = SingletonImageLoader.get(context)
        loader.memoryCache?.clear()
        loader.diskCache?.clear()
        Unit
    }
}

private fun File.bytes(): Long = if (!exists()) 0 else walkTopDown().filter(File::isFile).sumOf(File::length)
