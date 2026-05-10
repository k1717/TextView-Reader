# Changelog

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
- Added user folder shortcuts.
  - Long-press a folder in the file browser to add it as a drawer shortcut.
  - User-added shortcuts appear in the drawer below the built-in storage entries.
  - Long-press an added drawer shortcut to remove it.
  - Long-press the same folder again in the file browser to remove its shortcut.
- Improved drawer organization and recent-folder separation.
- Improved drawer navigation responsiveness by deferring heavy navigation until after drawer close.
- Added PDF horizontal page-slide mode and vertical continuous reading mode.
- Added TXT page indicator alignment controls: left, center, right, or hidden.
- Refined file search, file-type filtering, and search clear-button behavior.
- Improved sorting behavior on Recent and folder pages.
- Strengthened viewer lifecycle cleanup and single-viewer reuse behavior.

## 2.0.1 - 2026-05-07

### Functional changes from 2.0.0

- The app follows the system language by default until the user manually selects a language.
- TXT reading includes disposable page/index cache bookkeeping.
- Generated page/index cache data can be cleaned using retention logic.
- Cache cleanup affects generated pagination/index data only, not bookmarks, reading history, or saved reading positions.
- Viewer reuse behavior was improved so opening a new file uses the existing viewer flow instead of stacking repeated viewer instances.
- Recently-read order and folder sorting behavior were refined.
- Drawer navigation and lifecycle cleanup were improved.
