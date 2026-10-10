package com.dublikunt.dmclient.searchexport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SearchExportPolicyTest {
    @Test
    fun `retry after uses valid seconds and caps at five minutes`() {
        assertEquals(12L, SearchExportPolicy.retryAfterSeconds("12"))
        assertEquals(300L, SearchExportPolicy.retryAfterSeconds("900"))
    }

    @Test
    fun `retry after defaults for missing invalid or nonpositive values`() {
        assertEquals(60L, SearchExportPolicy.retryAfterSeconds(null))
        assertEquals(60L, SearchExportPolicy.retryAfterSeconds("invalid"))
        assertEquals(60L, SearchExportPolicy.retryAfterSeconds("0"))
        assertEquals(60L, SearchExportPolicy.retryAfterSeconds("-4"))
    }

    @Test
    fun `completeness allows at least 95 percent`() {
        SearchExportPolicy.requireComplete("tag", fetchedCount = 95, reportedTotal = 100)
        SearchExportPolicy.requireComplete("tag", fetchedCount = 100, reportedTotal = 100)
    }

    @Test
    fun `completeness rejects fewer than 95 percent`() {
        assertThrows(IllegalArgumentException::class.java) {
            SearchExportPolicy.requireComplete("tag", fetchedCount = 94, reportedTotal = 100)
        }
    }
}
