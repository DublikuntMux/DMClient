package com.dublikunt.dmclient.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dublikunt.dmclient.di.ApplicationScope
import com.dublikunt.dmclient.network.ContentLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val store: DataStore<Preferences>,
    @ApplicationScope scope: CoroutineScope,
) {
    /** Current preferences, observed eagerly for the application's lifetime. */
    val settings: StateFlow<AppSettings> = store.data.map(::decode)
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    internal suspend fun read(): AppSettings = decode(store.data.first())

    /** Changes the language used for browsing and search. */
    suspend fun setLanguage(value: ContentLanguage) = set(language, value.name)
    /** Selects system, light, or dark appearance. */
    suspend fun setThemeMode(value: ThemeMode) = set(theme, value.name)
    /** Enables colors derived from the system wallpaper. */
    suspend fun setDynamicColor(value: Boolean) = set(dynamicColor, value)
    /** Enables black surfaces in dark appearance. */
    suspend fun setPureBlack(value: Boolean) = set(pureBlack, value)
    /** Changes the gallery grid's minimum cell size. */
    suspend fun setGridDensity(value: GridDensity) = set(grid, value.name)
    /** Changes the reader's paging direction or scrolling mode. */
    suspend fun setReaderMode(value: ReaderMode) = set(reader, value.name)
    /** Controls whether the reader keeps the display awake. */
    suspend fun setKeepScreenOn(value: Boolean) = set(keepScreenOn, value)
    /** Controls recording gallery opens and reading progress. */
    suspend fun setRecordHistory(value: Boolean) = set(recordHistory, value)
    /** Controls screen capture and recents privacy. */
    suspend fun setSecureScreen(value: Boolean) = set(secureScreen, value)
    /** Selects when returning from the background requires unlocking. */
    suspend fun setLockTimeout(value: LockTimeout) = set(lockTimeout, value.name)
    /** Selects the DNS-over-HTTPS resolver used for all app traffic; applies to new connections. */
    suspend fun setSecureDns(value: SecureDns) = set(secureDns, value.name)
    /** Controls automatic release checks. */
    suspend fun setCheckUpdates(value: Boolean) = set(checkUpdates, value)
    /** Selects the image disk cache limit, applied after restarting the app. */
    suspend fun setImageCacheSize(value: Long) {
        require(value in IMAGE_CACHE_SIZE_OPTIONS)
        set(cacheSize, value.toString())
    }
    /** Records a completed search-data refresh, or removes its timestamp. */
    suspend fun setSearchDataUpdatedAt(value: Long?) {
        store.edit { if (value == null) it.remove(searchUpdatedAt) else it[searchUpdatedAt] = value }
    }

    internal suspend fun restore(value: BackupSettings) {
        store.edit {
            it[language] = value.language.name
            it[theme] = value.themeMode.name
            it[dynamicColor] = value.dynamicColor
            it[pureBlack] = value.pureBlack
            it[grid] = value.gridDensity.name
            it[reader] = value.readerMode.name
            it[keepScreenOn] = value.keepScreenOn
        }
    }

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) { store.edit { it[key] = value } }

    private fun decode(prefs: Preferences): AppSettings = AppSettings(
        language = ContentLanguage.entries.firstOrNull {
            it.name == prefs[language] || it.key == prefs[language]
        } ?: ContentLanguage.All,
        themeMode = enum(prefs[theme], ThemeMode.System),
        dynamicColor = prefs[dynamicColor] ?: true,
        pureBlack = prefs[pureBlack] ?: false,
        gridDensity = enum(prefs[grid], GridDensity.Comfortable),
        readerMode = enum(prefs[reader], ReaderMode.PagedLtr),
        keepScreenOn = prefs[keepScreenOn] ?: true,
        recordHistory = prefs[recordHistory] ?: true,
        secureScreen = prefs[secureScreen] ?: false,
        lockTimeout = enum(prefs[lockTimeout], LockTimeout.OneMinute),
        checkUpdates = prefs[checkUpdates] ?: true,
        secureDns = enum(prefs[secureDns], SecureDns.Cloudflare),
        imageCacheSize = prefs[cacheSize]?.toLongOrNull()?.let { size ->
            IMAGE_CACHE_SIZE_OPTIONS.minBy { kotlin.math.abs(it - size) }
        } ?: DEFAULT_IMAGE_CACHE_SIZE,
        searchDataUpdatedAt = prefs[searchUpdatedAt],
    )

    private inline fun <reified T : Enum<T>> enum(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    companion object {
        /** Default disk cache capacity in bytes. */
        const val DEFAULT_IMAGE_CACHE_SIZE = 1024L * 1024 * 1024
        /** Supported image cache limits in bytes, applied after restarting. */
        val IMAGE_CACHE_SIZE_OPTIONS = listOf(128L, 256L, 512L, 1024L, 2048L, 4096L).map { it * 1024 * 1024 }
        private val language = stringPreferencesKey("preferred_language")
        private val theme = stringPreferencesKey("theme_mode")
        private val dynamicColor = booleanPreferencesKey("dynamic_color")
        private val pureBlack = booleanPreferencesKey("pure_black")
        private val grid = stringPreferencesKey("grid_density")
        private val reader = stringPreferencesKey("reader_mode")
        private val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        private val recordHistory = booleanPreferencesKey("record_history")
        private val secureScreen = booleanPreferencesKey("secure_screen")
        private val lockTimeout = stringPreferencesKey("lock_timeout")
        private val checkUpdates = booleanPreferencesKey("check_updates")
        private val secureDns = stringPreferencesKey("secure_dns")
        private val cacheSize = stringPreferencesKey("max_image_cache_size")
        private val searchUpdatedAt = longPreferencesKey("search_data_updated_at")
    }
}
