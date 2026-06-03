# TextView Reader 2.2.5 GitHub Upload Notes

Use this package as the GitHub source upload for **TextView Reader 2.2.5**.

## Release metadata

- `versionCode 2250`
- `versionName "2.2.5"`
- Package ID: `com.textview.reader`
- Project license: Apache License 2.0
- First-party copyright: Copyright 2026 k1717 aka Delphinium
- Third-party notices: keep `THIRD_PARTY_NOTICES.md` with release materials
- Required root release files: `LICENSE`, `NOTICE`, `THIRD_PARTY_NOTICES.md`, `CHANGELOG.md`, `PATCHNOTES.md`, `README.md`

## Suggested release title

`TextView Reader 2.2.5`

## Suggested short release description

TextView Reader 2.2.5 improves archive compatibility and file-browser responsiveness, adds direct comic/image archive opening into the image reader, unifies file-operation progress windows, and keeps the 2.2.4 archive, palette, TXT-reader, Apache-2.0, and dependency-refresh baseline.

## Release highlights

- ZIP extraction now uses Zip4j as the primary path and Apache Commons Compress as a non-encrypted fallback for methods Commons can decode with the bundled runtime, notably Deflate64, BZip2, XZ, and ZSTD.
- `com.github.luben:zstd-jni:1.5.7-9` is bundled so Commons Compress Zstandard support is available to release builds without a missing-class suppression.
- Encrypted ZIP entries remain on Zip4j; AES plus non-Zip4j methods such as AES+XZ still fail cleanly as unsupported.
- Archive QA notes keep `.Z`, real ESTsoft ALZ, and real ESTsoft EGG fixtures as explicit release-validation items rather than over-claiming from synthetic or host-side tests.
- Pending ZIP creation destination is resolved from the folder where the pending action is executed, so queued compression can be run from the intended destination folder.
- Comic/image archives can open directly from the main file list into the image reader without visibly passing through the archive-preview screen. The archive preview remains the fallback for no-image, password, unsupported, or explicit preview cases.
- Drawer shortcut folder taps use fast cache restore/background validation and earlier uncached load snapshots for better perceived response.
- Regular main-list file/folder action short-hold now triggers after 200 ms instead of the previous roughly 500 ms delay; multi-select hold now enters after 800 ms.
- Returning from an internal viewer preserves the current main-folder list and scroll state instead of reloading the folder every time; if the original folder changed while the viewer was open, the folder refreshes to clear stale rows and show newly generated files.
- Returning to already loaded folders restores cached adapter lists and RecyclerView state in both directions, including A -> B -> A and A -> B -> A -> B, when folder signature, sort mode, and hidden-file setting still match.
- Returning from screen off/on or Home/app-switcher does not rescan the visible browse folder when it is unchanged; changes still force a reload.
- Switching from a current-folder type filter such as General / Archive / PDF / IMG back to All restores the cached full-folder list when the folder did not change.
- Multi-select delete exits selection mode immediately after delete confirmation, keeping the normal toolbar and active progress button available while the background delete worker runs.
- Returning to the app refreshes toolbar progress/pending visibility so paused/backgrounded operations can be reopened without folder navigation.
- Progress windows for extraction, copy, move/cut, delete, and ZIP creation now use a common delete-style layout with stable file/folder rows, fixed right-side `(current/total)` count columns, and a fixed-size monochrome pause/resume icon.
- Archive extraction progress counts files against the current archive's full regular-file count; copy/move/delete/ZIP creation use recursive selected-workload totals.
- Folder counters stay visible for extraction, copy, and move/cut instead of collapsing to `(1/1)` during multi-selection.
- Folder information dialogs now calculate recursive folder size in the background instead of displaying an unreliable raw directory size.
- Color-palette dialogs use the same rounded themed button style as the rest of the app.
- Long main-list file/folder names keep a small right-side inset so ellipsized text no longer sits against the row edge or reading-progress badge.
- Compress actions add queued ZIP-creation tasks instead of running immediately; queued copy/move/extract/compress tasks share the pending-actions menu.
- Archive support includes RAR/CBR, ALZ, EGG with AZO/LZMA, standard 7z/CB7 split chains, ZIP/CBZ/ZIPX recognition, TAR-family archives, and single-file compressor streams.
- RAR4/RAR3 complex extraction attempts Junrar fallback where possible. RAR5 compressed/solid/encrypted extraction is optional through `app/libs/unrar5j-v1.0.3.jar` and remains cleanly unsupported when the jar is absent.
- 7z/CB7 split chains such as `.7z.001` / `.7z.002` are opened through a concatenated seekable channel without building a temporary combined archive.
- Archive extraction management includes stricter preview-cache keys, path traversal rejection, cooperative progress/cancel checkpoints, background password preflight, backup/restore overwrite replacement, and conservative extraction/cache guards.
- The pending queue can be inspected while a background operation is active; execution/cancel/clear controls remain locked until the active worker finishes.
- Image viewer launch from the file list or archive image preview uses a short fade/scale transition.
- TXT page labels show `current / total` at exact page starts and `current (line-in-page) / total` only for mid-page positions.
- Build/runtime/test dependencies were refreshed while preserving `compileSdk 35` and `targetSdk 35`.
- Refactoring pass 1: browse-folder state preservation and cache logic now lives in `MainBrowseStateController` instead of `MainActivity`, preserving viewer-return, resume, drawer shortcut, filter-return, and A/B/A/B behavior.
- Refactoring pass 2: archive-browser list shaping, archive image sequence extraction preparation, direct archive-image routing, and archive create/extract naming/validation policies were split into focused helper classes while preserving current behavior.

## Known boundaries to mention if asked

- Archive creation is plain ZIP only.
- RAR creation/compression is intentionally unsupported.
- RAR5 compressed extraction requires the optional local unrar5j jar; RAR5 split/multi-volume is not guaranteed.
- Encrypted/split/solid EGG and broad legacy ALZ variants remain unsupported.
- ZIP/ZIPX extraction depends on method support. The Commons fallback helps non-encrypted Deflate64/BZip2/XZ/ZSTD-style entries, but AES plus non-Zip4j methods such as AES+XZ, LZMA/PPMd, and ABI/native codec load failures are not guaranteed.
- 7z extraction depends on Apache Commons Compress method coverage, and missing/gapped split-volume chains are rejected.

## Upload checklist

- Upload the full source package, not only the `app/` directory.
- Keep `gradle/wrapper/gradle-wrapper.jar` and `gradle/wrapper/gradle-wrapper.properties` in the repository.
- Include `LICENSE`, `NOTICE`, and `THIRD_PARTY_NOTICES.md` with source and binary release materials.
- If enabling optional RAR5 fallback, place `unrar5j-v1.0.3.jar` in `app/libs/` before building and keep its Apache-2.0 notice.
- Validate locally with `./gradlew :app:assembleRelease` or `gradlew.bat assembleRelease` before tagging.
