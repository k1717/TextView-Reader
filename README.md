# TextView Reader

TextView Reader is a local Android reader for TXT, PDF, EPUB, and Word documents. It is designed around fast opening, simple navigation, bookmarks, theme control, custom fonts, and a file-browser workflow inspired by TekView.

Current version: **2.1.2**

## What changed in 2.1.2 from 2.1.1

- Added **TXT Display Rules** for viewing-only text replacement or masking. Normal display rules change only the text shown in the TXT viewer; the source file is not modified.
- Rules can be enabled/disabled, case-sensitive or case-insensitive, plain-text or regular-expression based, global for all TXT files, or limited to one TXT file.
- Added TXT-viewer quick add flow: long-press a visible word or use **More > Add display rule** to create a rule without leaving the reader.
- Added rule-source labeling so saved rules can show which file they were originally created from.
- Added rule ordering controls so multiple replacement rules can be moved up/down and applied in top-to-bottom order. Up/down does not reload the active viewer by itself, but order can affect final output when rules overlap.
- Added quick enable/disable/delete controls and rounded delete-confirmation dialogs in the display-rule list.
- Reader-side display-rule changes are applied when the rule/add window closes, so the rule manager remains responsive while editing.
- Display rules are applied before TXT pagination and large-TXT exact indexing, so the page count, slider, Go to Page, search, and bookmarks follow the text shown on screen while rules are enabled.
- Added **Edit Actual TXT File** below **TXT Display Rules** in Settings when Settings is opened from a TXT viewer. It can permanently apply all enabled applicable rules to either the original TXT file or a copied `*_edited.txt` file.
- Actual-file edit uses rounded confirmation popups, yellow/red warning boxes, a second **Are you sure?** step, and an emphasized **There is no turning back.** warning.
- In copy mode, the app writes to the same `*_edited.txt` target and overwrites that edited copy instead of creating repeated numbered copies. In original mode, the opened TXT is reloaded and fully repaginated after the write succeeds. Physical writes now use a same-folder temporary file plus replacement step to reduce the chance of partially written output.
- Added low-power **Auto Page Turn** as a TXT toolbar button for e-ink/low-end devices: the reader turns one page after the user-specified number of seconds instead of continuously scrolling.
- Applied rounded popup styling to the new display-rule, actual-file edit, auto-page-turn, and settings-reset dialogs; auto-page-turn action buttons now use short Start/Stop labels.
- Settings backups include the saved display-rule JSON through the existing settings export/import path.
- Added **Reset settings** in Settings. It restores reader/app preferences to defaults while keeping bookmarks, reading positions, recent files, folder shortcuts, TXT display rules, custom themes, and PIN lock.
- Multi-line find/replace is intentionally left out for now to keep large-TXT partition boundaries predictable.
- Updated Android version metadata to `versionCode 212` and `versionName "2.1.2"`.

## What changed in 2.1.1 from 2.1.0

- Reworked large TXT active rendering around fixed **4,000-logical-line partitions** with lookahead, in-place partition switching, a bounded partition cache, and direction-aware neighbor prefetch.
- Added a background exact large-TXT page-anchor index so page labels, toolbar slider jumps, Go to Page, page turns, and bookmark jumps can resolve against real page anchors after indexing completes.
- Hardened large-TXT partition seams so page movement continues from the correct next-page anchor, respects the configured page-overlap setting, and avoids extra duplicated or skipped displayed content at 4,000-line boundaries.
- Stabilized large-TXT page status during fast forward/backward partition changes so temporary partition estimates do not make the displayed page or total page count jump backward.
- Made TXT pagination use a canonical status-bar-off content spacing model, so toggling Android status-bar visibility does not change TXT page counts. The page indicator is visually lowered by one reader text row.
- Fixed TXT toolbar slider and Go to Page behavior so the selected target page does not snap back to the old page while an uncached large-TXT partition is loading.
- Updated the TXT loading window to a compact rounded, theme-aware panel and reused it for uncached large-TXT slider, Go to Page, and bookmark jumps.
- Changed backup bookmark PC editing from the old repeated `beginnerEditableBookmarks` tutorial format to a cleaner `bookmarkEdits.beginner` / `bookmarkEdits.developer` structure, while keeping backward-compatible import for old backups.
- Added friendlier bilingual English/Korean backup-edit guidance for both beginner-safe edits and developer/internal recovery edits.
- Improved TXT bookmark loading by carrying anchor context into bookmark jumps, making large-TXT and layout-change restoration more stable.
- Made original-size PDF page swipes more sensitive while keeping zoomed PDF gestures conservative enough to pan first.
- Removed the PDF loading spinner from fast page-turn and zoom redraws, while keeping it for initial PDF loading.
- Changed zoomed PDF next/previous page turns to land centered instead of at the upper-left corner.
- Added EPUB page-direction and transition settings, including right-to-left **Japanese-style** reading and an option to disable slide animation.
- Added memory/lifecycle hardening for large TXT caches, pending prefetch state, exact page anchors, partition-switch generations, and background file-read context use.
- Added root `.gitignore` rules for GitHub submission and excluded local configuration, build outputs, signing files, secrets, logs, and exported backup JSON files from the source package.
- Updated Android version metadata to `versionCode 211` and `versionName "2.1.1"`.

## What changed in 2.1.0 from 2.0.9

- Changed the Android package/application ID to `com.textview.reader` and moved the Java source package to `com.textview.reader`.
- Added adaptive rounded-popup sizing for constrained app windows such as split-screen, pop-up view, foldable half-window, and small-window modes, while preserving normal full-screen dialog sizing.
- Centered popup/window headers and cleaned up rounded dialog styling across TXT/PDF/EPUB/Word viewers and Settings dialogs.
- Removed shaded/ripple option-box effects from the backup import and custom reading-theme option/delete dialogs, and made those Settings dialogs compact at about 70% screen width.
- Replaced in-app update checking with a static, copyable Settings line: `Check updates at https://github.com/k1717/TextView-Reader/releases`; the app no longer contacts GitHub.
- Changed the default TXT tap-zone layout for fresh installs to horizontal: left = previous page, center = menu, right = next page.
- Improved TXT bookmark save behavior so it anchors to the actual title-covered visual row and avoids off-by-one saves caused by row-boundary ambiguity.
- Added robust TXT bookmark restore using saved character position plus surrounding anchor text, so bookmarks stay tied to the same text passage after font, boundary, or spacing changes.
- Added portable bookmark identity metadata so imported bookmarks can rebind to the same file after the file is moved or restored on another device.
- Expanded JSON backup/export with beginner-friendly bookmark editing sections, bilingual tutorial markers, detailed examples, and timestamped filenames such as `textview_backup_year_month_day_hour_minute_second.json`.

> Package migration note: Android treats `com.textview.reader` as a different app from legacy package builds. Use the backup/export flow in the old app and import the backup in this build to migrate bookmarks, reading positions, settings, and custom themes.

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
- Use **Bookmarks** to add, move to, edit, or delete bookmarks.
- Use **More > Font** to choose built-in, system-scanned, or imported fonts.
- Long-press an added font in **Font** to remove it from the compact font list.
- Page indicator alignment can be set to left, center, right, or hidden.
- When the TXT control selector is open, the current file title appears under the top page indicator and hides again in full viewer mode.
- TXT pagination uses line-boundary anchors to reduce duplicate/skipped lines and keep first/last page rows aligned.
- Use **More > Add display rule** to create a TXT display rule from inside the reader. Long-pressing a visible word can prefill the find field.
- Use **More > TXT Display Rules** to manage display rules for viewing-only masking/replacement. Rule changes are applied to the viewer after the rule window closes.
- Use **Settings > Edit Actual TXT File** only when you want to permanently write enabled display-rule results into the current TXT file or a copied `*_edited.txt` file.

### PDF reader

- Supports horizontal page-slide mode and vertical continuous mode.
- Pinch zoom is supported and preserves the selected focal spot.
- In vertical continuous mode, zoomed pages can be horizontally panned.
- PDF popups use the same compact dialog width style as the TXT reader.
- Bookmarks remain wider so long bookmark/file information is easier to read.
- Single-tap the page area to fold or restore the top toolbar and bottom controls.
- The **More** popup includes **Open File** next to **Close** for returning to the file browser.
- Folded toolbar mode keeps 6dp extra safe-area padding around punch-hole/status-bar and navigation-bar regions.

### EPUB / Word reader

- Uses a page-style WebView reader.
- The bottom control bar includes **Previous**, **Next**, **Find**, **Go to Page**, **Bookmarks**, and **More**.
- **Find** searches within the current document and wraps across pages.
- **More > Font** uses the same structure as the TXT font selector.
- EPUB **More** includes **Increase Font**, **Decrease Font**, and **Reset Font Size** controls.
- EPUB boundary is controlled from **Settings > EPUB layout**, including separate left, right, top, and bottom boundaries from 0–240px.
- EPUB and Word files can use **Default font** first when the file declares its own font.
- Multiple user-added fonts can be kept in the compact font picker.
- Long-press an added font in **Font** to remove it from the compact font list.
- Double-tap resets the document view to the default/original size.
- Single-tap the page area to fold or restore the top toolbar and bottom controls.
- The **More** popup includes **Open File** next to **Close** for returning to the file browser.
- Folded toolbar mode keeps 6dp extra safe-area padding around punch-hole/status-bar and navigation-bar regions.
- Word keeps gesture-style viewing controls; EPUB uses font-size controls in **More** for reader text size.

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
- Large TXT handling with fixed 4,000-logical-line active partitions, lookahead rendering, neighbor prefetch, and generated page/index cache bookkeeping.
- Generated TXT cache cleanup uses retention logic for disposable pagination/index data.
- Cache cleanup does not delete bookmarks, history, reading position, folder shortcuts, or documents.
- Text search with custom reader-dialog input styling.
- Custom reader themes and brightness control.
- Custom font import, system font scanning, and multiple added-font persistence.
- User-added font removal from the normal compact font picker by long-press.
- Volume-key page movement.
- Auto-resume reading position.
- Compact rounded theme-matched loading panel instead of a hard black loading box; uncached large-TXT Go to Page, toolbar slider, and bookmark jumps use this loading panel.

### TXT display rules and actual-file editing

- TXT display rules can replace or mask text while viewing TXT files.
- Rules support enabled/disabled state, case-sensitive matching, regular-expression mode, all-file scope, and current-file-only scope.
- Rules can be created from Settings or directly from the TXT viewer.
- Rules created from a TXT file can show the source file where the rule was made.
- Multiple rules are applied in saved top-to-bottom order. This matters when one rule output can become another rule input.
- Moving rules up/down changes only the saved order and does not refresh the active TXT viewer by itself.
- Closing the TXT display-rule manager or add/edit window applies relevant active rule changes to the viewer and recalculates TXT pagination if the visible text changes.
- The normal display-rule layer is non-destructive: it changes the viewer output, not the original TXT file.
- **Edit Actual TXT File** is a separate destructive/manual action available from Settings when opened from a TXT viewer.
- Actual-file edit applies all enabled rules that currently apply to that TXT file, in top-to-bottom order.
- **Fix original file** overwrites the opened TXT file, reloads the viewer, clears stale page/index state, and recalculates the full page count.
- **Copy original and fix copy** writes to `originalname_edited.txt`; if that file already exists, the edited copy is overwritten. The original viewer is not reloaded in copy mode.
- Actual-file edit uses an extra confirmation step and warning boxes because the app does not provide an internal undo for overwritten file content. Large TXT files show an additional memory/time warning because the app must load the whole file before applying the full rule chain.

### PDF reading

- Android `PdfRenderer` based reading path.
- Horizontal page-slide mode.
- Original-size page swipes are more sensitive for easier page turning.
- Vertical continuous mode.
- Pinch zoom that keeps the selected focal spot stable.
- Zoomed next/previous page turns land centered instead of at the upper-left corner.
- Horizontal panning for zoomed pages in vertical continuous mode.
- Single-tap toolbar fold/return with folded-mode safe-area padding plus a 6dp extra buffer.
- Faster horizontal pan response for zoomed vertical pages.
- Improved continuous-mode blank-page recovery and render rebinding.
- More stable popup/dialog sizing and positioning.
- Page-turn and zoom redraws avoid showing the centered PDF spinner unless the file is initially loading.
- PDF slide-mode label refreshes while the More dialog is open.
- Bookmarks and file info.

### EPUB and Word reading

- EPUB and OOXML Word support through extracted/rendered WebView pages.
- Bottom-bar Find button next to Next.
- Search popup uses reader-style input, custom cursor/handle styling, match counter, Previous/Next, and wrap behavior.
- Font selector follows the TXT reader font-dialog structure.
- EPUB file-declared fonts can be used as **Default font**.
- EPUB boundary can be adjusted from **Settings > EPUB layout** for left, right, top, and bottom boundaries without changing Word/DOCX padding.
- EPUB page direction can be set for left-to-right Korean/Western books or right-to-left Japanese-style books.
- EPUB page transition can use slide animation or no transition effect.
- Word file-declared fonts can be used as **Default font** when detected from DOCX style/font metadata.
- Imported/system fonts are served to WebView through the internal local font route instead of direct file access.
- Multiple added fonts are preserved in the compact font picker.
- Added fonts remain available after returning from the viewer.
- Double-tap resets to the default/original size.
- Single-tap toolbar fold/return with folded-mode safe-area padding plus a 6dp extra buffer.
- Zoomed edge-swipe page turning requires a deliberate edge gesture instead of accidentally turning during the same pan.
- Word/EPUB popup windows match the compact TXT reader width, except bookmark dialogs.

### Bookmarks

- JSON-based bookmark storage.
- Custom bookmark labels, memos, excerpts, and grouped bookmark list by file.
- Bookmark folders default to collapsed on the main bookmark page.
- Viewer bookmark dialogs keep stable height to avoid first-bookmark bounce.
- Long-press a bookmark folder to delete all bookmarks inside that folder.
- Folder-delete confirmation uses the rounded/bordered dialog style.
- Bookmark memo dialogs include **Cancel**, **Clear memo**, and **Save**.
- TXT bookmarks save from the title-covered visual row and restore by character position plus nearby anchor text.
- TXT bookmark jumps pass anchor context into the loading path, improving restoration across large-TXT partitions and layout changes.
- TXT bookmarks remain more stable after font, spacing, and boundary changes because the saved text passage is used as the logical target.
- Portable bookmark identity metadata allows imported bookmarks to rebind to the same file after the file is moved or restored on another device.
- Export/import support for bookmarks, reading positions, app settings, layout settings, and custom reading themes.
- Backup filenames use the timestamped form `textview_backup_year_month_day_hour_minute_second.json`.
- Exported backups include friendly bilingual backup-edit guidance and separated `bookmarkEdits.beginner` / `bookmarkEdits.developer` areas for PC editing.
- Import remains compatible with older backups that use `beginnerEditableBookmarks`, `pcEditPosition`, or older memo/position edit fields.
- Reading-position persistence per file.
- Bookmark cleanup and cache cleanup are separate.

### Themes, fonts, and appearance

- Built-in reading themes.
- Custom theme editor.
- Long-press custom reading themes to edit or delete them with rounded popup controls.
- Light/dark/follow-system behavior.
- Theme state refreshes correctly when returning from Settings to viewer popups and already-open EPUB/Word WebView pages.
- Font selection shared across TXT, EPUB, and Word where applicable.
- EPUB/Word can preserve file-declared default fonts.
- Dialogs, popup windows, and loading indicators are theme-matched and sized consistently across viewers.
- TXT bookmark, More, Font, and Add Font windows use rounded card-style styling aligned with PDF/EPUB/Word dialogs.

### Privacy

TextView Reader is intended as a local reader. Settings shows a static GitHub releases link for manual update checking; the app does not contact GitHub itself.

- No analytics SDK.
- No advertising SDK.
- No account login.
- No cloud sync backend.
- No remote telemetry collection.
- No in-app network update checks.
- No background update checks.

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
app/src/main/java/com/textview/reader/
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

See [`CHANGELOG.md`](CHANGELOG.md) and [`PATCHNOTES.md`](PATCHNOTES.md).
