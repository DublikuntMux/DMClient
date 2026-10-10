package com.dublikunt.dmclient.ui.reader

import com.dublikunt.dmclient.data.settings.ReaderMode
import kotlin.math.max
import kotlin.math.min

internal fun resolveStartPage(routePage: Int?, historyPage: Int?, pageCount: Int): Int =
    (routePage ?: historyPage ?: 1).coerceIn(1, pageCount.coerceAtLeast(1))

internal enum class ReaderTap { Previous, Next, ToggleOverlay }

internal fun readerTap(xFraction: Float, rtl: Boolean): ReaderTap = when {
    xFraction < 0.3f -> if (rtl) ReaderTap.Next else ReaderTap.Previous
    xFraction > 0.7f -> if (rtl) ReaderTap.Previous else ReaderTap.Next
    else -> ReaderTap.ToggleOverlay
}

internal fun nextReaderMode(mode: ReaderMode): ReaderMode = when (mode) {
    ReaderMode.PagedLtr -> ReaderMode.PagedRtl
    ReaderMode.PagedRtl -> ReaderMode.Vertical
    ReaderMode.Vertical -> ReaderMode.PagedLtr
}

internal data class ZoomOffset(val x: Float, val y: Float)

internal fun clampZoomOffset(
    x: Float,
    y: Float,
    scale: Float,
    viewportWidth: Float,
    viewportHeight: Float,
    imageWidth: Float,
    imageHeight: Float
): ZoomOffset {
    if (viewportWidth <= 0 || viewportHeight <= 0 || imageWidth <= 0 || imageHeight <= 0) return ZoomOffset(0f, 0f)
    val fit = min(viewportWidth / imageWidth, viewportHeight / imageHeight)
    val maxX = max(0f, (imageWidth * fit * scale - viewportWidth) / 2)
    val maxY = max(0f, (imageHeight * fit * scale - viewportHeight) / 2)
    return ZoomOffset(
        if (maxX == 0f) 0f else x.coerceIn(-maxX, maxX),
        if (maxY == 0f) 0f else y.coerceIn(-maxY, maxY)
    )
}

internal fun zoomAroundPoint(
    offset: ZoomOffset,
    oldScale: Float,
    newScale: Float,
    pointX: Float,
    pointY: Float
): ZoomOffset {
    val ratio = newScale / oldScale
    return ZoomOffset(pointX - (pointX - offset.x) * ratio, pointY - (pointY - offset.y) * ratio)
}
