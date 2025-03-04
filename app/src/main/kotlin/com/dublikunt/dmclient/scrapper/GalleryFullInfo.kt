package com.dublikunt.dmclient.scrapper

enum class ImageType {
    Jpg,
    Webp
}

data class GalleryFullInfo(
    val id: Int,
    val thumb: String,
    val name: String,
    val tags: List<String>,
    val artists: List<String>,
    val characters: List<String>,
    val pages: Int,
    val pagesId: Int,
    val imageType: ImageType
)

// https://t3.nhentai.net/galleries/{pagesId}/{page}t.jpg
// https://i1.nhentai.net/galleries/{pagesId}/{page}.jpg

// https://t2.nhentai.net/galleries/{pagesId}/{page}t.webp
// https://i4.nhentai.net/galleries/{pagesId}/{page}.webp
