# Changelog

## 2.0.7 - 2026-05-11

### EPUB reader

- Moved EPUB boundary control from the viewer **More** menu into **Settings**.
- Added **Settings > EPUB layout** controls for separate left, right, top, and bottom EPUB reading boundaries.
- EPUB boundary sliders use raw px units, support **0px to 240px**, and move in **5px** steps.
- EPUB top/bottom boundaries now work as viewer-edge boundaries, matching the TXT boundary behavior instead of acting as HTML body padding.
- EPUB boundary changes apply when returning to the EPUB viewer from Settings.
- Fixed EPUB/Word page content so changing the reader theme in Settings refreshes the already-open WebView page after returning to the viewer, while preserving the current page/scroll position.
- Adjusted EPUB bottom boundary behavior so the visible bottom toolbar counts toward the requested boundary; extra bottom margin is only added when the boundary value is larger than the toolbar height.
- Added EPUB **More** menu controls for **Increase Font**, **Decrease Font**, and **Reset Font Size**; these adjust the EPUB WebView text zoom and refresh the current page.
- Moved shared **Font Size** and **Line Spacing** controls above the **TXT layout** section in Settings so TXT layout only contains TXT-specific boundary controls.
- Kept Word/DOCX padding unchanged; the EPUB layout controls only affect EPUB files.
- Added **Open File** next to **Close** in the PDF, EPUB, and Word **More** popups for quicker return to the file browser.
- Backup export/import now includes app settings and custom reading themes in addition to bookmarks and reading positions; lock PIN data is intentionally not written to plain JSON backups.
- Custom reading themes can now be long-pressed and removed through the same rounded popup UI used by the other theme windows.
- Fixed custom-theme option popups so they open directly in their final centered position instead of first appearing on the right and then snapping into place.
- Moved the custom-theme options and delete-confirmation popups slightly below center while keeping the no-jump rounded-window behavior.
- Centered custom-theme popup button labels inside their rounded button boxes.
- Centered main file-operation popup action labels inside their rounded boxes, including shortcut and clear/remove confirmation actions.
- Fixed the Theme Editor **Save Theme** button text contrast in light/main-white mode.

### TXT viewer UI

- Updated the TXT bottom overlay to better match the document viewer control style while keeping the TXT-specific Find, Page, Bookmark, Settings, and More controls.
- Kept the slider/button area continuous without adding a horizontal separator between the middle control area and the bottom button row.
- Removed the remaining horizontal divider from TXT popup bottom action areas so Page/More/File Info-style windows use the same continuous card surface as the PDF/EPUB/Word viewer dialogs.
- Matched TXT popup outer borders to the PDF/EPUB/Word viewer dialogs by using the same thin theme-derived outline instead of the heavier TXT-only border.
- Matched TXT popup text tone to PDF/EPUB/Word dialogs by using the active reader theme text color instead of a generic black/white readable fallback.
- Changed the TXT **Go to Page** popup action from **Close** to **Go**.
- Reordered TXT/PDF/EPUB/Word bookmark popups so the saved-bookmark list stays as the main section and the **Add current bookmark** button sits below it.
- Changed bookmark hints from inline collapse/expand text to a separate small rounded popup in TXT, PDF, EPUB, and Word viewers.
- Moved TXT toolbar-triggered popups and their More-menu subsections slightly lower while keeping bookmark windows at their existing bookmark position.
- Matched PDF/EPUB/Word toolbar-triggered Find, More, File Info, and font popup positions to the same bottom offset used by the Go to Page popup.
- Kept the Font picker horizontal header/action separators while reducing font-page outer and row-card borders to 1px so the outline looks thinner on high-density screens.
- Added **Reset Font Size** to the TXT viewer **More** popup.

## 2.0.6 - 2026-05-11

### TXT Viewer

- Added a file-title overlay under the top page indicator when the 5-button TXT control selector is visible.
- Kept the file title hidden in full viewer mode.
- Matched the title color to the active viewer font color and increased the title size to **14sp**.
- Reworked the title mask so it uses a stable first-row slot instead of following last-page scroll clamping.
- Fixed page-boundary behavior so the next page no longer repeats the previous page's last line.
- Fixed page-boundary behavior so page turns no longer skip a line between pages.
- Fixed first-page row-grid alignment so page 1 aligns more closely with page 2 and later pages.
- Fixed cases where the last page's upper text row could be slightly cut off.

### Loading Windows

- Updated TXT loading UI so the rotating loading window no longer appears as a hard black box.
- Made TXT loading background, text, and spinner tint follow the active viewer theme.
- Made PDF loading spinner background/tint blend with the active viewer theme.
- Made EPUB/Word loading spinner background/tint blend with the active viewer theme.

### PDF / EPUB / Word Toolbar Folding

- Added single-tap toolbar fold/return behavior for PDF, EPUB, and Word viewers.
- Single-tapping the document/page area now hides or restores the top toolbar and bottom control bar.
- Preserved existing double-tap behavior:
  - PDF double-tap zoom.
  - EPUB/Word double-tap reset/original-size behavior.
- Added folded-mode safe-area padding so content is not blocked by punch-hole/status-bar areas or the 3-button navigation bar.
- Added extra top/bottom folded-mode margin beyond the raw system insets for better readability; tuned to 6dp so it protects content without wasting too much screen space.

### PDF Zoom

- Improved PDF pinch-zoom so it preserves the selected pinch/focal spot instead of drifting toward the upper-left corner.
- Improved PDF More-menu Zoom In/Zoom Out/Reset behavior so zoom changes preserve the current viewport center.
- Preserved existing double-tap zoom focus behavior.

### TXT viewer UI

- Updated the TXT bottom overlay to better match the document viewer control style while keeping the TXT-specific Find, Page, Bookmark, Settings, and More controls.
- Kept the slider/button area continuous without adding a horizontal separator between the middle control area and the bottom button row.

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
- Improved DOCX/Word page behavior and left and right swipe page movement.

### Documentation and release hygiene

- Rebuilt the public documentation set with readable Markdown formatting.
- Added repository hygiene guidance and privacy notes.
- Expanded `.gitignore` for Android local/generated/private files.
