# Changelog

## Unreleased

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
- Updated README with a first-use UI map showing where major functions are located.

### Repository

- Updated README, setup guide, build notes, privacy notes, contribution notes, changelog, and GitHub upload notes.
- Added MIT license with `Copyright (c) 2026 k1717 aka Delphinium`.
- Expanded `.gitignore` for public GitHub hygiene.
- Excluded local IDE files, Gradle caches, build outputs, signing keys, APK/AAB files, and machine-specific paths.
- Documented that accidental root-level Android duplicates such as `java/`, `res/`, root `AndroidManifest.xml`, and root `ic_launcher-playstore.png` should be deleted.
