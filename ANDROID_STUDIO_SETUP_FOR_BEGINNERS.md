# TextView - Android Studio Beginner Setup

This project is a Java Android TXT reader prototype inspired by TekView.
It uses continuous scrolling plus TekView-style tap paging:

- Tap top area: page up
- Tap bottom area: page down
- Tap middle area: show/hide controls
- Volume up/down: page up/down if enabled in settings

## 1. Open the project

1. Unzip this folder.
2. Open Android Studio.
3. Click **Open**.
4. Select the unzipped project folder, not the `app` folder.
   - Correct: `tekview-reader-v4-android-studio-ready`
   - Wrong: `tekview-reader-v4-android-studio-ready/app`
5. Wait for **Gradle Sync**.

## 2. Install missing SDK if Android Studio asks

The project uses:

- compileSdk 35
- targetSdk 35
- minSdk 24

If Android Studio says SDK Platform 35 is missing, click the install/fix link.

## 3. Build a debug APK

Use:

- **Build > Make Project**

Then:

- **Build > Build Bundle(s) / APK(s) > Build APK(s)**

The debug APK should appear under:

`app/build/outputs/apk/debug/app-debug.apk`

## 4. Run on your phone

1. Enable Developer Options on the phone.
2. Enable USB Debugging.
3. Connect by USB.
4. In Android Studio, choose your phone from the device dropdown.
5. Press the green Run button.

## 5. First test checklist

After the app installs:

1. Give storage/all-files permission if prompted.
2. Open a `.txt` file.
3. Drag-scroll normally.
4. Tap bottom area: text should jump one screen down.
5. Tap top area: text should jump one screen up.
6. Tap middle area: toolbar/bottom controls should hide/show.
7. Add a bookmark.
8. Close and reopen the file; position should restore.
9. Try dark mode in Settings.

## 6. Known prototype limitations

This version is usable for testing, but not final:

- Large TXT files are loaded into one TextView, so very large files may lag.
- SAF opened files are copied to cache; the final app should bookmark original Uri values.
- All-files storage permission is okay for sideload testing, but SAF-first design is better for public release.
- Background image theme data exists but full rendering support is not final.
- PIN lock is basic and not cryptographically secure.

## 7. If Gradle fails

Copy the exact first red error line and send it back. The common first failures are:

- Missing SDK Platform 35
- Gradle plugin update prompt
- Missing Java/JDK setting
- Resource compile issue

Do not randomly change files before capturing the error.
