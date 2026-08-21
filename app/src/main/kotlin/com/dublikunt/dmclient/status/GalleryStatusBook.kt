package com.dublikunt.dmclient.status

import com.dublikunt.dmclient.database.status.GalleryStatusDao
import com.dublikunt.dmclient.database.status.GalleryStatusWithCustomStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GalleryStatusView(
    val statusId: Int?,
    val name: String?,
    val color: Int?,
    val favorite: Boolean
)

private fun GalleryStatusWithCustomStatus.toView() = GalleryStatusView(
    statusId = galleryStatus.statusId,
    name = status?.name,
    color = status?.color,
    favorite = galleryStatus.favorite
)

class GalleryStatusBook @Inject constructor(
    private val statusDao: GalleryStatusDao,
    private val scope: CoroutineScope
) {
    private val _statuses = MutableStateFlow<Map<Int, GalleryStatusView>>(emptyMap())
    val statuses: StateFlow<Map<Int, GalleryStatusView>> = _statuses.asStateFlow()

    private val requestedIds = mutableSetOf<Int>()
    private val lock = Any()

    fun load(ids: Collection<Int>) {
        synchronized(lock) {
            val newIds = ids.filter { it !in requestedIds }.toSet()
            if (newIds.isEmpty()) return
            requestedIds += newIds
            scope.launch {
                val statuses = statusDao.getStatuses(newIds.toList())
                _statuses.value += statuses.associate { it.galleryStatus.id to it.toView() }
            }
        }
    }

    fun reset() {
        synchronized(lock) {
            requestedIds.clear()
            _statuses.value = emptyMap()
        }
    }
}
