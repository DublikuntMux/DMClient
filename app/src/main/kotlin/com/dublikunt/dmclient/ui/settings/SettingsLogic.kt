package com.dublikunt.dmclient.ui.settings

import java.time.LocalDate

fun validatePinForm(pin: String, confirmation: String? = null): String? =
    when {
        pin.length !in 4..12 || pin.any { it !in '0'..'9' } -> "Enter 4–12 digits."
        confirmation != null && confirmation != pin -> "PINs don't match."
        else -> null
    }

fun backupFileName(date: LocalDate): String = "dmclient-backup-$date.json"

data class StorageFractions(val downloads: Float, val imageCache: Float)

fun storageFractions(downloadsBytes: Long, imageCacheBytes: Long): StorageFractions {
    val downloads = downloadsBytes.coerceAtLeast(0).toDouble()
    val cache = imageCacheBytes.coerceAtLeast(0).toDouble()
    val total = downloads + cache
    return if (total == 0.0) StorageFractions(0f, 0f)
    else StorageFractions((downloads / total).toFloat(), (cache / total).toFloat())
}
