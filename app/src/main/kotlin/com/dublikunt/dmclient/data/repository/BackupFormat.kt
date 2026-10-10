package com.dublikunt.dmclient.data.repository

import com.dublikunt.dmclient.data.db.entity.GalleryEntity
import com.dublikunt.dmclient.data.db.entity.HistoryEntity
import com.dublikunt.dmclient.data.db.entity.LibraryEntity
import com.dublikunt.dmclient.data.db.entity.StatusEntity
import com.dublikunt.dmclient.data.settings.BackupSettings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale

@Serializable
internal data class BackupData(
    val version: Int = 2,
    val galleries: List<GalleryEntity> = emptyList(),
    val library: List<LibraryEntity> = emptyList(),
    val statuses: List<StatusEntity> = emptyList(),
    val history: List<HistoryEntity> = emptyList(),
    val settings: BackupSettings? = null,
)

@Serializable
private data class V1Backup(
    val history: List<V1History> = emptyList(),
    val galleryStatuses: List<V1Mark> = emptyList(),
    val customStatuses: List<V1Status> = emptyList(),
)
@Serializable private data class V1History(val id: Int, val coverUrl: String, val name: String, val timestamp: Long)
@Serializable private data class V1Mark(val id: Int, val statusId: Int?, val favorite: Boolean)
@Serializable private data class V1Status(val id: Int, val name: String, val color: Int)

internal object BackupFormat {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun parse(text: String, now: Long = System.currentTimeMillis()): BackupData {
        val root = json.parseToJsonElement(text).jsonObject
        val version = root["version"]?.jsonPrimitive?.intOrNull ?: 1
        return when (version) {
            2 -> json.decodeFromString<BackupData>(text)
            1 -> {
                require(root.containsKey("history") && root.containsKey("galleryStatuses") && root.containsKey("customStatuses")) { "Invalid v1 backup" }
                val old = json.decodeFromString<V1Backup>(text)
                val galleries = old.history.associate { it.id to GalleryEntity(it.id, it.name, it.coverUrl, 0, it.timestamp) }.toMutableMap()
                old.galleryStatuses.forEach { galleries.putIfAbsent(it.id, GalleryEntity(it.id, "Gallery #${it.id}", "", 0, now)) }
                BackupData(
                    galleries = galleries.values.toList(),
                    library = old.galleryStatuses.filter { it.statusId != null || it.favorite }.map { LibraryEntity(it.id, it.statusId, it.favorite, now) },
                    statuses = old.customStatuses.map { StatusEntity(it.id, it.name, it.color, it.id) },
                    history = old.history.map { HistoryEntity(it.id, 1, it.timestamp) },
                )
            }
            else -> throw IllegalArgumentException("Unsupported backup version: $version")
        }
    }
}

internal data class StatusMergePlan(val additions: List<StatusEntity>, val remapping: Map<Int, Int>)

internal fun mergeStatusNames(incoming: List<StatusEntity>, existing: List<StatusEntity>): StatusMergePlan {
    val names = existing.associate { it.name.lowercase(Locale.ROOT) to it.id }.toMutableMap()
    var nextId = (existing.maxOfOrNull { it.id } ?: 0) + 1
    var position = (existing.maxOfOrNull { it.position } ?: 0) + 1
    val additions = mutableListOf<StatusEntity>()
    val mapping = mutableMapOf<Int, Int>()
    incoming.sortedBy { it.position }.forEach { status ->
        require(status.name.isNotBlank()) { "Status name is empty" }
        val key = status.name.trim().lowercase(Locale.ROOT)
        val id = names[key] ?: nextId++.also { id ->
            names[key] = id
            additions.add(status.copy(id = id, name = status.name.trim(), position = position++))
        }
        mapping[status.id] = id
    }
    return StatusMergePlan(additions, mapping)
}

internal fun remapLibrary(rows: List<LibraryEntity>, mapping: Map<Int, Int>): List<LibraryEntity> =
    rows.map { it.copy(statusId = it.statusId?.let(mapping::get)) }.filter { it.statusId != null || it.favorite }
