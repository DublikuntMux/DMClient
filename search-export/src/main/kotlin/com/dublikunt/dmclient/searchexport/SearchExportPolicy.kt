package com.dublikunt.dmclient.searchexport

internal object SearchExportPolicy {
    private const val DEFAULT_RETRY_AFTER_SECONDS = 60L
    private const val MAX_RETRY_AFTER_SECONDS = 300L

    fun retryAfterSeconds(header: String?): Long =
        header?.trim()?.toLongOrNull()
            ?.takeIf { it > 0 }
            ?.coerceAtMost(MAX_RETRY_AFTER_SECONDS)
            ?: DEFAULT_RETRY_AFTER_SECONDS

    fun requireComplete(type: String, fetchedCount: Int, reportedTotal: Int) {
        require(fetchedCount.toLong() * 100 >= reportedTotal.toLong() * 95) {
            "Incomplete $type data: fetched $fetchedCount of $reportedTotal " +
                "(${if (reportedTotal == 0) 100 else fetchedCount * 100 / reportedTotal}%; need at least 95%)"
        }
    }
}
