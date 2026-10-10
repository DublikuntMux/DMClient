package com.dublikunt.dmclient.data.repository

import androidx.room.withTransaction
import com.dublikunt.dmclient.data.db.AppDatabase
import com.dublikunt.dmclient.data.db.dao.LibraryRow
import com.dublikunt.dmclient.data.db.entity.GalleryEntity
import com.dublikunt.dmclient.data.db.entity.LibraryEntity
import com.dublikunt.dmclient.data.db.entity.StatusEntity
import com.dublikunt.dmclient.di.ApplicationScope
import com.dublikunt.dmclient.network.GallerySummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepository @Inject constructor(private val db: AppDatabase, @ApplicationScope scope: CoroutineScope) {
    private val dao = db.library()

    /** Reactive marks joined with their current status, shared throughout the app. */
    val marks: StateFlow<Map<Int, GalleryMark>> = dao.observeAll().map { rows -> rows.associate { it.entry.galleryId to it.mark() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())
    /** Statuses in the user's chosen order. */
    val statuses: Flow<List<ReadingStatus>> = dao.statuses().map { list -> list.map { it.readingStatus() } }

    /** Observes tracked galleries matching a filter and case-insensitive title substring. */
    fun entries(filter: LibraryFilter, query: String): Flow<List<LibraryItem>> =
        dao.entries(filter == LibraryFilter.Favorites, (filter as? LibraryFilter.Status)?.id, query)
            .map { rows -> rows.map { LibraryItem(it.gallery.summary(), it.mark(), it.entry.updatedAt) } }

    /** Changes favorite while preserving status; removes untracked rows. */
    suspend fun setFavorite(gallery: GallerySummary, favorite: Boolean) = mutate(gallery) { it.copy(favorite = favorite) }
    /** Changes status while preserving favorite; removes untracked rows. */
    suspend fun setStatus(gallery: GallerySummary, statusId: Int?) = mutate(gallery) { it.copy(statusId = statusId) }

    /** Creates a status at the end of the ordered list and returns its id. */
    suspend fun createStatus(name: String, color: Int): Int = db.withTransaction {
        require(name.isNotBlank())
        dao.insertStatus(StatusEntity(name = name.trim(), color = color, position = (dao.allStatuses().maxOfOrNull { it.position } ?: 0) + 1)).toInt()
    }

    /** Changes a status name and ARGB color while preserving its position. */
    suspend fun updateStatus(status: ReadingStatus) = db.withTransaction {
        require(status.name.isNotBlank())
        val existing = dao.allStatuses().first { it.id == status.id }
        dao.updateStatus(existing.copy(name = status.name.trim(), color = status.color))
    }

    /** Removes a status, clears its references, and prunes galleries with no remaining mark. */
    suspend fun deleteStatus(id: Int) = db.withTransaction { dao.deleteStatus(id); dao.prune() }

    /** Reorders all statuses atomically; ids must contain every current status exactly once. */
    suspend fun reorderStatuses(ids: List<Int>) = db.withTransaction {
        require(ids.size == ids.distinct().size && ids.toSet() == dao.allStatuses().map { it.id }.toSet())
        ids.forEachIndexed { index, id -> dao.position(id, index) }
    }

    private suspend fun mutate(gallery: GallerySummary, change: (LibraryEntity) -> LibraryEntity) = db.withTransaction {
        val now = System.currentTimeMillis()
        val metadata = db.galleries().get(gallery.id)
        // Stored metadata comes from the gallery page; a summary may carry a local cover path.
        if (metadata == null || metadata.coverUrl.isBlank()) {
            db.galleries().upsert(GalleryEntity(gallery.id, gallery.title, gallery.coverUrl, metadata?.pageCount ?: 0, now))
        }
        val mark = change(dao.get(gallery.id) ?: LibraryEntity(gallery.id, null, false, now)).copy(updatedAt = now)
        if (mark.statusId == null && !mark.favorite) dao.delete(gallery.id) else dao.upsert(mark)
    }
}

internal fun StatusEntity.readingStatus(): ReadingStatus = ReadingStatus(id, name, color)
private fun LibraryRow.mark(): GalleryMark = GalleryMark(entry.favorite, status?.readingStatus())
