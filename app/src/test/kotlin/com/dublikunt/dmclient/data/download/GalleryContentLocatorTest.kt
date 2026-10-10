package com.dublikunt.dmclient.data.download

import com.dublikunt.dmclient.network.ImageType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class GalleryContentLocatorTest {
    private val images = listOf(ImageType.Jpg, ImageType.Webp, ImageType.Png)
    @Test fun `page extensions retain persisted image type mapping`() {
        assertEquals("jpg", GalleryContentLocator.pageExtension(images, 1))
        assertEquals("webp", GalleryContentLocator.pageExtension(images, 2))
        assertEquals("png", GalleryContentLocator.pageExtension(images, 3))
        assertEquals("jpg", GalleryContentLocator.pageExtension(images, 99))
    }
    @Test fun `page and thumbnail URLs preserve CDN hosts and one-based page numbering`() {
        assertEquals("https://i1.nhentai.net/galleries/555001/2.webp", GalleryContentLocator.remotePageUrl(555001, 2, images))
        assertEquals("https://t.nhentai.net/galleries/555001/2t.webp", GalleryContentLocator.remoteThumbnailUrl(555001, 2, images))
    }
    @Test fun `cover and page files preserve the existing layout`() {
        val root = File("/tmp/filesdir")
        assertEquals("galleries/42/cover.webp", GalleryContentLocator.relativeCoverPath(42, "https://t.nhentai.net/cover.webp"))
        assertEquals("/tmp/filesdir/galleries/42/3.png", GalleryContentLocator.pageFile(root, 42, 3, images).absolutePath)
        assertEquals("/tmp/filesdir/galleries/42/cover.png", GalleryContentLocator.coverFile(root, 42, "cover.png").absolutePath)
        assertEquals("cover.jpg", GalleryContentLocator.coverFileName("no-extension"))
        assertEquals("cover.jpg", GalleryContentLocator.coverFileName("https://host/cover.jpg?x=1"))
    }
}
