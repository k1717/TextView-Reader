## TextView Reader 2.0.9

### TXT small-file row alignment

- Fixed very small TXT files whose content is shorter than the viewport so their first text row uses the same visual row grid as normal TXT files.
- This avoids the short-file case sitting slightly lower than normal files.
- The fix keeps very small TXT files as single-page content and does not add artificial paging behavior.
- Normal TXT paging, row anchors, and large-file behavior are unchanged.

### TXT page-boundary alignment

- Fixed a second-page-only TXT pagination case where page 2 could repeat page 1's last fully visible sentence after increasing the TXT bottom boundary, such as around 80px.
- The page-anchor builder now uses the actual visible layout top for each page, including the first-page top-pad compensation, so page 2 starts after the true last fully visible line of page 1.
- This keeps the existing line-boundary pagination logic and does not change normal page-turn controls.

### Main-screen long-press dialog positioning

- Fixed hard-landing behavior for main-screen long-press follow-up windows:
  - **Delete / 삭제**
  - **Rename / 이름 변경**
  - **File Info / 파일 정보**
- Moved the **Delete / 삭제** confirmation window slightly below center, while keeping it clearly above the **Rename / 이름 변경** window.
- Moved the **Rename / 이름 변경** window slightly upward from its previous bottom position.
- The rounded-window UI is preserved; this only stabilizes and refines first-open placement.


### Main-screen folder loading

- Improved folder-opening responsiveness from the drawer and main browser by moving directory scanning off the UI thread.
- Large folders now show a loading state while files are collected and sorted in the background.
- Added stale-load cancellation so tapping another folder/search/home before the previous folder scan finishes cannot overwrite the current view.
- Replaced DiffUtil animation for full folder switches with direct list replacement, which avoids expensive item-by-item comparison when opening a different directory.
- File opening, sorting choices, folder shortcuts, and drawer structure are unchanged.

