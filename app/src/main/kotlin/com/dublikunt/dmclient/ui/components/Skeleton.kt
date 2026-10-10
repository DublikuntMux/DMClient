package com.dublikunt.dmclient.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.shimmer(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_progress"
    )
    val base = MaterialTheme.colorScheme.surfaceContainerHighest
    val highlight = MaterialTheme.colorScheme.surfaceContainerLow
    return drawBehind {
        val x = size.width * progress
        drawRect(
            Brush.linearGradient(
                colors = listOf(base, highlight, base),
                start = Offset(x - size.width / 2, 0f),
                end = Offset(x + size.width / 2, size.height)
            )
        )
    }
}

@Composable
fun GalleryCardSkeleton(modifier: Modifier = Modifier) {
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(GALLERY_COVER_ASPECT)
                .clip(MaterialTheme.shapes.large)
                .shimmer()
        )
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth(0.85f)
                .height(12.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .shimmer()
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth(0.5f)
                .height(12.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .shimmer()
        )
    }
}

@Composable
fun GalleryGridSkeleton(
    minCellSize: Dp,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = GalleryGridDefaults.ContentPadding
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minCellSize),
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = GalleryGridDefaults.HorizontalSpacing,
        verticalArrangement = GalleryGridDefaults.VerticalSpacing,
        userScrollEnabled = false
    ) {
        items(count = 18) { GalleryCardSkeleton() }
    }
}
