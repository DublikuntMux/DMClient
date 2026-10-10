package com.dublikunt.dmclient.ui.reader

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ZoomableImage(
    model: Any,
    page: Int,
    active: Boolean,
    onScaleChanged: (Float) -> Unit,
    onTap: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember(model) { mutableFloatStateOf(1f) }
    var offset by remember(model) { mutableStateOf(ZoomOffset(0f, 0f)) }
    var viewport by remember { mutableStateOf(Size.Zero) }
    var image by remember(model) { mutableStateOf(Size.Zero) }
    var animation by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val latestTap by rememberUpdatedState(onTap)

    fun clamp(value: ZoomOffset, zoom: Float) = clampZoomOffset(
        value.x, value.y, zoom, viewport.width, viewport.height, image.width, image.height
    )

    LaunchedEffect(scale, active) { if (active) onScaleChanged(scale) }
    LaunchedEffect(active) {
        if (!active) {
            animation?.cancel()
            scale = 1f
            offset = ZoomOffset(0f, 0f)
        }
    }
    LaunchedEffect(viewport, image) { offset = clamp(offset, scale) }

    Box(
        modifier.fillMaxSize().clipToBounds().onSizeChanged {
            viewport = Size(it.width.toFloat(), it.height.toFloat())
        }.pointerInput(model) {
            detectTapGestures(
                onTap = { latestTap(it.x / size.width.coerceAtLeast(1)) },
                onDoubleTap = { tap ->
                    animation?.cancel()
                    val startScale = scale
                    val startOffset = offset
                    val targetScale = if (scale > 1f) 1f else 2.5f
                    val targetOffset = clamp(
                        zoomAroundPoint(startOffset, startScale, targetScale, tap.x - viewport.width / 2, tap.y - viewport.height / 2), targetScale
                    )
                    animation = scope.launch {
                        animate(0f, 1f, animationSpec = spatialSpec) { fraction, _ ->
                            scale = (startScale + (targetScale - startScale) * fraction).coerceIn(1f, 5f)
                            offset = clamp(
                                ZoomOffset(startOffset.x + (targetOffset.x - startOffset.x) * fraction, startOffset.y + (targetOffset.y - startOffset.y) * fraction), scale
                            )
                        }
                        scale = targetScale
                        offset = targetOffset
                    }
                }
            )
        }.pointerInput(model) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var transforming = false
                do {
                    val event = awaitPointerEvent()
                    val multiplePointers = event.changes.count { it.pressed } > 1
                    val zoom = event.calculateZoom()
                    val pan = event.calculatePan()
                    if (multiplePointers || scale > 1f || transforming) {
                        if (!event.changes.any { it.isConsumed }) {
                            if (zoom != 1f || pan != Offset.Zero) {
                                transforming = true
                                animation?.cancel()
                                val nextScale = (scale * zoom).coerceIn(1f, 5f)
                                val centroid = event.calculateCentroid(useCurrent = false)
                                val anchored = zoomAroundPoint(offset, scale, nextScale, centroid.x - viewport.width / 2, centroid.y - viewport.height / 2)
                                scale = nextScale
                                offset = clamp(ZoomOffset(anchored.x + pan.x, anchored.y + pan.y), scale)
                                event.changes.forEach { if (it.pressed) it.consume() }
                            }
                        }
                    }
                } while (event.changes.any { it.pressed })
            }
        }
    ) {
        ReaderImage(
            model = model, page = page, onImageSize = { image = it },
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ReaderImage(
    model: Any,
    page: Int,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
    onImageSize: (Size) -> Unit = {}
) {
    var retry by remember(model) { mutableIntStateOf(0) }
    val context = LocalContext.current
    val request = remember(context, model, retry) {
        ImageRequest.Builder(context).data(model).memoryCacheKeyExtra("retry", retry.toString()).build()
    }
    SubcomposeAsyncImage(
        model = request, contentDescription = "Page $page", modifier = modifier,
        contentScale = if (vertical) ContentScale.FillWidth else ContentScale.Fit,
        onSuccess = { onImageSize(Size(it.result.image.width.toFloat(), it.result.image.height.toFloat())) },
        loading = {
            Box(
                (if (vertical) Modifier.fillMaxWidth().height(260.dp) else Modifier.fillMaxSize()).background(Color.Black),
                contentAlignment = Alignment.Center
            ) { LoadingIndicator(color = Color.White, modifier = Modifier.size(64.dp)) }
        },
        error = {
            Box(
                (if (vertical) Modifier.fillMaxWidth().height(260.dp) else Modifier.fillMaxSize()).background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                FilledTonalIconButton(onClick = { retry++ }, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.Refresh, "Retry loading page $page")
                }
            }
        }
    )
}
