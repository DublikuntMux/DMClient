package com.dublikunt.dmclient.data.search

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchDataFreshnessTest {
    private val week = 7 * 24 * 60 * 60 * 1000L
    private val now = 1720000000000L

    @Test fun `empty table refreshes even with a current timestamp`() {
        assertTrue(shouldRefreshSearchData(0, now, now))
    }

    @Test fun `missing timestamp refreshes even with entries`() {
        assertTrue(shouldRefreshSearchData(1, null, now))
    }

    @Test fun `data older than seven days refreshes`() {
        assertTrue(shouldRefreshSearchData(1, now - week - 1, now))
    }

    @Test fun `data up to seven days old remains current`() {
        assertFalse(shouldRefreshSearchData(1, now - week, now))
        assertFalse(shouldRefreshSearchData(1, now - week + 1, now))
        assertFalse(shouldRefreshSearchData(1, now, now))
    }

    @Test fun `future timestamp remains current`() {
        assertFalse(shouldRefreshSearchData(1, now + 1, now))
    }
}
