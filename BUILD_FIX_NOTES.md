# Build Fix Notes

This source uses the modern Gradle layout for the Android project root:

- root `plugins { ... }` block;
- `pluginManagement` and `dependencyResolutionManagement` in `settings.gradle`;
- Android Gradle Plugin `9.2.0`;
- Gradle wrapper `9.4.1`;
- `compileSdk 35` and `targetSdk 35`;
- Java compatibility set to 17.

This avoids the older Gradle error:

> Cannot mutate the dependencies of configuration ':app:debugCompileClasspath' after the configuration was resolved.

Open the repository root folder in Android Studio, not the `app/` folder, then click **Sync Now**. If Android Studio asks to install SDK Platform 35 or Build Tools, click **Install**.

## Current Gradle notes

This package removes the deprecated AGP compatibility toggles that previously produced AGP-10 removal warnings. The project uses `android.dependency.useConstraints=false` instead of the deprecated `android.dependency.excludeLibraryComponentsFromConstraints=true` flag. If a future build still prints warnings, treat the first red compile/test failure as the priority and clean non-blocking Gradle warnings separately.


## Java toolchain resolver

`settings.gradle` applies `org.gradle.toolchains.foojay-resolver-convention` version `1.0.0` so Gradle can resolve Java toolchains during local builds. This is a build-time plugin and is recorded in `THIRD_PARTY_NOTICES.md`.

## 2.2.5 archive/browser/progress note

2.2.5 keeps the 2.2.4 archive/drawer baseline and adds the ZIP Commons fallback, direct archive-image opening, folder browse-state preservation, and unified operation-progress windows.

The 2.2.5 source includes archive-support matrix work plus drawer gesture repairs. Build failures in this package should be debugged as normal Java/XML/Gradle errors; drawer behavior changes are implemented in `MainDrawerGestureController`, drawer bottom action routing is in `MainDrawerController`, browse-state caching is in `MainBrowseStateController`, direct archive image routing is in `MainArchiveImageOpenController`, and operation progress UI is in `MainFileOperationProgressController`.

## ZIP Commons fallback and Zstandard note

The ZIP Commons fallback uses `org.apache.commons:commons-compress:1.28.0` for non-encrypted ZIP entries that Zip4j cannot decode. ZSTD ZIP support requires Commons Compress plus `com.github.luben:zstd-jni:1.5.7-9`, which is now bundled, so release minification no longer needs a missing-class suppression for Commons Compress Zstandard classes. Runtime extraction still catches `LinkageError` so ABI-specific native-load failures are reported as unsupported instead of crashing.

Update `THIRD_PARTY_NOTICES.md` whenever changing archive-engine usage descriptions or bundled archive codec dependencies.

