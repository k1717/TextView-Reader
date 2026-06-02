# Public release build checklist

This source package is prepared for public GitHub distribution of TextView Reader 2.2.3.
The app version metadata remains:

```text
versionCode 2230
versionName "2.2.3"
```

## Keystore policy

Do not commit release signing files or passwords. The release build reads signing values from environment variables first and then from `~/.gradle/gradle.properties`:

```text
TEXTVIEW_KEYSTORE_PATH=/secure/path/release.keystore
TEXTVIEW_KEYSTORE_PASSWORD=...
TEXTVIEW_KEY_ALIAS=textview
TEXTVIEW_KEY_PASSWORD=...
```

Create the release keystore once and back it up securely:

```bash
keytool -genkeypair \
  -alias textview \
  -keyalg RSA -keysize 4096 \
  -validity 36500 \
  -keystore release.keystore \
  -dname "CN=your-alias, OU=Apps, O=your-project-name, L=City, ST=State, C=Country"
```

The distinguished name is visible in the APK certificate. Use values you are comfortable publishing.
Losing this keystore prevents normal updates to the same installed app package.

## Build commands

```bash
./gradlew clean
./gradlew testDebugUnitTest
./gradlew compileReleaseJavaWithJavac
./gradlew assembleRelease
```

The signed APK is expected at:

```text
app/build/outputs/apk/release/app-release.apk
```

## Release verification

```bash
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk

aapt dump xmltree app/build/outputs/apk/release/app-release.apk AndroidManifest.xml \
  | grep -E "debuggable|usesCleartextTraffic|INTERNET"

strings app/build/outputs/apk/release/app-release.apk \
  | grep -E "^/(home|Users|builds)/" | sort -u

sha256sum app/build/outputs/apk/release/app-release.apk
```

Expected results:

- certificate is your release certificate, not `CN=Android Debug, O=Android, C=US`;
- `debuggable` is absent or false;
- `usesCleartextTraffic` is false;
- no `INTERNET` permission is present;
- no build-machine path appears in the APK strings output;
- the APK SHA-256 can be copied into the GitHub Release notes.

## Public-source notes

- Upload the contents of the extracted project folder, not the outer extracted folder itself.
- `*.keystore`, `*.jks`, `local.properties`, and build outputs are ignored by git.
- R8/minify and resource shrinking are enabled for release builds.
- JUniversalChardet remains active for TXT encoding detection.
- TXT TTS uses Android platform TTS APIs and does not add a bundled third-party voice engine.
- Junrar is bundled for RAR extraction-only fallback. Do not add RAR creation/compression paths that use Junrar or UnRAR-derived code.
- ALZ/EGG readers are first-party extraction code. EGG decoding is limited to the verified Store/Deflate/BZip2/LZMA methods; encrypted, split, solid, AZO, and unverified legacy variants must fail cleanly.
- Network cleartext is disabled, and WebView resource interception explicitly blocks non-local requests.
- Short feedback toasts are centralized in `ShortToast` with a roughly 700ms display window; longer user-facing warnings remain explicit long toasts where needed.

## Pre-upload source check

Before uploading through the GitHub web UI, confirm the extracted source tree does not include:

```text
.gradle/
.idea/
build/
app/build/
app/src/androidTest/assets/large_txt_real_fixture.txt
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

## 2.2.3 focused QA

Verify drawer gestures before publishing: from the main screen, right-swipe should open the drawer from the intended broad main area; with the drawer open or partially open, a real left-swipe on the outside area should close it; a light outside tap should not be treated as a swipe-close; manual drags should settle fully open or fully closed instead of sticking half-open.

Verify the drawer bottom **Open File / Bookmarks / Settings** buttons: each action should start immediately, and the drawer should close in the background instead of delaying the action until after the close animation.

Before publishing this 2.2.3 source, verify image preview/detail policy on a large ZIP/CBZ: the selected image should open quickly, preview decode should remain bounded near the 12MP preview policy, zoom should request higher detail up to the 48MP detail policy, and paging should lazy-extract or prefetch adjacent images without viewer crashes.


Also verify Settings > Button / icon order for the main filter strip and TXT / EPUB-Word / PDF viewer controls, including reset-to-default and Settings return/apply behavior.

Verify large-folder browsing and search on a folder with thousands of files: progressive folder entries should appear before the final sorted list, sort changes should reorder the visible list without an app-not-responding pause, and both the main list and recent list right-edge drag scrollers should track the loaded item count.

Verify RAR/CBR behavior with explicit fixture categories: RAR5 and RAR4/RAR3 method-0 stored entries should list and extract, compressed RAR4/RAR3 entries should extract through Junrar when not split/solid edge cases, and compressed RAR5, solid archives, encrypted headers, compressed encrypted archives, and unsupported split archives should fail cleanly without writing partial output.

Verify ALZ/EGG handling with small fixtures: `.alz` and `.egg` should appear under the archive filter and derive clean extraction-folder names. ALZ Store/Deflate/BZip2 entries should extract with CRC checks, EGG Store/Deflate/BZip2/LZMA entries should extract with per-block CRC checks, and unsupported ALZ/EGG variants such as encrypted EGG, split/solid EGG, AZO, or malformed containers should fail cleanly without partial output.

Verify archive-engine routing with ZIP fixtures: production ZIP/CBZ should still list and extract through Zip4j, encrypted ZIP should require and accept the correct password, standard split ZIP should extract, and ZIPX method-95/XZ samples should list/password-detect but report unsupported extraction. The experimental lightweight ZIP benchmark should continue producing identical output before any future route switch.

Verify RAR volume selection with `.part1.rar`/`.part2.rar` and `.rar`/`.r00` fixtures: selecting a later volume should resolve to the first volume for listing, while entries that actually span volumes should fail cleanly. Verify encrypted RAR headers request a password and encrypted payloads do not loop password prompts after a failed password-backed attempt.

Verify CBZ, CBR, CB7, and CBT comic opening from the main file list: if a saved archive image entry exists, the image reader should open directly and Back should return to the previous main/browser screen, not the archive internals.

Verify RAR5 encrypted stored-entry fixtures separately from compressed encrypted RARs: method-0 encrypted payloads should decrypt only with the correct password and then pass CRC validation, while encrypted headers, RAR4 encrypted files, compressed encrypted files, and split encrypted files should fail cleanly.

Verify stored split RAR fixtures separately: method-0 entries split across `.partN.rar` or `.rNN` sibling volumes should extract as one file, while compressed, solid, or encrypted split entries should fail cleanly.

Verify long copy/move and ZIP/TAR/7z extraction runs: the rounded progress window should update progress, pause/resume without UI blocking, cancel without leaving a completed destination, and continue after tapping Background.

Optional external archive fixture smoke tests can be run by placing the provided password ZIP, password 7z, password ZIPX, and `rar-test-files-master.zip` files in a local folder and setting:

```bash
TEXTVIEW_EXTERNAL_ARCHIVE_FIXTURE_DIR=/path/to/archive-fixtures
./gradlew testDebugUnitTest
```

The committed unit tests also include synthetic ALZ and EGG fixtures for Store/Deflate extraction and encrypted-entry failure classification, so the public test suite remains runnable without shipping private sample archives.
