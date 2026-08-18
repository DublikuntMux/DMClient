package com.dublikunt.dmclient.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.dublikunt.dmclient.repository.HomeRepository
import com.dublikunt.dmclient.scrapper.ContentLanguage
import com.dublikunt.dmclient.scrapper.GallerySimpleInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HomePagingSource(
    private val homeRepository: HomeRepository,
    private val language: ContentLanguage
) : PagingSource<Int, GallerySimpleInfo>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, GallerySimpleInfo> {
        val page = params.key ?: 1
        return try {
            val result =
                withContext(Dispatchers.IO) { homeRepository.fetchMainPage(page, language) }
            LoadResult.Page(
                data = result,
                prevKey = if (page == 1) null else page - 1,
                nextKey = if (result.isEmpty()) null else page + 1
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, GallerySimpleInfo>): Int? =
        state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.let { page ->
                page.prevKey?.plus(1) ?: page.nextKey?.minus(1)
            }
        }
}
