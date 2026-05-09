# Changelog

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

### Documentation and repository updates

- Updated `README.md` for version 2.0.2 and the new folder-shortcut, PDF-mode, TXT threshold, page-indicator, drawer, and search/filter behavior.
- Updated `CHANGELOG.md` with this 2.0.2 comparison against 2.0.1.
- Updated `PRIVACY.md` to mention locally stored folder shortcuts and generated cache metadata.
- Updated `CONTRIBUTING.md`, `ANDROID_STUDIO_SETUP_FOR_BEGINNERS.md`, `BUILD_FIX_NOTES.md`, and `GITHUB_UPLOAD_NOTES.md` for the current package.
- Updated Android version metadata to `versionCode 202` and `versionName "2.0.2"`.
- Kept the MIT license under `Copyright (c) 2026 k1717 aka Delphinium`.
- Cleaned the public source package by excluding `.idea/`, `.gradle/`, `app/build/`, root `build/`, `local.properties`, generated APK/AAB files, signing keys, `.env` files, and other private/generated files.

## 2.0.1 - 2026-05-07

### Final 2.0.1 source refresh

- Kept the public release version at `versionName "2.0.1"` and `versionCode 201`.
- Added initial language behavior that follows the Android system locale until the user explicitly chooses English or Korean.
- Added disposable TXT page/index cache bookkeeping for large TXT files.
- Added `PageIndexCacheManager` and TXT reader integration for best-effort large-file cache access recording.
- Updated README and instruction documents to describe the 2.0.1 behavior.

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
