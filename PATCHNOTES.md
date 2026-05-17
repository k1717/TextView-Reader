# TextView Reader 2.1.1 Patch Notes

### 1. Large TXT paging became partition-based

2.1.1 changes the large-TXT active render window to fixed **4,000-logical-line partitions**. Partition boundaries are treated as internal cache boundaries, not user-visible reading boundaries.

Functional result:

- smoother page movement near large-TXT partition seams;
- fewer duplicated/skipped-line risks around partition changes;
- better bookmark and page-target ownership by line partition;
- in-place partition switching when the next partition is already cached or can be loaded directly.

### 2. Large TXT exact page indexing was added

2.1.1 builds an exact background page-anchor index for large TXT files.

Functional result:

- page-count status becomes exact after indexing;
- current-page lookup is faster;
- toolbar slider and Go to Page can target real page anchors;
- exact Go to Page is blocked from silently using estimates while the exact index is not ready.

### 3. TXT page-jump UI is more stable

2.1.1 keeps the toolbar slider and label on the selected target page during async jumps. The loading panel is now rounded, compact, theme-aware, and used for slow uncached large-TXT slider, Go to Page, and bookmark jumps.

Functional result:

- no old-page snap-back while a selected page is loading;
- no permanent leftover loading panel;
- no loading panel for fast cached partition jumps.

### 4. Large TXT end-of-file behavior was corrected

2.1.1 ignores terminal blank-line filler in page-index calculations and normalizes final-page reporting.

Functional result:

- less chance of an artificial blank final page;
- final visible text reports the true final page.

### 5. Bookmark backup editing was redesigned

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

### 6. TXT bookmark restoration gained anchor context

2.1.1 passes nearby TXT anchor text when opening bookmarks.

Functional result:

- bookmark jumps are more robust after layout changes, file rebinding, or large-TXT partition movement.

### 7. PDF page movement was polished

2.1.1 improves original-size PDF swipe sensitivity, removes fast-operation spinner flashes for page/zoom redraws, and centers the new page when changing pages while zoomed in.

Functional result:

- original-size PDF pages turn with shorter swipes;
- zoomed page turns no longer start at the upper-left corner;
- quick redraws do not show the small centered loading dot.

### 8. EPUB direction and transition settings were added/refined

2.1.1 adds EPUB page-direction and transition-effect settings. Right-to-left mode is labeled as Japanese-style reading.

Functional result:

- EPUB swipe direction can match left-to-right or Japanese-style right-to-left books;
- slide animation direction follows the selected reading direction;
- transition effect can be disabled.

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
