# TextView Reader 2.0.7 GitHub Upload Notes

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
Update TextView Reader 2.0.7 source
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

## Release notes summary for v2.0.7

Use this if GitHub asks for a short release description:

```markdown
## TextView Reader 2.0.7

- Moved EPUB boundary controls into Settings > EPUB layout.
- EPUB now supports separate left, right, top, and bottom boundaries from 0px to 240px in 5px steps.
- EPUB bottom boundary now accounts for the visible bottom toolbar instead of pushing the page upward by the full boundary value.
- Fixed EPUB/Word theme refresh after returning from Settings, preserving page and scroll position.
- Added EPUB More controls for Increase Font, Decrease Font, and Reset Font Size.
- Added Reset Font Size to TXT More.
- Moved shared Font Size and Line Spacing settings above the TXT layout section.
- Unified TXT popup UI with PDF/EPUB/Word rounded dialog styling, thinner borders, and matching text tone.
- Reworked bookmark popups so the bookmark list stays above Add Bookmark, with hints shown in a small rounded popup.
- Added Open File next to Close in PDF/EPUB/Word More popups.
- Backup export/import now includes app settings and custom reading themes, while excluding lock PIN data from plain JSON.
- Custom reading themes can be long-pressed for Edit/Delete options using rounded popup UI.
- Fixed Theme Editor Save Theme button contrast in light mode.
- Centered rounded action-box labels in custom-theme and file-operation popups.
```
