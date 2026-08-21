package com.dublikunt.dmclient.download

import com.dublikunt.dmclient.scrapper.ImageType
import java.io.File

object GalleryContentLocator {
    const val IMAGE_CDN = "https://i1.nhentai.net"
    const val ROOT_DIR = "galleries"

    fun pageExtension(images: List<ImageType>, page: Int): String =
        when (images.getOrNull(page - 1)) {
            ImageType.Webp -> "webp"
            ImageType.Png -> "png"
            else -> "jpg"
        }

    fun remotePageUrl(pagesId: Int, page: Int, images: List<ImageType>): String =
        "$IMAGE_CDN/$ROOT_DIR/$pagesId/$page.${pageExtension(images, page)}"

    fun coverFileName(thumbUrl: String): String {
        val ext = thumbUrl.substringAfterLast('.', missingDelimiterValue = "jpg")
        return if (ext == thumbUrl || ext.isBlank() || ext.contains('/')) "cover.jpg"
        else "cover.$ext"
    }

    fun relativeCoverPath(galleryId: Int, thumbUrl: String): String =
        "$ROOT_DIR/$galleryId/${coverFileName(thumbUrl)}"

    fun galleryDir(root: File, galleryId: Int): File = File(root, "$ROOT_DIR/$galleryId")

    fun pageFile(root: File, galleryId: Int, page: Int, images: List<ImageType>): File =
        File(galleryDir(root, galleryId), "$page.${pageExtension(images, page)}")

    fun coverFile(root: File, galleryId: Int, thumbUrl: String): File =
        File(galleryDir(root, galleryId), coverFileName(thumbUrl))

    fun localPageAbsolutePath(root: File, galleryId: Int, page: Int, images: List<ImageType>): String =
        pageFile(root, galleryId, page, images).absolutePath

    fun resolveCover(root: File, storedRelativePath: String): String =
        File(root, storedRelativePath).absolutePath
}
