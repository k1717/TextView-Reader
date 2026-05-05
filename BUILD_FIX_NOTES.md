# Build Fix Notes

This version fixes the Gradle error:

> Cannot mutate the dependencies of configuration ':app:debugCompileClasspath' after the configuration was resolved.

Changes made:

- Replaced old `buildscript { ... }` / `allprojects { ... }` Gradle style with modern `plugins { ... }` + `dependencyResolutionManagement`.
- Updated Android Gradle Plugin from 8.2.0 to 8.13.1.
- Kept `compileSdk 35` and `targetSdk 35`.
- Set Java compatibility to 17.
- Updated AndroidX dependencies.

Open the project root folder in Android Studio, then click **Sync Now**.

If Android Studio asks to install SDK Platform 35 or Build Tools, click **Install**.
