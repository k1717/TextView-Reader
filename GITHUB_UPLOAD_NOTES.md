# TextView Reader 2.1.9 GitHub Upload Notes

Use this package as the GitHub submission source for **TextView Reader 2.1.9**.

This source package uses Android metadata:

- `versionCode 2190`
- `versionName "2.1.9"`
- package ID `com.textview.reader`

## 2.1.9 release summary

2.1.9 is a UI/theme polish and large-TXT navigation maintenance release. The final uploaded source should include only the final behavior, not the intermediate trial patches.

Highlights:

- Finalized Deep Navy / 짙은 남색 테마 colors across the main screen, drawer, shortcut boxes, file-type buttons, selected states, and reading-theme cards.
- Added custom main-theme controls for selected surfaces, shortcut boxes, and drawer bottom icon color while keeping custom defaults aligned with the Deep Navy palette.
- Custom main-theme HEX inputs are standardized to 6-digit RGB values (`RRGGBB` or `#RRGGBB`) to match the 0-255 RGB sliders.
- Fixed drawer inset/header handling so the drawer top/status area, Recent folders header, recent-folder rows, and bottom drawer actions keep their intended surfaces.
- Improved file/folder long-hold, file info, recent-folder clear, shortcut-remove, TXT rule, and actual TXT edit/delete confirmation dialogs for consistent themed card/dialog styling.
- Increased PIN lock number-pad digit and OK action text sizes for better readability while keeping the OK action text-only.
- Updated custom reading-theme creation so the editor follows the active main theme, the Save Theme button stays above the navigation bar, and Choose Image / Clear / Save Theme use flat card-colored rounded buttons without stroke or shadow.
- Added custom reading-theme toolbar background control with 6-digit RGB HEX/slider input, and applied it to the actual reader toolbar surfaces.
- TXT viewer title/page status text now follows the reading theme body text color.
- Changed the built-in Night Blue reading theme to Deep Navy with background `#050D23`, text `#C3D2EA`, and fixed toolbar color `#041630`; other built-in toolbar fallback colors preserve the reading background hue with bright-background 0.25 inverse 4:2:4 intensity reduction, dark-background simple hue-preserving intensity increase, and Light/Dark-only 0.17 equal gray fallback.
- Reading-theme selection cards now use tone-aware selected outlines: brighter than the normal outline on dark themes, darker than the normal outline on light themes, with the check mark using the same selected outline color.
- TXT Display Rule cards inside the TXT viewer now derive their contrast from the active reading theme, and inline Up / Down / Off / Delete controls are transparent and stroke-free.
- Large-TXT final-page navigation now targets the final exact page anchor consistently for Go to Page, toolbar/slider jumps, tap paging, queued partition jumps, and final-partition fallback paging.
- Removed unused private helper paths and redundant large-TXT wrapper code while preserving settings, bookmarks, reader state, backup/import data, and runtime behavior.

## Suggested commit message

```text
Update TextView Reader 2.1.9 source
```

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
2. Open the extracted project folder.
3. Select the files and folders inside the extracted folder.
4. Do not select the outer extracted folder itself.
5. Open the GitHub repository root page.
6. Click **Add file > Upload files**.
7. Drag the selected contents into GitHub.
8. Wait until GitHub finishes processing the files.
9. Commit with the suggested message above, or a similarly concise 2.1.9 update message.

## Important limitation

GitHub web upload overwrites files with matching paths, but it does not automatically delete old files that are no longer in the upload. If obsolete files remain in the repository, delete them manually on GitHub and commit the deletion.

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
