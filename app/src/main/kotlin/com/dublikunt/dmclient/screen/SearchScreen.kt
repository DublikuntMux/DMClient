package com.dublikunt.dmclient.screen

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val json = Json { ignoreUnknownKeys = true }
    val tags = mutableStateListOf<String>()
    val isLoading = mutableStateOf(true)

    fun loadTags(filesDir: File) {
        viewModelScope.launch {
            isLoading.value = true
            val tagsFile = File(filesDir, "tags.json")
            if (tagsFile.exists()) {
                tags.clear()
                tags.addAll(loadTagsFromFile(tagsFile))
            } else {
                fetchAndSaveTags(filesDir)
            }
            isLoading.value = false
        }
    }

    private suspend fun loadTagsFromFile(file: File): List<String> = withContext(Dispatchers.IO) {
        val jsonString = file.readText()
        json.decodeFromString<List<String>>(jsonString)
    }

    private suspend fun fetchAndSaveTags(filesDir: File) {
        val fetchedTags = withContext(Dispatchers.IO) {
            NHentaiApi.getAllTags()
        }
        val tagsFile = File(filesDir, "tags.json")
        saveTagsToFile(fetchedTags, tagsFile)
        tags.clear()
        tags.addAll(fetchedTags)
    }

    private suspend fun saveTagsToFile(tags: List<String>, file: File) {
        withContext(Dispatchers.IO) {
            val jsonString = json.encodeToString(tags)
            file.writeText(jsonString)
        }
    }
}

@Composable
fun SearchScreen(navController: NavHostController, viewModel: SearchViewModel = viewModel()) {
    val context = LocalContext.current
    val selectedTags = remember { mutableStateListOf<String>() }
    val searchQuery = remember { mutableStateOf("") }
    val tagSearchQuery = remember { mutableStateOf("") }
    val listState = rememberLazyGridState()

    LaunchedEffect(Unit) {
        viewModel.loadTags(context.filesDir)
    }

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
                        val query = concatenateStrings(searchQuery.value, selectedTags)
                        navController.navigate("search?query=${query}")
                    })
                    {
                        Icon(Icons.Rounded.Search, contentDescription = "Search")
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedTextField(
                    singleLine = true,
                    value = tagSearchQuery.value,
                    onValueChange = { tagSearchQuery.value = it },
                    label = { Text("Search Tags") },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (viewModel.isLoading.value) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Loading tags its can take a while...")
                    }
                }
            } else {
                LazyVerticalGrid(
                    state = listState,
                    columns = GridCells.Adaptive(minSize = 100.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                ) {
                    items(selectedTags) { tag ->
                        TagButton(tag, selectedTags)
                    }

                    viewModel.tags.filter {
                        it.contains(
                            tagSearchQuery.value,
                            ignoreCase = true
                        ) && !selectedTags.contains(it)
                    }
                        .forEach { tag ->
                            item {
                                TagButton(tag, selectedTags)
                            }
                        }
                }
            }
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

fun concatenateStrings(inputString: String, stringList: List<String>): String {
    val formattedInput = inputString.takeIf { it.isNotEmpty() }?.replace(" ", "+") ?: ""

    val formattedList = stringList.filter { it.isNotEmpty() }
        .joinToString("+") { it.replace(" ", "+") }

    return if (formattedInput.isNotEmpty() && formattedList.isNotEmpty()) {
        "$formattedInput+$formattedList"
    } else {
        formattedInput + formattedList
    }
}
