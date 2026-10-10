package com.dublikunt.dmclient.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dublikunt.dmclient.network.GalleryDetail
import com.dublikunt.dmclient.network.ImageType
import com.dublikunt.dmclient.network.Tag
import com.dublikunt.dmclient.network.TagType
import kotlinx.serialization.json.Json

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS galleries (id INTEGER NOT NULL PRIMARY KEY, title TEXT NOT NULL, coverUrl TEXT NOT NULL, pageCount INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS statuses (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, color INTEGER NOT NULL, position INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS library (galleryId INTEGER NOT NULL PRIMARY KEY, statusId INTEGER, favorite INTEGER NOT NULL, updatedAt INTEGER NOT NULL, FOREIGN KEY(statusId) REFERENCES statuses(id) ON UPDATE NO ACTION ON DELETE SET NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_library_statusId ON library(statusId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_library_favorite ON library(favorite)")
        db.execSQL("CREATE TABLE IF NOT EXISTS history (galleryId INTEGER NOT NULL PRIMARY KEY, lastPage INTEGER NOT NULL, openedAt INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_history_openedAt ON history(openedAt DESC)")
        db.execSQL("CREATE TABLE IF NOT EXISTS downloads (galleryId INTEGER NOT NULL PRIMARY KEY, detailJson TEXT NOT NULL, coverPath TEXT NOT NULL, state TEXT NOT NULL, downloadedPages INTEGER NOT NULL, error TEXT, createdAt INTEGER NOT NULL, completedAt INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS search_entries (type TEXT NOT NULL, name TEXT NOT NULL, PRIMARY KEY(type, name))")

        db.execSQL("INSERT INTO statuses SELECT id, name, color, id FROM custom_status")
        db.execSQL("INSERT INTO galleries SELECT id, name, coverUrl, 0, timestamp FROM gallery_history")
        db.execSQL("INSERT INTO history SELECT id, 1, timestamp FROM gallery_history")
        db.query("SELECT * FROM downloaded_galleries").use { cursor ->
            fun text(column: String): String =
                cursor.getString(cursor.getColumnIndexOrThrow(column))

            fun int(column: String): Int = cursor.getInt(cursor.getColumnIndexOrThrow(column))
            while (cursor.moveToNext()) {
                val id = int("id")
                val time = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"))
                val coverPath = text("coverPath")
                val mediaId = int("pagesId")
                val coverUrl =
                    "https://t.nhentai.net/galleries/$mediaId/${coverPath.substringAfterLast('/')}"
                val tags = listOf(
                    "parodies" to TagType.Parody,
                    "tags" to TagType.Tag,
                    "artists" to TagType.Artist,
                    "characters" to TagType.Character
                )
                    .flatMap { (column, type) ->
                        Json.decodeFromString<List<String>>(text(column)).map { Tag(type, it) }
                    }
                val detail = GalleryDetail(
                    id, text("title"), null, coverUrl, mediaId, int("totalPages"),
                    Json.decodeFromString<List<ImageType>>(text("imageTypes")), tags, null, null
                )
                db.execSQL(
                    "INSERT OR REPLACE INTO galleries (id, title, coverUrl, pageCount, updatedAt) VALUES (?, ?, ?, ?, ?)",
                    arrayOf<Any?>(id, detail.title, coverUrl, detail.pageCount, time)
                )
                db.execSQL(
                    "INSERT INTO downloads VALUES (?, ?, ?, 'Completed', ?, NULL, ?, ?)",
                    arrayOf<Any?>(
                        id,
                        Json.encodeToString(detail),
                        coverPath,
                        detail.pageCount,
                        time,
                        time
                    )
                )
            }
        }
        val now = System.currentTimeMillis()
        db.query("SELECT * FROM gallery_status WHERE statusId IS NOT NULL OR favorite != 0")
            .use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getInt(cursor.getColumnIndexOrThrow("id"))
                    val statusColumn = cursor.getColumnIndexOrThrow("statusId")
                    val statusId =
                        if (cursor.isNull(statusColumn)) null else cursor.getInt(statusColumn)
                    val validStatus = statusId?.takeIf { status ->
                        db.query("SELECT id FROM statuses WHERE id = ?", arrayOf(status))
                            .use { it.moveToFirst() }
                    }
                    val favorite = cursor.getInt(cursor.getColumnIndexOrThrow("favorite"))
                    db.execSQL(
                        "INSERT OR IGNORE INTO galleries VALUES (?, ?, '', 0, ?)",
                        arrayOf<Any?>(id, "Gallery #$id", now)
                    )
                    if (validStatus != null || favorite != 0) db.execSQL(
                        "INSERT INTO library VALUES (?, ?, ?, ?)",
                        arrayOf<Any?>(id, validStatus, favorite, now)
                    )
                }
            }
        db.query("SELECT type, names FROM search_cache").use { cursor ->
            while (cursor.moveToNext()) {
                val type = when (cursor.getString(0)) {
                    "tags" -> "tag"
                    "artists" -> "artist"
                    "characters" -> "character"
                    "parodies" -> "parody"
                    else -> cursor.getString(0)
                }
                Json.decodeFromString<List<String>>(cursor.getString(1)).forEach { name ->
                    db.execSQL(
                        "INSERT OR IGNORE INTO search_entries VALUES (?, ?)",
                        arrayOf(type, name)
                    )
                }
            }
        }
        listOf(
            "gallery_history",
            "gallery_status",
            "custom_status",
            "downloaded_galleries",
            "search_cache"
        )
            .forEach { db.execSQL("DROP TABLE $it") }
    }
}

internal fun seedDefaultStatuses(db: SupportSQLiteDatabase) {
    db.execSQL("INSERT OR IGNORE INTO statuses VALUES (1, 'Reading', ${0xFF1E88E5.toInt()}, 1)")
    db.execSQL("INSERT OR IGNORE INTO statuses VALUES (2, 'Read', ${0xFF43A047.toInt()}, 2)")
}
