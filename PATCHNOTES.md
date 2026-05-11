## TextView Reader 2.0.6

This release focuses on TXT viewer row/pagination correctness, title-overlay placement, themed loading windows, PDF/EPUB/Word toolbar folding, safe-area handling, and PDF zoom focus compared to 2.0.5.

### TXT Viewer

- Added a file-title overlay under the top page indicator when the 5-button TXT control selector is visible.
- Kept the file title hidden in full viewer mode.
- Matched the title color to the active viewer font color and increased the title size to **14sp**.
- Reworked the title mask so it uses a stable first-row slot instead of following last-page scroll clamping.
- Fixed page-boundary behavior so the next page no longer repeats the previous page's last line.
- Fixed page-boundary behavior so page turns no longer skip a line between pages.
- Fixed first-page row-grid alignment so page 1 aligns more closely with page 2 and later pages.
- Fixed cases where the last page's upper text row could be slightly cut off.

### Loading Windows

- Updated TXT loading UI so the rotating loading window no longer appears as a hard black box.
- Made TXT loading background, text, and spinner tint follow the active viewer theme.
- Made PDF loading spinner background/tint blend with the active viewer theme.
- Made EPUB/Word loading spinner background/tint blend with the active viewer theme.

### PDF / EPUB / Word Toolbar Folding

- Added single-tap toolbar fold/return behavior for PDF, EPUB, and Word viewers.
- Single-tapping the document/page area now hides or restores the top toolbar and bottom control bar.
- Preserved existing double-tap behavior:
  - PDF double-tap zoom.
  - EPUB/Word double-tap reset/original-size behavior.
- Added folded-mode safe-area padding so content is not blocked by punch-hole/status-bar areas or the 3-button navigation bar.
- Added extra top/bottom folded-mode margin beyond the raw system insets for better readability; tuned to 6dp so it protects content without wasting too much screen space.

### PDF Zoom

- Improved PDF pinch-zoom so it preserves the selected pinch/focal spot instead of drifting toward the upper-left corner.
- Improved PDF More-menu Zoom In/Zoom Out/Reset behavior so zoom changes preserve the current viewport center.
- Preserved existing double-tap zoom focus behavior.

