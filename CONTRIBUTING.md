# Contributing

Contributions are welcome, but keep the repository clean and source-only.

## Before opening a pull request

- Build the project in Android Studio or with `./gradlew assembleDebug`.
- Do not commit IDE folders, Gradle caches, build outputs, APK/AAB files, signing keys, or `local.properties`.
- Keep UI behavior changes focused and test them on an Android device when possible.

## Code style

- Main language: Java.
- Prefer readable, direct Android code over unnecessary abstraction.
- Keep reader behavior predictable: scrolling, page movement, text selection, and file navigation should not block each other.
- Lifecycle-sensitive code should cancel callbacks, stop background work, and release Android resources in `onDestroy()` or equivalent cleanup paths.
