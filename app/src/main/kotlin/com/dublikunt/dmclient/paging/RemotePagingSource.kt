package com.dublikunt.dmclient.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState

class RemotePagingSource<T : Any>(
    private val loadPage: suspend (page: Int) -> List<T>
) : PagingSource<Int, T>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> {
        val page = params.key ?: FIRST_PAGE
        return try {
            val result = loadPage(page)
            LoadResult.Page(
                data = result,
                prevKey = if (page == FIRST_PAGE) null else page - 1,
                nextKey = if (result.isEmpty()) null else page + 1
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, T>): Int? =
        state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.let { page ->
                page.prevKey?.plus(1) ?: page.nextKey?.minus(1)
            }
        }
}

private const val FIRST_PAGE = 1
