# TextView Reader - Android Studio Beginner Setup

TextView Reader is a Java Android reader for TXT, PDF, EPUB, and Word documents. It includes local file browsing, bookmarks, reader themes, custom fonts, and viewer-specific layout controls.

## 1. Open the project

1. Unzip this folder.
2. Open Android Studio.
3. Click **Open**.
4. Select the unzipped project root folder, not the `app/` folder.
   - Correct: `TextView_Reader_2.1.1_github_ready/`
   - Wrong: `TextView_Reader_2.1.1_github_ready/app/`
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

```text
app/build/outputs/apk/debug/app-debug.apk
```

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
4. Use the TXT bottom toolbar: Find, Page, Bookmark, Settings, and More.
5. Add a TXT bookmark, then close and reopen the file to confirm the saved position restores.
6. Open a PDF and verify horizontal/vertical reading mode behavior.
7. Open an EPUB and verify Settings > EPUB layout changes apply after returning to the viewer.
8. Change the reader theme from Settings while an EPUB is open and confirm the page refreshes to the new theme.
9. Export and import a backup if you need to verify bookmarks/settings backup behavior.

## 6. Current notes

- All reader data is local unless the user manually exports, shares, or backs it up.
- Backup JSON can include bookmarks, reading positions, app settings, layout settings, and custom reading themes.
- Lock PIN data is intentionally excluded from backup JSON because that format is plain text.
- The optional PIN lock is a convenience lock, not a substitute for Android device security.
- Android storage behavior can vary by device and Android version, especially around all-files access and the Storage Access Framework.

## 7. If Gradle fails

Copy the exact first red error line and send it back. Common first failures are:

- Missing SDK Platform 35.
- Android Gradle plugin sync/update prompt.
- Wrong JDK selected; use JDK 17.
- Network unavailable while Gradle is downloading dependencies.
- Resource compile error from an edited XML file.

Do not randomly change files before capturing the first error.
