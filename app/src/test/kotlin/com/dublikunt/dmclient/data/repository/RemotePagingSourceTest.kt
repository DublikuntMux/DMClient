package com.dublikunt.dmclient.data.repository

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.dublikunt.dmclient.network.PageResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RemotePagingSourceTest {
    private fun params(page: Int? = null) = PagingSource.LoadParams.Refresh(page, 25, false)
    @Test
    fun `first page starts at one and links forward`() = runTest {
        var requested = 0
        val result = RemotePagingSource { page ->
            requested = page; PageResult(
            listOf("a", "b"),
            null
        )
        }.load(params()) as PagingSource.LoadResult.Page
        assertEquals(1, requested)
        assertEquals(listOf("a", "b"), result.data)
        assertNull(result.prevKey)
        assertEquals(2, result.nextKey)
    }

    @Test
    fun `later pages link both ways and stop at total pages`() = runTest {
        val result = RemotePagingSource {
            PageResult(
                listOf(it),
                3
            )
        }.load(params(3)) as PagingSource.LoadResult.Page
        assertEquals(2, result.prevKey)
        assertNull(result.nextKey)
    }

    @Test
    fun `empty page stops paging`() = runTest {
        val result = RemotePagingSource {
            PageResult(
                emptyList<Int>(),
                null
            )
        }.load(params(5)) as PagingSource.LoadResult.Page
        assertEquals(4, result.prevKey)
        assertNull(result.nextKey)
    }

    @Test
    fun `network failures surface and cancellation propagates`() = runTest {
        assertTrue(RemotePagingSource<Int> { throw java.io.IOException("offline") }.load(params()) is PagingSource.LoadResult.Error)
        try {
            RemotePagingSource<Int> { throw CancellationException() }.load(params()); fail("Cancellation swallowed")
        } catch (_: CancellationException) {
        }
    }

    @Test
    fun `refresh resumes the anchor page`() {
        val page: PagingSource.LoadResult.Page<Int, Int> =
            PagingSource.LoadResult.Page(List(25) { it }, null, 2)
        val state = PagingState(listOf(page), 10, PagingConfig(25), 0)
        assertEquals(
            1,
            RemotePagingSource { PageResult(emptyList<Int>(), null) }.getRefreshKey(state)
        )
    }
}
