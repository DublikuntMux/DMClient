package com.dublikunt.dmclient.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.dublikunt.dmclient.database.download.DownloadedGallery
import com.dublikunt.dmclient.database.download.DownloadedGalleryDao
import com.dublikunt.dmclient.database.history.GalleryHistory
import com.dublikunt.dmclient.database.history.GalleryHistoryDao
import com.dublikunt.dmclient.database.status.GalleryStatus
import com.dublikunt.dmclient.database.status.GalleryStatusDao

@Database(
    entities = [GalleryHistory::class, GalleryStatus::class, DownloadedGallery::class],
    version = 3
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun galleryHistoryDao(): GalleryHistoryDao
    abstract fun galleryStatusDao(): GalleryStatusDao
    abstract fun downloadedGalleryDao(): DownloadedGalleryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "main_database"
                )
                    .fallbackToDestructiveMigration(false)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
