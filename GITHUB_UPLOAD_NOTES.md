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
PATCHNOTES.md
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
*.idsig
*.jks
*.keystore
*.pem
*.p12
.env
.env.*
secrets.properties
google-services.json
firebase*.json
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
Update TextView Reader 2.0.4 source
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

Also delete these from GitHub manually if they already exist in the repository from an earlier upload:

```text
.gradle/
.idea/
build/
app/build/
local.properties
*.apk
*.aab
```

## Privacy check before upload

Before uploading, confirm the package does not contain:

- local username paths from your computer;
- Android Studio workspace files;
- Gradle cache folders;
- generated APK/AAB files;
- local SDK path files;
- signing keys;
- secret/environment files;
- duplicate root-level Android source folders.

A quick local check from the repository root:

```bash
find . -maxdepth 3 \
  \( -path './.git' -o -path './gradle' -o -path './app/src' \) -prune -o \
  \( -name '.gradle' -o -name '.idea' -o -name 'build' -o -name 'local.properties' -o -name '*.apk' -o -name '*.aab' -o -name '*.jks' -o -name '*.keystore' -o -name '.env' \) \
  -print
```

Expected result for a clean public source package: no private/generated files except normal source/build-script folders that should remain.

## Release notes summary for v2.0.4

Use this if GitHub asks for a short release description:

```markdown
## TextView Reader 2.0.4

- Prepared the repository for public GitHub submission by removing generated/private files from the source package.
- Updated app metadata to versionCode 204 / versionName 2.0.4.
- Updated README, CHANGELOG, PATCHNOTES, PRIVACY, and upload guidance.
- Strengthened .gitignore for Android build outputs, IDE state, machine-local config, signing files, APK/AAB outputs, and secret/config files.
- Main bookmark folders now default to collapsed, and bookmark edit dialogs use rounded bordered styling.
- Added Cancel / Clear memo / Save to TXT, PDF, EPUB, and Word bookmark memo dialogs.
- Fixed stale viewer-theme behavior after returning from Settings/theme editing, including PDF More dialogs.
- Reduced Logcat exposure of user file paths and local file names.
```
