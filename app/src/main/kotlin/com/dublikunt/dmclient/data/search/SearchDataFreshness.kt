package com.dublikunt.dmclient.data.search

private const val MAX_AGE_MILLIS = 7 * 24 * 60 * 60 * 1000L

internal fun shouldRefreshSearchData(count: Int, updatedAt: Long?, now: Long): Boolean =
    count == 0 || updatedAt == null || updatedAt < now - MAX_AGE_MILLIS
