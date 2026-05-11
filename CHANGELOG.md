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

## 2.0.4 - 2026-05-10

### Bookmark stability and UI polish

- Main bookmark folders now default to collapsed/shrunk.
- Folder expand/shrink behavior remains fast with unnecessary list animation disabled.
- Bookmark edit memo dialogs use a more rounded custom bordered dialog style.
- TXT, PDF, EPUB, and Word bookmark memo edit dialogs now provide **Cancel**, **Clear memo**, and **Save** actions.
- Main bookmark delete/edit dialogs use the same stable custom dialog path to reduce first-open hard-edge or hard-landing glitches.
- Bookmark opening now uses a shared navigation path with null/empty file-path protection.

### Theme refresh and viewer popup fixes

- Viewers reload active theme state when returning from Settings or the theme editor.
- TXT, PDF, EPUB, and Word More dialogs refresh theme colors before drawing.
- PDF More now dismisses the old dialog before opening Settings or File Info, preventing stacked stale dialogs.
- Active theme saving now uses synchronous preference commit to avoid immediate-return race conditions.

## 2.0.3 - 2026-05-10

### EPUB / Word viewer

- Added a bottom-bar **Find/Search** button next to **Previous** / **Next**.
- Added EPUB/Word document search with a match counter, previous/next result navigation, and page-to-page wrapping.
- Updated the EPUB/Word search popup with TXT-style search input, cursor, and selection-handle behavior.
- Improved EPUB/Word font selection UI to follow the TXT reader font-window structure.
- Added **Default font** support.
  - EPUB prefers the file's declared font when available.
  - Word/DOCX detects document fonts and can use them as the default font.
- Removed unused More-menu zoom buttons from EPUB/Word while preserving double-tap reset/original-size behavior.
- Improved expanded-page edge swiping so page turns are more responsive while avoiding accidental same-drag page jumps.

### PDF viewer

- Improved vertical continuous-scroll mode.
- Fixed cases where vertical scrolling could show blank PDF pages.
- Improved zoom behavior in vertical PDF mode.
- Added horizontal panning for zoomed PDF pages in vertical mode.
- Increased horizontal pan speed for smoother movement.
- Improved bitmap/cache handling to reduce stale blank renders and memory pressure.
- Preserved the existing vertical Go-to-page behavior; the earlier repeated-snap Go-to-page change was not reintroduced.

### Popup / dialog UI

- Updated Word/EPUB/PDF popup widths to match the TXT viewer style, except bookmark dialogs.
- Improved More windows and subsection windows such as File information, Page move, Font picker, and the full system font list.
- Fixed popup hard-landing / diagonal-drop behavior.
- Fixed transparent popup background issues.

### Main file browser

- Improved the sort-window radio selection bubble placement so it sits more naturally inward.

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

## 2.0.1 - 2026-05-07

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
