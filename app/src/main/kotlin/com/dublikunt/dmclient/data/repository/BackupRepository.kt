package com.dublikunt.dmclient.data.repository

import androidx.room.withTransaction
import com.dublikunt.dmclient.data.db.AppDatabase
import com.dublikunt.dmclient.data.db.entity.GalleryEntity
import com.dublikunt.dmclient.data.settings.BackupSettings
import com.dublikunt.dmclient.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepository @Inject constructor(private val db: AppDatabase, private val settings: SettingsRepository) {
    /** Writes a v2 backup with library/history and appearance/browsing/reader settings; caller closes output. */
    suspend fun export(out: OutputStream) = withContext(Dispatchers.IO) {
        val prefs = BackupSettings(settings.read())
        val snapshot = db.withTransaction { BackupData(
            galleries = db.galleries().all(), library = db.library().all(), statuses = db.library().allStatuses(),
            history = db.history().all(), settings = prefs,
        ) }
        out.write(BackupFormat.json.encodeToString(snapshot).toByteArray(Charsets.UTF_8))
        out.flush()
    }

    /** Merges v1/v2 backup rows, matching status names case-insensitively; caller closes input. */
    suspend fun import(input: InputStream): ImportSummary = withContext(Dispatchers.IO) {
        val backup = BackupFormat.parse(input.bufferedReader().readText())
        val summary = db.withTransaction {
            val plan = mergeStatusNames(backup.statuses, db.library().allStatuses())
            plan.additions.forEach { db.library().insertStatus(it) }
            val marks = remapLibrary(backup.library, plan.remapping)
            val galleries = backup.galleries.associateBy { it.id }.toMutableMap()
            (marks.map { it.galleryId } + backup.history.map { it.galleryId }).forEach { id ->
                if (id !in galleries && db.galleries().get(id) == null) galleries[id] = GalleryEntity(id, "Gallery #$id", "", 0, System.currentTimeMillis())
            }
            db.galleries().upsert(galleries.values.toList())
            marks.forEach { db.library().upsert(it) }
            backup.history.forEach { db.history().upsert(it) }
            ImportSummary(galleries.size, marks.size, plan.additions.size, backup.history.size)
        }
        backup.settings?.let { settings.restore(it) }
        summary
    }
}
