# search-export

Standalone JVM CLI that fetches the same search suggestion data the Android app crawls per-device
(`SearchCacheWorker` → `NHentaiApi.getAllTags/Artists/Characters/Parodies`)
and saves it to a single JSON file.

The app can then seed its Room `search_cache` table from that file (see `SearchBundleImporter` in
`:app`) instead of refetching on each device.

DNS over HTTPS uses Cloudflare by default, matching the Android client. Use
`--secure-dns google` to select Google or `--secure-dns off` to use system DNS. Both HTTPS resolvers
use the same bootstrap IPs as the app, so resolving the DNS provider does not depend on system DNS.

## Build

```bash
./gradlew :search-export:build
```

## Run

```bash
./gradlew :search-export:run --args="--out search-data.json"
```

Options:

| Flag                    | Default            | Description                                    |
|-------------------------|--------------------|------------------------------------------------|
| `--out <path>`          | `search-data.json` | Output file                                    |
| `--types <csv>`         | all                | Subset of `tags,artists,characters,parodies`   |
| `--max-pages <n>`       | `1000`             | Page cap per type (mirrors the app)            |
| `--retries <n>`         | `4`                | Retries per request on 429/5xx                 |
| `--page-delay-ms <n>`   | `500`              | Delay between pages to avoid rate limiting     |
| `--secure-dns <name>`   | `cloudflare`       | DNS resolver: `cloudflare`, `google`, or `off` |
| `--minify` / `--pretty` | `--pretty`         | JSON formatting                                |
| `-h`, `--help`          |                    | Show help                                      |

Quick smoke test without network crawl:

```bash
./gradlew :search-export:run --args="--help"
./gradlew :search-export:run --args="--out /tmp/search-data.json --types tags --max-pages 1"
```

## Output format

```json
{
  "version": 1,
  "generatedAt": 1720000000000,
  "tags": ["...", "..."],
  "artists": ["..."],
  "characters": ["..."],
  "parodies": ["..."]
}
```

## Publishing for the app

1. Run the exporter (e.g. in CI on a schedule).
2. Host the resulting `search-data.json` on any static HTTPS host (GitHub release asset, Firebase
   Hosting, S3, etc.).
3. In the app, place a downloaded copy at `filesDir/search-data.json`, bundle a copy as the
   `search-data.json` asset, or import it from Settings → Import Search Data.
   `SearchViewModel.loadData()` prefers the downloaded file, then the bundled asset, and only falls
   back to the per-device `SearchCacheWorker` crawl when neither exists.
