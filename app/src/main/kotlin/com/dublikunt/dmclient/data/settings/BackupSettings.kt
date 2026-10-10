package com.dublikunt.dmclient.data.settings

import com.dublikunt.dmclient.network.ContentLanguage
import kotlinx.serialization.Serializable

@Serializable
data class BackupSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
    val pureBlack: Boolean = false,
    val language: ContentLanguage = ContentLanguage.All,
    val gridDensity: GridDensity = GridDensity.Comfortable,
    val readerMode: ReaderMode = ReaderMode.PagedLtr,
    val keepScreenOn: Boolean = true,
) {
    internal constructor(settings: AppSettings) : this(
        settings.themeMode, settings.dynamicColor, settings.pureBlack, settings.language,
        settings.gridDensity, settings.readerMode, settings.keepScreenOn,
    )
}
