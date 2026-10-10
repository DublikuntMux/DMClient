package com.dublikunt.dmclient.data.repository

import android.content.Context
import com.dublikunt.dmclient.network.GitHubReleaseApi
import com.dublikunt.dmclient.network.ReleaseInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateRepository @Inject constructor(
    private val api: GitHubReleaseApi,
    @ApplicationContext private val context: Context
) {
    private val downloadLock = Mutex()

    /** Returns the latest GitHub release, or null on network/parse failure; cancellation propagates. */
    suspend fun latestRelease(): ReleaseInfo? = try {
        api.latest()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    /** Compares numeric version segments, optional v prefix, and release/prerelease precedence. */
    fun isNewer(current: String, remote: String): Boolean {
        fun parts(value: String): List<Int>? =
            value.trim().removePrefix("v").substringBefore('-').substringBefore('+')
                .split('.').map { it.toIntOrNull() ?: return null }

        val local = parts(current) ?: return false
        val incoming = parts(remote) ?: return false
        for (index in 0 until maxOf(local.size, incoming.size)) {
            val comparison = incoming.getOrElse(index) { 0 }.compareTo(local.getOrElse(index) { 0 })
            if (comparison != 0) return comparison > 0
        }
        return current.contains('-') && !remote.contains('-')
    }

    /** Downloads the release's APK asset to cache and reports 0–1 progress; UI handles installation. */
    suspend fun downloadApk(release: ReleaseInfo, onProgress: (Float) -> Unit): File =
        downloadLock.withLock {
            val asset = release.assets.firstOrNull { it.name == "app-release.apk" }
                ?: release.assets.firstOrNull { it.name.endsWith(".apk") }
                ?: throw IllegalArgumentException("Release has no APK asset")
            api.download(asset.downloadUrl, File(context.cacheDir, "update.apk"), onProgress)
        }
}
