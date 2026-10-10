package com.dublikunt.dmclient.ui.gallery

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal fun formatUploadDate(
    epochSeconds: Long,
    nowSeconds: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault()
): String {
    val elapsed = (nowSeconds - epochSeconds).coerceAtLeast(0)
    return when {
        elapsed < 60 -> "Just now"
        elapsed < 3_600 -> "${elapsed / 60} min ago"
        elapsed < 86_400 -> "${elapsed / 3_600} h ago"
        elapsed < 7 * 86_400 -> "${elapsed / 86_400} d ago"
        else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
            .format(Instant.ofEpochSecond(epochSeconds).atZone(zone))
    }
}
