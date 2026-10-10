package com.dublikunt.dmclient.ui.gallery

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dublikunt.dmclient.network.Tag
import com.dublikunt.dmclient.ui.components.ErrorState
import com.dublikunt.dmclient.ui.components.LoadingState
import com.dublikunt.dmclient.ui.components.userMessage
import kotlinx.coroutines.launch

@Composable
fun GalleryScreen(
    onBack: () -> Unit,
    onRead: (id: Int, page: Int?) -> Unit,
    onSearchTag: (Tag) -> Unit
) {
    val viewModel: GalleryViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val grid = rememberLazyGridState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var overflow by remember { mutableStateOf(false) }
    val collapsed by remember {
        derivedStateOf { state.detail == null || grid.firstVisibleItemIndex > 0 }
    }
    val barColor by animateColorAsState(
        if (collapsed) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(), label = "gallery_bar"
    )

    fun copy(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.primaryClip = ClipData.newPlainText("Gallery", text)
        scope.launch { snackbar.showSnackbar("Copied") }
    }

    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) viewModel.exportArchive()
            else scope.launch { snackbar.showSnackbar("Storage permission is needed to export ZIP files") }
        }
    val export: () -> Unit = {
        if (Build.VERSION.SDK_INT == 28 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        else viewModel.exportArchive()
    }
    LaunchedEffect(viewModel) { viewModel.events.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    if (collapsed) Text(
                        state.detail?.title ?: "Gallery",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = barColor),
                navigationIcon = {
                    FilledTonalIconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
                actions = {
                    state.detail?.let { detail ->
                        FilledTonalIconButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "https://nhentai.net/g/${detail.id}/"
                                    )
                                }
                                context.startActivity(Intent.createChooser(intent, "Share gallery"))
                            }, shapes = IconButtonDefaults.shapes()
                        ) { Icon(Icons.Rounded.Share, "Share gallery") }
                        Box {
                            FilledTonalIconButton(
                                onClick = { overflow = true }, shapes = IconButtonDefaults.shapes()
                            ) { Icon(Icons.Rounded.MoreVert, "More options") }
                            DropdownMenu(
                                expanded = overflow,
                                onDismissRequest = { overflow = false }) {
                                DropdownMenuItem(text = { Text("Copy title") }, onClick = {
                                    overflow = false
                                    copy(detail.title)
                                })
                                DropdownMenuItem(text = { Text("Open in browser") }, onClick = {
                                    overflow = false
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            "https://nhentai.net/g/${detail.id}/".toUri()
                                        )
                                    )
                                })
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.error != null -> ErrorState(
                message = state.error!!.userMessage(),
                onRetry = viewModel::retry,
                modifier = Modifier.padding(padding)
            )

            state.detail != null -> GalleryContent(
                state = state, grid = grid, headerTopPadding = padding.calculateTopPadding(),
                contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 16.dp),
                viewModel = viewModel, onRead = onRead, onSearchTag = onSearchTag,
                onCopyTag = { copy(it.name) }, onExport = export, modifier = Modifier.fillMaxSize()
            )
        }
    }
}
