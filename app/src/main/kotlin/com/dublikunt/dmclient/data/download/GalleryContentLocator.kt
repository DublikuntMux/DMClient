package com.dublikunt.dmclient.data.download

import com.dublikunt.dmclient.network.ImageType
import java.io.File

object GalleryContentLocator {
    const val IMAGE_CDN = "https://i1.nhentai.net"
    const val THUMBNAIL_CDN = "https://t.nhentai.net"
    const val ROOT_DIR = "galleries"

    fun pageExtension(images: List<ImageType>, page: Int): String =
        when (images.getOrNull(page - 1)) {
            ImageType.Webp -> "webp"
            ImageType.Png -> "png"
            else -> "jpg"
        }

    fun remotePageUrl(mediaId: Int, page: Int, images: List<ImageType>): String =
        "$IMAGE_CDN/$ROOT_DIR/$mediaId/$page.${pageExtension(images, page)}"

    fun remoteThumbnailUrl(mediaId: Int, page: Int, images: List<ImageType>): String =
        "$THUMBNAIL_CDN/$ROOT_DIR/$mediaId/${page}t.${pageExtension(images, page)}"

    fun coverFileName(coverUrl: String): String {
        val extension = coverUrl.substringBefore('?').substringAfterLast('.', "jpg").lowercase()
        return "cover.${extension.takeIf { it in listOf("jpg", "jpeg", "webp", "png") } ?: "jpg"}"
    }

    fun relativeCoverPath(galleryId: Int, coverUrl: String): String =
        "$ROOT_DIR/$galleryId/${coverFileName(coverUrl)}"

    fun galleryDir(root: File, galleryId: Int): File = File(root, "$ROOT_DIR/$galleryId")
    fun pageFile(root: File, galleryId: Int, page: Int, images: List<ImageType>): File =
        File(galleryDir(root, galleryId), "$page.${pageExtension(images, page)}")

    fun coverFile(root: File, galleryId: Int, coverUrl: String): File =
        File(galleryDir(root, galleryId), coverFileName(coverUrl))
}
