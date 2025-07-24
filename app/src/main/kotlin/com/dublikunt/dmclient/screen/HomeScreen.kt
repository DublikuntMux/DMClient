package com.dublikunt.dmclient.screen


import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.dublikunt.dmclient.component.GalleryCard
import com.dublikunt.dmclient.database.AppDatabase
import com.dublikunt.dmclient.database.PreferenceHelper
import com.dublikunt.dmclient.database.history.GalleryHistory
import com.dublikunt.dmclient.database.status.GalleryStatus
import com.dublikunt.dmclient.scrapper.GallerySimpleInfo
import com.dublikunt.dmclient.scrapper.NHentaiApi
import com.dublikunt.dmclient.scrapper.NHentaiWebView
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class FetchStatus {
    Check,
    Fetched,
    NotFetched
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    var tokenFetched by mutableStateOf(FetchStatus.Check)
    val stateList = mutableStateListOf<GallerySimpleInfo>()
    private var currentPage by mutableIntStateOf(1)
    var isLoading by mutableStateOf(false)
    var isError by mutableStateOf(false)

    private val db = AppDatabase.getDatabase(application)
    private val historyDao = db.galleryHistoryDao()
    private val statusDao = db.galleryStatusDao()

    val statusMap = mutableStateMapOf<Int, GalleryStatus?>()

    fun fetchNextPage(scope: CoroutineScope) {
        if (isLoading) return
        isLoading = true
        isError = false

        scope.launch {
            try {
                val fetched = withContext(Dispatchers.IO) {
                    NHentaiApi.fetchMainPage(currentPage)
                }
                if (fetched.isNotEmpty()) {
                    stateList.addAll(fetched)
                    currentPage++
                    fetchStatuses(fetched.map { it.id })
                }
                isLoading = false
            } catch (e: Exception) {
                isError = true
                isLoading = false
            }
        }
    }

    fun addGalleryToHistory(gallery: GallerySimpleInfo) {
        viewModelScope.launch {
            historyDao.insertHistory(GalleryHistory(gallery.id, gallery.thumb, gallery.name))
        }
    }

    private fun fetchStatuses(ids: List<Int>) {
        viewModelScope.launch(Dispatchers.IO) {
            ids.forEach { id ->
                val status = statusDao.getStatus(id)
                statusMap[id] = status
            }
        }
    }

    fun saveTokensAndFetch(session: String, token: String) {
        viewModelScope.launch {
            PreferenceHelper.saveTokens(getApplication(), session, token)
            NHentaiApi.setTokens(session, token)
            tokenFetched = FetchStatus.Fetched
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(navController: NavHostController, viewModel: HomeViewModel = viewModel()) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val internetPermissionState = rememberPermissionState(android.Manifest.permission.INTERNET)
    val isPermissionGranted by rememberUpdatedState(internetPermissionState.status.isGranted)

    val scrollState = rememberLazyGridState()

    LaunchedEffect(viewModel.tokenFetched) {
        if (viewModel.tokenFetched == FetchStatus.Check) {
            val language = PreferenceHelper.getPreferredLanguage(context).firstOrNull() ?: "all"
            val session = PreferenceHelper.getSessionAffinity(context).firstOrNull()
            val token = PreferenceHelper.getCsrfToken(context).firstOrNull()

            NHentaiApi.setLanguage(language)

            if (!session.isNullOrEmpty() && !token.isNullOrEmpty()) {
                NHentaiApi.setTokens(session, token)
                viewModel.tokenFetched = FetchStatus.Fetched
            } else {
                viewModel.tokenFetched = FetchStatus.NotFetched
            }
        }
    }

    if (isPermissionGranted) {
        when (viewModel.tokenFetched) {
            FetchStatus.NotFetched -> NHentaiWebView { session, token ->
                viewModel.saveTokensAndFetch(session, token)
            }

            FetchStatus.Fetched -> {
                if (viewModel.stateList.isEmpty() && !viewModel.isLoading) {
                    viewModel.fetchNextPage(scope)
                }

                LazyVerticalGrid(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    columns = GridCells.Adaptive(minSize = 128.dp),
                    state = scrollState
                ) {
                    items(viewModel.stateList) { galleryItem ->
                        GalleryCard(
                            galleryItem,
                            navController,
                            viewModel.statusMap[galleryItem.id]?.status,
                            viewModel.statusMap[galleryItem.id]?.favorite ?: false
                        ) {
                            viewModel.addGalleryToHistory(galleryItem)
                        }
                    }

                    item {
                        if (viewModel.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            )
                        } else if (viewModel.isError) {
                            Text(
                                "Failed to load data. Please try again.",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            )
                        } else {
                            LaunchedEffect(Unit) {
                                viewModel.fetchNextPage(scope)
                            }
                        }
                    }
                }
            }

            else -> Unit
        }
    } else {
        PermissionRequestScreen(internetPermissionState)
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionRequestScreen(internetPermissionState: PermissionState) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val message = if (internetPermissionState.status.shouldShowRationale) {
                "The internet is important for this app. Please grant the permission."
            } else {
                "Internet permission required for this feature to be available. Please grant the permission."
            }
            Text(message, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { internetPermissionState.launchPermissionRequest() }) {
                Text("Request permission")
            }
        }
    }
}
