# TextView Reader 2.1.0 Patch Notes

## Final 2.1.0 status

- Android package/application ID: `com.textview.reader`
- Android namespace: `com.textview.reader`
- Java source package: `com.textview.reader`
- Android `versionCode`: `210`
- Android `versionName`: `2.1.0`
- Backup schema: `textview-full-backup-v9`
- Default backup filename format: `textview_backup_year_month_day_hour_minute_second.json`

## Important package migration note

Because the application ID changed to `com.textview.reader`, Android installs this build as a different app from legacy package builds. Existing app-local data does not transfer automatically. To migrate data, export a TextView backup from the old build and import it in this build.

## Main changes after 2.0.9

### Package and repository cleanup

- Changed Android `namespace` and `applicationId` to `com.textview.reader`.
- Moved Java package declarations and source folder paths to `com.textview.reader`.
- Updated XML custom-view references, ProGuard keep rules, README project structure, and source-path documentation.
- Removed remaining legacy app-name naming from filenames/docs/source references, except for unavoidable user data that may appear inside externally exported backups.

### Dialog and popup UI

- Added adaptive rounded-popup sizing for constrained app-window modes.
- Kept normal full-screen popup sizing unchanged.
- Centered popup/window headers in main-screen and reader dialogs.
- Removed shaded/ripple option-box effects from backup import and custom reading-theme dialogs.
- Made backup import and custom reading-theme action/delete dialogs compact at about 70% screen width.
- Kept rounded border-only styling.

### Settings update line

- Replaced in-app update checking with the static release-page line `Check updates at https://github.com/k1717/TextView-Reader/releases`.
- The line remains pressable/copyable, but no longer uses underline styling.
- The app no longer performs a GitHub network update check.

### TXT reader defaults and bookmarks

- Changed the default TXT tap-zone layout to horizontal for fresh settings.
- Fixed TXT bookmark save to use the actual title-covered visual row.
- Changed TXT bookmark save sampling to use an interior row point so it does not save one row above or below the intended line.
- Added robust TXT bookmark anchor restore with nearby text context.
- Kept bookmark restore tied to the same text passage across font size, line spacing, boundary, and wrapping changes.

### Portable bookmark identity

- Added file identity metadata to backup bookmarks: file size, quick fingerprint, file identity object, and local binding path.
- Treats file paths as device-local shortcuts rather than permanent bookmark identity.
- Allows bookmarks to rebind when the same file is opened from another directory/device.
- Uses lazy fingerprint matching only when exact local-path lookup fails, avoiding normal-load slowdown.

### Beginner-friendly backup editing

- Added bilingual read-first warnings and tutorial sections to exported backups.
- Added visible section markers separating the read-only bookmark tutorial from the actual `beginnerEditableBookmarks` edit area.
- Added detailed beginner examples for TXT line movement, TXT phrase search, PDF/Word page movement, EPUB page/section movement, and memo editing.
- Supported beginner-edit fields:
  - TXT: `memo`, `setLine`, `moveByLines`, `findText`, `findOccurrence`, `findTextCaseSensitive`
  - PDF/Word: `memo`, `setPage`, `moveByPages`
  - EPUB: `memo`, `setPageOrSection`, `moveByPages`
- Import applies beginner edits back into the internal bookmark model and regenerates excerpts/anchors.

## Build note

Use Android Studio with JDK 17, compile SDK 35, and Gradle wrapper 9.0.0. In this sandbox, command-line Gradle verification cannot complete because the wrapper needs to download Gradle from `services.gradle.org`, which is unavailable without internet access.
