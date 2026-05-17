# TextView Reader 2.1.1 GitHub Upload Notes

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
Update TextView Reader 2.1.1 source
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

## Release notes summary for v2.1.1

Use this if GitHub asks for a release description. This summary lists the functional difference from the uploaded **2.1.0** source package. The full previous-version history remains in `CHANGELOG.md`:

```markdown
## TextView Reader 2.1.1

2.1.1 is a functional polish and stability release over 2.1.0. It keeps the same package identity and migration model.

### Functional changes from 2.1.0

- Large TXT now uses fixed 4,000-logical-line active partitions, lookahead rendering, neighbor partition caching, direction-aware prefetch, in-place partition switching, and a background exact whole-file page-anchor index.
- Large TXT partition seams now use next-page anchors and the configured overlap setting, preventing skipped content and preventing extra duplicated display beyond the selected overlap.
- TXT page status stays stable during fast partition-boundary paging, and status-bar visibility uses canonical status-bar-off spacing so total page count does not change when the Android status bar is toggled.
- TXT toolbar slider, Go to Page, and bookmark jumps hold the selected target while async loading is pending and use the compact rounded loading panel only for slower uncached jumps.
- Bookmark backup export now uses `bookmarkEdits.beginner` and `bookmarkEdits.developer`, with friendlier English/Korean guidance and backward-compatible import from the old `beginnerEditableBookmarks` format.
- TXT bookmark jumps pass anchor context to improve restoration after layout, partition, or file-binding changes.
- PDF original-size swipes are more sensitive, fast page/zoom redraws avoid spinner flashes, and zoomed next/previous page turns land centered instead of upper-left.
- EPUB adds left-to-right / Japanese-style right-to-left page-direction settings and slide/none transition behavior.
- Large-TXT runtime memory is cleared more aggressively on reader release, stale background generations are invalidated on destroy, and background file reads use application context where possible.
- The GitHub source package includes `.gitignore` and excludes local configuration, build outputs, signing keys, secrets, logs, and exported backup JSON files.

Version metadata: `versionCode 211`, `versionName 2.1.1`.
```
