package com.dublikunt.dmclient.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val historicalMigrations = arrayOf(
    object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS custom_status (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, color INTEGER NOT NULL)")
            db.execSQL("INSERT OR IGNORE INTO custom_status VALUES (1, 'Reading', 4278255360)")
            db.execSQL("INSERT OR IGNORE INTO custom_status VALUES (2, 'Read', 4278190335)")
            db.execSQL("CREATE TABLE gallery_status_new (id INTEGER PRIMARY KEY NOT NULL, statusId INTEGER, favorite INTEGER NOT NULL)")
            db.execSQL("INSERT INTO gallery_status_new SELECT id, CASE status WHEN 'Reading' THEN 1 WHEN 'Read' THEN 2 ELSE NULL END, favorite FROM gallery_status")
            db.execSQL("DROP TABLE gallery_status")
            db.execSQL("ALTER TABLE gallery_status_new RENAME TO gallery_status")
        }
    },
    object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE downloaded_galleries ADD COLUMN parodies TEXT NOT NULL DEFAULT '[]'")
        }
    },
    object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS index_gallery_history_timestamp ON gallery_history(timestamp DESC)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_downloaded_galleries_timestamp ON downloaded_galleries(timestamp DESC)")
        }
    },
    object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS search_cache (type TEXT PRIMARY KEY NOT NULL, names TEXT NOT NULL DEFAULT '[]', lastUpdated INTEGER NOT NULL DEFAULT 0)")
        }
    },
    object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP INDEX IF EXISTS index_gallery_status_id")
            db.execSQL("DROP INDEX IF EXISTS index_gallery_status_favorite")
            db.execSQL("UPDATE downloaded_galleries SET coverPath = substr(coverPath, instr(coverPath, 'galleries')) WHERE instr(coverPath, 'galleries') > 1")
        }
    },
)
