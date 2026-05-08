# Changelog

## 2.0.1 - 2026-05-07

### Final 2.0.1 source refresh

- Kept the public release version at `versionName "2.0.1"` and `versionCode 201`.
- Updated the package from the latest stable build while keeping the GitHub upload source-only and privacy-safe.
- Added initial language behavior that follows the Android system locale until the user explicitly chooses English or Korean.
- Added disposable TXT page/index cache bookkeeping for large TXT files.
  - The cache records file path hash, file length, modified time, layout signature, last access time, and access count.
  - Cleanup uses stale-entry removal plus LFU/LRU-style shrinking.
  - Cleanup is limited to generated cache data under app cache storage.
  - It does **not** delete bookmarks, reading history, or saved reading position.
- Added `PageIndexCacheManager` and TXT reader integration for best-effort large-file cache access recording.
- Updated `README.md` and instruction documents to describe the latest 2.0.1 behavior.

### Documentation and release hygiene

- Fixed and reformatted `README.md` so GitHub renders headings, lists, tables, code blocks, build instructions, UI map, and repository hygiene notes correctly.
- Updated `CHANGELOG.md` with a proper 2.0.1 entry covering current reader, file-browser, cache, language, and documentation updates.
- Updated `ANDROID_STUDIO_SETUP_FOR_BEGINNERS.md` for the current app behavior, first-test checklist, and correct project-root opening instructions.
- Updated `BUILD_FIX_NOTES.md` with the current Gradle/SDK/JDK configuration and clean repository rules.
- Updated `GITHUB_UPLOAD_NOTES.md` with the exact safe upload flow and warnings about GitHub web upload not deleting obsolete files.
- Updated `PRIVACY.md` to describe local-only data, permissions, exported bookmark JSON, and the optional PIN-lock limitation.
- Updated `CONTRIBUTING.md` to reflect the current app structure, viewer-stack behavior, cache-safety rules, and documentation expectations.
- Kept the MIT license under `Copyright (c) 2026 k1717 aka Delphinium`.
- Expanded `.gitignore` to exclude local IDE folders, Gradle caches, build outputs, APK/AAB files, signing material, environment/secrets files, logs, captures, and heap dumps.
- Cleaned this public source package by excluding `.idea/`, `.gradle/`, `app/build/`, root `build/`, `local.properties`, generated APK/AAB files, signing keys, `.env` files, and other private/generated files.

### Main file browser

- Home page recent files default to **recently read first** instead of alphabetical or numeric order.
- Added a compact sort icon beside the file search field.
- Sort control works on both the home recent-files page and normal folder browsing.
- Added a separate recent-home sort preference so the recent page can keep **Recently read** order while folders use normal file sort modes.
- Kept drawer storage shortcuts fixed and made only recent-folder entries scrollable.
- Cached external storage detection to reduce drawer rebuild overhead.
- Improved main search and keyboard resize behavior.

### Reader and document viewer

- Reused viewer activities with `singleTop` / `onNewIntent` handling to avoid repeated viewer stacks when opening a new file.
- Improved lifecycle cleanup for handlers, executor services, PDF bitmaps/renderers, WebView resources, and selection state.
- Refined TXT/document selection handle drawables.
- Kept Word/DOCX rendering on WebView after testing showed selectable-TextView rendering degraded document layout.
- Improved DOCX/Word page behavior:
  - long-line wrapping inside the page border;
  - per-page vertical scrolling preserved;
  - left/right swipe page movement;
  - stale native selection handles cleaned after selected text scrolls away.

### UI polish

- Continued cleanup of rounded dialog/menu styling.
- Applied reader-theme colors across PDF/document viewer controls where applicable.
- Updated the README with a first-use UI map showing where major functions are located.

## Earlier development notes

Earlier internal packages contained local Gradle/IDE/build artifacts and documentation drafts. The 2.0.1 package is the cleaned public source snapshot intended for GitHub upload.
