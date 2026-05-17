# Changelog

## 2.1.2 - 2026-05-17

This release adds TXT display-rule masking/replacement, optional permanent TXT rule application, low-power auto page turning, and several rounded-dialog UI refinements while keeping the same `com.textview.reader` package identity.

### TXT display rules

- Added **TXT Display Rules** for viewing-only text replacement or masking. Normal display rules do not modify original TXT files.
- Added Settings management for display rules.
- Added TXT viewer quick rule creation from **More > Add display rule**.
- Added long-press word detection in the TXT viewer so a visible word can be used as the prefilled find text for a new display rule.
- Added plain-text replacement as the default safe mode.
- Added advanced regular-expression mode for users who need more flexible term/name correction.
- Rules support enable/disable, case-sensitive or case-insensitive matching, all-TXT scope, and current-file-only scope when opened from the TXT reader.
- Added rule-source labeling so rules can show which file they were originally made from.
- Added rule ordering controls. Rules are applied from top to bottom, so overlapping rules can produce different results after Up/Down.
- Added quick enable/disable/delete controls in the rule list. Individual deletion now uses a rounded confirmation dialog instead of deleting on a single touch.
- Changed TXT display-rule list long-press behavior from delete to edit.
- Display-rule edits from the reader-side manager are applied when the manager/add window closes, keeping the rule window responsive while editing.
- Up/down order changes do not reload the active TXT viewer by themselves.
- Display rules are applied before TXT pagination, search highlighting, large-TXT partition rendering, and exact page-index construction, so page count and page movement follow the text actually shown on screen.
- Display-rule changes that affect visible TXT text invalidate stale page/index state when they are applied.
- Display rules are included in settings backup/import through the existing settings backup path.
- Multi-line find/replace is intentionally not supported in this version to keep partitioned large-TXT pagination consistent.

### Actual TXT file editing

- Added **Edit Actual TXT File** below **TXT Display Rules** in Settings when Settings is opened from a TXT viewer.
- The action permanently applies all enabled display rules that currently apply to the opened TXT file.
- Users can choose **Fix original file** or **Copy original and fix copy**.
- Original mode overwrites the opened TXT file, marks it physically modified, reloads the viewer, clears stale TXT page/index state, and recalculates the full page count. Writes are routed through a same-directory temporary file and replacement step to reduce partial-write risk.
- Copy mode writes to `originalname_edited.txt`. If that edited copy already exists, it is overwritten instead of creating numbered duplicates.
- Copy mode does not reload the currently opened original viewer.
- The application window warns that rule sequence matters, overwrite behavior is permanent, and large TXT physical edits can take extra time/memory because the full file is rewritten.
- The second **Are you sure?** confirmation uses rounded UI with merged red warning content and a larger bold **There is no turning back.** warning.

### TXT auto page turn

- Added low-power automatic page turning for TXT.
- Auto page turn is available from the TXT bottom toolbar and advances one full page after the user-specified number of seconds instead of continuously scrolling, making it more suitable for low-end and e-ink devices.
- Auto page turn stops at the final page and stops when the TXT viewer leaves the foreground.
- Auto page turn also stops when the user manually scrolls/drags the TXT body, uses tap paging, or jumps pages through the slider / Go to Page controls.
- Auto page turn shows a stopped message when manual page movement stops it.

### UI polish

- Kept Auto Page Turn on the TXT bottom toolbar, but moved TXT Display Rules back into More so the bookmark button label has enough toolbar space.
- Moved **Manage display rules** out of the TXT quick-add dialog bottom action bar and into the dialog body, leaving the bottom bar as **Cancel / Save** only.
- **Manage display rules** now opens the TXT display-rule manager directly from the reader instead of leaving the viewer for the general Settings screen.
- Reduced the quick-add popup input text size and input-box height for **Text to find / Text to show instead** so the form is less crowded.
- Moved the quick-add popup option labels inward so the four switch rows no longer sit too close to the left edge.
- Added extra top and bottom spacing around the four option toggles in the Settings > TXT Display Rules add/edit dialog.
- Moved the quick-add display-rule popup and auto-page-turn popup down to the same bottom-positioned height as the TXT Find popup.
- Moved the TXT viewer rule-delete confirmation box higher in the viewer.
- Restored normal action-button text sizing for the TXT display-rule quick-add dialog bottom buttons.
- Applied the app's rounded popup style to the new TXT display-rule, actual-file edit, auto-page-turn, and settings-reset popups.
- Shortened Auto Page Turn action buttons to Start/Stop, restored the normal action-button text size there, and clarified that the interval input is in seconds per page.
- Reduced the Auto Page Turn popup width to about 70% and centered the seconds input as a compact field taking about 40% of the popup width.
- Settings-side display-rule add/edit/clear dialogs use the same rounded bordered custom dialog style as the other Settings popups.

### Settings reset

- Added **Reset settings** in Settings. It restores reader/app preferences to defaults while preserving bookmarks, reading positions, recent files, folder shortcuts, TXT display rules, custom themes, and PIN lock.

### Version metadata

- Android `versionCode`: `212`
- Android `versionName`: `2.1.2`

## 2.1.1 - 2026-05-16

This entry shows the final functional difference from the uploaded **2.1.0** GitHub source package. It does not list every intermediate patch step, while older version entries remain below for full history.

### Large TXT paging and partitioning

- Changed large TXT active rendering to fixed **4,000-logical-line partitions** instead of the earlier estimated preview-window behavior.
- Added a lookahead region after each active partition so partition-end pages can render smoothly.
- Added in-place partition switching so crossing a partition boundary behaves like a page turn instead of a visible file reload where possible.
- Added coverage-exact partition handoff: before the exact global index is ready, large-TXT forward page turns continue from the first line not displayed on the previous screen instead of jumping blindly to the next 4,000-line boundary. This prevents skipped content and prevents extra repeated displayed lines beyond the configured overlap at partition seams.
- Respected the configured page-overlap setting in large-TXT partition mode while preventing extra duplication at partition seams beyond the user-selected overlap.
- Preserved the displayed global page number during partition-boundary page turns when the exact large-TXT page index is still unavailable, preventing jumps such as `8082/8093` suddenly falling back to an earlier partition estimate.
- Hardened rapid backward page turns at partition boundaries by blocking stacked boundary reloads until the active partition swap finishes.
- Preserved the current displayed total page count during partition swaps instead of recomputing the denominator from the temporary active partition.
- Added neighbor partition prefetching and a small partition cache for smoother boundary crossing.
- Expanded the active partition cache and made prefetch direction-aware, so repeated forward paging prioritizes next/next-next partitions while repeated backward paging prioritizes previous/previous-previous partitions.
- Added exact-index-aware queued page taps while a partition swap is pending, so rapid page-key input can be resolved through exact anchors after the swap instead of stacking unsafe reloads.

### Large TXT exact page index

- Added a background exact page-anchor index for large TXT files.
- Optimized current-page lookup using page anchors instead of linear scanning.
- Improved toolbar slider, Go to Page, Page Up/Down, and bookmark jump accuracy after the exact index is ready.
- Prevented exact Go to Page from silently relying on estimated positions while the exact index is still unavailable.
- Added layout-signature and generation checks to discard stale exact page-index jobs when TXT layout geometry, font, overlap, or file metadata changes before indexing finishes.

### TXT toolbar, loading window, and status-bar spacing

- Fixed toolbar page slider snap-back during async large-TXT jumps.
- Added pending target-page state so slider and label stay on the user-selected destination while loading.
- Restyled the TXT loading window as a compact rounded, theme-aware panel.
- Used the loading window for uncached large-TXT slider, Go to Page, and bookmark jumps.
- Kept cached same-partition jumps immediate without unnecessary loading flashes.
- Stabilized TXT pagination against status-bar visibility changes by using status-bar-off top spacing as the canonical TXT content layout.
- Moved the TXT page-indicator row visually lower by one reader text row so the stable spacing does not make the indicator feel pinned to the top.

### Large TXT final-page behavior

- Kept the exact page index aligned with the full StaticLayout paging model used by the TXT renderer, so large-TXT page boundaries use the same visual layout model as the active viewer.
- Normalized EOF page status so the final visible document content reports the final page instead of stopping one page early.

### Bookmark backup editing

- Replaced the long `beginnerEditableBookmarks` export layout with a cleaner nested structure:

```json
"bookmarkEdits": {
  "beginner": [],
  "developer": []
}
```

- `bookmarkEdits.beginner` is intended for safe user edits such as memo, target line/page, relative movement, and TXT phrase search.
- `bookmarkEdits.developer` keeps repair-oriented fields such as raw character position, anchors, file identity, and internal metadata.
- Added shorter and kinder bilingual English/Korean guidance for both beginner and developer sections.
- Kept import compatibility with the older 2.1.0 backup-edit fields.

### Bookmark jump anchoring

- Passed TXT bookmark anchor context during bookmark opening.
- Improved restoration to the intended passage after layout changes, large-TXT partition changes, or portable file rebinding.

### PDF viewer

- Made original-size PDF page swipes more sensitive.
- Relaxed original-size swipe direction strictness so slight diagonal motion is less likely to block a page turn.
- Removed unnecessary loading-spinner flashes during normal PDF page turns and zoom redraws.
- Kept initial PDF file loading feedback.
- Centered newly rendered pages after next/previous movement while zoomed in, instead of landing at the upper-left corner.

### EPUB reader

- Added EPUB page-direction settings for left-to-right and right-to-left reading.
- Renamed right-to-left EPUB wording to **Japanese-style** reading.
- Added EPUB transition-effect setting for slide or none.
- Made EPUB swipe direction and slide animation follow the selected reading direction.

### Memory and lifecycle hardening

- Cleared large-TXT partition caches, pending prefetch markers, exact page anchors, queued page deltas, and partition-switch state when the TXT reader releases memory.
- Invalidated large-TXT exact-index and partition-switch generations during TXT reader destruction so stale background work cannot reapply after the viewer closes.
- Switched background TXT file reads and exact-index reads to use the application context where possible, reducing temporary Activity retention risk without changing reader behavior.
- Cleared `CustomReaderView` page-anchor and search-highlight path state when text resources are released.

## 2.1.0 - 2026-05-15

This release consolidates the post-2.0.9 UI, package-name, bookmark, and backup-editing work into a GitHub-ready source package.

### Package identity

- Changed Android `namespace` and `applicationId` to `com.textview.reader`.
- Moved Java source packages to `com.textview.reader` and updated XML custom-view paths, ProGuard rules, README paths, and related references.
- Android treats this as a different app from legacy package builds. Use TextView backup/export in the old build and import it in this build to migrate app data.

### Dialog and popup UI

- Added adaptive rounded-popup sizing for constrained app windows such as split-screen, Samsung pop-up view, foldable half-window, and small-window modes.
- Preserved normal full-screen popup sizing; adaptive max-height/scroll behavior only activates when the app window is constrained.
- Centered popup/window headers across main-screen dialogs and TXT/PDF/EPUB/Word viewer dialogs.
- Removed shaded/ripple option-box effects from backup import and custom reading-theme action/delete dialogs.
- Made backup import and custom reading-theme action/delete dialogs compact at about 70% screen width.
- Kept rounded border-only styling for the affected Settings dialogs.

### Settings and update line

- Replaced the in-app GitHub update-check function with a static Settings release link: `Check updates at https://github.com/k1717/TextView-Reader/releases`.
- Tapping the release link copies it to the clipboard.
- Removed underline styling from the update link while keeping it pressable.
- Removed the in-app network update-check path; the app does not contact GitHub for updates.

### TXT reader and bookmark behavior

- Changed the default TXT tap-zone layout for new/fresh settings to horizontal: left = previous page, center = menu, right = next page.
- Fixed TXT bookmark saving so it samples the interior of the actual title-covered visual row instead of relying on raw scroll offsets.
- Avoided off-by-one bookmark saves caused by title-row boundary ambiguity.
- Added robust TXT bookmark anchors using saved character position plus nearby `anchorTextBefore` / `anchorTextAfter` context.
- TXT bookmark restore remains tied to the same text passage even after font size, line spacing, or boundary settings change.

### Portable bookmark identity and backup editing

- Added portable bookmark identity fields to backup JSON: `fileSizeBytes`, `quickFingerprint`, `fileIdentity`, and `localBindingPath`.
- Treats paths as local shortcuts; imported bookmarks can rebind to the same file after it is moved or restored on another device.
- Keeps normal bookmark loading fast: exact path lookup is used first, and quick fingerprint matching is lazy.
- Backup export filenames now use the timestamped format `textview_backup_year_month_day_hour_minute_second.json`.
- Backup schema updated to `textview-full-backup-v9`.
- Added bilingual read-first guidance and separated bookmark tutorial/edit sections.
- Added `beginnerEditableBookmarks` as the actual PC-edit area.
- Added beginner-edit support for TXT `setLine`, `moveByLines`, `findText`, `findOccurrence`, `findTextCaseSensitive`; PDF/Word `setPage`, `moveByPages`; and EPUB `setPageOrSection`, `moveByPages`.
- Import regenerates excerpts and anchor fields after beginner edits are applied.

## 2.0.9 - 2026-05-11

This entry lists the functional difference from **2.0.8** only. The 2.0.8 hardware-key, e-ink, toolbar animation, TXT reload, optimization, and Toast cleanup changes remain in the 2.0.8 entry below.

### TXT small-file row alignment

- Fixed very small TXT files so the first row aligns to the same visual row grid as normal TXT files instead of sitting lower when the content is shorter than the viewport.
- Kept very small TXT files as single-page content; this does not change paging logic for normal files.

### TXT page-boundary alignment

- Fixed a second-page-only TXT pagination case where page 2 could repeat page 1's last fully visible sentence after increasing the TXT bottom boundary.
- Page anchors now calculate page capacity from the actual visible layout top at each page anchor, including first-page visual compensation.

### Main-screen long-press dialog positioning

- Fixed hard-landing behavior for main-screen long-press follow-up windows: **Delete / 삭제**, **Rename / 이름 변경**, and **File Info / 파일 정보**.
- Moved the **Delete / 삭제** confirmation window slightly below center, while keeping it clearly above the **Rename / 이름 변경** window.
- Moved the **Rename / 이름 변경** window slightly upward from its previous bottom position.


### Main-screen folder loading

- Improved folder-opening responsiveness by loading directory contents on a background thread instead of blocking the UI thread.
- Added stale-load cancellation so older folder scans are ignored if the user navigates elsewhere before they finish.
- Switched full folder navigation updates to direct list replacement instead of DiffUtil animation to reduce delay on large folders.
- Kept existing sorting options, file-opening behavior, drawer shortcuts, and viewer logic unchanged.

## 2.0.8 - 2026-05-11

This entry lists the functional difference from **2.0.7** only. The full 2.0.7 UI/settings changes remain in the 2.0.7 entry below.

### E-ink and hardware page-turn support

- Hardened TXT bottom-toolbar tap handling for e-ink readers so toolbar buttons, including **More / 더보기**, are less likely to miss taps.
- Kept the TXT bottom toolbar in the front touch layer when visible.
- Expanded TXT hardware page-turn handling from volume keys only to common e-reader key codes, including Page Up/Down, D-pad keys, Space, media next/previous, L1/R1-style page buttons, and navigate previous/next.
- Consumed key-up events after page-turn handling to reduce accidental system volume changes on e-reader firmware.
- Applied the same **Volume Keys Page Up/Down** setting to **PDF, EPUB, and Word/DOCX** viewers.

### Toolbar interaction cleanup

- Removed hold/ripple animation from TXT, PDF, EPUB, and Word viewer bottom toolbar buttons.
- Disabled long-click/haptic behavior for the shared viewer toolbar button style while preserving normal tap actions.
- Kept the TXT e-ink touch fallback but removed the visible pressed-state animation.

### TXT viewer reload behavior

- Fixed TXT viewer return-from-theme/settings behavior so normal Activity recreation restores the loaded text from memory instead of reloading the text file from disk.
- Restored TXT state includes character position, search state, large-text preview state, file title, and page label.
- Kept disk reload as a fallback if the app process was killed and the memory snapshot is unavailable.

### Safe optimization cleanup

- Enabled release resource shrinking.
- Removed stale PdfBox ProGuard rules because the app uses Android `PdfRenderer`.
- Added a fast `hasRecentFiles()` check to avoid sorting all recent-file states when only checking whether any recent file exists.
- Replaced large-TXT preview stream skipping with direct file seeking.
- Avoided repeated full font-folder rescans once fonts have already been scanned in the current app session.
- Converted all remaining `Toast.LENGTH_LONG` messages to `Toast.LENGTH_SHORT`.

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

### 2.1.0 - Beginner bookmark edit freedom expansion
- Expanded beginner bookmark backup editing fields beyond absolute position.
- TXT bookmark edits can now use exact line, relative line movement, or phrase search.
- Document bookmark edits can now use exact page/section plus relative movement.
- Added pending TXT edit resolution for imported backups when files are on a different device/path.

### Import dialog no-shade option boxes

- Removed the shade/ripple/elevation effect from the backup import confirmation option boxes.
- Kept the rounded box border style and Merge / Replace / Cancel behavior unchanged.

### Import dialog rounded UI hotfix
- Changed the backup import confirmation window from the default system AlertDialog to the same rounded custom settings dialog style used by the other Settings popups.
- Kept import behavior unchanged: Merge, Replace, and Cancel still perform the same actions.


### Settings dialog no-shade cleanup
- Removed the remaining shaded/ripple option-box style from custom reading-theme action/delete dialogs.
- Kept the rounded border-only style consistent with the import confirmation window.

### Compact custom-theme dialogs
- Made the custom reading-theme action and delete confirmation windows more compact horizontally, about 70% screen width.

### 2.1.0 import dialog compact width hotfix
- Made the Backup Import / 가져오기 confirmation dialog use the compact ~70% dialog width.
