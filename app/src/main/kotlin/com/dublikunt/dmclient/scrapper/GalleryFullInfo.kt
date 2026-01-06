package com.dublikunt.dmclient.scrapper

import kotlinx.serialization.Serializable

@Serializable
enum class ImageType {
    Jpg,
    Webp
}

@Serializable
data class GalleryFullInfo(
    val id: Int,
    val thumb: String,
    val name: String,
    val tags: List<String>,
    val artists: List<String>,
    val characters: List<String>,
    val pages: Int,
    val pagesId: Int,
    val images: List<ImageType>
)

// https://t{1-9}.nhentai.net/galleries/{pagesId}/{page}t.{jpg|webp}
// https://i{1-9}.nhentai.net/galleries/{pagesId}/{page}.{jpg|webp}
