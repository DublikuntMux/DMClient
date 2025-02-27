package com.dublikunt.dmclient.component

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import coil3.compose.AsyncImage
import coil3.request.ImageRequest

@Composable
fun GalleryPageViewer(
    imageUrl: String,
    pageIndex: Int,
    totalPages: Int,
    onClose: () -> Unit,
    onNextPage: () -> Unit,
    onPreviousPage: () -> Unit
) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset(0f, 0f)) }
    val maxScale = 5f
    val minScale = 0.2f
    val screenWidth = LocalDensity.current.density * LocalConfiguration.current.screenWidthDp

    val tapGesturesHandler = rememberUpdatedState { tapOffset: Offset ->
        when {
            tapOffset.x < screenWidth * 0.3f -> {
                if (pageIndex > 1) {
                    scale = 1f
                    offset = Offset(0f, 0f)
                    onPreviousPage()
                }
            }

            tapOffset.x > screenWidth * 0.7f -> {
                if (pageIndex < totalPages) {
                    scale = 1f
                    offset = Offset(0f, 0f)
                    onNextPage()
                }
            }

            else -> {
                scale = 1f
                offset = Offset(0f, 0f)
                onClose()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(pageIndex) {
                detectTapGestures { tapOffset ->
                    tapGesturesHandler.value(tapOffset)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(imageUrl)
                .build(),
            contentDescription = "Fullscreen Page $pageIndex",
            modifier = Modifier
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(minScale, maxScale)
                        offset = Offset(offset.x + (pan.x * scale), offset.y + (pan.y * scale))
                    }
                },
            contentScale = ContentScale.Fit
        )
    }
}