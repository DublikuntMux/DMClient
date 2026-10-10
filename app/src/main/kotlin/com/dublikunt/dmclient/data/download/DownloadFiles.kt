package com.dublikunt.dmclient.data.download

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DownloadFiles @Inject constructor(@ApplicationContext context: Context) {
    val root = context.filesDir
    private val locks = ConcurrentHashMap<Int, Mutex>()
    suspend fun <T> withGallery(id: Int, block: suspend () -> T): T =
        locks.getOrPut(id) { Mutex() }.withLock { block() }
}
