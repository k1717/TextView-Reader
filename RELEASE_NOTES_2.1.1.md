# TextView Reader 2.1.1

2.1.1 is a functional polish and stability release over the uploaded 2.1.0 GitHub source package. It keeps the same package identity and migration model introduced in 2.1.0. This file is the 2.1.1 release note only; `CHANGELOG.md` keeps the full previous-version history.

## Functional difference from 2.1.0

### Large TXT

- Uses fixed **4,000-logical-line active partitions** for large TXT rendering.
- Adds lookahead text after active partitions for smoother boundary rendering.
- Switches large-TXT partitions in place where possible instead of visibly reloading the file.
- Prefetches and caches neighboring partitions, with direction-aware prefetch for repeated forward/backward reading.
- Keeps page movement continuous at partition seams by using next-page anchors and respecting the configured overlap setting.
- Prevents extra duplicated display beyond the user-selected overlap at partition seams.
- Prevents skipped displayed content at partition seams through coverage-first handoff.
- Builds an exact background whole-file page-anchor index.
- Uses exact page anchors for slider, Go to Page, page turning, and bookmark jumps once available.
- Prevents exact Go to Page from silently using estimates while the exact index is still building.
- Keeps displayed page number and total stable during fast partition-boundary paging.
- Uses status-bar-off top spacing as canonical TXT pagination geometry so status-bar visibility does not change total page count.

### TXT page-jump UI

- Keeps the toolbar slider on the selected target page while async jumps are loading.
- Uses a compact rounded loading window for uncached large-TXT slider, Go to Page, and bookmark jumps.
- Avoids loading flashes for cached same-partition jumps.
- Moves the TXT page indicator visually one reader text row lower under the canonical status-bar spacing model.

### Bookmark backup editing

- Replaces the old long `beginnerEditableBookmarks` export area with:

```json
"bookmarkEdits": {
  "beginner": [],
  "developer": []
}
```

- Beginner edit rows focus on memo, line/page target, relative movement, and TXT phrase search.
- Developer edit rows expose repair-oriented position, anchor, identity, and metadata fields.
- Guide text is shorter, friendlier, and bilingual in English/Korean.
- Import remains compatible with the old 2.1.0 edit fields.

### Bookmarks

- TXT bookmark loading passes anchor context to improve restoration after partition/layout/file-binding changes.

### PDF

- Original-size page swipes are more sensitive.
- Slight diagonal motion is less likely to block page turns.
- Normal PDF page turns and zoom redraws no longer show the loading spinner.
- Initial PDF file loading can still show loading feedback.
- Zoomed next/previous page movement lands near the center of the newly rendered page.

### EPUB

- Adds left-to-right and right-to-left page-direction settings.
- Labels right-to-left mode as Japanese-style reading.
- Adds slide/none page-transition setting.
- Applies selected reading direction to swipe behavior and slide animation.

### Memory and lifecycle

- Clears large-TXT partition caches, pending prefetch markers, exact page anchors, queued page deltas, and partition-switch state when the TXT reader releases memory.
- Invalidates large-TXT exact-index and partition-switch generations during TXT reader destruction.
- Uses application context for background TXT reads where possible to reduce temporary Activity retention.
- Clears `CustomReaderView` page-anchor and search-highlight path state when text resources are released.

## Version

- Version name: `2.1.1`
- Version code: `211`
- Application ID / namespace: `com.textview.reader`
