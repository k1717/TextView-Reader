# Changelog

## 2.0.5 - 2026-05-10

### Bookmark UI

- Added long-press bookmark-folder deletion in TXT, PDF, EPUB, and Word bookmark windows.
- Added rounded/bordered confirmation dialogs for bookmark-folder deletion.
- Kept viewer bookmark dialog height stable so adding the first bookmark does not bounce or resize the window.
- Kept empty bookmark dialogs at the same length, leaving the middle list area blank while keeping the Close button in place.
- Reduced TXT bookmark title/status spacing.
- Reordered viewer bookmark controls so the hint appears before the add-bookmark button.

### Dialog and card styling

- Retuned TXT bookmark cards to better match the tone used by PDF/EPUB/Word bookmark cards.
- Added spacing between expanded bookmark rows.
- Updated TXT More rows and subsections, including font windows, to use rounded card-style surfaces.
- Matched TXT Font/Add Font outer border width with the other stable reader dialogs.
- Retuned the Add Current Bookmark button style across TXT, PDF, EPUB, and Word.
- Stabilized the main Sort popup to avoid visible position movement after opening.

### Font management

- Added persistent multi-font support for user-added fonts.
- Added fonts now remain available after returning from the viewer and reopening the font picker.
- Added long-press removal for user-added fonts from the normal compact Font picker only.
- Kept the Add Font / All System Fonts window as add-only, so removed fonts can be retrieved later.
- Reset font selection to default if the selected added font is removed.

### Version metadata

- Updated Android version metadata to `versionCode 205` and `versionName "2.0.5"`.
- Updated `README.md`, `CHANGELOG.md`, and `PATCHNOTES.md` for the 2.0.5 release.

## 2.0.3 - 2026-05-09

### Functional changes from 2.0.2

#### EPUB / Word reader

- Added a bottom-bar **Find** button next to **Next** instead of placing document search inside the More menu.
- Added document search for EPUB/Word with:
  - search input;
  - match counter;
  - Previous / Next navigation;
  - wrap-around across document pages;
  - WebView-native text highlighting.
- Applied the TXT-reader style custom cursor, highlight, and selection-handle behavior to the EPUB/Word search input.
- Removed only the unused Word/EPUB More-menu zoom buttons:
  - Decrease zoom;
  - Increase zoom;
  - Reset zoom.
- Restored and preserved double-tap reset/original-size behavior after removing the buttons.
- Improved zoomed Word/EPUB edge-swipe behavior:
  - first drag pans within the expanded page;
  - page turn requires a deliberate edge gesture;
  - edge re-swipe window is now 600 ms;
  - page-turn sensitivity is slightly improved so it does not feel like two full swipes are required.

#### EPUB / Word fonts

- Connected the TXT reader font-selection structure to the EPUB/Word font dialog.
- Added the same fixed header, scrollable font list, bottom action row, Add Font action, and all-system-font subsection structure.
- EPUB files that declare their own font can open with **Default font** as the first option.
- Word files now use the same **Default font** logic when a DOCX-declared font is detected from document/style/font metadata.
- Choosing **Default font** avoids forcing the shared TXT reader font when the file has its own declared font.
- Imported/custom fonts for EPUB/Word are routed through the internal WebView font path instead of direct external file access.
- Removed the bottom-edge barrier from the EPUB/Word font picker and all-system-font subsection while keeping the separator above the bottom action row.

#### PDF reader

- Improved the PDF More dialog slide-mode label so it updates immediately when the mode changes.
- Improved vertical continuous PDF rendering when pages previously appeared blank.
- Improved zoom behavior in vertical continuous PDF mode.
- Added horizontal panning for zoomed pages in vertical continuous PDF mode.
- Increased horizontal pan response speed for zoomed vertical PDF pages.
- Kept bookmark windows wider, while regular PDF popups follow the compact TXT-reader width.

#### Popup/dialog behavior

- Word/EPUB/PDF More, File Info, Go to Page, and related subsection windows now follow the TXT reader compact popup width.
- Bookmark dialogs are intentionally excluded from this width limit.
- Fixed hard-landing/diagonal drop behavior in those popups.
- Fixed transparent-popup regression so dialog panels keep their visible rounded background.
- Fixed Go-to-Page positioning behavior in PDF and Word/EPUB so the dialog does not visibly jump after opening.

#### Main screen

- Improved the main sort dialog selection indicator so the radio bubble sits more naturally inward from the left edge.

### Packaging and documentation

- Updated README for the 2.0.3 reader behavior.
- Updated privacy notes for folder shortcuts, generated cache data, local font handling, and document search behavior.
- Updated GitHub upload notes for 2.0.3.
- Kept the public source package free of Android Studio workspace files, Gradle cache files, build outputs, local SDK paths, APK/AAB files, signing keys, `.env` files, and secret configuration files.

## 2.0.2 - 2026-05-09

### Functional changes from 2.0.1

- Increased the huge TXT preview-only threshold from **20 MB** to **32 MB**.
  - This allows larger TXT files to stay in the fuller large-text path before falling back to preview-only mode.
- Added user folder shortcuts.
  - Long-press a folder in the file browser to add it as a drawer shortcut.
  - User-added shortcuts appear in the drawer below the built-in storage entries.
  - Long-press an added drawer shortcut to remove it.
  - Long-press the same folder again in the file browser to remove its shortcut.
- Improved drawer organization.
  - Built-in entries remain available.
  - User shortcuts are separated from recent folders.
  - Recent folders no longer duplicate user-added shortcuts.
  - Placeholder shortcut rows keep the drawer layout stable.
- Improved drawer navigation responsiveness.
  - Drawer entry navigation is queued until after the drawer close animation, reducing the perceived delay when opening heavy folders such as Downloads.
- Added PDF reading-mode control.
  - PDF can use horizontal page-slide mode.
  - PDF can use vertical continuous reading mode.
  - The selected PDF mode is saved in local preferences.
- Added TXT page indicator alignment controls.
  - Page indicator can be left, center, right, or hidden.
  - Hidden mode keeps the reserved indicator row so TXT content does not jump upward.
- Refined file search and file-type filtering.
  - The search clear button appears only when there is typed search text.
  - Selecting General/PDF/EPUB/Word without typed search no longer shows an unnecessary clear button.
  - Empty type filtering on the Recent page filters the recent list locally instead of starting a broad storage scan.
  - Returning from search/filter restores the prior home/folder location more reliably.
- Improved sorting behavior.
  - Folder sorting reloads the current directory instead of disturbing unrelated search roots.
  - Recent and folder lists scroll back to the top after sort/filter/list refreshes.
- Strengthened viewer lifecycle handling.
  - Added shared viewer registration to reduce stacked viewer instances across TXT, PDF, EPUB, and Word transitions.
  - TXT, PDF, and document viewers perform additional cleanup of callbacks, adapters, renderers, WebViews, and transient state.
- Improved document/PDF page swipe behavior.
  - Document page swipes better distinguish between page turns and internal page scrolling.
  - PDF page animation supports horizontal and vertical direction depending on reading mode.


### 2.0.2 stability refresh

- Fixed PDF **Go to Page** behavior in vertical continuous mode.
  - Page jumps now snap the RecyclerView directly to the requested page instead of landing between pages while page bitmaps are still binding.
  - Continuous PDF rows now keep stable estimated heights before their bitmaps finish rendering, reducing jump drift and visual reflow.
  - The current-page indicator is synchronized from the visible page closest to the viewport center.
- Improved PDF continuous-mode rendering stability.
  - Added bounded bitmap caching for continuous PDF pages.
  - Suppressed duplicate background renders for the same page/zoom/generation.
  - Prefetches the current page and neighboring pages around jumps.
  - Releases single-page bitmaps when switching to continuous mode and clears continuous-page bitmaps when switching back to horizontal mode.
- Strengthened PDF viewer lifecycle cleanup.
  - Removes RecyclerView scroll listeners on destroy.
  - Detaches the continuous adapter before releasing cached page bitmaps.
  - Cancels stale render generations so old background work cannot reattach bitmaps after the viewer is closed or a new PDF is opened.

### Documentation and repository updates

- Updated `README.md` for version 2.0.2 and the new folder-shortcut, PDF-mode, TXT threshold, page-indicator, drawer, and search/filter behavior.
- Updated `CHANGELOG.md` with this 2.0.2 comparison against 2.0.1.
- Updated `PRIVACY.md` to mention locally stored folder shortcuts and generated cache metadata.
- Updated `CONTRIBUTING.md`, `ANDROID_STUDIO_SETUP_FOR_BEGINNERS.md`, `BUILD_FIX_NOTES.md`, and `GITHUB_UPLOAD_NOTES.md` for the current package.
- Updated Android version metadata to `versionCode 202` and `versionName "2.0.2"`.
- Kept the MIT license under `Copyright (c) 2026 k1717 aka Delphinium`.
- Cleaned the public source package by excluding `.idea/`, `.gradle/`, `app/build/`, root `build/`, `local.properties`, generated APK/AAB files, signing keys, `.env` files, and other private/generated files.

## 2.0.1 - 2026-05-07

### Final 2.0.1 source refresh

- Kept the public release version at `versionName "2.0.1"` and `versionCode 201`.
- Added initial language behavior that follows the Android system locale until the user explicitly chooses English or Korean.
- Added disposable TXT page/index cache bookkeeping for large TXT files.
- Added `PageIndexCacheManager` and TXT reader integration for best-effort large-file cache access recording.
- Updated README and instruction documents to describe the 2.0.1 behavior.

### Main file browser

- Home page recent files default to recently read first.
- Added a compact sort icon beside the file search field.
- Sort control works on both the home recent-files page and normal folder browsing.
- Added a separate recent-home sort preference.
- Kept drawer storage shortcuts fixed and made only recent-folder entries scrollable.
- Cached external storage detection to reduce drawer rebuild overhead.

### Reader and document viewer

- Reused viewer activities with `singleTop` / `onNewIntent` handling to avoid repeated viewer stacks.
- Improved lifecycle cleanup for handlers, executor services, PDF bitmaps/renderers, WebView resources, and selection state.
- Kept Word/DOCX rendering on WebView after testing showed selectable-TextView rendering degraded document layout.
- Improved DOCX/Word page behavior and left/right swipe page movement.

### Documentation and release hygiene

- Rebuilt the public documentation set with readable Markdown formatting.
- Added repository hygiene guidance and privacy notes.
- Expanded `.gitignore` for Android local/generated/private files.
