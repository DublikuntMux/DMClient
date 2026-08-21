package com.dublikunt.dmclient.download

import com.dublikunt.dmclient.scrapper.ImageType
import org.junit.Assert.assertEquals
import org.junit.Test

class GalleryContentLocatorTest {

    private val images = listOf(ImageType.Jpg, ImageType.Webp, ImageType.Png)

    @Test
    fun `page extension follows the declared image type`() {
        assertEquals("jpg", GalleryContentLocator.pageExtension(images, page = 1))
        assertEquals("webp", GalleryContentLocator.pageExtension(images, page = 2))
        assertEquals("png", GalleryContentLocator.pageExtension(images, page = 3))
    }

    @Test
    fun `missing image type falls back to jpg`() {
        assertEquals("jpg", GalleryContentLocator.pageExtension(emptyList(), page = 5))
        assertEquals("jpg", GalleryContentLocator.pageExtension(images, page = 99))
    }

    @Test
    fun `remote page url targets the gallery CDN with the right extension`() {
        val url = GalleryContentLocator.remotePageUrl(pagesId = 555001, page = 2, images = images)

        assertEquals("https://i1.nhentai.net/galleries/555001/2.webp", url)
    }

    @Test
    fun `cover file name keeps the thumbnail extension`() {
        assertEquals("cover.jpg", GalleryContentLocator.coverFileName("https://t.nhentai.net/galleries/1/cover.jpg"))
        assertEquals("cover.png", GalleryContentLocator.coverFileName("galleries/2/cover.png"))
        assertEquals("cover.jpg", GalleryContentLocator.coverFileName("no-extension"))
    }

    @Test
    fun `relative cover path lives inside the gallery folder`() {
        val path = GalleryContentLocator.relativeCoverPath(galleryId = 42, thumbUrl = "https://t.nhentai.net/galleries/999/cover.webp")

        assertEquals("galleries/42/cover.webp", path)
    }

    @Test
    fun `local paths resolve under a caller-provided root without Context`() {
        val dir = java.io.File("/tmp/filesdir")

        val pagePath = GalleryContentLocator.localPageAbsolutePath(dir, galleryId = 42, page = 3, images = images)
        val coverFile = GalleryContentLocator.coverFile(dir, galleryId = 42, thumbUrl = "https://t.nhentai.net/galleries/999/cover.jpg")
        val galleryDir = GalleryContentLocator.galleryDir(dir, galleryId = 42)

        assertEquals("/tmp/filesdir/galleries/42/3.png", pagePath)
        assertEquals("/tmp/filesdir/galleries/42/cover.jpg", coverFile.absolutePath)
        assertEquals("/tmp/filesdir/galleries/42", galleryDir.absolutePath)
    }
}
