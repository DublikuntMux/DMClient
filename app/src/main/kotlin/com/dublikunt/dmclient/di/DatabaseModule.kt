package com.dublikunt.dmclient.di

import android.content.Context
import com.dublikunt.dmclient.database.AppDatabase
import com.dublikunt.dmclient.database.download.DownloadedGalleryDao
import com.dublikunt.dmclient.database.history.GalleryHistoryDao
import com.dublikunt.dmclient.database.status.GalleryStatusDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.getDatabase(context)

    @Provides
    fun provideGalleryHistoryDao(db: AppDatabase): GalleryHistoryDao = db.galleryHistoryDao()

    @Provides
    fun provideGalleryStatusDao(db: AppDatabase): GalleryStatusDao = db.galleryStatusDao()

    @Provides
    fun provideDownloadedGalleryDao(db: AppDatabase): DownloadedGalleryDao =
        db.downloadedGalleryDao()
}
