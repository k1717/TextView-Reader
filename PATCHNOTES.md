# TextView Reader 2.0.4

This release prepares the repository for public GitHub submission and adds the latest bookmark, theme-refresh, and documentation cleanup on top of the 2.0.3 EPUB/Word/PDF improvements.

## Repository / privacy cleanup

- Updated app metadata to `versionCode 204` and `versionName "2.0.4"`.
- Updated `README.md` for the current TXT/PDF/EPUB/Word reader workflow.
- Added this release-ready patch-notes file for GitHub Releases.
- Strengthened `.gitignore` for Android generated files, IDE state, local machine configuration, packaged APK/AAB outputs, signing files, and secret/config files.
- Removed private/generated files from the source package:
  - `.gradle/`
  - `.idea/`
  - root `build/`
  - `app/build/`
  - generated APKs
  - `local.properties`
- Reduced release Logcat exposure by removing path/file-name-bearing internal utility logs.

## Bookmark UI and stability

- Main bookmark folders default to collapsed/shrunk.
- Bookmark folder expand/shrink remains fast.
- Bookmark edit dialogs use a more rounded bordered custom style.
- TXT bookmark memo edit now has **Cancel / Clear memo / Save**.
- PDF bookmark memo edit now has **Cancel / Clear memo / Save**.
- EPUB/Word bookmark memo edit now has **Cancel / Clear memo / Save**.
- Main bookmark delete/edit dialogs use the stable custom dialog path to reduce hard-edge and hard-landing visual glitches.
- Bookmark opening uses shared navigation with null/empty file-path protection.

## Theme refresh fix

- Viewers reload theme state after returning from Settings or the theme editor.
- TXT, PDF, EPUB, and Word More dialogs refresh active theme colors before drawing.
- PDF More dismisses stale dialog instances before opening Settings or File Info.
- Active theme saving uses synchronous commit to avoid immediate-return race behavior.

## EPUB / Word viewer changes carried from 2.0.3

- Added a bottom-bar **Find/Search** button next to **Previous / Next**.
- Added document search with match counter, previous/next navigation, and page-to-page wrapping.
- Improved search input cursor/selection-handle behavior.
- Updated font selection to follow the TXT reader font-window structure.
- Added **Default font** support for EPUB-declared fonts and detected Word/DOCX fonts.
- Removed duplicate More-menu zoom buttons while preserving double-tap reset/original-size behavior.
- Improved zoomed edge-swipe page turning to avoid accidental same-drag page jumps.

## PDF viewer changes carried from 2.0.3

- Improved vertical continuous-scroll behavior.
- Fixed blank-page cases in vertical PDF scrolling.
- Improved zoom behavior in vertical PDF mode.
- Added and tuned horizontal panning for zoomed PDF pages in vertical mode.
- Improved bitmap/cache handling to reduce stale blank renders and memory pressure.
- Preserved the existing vertical Go-to-page behavior.

## Popup / dialog polish carried from 2.0.3

- Word/EPUB/PDF popup widths now match the TXT viewer style except bookmark dialogs.
- More windows and subsection windows such as File Information, Page Move, Font Picker, and Full System Font List were made more consistent.
- Popup hard-landing / diagonal-drop behavior was reduced.
- Transparent popup background issues were fixed.
- Main file-browser sort radio selection bubble placement was improved.
