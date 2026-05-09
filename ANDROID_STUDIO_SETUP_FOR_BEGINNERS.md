# Android Studio Setup for Beginners

This guide explains how to open and build TextView Reader from source.

## 1. Open the correct folder

1. Unzip the source package.
2. Open Android Studio.
3. Click **Open**.
4. Select the project root folder.

Correct folder contents should include:

```text
app/
gradle/
settings.gradle
build.gradle
gradlew
gradlew.bat
```

Do **not** open only the `app/` folder.

## 2. Sync Gradle

After opening the project, Android Studio should start Gradle sync automatically.

The project uses:

- Android Gradle Plugin 8.13.1;
- compileSdk 35;
- targetSdk 35;
- minSdk 24;
- Java 17;
- app versionName `2.0.2`;
- app versionCode `202`.

If Android Studio asks to install SDK Platform 35 or related build tools, accept the installation.

## 3. Build a debug APK

Use Android Studio:

1. Click **Build > Make Project**.
2. Then click **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

Or use command line:

```bash
./gradlew assembleDebug
```

On Windows:

```bat
.\gradlew.bat assembleDebug
```

The debug APK is generated locally at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Do not upload this APK to GitHub source history.

## 4. Run on a phone

1. Enable Developer Options on the phone.
2. Enable USB Debugging.
3. Connect the phone by USB.
4. Select the phone in Android Studio.
5. Press the green Run button.

## 5. First-use checklist for 2.0.2

After the app launches:

1. Confirm the initial app language follows the device/system language until manually changed.
2. Open a TXT file larger than 20 MB if available and confirm it no longer switches to preview-only mode at the old 20 MB threshold.
3. Open a very large TXT file at or above 32 MB and confirm preview-only behavior is used as intended.
4. Long-press a folder in the file browser and add it as a shortcut.
5. Open the left drawer and confirm the shortcut appears below built-in storage entries.
6. Long-press the added drawer shortcut and remove it.
7. Use the search field and file-type chips; confirm the clear button only appears for typed search text.
8. Use the small sort icon beside search to change recent or folder sort order.
9. Open a PDF and test the horizontal/vertical reading-mode toggle.
10. Open Settings and test TXT page indicator alignment: left, center, right, and hidden.
11. Open TXT, PDF, EPUB, and Word files to confirm viewer transitions do not create stacked repeated viewers.
12. Add a bookmark and confirm it appears in the bookmark list.

## 6. Clean package rule

Before uploading source to GitHub, remove or exclude:

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
