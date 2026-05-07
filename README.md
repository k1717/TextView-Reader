# TextView Reader

TextView Reader is a local Android reader for TXT files and common document formats. It focuses on fast local browsing, comfortable reading controls, recent-file recovery, bookmarks, Korean/Unicode-friendly text decoding, and a clean reader UI.

The app label is **TextView**. The repository name is **TextView-Reader**.

## Current update highlights

- Recent files open on the home screen and default to **recently read first**, not alphabetical or numeric order.
- The sort control is a compact icon beside the file search field and works on both the home recent-files page and normal folder browsing.
- The left drawer keeps storage shortcuts fixed while recent folders remain scrollable.
- Viewer activities use `singleTop`/`onNewIntent` handling so opening another file reuses the matching viewer instead of stacking repeated viewer loops.
- Reader, PDF, and document viewers clean up callbacks, background workers, bitmaps/WebViews, and selection state during lifecycle teardown.

## Features

### Reader modes

- **TXT reader** with custom rendered scrolling, tap-zone page movement, hardware volume-key paging, search, page/position jump, and auto-resume.
- **PDF reader** using Android `PdfRenderer`, with page navigation, zoom controls, bookmarks, and recent-position restore.
- **Document viewer** for EPUB and OOXML Word files (`.docx`, `.docm`, `.dotx`, `.dotm`) using page-style WebView rendering.
- Word/document pages support per-page vertical scrolling, left/right swipe page movement, selectable text, and cleanup of stale native selection handles after scrolling.

### File browser

- Home page shows **Recently Read** files.
- Recent-files page defaults to **Recently read** ordering. Optional sort modes are available from the small sort icon beside file search.
- Folder browser supports sorting by name, date, size, and type.
- File search bar with type filters: All, General, PDF, EPUB, and Word.
- Left drawer shortcuts for Recent, internal storage, external storage when available, Downloads, and `/storage`.
- Recent-folder drawer section keeps up to 10 recent folder entries.
- File operations: rename, delete, new folder, and file information.
- Android Storage Access Framework support for opening files from other apps.

### Encoding support

TextView Reader tries multiple common encodings before falling back safely. Supported candidates include:

- UTF-8
- UTF-16LE / UTF-16BE
- MS949 / Windows-949 / CP949
- EUC-KR
- Shift_JIS / Windows-31J
- EUC-JP
- ISO-2022-JP
- GB18030 / GBK
- Big5

Invalid or unmappable bytes are replaced safely instead of crashing the reader.

### Bookmarks and reading state

- Auto-resume reading position per file.
- Bookmarks with labels and excerpts.
- Bookmark groups by file and by file type.
- Bookmark import/export using readable JSON.
- Path-prefix replacement support for moved storage paths.

### Appearance and controls

- Built-in reading themes: Light, Dark, Sepia, Night Blue, Eye Care, and Cream.
- Custom theme editor with RGB color selection.
- Custom font import and device font scanning.
- App dark mode: Follow System / Light / Dark.
- Reader brightness override.
- Edge-to-edge safe toolbar and bottom-bar insets for newer Android targets.
- Custom rounded dialogs/menus and reader/document selection handle styling.

### Privacy

- No analytics SDK.
- No advertising SDK.
- No account system.
- Reading history, bookmarks, settings, and imported fonts stay local on the device unless the user manually exports or shares files.

## Supported file types

| Type | Extensions / MIME examples | Reader |
| --- | --- | --- |
| Plain text | `.txt`, `text/plain`, `text/*` | TXT reader |
| PDF | `.pdf`, `application/pdf` | PDF reader |
| EPUB | `.epub`, `application/epub+zip` | Document viewer |
| Word OOXML | `.docx`, `.docm`, `.dotx`, `.dotm` | Document viewer |

## Requirements

- Android 7.0+ / API 24+
- Android Studio with JDK 17
- Android Gradle Plugin 8.13.1
- Gradle wrapper included in the repository
- Compile SDK: 35
- Target SDK: 35
- Language: Java

## Build from source

1. Clone or download the repository.
2. Open the repository root folder in Android Studio. Do not open only the `app/` folder.
3. Let Android Studio sync Gradle.
4. Install SDK Platform 35 if Android Studio asks for it.
5. Build with **Build > Make Project** or generate an APK with **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

Command-line build:

```bash
./gradlew assembleDebug
```

Windows:

```powershell
.\gradlew.bat assembleDebug
```

Debug APK output after building locally:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Do not commit that generated APK to GitHub.

## Project structure

```text
app/src/main/java/com/simpletext/reader/
├── MainActivity.java             # Home recent files, folder browser, drawer, search, sort, file actions
├── ReaderActivity.java           # TXT reader, themes, bookmarks, search, position jump
├── PdfReaderActivity.java        # PDF page viewer
├── DocumentPageActivity.java     # EPUB/Word document viewer
├── SettingsActivity.java         # App settings
├── BookmarkListActivity.java     # Bookmark list UI
├── LockActivity.java             # PIN lock screen
├── ThemeEditorActivity.java      # Custom theme editor
├── adapter/                      # RecyclerView adapters
├── model/                        # Data models
├── util/                         # Preferences, bookmarks, files, fonts, themes
├── view/                         # Custom reader view
└── widget/                       # WebView and selection helpers
```

## Repository hygiene

The repository should contain source files only. Do not commit local IDE files, Gradle caches, build outputs, APKs/AABs, signing keys, or machine-specific paths such as `local.properties`.

See `.gitignore` and `GITHUB_UPLOAD_NOTES.md` before pushing a new package.

## License

MIT License. See `LICENSE`.
