package com.dublikunt.dmclient.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dublikunt.dmclient.database.download.DownloadedGallery
import com.dublikunt.dmclient.database.download.DownloadedGalleryDao
import com.dublikunt.dmclient.database.history.GalleryHistory
import com.dublikunt.dmclient.database.history.GalleryHistoryDao
import com.dublikunt.dmclient.database.status.CustomStatus
import com.dublikunt.dmclient.database.status.GalleryStatus
import com.dublikunt.dmclient.database.status.GalleryStatusDao

@Database(
    entities = [GalleryHistory::class, GalleryStatus::class, CustomStatus::class, DownloadedGallery::class],
    version = 4
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
                    .addMigrations(MIGRATION_3_4)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            seedDefaultStatuses(db)
                        }
                    })
                    .fallbackToDestructiveMigration(false)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private fun seedDefaultStatuses(db: SupportSQLiteDatabase) {
            db.execSQL("INSERT OR IGNORE INTO custom_status (id, name, color) VALUES (1, 'Reading', 4278255360)")
            db.execSQL("INSERT OR IGNORE INTO custom_status (id, name, color) VALUES (2, 'Read', 4278190335)")
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS custom_status (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        color INTEGER NOT NULL
                    )
                """.trimIndent())

                seedDefaultStatuses(db)

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS gallery_status_new (
                        id INTEGER PRIMARY KEY NOT NULL,
                        statusId INTEGER,
                        favorite INTEGER NOT NULL
                    )
                """.trimIndent())

                db.execSQL("""
                    INSERT INTO gallery_status_new (id, statusId, favorite)
                    SELECT
                        id,
                        CASE status
                            WHEN 'Reading' THEN 1
                            WHEN 'Read' THEN 2
                            ELSE NULL
                        END,
                        favorite
                    FROM gallery_status
                """.trimIndent())

                db.execSQL("DROP TABLE gallery_status")
                db.execSQL("ALTER TABLE gallery_status_new RENAME TO gallery_status")
            }
        }
    }
}
