# Contributing

Contributions are welcome, but keep the repository clean and source-only.

## Before opening a pull request

- Build the project in Android Studio or with `./gradlew assembleDebug`.
- Do not commit IDE folders, Gradle caches, build outputs, APK/AAB files, signing keys, or `local.properties`.
- Keep UI behavior changes focused and test them on an Android device when possible.
- For file-browser changes, test both the Recently Read page and normal folder browsing.
- For reader changes, test TXT, PDF, EPUB, and Word/DOCX flows when the change could affect shared viewer behavior.

## Code style

- Main language: Java.
- Prefer readable, direct Android code over unnecessary abstraction.
- Keep reader behavior predictable: scrolling, page movement, text selection, and file navigation should not block each other.
- Lifecycle-sensitive code should cancel callbacks, stop background work, and release Android resources in `onDestroy()` or equivalent cleanup paths.
- Avoid creating repeated viewer stacks when opening a new file; use the existing `singleTop` / `onNewIntent` pattern where appropriate.

## Repository structure

Correct Android source/resource paths are under `app/src/main/`:

```text
app/src/main/AndroidManifest.xml
app/src/main/java/
app/src/main/res/
```

Do not add duplicate root-level Android folders/files such as:

```text
java/
res/
AndroidManifest.xml
ic_launcher-playstore.png
```

## Documentation updates

When changing user-visible behavior, update the relevant documentation:

- `README.md` for user-facing features and build instructions;
- `CHANGELOG.md` for update summaries;
- `ANDROID_STUDIO_SETUP_FOR_BEGINNERS.md` for setup/test flow changes;
- `BUILD_FIX_NOTES.md` for Gradle/build-specific changes;
- `PRIVACY.md` if stored data, permissions, or network behavior changes;
- `GITHUB_UPLOAD_NOTES.md` for packaging/upload hygiene changes.
