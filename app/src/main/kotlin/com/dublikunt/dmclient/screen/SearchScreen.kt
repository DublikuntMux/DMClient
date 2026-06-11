package com.dublikunt.dmclient.screen

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.work.WorkManager
import com.dublikunt.dmclient.database.search.SearchCacheDao
import com.dublikunt.dmclient.work.SearchCacheWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

enum class CacheStatus {
    Loading,
    Fetching,
    Ready,
    Error
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchCacheDao: SearchCacheDao,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
    private val workManager = WorkManager.getInstance(context)

    private val _tags = MutableStateFlow<List<String>>(emptyList())
    val tags: StateFlow<List<String>> = _tags.asStateFlow()

    private val _artists = MutableStateFlow<List<String>>(emptyList())
    val artists: StateFlow<List<String>> = _artists.asStateFlow()

    private val _characters = MutableStateFlow<List<String>>(emptyList())
    val characters: StateFlow<List<String>> = _characters.asStateFlow()

    private val _parodies = MutableStateFlow<List<String>>(emptyList())
    val parodies: StateFlow<List<String>> = _parodies.asStateFlow()

    private val _cacheStatus = MutableStateFlow(CacheStatus.Loading)
    val cacheStatus: StateFlow<CacheStatus> = _cacheStatus.asStateFlow()

    fun loadData(filesDir: File) {
        viewModelScope.launch {
            _cacheStatus.value = CacheStatus.Loading

            val cachedTags = searchCacheDao.getByType("tags")
            val cachedArtists = searchCacheDao.getByType("artists")
            val cachedCharacters = searchCacheDao.getByType("characters")
            val cachedParodies = searchCacheDao.getByType("parodies")

            cachedTags?.let { _tags.value = it.names }
            cachedArtists?.let { _artists.value = it.names }
            cachedCharacters?.let { _characters.value = it.names }
            cachedParodies?.let { _parodies.value = it.names }

            val allCached = cachedTags != null && cachedArtists != null &&
                    cachedCharacters != null && cachedParodies != null

            if (allCached) {
                _cacheStatus.value = CacheStatus.Ready
            } else {
                enqueueCacheWorker(filesDir)
            }
        }
    }

    private fun enqueueCacheWorker(filesDir: File) {
        _cacheStatus.value = CacheStatus.Fetching

        val request = androidx.work.OneTimeWorkRequestBuilder<SearchCacheWorker>()
            .build()

        workManager.enqueueUniqueWork(
            SearchCacheWorker.UNIQUE_WORK_NAME,
            androidx.work.ExistingWorkPolicy.KEEP,
            request
        )

        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(request.id)
                .collect { workInfo ->
                    val state = workInfo?.state
                    if (state == androidx.work.WorkInfo.State.SUCCEEDED) {
                        reloadFromDatabase(filesDir)
                    } else if (state == androidx.work.WorkInfo.State.FAILED) {
                        _cacheStatus.value = CacheStatus.Error
                    }
                }
        }
    }

    private suspend fun reloadFromDatabase(filesDir: File) {
        val cachedTags = searchCacheDao.getByType("tags")
        val cachedArtists = searchCacheDao.getByType("artists")
        val cachedCharacters = searchCacheDao.getByType("characters")
        val cachedParodies = searchCacheDao.getByType("parodies")

        cachedTags?.let { _tags.value = it.names }
        cachedArtists?.let { _artists.value = it.names }
        cachedCharacters?.let { _characters.value = it.names }
        cachedParodies?.let { _parodies.value = it.names }

        _cacheStatus.value = CacheStatus.Ready

        withContext(Dispatchers.IO) {
            listOf("artists.json", "characters.json", "tags.json", "parodies.json")
                .forEach { name -> File(filesDir, name).delete() }
        }
    }
}

@Composable
fun SearchScreen(
    navController: NavHostController,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val tags by viewModel.tags.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val characters by viewModel.characters.collectAsState()
    val parodies by viewModel.parodies.collectAsState()
    val cacheStatus by viewModel.cacheStatus.collectAsState()

    val selectedTags = remember { mutableStateListOf<String>() }
    val selectedArtists = remember { mutableStateListOf<String>() }
    val selectedCharacters = remember { mutableStateListOf<String>() }
    val selectedParodies = remember { mutableStateListOf<String>() }
    val searchQuery = remember { mutableStateOf("") }
    val tagSearchQuery = remember { mutableStateOf("") }
    val artistSearchQuery = remember { mutableStateOf("") }
    val characterSearchQuery = remember { mutableStateOf("") }
    val parodiesSearchQuery = remember { mutableStateOf("") }
    val scrollState = rememberLazyGridState()
    var selectedTab by remember { mutableIntStateOf(0) }

    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { viewModel.loadData(context.filesDir) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(text = "Search:", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                singleLine = true,
                value = searchQuery.value,
                onValueChange = { searchQuery.value = it },
                label = { Text("Query") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = {
                        val query = concatenateStrings(
                            searchQuery.value,
                            selectedTags,
                            selectedArtists,
                            selectedCharacters,
                            selectedParodies
                        )
                        navController.navigate("search?query=${query}")
                    }) {
                        Icon(Icons.Rounded.Search, contentDescription = "Search")
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            when (cacheStatus) {
                CacheStatus.Fetching -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = "Fetching tag data in background...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                CacheStatus.Error -> {
                    Text(
                        text = "Failed to fetch tag data. Pull to try again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                else -> {}
            }

            SecondaryTabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) { Text("Tags") }
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }) { Text("Artists") }
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 }) { Text("Character") }
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 }) { Text("Parodies") }
                Tab(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 }) { Text("Selected") }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (selectedTab) {
                0 -> {
                    OutlinedTextField(
                        singleLine = true,
                        value = tagSearchQuery.value,
                        onValueChange = { tagSearchQuery.value = it },
                        label = { Text("Search Tags") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TagGrid(selectedTags, tags, tagSearchQuery.value, scrollState)
                }

                1 -> {
                    OutlinedTextField(
                        singleLine = true,
                        value = artistSearchQuery.value,
                        onValueChange = { artistSearchQuery.value = it },
                        label = { Text("Search Artists") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TagGrid(selectedArtists, artists, artistSearchQuery.value, scrollState)
                }

                2 -> {
                    OutlinedTextField(
                        singleLine = true,
                        value = characterSearchQuery.value,
                        onValueChange = { characterSearchQuery.value = it },
                        label = { Text("Search Characters") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TagGrid(
                        selectedCharacters,
                        characters,
                        characterSearchQuery.value,
                        scrollState
                    )
                }

                3 -> {
                    OutlinedTextField(
                        singleLine = true,
                        value = parodiesSearchQuery.value,
                        onValueChange = { parodiesSearchQuery.value = it },
                        label = { Text("Search Parodies") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TagGrid(selectedParodies, parodies, parodiesSearchQuery.value, scrollState)
                }

                4 -> {
                    SelectedItemsGrid(
                        selectedTags,
                        selectedArtists,
                        selectedCharacters,
                        selectedParodies
                    )
                }
            }
        }
    }
}

@Composable
fun TagGrid(
    selectedItems: MutableList<String>,
    items: List<String>,
    searchQuery: String,
    scrollState: LazyGridState
) {
    LazyVerticalGrid(
        state = scrollState,
        columns = GridCells.Adaptive(minSize = 100.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp)
    ) {
        items(selectedItems) { item -> TagButton(item, selectedItems) }

        items.filter {
            it.contains(searchQuery, ignoreCase = true) && !selectedItems.contains(it)
        }.forEach { item ->
            item { TagButton(item, selectedItems) }
        }
    }
}

@Composable
fun SelectedItemsGrid(
    selectedTags: MutableList<String>,
    selectedArtists: MutableList<String>,
    selectedCharacters: MutableList<String>,
    selectedParodies: MutableList<String>
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 100.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp)
    ) {
        items(selectedTags) { item -> TagButton(item, selectedTags) }
        items(selectedArtists) { item -> TagButton(item, selectedArtists) }
        items(selectedCharacters) { item -> TagButton(item, selectedCharacters) }
        items(selectedParodies) { item -> TagButton(item, selectedParodies) }
    }
}

@Composable
fun TagButton(item: String, selectedItems: MutableList<String>) {
    val isSelected = selectedItems.contains(item)
    FilledTonalButton(
        onClick = {
            if (isSelected) selectedItems.remove(item)
            else selectedItems.add(item)
        },
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        shape = RectangleShape,
        modifier = Modifier.padding(4.dp)
    ) {
        Text(text = item, textAlign = TextAlign.Center)
    }
}

fun concatenateStrings(
    query: String,
    tags: List<String>,
    artists: List<String>,
    characters: List<String>,
    selectedParodies: SnapshotStateList<String>
): String {
    val sb = StringBuilder(query)
    tags.forEach { sb.append(" tag:\"$it\"") }
    artists.forEach { sb.append(" artist:\"$it\"") }
    characters.forEach { sb.append(" character:\"$it\"") }
    selectedParodies.forEach { sb.append(" parody:\"$it\"") }
    return sb.toString().trim().replace(" ", "+")
}
