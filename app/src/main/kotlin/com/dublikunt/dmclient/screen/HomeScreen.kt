package com.dublikunt.dmclient.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.paging.LoadState
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import com.dublikunt.dmclient.component.GalleryCard
import com.dublikunt.dmclient.component.LoadingScreen
import com.dublikunt.dmclient.database.status.GalleryStatusDao
import com.dublikunt.dmclient.database.status.GalleryStatusWithCustomStatus
import com.dublikunt.dmclient.paging.HomePagingSource
import com.dublikunt.dmclient.repository.HistoryRepository
import com.dublikunt.dmclient.repository.HomeRepository
import com.dublikunt.dmclient.repository.PreferenceRepository
import com.dublikunt.dmclient.scrapper.ContentLanguage
import com.dublikunt.dmclient.scrapper.GallerySimpleInfo
import com.dublikunt.dmclient.scrapper.NHentaiApi
import com.dublikunt.dmclient.scrapper.NHentaiWebView
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FetchStatus {
    Check, Fetched, NotFetched
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val homeRepository: HomeRepository,
    private val historyRepository: HistoryRepository,
    private val preferenceRepository: PreferenceRepository,
    private val nHentaiApi: NHentaiApi,
    private val galleryStatusDao: GalleryStatusDao,
) : ViewModel() {
    private val _language = MutableStateFlow(ContentLanguage.All)

    @OptIn(ExperimentalCoroutinesApi::class)
    val flow = _language.flatMapLatest { lang ->
        Pager(PagingConfig(pageSize = 25)) {
            HomePagingSource(homeRepository, lang)
        }.flow
    }.cachedIn(viewModelScope)

    private val _tokenFetched = MutableStateFlow(FetchStatus.Check)
    val tokenFetched: StateFlow<FetchStatus> = _tokenFetched.asStateFlow()

    private val _statusMap = MutableStateFlow<Map<Int, GalleryStatusWithCustomStatus?>>(emptyMap())
    val statusMap: StateFlow<Map<Int, GalleryStatusWithCustomStatus?>> = _statusMap.asStateFlow()

    private val loadedStatusIds = mutableSetOf<Int>()

    init {
        viewModelScope.launch {
            val lang = preferenceRepository.preferredLanguage.first()
            _language.value = ContentLanguage.fromString(lang ?: "all")
            val session = preferenceRepository.sessionAffinity.first()
            val token = preferenceRepository.csrfToken.first()
            if (!session.isNullOrEmpty() && !token.isNullOrEmpty()) {
                nHentaiApi.setTokens(session, token)
                _tokenFetched.value = FetchStatus.Fetched
            } else {
                _tokenFetched.value = FetchStatus.NotFetched
            }
        }
    }

    fun loadStatuses(ids: List<Int>) {
        val newIds = ids.filter { it !in loadedStatusIds }
        if (newIds.isEmpty()) return
        loadedStatusIds.addAll(newIds)
        viewModelScope.launch(Dispatchers.IO) {
            val statuses = galleryStatusDao.getStatuses(newIds)
            _statusMap.value += statuses.associateBy { it.galleryStatus.id }
        }
    }

    fun addGalleryToHistory(gallery: GallerySimpleInfo) {
        viewModelScope.launch {
            historyRepository.insertHistory(
                com.dublikunt.dmclient.database.history.GalleryHistory(
                    gallery.id,
                    gallery.thumb,
                    gallery.name
                )
            )
        }
    }

    fun saveTokensAndFetch(session: String, token: String) {
        viewModelScope.launch {
            preferenceRepository.saveTokens(session, token)
            nHentaiApi.setTokens(session, token)
            _tokenFetched.value = FetchStatus.Fetched
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(
    navController: NavHostController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val tokenFetched by viewModel.tokenFetched.collectAsState()

    val internetPermissionState = rememberPermissionState(android.Manifest.permission.INTERNET)
    val isPermissionGranted by rememberUpdatedState(internetPermissionState.status.isGranted)

    val scrollState = rememberLazyGridState()

    if (isPermissionGranted) {
        when (tokenFetched) {
            FetchStatus.NotFetched -> {
                NHentaiWebView { session, token -> viewModel.saveTokensAndFetch(session, token) }
            }

            FetchStatus.Fetched -> {
                val items = viewModel.flow.collectAsLazyPagingItems()

                val itemCount = items.itemCount
                LaunchedEffect(itemCount) {
                    val ids = (0 until itemCount).mapNotNull { items.peek(it)?.id }
                    if (ids.isNotEmpty()) viewModel.loadStatuses(ids)
                }

                val statusMap by viewModel.statusMap.collectAsState()

                if (items.itemCount == 0 && items.loadState.refresh is LoadState.Loading) {
                    LoadingScreen()
                } else {
                    LazyVerticalGrid(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        columns = GridCells.Adaptive(minSize = 128.dp),
                        state = scrollState
                    ) {
                        items(count = items.itemCount) { index ->
                            val galleryItem = items[index]
                            galleryItem?.let {
                                GalleryCard(
                                    it, navController,
                                    statusMap[it.id]?.status?.name,
                                    statusMap[it.id]?.status?.color,
                                    statusMap[it.id]?.galleryStatus?.favorite ?: false
                                ) { viewModel.addGalleryToHistory(it) }
                            }
                        }

                        when (val state = items.loadState.append) {
                            is LoadState.Loading -> {
                                item { LoadingScreen(modifier = Modifier.padding(16.dp)) }
                            }

                            is LoadState.Error -> {
                                item {
                                    Text(
                                        "Failed to load data. Please try again.",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp)
                                    )
                                }
                            }

                            else -> {}
                        }
                    }
                }
            }

            FetchStatus.Check -> LoadingScreen()
        }
    } else {
        PermissionRequestScreen(internetPermissionState)
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionRequestScreen(internetPermissionState: PermissionState) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val message = if (internetPermissionState.status.shouldShowRationale)
                "The internet is important for this app. Please grant the permission."
            else
                "Internet permission required for this feature to be available. Please grant the permission."
            Text(message, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            ElevatedButton(onClick = { internetPermissionState.launchPermissionRequest() }) {
                Text("Request permission")
            }
        }
    }
}
