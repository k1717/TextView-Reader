# Contributing

Thank you for improving TextView Reader.

## Development setup

1. Use Android Studio with JDK 17.
2. Open the repository root folder, not the `app/` folder.
3. Let Gradle sync.
4. Install Android SDK Platform 35 if prompted.
5. Build with:

```bash
./gradlew assembleDebug
```

## Project rules

- Keep Android source under `app/src/main/`.
- Do not add duplicate root-level Android folders such as `java/`, `res/`, or root `AndroidManifest.xml`.
- Do not commit generated files from `.gradle/`, `build/`, or `app/build/`.
- Do not commit APK/AAB files, signing keys, local SDK paths, `.env` files, or private configuration files.
- Keep docs readable on GitHub with normal Markdown line breaks.

## Code style expectations

- Prefer small, focused changes.
- Keep reader state, bookmarks, folder shortcuts, and file actions safe and predictable.
- Folder shortcut removal must remove only the shortcut entry, never the folder or its files.
- Viewer activities should continue using single-viewer reuse behavior where appropriate.
- Cache cleanup must only affect generated/disposable cache data. It must never delete bookmarks, reading history, saved reading position, folder shortcuts, or user documents.
- Preserve Korean/Unicode text handling when touching decoding or rendering code.
- Test TXT, PDF, EPUB, and Word paths when changing shared file-opening logic.
- Test both PDF horizontal slide mode and vertical continuous mode when touching PDF gestures or rendering.
- Test TXT page indicator left/center/right/hidden alignment when touching reader insets or status-bar behavior.

## Documentation expectations

When behavior changes, update the relevant docs:

- `README.md` for user-visible features and build instructions.
- `CHANGELOG.md` for release changes.
- `PRIVACY.md` for data, permissions, or storage behavior.
- `GITHUB_UPLOAD_NOTES.md` for packaging/upload workflow changes.
