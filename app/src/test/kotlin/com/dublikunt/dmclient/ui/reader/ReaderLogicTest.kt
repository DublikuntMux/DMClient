package com.dublikunt.dmclient.ui.reader

import com.dublikunt.dmclient.data.settings.ReaderMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderLogicTest {
    @Test
    fun explicitPageWinsOverHistoryAndHistoryWinsOverDefault() {
        assertEquals(8, resolveStartPage(8, 12, 20))
        assertEquals(12, resolveStartPage(null, 12, 20))
        assertEquals(1, resolveStartPage(null, null, 20))
    }

    @Test
    fun startPageIsClampedWhenHistoryOrRouteFallsOutsideGallery() {
        assertEquals(1, resolveStartPage(-2, 8, 20))
        assertEquals(20, resolveStartPage(null, 25, 20))
        assertEquals(20, resolveStartPage(25, null, 20))
        assertEquals(1, resolveStartPage(null, null, 0))
    }

    @Test
    fun outerTapZonesAreMirroredInRtl() {
        assertEquals(ReaderTap.Previous, readerTap(0.29f, false))
        assertEquals(ReaderTap.Next, readerTap(0.71f, false))
        assertEquals(ReaderTap.Next, readerTap(0.29f, true))
        assertEquals(ReaderTap.Previous, readerTap(0.71f, true))
        listOf(false, true).forEach { rtl ->
            listOf(0.3f, 0.5f, 0.7f).forEach { fraction ->
                assertEquals(ReaderTap.ToggleOverlay, readerTap(fraction, rtl))
            }
        }
    }

    @Test
    fun readerModeCyclesInSpecifiedOrder() {
        assertEquals(ReaderMode.PagedRtl, nextReaderMode(ReaderMode.PagedLtr))
        assertEquals(ReaderMode.Vertical, nextReaderMode(ReaderMode.PagedRtl))
        assertEquals(ReaderMode.PagedLtr, nextReaderMode(ReaderMode.Vertical))
    }

    @Test
    fun unzoomedFitImageCannotPanIncludingLetterboxedAxis() {
        assertEquals(ZoomOffset(0f, 0f), clampZoomOffset(200f, -200f, 1f, 400f, 800f, 600f, 800f))
    }

    @Test
    fun zoomBoundsUseActualFittedImageRatherThanViewportSize() {
        assertEquals(
            ZoomOffset(200f, -400f),
            clampZoomOffset(300f, -500f, 2f, 400f, 800f, 400f, 800f)
        )
        assertEquals(
            ZoomOffset(-200f, 0f),
            clampZoomOffset(-300f, 400f, 2f, 400f, 800f, 800f, 400f)
        )
        assertEquals(ZoomOffset(20f, -30f), clampZoomOffset(20f, -30f, 2f, 400f, 800f, 400f, 800f))
    }

    @Test
    fun missingDimensionsHaveZeroPanBounds() {
        assertEquals(ZoomOffset(0f, 0f), clampZoomOffset(100f, 200f, 2f, 0f, 800f, 400f, 800f))
        assertEquals(ZoomOffset(0f, 0f), clampZoomOffset(100f, 200f, 2f, 400f, 800f, 0f, 0f))
    }

    @Test
    fun scalingAroundTapKeepsTheTappedImagePointStationary() {
        assertEquals(
            ZoomOffset(-150f, 75f),
            zoomAroundPoint(ZoomOffset(0f, 0f), 1f, 2.5f, 100f, -50f)
        )
        val initial = ZoomOffset(20f, -30f)
        val zoomed = zoomAroundPoint(initial, 1f, 2.5f, 100f, -50f)
        assertEquals(initial, zoomAroundPoint(zoomed, 2.5f, 1f, 100f, -50f))
    }
}
