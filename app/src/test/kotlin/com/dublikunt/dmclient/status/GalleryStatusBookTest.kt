package com.dublikunt.dmclient.status

import com.dublikunt.dmclient.database.status.CustomStatus
import com.dublikunt.dmclient.database.status.GalleryStatus
import com.dublikunt.dmclient.database.status.GalleryStatusDao
import com.dublikunt.dmclient.database.status.GalleryStatusWithCustomStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GalleryStatusBookTest {

    private class CountingStatusDao : GalleryStatusDao {
        val rows = mutableMapOf(
            1 to relation(
                id = 1,
                statusId = 7,
                favorite = true,
                custom = CustomStatus(7, "Reading", 0xFF0000.toInt())
            ),
            2 to relation(id = 2, statusId = null, favorite = false, custom = null)
        )
        var getStatusesCalls = 0

        override suspend fun getStatuses(ids: List<Int>): List<GalleryStatusWithCustomStatus> {
            getStatusesCalls++
            return ids.mapNotNull { rows[it] }
        }

        private fun relation(
            id: Int,
            statusId: Int?,
            favorite: Boolean,
            custom: CustomStatus?
        ) = GalleryStatusWithCustomStatus(
            galleryStatus = GalleryStatus(id, statusId, favorite),
            status = custom
        )

        override suspend fun insertStatus(status: GalleryStatus) = Unit
        override suspend fun insertStatuses(statuses: List<GalleryStatus>) = Unit
        override suspend fun getAllGalleryStatusEntities(): List<GalleryStatus> = TODO()
        override suspend fun getStatus(id: Int): GalleryStatusWithCustomStatus? =
            rows[id]

        override suspend fun insertCustomStatus(status: CustomStatus): Long = TODO()
        override suspend fun insertCustomStatuses(statuses: List<CustomStatus>) = Unit
        override suspend fun updateCustomStatus(status: CustomStatus) = Unit
        override suspend fun getCustomStatuses(): List<CustomStatus> = TODO()
        override suspend fun clearStatusFromGalleries(statusId: Int) = Unit
        override suspend fun deleteCustomStatus(statusId: Int) = Unit
    }

    private val dao = CountingStatusDao()

    private fun book() = GalleryStatusBook(
        dao,
        CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
    )

    @Test
    fun `load exposes a ui-shaped view per gallery`() = runTest {
        val b = book()

        b.load(listOf(1, 2))

        assertEquals("Reading", b.statuses.value[1]?.name)
        assertEquals(true, b.statuses.value[1]?.favorite)
        assertEquals(null, b.statuses.value[2]?.name)
        assertEquals(false, b.statuses.value[2]?.favorite)
    }

    @Test
    fun `repeated ids are fetched only once`() = runTest {
        val b = book()

        b.load(listOf(1))
        b.load(listOf(1, 1))
        b.load(listOf(1, 2))

        assertEquals(2, dao.getStatusesCalls)
    }

    @Test
    fun `reset forgets everything and allows refetch`() = runTest {
        val b = book()
        b.load(listOf(1))

        b.reset()
        assertEquals(emptyMap<Int, Nothing>(), b.statuses.value)

        b.load(listOf(1))
        assertEquals("Reading", b.statuses.value[1]?.name)
    }
}
