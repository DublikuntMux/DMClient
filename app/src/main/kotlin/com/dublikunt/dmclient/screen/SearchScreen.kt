package com.dublikunt.dmclient.screen

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.dublikunt.dmclient.component.LoadingScreen
import com.dublikunt.dmclient.repository.PreferenceRepository
import com.dublikunt.dmclient.repository.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val preferenceRepository: PreferenceRepository,
) : ViewModel() {
    private val json = Json { ignoreUnknownKeys = true }

    private val _tags = MutableStateFlow<List<String>>(emptyList())
    val tags: StateFlow<List<String>> = _tags.asStateFlow()

    private val _artists = MutableStateFlow<List<String>>(emptyList())
    val artists: StateFlow<List<String>> = _artists.asStateFlow()

    private val _characters = MutableStateFlow<List<String>>(emptyList())
    val characters: StateFlow<List<String>> = _characters.asStateFlow()

    private val _parodies = MutableStateFlow<List<String>>(emptyList())
    val parodies: StateFlow<List<String>> = _parodies.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadData(filesDir: File) {
        viewModelScope.launch {
            _isLoading.value = true
            val tagsFile = File(filesDir, "tags.json")
            val artistsFile = File(filesDir, "artists.json")
            val charactersFile = File(filesDir, "characters.json")
            val parodiesFile = File(filesDir, "parodies.json")

            if (tagsFile.exists()) {
                _tags.value = loadFromFile(tagsFile)
            } else {
                fetchAndSaveTags(filesDir)
            }

            if (artistsFile.exists()) {
                _artists.value = loadFromFile(artistsFile)
            } else {
                fetchAndSaveArtists(filesDir)
            }

            if (charactersFile.exists()) {
                _characters.value = loadFromFile(charactersFile)
            } else {
                fetchAndSaveCharacters(filesDir)
            }

            if (parodiesFile.exists()) {
                _parodies.value = loadFromFile(parodiesFile)
            } else {
                fetchAndSaveParodies(filesDir)
            }

            _isLoading.value = false
        }
    }

    private suspend fun loadFromFile(file: File): List<String> = withContext(Dispatchers.IO) {
        val jsonString = file.readText()
        json.decodeFromString<List<String>>(jsonString)
    }

    private suspend fun fetchAndSaveTags(filesDir: File) {
        val fetchedTags = withContext(Dispatchers.IO) { searchRepository.getAllTags() }
        saveToFile(fetchedTags, File(filesDir, "tags.json"))
        _tags.value = fetchedTags
    }

    private suspend fun fetchAndSaveArtists(filesDir: File) {
        val fetchedArtists = withContext(Dispatchers.IO) { searchRepository.getAllArtists() }
        saveToFile(fetchedArtists, File(filesDir, "artists.json"))
        _artists.value = fetchedArtists
    }

    private suspend fun fetchAndSaveCharacters(filesDir: File) {
        val fetchedCharacters = withContext(Dispatchers.IO) { searchRepository.getAllCharacters() }
        saveToFile(fetchedCharacters, File(filesDir, "characters.json"))
        _characters.value = fetchedCharacters
    }

    private suspend fun fetchAndSaveParodies(filesDir: File) {
        val fetchedParodies = withContext(Dispatchers.IO) { searchRepository.getAllParodies() }
        saveToFile(fetchedParodies, File(filesDir, "parodies.json"))
        _parodies.value = fetchedParodies
    }

    private suspend fun saveToFile(data: List<String>, file: File) {
        withContext(Dispatchers.IO) {
            val jsonString = json.encodeToString(data)
            file.writeText(jsonString)
        }
    }
}

@Composable
fun SearchScreen(
    navController: NavHostController,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val tags by viewModel.tags.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val characters by viewModel.characters.collectAsState()
    val parodies by viewModel.parodies.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

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

    LaunchedEffect(Unit) { viewModel.loadData(context.filesDir) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            LoadingScreen(text = "Loading...\nFirst time may take a while")
        } else {
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
