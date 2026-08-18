package com.dublikunt.dmclient.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.shimmer(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val brush = remember(translateAnim, base) {
        Brush.linearGradient(
            colors = listOf(base, base.copy(alpha = 0.55f), base),
            start = Offset(translateAnim - 300f, 0f),
            end = Offset(translateAnim + 300f, 0f)
        )
    }
    return this.background(brush)
}

@Composable
private fun Modifier.skeletonShape(): Modifier =
    this
        .clip(RoundedCornerShape(4.dp))
        .shimmer()

@Composable
fun GalleryCardSkeleton(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .padding(8.dp)
            .fillMaxWidth()
            .height(200.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .shimmer()
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .height(14.dp)
                    .skeletonShape()
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
fun GalleryGridSkeleton(
    modifier: Modifier = Modifier,
    minSize: Dp = 128.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp)
) {
    LazyVerticalGrid(
        modifier = modifier.fillMaxSize(),
        columns = GridCells.Adaptive(minSize = minSize),
        userScrollEnabled = false,
        contentPadding = contentPadding
    ) {
        items(12) { GalleryCardSkeleton() }
    }
}

@Composable
fun GalleryLoadingRowSkeleton() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        repeat(3) {
            GalleryCardSkeleton(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun GalleryDetailSkeleton() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 16.dp),
        userScrollEnabled = false
    ) {
        item {
            Column(modifier = Modifier.padding(16.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .shimmer()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(24.dp)
                        .skeletonShape()
                )
                Spacer(modifier = Modifier.height(12.dp))
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(18.dp)
                            .skeletonShape()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
        items(6) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .height(300.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .shimmer()
            )
        }
    }
}
