## TextView Reader 2.0.5

This release focuses on bookmark-window polish, TXT dialog/card styling, sort-popup stability, and persistent multi-font handling compared to 2.0.4.

### Bookmark UI

- Added long-press deletion for bookmark folders in TXT, PDF, EPUB, and Word bookmark windows.
- Long-pressing a bookmark folder deletes all bookmarks inside that folder after confirmation.
- Folder deletion does **not** delete the original document file.
- Applied the app's rounded/bordered confirmation dialog style to bookmark-folder deletion.
- Viewer bookmark dialogs now keep a stable list area height so adding the first bookmark does not resize the whole window.
- Empty bookmark windows keep the middle area blank instead of collapsing and moving the Close button.
- Reduced the gap between the bookmark title and bookmark status line in the TXT viewer.
- Switched the order of bookmark hint and add-bookmark button so the hint appears before **Add current bookmark**.

### TXT Dialog and Card Styling

- Updated TXT bookmark folder rows to better match the PDF/EPUB/Word rounded card tone.
- Added slight spacing between bookmarks inside expanded folders.
- Retuned TXT bookmark chips, folder cards, current-file cards, and bookmark rows to use a consistent theme-derived tone.
- Updated the TXT **More** menu to use rounded card-style rows.
- Applied matching rounded card tone to TXT **More** subsections, including Font-related windows.
- Matched the TXT Font and Add Font window border width with the other stable reader dialogs.
- Updated the **Add current bookmark** button tone and rounded-card styling across TXT, PDF, EPUB, and Word.

### Font Management

- Added support for keeping multiple user-added fonts in the compact font picker.
- Added fonts now remain visible after returning from the viewer and reopening the font picker.
- Long-pressing an added font in the normal **Font** picker removes it from the compact picker list.
- The **Add font / All system fonts** window does not remove fonts; it only adds fonts to the compact picker.
- Removed fonts can be added again later from the system/all-font list.
- If the currently selected added font is removed, the viewer falls back to the default font.

### Popup and Dialog Stability

- Stabilized the main Sort popup so it does not visibly shift after first appearing.
- Reused the same bottom-positioned rounded dialog behavior used by other fixed windows.
- Disabled unstable popup window animation paths that caused hard-landing or position movement.

### Build and Version

- Updated app version metadata to:
  - `versionCode 205`
  - `versionName "2.0.5"`
