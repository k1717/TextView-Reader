# TextView Reader

TextView Reader is a local Android reader for TXT, PDF, EPUB, and Word documents. It is designed around fast opening, simple navigation, bookmarks, theme control, custom fonts, and a file-browser workflow inspired by TekView.

Current version: **2.0.4**

## Quick UI map

### Main screen

- **Recent files** are shown first.
- **File search** filters the visible file list.
- **Sort button** opens sort options for recent files and folder browsing.
- **Left drawer** contains fixed storage shortcuts, user-added folder shortcuts, recent folders, and bottom actions.
- **Long-press a folder** in the file browser to add or remove it as a drawer shortcut.
- **Long-press a user-added shortcut** in the drawer to remove it.

### TXT reader

- Tap the reader area to toggle the overlay controls.
- Use **Find** for text search.
- Use **Go to Position** for percent/line jumps.
- Use **More > Font** to choose built-in, system-scanned, or imported fonts.
- Page indicator alignment can be set to left, center, right, or hidden.

### PDF reader

- Supports horizontal page-slide mode and vertical continuous mode.
- Pinch zoom is supported.
- In vertical continuous mode, zoomed pages can be horizontally panned.
- PDF popups use the same compact dialog width style as the TXT reader.
- Bookmarks remain wider so long bookmark/file information is easier to read.
- Returning from Settings/theme editing refreshes the PDF More popup theme instead of keeping stale colors.

### EPUB / Word reader

- Uses a page-style WebView reader.
- The bottom control bar includes **Previous**, **Next**, **Find**, **Go to Page**, **Bookmarks**, and **More**.
- **Find** searches within the current document and wraps across pages.
- **More > Font** uses the same structure as the TXT font selector.
- EPUB and Word files can use **Default font** first when the file declares its own font.
- Double-tap resets the document view to the default/original size.
- Zoom controls were removed from the Word/EPUB **More** menu because they duplicated gesture behavior.

## Features

### File browsing and navigation

- Recent-file-first home screen.
- Folder browsing with sorting by name, date, size, and type.
- Compact sort dialog with theme-matched selection indicators.
- Fixed drawer storage shortcuts for common locations.
- User-added folder shortcuts.
- Recent folders separated from user shortcuts.
- Rename, delete, new-folder, and file-info actions.
- Hidden-file toggle.
- Android Storage Access Framework support for files opened from other apps.

### TXT reading

- Encoding detection for UTF-8, EUC-KR/CP949/MS949, and UTF-16.
- Large TXT handling with generated page/index cache bookkeeping.
- Huge TXT preview-only threshold increased to **32 MB**.
- Generated TXT cache cleanup uses retention logic for disposable pagination/index data.
- Cache cleanup does not delete bookmarks, history, reading position, folder shortcuts, or documents.
- Text search with custom reader-dialog input styling.
- Custom reader themes and brightness control.
- Custom font import and system font scanning.
- Volume-key page movement.
- Auto-resume reading position.

### PDF reading

- Android `PdfRenderer` based reading path.
- Horizontal page-slide mode.
- Vertical continuous mode.
- Pinch zoom.
- Horizontal panning for zoomed pages in vertical continuous mode.
- Faster horizontal pan response for zoomed vertical pages.
- Improved continuous-mode blank-page recovery and render rebinding.
- More stable popup/dialog sizing and positioning.
- PDF slide-mode label refreshes while the More dialog is open.
- PDF More dialog refreshes theme colors after returning from Settings or theme editing.
- Bookmarks and file info.

### EPUB and Word reading

- EPUB and OOXML Word support through extracted/rendered WebView pages.
- Bottom-bar Find button next to Next.
- Search popup uses reader-style input, custom cursor/handle styling, match counter, Previous/Next, and wrap behavior.
- Font selector follows the TXT reader font-dialog structure.
- EPUB file-declared fonts can be used as **Default font**.
- Word file-declared fonts can be used as **Default font** when detected from DOCX style/font metadata.
- Imported/system fonts are served to WebView through the internal local font route instead of direct file access.
- Double-tap resets to the default/original size.
- Zoomed edge-swipe page turning requires a deliberate edge gesture instead of accidentally turning during the same pan.
- Word/EPUB popup windows match the compact TXT reader width, except bookmark dialogs.

### Bookmarks

- JSON-based bookmark storage.
- Custom bookmark labels and excerpts.
- Grouped bookmark list by file.
- Bookmark folders default to collapsed on the main bookmark page.
- Rounded, bordered bookmark edit dialogs with **Cancel**, **Clear memo**, and **Save** actions.
- Export/import support.
- Reading-position persistence per file.
- Bookmark cleanup and cache cleanup are separate.

### Themes, fonts, and appearance

- Built-in reading themes.
- Custom theme editor.
- Light/dark/follow-system behavior.
- Font selection shared across TXT, EPUB, and Word where applicable.
- EPUB/Word can preserve file-declared default fonts.
- Dialogs and popup windows are theme-matched and sized consistently across viewers.
- Viewer popups reload the current theme after returning from Settings/theme editing.

### Privacy

TextView Reader is intended as an offline local reader.

- No analytics SDK.
- No advertising SDK.
- No account login.
- No cloud sync backend.
- No remote telemetry collection.
- Public source packages exclude local IDE/build caches, generated APKs, signing files, and machine-specific configuration such as `local.properties`.

See [`PRIVACY.md`](PRIVACY.md) for details.

## Build

1. Open the repository root folder in Android Studio.
2. Let Gradle sync.
3. Install Android SDK Platform 35 if prompted.
4. Build or run the app.

Command-line build:

```bash
./gradlew assembleDebug
```

## Requirements

- Android Studio
- JDK 17
- Min SDK: 24
- Target SDK: 35
- Compile SDK: 35
- Language: Java
- Android Gradle Plugin: 8.13.1
- Gradle wrapper: 9.0.0

Main dependencies:

- AndroidX AppCompat
- Material Components
- RecyclerView
- ConstraintLayout
- Activity
- DrawerLayout

## Project structure

```text
app/src/main/java/com/simpletext/reader/
├── MainActivity.java              # File browser, drawer, sort, shortcuts, file actions
├── ReaderActivity.java            # TXT reader
├── PdfReaderActivity.java         # PDF reader
├── DocumentPageActivity.java      # EPUB/Word reader
├── BookmarkListActivity.java      # Bookmark manager screen
├── SettingsActivity.java          # Settings and backup/import
├── ThemeEditorActivity.java       # Custom theme editor
├── ViewerRegistry.java            # Single-viewer reuse support
├── adapter/
├── model/
├── util/
└── widget/
```

Correct Android project locations:

```text
app/src/main/java/
app/src/main/res/
app/src/main/AndroidManifest.xml
```

Do not upload duplicate root-level Android folders such as `java/`, `res/`, or root `AndroidManifest.xml`.

## Release notes

See [`CHANGELOG.md`](CHANGELOG.md). For GitHub release text, see [`PATCHNOTES.md`](PATCHNOTES.md).
