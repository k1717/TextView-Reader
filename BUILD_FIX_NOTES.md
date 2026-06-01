# Build Fix Notes

This source uses the modern Gradle layout for the Android project root:

- root `plugins { ... }` block;
- `pluginManagement` and `dependencyResolutionManagement` in `settings.gradle`;
- Android Gradle Plugin `9.1.1`;
- Gradle wrapper `9.3.1`;
- `compileSdk 35` and `targetSdk 35`;
- Java compatibility set to 17.

This avoids the older Gradle error:

> Cannot mutate the dependencies of configuration ':app:debugCompileClasspath' after the configuration was resolved.

Open the repository root folder in Android Studio, not the `app/` folder, then click **Sync Now**. If Android Studio asks to install SDK Platform 35 or Build Tools, click **Install**.

## Current Gradle warnings

Android Gradle Plugin 9.x may print deprecation or sync-performance warnings for some legacy compatibility flags in `gradle.properties`, such as Jetifier or build-feature defaults. Those warnings are not the same as a Java compile error. For release work, fix the first red compile/test failure first, then clean Gradle warnings separately.


## Java toolchain resolver

`settings.gradle` applies `org.gradle.toolchains.foojay-resolver-convention` version `1.0.0` so Gradle can resolve Java toolchains during local builds. This is a build-time plugin and is recorded in `THIRD_PARTY_NOTICES.md`.
