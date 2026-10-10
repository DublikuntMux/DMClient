package com.dublikunt.dmclient.ui.library

fun relativeTime(timestamp: Long, now: Long): String {
    val seconds = (now - timestamp).coerceAtLeast(0) / 1_000
    val (amount, unit) =
        when {
            seconds < 60 -> return "Just now"
            seconds < 3_600 -> seconds / 60 to "minute"
            seconds < 86_400 -> seconds / 3_600 to "hour"
            seconds < 604_800 -> seconds / 86_400 to "day"
            seconds < 2_592_000 -> seconds / 604_800 to "week"
            seconds < 31_536_000 -> seconds / 2_592_000 to "month"
            else -> seconds / 31_536_000 to "year"
        }
    return "$amount $unit${if (amount == 1L) "" else "s"} ago"
}
