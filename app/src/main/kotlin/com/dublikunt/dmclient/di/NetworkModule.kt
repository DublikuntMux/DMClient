package com.dublikunt.dmclient.di

import com.dublikunt.dmclient.scrapper.EasyCookieJar
import com.dublikunt.dmclient.scrapper.NHentaiApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideCookieJar(): EasyCookieJar = EasyCookieJar()

    @Provides
    @Singleton
    fun provideOkHttpClient(cookieJar: EasyCookieJar): OkHttpClient =
        OkHttpClient.Builder().cookieJar(cookieJar).build()

    @Provides
    @Singleton
    fun provideNHentaiApi(client: OkHttpClient, cookieJar: EasyCookieJar): NHentaiApi =
        NHentaiApi(client, cookieJar)
}
