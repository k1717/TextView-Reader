# TextView Reader - Android Studio Setup

This project is a Java Android reader app. It supports TXT, PDF, EPUB, and OOXML Word files.

## 1. Open the project

1. Unzip the package.
2. Open Android Studio.
3. Click **Open**.
4. Select the unzipped project root folder, not the `app` folder.
   - Correct: the folder that contains `settings.gradle`, `build.gradle`, `gradlew`, and `app/`.
   - Wrong: selecting only `app/`.
5. Wait for **Gradle Sync**.

## 2. Install missing SDK if Android Studio asks

The project uses:

- `compileSdk 35`
- `targetSdk 35`
- `minSdk 24`
- Java 17

If Android Studio says SDK Platform 35 is missing, use the install/fix link.

## 3. Build a debug APK

Android Studio menu path:

1. **Build > Make Project**
2. **Build > Build Bundle(s) / APK(s) > Build APK(s)**

Command line:

```bash
./gradlew assembleDebug
```

Windows:

```powershell
.\gradlew.bat assembleDebug
```

The locally generated debug APK appears under:

```text
app/build/outputs/apk/debug/app-debug.apk
```

That APK is build output. Do not upload it to GitHub.

## 4. Run on your phone

1. Enable Developer Options on the phone.
2. Enable USB Debugging.
3. Connect by USB.
4. In Android Studio, choose your phone from the device dropdown.
5. Press the green Run button.

## 5. First test checklist

After the app installs:

1. Give storage permission/all-files permission if prompted.
2. Open the app. The home page should show **Recently Read** files.
3. Open a `.txt`, `.pdf`, `.epub`, or `.docx` file.
4. Return to the main screen and confirm the file appears at the top of Recently Read.
5. Tap the small sort icon beside file search and verify sort modes work on the home page.
6. Open a folder from the drawer and verify the same sort icon works in folder browsing.
7. Type in the file search bar and test the All / General / PDF / EPUB / Word filters.
8. Open the left drawer and confirm fixed shortcuts stay separate from the scrollable recent-folder list.
9. In the TXT reader, test tap-zone page movement, volume-key paging, search, and bookmark creation.
10. In PDF/Word/EPUB viewers, test page movement, bookmarks, and returning to the file browser.
11. Close and reopen a file; position should restore.
12. Try dark mode and reading themes in Settings.

## 6. Current known limitations

- This is still a source package for development/testing, not a polished store release.
- Large TXT files are rendered through a custom view; extremely large files may still need optimization.
- SAF-opened files may be copied/cached for local reading behavior.
- All-files storage permission is convenient for sideload testing, but a stricter SAF-first model is better for public store distribution.
- PIN lock is an app-level convenience lock, not cryptographic file encryption.

## 7. If Gradle fails

Capture the first red error line before changing files. Common first failures are:

- Missing SDK Platform 35
- Wrong JDK selection; use JDK 17
- Gradle sync interrupted before wrapper download finished
- Resource compile issue after manually editing XML

Do not randomly delete or regenerate source files before recording the exact error.
