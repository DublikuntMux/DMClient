package com.dublikunt.dmclient.data.repository

import android.content.Context
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.room.withTransaction
import com.dublikunt.dmclient.data.db.AppDatabase
import com.dublikunt.dmclient.data.db.entity.GalleryEntity
import com.dublikunt.dmclient.data.db.entity.HistoryEntity
import com.dublikunt.dmclient.data.download.GalleryContentLocator
import com.dublikunt.dmclient.data.settings.SettingsRepository
import com.dublikunt.dmclient.network.ContentLanguage
import com.dublikunt.dmclient.network.GalleryDetail
import com.dublikunt.dmclient.network.GallerySummary
import com.dublikunt.dmclient.network.NHentaiApi
import com.dublikunt.dmclient.network.SearchRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GalleryRepository @Inject constructor(
    private val api: NHentaiApi,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    @ApplicationContext private val context: Context,
) {
    /** Pages through the latest galleries for the selected language; failures surface through Paging. */
    fun latest(language: ContentLanguage): Flow<PagingData<GallerySummary>> =
        Pager(PagingConfig(25)) { RemotePagingSource { api.latest(it, language) } }.flow

    /** Pages a structured search; requests require text or an include/exclude filter. */
    fun search(request: SearchRequest): Flow<PagingData<GallerySummary>> =
        Pager(PagingConfig(25)) { RemotePagingSource { api.search(request, it) } }.flow

    /** Uses completed downloads first, refreshes gallery metadata, and records opens when enabled. */
    suspend fun detail(id: Int): GalleryDetail {
        val local = db.downloads().get(id)?.takeIf { it.state == DownloadState.Completed }
        val detail = local?.let { Json.decodeFromString<GalleryDetail>(it.detailJson) } ?: api.gallery(id)
        val recordHistory = settings.read().recordHistory
        db.withTransaction {
            val now = System.currentTimeMillis()
            db.galleries().upsert(detail.entity(now))
            if (recordHistory) db.history().upsert(HistoryEntity(id, db.history().get(id)?.lastPage ?: 1, now))
        }
        return detail
    }

    /** Returns a local page File or its remote image URL; page numbering starts at one. */
    fun pageImage(detail: GalleryDetail, page: Int, downloaded: Boolean): Any {
        require(page in 1..detail.pageCount)
        return if (downloaded) GalleryContentLocator.pageFile(context.filesDir, detail.id, page, detail.pageTypes)
        else GalleryContentLocator.remotePageUrl(detail.mediaId, page, detail.pageTypes)
    }

    /** Returns a local page File or a remote thumbnail URL. */
    fun pageThumbnail(detail: GalleryDetail, page: Int, downloaded: Boolean): Any {
        require(page in 1..detail.pageCount)
        return if (downloaded) pageImage(detail, page, true)
        else GalleryContentLocator.remoteThumbnailUrl(detail.mediaId, page, detail.pageTypes)
    }
}

internal fun GalleryDetail.entity(now: Long = System.currentTimeMillis()): GalleryEntity =
    GalleryEntity(id, title, coverUrl, pageCount, now)
internal fun GalleryEntity.summary(): GallerySummary = GallerySummary(id, title, coverUrl)
