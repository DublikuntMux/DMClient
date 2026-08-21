package com.dublikunt.dmclient.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.dublikunt.dmclient.auth.NhentaiSession
import com.dublikunt.dmclient.auth.PrefsSessionTokenStorage
import com.dublikunt.dmclient.auth.SessionTokenStorage
import com.dublikunt.dmclient.scrapper.EasyCookieJar
import com.dublikunt.dmclient.scrapper.NHentaiApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object CoroutinesModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
}

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
    fun provideNHentaiApi(client: OkHttpClient): NHentaiApi = NHentaiApi(client)

    @Provides
    @Singleton
    fun provideSessionTokenStorage(dataStore: DataStore<Preferences>): SessionTokenStorage =
        PrefsSessionTokenStorage(dataStore)

    @Provides
    @Singleton
    fun provideNhentaiSession(
        cookieJar: EasyCookieJar,
        api: NHentaiApi,
        tokenStorage: SessionTokenStorage,
        @ApplicationScope scope: CoroutineScope
    ): NhentaiSession {
        val authRequired: Flow<Unit> = api.authRequired
        return NhentaiSession(cookieJar, authRequired, tokenStorage, scope)
    }
}
