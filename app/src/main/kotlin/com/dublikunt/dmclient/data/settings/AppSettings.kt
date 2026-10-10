package com.dublikunt.dmclient.data.settings

import com.dublikunt.dmclient.network.ContentLanguage
import kotlinx.serialization.Serializable

@Serializable enum class ThemeMode { System, Light, Dark }
@Serializable enum class GridDensity(val minCell: Int) { Compact(92), Comfortable(112), Large(160) }
@Serializable enum class ReaderMode { PagedLtr, PagedRtl, Vertical }
enum class SecureDns { Off, Cloudflare, Google }

enum class LockTimeout(val milliseconds: Long?) {
    Immediate(0), OneMinute(60_000), FiveMinutes(300_000), OnRestart(null)
}

data class AppSettings(
    val language: ContentLanguage = ContentLanguage.All,
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
    val pureBlack: Boolean = false,
    val gridDensity: GridDensity = GridDensity.Comfortable,
    val readerMode: ReaderMode = ReaderMode.PagedLtr,
    val keepScreenOn: Boolean = true,
    val recordHistory: Boolean = true,
    val secureScreen: Boolean = false,
    val lockTimeout: LockTimeout = LockTimeout.OneMinute,
    val checkUpdates: Boolean = true,
    val secureDns: SecureDns = SecureDns.Cloudflare,
    val imageCacheSize: Long = 1024L * 1024 * 1024,
    val searchDataUpdatedAt: Long? = null,
)
