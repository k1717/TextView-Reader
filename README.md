# TextView Reader

TextView Reader is a local-first Android reader app for TXT, PDF, EPUB, and OOXML Word files. The app label is **TextView** and the public repository is **TextView-Reader**.

The project focuses on comfortable offline reading, fast local file browsing, recent-file recovery, bookmarks, Korean/Unicode-friendly text decoding, and a clean reader UI.

## Current update highlights

- Version remains **2.0.1**.
- Initial language now follows the Android system language until the user explicitly chooses English or Korean in settings.
- Home opens to **Recently Read** files.
- Recent files default to recently-read order, not alphabetical or numeric order.
- The sort control is now a small icon beside the file search field.
- Sorting works on both the Recently Read page and normal folder browsing.
- Folder browsing supports sorting by name, date, size, and type.
- The left drawer keeps storage shortcuts fixed while only the recent-folder list scrolls.
- Viewer activities use `singleTop` / `onNewIntent` behavior so opening a new file reuses the matching viewer instead of creating repeated viewer stacks.
- TXT large-file cache bookkeeping was added for disposable page/index cache cleanup. It uses access count and last-access time, but does **not** delete bookmarks, reading history, or saved reading position.
- TXT, PDF, EPUB, and document viewers include additional lifecycle cleanup for callbacks, background work, bitmaps/renderers, WebViews, and selection state.
- DOCX/Word rendering stays WebView-based to preserve document layout and selectable text behavior.
- Dialogs, file actions, and document/PDF controls were polished to better match the app theme.
- Public repository documentation and upload instructions were cleaned up.

## Quick UI map

| Area | What it does |
|---|---|
| Home / Recently Read | Shows files ordered by most recently read first. |
| Search field | Filters files by name. |
| File-type chips | Filter by All, General, PDF, EPUB, or Word. |
| Small sort icon beside search | Changes recent-page or folder-page sorting. |
| Left drawer | Opens Recent, Internal Storage, External Storage, Downloads, `/storage`, and recent folders. |
| Drawer bottom actions | Opens file picker, bookmarks, and settings. |
| File long press | Opens the actions menu with info, rename, and delete. |
| TXT reader bottom bar | Page movement, search, bookmarks, and reader options. |
| PDF / document bottom bar | Page movement, page jump, bookmark, and viewer options. |
| Settings | Reading themes, language, behavior, backup/import, and lock options. |

## Features

### Reader modes

- **TXT reader**
  - Custom rendered scrolling.
  - Tap-zone page movement.
  - Hardware volume-key paging.
  - Text search with next/previous match movement.
  - Page, percentage, and line-position jump.
  - Auto-resume reading position.
  - Large-text fast-open path with safe disposable cache bookkeeping.

- **PDF reader**
  - Android `PdfRenderer`-based page rendering.
  - Page navigation.
  - Zoom controls.
  - Bookmark and recent-position restore.

- **Document viewer**
  - EPUB and OOXML Word support.
  - Supported Word extensions: `.docx`, `.docm`, `.dotx`, `.dotm`.
  - Page-style WebView rendering.
  - Pinch-page vertical scrolling.
  - Left/right swipe page movement.
  - Selectable text.
  - Cleanup of stale native text-selection handles after scrolling.

### File browser

- Home page shows **Recently Read** files.
- Recent-file page keeps a separate recently-read ordering preference.
- Folder browser supports sorting by:
  - name A to Z / Z to A;
  - date newest / oldest;
  - size largest / smallest;
  - file type.
- File search supports these filters:
  - All;
  - General;
  - PDF;
  - EPUB;
  - Word.
- Left drawer shortcuts:
  - Recent;
  - Internal Storage;
  - External Storage when available;
  - Downloads;
  - `/storage`.
- Recent-folder drawer section keeps up to 10 recent folder entries.
- File actions include open, file information, rename, delete, and new folder.
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

- Built-in reading themes:
  - Light;
  - Dark;
  - Sepia;
  - Night Blue;
  - Eye Care;
  - Cream.
- Custom theme editor with RGB color selection.
- Custom font import and device font scanning.
- App dark mode:
  - Follow System;
  - Light;
  - Dark.
- Reader brightness override.
- Optional keep-screen-on behavior.
- Optional reading-progress notification.
- Edge-to-edge safe toolbar and bottom-bar insets for newer Android targets.
- Custom rounded dialogs/menus and reader/document selection-handle styling.

## Supported file types

| Type | Extensions / MIME examples | Reader |
|---|---|---|
| Plain text | `.txt`, `.text/plain`, `text/*` | TXT reader |
| PDF | `.pdf`, `application/pdf` | PDF reader |
| EPUB | `.epub`, `application/epub+zip` | Document viewer |
| Word OOXML | `.docx`, `.docm`, `.dotx`, `.dotm` | Document viewer |

## Requirements

- Android 7.0 / API 24+
- Android Studio with JDK 17
- Android Gradle Plugin 8.13.1
- Gradle wrapper included in the repository
- Compile SDK 35
- Target SDK 35
- Language: Java

## Build from source

1. Clone or download the repository.
2. Open the repository root folder in Android Studio.
   - Correct: the folder containing `settings.gradle`, `build.gradle`, `gradlew`, and `app/`.
   - Wrong: opening only the `app/` folder.
3. Let Android Studio sync Gradle.
4. Install SDK Platform 35 if Android Studio asks for it.
5. Build with **Build > Make Project** or generate an APK with **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

Command-line build:

```bash
./gradlew assembleDebug
```

Windows:

```bat
.\gradlew.bat assembleDebug
```

Debug APK output after building locally:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Do not commit generated APKs to GitHub.

## Project structure

```text
app/src/main/java/com/simpletext/reader/
├── MainActivity.java              # Home recent files, folder browser, drawer, search, sort, file actions
├── ReaderActivity.java            # TXT reader, themes, bookmarks, search, position jump
├── PdfReaderActivity.java         # PDF page viewer
├── DocumentPageActivity.java      # EPUB/Word document viewer
├── SettingsActivity.java          # App settings
├── BookmarkListActivity.java      # Bookmark list UI
├── LockActivity.java              # PIN lock screen
├── ThemeEditorActivity.java       # Custom theme editor
├── adapter/                       # RecyclerView adapters
├── model/                         # Data models
├── util/                          # Preferences, bookmarks, files, fonts, themes, cache helpers
├── view/                          # Custom reader view
└── widget/                        # WebView and selection helpers
```

Correct Android source/resource locations:

```text
app/src/main/AndroidManifest.xml
app/src/main/java/
app/src/main/res/
app/src/main/ic_launcher-playstore.png
```

The repository root should not contain duplicate Android source folders such as root-level `java/`, `res/`, `AndroidManifest.xml`, or `ic_launcher-playstore.png`.

## Privacy

TextView Reader is designed as an offline local reader.

- No analytics SDK.
- No advertising SDK.
- No account system.
- Reading history, bookmarks, settings, disposable cache metadata, and imported fonts stay local on the device unless the user manually exports, backs up, shares, or deletes them.

See `PRIVACY.md` for details.

## Repository hygiene

The repository should contain source files and public documentation only.

Do not commit:

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

See `.gitignore` and `GITHUB_UPLOAD_NOTES.md` before pushing or uploading a new package.

## License

MIT License. See `LICENSE`.
