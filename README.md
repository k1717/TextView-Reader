# TextView Reader

TextView Reader is a local-first Android reader app for TXT, PDF, EPUB, and OOXML Word files. The app label is **TextView** and the public repository is **TextView-Reader**.

The project focuses on comfortable offline reading, fast local file browsing, recent-file recovery, bookmarks, Korean/Unicode-friendly text decoding, and a clean reader UI.

## Current version

- Version: **2.0.2**
- Android `versionName`: `2.0.2`
- Android `versionCode`: `202`
- Minimum Android version: Android 7.0 / API 24
- Target SDK: 35

## What changed from 2.0.1 to 2.0.2

### Functional changes

- Large TXT preview-only threshold increased from **20 MB** to **32 MB**.
- Folder shortcuts can now be added by long-pressing a folder in the file browser.
- Added folder shortcuts appear in the left drawer below the built-in storage shortcuts.
- Added folder shortcuts can be removed by long-pressing the shortcut in the drawer or by long-pressing the folder again and choosing remove shortcut.
- PDF reading now includes a mode toggle for horizontal page-slide mode versus vertical continuous reading mode.
- TXT page indicator alignment can now be configured as left, center, right, or hidden.
- The page-indicator row keeps its reserved space even when hidden, preventing the TXT page content from jumping upward.
- Main file search and type-filter behavior were refined so type-only filters do not show an unnecessary clear button and empty recent-page filtering does not trigger a broad storage scan.
- Drawer navigation is more responsive because expensive folder navigation is queued until after the drawer closes.
- Viewer lifecycle handling was tightened through shared viewer registration, reducing repeated stacked viewer instances when moving between TXT, PDF, EPUB, and Word viewers.

### Documentation and package changes

- README, changelog, privacy notes, build notes, contribution notes, and GitHub upload instructions were refreshed for the 2.0.2 source package.
- The source zip excludes local/private/generated files such as Android Studio workspace state, Gradle caches, build outputs, APKs, local SDK paths, signing files, and secret files.

## Quick UI map

| Area | What it does |
|---|---|
| Home / Recently Read | Shows files ordered by most recently read first. |
| Search field | Filters files by name. |
| File-type chips | Filter by All, General, PDF, EPUB, or Word. |
| Small sort icon beside search | Changes recent-page or folder-page sorting. |
| Left drawer | Opens Recent, Internal Storage, External Storage, Downloads, user folder shortcuts, and recent folders. |
| Folder long press | Adds or removes a drawer shortcut for that folder. |
| Drawer shortcut long press | Removes a user-added folder shortcut. |
| Drawer bottom actions | Opens file picker, bookmarks, and settings. |
| File long press | Opens the actions menu with open, shortcut, info, rename, and delete actions depending on file type. |
| TXT reader bottom bar | Page movement, search, bookmarks, and reader options. |
| PDF bottom bar | Page movement, slide/continuous-mode toggle, page jump, bookmarks, and more options. |
| Document bottom bar | Page movement, page jump, bookmarks, and viewer options for EPUB/Word. |
| Settings | Reading themes, language, page indicator alignment, behavior, backup/import, and lock options. |

## Features

### TXT reader

- Custom rendered scrolling reader.
- Tap-zone page movement.
- Hardware volume-key paging.
- Text search with next/previous match movement.
- Page, percentage, and line-position jump.
- Auto-resume reading position.
- Large-text fast-open path.
- Preview-only fallback now starts at **32 MB** instead of **20 MB**.
- Disposable page/index cache bookkeeping for large files.
- Page indicator alignment: left, center, right, or hidden.

### PDF reader

- Android `PdfRenderer`-based page rendering.
- Horizontal page-slide mode.
- Vertical continuous reading mode.
- Mode toggle in the PDF bottom control bar.
- Page navigation and jump controls.
- Zoom controls.
- Bookmark and recent-position restore.

### Document viewer

- EPUB and OOXML Word support.
- Supported Word extensions: `.docx`, `.docm`, `.dotx`, `.dotm`.
- Page-style WebView rendering.
- Per-page vertical scrolling.
- Left/right swipe page movement.
- Selectable text.
- Cleanup of stale native text-selection handles after scrolling.

### File browser and drawer

- Home page shows **Recently Read** files.
- Recent-file page keeps a separate recently-read ordering preference.
- Folder browser supports sorting by name, date, size, and type.
- File search supports All, General, PDF, EPUB, and Word filters.
- Left drawer shortcuts include Recent, Internal Storage, External Storage when available, Downloads, user-added folder shortcuts, and recent folders.
- Add a folder shortcut by long-pressing a folder in the browser.
- Remove a folder shortcut by long-pressing the shortcut in the drawer or long-pressing that folder again.
- File actions include open, shortcut add/remove for folders, file information, rename, delete, and new folder.
- Android Storage Access Framework support for files opened from other apps.

### Encoding support

TextView Reader tries multiple common encodings before falling back safely.

Supported candidates include:

- UTF-8;
- UTF-16LE / UTF-16BE;
- MS949 / Windows-949 / CP949;
- EUC-KR;
- Shift_JIS / Windows-31J;
- EUC-JP;
- ISO-2022-JP;
- GB18030 / GBK;
- Big5.

Invalid or unmappable bytes are replaced safely instead of crashing the reader.

### Bookmarks and reading state

- Auto-resume reading position per file.
- Bookmarks with labels and excerpts.
- Bookmark groups by file and by file type.
- Bookmark import/export using readable JSON.
- Path-prefix replacement support for moved storage paths.

### Appearance and controls

- Built-in reading themes.
- Custom theme editor.
- Light/dark app appearance with follow-system option.
- Per-app brightness override.
- Optional status bar visibility in the reader.
- Configurable TXT page indicator alignment.
- Keep-screen-on option.
- Optional reading progress notification.
- Optional PIN lock.

## Project structure

```text
app/src/main/java/com/simpletext/reader/
├── MainActivity.java             # File browser, drawer, search, sort, shortcuts, file actions
├── ReaderActivity.java           # TXT reader, large-file handling, themes, search, bookmarks
├── PdfReaderActivity.java        # PDF reader, zoom, page modes, bookmarks
├── DocumentPageActivity.java     # EPUB/Word WebView-based document reader
├── BookmarkListActivity.java     # All-bookmarks screen
├── SettingsActivity.java         # Settings, language, behavior, page indicator alignment
├── LockActivity.java             # PIN lock screen
├── ThemeEditorActivity.java      # Custom theme editor
├── ViewerRegistry.java           # Shared viewer lifecycle coordination
├── adapter/                      # RecyclerView adapters
├── model/                        # Bookmark, drawer entry, reader state, theme models
├── util/                         # Preferences, bookmarks, file utils, fonts, themes, cache manager
└── view/                         # Custom TXT reader view
```

## Build

1. Open the project root folder in Android Studio.
2. Let Gradle sync.
3. Install SDK Platform 35 if prompted.
4. Build with Android Studio or command line.

Command line:

```bash
./gradlew assembleDebug
```

Windows:

```bat
.\gradlew.bat assembleDebug
```

## Requirements

- Android Studio with JDK 17.
- Android Gradle Plugin 8.13.1.
- `compileSdk 35`.
- `targetSdk 35`.
- `minSdk 24`.
- Java source/target compatibility 17.

## Privacy model

TextView Reader is designed as an offline local reader. It does not include analytics, ads, accounts, cloud sync, or remote telemetry. Reading state, bookmarks, recent files, folder shortcuts, settings, and disposable cache metadata are stored locally on the device unless the user manually exports or shares them.

## Repository hygiene

Do not commit local or generated files:

```text
.gradle/
.idea/
build/
app/build/
local.properties
*.apk
*.aab
*.apks
*.jks
*.keystore
*.pem
*.p12
.env
.env.*
secrets.properties
google-services.json
captures/
*.hprof
*.log
```

Android source should stay under `app/src/main/`. Do not upload duplicate root-level Android folders such as `java/`, `res/`, or root `AndroidManifest.xml`.

## License

MIT License. See `LICENSE`.
