package com.dublikunt.dmclient.data.repository

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.dublikunt.dmclient.network.PageResult
import kotlinx.coroutines.CancellationException

internal class RemotePagingSource<T : Any>(private val loadPage: suspend (Int) -> PageResult<T>) : PagingSource<Int, T>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> {
        val page = params.key ?: 1
        return try {
            val result = loadPage(page)
            LoadResult.Page(
                result.items, if (page == 1) null else page - 1,
                if (result.items.isEmpty() || result.totalPages?.let { page >= it } == true) null else page + 1,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            LoadResult.Error(error)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, T>): Int? = state.anchorPosition?.let { anchor ->
        state.closestPageToPosition(anchor)?.let { it.prevKey?.plus(1) ?: it.nextKey?.minus(1) }
    }
}
