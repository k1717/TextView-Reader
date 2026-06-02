# TextView Reader 2.2.4 GitHub Upload Notes

Use this package as the GitHub source upload for **TextView Reader 2.2.4**.

## Release metadata

- `versionCode 2240`
- `versionName "2.2.4"`
- Package ID: `com.textview.reader`
- Project license: Apache License 2.0
- First-party copyright: Copyright 2026 k1717 aka Delphinium
- Third-party notices: keep `THIRD_PARTY_NOTICES.md` with release materials
- Required root release files: `LICENSE`, `NOTICE`, `THIRD_PARTY_NOTICES.md`, `CHANGELOG.md`, `PATCHNOTES.md`, `README.md`

## Suggested release title

`TextView Reader 2.2.4`

## Suggested short release description

TextView Reader 2.2.4 expands archive management and extraction support, adds queued ZIP creation, strengthens file-operation progress/confirmation behavior, refreshes dependencies while keeping SDK-35 compatibility, adds a lightweight color palette picker, smooths image-viewer handoff, and cleans TXT page-position labels.

## Release highlights

- Compress actions now add queued ZIP-creation tasks instead of running immediately; queued copy/move/extract/compress tasks share the pending-actions menu.
- Archive support includes RAR/CBR, ALZ, EGG with AZO/LZMA, standard 7z/CB7 split chains, ZIP/CBZ/ZIPX recognition, TAR-family archives, and single-file compressor streams.
- RAR4/RAR3 complex extraction attempts Junrar fallback where possible. RAR5 compressed/solid/encrypted extraction is optional through `app/libs/unrar5j-v1.0.3.jar` and remains cleanly unsupported when the jar is absent.
- 7z/CB7 split chains such as `.7z.001` / `.7z.002` are opened through a concatenated seekable channel without building a temporary combined archive.
- Archive extraction management now includes stricter preview-cache keys, path traversal rejection, cooperative progress/cancel checkpoints, background password preflight, backup/restore overwrite replacement, and conservative extraction/cache guards.
- The pending queue can be inspected while a background operation is active; execution/cancel/clear controls remain locked until the active worker finishes.
- File-operation progress now keeps item/file progress separate from folder counters and resets byte progress per payload.
- Extraction, paste/copy/move, and delete confirmation dialogs sit closer to screen center.
- Custom main-theme and reading-theme color editors now include a lightweight shader-based palette picker beside HEX/RGB input.
- Image viewer launch from the file list or archive image preview uses a short fade/scale transition.
- TXT page labels show `current / total` at exact page starts and `current (line-in-page) / total` only for mid-page positions.
- Build/runtime/test dependencies were refreshed while preserving `compileSdk 35` and `targetSdk 35`.

## Known boundaries to mention if asked

- Archive creation is plain ZIP only.
- RAR creation/compression is intentionally unsupported.
- RAR5 compressed extraction requires the optional local unrar5j jar; RAR5 split/multi-volume is not guaranteed.
- Encrypted/split/solid EGG and broad legacy ALZ variants remain unsupported.
- ZIPX extraction depends on method support; uncommon methods such as ZIP method-95/XZ are not guaranteed.
- 7z extraction depends on Apache Commons Compress method coverage, and missing/gapped split-volume chains are rejected.

## Upload checklist

- Upload the full source package, not only the `app/` directory.
- Keep `gradle/wrapper/gradle-wrapper.jar` and `gradle/wrapper/gradle-wrapper.properties` in the repository.
- Include `LICENSE`, `NOTICE`, and `THIRD_PARTY_NOTICES.md` with source and binary release materials.
- If enabling optional RAR5 fallback, place `unrar5j-v1.0.3.jar` in `app/libs/` before building and keep its Apache-2.0 notice.
- Validate locally with `./gradlew :app:assembleRelease` or `gradlew.bat assembleRelease` before tagging.
