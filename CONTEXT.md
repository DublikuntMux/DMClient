# DMClient — Domain Model

Android client for nhentai.net: browse, search, read, download, and archive Galleries; track
reading History and Custom statuses.

## Glossary

- **Gallery** — a single work from nhentai.net. Comes in two shapes:
  `GallerySimpleInfo` (id, thumb, name — grid cards) and `GalleryFullInfo` (tags, artists,
  characters, parodies, page count, media/pages id, per-page image types — detail screen,
  downloads, worker payloads).
- **Session / clearance** — the Cloudflare-cleared cookie state (`cf_clearance`, `csrftoken`,
  `session-affinity`) that lets requests through. Owned end-to-end by the Session module.
- **Downloaded gallery** — a Gallery whose cover + pages live under `filesDir/galleries/<id>/`,
  recorded in the `downloaded_galleries` Room table by relative path.
- **Status** — per-gallery user metadata: favorite flag plus an optional reference to a
  **Custom status** (named, colored category like "Reading").
- **History** — which galleries were opened, most recent first.
- **Tag catalog** — the site-wide lists of tags/artists/characters/parodies cached in the
  `search_cache` table to power search filtering.

## Deep modules

Small interfaces hiding the churn-heavy implementation:

- **NhentaiParser** (`scrapper/NhentaiParser.kt`) — pure String→Gallery parsing of the site's
  SvelteKit HTML/JSON. Where every website-shape change lands; fixture-tested.
- **NHentaiApi** (`scrapper/NHentaiApi.kt`) — transport only: endpoints, header fingerprint,
  retry/backoff (`withRetries`), emits auth-required signal on 403.
- **NhentaiSession** (`auth/`) — owns everything cookie-shaped: names, persistence
  (`SessionTokenStorage`), jar fan-out, adopt/wipe lifecycle, and the Active/NeedsChallenge
  status machine reacting to 403s app-scoped.
- **GalleryContentLocator / DownloadedGalleryStore** (`download/`) — pure path/URL math for
  pages & covers; filesystem+DAO lifecycle for downloaded galleries (relative paths in DB).
- **DownloadController** (`download/`) — the whole WorkManager surface for downloads/archives:
  start/archive/cancel/observe/delete. Payload files and work-name schemes are internal.
- **RemotePagingSource** (`paging/`) — one page-number-keyed PagingSource parameterized by a
  load lambda.
- **GalleryStatusBook** (`status/`) — batched, deduped loading of per-grid-status views;
  UI sees `Map<Id, GalleryStatusView>`, never the Room relation.

## Decisions

- Repositories are gone: pass-through forwarders failed the deletion test. Callers inject
  DAOs, the API, or the deep modules above directly — one tier convention.
- Unit tests are JVM-only (no Robolectric): tests live at the seams above, never at
  composables/workers.
