package com.dublikunt.dmclient.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.dublikunt.dmclient.component.GalleryCard
import com.dublikunt.dmclient.database.AppDatabase
import com.dublikunt.dmclient.scrapper.GallerySimpleInfo
import java.io.File

class DownloadViewModel(application: android.app.Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    val downloadedGalleries = db.downloadedGalleryDao().getAll()
}

@Composable
fun DownloadScreen(navController: NavHostController, viewModel: DownloadViewModel = viewModel()) {
    val galleries by viewModel.downloadedGalleries.collectAsState(initial = null)

    if (galleries == null) {
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("Loading...", textAlign = TextAlign.Center)
            }
        }
    } else if (galleries!!.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No downloaded galleries found.")
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(galleries!!) { gallery ->
                val simpleInfo = GallerySimpleInfo(
                    id = gallery.id,
                    thumb = File(gallery.coverPath).path,
                    name = gallery.title
                )

                GalleryCard(
                    gallery = simpleInfo,
                    navController = navController,
                    status = null,
                    isFavorite = false
                )
            }
        }
    }
}
