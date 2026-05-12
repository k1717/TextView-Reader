## TextView Reader 2.0.8

This patch note lists only the changes made **after 2.0.7**. The 2.0.7 EPUB boundary, TXT popup UI, bookmark popup, backup/settings, and custom-theme changes are not repeated here.

### Hardware page-turn and e-ink reader fixes

- Hardened TXT bottom-toolbar tap handling for e-ink devices such as iReader-class readers.
- TXT bottom toolbar buttons now consume their own tap events from press to release, reducing missed taps on buttons such as **More / 더보기**.
- The TXT bottom toolbar is explicitly kept in the front touch layer when visible.
- Expanded the **Volume Keys Page Up/Down** behavior beyond only Android volume keys.
- When the setting is enabled, TXT now handles common e-reader/page-turn key codes:
  - Volume Up / Down
  - Page Up / Down
  - D-pad Left / Right / Up / Down
  - Space
  - Media Next / Previous
  - Forward
  - L1/R1-style page buttons
  - Navigate Previous / Next
- Page-turn key handling now consumes key-up events too, reducing the chance that device firmware also changes system volume.
- Applied the same hardware page-turn key behavior to **PDF, EPUB, and Word/DOCX** viewers, not only TXT.

### Viewer toolbar behavior

- Removed hold/ripple animation from TXT, PDF, EPUB, and Word bottom toolbar buttons.
- Disabled long-click/haptic behavior on the shared viewer toolbar button style.
- Kept normal tap behavior unchanged.
- Kept the TXT e-ink tap-consumption fallback, but removed the pressed-state visual so toolbar buttons no longer show the hold animation.

### TXT theme-return reload fix

- Fixed TXT viewer behavior after changing reader theme/settings and returning to the viewer.
- During normal Activity recreation, the TXT viewer now restores the already-loaded text from memory instead of showing the loading flow and reading the file from disk again.
- Restored state includes current character position, search state, large-text preview state, file title, and page label.
- If the app process is actually killed and the memory snapshot is gone, the old safe disk-reload fallback still remains.

### Safe optimization cleanup

- Enabled `shrinkResources true` for release builds.
- Removed stale PdfBox ProGuard keep rules because the current PDF path uses Android `PdfRenderer`.
- Added a fast recent-file existence check so the UI does not sort the full recent-file list just to test whether recent files exist.
- Replaced large TXT preview stream skipping with direct `RandomAccessFile.seek()`.
- Avoided repeated full font-folder rescans after fonts have already been scanned during the current app session.
- Converted every remaining long-duration Toast to short-duration Toast.

### Version metadata

- Android `versionCode`: `208`
- Android `versionName`: `2.0.8`
