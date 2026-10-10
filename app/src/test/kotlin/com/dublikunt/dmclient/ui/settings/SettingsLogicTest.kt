package com.dublikunt.dmclient.ui.settings

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsLogicTest {
    @Test
    fun pinRequiresFourToTwelveAsciiDigitsAndMatchingConfirmation() {
        listOf("", "123", "1234567890123", "123a", "１２３４", " 1234").forEach {
            assertNotNull(validatePinForm(it))
        }
        assertNull(validatePinForm("0000", "0000"))
        assertNull(validatePinForm("123456789012", "123456789012"))
        assertNotNull(validatePinForm("1234", "4321"))
        assertNull(validatePinForm("1234"))
    }

    @Test
    fun backupNameUsesUnambiguousIsoDate() {
        assertEquals("dmclient-backup-2026-01-02.json", backupFileName(LocalDate.of(2026, 1, 2)))
        assertEquals("dmclient-backup-2024-02-29.json", backupFileName(LocalDate.of(2024, 2, 29)))
    }

    @Test
    fun storageFractionsHandleEmptyAndSingleCategories() {
        assertEquals(StorageFractions(0f, 0f), storageFractions(0, 0))
        assertEquals(StorageFractions(1f, 0f), storageFractions(100, 0))
        assertEquals(StorageFractions(0f, 1f), storageFractions(0, 100))
        assertEquals(StorageFractions(0f, 1f), storageFractions(-10, 100))
    }

    @Test
    fun storageFractionsRemainProportionalWithoutLongOverflow() {
        val split = storageFractions(300, 100)
        assertEquals(0.75f, split.downloads, 0.0001f)
        assertEquals(0.25f, split.imageCache, 0.0001f)
        assertEquals(StorageFractions(0.5f, 0.5f), storageFractions(Long.MAX_VALUE, Long.MAX_VALUE))
    }
}
