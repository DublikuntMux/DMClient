package com.dublikunt.dmclient.download

import androidx.work.WorkInfo
import com.dublikunt.dmclient.scrapper.GalleryFullInfo
import com.dublikunt.dmclient.scrapper.ImageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadControllerTest {

    private val gallery = GalleryFullInfo(
        id = 1770002,
        thumb = "https://t.nhentai.net/galleries/999/cover.webp",
        name = "Sample",
        parodies = listOf("Parody"),
        tags = listOf("Tag"),
        artists = listOf("Artist"),
        characters = emptyList(),
        pages = 3,
        pagesId = 555001,
        images = listOf(ImageType.Jpg, ImageType.Webp, ImageType.Png)
    )

    @Test
    fun `payload survives an encode-decode round trip`() {
        val encoded = DownloadPayload.encode(gallery)

        assertEquals(gallery, DownloadPayload.decode(encoded))
    }

    @Test
    fun `garbage payload decodes to null instead of crashing`() {
        assertNull(DownloadPayload.decode("not json at all"))
        assertNull(DownloadPayload.decode("{\"unknown\":true}"))
    }

    @Test
    fun `work naming follows one convention`() {
        assertEquals("download_1770002", DownloadController.uniqueDownloadName(1770002))
        assertEquals("archive_1770002", DownloadController.uniqueArchiveName(1770002))
    }

    @Test
    fun `work state maps onto download phases`() {
        assertEquals(DownloadPhase.Idle, DownloadController.phaseFrom(null))
        assertEquals(DownloadPhase.Running, DownloadController.phaseFrom(state(WorkInfo.State.ENQUEUED)))
        assertEquals(DownloadPhase.Running, DownloadController.phaseFrom(state(WorkInfo.State.RUNNING)))
        assertEquals(DownloadPhase.Succeeded, DownloadController.phaseFrom(state(WorkInfo.State.SUCCEEDED)))
        assertEquals(DownloadPhase.Failed, DownloadController.phaseFrom(state(WorkInfo.State.FAILED)))
        assertEquals(DownloadPhase.Failed, DownloadController.phaseFrom(state(WorkInfo.State.CANCELLED)))
    }

    private fun state(s: WorkInfo.State): WorkInfo =
        WorkInfo(
            java.util.UUID.randomUUID(), s, emptySet(),
            androidx.work.Data.EMPTY, androidx.work.Data.EMPTY, 0
        )
}
