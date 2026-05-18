# TextView Reader 2.1.2b Patch Notes

## Scope

This 2.1.2b package is prepared for GitHub upload. It keeps Android metadata at `versionCode 2122` and `versionName "2.1.2b"`.

## Main browser parent-folder button

- Added a right-side **← Parent folder** / **← 상위 폴더로** button in the main file-browser path bar.
- The button moves to the current folder's parent and returns to Recent/home at a storage root.
- The button is hidden on the Recent screen and search-result screen.

## TXT Display Rules

- Added non-destructive TXT display rules for masking/replacing text while reading TXT files.
- Rules support enabled/disabled state, case sensitivity, regex/plain-text mode, global TXT scope, and current-file-only scope.
- Rules can be created from Settings or directly from the TXT viewer through **More > Add display rule** and long-press word prefill.
- Rules can show the source file they were created from.
- Up/Down ordering controls preserve top-to-bottom rule application.
- Delete actions use rounded confirmation UI.
- Rule edits apply after the rule/add window closes, keeping the manager responsive during editing.
- Display rules are applied before pagination, partition rendering, exact page indexing, search, and bookmark positioning.

## Edit Actual TXT File

- Added **Edit Actual TXT File** below **TXT Display Rules** in Settings when Settings is opened from a TXT viewer.
- The action permanently applies all enabled rules that apply to the current TXT file.
- Users can overwrite the original TXT file or write to `originalname_edited.txt`.
- Copy mode overwrites the same `*_edited.txt` target instead of creating repeated numbered copies.
- Original mode reloads and fully repaginates the opened viewer after the write succeeds.
- The destructive flow uses rounded dialogs, warning boxes, a second **Are you sure?** step, and an emphasized **There is no turning back.** warning.
- Physical writes use a same-folder temporary file and replacement step to reduce partial-write risk.

## TXT Auto Page Turn

- Added low-power automatic page turning for TXT reading.
- The interval is entered in seconds per page.
- Auto Page Turn advances by one full page at each interval.
- It stops at the final page, when the viewer leaves the foreground, or when the user manually scrolls/moves the page.
- A stopped message is shown when manual page movement interrupts Auto Page Turn.

## Large TXT page movement and indexing

- Large TXT page movement no longer waits for exact full-page indexing.
- If exact anchors are still building or fail, Go to Page / slider movement falls back to an estimated 4,000-logical-line partition jump.
- Replaced the memory-heavy full-file exact-index layout path with chunked line-based exact indexing.
- Exact page anchors are still used automatically after background indexing is ready.
- The partition-aware exact map follows sequential reading continuity rather than restoring the old inflated full-file continuous count.
- Manual finger scrolling now has safer large-TXT seam handling: after scrolling settles inside lookahead text, the viewer switches to the owning next partition at the same absolute text position.
- Downward overscroll at the top of a large-TXT partition queues a previous-partition load instead of leaving manual scrolling trapped at the partition start.

## Large TXT page-count stability

- Exact large-TXT page indexing no longer starts immediately after `onFileLoaded()`. The first build is routed through the same restart path as later rebuilds.
- The restart path now waits for the TXT layout geometry signature to match across two debounce checks before starting the page-anchor build.
- The stability signature covers layout width, viewport height, vertical margin, overlap, and quantized line spacing.
- If the full exact-index signature changes while the background build is running, the stale result is discarded and a fresh stable-layout rebuild is scheduled automatically.
- The large-TXT exact-index signature now uses a stable FontManager typeface key instead of `Typeface.hashCode()`, avoiding false signature changes from new Typeface object instances.
- TXT pagination uses a canonical 60dp reader bottom band for page-count input, so live navigation-bar inset timing does not change the viewport height used by large-TXT exact indexing.

## Large TXT final-page fixes

- Added a dedicated final-page path for partitioned TXT files.
- Final-page movement loads or uses the real EOF partition instead of relying only on a global trailing-blank anchor.
- Fixed a case where manual scroll could reach the final page but tap/page-down stopped at the previous page.
- Final-page jumps can clamp to the physical visual EOF of the last partition when the final page is only a small EOF tail below the last anchor.
- If the final partition is already active, the viewer moves directly to visual EOF instead of reloading/rebuilding the partition.

## TXT body search and nth occurrence

- Large-TXT body search scans the display-rule-applied TXT stream instead of only the currently loaded 4,000-line partition.
- Search finds the matching logical line, loads the owning partition, and moves to the result without waiting for exact page indexing.
- Previous/Next search checks the current partition first and can move immediately when the next match is already loaded.
- When the total count is unknown, the counter may show `n / …` first and then update to `n / total` after a background count pass.
- Added **Nth / n번째** search to jump directly to a specific occurrence.
- Search-result jumps use a safe reveal position below the top title/toolbar and above lower Find UI.

## EPUB and Word Find / fixed-layout EPUB

- Word Find-follow correction remains rolled back. Word same-page Previous/Next uses native WebView `findNext()` / `FindListener` behavior only.
- EPUB and Word Find avoid JavaScript/DOM marker layout edits and rely on native WebView Find behavior.
- Word and normal EPUB Find use an inline toolbar-level panel so controls do not float over the WebView search target.
- Fixed-layout EPUB Find uses an overlay panel so opening Find does not shrink the WebView or reflow fixed-size pages downward.
- Fixed-layout EPUB pages are detected through fixed-layout metadata / numeric viewport data, kept out of reader-theme reflow CSS, and centered as fixed-size pages during normal reading.
- While fixed-layout EPUB Find is open, the fixed page top is temporarily moved below the Find overlay; closing Find restores normal centered placement.
- EPUB/Word bottom toolbar stays fixed while the soft keyboard is open.
- EPUB/Word double-tap reset was stabilized so app reset handling does not conflict with WebView native double-tap zoom.

## PDF zoom and pan behavior

- Corrected PDF zoom-reset scope: vertical continuous scroll keeps the current zoom, while horizontal page-swipe mode resets zoom to `1.0` when moving to another page.
- Rolled back PDF page-navigation scroll-offset clearing, so page movement no longer forces a top-left snap.
- Increased zoomed PDF in-page panning acceleration for both horizontal and vertical movement in horizontal swipe mode.
- Kept the horizontal page-turn threshold unchanged.

## Rounded popup and Settings cleanup

- Applied rounded popup styling to the display-rule, actual-file edit, auto-page-turn, delete-confirmation, and settings-reset dialogs.
- Added **Reset settings** for restoring reader/app preferences while keeping bookmarks, reading positions, recent files, folder shortcuts, TXT Display Rules, custom themes, and PIN lock.
- Settings backup/export includes TXT Display Rules through the existing settings import/export path.

## Kept from 2.1.1 / 2.1.0 behavior

- Large TXT active rendering remains based on fixed 4,000-logical-line partitions.
- TXT pagination keeps the status-bar-off content spacing model so status-bar visibility does not change total page count.
- Backup bookmark editing keeps the `bookmarkEdits.beginner` / `bookmarkEdits.developer` structure.
- PDF, EPUB, and Word/DOCX remain separate document-viewer paths.
- The package identity remains `com.textview.reader`; legacy-package users still need backup/export/import migration.

## Build toolchain metadata

- README / changelog / patch notes now match the project build files: Android Gradle Plugin `9.1.1` and Gradle wrapper `9.3.1`.
- Android app metadata remains `versionCode 2122` and `versionName "2.1.2b"`.

## Verification note

ZIP integrity and Markdown structure were checked. Full Gradle verification should be run locally in Android Studio or another network-enabled environment if the Gradle wrapper needs to download Gradle.
