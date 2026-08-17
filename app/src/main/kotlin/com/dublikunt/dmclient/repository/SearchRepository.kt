package com.dublikunt.dmclient.repository

import com.dublikunt.dmclient.scrapper.ContentLanguage
import com.dublikunt.dmclient.scrapper.GalleryFullInfo
import com.dublikunt.dmclient.scrapper.GallerySimpleInfo
import com.dublikunt.dmclient.scrapper.NHentaiApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepository @Inject constructor(
    private val nHentaiApi: NHentaiApi
) {
    suspend fun search(
        query: String,
        page: Int,
        language: ContentLanguage
    ): List<GallerySimpleInfo> =
        nHentaiApi.search(query, page, language)

    suspend fun getAllTags(): List<String> = nHentaiApi.getAllTags()
    suspend fun getAllArtists(): List<String> = nHentaiApi.getAllArtists()
    suspend fun getAllCharacters(): List<String> = nHentaiApi.getAllCharacters()
    suspend fun getAllParodies(): List<String> = nHentaiApi.getAllParodies()

    suspend fun fetchGallery(id: Int): GalleryFullInfo? = nHentaiApi.fetchGallery(id)
}
