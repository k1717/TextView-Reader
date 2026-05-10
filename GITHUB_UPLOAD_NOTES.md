# GitHub Upload Notes

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
Update TextView Reader 2.0.3 source
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
- local SDK paths;
- keystores/signing keys;
- `.env` files;
- Firebase or secret configuration files;
- generated logs or heap dumps.

## Recommended release title

```text
TextView Reader 2.0.3
```

## Recommended release tag

```text
v2.0.3
```

## Functional release-note summary

```markdown
## TextView Reader 2.0.3 — Functional changes from 2.0.2

- Added EPUB/Word search as a bottom-bar Find button next to Next.
- Matched EPUB/Word search input styling with the TXT reader custom cursor/selection-handle behavior.
- Connected the TXT font selector structure to EPUB/Word.
- Added Default font behavior for EPUB and Word files that declare their own fonts.
- Removed unused EPUB/Word More-menu zoom buttons while preserving double-tap reset.
- Improved EPUB/Word zoomed edge-swipe page turning.
- Improved PDF vertical continuous mode blank-page recovery, zoom behavior, and horizontal panning.
- Matched regular EPUB/Word/PDF popup widths to the TXT viewer style, excluding bookmark dialogs.
- Fixed popup hard-landing and transparent-background regressions.
- Improved the main sort dialog selection bubble placement.
```
