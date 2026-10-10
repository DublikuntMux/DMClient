package com.dublikunt.dmclient.ui.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import com.dublikunt.dmclient.data.settings.ReaderMode
import com.dublikunt.dmclient.ui.components.ErrorState
import com.dublikunt.dmclient.ui.components.userMessage

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReaderScreen(onBack: () -> Unit) {
    val viewModel: ReaderViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var overlay by rememberSaveable { mutableStateOf(true) }
    var seek by remember { mutableStateOf<((Int) -> Unit)?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val window = LocalContext.current.findActivity()?.window
    val barsVisible = overlay || state.detail == null

    DisposableEffect(window) {
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val previousBehavior = controller?.systemBarsBehavior
        val previousLightStatus = controller?.isAppearanceLightStatusBars
        val previousLightNavigation = controller?.isAppearanceLightNavigationBars
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (previousBehavior != null) controller?.systemBarsBehavior = previousBehavior
            if (previousLightStatus != null) controller?.isAppearanceLightStatusBars = previousLightStatus
            if (previousLightNavigation != null) controller?.isAppearanceLightNavigationBars = previousLightNavigation
        }
    }
    LaunchedEffect(window, barsVisible) {
        window?.let {
            val controller = WindowCompat.getInsetsController(it, it.decorView)
            if (barsVisible) controller.show(WindowInsetsCompat.Type.systemBars())
            else controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(window, state.settings.keepScreenOn) {
        if (state.settings.keepScreenOn) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    DisposableEffect(viewModel) { onDispose { viewModel.flushProgress() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.flushProgress() }
    LaunchedEffect(viewModel) { viewModel.events.collect { snackbar.showSnackbar(it) } }

    Scaffold(containerColor = Color.Black, contentColor = Color.White, contentWindowInsets = WindowInsets(0), snackbarHost = { SnackbarHost(snackbar) }) { _ ->
        Box(Modifier.fillMaxSize()) {
            when {
                state.loading -> LoadingIndicator(Modifier.align(Alignment.Center), color = Color.White)
                state.error != null -> ErrorState(message = state.error!!.userMessage(), onRetry = viewModel::retry)
                state.detail != null -> ReaderPages(
                    state = state, viewModel = viewModel, onToggleOverlay = { overlay = !overlay },
                    onSeekReady = { seek = it }
                )
            }
            AnimatedVisibility(
                visible = barsVisible, modifier = Modifier.align(Alignment.TopCenter),
                enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) + slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { -it },
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) + slideOutVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { -it }
            ) {
                TopAppBar(
                    title = { Text(state.detail?.title ?: "Reader", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        FilledTonalIconButton(
                            onClick = onBack, shapes = IconButtonDefaults.shapes(),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.White.copy(alpha = 0.15f), contentColor = Color.White)
                        ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                    },
                    actions = {
                        state.detail?.let { Text("${state.page} / ${it.pageCount}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp)) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black.copy(alpha = 0.75f), titleContentColor = Color.White, actionIconContentColor = Color.White)
                )
            }
            AnimatedVisibility(
                visible = overlay && state.detail != null, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
                enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) + slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it },
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) + slideOutVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it }
            ) {
                val pages = state.detail?.pageCount ?: 1
                HorizontalFloatingToolbar(expanded = true, modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = state.page.toFloat(), onValueChange = { seek?.invoke(it.toInt()) },
                        valueRange = 1f..pages.coerceAtLeast(2).toFloat(), steps = (pages - 2).coerceAtLeast(0),
                        enabled = pages > 1,
                        modifier = Modifier.weight(1f).semantics { contentDescription = "Seek page" }
                    )
                    FilledTonalIconButton(onClick = viewModel::cycleMode, shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            if (state.settings.readerMode == ReaderMode.Vertical) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.SwapHoriz,
                            "Reading mode: ${state.settings.readerMode.label()}. Change reading mode"
                        )
                    }
                }
            }
        }
    }
}

private fun ReaderMode.label(): String = when (this) {
    ReaderMode.PagedLtr -> "Left to right"
    ReaderMode.PagedRtl -> "Right to left"
    ReaderMode.Vertical -> "Vertical"
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return context as? Activity
}
