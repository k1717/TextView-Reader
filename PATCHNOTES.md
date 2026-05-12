## TextView Reader 2.0.8

This release adds EPUB-only layout boundary controls in Settings while keeping the 2.0.6 TXT, PDF, toolbar-folding, safe-area, loading-window, and zoom-focus changes unchanged.

### EPUB Reader

- Added **Settings > EPUB layout** for EPUB files.
- EPUB layout now controls separate left, right, top, and bottom reading boundaries.
- Each boundary slider supports **0px to 240px** and moves in **5px** steps.
- EPUB top/bottom boundaries now work as viewer-edge boundaries, matching the TXT boundary behavior instead of acting as HTML body padding.
- Changes apply after returning from Settings to the EPUB viewer.
- Fixed EPUB/Word page content so changing the reader theme in Settings updates the already-open WebView page after returning to the viewer, while preserving the current page/scroll position.
- Adjusted EPUB bottom boundary behavior so the bottom toolbar is not pushed by the boundary value; the viewer only adds extra bottom margin when the requested boundary exceeds the visible toolbar height.
- Added EPUB **More** menu controls for **Increase Font**, **Decrease Font**, and **Reset Font Size**; these adjust the EPUB WebView text zoom and refresh the current page.
- Moved shared **Font Size** and **Line Spacing** controls above the **TXT layout** Settings header so the TXT layout section only contains TXT-specific boundary controls.
- Backup export/import now saves and restores app settings plus custom reading themes together with bookmarks and reading positions. Lock PIN data is excluded from the plain JSON backup for safety.
- Custom reading themes can now be long-pressed and removed through a rounded theme-options popup.
- Fixed custom-theme option popups so they open directly in their final centered position instead of first appearing on the right and then snapping into place.
- Moved the custom-theme options and delete-confirmation popups slightly below center while keeping the no-jump rounded-window behavior.
- Centered custom-theme popup button labels inside their rounded button boxes.
- Centered main file-operation popup action labels inside their rounded boxes, including shortcut and clear/remove confirmation actions.
- Fixed the Theme Editor **Save Theme** button so the filled button uses a high-contrast text color in light/main-white mode.

### TXT Viewer UI

- Updated the TXT bottom overlay to better match the document viewer control style.
- Kept the TXT-specific Find, Page, Bookmark, Settings, and More controls.
- Kept the slider/button area continuous without adding a horizontal separator between the middle control area and the bottom button row.
- Removed the remaining horizontal divider from TXT popup bottom action areas so Page/More/File Info-style windows use the same continuous card surface as the PDF/EPUB/Word viewer dialogs.
- Matched TXT popup outer borders to the PDF/EPUB/Word viewer dialogs by using the same thin theme-derived outline instead of the heavier TXT-only border.
- Matched TXT popup text tone to PDF/EPUB/Word dialogs by using the active reader theme text color instead of a generic black/white readable fallback.
- Changed the TXT **Go to Page** popup action from **Close** to **Go**.
- Reordered bookmark popups in TXT, PDF, EPUB, and Word viewers so the main bookmark list appears above the add-bookmark button.
- Changed bookmark hints from inline collapse/expand text to a separate small rounded popup in TXT, PDF, EPUB, and Word viewer bookmark popups.
- Moved TXT toolbar-triggered popups and their More-menu subsections slightly lower while keeping bookmark windows at their existing bookmark position.
- Matched PDF/EPUB/Word toolbar-triggered Find, More, File Info, and font popup positions to the same bottom offset used by the Go to Page popup.
- Kept the Font picker horizontal separation lines but changed TXT/EPUB/Word font-page outer borders and row-card borders to 1px for a thinner, less heavy outline.
- Added **Reset Font Size** to the TXT viewer **More** popup.
- Hardened TXT bottom-toolbar touch handling for e-ink readers so buttons such as **More** consume their own tap events reliably.
- Expanded the volume-key page-turn setting to also catch common e-reader hardware page keys before the system treats them as volume/media events.
- Applied the volume-key / hardware page-turn setting to PDF, EPUB, and Word viewers as well as TXT.
- Removed the long-press/hold ripple animation from viewer bottom toolbar buttons while keeping the e-ink tap fallback active.
- Changed remaining long-duration Toast messages to short Toast messages across viewer and Settings flows.
- Fixed TXT viewer theme return behavior so normal Activity recreation can restore the already-loaded text from memory instead of showing the loading flow and reading the file again.

### Word/DOCX Reader

- Word/DOCX padding is unchanged.
- The EPUB layout setting only affects EPUB files.

### PDF / EPUB / Word More Popup

- Added **Open File** next to **Close** in the PDF, EPUB, and Word **More** popups.
- The button returns to the file browser from the current viewer without changing the existing toolbar controls.

### Version Metadata

- Updated Android version metadata to `versionCode 208` and `versionName "2.0.8"`.
- Kept the 2.0.6 changelog section intact below the new 2.0.8 entry.

### 2.0.8 optimization cleanup
- Enabled release resource shrinking to reduce packaged APK resources.
- Removed stale PdfBox ProGuard rules because the app now uses Android PdfRenderer.
- Avoided sorting all recent-file states when the UI only needs to know whether recent files exist.
- Replaced large-TXT preview stream skipping with direct file seeking.
- Avoided repeated full font-folder rescans when the font list has already been scanned in the current app session.
- Standardized all app Toast messages to short duration.

