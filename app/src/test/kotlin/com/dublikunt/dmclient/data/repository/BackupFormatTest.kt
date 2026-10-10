package com.dublikunt.dmclient.data.repository

import com.dublikunt.dmclient.data.db.entity.LibraryEntity
import com.dublikunt.dmclient.data.db.entity.StatusEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupFormatTest {
    @Test fun `v1 preserves history marks statuses and creates missing metadata`() {
        val data = BackupFormat.parse("""{
            "history":[{"id":12,"coverUrl":"https://cover","name":"Title","timestamp":123}],
            "galleryStatuses":[{"id":12,"statusId":2,"favorite":true},{"id":15,"statusId":null,"favorite":true},{"id":16,"statusId":null,"favorite":false}],
            "customStatuses":[{"id":2,"name":"Read","color":-1}]
        }""", now = 456)
        assertEquals(2, data.version)
        assertEquals("Title", data.galleries.first { it.id == 12 }.title)
        assertEquals("Gallery #15", data.galleries.first { it.id == 15 }.title)
        assertEquals(1, data.history.single().lastPage)
        assertEquals(123L, data.history.single().openedAt)
        assertEquals(listOf(12, 15), data.library.map { it.galleryId })
        assertEquals(2, data.statuses.single().position)
        assertNull(data.settings)
    }

    @Test fun `v2 round trips all rows and settings`() {
        val text = """{"version":2,"galleries":[{"id":1,"title":"Gallery","coverUrl":"url","pageCount":9,"updatedAt":50}],
            "library":[{"galleryId":1,"statusId":4,"favorite":true,"updatedAt":50}],
            "statuses":[{"id":4,"name":"Reading","color":123,"position":0}],
            "history":[{"galleryId":1,"lastPage":3,"openedAt":60}],
            "settings":{"themeMode":"Dark","language":"English","gridDensity":"Compact","readerMode":"PagedRtl"},"unknown":0}"""
        val data = BackupFormat.parse(text)
        assertEquals(9, data.galleries.single().pageCount)
        assertEquals(3, data.history.single().lastPage)
        assertEquals("Dark", data.settings?.themeMode?.name)
        assertEquals(data, BackupFormat.parse(BackupFormat.json.encodeToString(data)))
    }

    @Test fun `status merge matches names and deduplicates imported names without replacing local colors`() {
        val local = listOf(StatusEntity(10, "Reading", 7, 2), StatusEntity(20, "Read", 8, 5))
        val imported = listOf(StatusEntity(1, "reading", 99, 0), StatusEntity(2, "Later", 3, 1), StatusEntity(3, "LATER", 4, 2))
        val plan = mergeStatusNames(imported, local)
        assertEquals(mapOf(1 to 10, 2 to 21, 3 to 21), plan.remapping)
        assertEquals(listOf(StatusEntity(21, "Later", 3, 6)), plan.additions)
        val rows = remapLibrary(listOf(LibraryEntity(1, 1, false, 0), LibraryEntity(2, 99, true, 0), LibraryEntity(3, 99, false, 0)), plan.remapping)
        assertEquals(10, rows[0].statusId)
        assertNull(rows[1].statusId)
        assertEquals(2, rows.size)
    }

    @Test fun `unsupported and unrelated files are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.parse("""{"version":99}""") }
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.parse("{}") }
    }
}
