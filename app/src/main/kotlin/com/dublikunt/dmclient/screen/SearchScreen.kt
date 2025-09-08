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
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.dublikunt.dmclient.scrapper.NHentaiApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val json = Json { ignoreUnknownKeys = true }
    val tags = mutableStateListOf<String>()
    val artists = mutableStateListOf<String>()
    val characters = mutableStateListOf<String>()
    val isLoading = mutableStateOf(true)

    fun loadData(filesDir: File) {
        viewModelScope.launch {
            isLoading.value = true
            val tagsFile = File(filesDir, "tags.json")
            val artistsFile = File(filesDir, "artists.json")
            val charactersFile = File(filesDir, "characters.json")

            if (tagsFile.exists()) {
                tags.clear()
                tags.addAll(loadFromFile(tagsFile))
            } else {
                fetchAndSaveTags(filesDir)
            }

            if (artistsFile.exists()) {
                artists.clear()
                artists.addAll(loadFromFile(artistsFile))
            } else {
                fetchAndSaveArtists(filesDir)
            }

            if (charactersFile.exists()) {
                characters.clear()
                characters.addAll(loadFromFile(charactersFile))
            } else {
                fetchAndSaveCharacters(filesDir)
            }

            isLoading.value = false
        }
    }

    private suspend fun loadFromFile(file: File): List<String> = withContext(Dispatchers.IO) {
        val jsonString = file.readText()
        json.decodeFromString<List<String>>(jsonString)
    }

    private suspend fun fetchAndSaveTags(filesDir: File) {
        val fetchedTags = withContext(Dispatchers.IO) {
            NHentaiApi.getAllTags()
        }
        val tagsFile = File(filesDir, "tags.json")
        saveToFile(fetchedTags, tagsFile)
        tags.clear()
        tags.addAll(fetchedTags)
    }

    private suspend fun fetchAndSaveArtists(filesDir: File) {
        val fetchedArtists = withContext(Dispatchers.IO) {
            NHentaiApi.getAllArtists()
        }
        val artistsFile = File(filesDir, "artists.json")
        saveToFile(fetchedArtists, artistsFile)
        artists.clear()
        artists.addAll(fetchedArtists)
    }

    private suspend fun fetchAndSaveCharacters(filesDir: File) {
        val fetchedCharacters = withContext(Dispatchers.IO) {
            NHentaiApi.getAllCharacters()
        }
        val charactersFile = File(filesDir, "characters.json")
        saveToFile(fetchedCharacters, charactersFile)
        characters.clear()
        characters.addAll(fetchedCharacters)
    }

    private suspend fun saveToFile(data: List<String>, file: File) {
        withContext(Dispatchers.IO) {
            val jsonString = json.encodeToString(data)
            file.writeText(jsonString)
        }
    }
}

@Composable
fun SearchScreen(navController: NavHostController, viewModel: SearchViewModel = viewModel()) {
    val context = LocalContext.current
    val selectedTags = remember { mutableStateListOf<String>() }
    val selectedArtists = remember { mutableStateListOf<String>() }
    val selectedCharacters = remember { mutableStateListOf<String>() }
    val searchQuery = remember { mutableStateOf("") }
    val tagSearchQuery = remember { mutableStateOf("") }
    val artistSearchQuery = remember { mutableStateOf("") }
    val characterSearchQuery = remember { mutableStateOf("") }
    val scrollState = rememberLazyGridState()
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.loadData(context.filesDir)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (viewModel.isLoading.value) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp)
            )
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
                                selectedCharacters
                            )
                            navController.navigate("search?query=${query}")
                        }) {
                            Icon(Icons.Rounded.Search, contentDescription = "Search")
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                        Text("Tags")
                    }
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                        Text("Artists")
                    }
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }) {
                        Text("Characters")
                    }
                    Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }) {
                        Text("Selected")
                    }
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
                        TagGrid(selectedTags, viewModel.tags, tagSearchQuery.value, scrollState)
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
                        TagGrid(
                            selectedArtists,
                            viewModel.artists,
                            artistSearchQuery.value,
                            scrollState
                        )
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
                            viewModel.characters,
                            characterSearchQuery.value,
                            scrollState
                        )
                    }

                    3 -> {
                        SelectedItemsGrid(selectedTags, selectedArtists, selectedCharacters)
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
        items(selectedItems) { item ->
            TagButton(item, selectedItems)
        }

        items.filter {
            it.contains(searchQuery, ignoreCase = true) && !selectedItems.contains(it)
        }.forEach { item ->
            item {
                TagButton(item, selectedItems)
            }
        }
    }
}

@Composable
fun SelectedItemsGrid(
    selectedTags: List<String>,
    selectedArtists: List<String>,
    selectedCharacters: List<String>
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 100.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp)
    ) {
        items(selectedTags) { tag ->
            TagButton(tag, selectedTags.toMutableList())
        }
        items(selectedArtists) { artist ->
            TagButton(artist, selectedArtists.toMutableList())
        }
        items(selectedCharacters) { character ->
            TagButton(character, selectedCharacters.toMutableList())
        }
    }
}

@Composable
fun TagButton(tag: String, selectedTags: MutableList<String>) {
    val isSelected = selectedTags.contains(tag)
    Button(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        onClick = {
            if (isSelected) selectedTags.remove(tag) else selectedTags.add(tag)
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
        ),
        shape = RectangleShape
    ) {
        Text(
            text = tag,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
        )
    }
}

fun concatenateStrings(
    inputString: String,
    tags: List<String>,
    artists: List<String>,
    characters: List<String>
): String {
    val formattedInput = inputString.takeIf { it.isNotEmpty() }?.replace(" ", "+") ?: ""

    val formattedTags = tags.filter { it.isNotEmpty() }
        .joinToString("+") { it.replace(" ", "+") }

    val formattedArtists = artists.filter { it.isNotEmpty() }
        .joinToString("+") { it.replace(" ", "+") }

    val formattedCharacters = characters.filter { it.isNotEmpty() }
        .joinToString("+") { it.replace(" ", "+") }

    return listOf(formattedInput, formattedTags, formattedArtists, formattedCharacters)
        .filter { it.isNotEmpty() }
        .joinToString("+")
}
