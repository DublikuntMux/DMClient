# Data layer handoff

All repository classes and AppLockManager are Hilt-injected singletons. Repository methods below
are public; omitted return types are Unit. Domain models live in `com.dublikunt.dmclient.network`;
repository models live in `com.dublikunt.dmclient.data.repository`. Settings and lock types live
in their respective packages. UI code should use repositories rather than DAOs.

## Public repository APIs

```kotlin
class GalleryRepository {
    fun latest(language: ContentLanguage): Flow<PagingData<GallerySummary>>
    fun search(request: SearchRequest): Flow<PagingData<GallerySummary>>
    suspend fun detail(id: Int): GalleryDetail
    fun pageImage(detail: GalleryDetail, page: Int, downloaded: Boolean): Any
    fun pageThumbnail(detail: GalleryDetail, page: Int, downloaded: Boolean): Any
}

class LibraryRepository {
    val marks: StateFlow<Map<Int, GalleryMark>>
    val statuses: Flow<List<ReadingStatus>>
    fun entries(filter: LibraryFilter, query: String): Flow<List<LibraryItem>>
    suspend fun setFavorite(gallery: GallerySummary, favorite: Boolean)
    suspend fun setStatus(gallery: GallerySummary, statusId: Int?)
    suspend fun createStatus(name: String, color: Int): Int
    suspend fun updateStatus(status: ReadingStatus)
    suspend fun deleteStatus(id: Int)
    suspend fun reorderStatuses(ids: List<Int>)
}

class HistoryRepository {
    fun history(query: String): Flow<PagingData<HistoryItem>>
    fun lastPage(id: Int): Flow<Int?>
    suspend fun saveProgress(id: Int, page: Int)
    suspend fun remove(id: Int)
    suspend fun clear()
}

class DownloadRepository {
    val downloads: Flow<List<DownloadItem>>
    fun observe(id: Int): Flow<DownloadItem?>
    suspend fun enqueue(detail: GalleryDetail)
    suspend fun retry(id: Int)
    suspend fun cancel(id: Int)
    suspend fun delete(id: Int)
    suspend fun deleteAll()
    fun exportArchive(id: Int): Flow<ArchiveState>
    suspend fun isDownloaded(id: Int): Boolean
}

class SearchDataRepository {
    val status: Flow<SearchDataStatus>
    suspend fun ensureSeeded()
    fun refresh()
    suspend fun importBundle(stream: InputStream): Int
    suspend fun clear()
    suspend fun suggest(query: String, limit: Int = 50): List<Tag>
}

class BackupRepository {
    suspend fun export(out: OutputStream)
    suspend fun import(input: InputStream): ImportSummary
}

class StorageRepository {
    suspend fun usage(): StorageUsage
    suspend fun clearImageCache()
}

class UpdateRepository {
    suspend fun latestRelease(): ReleaseInfo?
    fun isNewer(current: String, remote: String): Boolean
    suspend fun downloadApk(release: ReleaseInfo, onProgress: (Float) -> Unit): File
}

class SettingsRepository {
    val settings: StateFlow<AppSettings>
    suspend fun setLanguage(value: ContentLanguage)
    suspend fun setThemeMode(value: ThemeMode)
    suspend fun setDynamicColor(value: Boolean)
    suspend fun setPureBlack(value: Boolean)
    suspend fun setGridDensity(value: GridDensity)
    suspend fun setReaderMode(value: ReaderMode)
    suspend fun setKeepScreenOn(value: Boolean)
    suspend fun setRecordHistory(value: Boolean)
    suspend fun setSecureScreen(value: Boolean)
    suspend fun setLockTimeout(value: LockTimeout)
    suspend fun setCheckUpdates(value: Boolean)
    suspend fun setImageCacheSize(value: Long)
    suspend fun setSearchDataUpdatedAt(value: Long?)
    companion object {
        const val DEFAULT_IMAGE_CACHE_SIZE: Long = 1073741824
        val IMAGE_CACHE_SIZE_OPTIONS: List<Long>
    }
}

class AppLockManager : DefaultLifecycleObserver {
    val state: StateFlow<LockState>
    val isPinSet: Flow<Boolean>
    fun verify(pin: String): Boolean
    fun unlockWithBiometric()
    fun onAppBackground()
    fun onAppForeground()
    suspend fun setPin(pin: String)
    suspend fun removePin()
    override fun onStart(owner: LifecycleOwner)
    override fun onStop(owner: LifecycleOwner)
}
```

## UI-facing data classes

```kotlin
data class GallerySummary(val id: Int, val title: String, val coverUrl: String)
data class Tag(val type: TagType, val name: String, val count: Int = 0)
data class GalleryDetail(
    val id: Int, val title: String, val subtitle: String?, val coverUrl: String,
    val mediaId: Int, val pageCount: Int, val pageTypes: List<ImageType>,
    val tags: List<Tag>, val uploadDate: Long?, val favorites: Int?,
)
data class SearchRequest(
    val text: String = "", val include: List<Tag> = emptyList(),
    val exclude: List<Tag> = emptyList(), val language: ContentLanguage = ContentLanguage.All,
    val sort: SortOrder = SortOrder.Recent,
)
data class PageResult<T>(val items: List<T>, val totalPages: Int?)
data class ReadingStatus(val id: Int, val name: String, val color: Int)
data class GalleryMark(val favorite: Boolean, val status: ReadingStatus?)
data class LibraryItem(val gallery: GallerySummary, val mark: GalleryMark, val updatedAt: Long)
data class HistoryItem(val gallery: GallerySummary, val lastPage: Int, val pageCount: Int, val openedAt: Long)
sealed interface LibraryFilter {
    data object Favorites : LibraryFilter
    data class Status(val id: Int) : LibraryFilter
    data object AllTracked : LibraryFilter
}
enum class DownloadState { Queued, Downloading, Completed, Failed }
data class DownloadItem(
    val gallery: GallerySummary, val coverFile: File, val state: DownloadState,
    val downloadedPages: Int, val pageCount: Int, val error: String?, val createdAt: Long,
)
sealed interface ArchiveState {
    data object Queued : ArchiveState
    data object Running : ArchiveState
    data class Completed(val uri: String) : ArchiveState
    data class Failed(val error: String) : ArchiveState
}
data class SearchDataStatus(
    val counts: Map<TagType, Int>, val lastUpdated: Long?, val refreshing: Boolean, val failed: Boolean,
)
data class ImportSummary(val galleries: Int, val library: Int, val statuses: Int, val history: Int)
data class StorageUsage(
    val imageCacheBytes: Long, val downloadsBytes: Long, val downloadsCount: Int,
    val historyCount: Int, val searchEntries: Int,
)
sealed interface LockState {
    data object Loading : LockState
    data class Locked(val failedAttempts: Int = 0, val cooldownUntil: Long? = null) : LockState
    data object Unlocked : LockState
}
```

`ReleaseInfo(name, htmlUrl, body, tagName, assets)` and `ReleaseAsset(name, downloadUrl)` are in
`network`; use `tagName` for version comparison. `AppSettings` has every field and default in the
spec. GridDensity exposes its minimum cell size as an integer `minCell`; UI converts it to dp.

Pages are one-based. Image methods return File for downloaded content and String URL for remote
content. DownloadItem.coverFile is the internal cover path; gallery.coverUrl uses that local path
when present and the remote URL while the cover is pending. Archive collection enqueues work and
ends after Completed or Failed. Android 9 (API 28) archive export needs the declared
WRITE_EXTERNAL_STORAGE runtime permission, which the UI must request before export. Android 10+
uses MediaStore Downloads with pending-row cleanup.

LockState.Loading blocks entry until credentials are read. isPinSet emits after that read.
cooldownUntil uses SystemClock.elapsedRealtime milliseconds; five failures cause a 30-second
cooldown. New PINs require 4–12 ASCII digits. Legacy PINs retain their original accepted value
during hashing. Process lifecycle observation is registered by DMClientApplication.

Backup and bundle streams remain owned by the caller. Backups never include PIN credentials or
download files. v1/v2 backups merge statuses by case-insensitive name, preserving existing status
colors and remapping library references. ImportSummary.statuses counts newly created statuses.

## Files and database

New packages/files:

- `network/`: Models, ApiException, AppCookieJar, FetchRetry, SearchUrls, NhentaiParser, NHentaiApi,
  GitHubReleaseApi.
- `data/db/`: AppDatabase, Converters, Migrations, HistoricalMigrations;
  `entity/Entities`; `dao/` GalleryDao, LibraryDao, HistoryDao, DownloadDao, SearchEntryDao.
- `data/settings/`: AppSettings, BackupSettings, SettingsRepository.
- `data/lock/`: AppLockManager, PinSecurity.
- `data/repository/`: Models, GalleryRepository, LibraryRepository, HistoryRepository,
  DownloadRepository, SearchDataRepository, BackupRepository, BackupFormat, StorageRepository,
  UpdateRepository, RemotePagingSource.
- `data/download/`: GalleryContentLocator, DownloadFiles.
- `data/search/`: SearchDataBundle, SearchBundleImporter, SearchDataStore.
- `data/work/`: DownloadWorker, ArchiveWorker, SearchDataWorker, Notifications.
- `di/DatabaseModule` and `di/NetworkModule` replaced; existing PreferenceModule keeps the original
  `user_prefs` DataStore. DMClientApplication configures shared Coil/OkHttp and startup recovery.
  MainActivity is the requested minimal placeholder.
- JVM tests in `network/`, `data/download/`, `data/search/`, `data/repository/`, and `data/lock/`;
  existing crash tests unchanged.

Room v9 tables: galleries (metadata), library (favorite/status marks), statuses (ordered ARGB
labels), history (reading page/open timestamp), downloads (detail JSON/state/progress),
search_entries (type/name composite key). library has statusId/favorite indexes and a statusId
foreign key with ON DELETE SET NULL. history has a descending openedAt index. Reading/Read are
seeded on creation. The 8→9 migration converts all old rows and cached JSON arrays, constructs
completed-download detail JSON, creates placeholder metadata for unknown library galleries,
and drops the five old tables. Room executes migrations transactionally. Existing 3→8 migration
steps remain to preserve older upgrade paths. fallbackToDestructiveMigration(false) remains.

Generated schema: `app/schemas/com.dublikunt.dmclient.data.db.AppDatabase/9.json`.

## Decisions and verification

The prescribed contracts are implemented. Where unspecified: models are top-level, archive
export uses Flow<ArchiveState>, new PIN length is 4–12 digits, and cache size choices remain
128 MiB through 4 GiB. Search refresh fetches all four types before replacing them in one Room
transaction, avoiding partially refreshed suggestions on crawl failure. This is the only timing
change from the spec's per-type worker updates. Cancellation and storage updates share locks
to prevent deleted content from being recreated. Existing CDN hosts are retained.

Verification:

- `sh ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --console=plain -q`: passed;
  45 JVM tests, zero failures/errors.
- `sh ./gradlew :app:lintDebug --console=plain -q`: passed; zero errors, 11 warnings, one hint.
- Migration CREATE statements match Room's generated v9 table/index/foreign-key definitions
  in an in-memory SQLite check; ON DELETE SET NULL verified.
- No instrumentation or device tests were added. No files under the other worktree were modified.
