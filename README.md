# DMClient

## Overview

DMClient is an Android client for nhentai.net with extra features for browsing, organizing, downloading, and reading galleries.

## Features

- Browse with a language filter and pull-to-refresh.
- Search with tag suggestions, include/exclude filters, sorting, and language selection.
- Library with favorites, custom reading statuses, and reading history with resume.
- Offline downloads with progress, retry, and ZIP export.
- Reader with left-to-right or right-to-left paging, vertical scrolling, and zoom.
- App lock with PIN or biometrics and a configurable lock timeout.
- Secure DNS (DNS-over-HTTPS) to work around ISP DNS blocking.
- Backup export and import.
- Material 3 Expressive design with dynamic color, pure black theme, and adaptive tablet navigation.

## Building

Requirements: JDK 21 or newer toolchain and Android SDK 37.

```sh
./gradlew assembleDebug
```

## Architecture

The app is organized into `network/`, `data/{db,repository,settings,lock,work,download,search}`, and `ui/{components,navigation,feature screens}`. It uses Hilt for dependency injection, Room v9 for persistence, Paging 3 for gallery lists, WorkManager for background jobs, and Coil 3 with the app's shared OkHttp client for images.

The `search-export` CLI fetches nhentai tag, artist, character, and parody suggestions into a JSON bundle that the app can import. The app downloads `search-data/search-data.json` from this repository, and a weekly GitHub Actions workflow regenerates it. You can regenerate the file manually with `./gradlew :search-export:run --args="--out search-data/search-data.json --minify"`; a full run takes about 35 minutes due to API rate limits.

## Contributing

1. Fork the repository.
2. Create a new branch for your feature or bug fix.
3. Make your changes and commit them.
4. Submit a pull request.

## License

This project is licensed under the [BSD 3-Clause] - see the [LICENSE.md](LICENSE.md) file for details.
