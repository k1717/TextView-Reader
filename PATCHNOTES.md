# TextView Reader Patch Notes

## 2.1.6

This 2.1.6 package is prepared as the current release. It uses Android metadata `versionCode 2160` and `versionName "2.1.6"`.

### TXT encoding coverage

- TXT encoding detection is no longer limited around UTF/CJK legacy candidates. It now uses Android ICU-assisted detection where available and includes common Latin, Greek, Cyrillic, Turkish, Baltic, Hebrew, Arabic, and Thai single-byte encodings.
- Added heuristic detection for BOM-less UTF-16LE/UTF-16BE before accepting strict-valid UTF-8, reducing broken no-BOM UTF-16 output with embedded NUL bytes.
- Android ICU-assisted detection is tried before fallback scoring; fallback scoring also counts broad alphabetic-script characters and common book punctuation for old TXT files across more writing systems.
- Strict valid UTF-8 is still preferred for explicit Unicode text, and BOM-based Unicode detection remains first.
- Decode-time fallback now trusts UTF-8 BOM and selected encoding results instead of rescoring the full file and accidentally reopening valid UTF-8 Korean text as legacy mojibake.

### TXT Unicode safety

- Bookmark excerpt, anchor-before/anchor-after, large-TXT exact anchor context, generated page-anchor context, text chunk splitting, and long-press word extraction use surrogate-safe substring boundaries.
- This avoids splitting emoji or supplementary Unicode characters into invalid UTF-16 halves while saving, exporting, rebuilding, resolving bookmarks, or extracting selected text.

### Large TXT tap/slider skip guard

- Exact-completed tap paging chooses the target from the current exact-anchor interval, not by applying tolerance directly to the current character position.
- Forward tap moves to the immediate next exact anchor; previous tap snaps back to the current exact page start when the current top row is already inside that page, then moves to the previous page on the next tap.
- The pre-exact fallback path applies the same previous-tap rule with local page-start anchors, including the first-page case before a previous-partition handoff.
- This keeps tap movement aligned with the same exact anchor table used by the toolbar slider and Go to Page while preventing anchor-nearby one-page skips.

### Main dark navy theme and drawer polish

- Added a **Dark Navy** app/main UI theme option to the Settings theme radio group.
- Added a **Custom** main UI theme option with HEX color fields for background, panel, app bar, text, secondary text, and outline colors.
- Custom reading themes now accept direct HEX color input in addition to RGB sliders for background and text colors.
- The theme uses a dedicated navy palette for background, app bar, panels, search field, file type chips, drawer rows, file-list rows, settings controls, and main rounded dialogs.
- Text, secondary text, headers, strokes, icons, and selected controls are assigned explicit contrast-safe colors so labels remain readable and do not visually merge into the navy surfaces.
- Dark Navy reading-theme preview cards use a darker navy card surface. Drawer bottom actions use flat drawer rows across all main themes instead of separate rounded card blocks.
- Drawer swipes update the drawer offset proportionally during the drag, use a lower start threshold with mild drag gain, and keep direction consistent: the left drawer follows rightward horizontal swipes only.
- Drawer swipes are no longer limited to the left edge of the main screen. Releasing after pulling the drawer at least 30% open completes the open gesture; smaller partial pulls close cleanly back to the main screen.
- Starting a drawer swipe cancels pending main-list long-press actions and clears visible pressed row states.
- Drawer item taps start navigation immediately while the drawer closes.
- All Bookmarks page now follows the selected main theme palette, including Dark Navy, instead of staying on the old black surface.

### Build reliability

- Custom main theme now has a separate Reading theme card color option for the Settings reading-theme list rows/cards.
- Custom main theme HEX field previews now survive the global Settings recolor pass by excluding those six fields from generic EditText background tinting.
- Removed duplicate PrefsManager reader/layout/lock/sort/gesture preference methods from the restored API merge, fixing the duplicate-method Java compile errors.
- Restored the full PrefsManager API surface while keeping Custom main theme HEX/RGB color support, fixing missing-method Java compile errors.

- Custom main theme HEX fields now preview their own color directly, and the Settings text explains which UI surfaces each color controls.

### Build metadata

- README, changelog, and patch notes match the project build files: Android Gradle Plugin `9.1.1` and Gradle wrapper `9.3.1`.
- Android app metadata is `versionCode 2160` and `versionName "2.1.6"`.

## 2.1.5

This 2.1.5 package is prepared for GitHub upload. It uses Android metadata `versionCode 2150` and `versionName "2.1.5"`.

### Main file-browser delete confirmation

- The main file-browser long-press Delete action now opens a compact rounded reconfirmation dialog before deleting a file or folder from storage.
- The dialog is narrower than the earlier full-width version and uses stacked actions: **Delete** on top and **Cancel** below.
- The dialog includes the selected file/folder name and a warning that deletion cannot be undone.
- After deletion, the current screen state is refreshed: Recent/home reloads recent files, search results rerun the active query, folder browsing reloads the current directory, and drawer/recent-folder state is rebuilt.
- Deleted folders are removed from recent-folder, folder-shortcut, and last-directory state where applicable.

### Bookmark backup page-model summary

- Backup export includes `largeTxtBookmarkPageModelSummary` for current, stale/unknown, and not-yet-recalculated TXT bookmark page labels.
- Backup export does not open every TXT file to force page/total recalculation after a partition-mode change.
- TXT bookmark edit rows keep status fields including `cachedPageModelMatchesCurrentPartitionMode` and `requiresOpenFileRebuildForCurrentPageModel`.
- Stale or unknown TXT `Page X / Y` values are marked as display metadata and refresh after the relevant TXT file is opened and exact anchors rebuild.
- Bookmark location remains preserved through `charPosition`, line, and surrounding anchor text.

### Background memory handling

- TXT and PDF viewers use delayed background memory trimming instead of clearing heavy state immediately on a brief app switch.
- A 420-second grace delay protects short app switches, Settings visits, and quick Home/app-switcher returns.
- Returning before the delay cancels the trim and keeps the current view warm.
- If the app remains hidden beyond the grace window, or Android reports stronger memory pressure such as `TRIM_MEMORY_BACKGROUND` or `TRIM_MEMORY_RUNNING_LOW`, the viewer trims heavy state more aggressively.
- TXT saves the current character/page position and nearby anchor text, releases loaded text snapshots, large-TXT partition caches, exact page anchors, prefetch/search state, and in-flight generation tokens, then lazy-reloads the same file at the saved position on return.
- PDF recycles the current single-page bitmap and clears vertical-continuous bitmap caches while keeping the PDF renderer itself available for faster redraw.

### Build metadata

- README, changelog, and patch notes match the project build files: Android Gradle Plugin `9.1.1` and Gradle wrapper `9.3.1`.
- Android app metadata is `versionCode 2150` and `versionName "2.1.5"`.

## 2.1.4

This section remains the 2.1.4 release history. It is not folded into later release sections.

### Large TXT partition modes and exact page model

- Added a two-track large-TXT partition policy in Settings.
  - Standard: `4000/400` lines, used by default.
  - High buffer: `12000/600` lines.
- The second value is used for both lookahead and manual-scroll lookbehind.
- Moved the large-TXT partition-mode selector below the TXT boundary sliders.
- Large TXT exact anchors, tap paging, slider, Go to Page, bookmarks, search jumps, partition cache signatures, and runtime partition switching read the same selected partition policy.
- The selected runtime canonical partition model is the official large-TXT page model, so page count and navigation agree with the rendered partition model.
- Switching between Standard and High buffer modes invalidates the previous exact-page denominator, refreshes the current partition under the selected mode, and schedules a full exact page-index rebuild through the layout-stability gate.
- Partition mode, body-line count, and buffer-line count are included in page/index signatures so incompatible exact anchors are not reused.

### Large TXT tap, slider, Go to Page, and manual-scroll continuity

- Exact-completed tap next/previous movement uses the exact page-anchor table. The reader selects the next/previous exact anchor from the current absolute top character position.
- Exact-completed slider and Go to Page movement uses the raw exact page-anchor char position directly.
- Manual-scroll lookbehind windows are stored separately and are not reused for page-number jumps, bookmarks, search jumps, or tap-page movement.
- Manual scroll can hand off to a prefetched next/previous partition after scroll settles while preserving the same absolute top position.
- If the neighboring partition is not cached or cannot represent the current viewport safely, the conservative top-based handoff path remains in use.
- Runtime lookbehind is only for manual seam handoff and does not inflate exact page-count indexing.

### TXT bookmark and backup page-model handling

- TXT bookmarks now store the page-layout signature that produced their cached page/total values.
- Switching between Standard `4000/400` and High buffer `12000/600` does not reuse stale TXT bookmark totals.
- When the current large-TXT exact anchor table is ready, existing bookmarks for the opened file are refreshed to the current partition mode's page/total count.
- Bookmark lists show TXT `Page current / total` only when the saved page metadata matches the active page-model signature.
- Bookmark jumps keep absolute text/anchor restore and discard stale page-offset metadata from a different partition mode.
- Exported backups now include explicit large-TXT bookmark page-model state: active partition mode, lookahead/lookbehind size, and whether cached `Page X / Y` matches the current mode.
- TXT bookmark backup edits that move a bookmark by line/search/raw character clear stale cached page metadata/signatures so `Page X / Y` is rebuilt from the active model.
- Backup edit guidance explains that TXT `pageNumber` / `totalPages` are cached display metadata tied to the selected partition mode; normal TXT bookmark edits should use `setLine`, `moveByLines`, or `findText`.
- Full backup schema marker is now `textview-full-backup-v10` with format version `8`.

### TXT boundary note and page-count stability

- Added a TXT layout boundary note in Settings explaining that boundary sliders move the readable viewport inward, but rendering and pagination snap to complete line boundaries to avoid half-cut rows.
- Explicit TXT page-model changes invalidate stale exact indexes immediately and schedule exact-anchor rebuilds through the stable-layout gate.
- Large-TXT exact indexing still waits for stable layout geometry and discards stale-signature results.

### Main browser parent-folder button

- Added a right-side **← Parent folder** / **← 상위 폴더로** button in the main file-browser path bar.
- The button moves to the current folder's parent and returns to Recent/home at a storage root.
- The button is hidden on the Recent screen and search-result screen.

### TXT Display Rules

- Added non-destructive TXT display rules for masking/replacing text while reading TXT files.
- Rules support enabled/disabled state, case sensitivity, regex/plain-text mode, global TXT scope, and current-file-only scope.
- Rules can be created from Settings or directly from the TXT viewer through **More > Add display rule** and long-press word prefill.
- Rules can show the source file they were created from.
- Up/Down ordering controls preserve top-to-bottom rule application.
- Delete actions use rounded confirmation UI.
- Rule edits apply after the rule/add window closes, keeping the manager responsive during editing.
- Display rules are applied before pagination, partition rendering, exact page indexing, search, and bookmark positioning.

### Edit Actual TXT File

- Added **Edit Actual TXT File** below **TXT Display Rules** in Settings when Settings is opened from a TXT viewer.
- The action permanently applies all enabled rules that apply to the current TXT file.
- Users can overwrite the original TXT file or write to `originalname_edited.txt`.
- Copy mode overwrites the same `*_edited.txt` target instead of creating repeated numbered copies.
- Original mode reloads and fully repaginates the opened viewer after the write succeeds.
- The destructive flow uses rounded dialogs, warning boxes, a second **Are you sure?** step, and an emphasized **There is no turning back.** warning.
- Physical writes use a same-folder temporary file and replacement step to reduce partial-write risk.

### TXT Auto Page Turn

- Added low-power automatic page turning for TXT reading.
- The interval is entered in seconds per page.
- Auto Page Turn advances by one full page at each interval.
- It stops at the final page, when the viewer leaves the foreground, or when the user manually scrolls/moves the page.
- A stopped message is shown when manual page movement interrupts Auto Page Turn.

### Large TXT page movement, search, and final page

- Large TXT page movement no longer waits for exact full-page indexing.
- If exact anchors are still building or fail, Go to Page / slider movement falls back to an estimated selected-size partition jump.
- Replaced the memory-heavy full-file exact-index layout path with chunked line-based exact indexing.
- Exact page anchors are used automatically after background indexing is ready.
- Added a dedicated final-page path for partitioned TXT files so the EOF partition and physical visual EOF remain reachable.
- Large-TXT body search scans the display-rule-applied TXT stream instead of only the currently loaded partition.
- Search finds the matching logical line, loads the owning partition, and moves to the result without waiting for exact page indexing.
- Added **Nth / n번째** search to jump directly to a specific occurrence.

### EPUB, Word, and PDF reader cleanup

- EPUB and Word Find avoid JavaScript/DOM marker layout edits and rely on native WebView Find behavior.
- Word same-page Previous/Next search uses native WebView `findNext()` / `FindListener` behavior.
- Word and normal EPUB Find use an inline toolbar-level panel so controls do not float over the WebView search target.
- Fixed-layout EPUB Find uses an overlay panel so opening Find does not shrink the WebView or reflow fixed-size pages downward.
- Fixed-layout EPUB pages are detected through fixed-layout metadata / numeric viewport data and centered as fixed-size pages during normal reading.
- EPUB/Word bottom toolbar stays fixed while the soft keyboard is open.
- EPUB/Word double-tap reset is stabilized so app reset handling does not conflict with WebView native double-tap zoom.
- PDF horizontal page-swipe mode resets zoom to `1.0` when moving to another page, while vertical continuous scroll keeps the current zoom.
- PDF page movement preserves scroll offset instead of forcing a top-left snap.
- Increased zoomed PDF in-page panning acceleration for both horizontal and vertical movement in horizontal swipe mode.

### Rounded popup and Settings cleanup

- Applied rounded popup styling to the display-rule, actual-file edit, auto-page-turn, delete-confirmation, and settings-reset dialogs.
- Added **Reset settings** for restoring reader/app preferences while keeping bookmarks, reading positions, recent files, folder shortcuts, TXT Display Rules, custom themes, and PIN lock.
- Settings backup/export includes TXT Display Rules through the existing settings import/export path.
- Settings backup guidance now uses neutral “clear/간단한” wording.

### Kept compatibility notes

- Large TXT active rendering remains based on fixed selected-size logical-line partitions.
- TXT pagination keeps the status-bar-off content spacing model so status-bar visibility does not change total page count.
- Backup bookmark editing keeps the `bookmarkEdits.beginner` / `bookmarkEdits.developer` structure.
- PDF, EPUB, and Word/DOCX remain separate document-viewer paths.
- The package identity remains `com.textview.reader`; legacy-package users still need backup/export/import migration.

### Build toolchain metadata

- README, changelog, and patch notes match the project build files: Android Gradle Plugin `9.1.1` and Gradle wrapper `9.3.1`.
- Android app metadata is `versionCode 2140` and `versionName "2.1.4"`.

### Verification note

ZIP integrity and Markdown structure were checked. Full Gradle verification should be run locally in Android Studio or another network-enabled environment if the Gradle wrapper needs to download Gradle.
