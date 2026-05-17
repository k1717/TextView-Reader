# TextView Reader 2.1.1 Patch Notes

This package is the GitHub-submittable 2.1.1 source snapshot. These notes summarize the final functional difference from the uploaded 2.1.0 package, not every intermediate hotfix. The complete previous-version history remains in `CHANGELOG.md`.

## Functional difference from 2.1.0

### 1. Large TXT paging is partition-based but continuity-safe

2.1.1 changes large-TXT active rendering to fixed **4,000-logical-line partitions**. Partitions are internal cache/render windows, not user-visible page boundaries.

Functional result:

- large files can be rendered through smaller active text windows;
- partition handoff uses the next-page anchor instead of blindly jumping to the next 4,000-line boundary;
- the configured page-overlap setting is respected;
- no extra duplicate display beyond the user-selected overlap is intended at partition seams;
- skipped displayed content at partition seams is avoided by coverage-first handoff.

### 2. Large TXT exact page indexing was added and hardened

2.1.1 builds a background exact page-anchor index for large TXT files.

Functional result:

- page-count status becomes exact after indexing;
- current-page lookup is faster;
- toolbar slider, Go to Page, page keys, and bookmark jumps can target real page anchors;
- exact Go to Page is blocked from silently using estimates while the exact index is not ready;
- stale exact-index jobs are discarded if layout geometry, font, overlap, status-bar spacing model, or file metadata changes before indexing finishes.

### 3. Fast page movement is more stable

2.1.1 prevents temporary partition estimates from overwriting the visible global page/total during partition swaps.

Functional result:

- fast backward paging near a partition boundary should not jump from a high page number to an earlier partition estimate;
- total page count is preserved during active partition swaps instead of being recomputed from the temporary active partition;
- rapid page taps are queued through exact anchors when safe, and unsafe stacked reloads are blocked while the exact index is unavailable.

### 4. Status-bar visibility no longer changes TXT page count

2.1.1 uses status-bar-off top spacing as the canonical TXT pagination geometry.

Functional result:

- Android status-bar on/off should not change TXT total page count;
- the TXT page indicator is visually moved one reader text row lower so the stable spacing does not make it feel pinned to the top.

### 5. TXT page-jump UI is more stable

2.1.1 keeps the toolbar slider and label on the selected target page during async jumps. The loading panel is rounded, compact, theme-aware, and used for slower uncached large-TXT slider, Go to Page, and bookmark jumps.

Functional result:

- no old-page snap-back while a selected page is loading;
- no permanent leftover loading panel;
- no loading panel for fast cached partition jumps.

### 6. Bookmark backup editing was redesigned

2.1.0 exported a large `beginnerEditableBookmarks` section with repeated guide fields. 2.1.1 exports:

```json
"bookmarkEdits": {
  "beginner": [],
  "developer": []
}
```

Functional result:

- normal users edit only the beginner section;
- developers can repair raw position, anchors, identity, and migration metadata separately;
- English/Korean guidance is shorter and kinder;
- old 2.1.0 backup-edit fields still import correctly.

### 7. TXT bookmark restoration gained anchor context

2.1.1 passes nearby TXT anchor text when opening bookmarks.

Functional result:

- bookmark jumps are more robust after layout changes, file rebinding, or large-TXT partition movement.

### 8. PDF page movement was polished

2.1.1 improves original-size PDF swipe sensitivity, removes fast-operation spinner flashes for page/zoom redraws, and centers the new page when changing pages while zoomed in.

Functional result:

- original-size PDF pages turn with shorter swipes;
- zoomed page turns no longer start at the upper-left corner;
- quick redraws do not show the small centered loading dot.

### 9. EPUB direction and transition settings were added/refined

2.1.1 adds EPUB page-direction and transition-effect settings. Right-to-left mode is labeled as Japanese-style reading.

Functional result:

- EPUB swipe direction can match left-to-right or Japanese-style right-to-left books;
- slide animation direction follows the selected reading direction;
- transition effect can be disabled.

### 10. Large TXT memory/lifecycle hardening was added

2.1.1 clears large-TXT runtime state when the TXT reader is released and invalidates stale background generations during destruction.

Functional result:

- partition cache, pending prefetch markers, exact page anchors, queued page deltas, and partition-switch state are cleared on release;
- stale exact-index and partition-switch results cannot reapply after the viewer closes;
- background TXT reads use the application context where possible to reduce temporary Activity retention.

## Current metadata

- Android package/application ID: `com.textview.reader`
- Android namespace: `com.textview.reader`
- Java source package: `com.textview.reader`
- Android `versionCode`: `211`
- Android `versionName`: `2.1.1`
- Backup schema: `textview-full-backup-v9`
- Default backup filename format: `textview_backup_year_month_day_hour_minute_second.json`

## Migration note

Because the package identity already changed in 2.1.0, 2.1.1 follows the same migration rule: if coming from a legacy package build, export a TextView backup from the old app and import it in this app.

## Build note

Use Android Studio with JDK 17, compile SDK 35, and the included Gradle wrapper. In this sandbox, command-line Gradle verification cannot complete because the wrapper needs internet access to download Gradle.
