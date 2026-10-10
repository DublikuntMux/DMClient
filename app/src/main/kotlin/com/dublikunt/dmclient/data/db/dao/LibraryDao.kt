package com.dublikunt.dmclient.data.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.dublikunt.dmclient.data.db.entity.GalleryEntity
import com.dublikunt.dmclient.data.db.entity.LibraryEntity
import com.dublikunt.dmclient.data.db.entity.StatusEntity
import kotlinx.coroutines.flow.Flow

data class LibraryRow(
    @Embedded val entry: LibraryEntity,
    @Relation(parentColumn = "galleryId", entityColumn = "id") val gallery: GalleryEntity,
    @Relation(parentColumn = "statusId", entityColumn = "id") val status: StatusEntity?,
)

@Dao
interface LibraryDao {
    @Transaction
    @Query("SELECT * FROM library ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<LibraryRow>>

    @Transaction
    @Query("SELECT library.* FROM library JOIN galleries ON galleries.id = library.galleryId WHERE (:favorites = 0 OR favorite = 1) AND (:statusId IS NULL OR library.statusId = :statusId) AND instr(lower(galleries.title), lower(:query)) > 0 ORDER BY library.updatedAt DESC")
    fun entries(favorites: Boolean, statusId: Int?, query: String): Flow<List<LibraryRow>>

    @Query("SELECT * FROM library WHERE galleryId = :id") suspend fun get(id: Int): LibraryEntity?
    @Query("SELECT * FROM library") suspend fun all(): List<LibraryEntity>
    @Upsert suspend fun upsert(entry: LibraryEntity)
    @Query("DELETE FROM library WHERE galleryId = :id") suspend fun delete(id: Int)
    @Query("DELETE FROM library WHERE statusId IS NULL AND favorite = 0") suspend fun prune()
    @Query("SELECT * FROM statuses ORDER BY position, id") fun statuses(): Flow<List<StatusEntity>>
    @Query("SELECT * FROM statuses ORDER BY position, id") suspend fun allStatuses(): List<StatusEntity>
    @Insert suspend fun insertStatus(status: StatusEntity): Long
    @Update suspend fun updateStatus(status: StatusEntity)
    @Query("UPDATE statuses SET position = :position WHERE id = :id") suspend fun position(id: Int, position: Int)
    @Query("DELETE FROM statuses WHERE id = :id") suspend fun deleteStatus(id: Int)
}
