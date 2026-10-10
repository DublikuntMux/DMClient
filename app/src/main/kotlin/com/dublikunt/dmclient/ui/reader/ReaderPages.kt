package com.dublikunt.dmclient.ui.reader

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import com.dublikunt.dmclient.data.settings.ReaderMode
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
internal fun ReaderPages(
    state: ReaderUiState,
    viewModel: ReaderViewModel,
    onToggleOverlay: () -> Unit,
    onSeekReady: (((Int) -> Unit)?) -> Unit
) {
    val detail = checkNotNull(state.detail)
    val context = LocalContext.current
    val latestToggle by rememberUpdatedState(onToggleOverlay)

    LaunchedEffect(detail.id, state.page, state.downloaded) {
        val loader = SingletonImageLoader.get(context)
        val requests = ((state.page - 2)..(state.page + 2)).filter { it in 1..detail.pageCount && it != state.page }.map { page ->
            loader.enqueue(
                ImageRequest.Builder(context).data(viewModel.pageImage(detail, page, state.downloaded))
                    .memoryCacheKeyExtra("retry", "0").build()
            )
        }
        try { kotlinx.coroutines.awaitCancellation() } finally { requests.forEach { it.dispose() } }
    }

    key(state.settings.readerMode) {
        val scope = rememberCoroutineScope()
        if (state.settings.readerMode == ReaderMode.Vertical) {
            val list = rememberLazyListState(initialFirstVisibleItemIndex = state.page - 1)
            LaunchedEffect(list) {
                snapshotFlow { list.firstVisibleItemIndex + 1 }.distinctUntilChanged().collect(viewModel::pageChanged)
            }
            DisposableEffect(list) {
                onSeekReady { page -> scope.launch { list.scrollToItem(page.coerceIn(1, detail.pageCount) - 1) } }
                onDispose { onSeekReady(null) }
            }
            LazyColumn(Modifier.fillMaxSize(), state = list) {
                items(detail.pageCount, key = { it + 1 }) { index ->
                    ReaderImage(
                        model = viewModel.pageImage(detail, index + 1, state.downloaded), page = index + 1, vertical = true,
                        modifier = Modifier.fillMaxWidth().pointerInput(Unit) { detectTapGestures(onTap = { latestToggle() }) }
                    )
                }
            }
        } else {
            val pager = rememberPagerState(initialPage = state.page - 1, pageCount = { detail.pageCount })
            var currentScale by remember { mutableFloatStateOf(1f) }
            val rtl = state.settings.readerMode == ReaderMode.PagedRtl
            LaunchedEffect(pager) {
                snapshotFlow { pager.settledPage + 1 }.distinctUntilChanged().collect(viewModel::pageChanged)
            }
            DisposableEffect(pager) {
                onSeekReady { page -> scope.launch { pager.scrollToPage(page.coerceIn(1, detail.pageCount) - 1) } }
                onDispose { onSeekReady(null) }
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                HorizontalPager(
                    state = pager, reverseLayout = rtl, beyondViewportPageCount = 1,
                    userScrollEnabled = currentScale <= 1f, modifier = Modifier.fillMaxSize()
                ) { index ->
                    ZoomableImage(
                        model = viewModel.pageImage(detail, index + 1, state.downloaded), page = index + 1,
                        active = index == pager.currentPage, onScaleChanged = { currentScale = it },
                        onTap = { fraction ->
                            when (readerTap(fraction, rtl)) {
                                ReaderTap.ToggleOverlay -> latestToggle()
                                ReaderTap.Previous -> scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) }
                                ReaderTap.Next -> scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(detail.pageCount - 1)) }
                            }
                        }
                    )
                }
            }
        }
    }
}
