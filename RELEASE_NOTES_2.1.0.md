# TextView Reader 2.1.0 Release Notes

2.1.0 is the GitHub-ready source release after 2.0.9. It includes UI cleanup, package-name migration, TXT bookmark stability work, portable bookmark identity, and a clearer beginner-edit backup format.

## Highlights

- Android package/application ID changed to `com.textview.reader`.
- Rounded dialogs and popup windows are more consistent across TXT/PDF/EPUB/Word and Settings.
- Backup import and custom reading-theme dialogs use compact rounded border-only windows without shaded/ripple option boxes.
- TXT tap-zone default for fresh settings is horizontal: left previous page, center menu, right next page.
- TXT bookmark saving now targets the actual title-covered visual row and avoids off-by-one saves.
- TXT bookmark restore uses saved position plus nearby text anchors, improving stability after font, spacing, and boundary changes.
- Bookmarks include portable file identity metadata so imported bookmarks can rebind when the same file is opened from another folder/device.
- Exported backup filenames use `textview_backup_year_month_day_hour_minute_second.json`.
- Exported backup JSON includes bilingual beginner bookmark-edit instructions and a clearly separated `beginnerEditableBookmarks` area.

## Migration note

Because the Android application ID changed, this build installs as a different app from older package builds. Export a backup from the old app and import it in this build to migrate bookmarks, reading positions, settings, layout options, and custom reading themes.

## Build metadata

- Version name: `2.1.0`
- Version code: `210`
- Namespace/application ID: `com.textview.reader`
- Min SDK: `24`
- Target SDK: `35`
- Compile SDK: `35`
- JDK: `17`
- Gradle wrapper: `9.0.0`
