package com.dublikunt.dmclient.repository

import com.dublikunt.dmclient.scrapper.ContentLanguage
import com.dublikunt.dmclient.scrapper.GallerySimpleInfo
import com.dublikunt.dmclient.scrapper.NHentaiApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeRepository @Inject constructor(
    private val nHentaiApi: NHentaiApi
) {
    suspend fun fetchMainPage(page: Int, language: ContentLanguage): List<GallerySimpleInfo> =
        nHentaiApi.fetchMainPage(page, language)
}
