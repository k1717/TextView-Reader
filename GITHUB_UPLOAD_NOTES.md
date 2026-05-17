# TextView Reader 2.1.2 GitHub Upload Notes

Use this package as the GitHub submission source for **TextView Reader 2.1.2**.

This source package keeps Android metadata at:

- `versionCode 212`
- `versionName "2.1.2"`
- package ID `com.textview.reader`

## 2.1.2 release summary

2.1.2 consolidates TXT Display Rules, optional actual TXT file editing, low-power TXT Auto Page Turn, large-TXT paging/search improvements, and the main-browser parent-folder button into one public release entry.

Highlights:

- Added TXT Display Rules for non-destructive text masking/replacement while reading TXT files.
- Added regex mode, case-sensitive matching, all-file/current-file scope, rule-source labels, rule ordering, and quick enable/disable/delete controls.
- Added **More > Add display rule** and long-press word prefill inside the TXT viewer.
- Added **Edit Actual TXT File** for deliberately applying enabled rules to the original TXT file or an overwritten `*_edited.txt` copy, with rounded confirmation UI and destructive-action warnings.
- Added low-power TXT **Auto Page Turn** using a fixed seconds-per-page interval.
- Added a right-side **← Parent folder** / **← 상위 폴더로** button to the main file-browser path bar.
- Kept large TXT active rendering on fixed 4,000-logical-line partitions with lookahead, neighbor caching, and direction-aware prefetch.
- Added estimated 4,000-line partition jumps for Go to Page / slider movement while exact page indexing is still building.
- Replaced full-file exact indexing with chunked line-based exact indexing so high-line-count TXT files can still reach an accurate final page count.
- Improved final-page EOF handling so the last page remains reachable by tap/page-down, with reduced flicker and less delay when the final partition is already active.
- Improved large-TXT body search so it scans the full display-rule-applied TXT stream and jumps by logical line / partition without waiting for exact page indexing.
- Added **Nth / n번째** search to jump directly to a selected occurrence.
- Added **Reset settings** while preserving user data such as bookmarks, recent files, reading positions, custom themes, folder shortcuts, TXT Display Rules, and PIN lock.

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

1. Unzip the clean source ZIP.
2. Open the extracted folder.
3. Select the files and folders **inside** the extracted folder.
4. Do not select the outer extracted folder itself.
5. Open the GitHub repository root page.
6. Click **Add file > Upload files**.
7. Drag the selected contents into GitHub.
8. Wait until GitHub finishes processing the files.
9. Commit with a message such as:

```text
Update TextView Reader 2.1.2 source
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
