package com.dublikunt.dmclient.ui.library

import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimeTest {
    private val now = 100_000_000_000L

    @Test
    fun clampsFutureTimesAndHandlesMinuteBoundary() {
        assertEquals("Just now", relativeTime(now + 60_000, now))
        assertEquals("Just now", relativeTime(now - 59_999, now))
        assertEquals("1 minute ago", relativeTime(now - 60_000, now))
        assertEquals("2 minutes ago", relativeTime(now - 120_000, now))
    }

    @Test
    fun formatsLargerUnitsWithSingularAndPluralLabels() {
        val cases =
            listOf(
                3_600L to "1 hour ago",
                7_200L to "2 hours ago",
                86_400L to "1 day ago",
                604_800L to "1 week ago",
                1_209_600L to "2 weeks ago",
                2_592_000L to "1 month ago",
                31_536_000L to "1 year ago",
                63_072_000L to "2 years ago",
            )
        cases.forEach { (seconds, expected) ->
            assertEquals(expected, relativeTime(now - seconds * 1_000, now))
        }
    }
}
