# TextView Reader 2.1.0 GitHub Upload Notes

Use this checklist before replacing files on GitHub through the web interface.

## Safe upload contents

Upload the contents of the clean source folder, not the outer folder itself.

The repository root should contain files and folders like:

```text
app/
gradle/
.gitignore
README.md
CHANGELOG.md
LICENSE
PRIVACY.md
CONTRIBUTING.md
ANDROID_STUDIO_SETUP_FOR_BEGINNERS.md
BUILD_FIX_NOTES.md
GITHUB_UPLOAD_NOTES.md
PATCHNOTES.md
build.gradle
gradle.properties
gradlew
gradlew.bat
settings.gradle
```

The Android source should remain under:

```text
app/src/main/java/
app/src/main/res/
app/src/main/AndroidManifest.xml
app/src/main/ic_launcher-playstore.png
```

## Do not upload

```text
.gradle/
.idea/
build/
app/build/
local.properties
*.apk
*.aab
*.apks
*.jks
*.keystore
*.pem
*.p12
.env
.env.*
secrets.properties
google-services.json
captures/
*.hprof
*.log
```

## Web upload steps

1. Unzip the clean source zip.
2. Open the extracted folder.
3. Select the files and folders **inside** the extracted folder.
4. Do not select the outer extracted folder itself.
5. Open the GitHub repository root page.
6. Click **Add file > Upload files**.
7. Drag the selected contents into GitHub.
8. Wait until GitHub finishes processing the files.
9. Commit with a message such as:

```text
Update TextView Reader 2.1.0 source
```

## Important limitation

GitHub web upload overwrites files with matching paths, but it does not automatically delete old files that are no longer in the upload.

If obsolete files remain in the repository, delete them manually on GitHub and commit the deletion.

Root-level duplicates to delete if they ever appear:

```text
java/
res/
AndroidManifest.xml
ic_launcher-playstore.png
```

Keep the correct copies under `app/src/main/`.

## Privacy check before upload

Before uploading, confirm the package does not contain:

- local username paths from your computer;
- Android Studio workspace files;
- Gradle cache folders;
- generated APK/AAB files;
- local SDK path files;
- signing keys;
- secret/environment files.

## Release notes summary for v2.1.0

Use this if GitHub asks for a short release description. This summary lists the consolidated difference from **2.0.9**:

```markdown
## TextView Reader 2.1.0

2.1.0 consolidates the post-2.0.9 UI, package-name, bookmark, and backup-editing work into a GitHub-ready source release.

- Changed the Android package/application ID and namespace to `com.textview.reader`.
- Added adaptive rounded-popup sizing for constrained app windows while preserving normal full-screen dialog sizing.
- Centered dialog/header text across main-screen and TXT/PDF/EPUB/Word viewer windows.
- Removed shaded/ripple option-box effects from backup import and custom reading-theme dialogs, and made those dialogs compact at about 70% screen width.
- Replaced in-app update checking with a static, copyable Settings release link: `Check updates at https://github.com/k1717/TextView-Reader/releases`.
- Changed the default TXT tap-zone layout for fresh installs to horizontal: left previous page, center menu, right next page.
- Fixed TXT bookmark saving to use the actual title-covered visual row and an interior row sample, avoiding off-by-one saves.
- Added robust TXT bookmark restoration with nearby anchor text so bookmarks remain tied to the same passage after font, line spacing, or boundary changes.
- Added portable bookmark identity metadata so imported bookmarks can rebind to the same file after moving folders/devices.
- Added timestamped backup filenames: `textview_backup_year_month_day_hour_minute_second.json`.
- Added bilingual beginner bookmark-edit tutorial sections and a separated `beginnerEditableBookmarks` edit area in exported backups.
- Updated Android metadata to `versionCode 210` and `versionName 2.1.0`.

Package migration note: Android treats `com.textview.reader` as a different app from older package builds. Export a TextView backup from the old app and import it in this build to migrate bookmarks, reading positions, settings, and custom themes.
```
