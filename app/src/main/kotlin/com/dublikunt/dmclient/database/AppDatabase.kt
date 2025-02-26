package com.dublikunt.dmclient.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.dublikunt.dmclient.database.history.GalleryHistory
import com.dublikunt.dmclient.database.history.GalleryHistoryDao
import com.dublikunt.dmclient.database.status.GalleryStatus
import com.dublikunt.dmclient.database.status.GalleryStatusDao

@Database(
    entities = [GalleryHistory::class, GalleryStatus::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun galleryHistoryDao(): GalleryHistoryDao
    abstract fun galleryStatusDao(): GalleryStatusDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gallery_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
