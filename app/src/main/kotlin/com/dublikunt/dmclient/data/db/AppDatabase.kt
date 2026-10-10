package com.dublikunt.dmclient.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.dublikunt.dmclient.data.db.dao.DownloadDao
import com.dublikunt.dmclient.data.db.dao.GalleryDao
import com.dublikunt.dmclient.data.db.dao.HistoryDao
import com.dublikunt.dmclient.data.db.dao.LibraryDao
import com.dublikunt.dmclient.data.db.dao.SearchEntryDao
import com.dublikunt.dmclient.data.db.entity.DownloadEntity
import com.dublikunt.dmclient.data.db.entity.GalleryEntity
import com.dublikunt.dmclient.data.db.entity.HistoryEntity
import com.dublikunt.dmclient.data.db.entity.LibraryEntity
import com.dublikunt.dmclient.data.db.entity.SearchEntryEntity
import com.dublikunt.dmclient.data.db.entity.StatusEntity

@Database(
    entities = [GalleryEntity::class, LibraryEntity::class, StatusEntity::class, HistoryEntity::class, DownloadEntity::class, SearchEntryEntity::class],
    version = 9,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun galleries(): GalleryDao
    abstract fun library(): LibraryDao
    abstract fun history(): HistoryDao
    abstract fun downloads(): DownloadDao
    abstract fun searchEntries(): SearchEntryDao
}
