package com.dublikunt.dmclient.paging


import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemotePagingSourceTest {

    @Test
    fun `first page starts at one and links forward`() = runTest {
        var requested: Int? = null

        val result = RemotePagingSource<String> { page ->
            requested = page
            listOf("a", "b")
        }.load(PagingSource.LoadParams.Refresh(key = null, loadSize = 25, placeholdersEnabled = false))

        val page = result as PagingSource.LoadResult.Page
        assertEquals(1, requested)
        assertEquals(listOf("a", "b"), page.data)
        assertNull(page.prevKey)
        assertEquals(2, page.nextKey)
    }

    @Test
    fun `later pages link in both directions`() = runTest {
        val result = RemotePagingSource<Int> { page -> List(25) { page } }
            .load(PagingSource.LoadParams.Refresh(key = 3, loadSize = 25, placeholdersEnabled = false))

        val page = result as PagingSource.LoadResult.Page
        assertEquals(2, page.prevKey)
        assertEquals(4, page.nextKey)
    }

    @Test
    fun `empty page ends the stream`() = runTest {
        val result = RemotePagingSource<Int> { emptyList() }
            .load(PagingSource.LoadParams.Refresh(key = 5, loadSize = 25, placeholdersEnabled = false))

        val page = result as PagingSource.LoadResult.Page
        assertEquals(emptyList<Int>(), page.data)
        assertEquals(4, page.prevKey)
        assertNull(page.nextKey)
    }

    @Test
    fun `loader failure surfaces as LoadResult error`() = runTest {
        val result = RemotePagingSource<Int> { throw java.io.IOException("offline") }
            .load(PagingSource.LoadParams.Refresh(key = null, loadSize = 25, placeholdersEnabled = false))

        assertTrue(result is PagingSource.LoadResult.Error)
    }

    @Test
    fun `refresh key returns to the anchor's page`() {
        val firstPage: PagingSource.LoadResult.Page<Int, Int> =
            PagingSource.LoadResult.Page(data = List(25) { it }, prevKey = null, nextKey = 2)

        val state = PagingState(
            pages = listOf(firstPage),
            anchorPosition = 10,
            config = PagingConfig(pageSize = 25),
            leadingPlaceholderCount = 0
        )

        val source = RemotePagingSource<Int> { emptyList() }

        assertEquals(1, source.getRefreshKey(state))
    }
}
