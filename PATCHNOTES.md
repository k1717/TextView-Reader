# TextView Reader 2.1.2 Patch Notes

This source package keeps Android metadata at `versionCode 212` and `versionName "2.1.2"`.

## Main browser parent-folder button

- Added a parent-folder button to the main file-browser path bar.
- The button is placed on the **right side** of the path bar.
- Button text uses a left arrow: **← Parent folder** / **← 상위 폴더로**.
- Tapping it moves to the current folder's parent without using Android Back.
- At a storage root, it returns to the Recent/home view.
- The button is hidden on the Recent screen and search-result screen.

## TXT Display Rules

- Added viewing-only TXT masking/replacement rules.
- Normal TXT Display Rules do not modify the source file.
- Rules support enabled/disabled state, case-sensitive matching, plain-text matching, regular-expression matching, all-file scope, and current-file-only scope.
- Added rule-source labeling so the list can show which file a rule was made from.
- Added rule ordering with up/down controls.  Up/down does not reload the active viewer by itself, but rule order affects overlapping replacements.
- Added quick rule creation from the TXT viewer through **More > Add display rule** and long-press word prefill.
- Rule changes from the TXT rule manager apply to the viewer after the rule/add window closes, keeping the manager responsive during editing.
- Display rules are applied before pagination, partition rendering, exact page indexing, search, and bookmark positioning.

## Edit Actual TXT File

- Added **Edit Actual TXT File** below **TXT Display Rules** in Settings when Settings is opened from a TXT viewer.
- The action permanently applies all enabled rules that apply to the current TXT file.
- Users can choose either:
  - overwrite the original TXT file, or
  - write to `originalname_edited.txt`.
- Copy mode overwrites the same `*_edited.txt` target instead of creating repeated numbered copies.
- Original mode reloads and fully repaginates the opened viewer after the write succeeds.
- The destructive flow uses rounded dialogs, colored warning boxes, a second **Are you sure?** step, and an emphasized **There is no turning back.** warning.
- Physical writes use a same-folder temporary file and replacement step to reduce the risk of partially written output.
- Large-file warnings and an `OutOfMemoryError` fallback were added because actual-file editing must read, transform, and rewrite the full file.

## TXT Auto Page Turn

- Added low-power automatic page turning for TXT reading.
- The interval is entered in seconds per page.
- Auto Page Turn advances by one full page at each interval.
- It stops at the final page, when the viewer leaves the foreground, or when the user manually scrolls/moves the page.

## Large TXT page movement fallback

- Large TXT page movement no longer waits for exact full-page indexing.
- If exact anchors are still building or fail, Go to Page / slider movement falls back to an estimated 4,000-logical-line partition jump.
- Exact page anchors are still used automatically after background indexing is ready.
- Exact-index failure is tracked separately instead of being treated as endless calculation.
- User-facing wording uses **대략적인 위치** for approximate movement.

## Large TXT chunked exact indexing

- Replaced the memory-heavy full-file exact-index layout path with chunked line-based exact indexing.
- Large TXT still tries to complete an accurate full page count even when it takes a long time.
- This targets high-line-count TXT files, such as 30MB-class UTF-8 files with hundreds of thousands of short CRLF lines.
- While exact indexing runs, page movement remains usable through the 4,000-line fallback.
- When chunked exact anchors finish, the viewer updates the total page count from estimated to exact.

## Large TXT final-page fixes

- Added a dedicated final-page path for partitioned TXT files.
- Final-page movement loads/uses the real EOF partition instead of relying only on a global trailing-blank anchor.
- Fixed a case where manual scroll could reach the final page but tap/page-down stopped at the previous page.
- Final-page jumps can clamp to the physical visual EOF of the last partition when the final page is only a small EOF tail below the last anchor.
- Final-page placement is applied before first draw in the final-page path to avoid anchor-page-to-EOF flicker.
- If the final partition is already active, the viewer moves directly to visual EOF instead of reloading/rebuilding the partition, reducing the final-page transition delay.

## TXT body search and nth occurrence

- Large-TXT body search no longer searches only the currently loaded 4,000-line partition.
- Search scans the display-rule-applied TXT stream, finds the matching logical line, loads the owning partition, and moves to the result.
- Search does not need to wait for exact page indexing to finish.
- Search result highlighting maps global large-TXT match positions into the active partition before drawing.
- Large-TXT search uses its own background executor so a long exact-page-index build does not block find-next/find-previous requests.
- Added an optional match-number field to the TXT body search window.
- Users can enter a number and tap **Nth / n번째** to jump directly to that occurrence.
- Nth search works in both normal TXT mode and large TXT partition mode.
- Next/Previous search behavior is unchanged when the nth field is empty.

## Rounded popup and Settings cleanup

- Applied rounded popup styling to the new display-rule, actual-file edit, auto-page-turn, delete-confirmation, and settings-reset dialogs.
- Added **Reset settings** for restoring reader/app preferences while keeping user data such as bookmarks, reading positions, recent files, folder shortcuts, TXT Display Rules, custom themes, and PIN lock.
- Settings backup/export includes TXT Display Rules through the existing settings import/export path.

## Kept from 2.1.1 / 2.1.0 behavior

- Large TXT active rendering remains based on fixed 4,000-logical-line partitions.
- TXT pagination keeps the status-bar-off content spacing model so status-bar visibility does not change total page count.
- Backup bookmark editing keeps the `bookmarkEdits.beginner` / `bookmarkEdits.developer` structure.
- PDF, EPUB, and Word/DOCX remain separate document-viewer paths.
- The package identity remains `com.textview.reader`; legacy-package users still need backup/export/import migration.

## Verification note

ZIP integrity and Markdown structure were checked. Full Gradle verification should be run locally in Android Studio or another network-enabled environment if the Gradle wrapper needs to download Gradle.
