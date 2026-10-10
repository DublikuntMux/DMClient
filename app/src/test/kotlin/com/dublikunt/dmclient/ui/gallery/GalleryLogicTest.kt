package com.dublikunt.dmclient.ui.gallery

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class GalleryLogicTest {
    private val now = Instant.parse("2026-10-10T12:00:00Z").epochSecond

    @Test
    fun recentUploadsUseRelativeTimeAtEachBoundary() {
        assertEquals("Just now", formatUploadDate(now - 59, now))
        assertEquals("1 min ago", formatUploadDate(now - 60, now))
        assertEquals("59 min ago", formatUploadDate(now - 3_599, now))
        assertEquals("1 h ago", formatUploadDate(now - 3_600, now))
        assertEquals("23 h ago", formatUploadDate(now - 86_399, now))
        assertEquals("1 d ago", formatUploadDate(now - 86_400, now))
        assertEquals("6 d ago", formatUploadDate(now - 7 * 86_400 + 1, now))
    }

    @Test
    fun olderUploadsUseMediumDateInRequestedTimeZone() {
        val date = Instant.parse("2026-10-01T23:30:00Z").epochSecond
        assertEquals("Oct 1, 2026", formatUploadDate(date, now, ZoneId.of("UTC"), Locale.US))
        assertEquals("Oct 2, 2026", formatUploadDate(date, now, ZoneId.of("Europe/Kyiv"), Locale.US))
        assertEquals("Oct 3, 2026", formatUploadDate(now - 7 * 86_400, now, ZoneId.of("UTC"), Locale.US))
    }

    @Test
    fun futureUploadDatesDoNotProduceNegativeElapsedTime() {
        assertEquals("Just now", formatUploadDate(now + 60, now))
    }
}
