# Build Fix Notes

This source package uses a modern Android Gradle setup.

## Current build configuration

- Android Gradle Plugin: 8.13.1
- Gradle wrapper: included
- compileSdk: 35
- targetSdk: 35
- minSdk: 24
- Java compatibility: 17
- App versionName: 2.0.1
- App versionCode: 201

## Dependency style

The project uses the modern Gradle layout:

- root `build.gradle` with `plugins { ... }`;
- `settings.gradle` with `pluginManagement` and `dependencyResolutionManagement`;
- app dependencies in `app/build.gradle`.

This avoids older `buildscript { ... }` / `allprojects { ... }` repository configuration issues.

## Build command

```bash
./gradlew assembleDebug
```

Windows:

```bat
.\gradlew.bat assembleDebug
```

## Common Android Studio fixes

If Gradle sync fails because SDK Platform 35 is missing, install it from Android Studio's SDK Manager or accept the install prompt.

If Android Studio opens the wrong folder, close the project and reopen the repository root folder containing `settings.gradle`.

## Repository cleanliness

Do not commit local or generated build files. Generated APKs, Gradle caches, Android Studio workspace files, local SDK paths, and signing files are excluded from the clean GitHub package.
