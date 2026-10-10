package com.dublikunt.dmclient.di

import com.dublikunt.dmclient.network.AppCookieJar
import com.dublikunt.dmclient.network.NHentaiApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object CoroutinesModule {
    @Provides @Singleton @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton
    fun client(jar: AppCookieJar): OkHttpClient = OkHttpClient.Builder()
        .cookieJar(jar)
        .addInterceptor { chain ->
            val request = chain.request()
            chain.proceed(
                if (request.header("User-Agent") != null) request
                else request.newBuilder().header("User-Agent", NHentaiApi.USER_AGENT).build()
            )
        }
        .build()
}
