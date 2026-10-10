package com.dublikunt.dmclient.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest

/**
 * Coil image with a shimmer placeholder and a tap-to-retry error state.
 * [model] is a URL string or a local [java.io.File].
 */
@Composable
fun GalleryImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    sizePx: Int? = null
) {
    var retryKey by remember(model) { mutableIntStateOf(0) }
    val context = LocalContext.current
    val request = remember(model, retryKey, sizePx) {
        ImageRequest.Builder(context)
            .data(model)
            .apply { if (sizePx != null) size(sizePx) }
            .memoryCacheKeyExtra("retry", retryKey.toString())
            .build()
    }
    SubcomposeAsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        loading = { Box(Modifier.matchParentSize().shimmer()) },
        error = {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = { retryKey++ }) {
                    Icon(
                        if (retryKey == 0) Icons.Rounded.BrokenImage else Icons.Rounded.Refresh,
                        contentDescription = "Retry loading image",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    )
}
