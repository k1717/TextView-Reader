# TextView Reader

TextView Reader is a local Android reader for TXT, PDF, EPUB, and Word documents. It is designed around fast opening, simple navigation, bookmarks, theme control, custom fonts, and a file-browser workflow inspired by TekView.

Current version: **2.1.6**

## 2.1.6 release summary

- Updated Android version metadata to `versionCode 2160` and `versionName "2.1.6"`.
- Expanded TXT encoding detection beyond CJK-focused legacy encodings to include Android ICU-assisted detection plus common Latin, Greek, Cyrillic, Turkish, Baltic, Hebrew, Arabic, and Thai single-byte encodings.
- Added heuristic detection for BOM-less UTF-16LE/UTF-16BE before accepting strict-valid UTF-8, reducing broken output for no-BOM UTF-16 text.
- Fixed UTF-8 BOM / detected UTF-8 decode handling so broad fallback scoring cannot override explicit Unicode text and reopen Korean UTF-8 files as legacy-encoding mojibake.
- Android ICU-assisted detection and fallback scoring now consider broad alphabetic scripts and common book punctuation, so old TXT files in more alphabet systems are less likely to be forced through the wrong fallback charset.
- TXT bookmark excerpts and anchor context now use surrogate-safe substring boundaries so emoji and supplementary Unicode characters are not split while saving, exporting, rebuilding, or resolving bookmarks.
- Exact large-TXT tap navigation now derives the target from the current exact-anchor interval: forward tap moves to the immediate next anchor, while previous tap first snaps back to the current page start when the viewport is already inside that page. This avoids one-page skips near anchor boundaries.
- The same previous-tap snap rule is now applied before exact indexing is ready, using the current local page-start line as the fallback anchor. This keeps line-overlap-0 paging behavior consistent before and after the background exact index completes.
- Preserved the Android build toolchain metadata at **Android Gradle Plugin 9.1.1** and **Gradle wrapper 9.3.1**.

## 2.1.5 release summary

- Updated Android version metadata to `versionCode 2150` and `versionName "2.1.5"`.
- The main file-browser long-press Delete action now uses a narrower compact rounded reconfirmation dialog before physically deleting a file or folder from storage.
- The delete confirmation uses stacked actions instead of side-by-side buttons: **Delete** on top and **Cancel** below.
- After deletion, the visible main-screen state is refreshed: Recent/home reloads recent files, search results rerun the active query, folder browsing reloads the current directory, and drawer/recent-folder state is rebuilt.
- Deleted folders are also removed from recent-folder, folder-shortcut, and last-directory state where applicable.
- Backup export now includes a large-TXT bookmark page-model summary count. It does not open every TXT file to recalculate page totals after a partition-mode change; stale or unknown cached `Page X / Y` labels are marked and refresh when that file is opened and exact anchors rebuild.
- TXT bookmark edit rows include page-model status fields such as `cachedPageModelMatchesCurrentPartitionMode` and `requiresOpenFileRebuildForCurrentPageModel`, while bookmark position remains preserved through `charPosition`, line, and surrounding anchor text.
- Added delayed background memory trimming for TXT and PDF viewers. The app does not immediately discard reader memory when it is briefly sent to the background.
- Added a 420-second grace delay for short app switches, Settings visits, or quick Home/app-switcher returns. Returning within the grace window cancels the pending trim and keeps the current view warm.
- If the app remains hidden beyond the grace window, or Android sends stronger memory-pressure callbacks such as `TRIM_MEMORY_BACKGROUND` or `TRIM_MEMORY_RUNNING_LOW`, the viewer trims heavy state more aggressively.
- TXT background trimming saves the current character/page position and nearby anchor text, clears loaded text snapshots, large-TXT partition caches, exact page anchors, search/prefetch state, and in-flight generation tokens, then lazy-reloads the same file at the saved position on return.
- PDF background trimming recycles the current single-page bitmap and clears vertical-continuous bitmap caches while keeping the PDF renderer itself available for faster redraw when the user returns.
- Preserved the Android build toolchain metadata at **Android Gradle Plugin 9.1.1** and **Gradle wrapper 9.3.1**.

## 2.1.4 release summary

- Updated Android version metadata to `versionCode 2140` and `versionName "2.1.4"`.
- Added two Settings-selectable large-TXT partition tracks: Standard `4000/400` by default and High buffer `12000/600`. The second value is used for both lookahead and manual-scroll lookbehind.
- Moved the large-TXT partition-mode selector below the TXT boundary sliders in Settings so it sits with the TXT layout controls.
- Made the selected runtime canonical partition mode the official large-TXT page model. Final page count, tap next/previous, slider, Go to Page, bookmark, search jump, exact-anchor cache, and partition cache signatures now use the same model.
- Changing the large-TXT partition mode while a TXT is open now invalidates the previous exact-page denominator, refreshes the active partition under the selected mode, and schedules a full exact page-index rebuild through the layout-stability gate.
- After the large-TXT exact page index is ready, tap next/previous follows the exact page-anchor table. The target is selected from the current absolute top character position, so a small manual scroll before tapping snaps to the proper page start.
- Kept low-power manual-scroll continuity: after scroll settles, the reader can hand off to a prefetched neighboring partition while preserving the same absolute top position; conservative top-based handoff remains the fallback when the next partition is not ready.
- TXT bookmarks now store the page-layout signature that produced cached `Page X / Y` metadata. When the active large-TXT page model changes, stale cached page totals are not reused, and bookmarks are refreshed after the current exact anchor table is ready.
- Exported backups now include large-TXT bookmark page-model state, including the active partition mode, lookahead/lookbehind size, and whether cached TXT page metadata matches the current mode.
- TXT bookmark backup-edit guidance now treats `pageNumber` / `totalPages` as mode-dependent display metadata and directs normal edits to `setLine`, `moveByLines`, or `findText` instead.
- Full backup schema marker is now `textview-full-backup-v10` with format version `8` for the added large-TXT partition page-model fields.
- Preserved the Android build toolchain metadata at **Android Gradle Plugin 9.1.1** and **Gradle wrapper 9.3.1**.

## 2.1.2b release summary

- Updated build toolchain metadata to **Android Gradle Plugin 9.1.1** and **Gradle wrapper 9.3.1**.
- Added **TXT Display Rules** for viewing-only text replacement or masking. Normal display rules change only the text shown in the TXT viewer; the source file is not modified.
- Rules can be enabled/disabled, case-sensitive or case-insensitive, plain-text or regular-expression based, global for all TXT files, or limited to one TXT file.
- Added TXT-viewer quick add flow: long-press a visible word or use **More > Add display rule** to create a rule without leaving the reader.
- Added rule-source labeling, rule ordering controls, quick enable/disable/delete controls, and rounded delete-confirmation dialogs for TXT Display Rules.
- Display rules are applied before TXT pagination, large-TXT partition rendering, exact indexing, search, and bookmarks, so visible page movement follows the text shown on screen while rules are enabled.
- Added **Edit Actual TXT File** below **TXT Display Rules** in Settings when Settings is opened from a TXT viewer. It can permanently apply enabled applicable rules to either the original TXT file or a copied `*_edited.txt` file.
- Actual-file edit uses rounded confirmation popups, warning boxes, a second **Are you sure?** step, and an emphasized **There is no turning back.** warning.
- Added low-power **Auto Page Turn** as a TXT toolbar button. Auto Page Turn advances one page after the user-selected seconds interval and stops when the user manually moves the page.
- Added **Reset settings** in Settings. It restores reader/app preferences to defaults while keeping bookmarks, reading positions, recent files, folder shortcuts, TXT Display Rules, custom themes, and PIN lock.
- Added a right-side **← Parent folder** / **← 상위 폴더로** button in the main file-browser path bar.
- Improved large-TXT page movement with estimated selected-size partition jumps while exact indexing is still building, replacing the previous full-file exact-index path with chunked line-based exact indexing.
- Added large-TXT page-count stability handling: exact indexing now waits until the TXT layout geometry is stable before building page anchors, and stale-geometry index results automatically trigger a fresh rebuild instead of becoming the final total.
- Replaced object-hash-based typeface signature data with a stable font key for large-TXT exact-index signatures, reducing unnecessary index rebuilds across app restarts or font reloads.
- Normalized TXT reader bottom padding through a canonical bottom band so live navigation-bar inset timing does not change the viewport height used for TXT page counting.
- Improved large-TXT final-page handling so EOF-tail pages remain reachable by tap/page-down, with reduced flicker and reduced delay when the final partition is already active.
- Improved large-TXT Find so search can jump to the matching logical line/partition without waiting for exact page indexing. Added **Nth / n번째** search and background total-count completion for large-TXT search counters.
- Word same-page Previous/Next search uses native WebView `findNext()` / `FindListener` behavior only, without custom reveal, forced ordinal correction, or DOM marker insertion.
- Reworked Word and normal EPUB Find UI into an inline toolbar-level panel so the Find controls do not float over the WebView search target.
- Kept fixed-layout EPUB Find as an overlay so opening Find does not shrink the WebView or push the fixed page down by layout reflow.
- Fixed-layout EPUB pages are detected through fixed-layout metadata / numeric viewport data, kept out of reader-theme reflow CSS, and centered as fixed-size pages during normal reading.
- While fixed-layout EPUB Find is open, the fixed page is temporarily positioned below the Find overlay; closing Find removes the temporary offset and restores normal centered placement.
- Stabilized EPUB/Word double-tap reset so the app reset does not fight WebView native double-tap zoom behavior.
- PDF horizontal page-swipe mode now resets zoom to `1.0` when moving to another page; PDF vertical continuous scroll keeps the current zoom while scrolling between pages.
- PDF page navigation preserves the current scroll offset, so page movement no longer forces a top-left scroll snap.
- Increased zoomed PDF in-page pan acceleration for both horizontal and vertical panning in horizontal swipe mode, while keeping the existing horizontal page-turn threshold unchanged.
- Updated Android version metadata to `versionCode 2122` and `versionName "2.1.2b"`.

## What changed in 2.1.1 from 2.1.0

- Reworked large TXT active rendering around selected fixed-line partitions with mode-dependent lookahead, in-place partition switching, a bounded partition cache, and direction-aware neighbor prefetch.
- Added a background exact large-TXT page-anchor index so page labels, toolbar slider jumps, Go to Page, page turns, and bookmark jumps can resolve against real page anchors after indexing completes.
- Hardened large-TXT partition seams so page movement continues from the correct next-page anchor, respects the configured page-overlap setting, and avoids extra duplicated or skipped displayed content at selected partition boundaries.
- Stabilized large-TXT page status during fast forward/backward partition changes so temporary partition estimates do not make the displayed page or total page count jump backward.
- Made TXT pagination use a canonical status-bar-off content spacing model, so toggling Android status-bar visibility does not change TXT page counts. The page indicator is visually lowered by one reader text row.
- Fixed TXT toolbar slider and Go to Page behavior so the selected target page does not snap back to the old page while an uncached large-TXT partition is loading.
- Large-TXT exact-completed slider / Go to Page jumps now use the raw exact page anchor directly, without bookmark-style text-context re-anchoring, so repeated phrases on the same page cannot shift the landing position away from the page start.
- Updated the TXT loading window to a compact rounded, theme-aware panel and reused it for uncached large-TXT slider, Go to Page, and bookmark jumps.
- Changed backup bookmark PC editing from the old repeated `beginnerEditableBookmarks` tutorial format to a cleaner `bookmarkEdits.beginner` / `bookmarkEdits.developer` structure, while keeping backward-compatible import for old backups.
- Added clear bilingual English/Korean backup-edit guidance for both beginner-safe edits and developer/internal recovery edits.
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
- Expanded JSON backup/export with beginner-oriented bookmark editing sections, bilingual tutorial markers, detailed examples, and timestamped filenames such as `textview_backup_year_month_day_hour_minute_second.json`.

> Package migration note: Android treats `com.textview.reader` as a different app from legacy package builds. Use the backup/export flow in the old app and import the backup in this build to migrate bookmarks, reading positions, settings, and custom themes.

## Quick UI map

### Main screen

- **Recent files** are shown first.
- **File search** filters the visible file list.
- **Sort button** opens sort options for recent files and folder browsing.
- **← Parent folder** / **← 상위 폴더로** appears on the right side of the path bar while browsing folders.
- **Left drawer** contains fixed storage shortcuts, user-added folder shortcuts, recent folders, and bottom actions.
- **Long-press a folder** in the file browser to add or remove it as a drawer shortcut.
- **Long-press a user-added shortcut** in the drawer to remove it.

### TXT reader

- Tap the reader area to toggle the overlay controls.
- Use **Find** for text search, including next/previous match and direct **Nth / n번째** occurrence movement.
- In large TXT files, Find can scan the full file and jump to the matching partition without waiting for exact page indexing.
- Use **Go to Position** for percent/line jumps.
- Use **Bookmarks** to add, move to, edit, or delete bookmarks.
- Use **More > Font** to choose built-in, system-scanned, or imported fonts.
- Long-press an added font in **Font** to remove it from the compact font list.
- Page indicator alignment can be set to left, center, right, or hidden.
- When the TXT control selector is open, the current file title appears under the top page indicator and hides again in full viewer mode.
- TXT pagination uses line-boundary anchors to reduce duplicate/skipped lines and keep first/last page rows aligned.
- TXT boundary controls move the readable viewport inward, but TXT rendering still snaps to complete line boundaries so text rows are never half-cut. Depending on font size, line spacing, and the current row position, even a range of several slider steps or tens of pixels may look unchanged until the next full-line boundary is crossed; page-count changes follow the same line-sized steps.
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
- Right-side **← Parent folder** / **← 상위 폴더로** button in folder-browsing mode.
- Folder browsing with sorting by name, date, size, and type.
- Compact sort dialog with theme-matched selection indicators.
- Fixed drawer storage shortcuts for common locations.
- User-added folder shortcuts.
- Recent folders separated from user shortcuts.
- Rename, delete, new-folder, and file-info actions. Long-press delete uses a rounded reconfirmation dialog with Delete stacked above Cancel.
- Hidden-file toggle.
- Android Storage Access Framework support for files opened from other apps.

### TXT reading

- Encoding detection for UTF-8, UTF-8 BOM, EUC-KR/CP949/MS949, UTF-16 with BOM, and BOM-less UTF-16LE/UTF-16BE heuristic detection.
- Explicit BOM / detected encoding results are trusted during decode, preventing full-file fallback scoring from changing a file that was already identified as UTF-8.
- Large TXT handling with selected fixed-line active partitions with mode-dependent lookahead rendering, neighbor prefetch, and generated page/index cache bookkeeping.
- Large TXT exact page count is built in the background with the same selected canonical partition model used by the runtime reader, so page count and page jumps stay synchronized with the actual rendered view without requiring one giant full-file layout.
- Large TXT exact indexing is gated by layout stability: the app waits for matching layout measurements before starting the background page-anchor build, and restarts the build if the layout signature changes before completion.
- Exact large-TXT slider and Go to Page jumps use canonical body partitions; manual-scroll lookbehind buffers are kept in a separate cache and are not reused as page-jump targets.
- Large TXT viewport-sensitive setting changes still use the debounced layout-stability rebuild path, so saved near-end page/total restore labels are not overwritten by a temporary local-partition estimate while the exact index rebuilds.
- Large TXT page-count signatures use a stable font key instead of `Typeface.hashCode()`, avoiding false rebuild triggers caused by new Typeface object instances.
- TXT page counting uses a canonical reader bottom band instead of live navigation-bar inset height, making the viewport used for pagination more repeatable across app launches and system-bar timing.
- The exact large-TXT page map follows the same selected runtime canonical partition path used by tap, slider, Go to Page, bookmark, and search movement; mode-dependent seam lookahead text is used for continuity, not as an independent full-file page model.
- Manual finger scrolling in large TXT mode now re-anchors from lookahead text into the owning next partition after scrolling settles, reducing duplicated seam text during scroll-based reading.
- Pulling downward at the top of a large-TXT partition can load the previous partition, so manual scroll navigation is safer in both directions.
- Sequential TXT page turns now use a coverage guard: forward tap/volume/auto paging cannot start after the first row that was not fully visible on the previous page, and partition handoff uses the same guarded next-page anchor.
- Large TXT Go to Page and slider movement can fall back to estimated logical-line/partition jumps before exact indexing finishes.
- Large TXT search jumps to the result partition without waiting for exact page indexing. Previous/Next stops at the nearest match for responsiveness, and repeated Previous/Next taps can move instantly when the next match is already inside the loaded selected-size partition. Nth occurrence search scans the full file when needed.
- Search jumps place the matching line in a safe upper-middle reader area so newly loaded large-TXT partitions do not hide the result under the page title, toolbar overlay, or open Find popup.
- TXT search includes an **Nth / n번째** occurrence option for direct match navigation.
- Final-page jumps in partitioned TXT files use the real EOF partition's visual end to keep the document's last page reachable even when the file ends with blank lines.
- Final-page tap/page-down handling avoids the old anchor-draw then EOF-correction flicker path, and skips unnecessary final-partition reloads when already active.
- Generated TXT cache cleanup uses retention logic for disposable pagination/index data.
- Cache cleanup does not delete bookmarks, history, reading position, folder shortcuts, or documents.
- Text search with custom reader-dialog input styling.
- Search-result jumps use a shared reveal-safe position near the upper area, keeping matches visible above the lower Find popup for Next/Previous and nth-result jumps.
- Large-TXT Previous/Next search moves after finding the nearest match instead of waiting for a full-file match count. The match counter may temporarily show `n / …` while the total is unknown, then a separate background count fills in `n / total` automatically. The counter stays visible while search/count work is running.
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
- Horizontal page-slide / swipe mode.
- Vertical continuous scroll mode.
- Original-size page swipes are more sensitive for easier page turning.
- Pinch zoom keeps the selected focal spot stable.
- In horizontal page-swipe mode, moving to another page resets zoom to `1.0`.
- In vertical continuous scroll mode, page changes keep the current zoom.
- Page navigation no longer forcibly clears the PDF scroll offset to top-left.
- Zoomed in-page panning in horizontal swipe mode has increased horizontal and vertical acceleration.
- The horizontal page-turn threshold is kept unchanged from the previous tuned value.
- Zoomed edge-swipe page turning still requires a deliberate edge gesture instead of accidental page turns during normal pan movement.
- Single-tap toolbar fold/return with folded-mode safe-area padding plus a 6dp extra buffer.
- Improved continuous-mode blank-page recovery and render rebinding.
- More stable popup/dialog sizing and positioning.
- Page-turn and zoom redraws avoid showing the centered PDF spinner unless the file is initially loading.
- PDF slide-mode label refreshes while the More dialog is open.
- Bookmarks and file info.

### EPUB and Word reading

- EPUB and OOXML Word support through extracted/rendered WebView pages.
- Bottom-bar Find button next to Next.
- Word and normal EPUB Find use an inline toolbar-level panel so search controls do not cover the WebView viewport.
- Fixed-layout EPUB Find remains an overlay so opening Find does not shrink the WebView and does not reflow fixed-size pages downward.
- Fixed-layout EPUB rendering is preserved by detecting numeric viewport/fixed-layout metadata, avoiding reader-theme reflow CSS, and centering fixed-size pages in the viewer instead of pinning them to the top.
- While fixed-layout EPUB Find is open, the fixed page is temporarily placed below the Find overlay; closing Find restores normal centered placement.
- Search UI uses reader-style input, custom cursor/handle styling, match counter, Previous/Next, and wrap behavior.
- EPUB/Word bottom toolbar stays fixed while the soft keyboard is open in Find/text-input dialogs.
- EPUB and Word Find avoid JavaScript/DOM marker layout edits and rely on native WebView Find behavior.
- Word same-page navigation uses WebView native `findNext()` / `FindListener` behavior only.
- EPUB/Word double-tap reset is stabilized so app reset handling does not conflict with WebView native double-tap zoom.
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
- Exported backups include clear bilingual backup-edit guidance and separated `bookmarkEdits.beginner` / `bookmarkEdits.developer` areas for PC editing.
- Bookmark backup-edit guidance now explains that TXT `pageNumber` / `totalPages` are cached page-model metadata: they can differ between Standard `4000/400` and High-buffer `12000/600`, so normal TXT bookmark edits should use `setLine`, `moveByLines`, or `findText`.
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
- Android Gradle Plugin: 9.1.1
- Gradle wrapper: 9.3.1

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

