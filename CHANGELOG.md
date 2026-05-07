# Changelog

## Unreleased

### Main file browser

- Home page recent files default to **recently read first** instead of alphabetical or numeric order.
- Added a compact sort icon beside the file search field.
- Sort control works on both the home recent-files page and normal folder browsing.
- Added a separate recent-home sort preference so the recent page can keep **Recently read** order while folders use normal file sort modes.
- Kept drawer storage shortcuts fixed and recent-folder entries scrollable.
- Cached external storage detection to reduce drawer rebuild overhead.

### Reader and document viewer

- Reused viewer activities with `singleTop`/`onNewIntent` handling to avoid repeated viewer stacks when opening a new file.
- Improved lifecycle cleanup for handlers, executor services, PDF bitmaps/renderers, and WebView resources.
- Refined TXT/document selection handle drawables.
- Kept Word/DOCX rendering on WebView after testing showed selectable-TextView rendering degraded document layout.
- Improved DOCX/Word page behavior:
  - long-line wrapping inside the page border;
  - per-page vertical scrolling preserved;
  - left/right swipe page movement;
  - stale native selection handles cleaned after selected text scrolls away.

### UI polish

- Improved main search/keyboard resize behavior.
- Continued cleanup of rounded dialog/menu styling.
- Applied reader-theme colors across PDF/document viewer controls where applicable.

### Repository

- Updated README, setup guide, build notes, privacy notes, contribution notes, changelog, and GitHub upload notes.
- Added MIT license with `Copyright (c) 2026 k1717 aka Delphinium`.
- Expanded `.gitignore` for public GitHub hygiene.
- Excluded local IDE files, Gradle caches, build outputs, signing keys, APK/AAB files, and machine-specific paths.
