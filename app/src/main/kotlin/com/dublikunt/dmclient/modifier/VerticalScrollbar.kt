package com.dublikunt.dmclient.modifier

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.verticalScrollbar(
    state: LazyGridState,
    width: Dp = 8.dp,
    cornerRadius: Dp = 4.dp,
    minHeight: Dp = 32.dp
): Modifier {
    val targetAlpha = if (state.isScrollInProgress) 1f else 0f
    val duration = if (state.isScrollInProgress) 150 else 1000
    val onSurface = MaterialTheme.colorScheme.onSurface

    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = duration)
    )

    val scrollbarWidthPx = with(LocalDensity.current) { width.toPx() }
    val minHeightPx = with(LocalDensity.current) { minHeight.toPx() }

    return drawWithContent {
        drawContent()

        val firstVisibleElementIndex = state.layoutInfo.visibleItemsInfo.firstOrNull()?.index
        val needDrawScrollbar = state.isScrollInProgress || alpha > 0.0f

        if (needDrawScrollbar && firstVisibleElementIndex != null) {
            val totalItemsCount = state.layoutInfo.totalItemsCount
            val visibleItemsCount = state.layoutInfo.visibleItemsInfo.size

            val scrollbarOffsetY =
                (firstVisibleElementIndex.toFloat() / totalItemsCount) * size.height
            val calculatedHeight = (visibleItemsCount.toFloat() / totalItemsCount) * size.height
            val scrollbarHeight = maxOf(calculatedHeight, minHeightPx)

            val scrollbarColor = onSurface.copy(alpha = 0.6f * alpha)

            drawRoundRect(
                color = scrollbarColor,
                topLeft = Offset(this.size.width - scrollbarWidthPx, scrollbarOffsetY),
                size = Size(scrollbarWidthPx, scrollbarHeight),
                cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx())
            )
        }
    }
}

@Composable
fun Modifier.verticalColumnScrollbar(
    scrollState: ScrollState,
    width: Dp = 8.dp,
    cornerRadius: Dp = 4.dp,
    minHeight: Dp = 32.dp
): Modifier {
    val targetAlpha = if (scrollState.isScrollInProgress) 1f else 0f
    val duration = if (scrollState.isScrollInProgress) 150 else 1000
    val onSurface = MaterialTheme.colorScheme.onSurface

    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = duration)
    )

    val scrollbarWidthPx = with(LocalDensity.current) { width.toPx() }
    val minHeightPx = with(LocalDensity.current) { minHeight.toPx() }

    return drawWithContent {
        drawContent()

        val needDrawScrollbar = scrollState.isScrollInProgress || alpha > 0.0f
        if (needDrawScrollbar) {
            val scrollRange = scrollState.maxValue.toFloat()
            val viewportSize = size.height
            val scrollbarOffsetY = (scrollState.value / scrollRange) * viewportSize

            val scrollbarColor = onSurface.copy(alpha = 0.6f * alpha)

            drawRoundRect(
                color = scrollbarColor,
                topLeft = Offset(this.size.width - scrollbarWidthPx, scrollbarOffsetY),
                size = Size(scrollbarWidthPx, minHeightPx),
                cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx())
            )
        }
    }
}
