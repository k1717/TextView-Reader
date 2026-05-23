# TextView Reader Patch Notes

## 2.1.5 GitHub upload patch

This 2.1.5 package is prepared for GitHub upload. It uses Android metadata `versionCode 2150` and `versionName "2.1.5"`.

### Main file-browser delete confirmation

- The main file-browser long-press Delete action now opens a compact rounded reconfirmation dialog before deleting a file or folder from storage.
- The dialog is narrower than the earlier full-width version and uses stacked actions: **Delete** on top and **Cancel** below.
- The dialog includes the selected file/folder name and a warning that deletion cannot be undone.
- After deletion, the current screen state is refreshed: Recent/home reloads recent files, search results rerun the active query, folder browsing reloads the current directory, and drawer/recent-folder state is rebuilt.
- Deleted folders are removed from recent-folder, folder-shortcut, and last-directory state where applicable.

### Bookmark backup page-model summary

- Backup export includes `largeTxtBookmarkPageModelSummary` for current, stale/unknown, and not-yet-recalculated TXT bookmark page labels.
- Backup export does not open every TXT file to force page/total recalculation after a partition-mode change.
- TXT bookmark edit rows keep status fields including `cachedPageModelMatchesCurrentPartitionMode` and `requiresOpenFileRebuildForCurrentPageModel`.
- Stale or unknown TXT `Page X / Y` values are marked as display metadata and refresh after the relevant TXT file is opened and exact anchors rebuild.
- Bookmark location remains preserved through `charPosition`, line, and surrounding anchor text.

### Background memory handling

- TXT and PDF viewers use delayed background memory trimming instead of clearing heavy state immediately on a brief app switch.
- A 420-second grace delay protects short app switches, Settings visits, and quick Home/app-switcher returns.
- Returning before the delay cancels the trim and keeps the current view warm.
- If the app remains hidden beyond the grace window, or Android reports stronger memory pressure such as `TRIM_MEMORY_BACKGROUND` or `TRIM_MEMORY_RUNNING_LOW`, the viewer trims heavy state more aggressively.
- TXT saves the current character/page position and nearby anchor text, releases loaded text snapshots, large-TXT partition caches, exact page anchors, prefetch/search state, and in-flight generation tokens, then lazy-reloads the same file at the saved position on return.
- PDF recycles the current single-page bitmap and clears vertical-continuous bitmap caches while keeping the PDF renderer itself available for faster redraw.

### Build metadata

- README, changelog, and patch notes match the project build files: Android Gradle Plugin `9.1.1` and Gradle wrapper `9.3.1`.
- Android app metadata is `versionCode 2150` and `versionName "2.1.5"`.
