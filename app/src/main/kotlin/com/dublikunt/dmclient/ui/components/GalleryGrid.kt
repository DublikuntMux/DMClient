package com.dublikunt.dmclient.ui.components

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.dublikunt.dmclient.ui.components.scrollbar.DraggableScrollbar
import com.dublikunt.dmclient.ui.components.scrollbar.rememberDraggableScroller
import com.dublikunt.dmclient.ui.components.scrollbar.scrollbarState

object GalleryGridDefaults {
    val ContentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    val HorizontalSpacing = Arrangement.spacedBy(10.dp)
    val VerticalSpacing = Arrangement.spacedBy(6.dp)
}

/**
 * Adaptive cover grid over paged data. Shows a skeleton while the first page loads, [ErrorState]
 * when it fails, [emptyContent] when nothing came back, and footer loading/error rows while appending.
 * Remote feeds can repeat ids across pages, so pass [key] only for sources with unique ids.
 */
@Composable
fun <T : Any> PagingGalleryGrid(
    items: LazyPagingItems<T>,
    minCellSize: Dp,
    errorMessage: (Throwable) -> String,
    emptyContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    key: ((T) -> Any)? = null,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = GalleryGridDefaults.ContentPadding,
    header: (LazyGridScope.() -> Unit)? = null,
    itemContent: @Composable (T) -> Unit
) {
    val refresh = items.loadState.refresh
    when {
        refresh is LoadState.Loading && items.itemCount == 0 ->
            GalleryGridSkeleton(minCellSize, modifier, contentPadding)

        refresh is LoadState.Error && items.itemCount == 0 ->
            ErrorState(message = errorMessage(refresh.error), onRetry = items::retry, modifier = modifier)

        refresh is LoadState.NotLoading && items.itemCount == 0 && header == null ->
            Box(modifier.fillMaxSize()) { emptyContent() }

        else -> GridWithScrollbar(
            itemCount = items.itemCount,
            minCellSize = minCellSize,
            state = state,
            contentPadding = contentPadding,
            modifier = modifier
        ) {
            header?.invoke(this)
            items(
                count = items.itemCount,
                key = key?.let { items.itemKey(it) }
            ) { index ->
                items[index]?.let { itemContent(it) } ?: GalleryCardSkeleton()
            }
            when (val append = items.loadState.append) {
                is LoadState.Loading -> fullWidthItem { AppendLoading() }
                is LoadState.Error -> fullWidthItem {
                    InlineError(errorMessage(append.error), onRetry = items::retry)
                }

                is LoadState.NotLoading -> Unit
            }
        }
    }
}

/** Adaptive cover grid over an in-memory list. */
@Composable
fun <T : Any> GalleryGrid(
    items: List<T>,
    minCellSize: Dp,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = GalleryGridDefaults.ContentPadding,
    header: (LazyGridScope.() -> Unit)? = null,
    itemContent: @Composable (T) -> Unit
) {
    GridWithScrollbar(
        itemCount = items.size,
        minCellSize = minCellSize,
        state = state,
        contentPadding = contentPadding,
        modifier = modifier
    ) {
        header?.invoke(this)
        items(count = items.size, key = { key(items[it]) }) { itemContent(items[it]) }
    }
}

fun LazyGridScope.fullWidthItem(key: Any? = null, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun GridWithScrollbar(
    itemCount: Int,
    minCellSize: Dp,
    state: LazyGridState,
    contentPadding: PaddingValues,
    modifier: Modifier,
    content: LazyGridScope.() -> Unit
) {
    Box(modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minCellSize),
            state = state,
            contentPadding = contentPadding,
            horizontalArrangement = GalleryGridDefaults.HorizontalSpacing,
            verticalArrangement = GalleryGridDefaults.VerticalSpacing,
            modifier = Modifier.fillMaxSize(),
            content = content
        )
        state.DraggableScrollbar(
            modifier = Modifier
                .fillMaxHeight()
                .padding(contentPadding)
                .padding(horizontal = 2.dp)
                .align(Alignment.CenterEnd),
            state = state.scrollbarState(itemsAvailable = itemCount),
            orientation = Orientation.Vertical,
            onThumbMoved = state.rememberDraggableScroller(itemsAvailable = itemCount)
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppendLoading() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        LoadingIndicator()
    }
}
