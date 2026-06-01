# TextView Reader 2.2.2 GitHub Upload Notes

Use this package as the GitHub submission source for **TextView Reader 2.2.2**.

This source package uses Android metadata:

- `versionCode 2220`
- `versionName "2.2.2"`
- package ID `com.textview.reader`

## 2.2.2 release summary

- Android metadata is `versionCode 2220` and `versionName "2.2.2"`.
- TXT reader includes text-to-speech controls for reading the current page or continuously reading forward with Android's built-in TTS engine, plus saved language, voice, speed, pitch, voice-install shortcuts, active segment highlighting, saved TTS playback position state, foreground notification controls, and media-button routing.
- Image viewer swipe/slider order follows the active file/archive sort mode, image fit bounds avoid the visible top toolbar and bottom image slider, placement during image changes is stabilized, and the orientation icon follows the current mode.
- Images opened from main search/filter results use the visible image result set as the viewer sequence, keeping slider counts aligned with the IMG/search result list.
- Image viewer startup now avoids large path-list Intent payloads by opening the selected image first and attaching the full slider/swipe sequence afterward through an in-memory handoff, while reserving slider space so image placement stays stable.
- Archive image viewing opens the selected ZIP/CBZ image first, lazily extracts later entries on demand, prefetches nearby entries, and avoids oversized Intent payloads that could crash large archive image sequences.
- Image preview/detail decode policy is set to roughly 12MP preview and 48MP detail caps, with original decode for images below those thresholds and OOM fallback for large images.
- Large images show a fast preview first and request higher-detail decode on zoom, while normal-sized images still load directly at original quality.
- Image viewer no longer builds a temporary previous/current/next sequence; adjacent movement stays disabled until the full deferred sequence is ready, and toolbar title text is slightly smaller.
- Main and recent file lists include a right-edge drag fast scroller for large folders and unbounded search results.
- Current-folder type filters keep folders above matching files for navigable IMG/PDF/TXT/etc. filtering.
- Filtered folder browsing now remembers where the type filter was enabled, preserving it when backing out of subfolders entered under the filter and clearing it when backing out from the activation folder.
- Wider folder search now scans downward from the current folder instead of jumping up to parent storage roots.
- Current-folder typed searches also publish partial results progressively while scanning continues.
- Empty-query `All` scans also publish partial results progressively when wider folder scope is enabled.
- Large folder navigation now progressively displays discovered entries before the final sorted list is ready. Newly tapped folders take priority over stale queued folder-load work during progressive loading.
- The All file filter includes APK and common video files, including MPEG transport stream `.ts`, with short taps routed to Android package install or external video playback apps.
- Main file search includes an icon scope toggle beside the search field, shows parent-folder paths in search results, and uses a centered loading spinner while scanning. The All-folders scope icon has been redrawn to remove darker vector-overlap seams.
- Current-folder and All-folders file search no longer apply a result cap, so large folders are not cut off at 300 items.
- Recent files now show up to 100 usable visible entries and share the custom right-edge drag fast scroller with the main file list.
- Main search root calculation, recursive search walking, progressive folder loading, visible-list resorting, reveal scrolling, and folder background-work execution are split into focused helper/controller classes.
- Date sorting and file-row dates prefer Android MediaStore download/added time when available, falling back to filesystem created time and then modified time; image info separates modified, created/downloaded, and EXIF taken dates.
- TXT search highlights use theme-blended translucent colors to better match custom reading themes.
- TXT search highlighting preserves a visible theme-derived secondary tone for other matches and a stronger related tone for the active match.
- The built-in Deep Navy reading-theme body text is slightly brighter (`#D7E4FA`) for cleaner long-form readability.
- Short feedback toasts are centralized through `ShortToast` and use an app-wide roughly 700ms display window.
- Settings include configurable button/icon order for main filters and TXT / EPUB-Word / PDF viewer controls. The editor uses compact one-line rows and highlights TXT default-visible slots.
- Sorting the current folder no longer triggers a full folder reload, recent-file mode hides the IMG chip, TXT search action buttons are text-only, and fast-scroll track styling avoids doubled dragbars.
- Tablet and large-screen drawer dismissal is improved so outside taps and left swipes from the outside scrim close the open drawer reliably, while bottom file-type sliding chips remain excluded from drawer-open gestures and drawer bottom actions transition more smoothly.
- TTS is implemented on Android platform APIs; this update does not add a new third-party runtime TTS dependency.
- This source is based on the 2.2.1 GitHub-ready cleanup package.

## 2.2.1 baseline summary

- Main file operations now include containing-folder jump, cut/copy queueing, archive extraction queueing, conflict handling, pending-action dropdown cleanup, and tablet drawer close fixes.
- Archive support covers ZIP/CBZ browsing, encrypted ZIP/7z prompts, standard split ZIP, supported numeric `.001` split archive families, TAR-family formats, and single-file compressor streams. RAR/CBR and lzip `.lz` remain unsupported.
- TXT large-file navigation, final page movement, bookmark page metadata, partition switching, and exact page-model logic are split into dedicated controllers and utility tests.
- Image viewing now supports smoother zoomed pan/fling and archive/comic image sequences.
- The public source tree has been cleaned for upload: no build outputs, APK/AAB files, keystores, local SDK paths, Android Studio workspace files, or local large-TXT fixtures should be included.

## 2.2.0 release summary

2.2.0 is an encoding-detection hardening and brightness-override maintenance release based on the finalized 2.1.9 UI/theme source. The final uploaded source should include only the final behavior, not the intermediate trial patches.

Highlights:

- Updated Android metadata to `versionCode 2200` and `versionName "2.2.0"`.
- Recent-file rows now show compact reading-progress percentages beside saved files, and long filenames remain clipped within the title area so the progress badge does not cover partially visible title text.
- Starting TXT Auto Page Turn now closes the bottom toolbar and returns the TXT viewer to body reading mode before the timer begins.
- Hardened CJK legacy disambiguation so Chinese GBK / GB18030 / Big5 text is not pulled into Korean `windows-949` or alphabetic single-byte encodings by fake-clean decoded output.
- Encoding-family conflict resolution now respects confident ICU/Mozilla family hints instead of forcing a fixed regional priority order. CJK hints still protect multibyte Chinese/Japanese/Korean text from single-byte theft, while confident single-byte hints for Western Latin, Greek, Cyrillic, Hebrew, Arabic, Thai, or Vietnamese are accepted when the detector path is not weak or internally conflicting. Weak US-ASCII fallback hints are still ignored. Concrete Western detector hints are accepted rather than being overridden by a Vietnamese heuristic; this matches the current policy of trusting explicit ICU/Mozilla family results while still ignoring weak US-ASCII-style fallbacks.
- Vietnamese remains supported when the detector/scorer path identifies `windows-1258`; however, if ICU/Mozilla explicitly reports a Western family such as `windows-1252`, the current policy accepts that detector family rather than applying a separate Vietnamese override.
- Added detector-confirmed CJK safeguards using Android ICU and Mozilla/JUniversalChardet family hints; when a multibyte CJK family is detected, Cyrillic / Greek / Hebrew / Arabic / Thai / Western / Vietnamese single-byte candidates are penalized by sample length.
- Improved short high-byte handling so short Korean CP949 titles and first lines resolve to Korean instead of weak Thai / Greek / Cyrillic / Western / Vietnamese guesses, while sufficiently long direct detector matches for real short single-byte texts are protected from the CJK short-sample guard.
- Tightened stateful 7-bit East Asian auto-detection so HZ-GB-2312, ISO-2022-JP, and ISO-2022-KR require concrete shift/designation signatures before auto-selection; manual selection remains available.
- Hardened loose no-BOM UTF-16 detection by requiring stronger zero-byte lane evidence, stronger decoded-text plausibility, and a clear endian winner before returning UTF-16LE/BE.
- Fixed the TXT brightness dialog so **Override system brightness** controls whether the app applies the slider value to the TXT window; PDF, EPUB, and Word readers stay on system brightness.
- Restored the main folder overflow as a compact 170dp popup anchored to the three-dot button, kept Sort on the existing bottom sort button, used lightly rounded theme-colored popup surfaces without an outline stroke, made Show hidden files toggle in place with a compact muted O/X indicator in a fixed label/indicator column so English and Korean layouts stay aligned, and hidden-file toggles refresh the folder list without the full-list clearing flicker, and made New Folder open the rounded main-theme UI used by other file/folder action dialogs.
- Regression tests cover CJK disambiguation, short Korean CP949 samples, and existing Korean / Japanese / Cyrillic / Unicode paths. Two synthetic ESC-heavy CP949 tests remain marked as known low-risk base-scoring limitations because strict stateful signatures already block the ISO-2022 misdetection they were meant to catch.

2.1.9 UI/theme baseline retained:

- Finalized Deep Navy / 짙은 남색 테마 colors across the main screen, drawer, shortcut boxes, file-type buttons, selected states, and reading-theme cards.
- Added custom main-theme controls for selected surfaces, shortcut boxes, and drawer bottom icon color while keeping custom defaults aligned with the Deep Navy palette.
- Custom main-theme HEX inputs are standardized to 6-digit RGB values (`RRGGBB` or `#RRGGBB`) to match the 0-255 RGB sliders.
- Fixed drawer inset/header handling so the drawer top/status area, Recent folders header, recent-folder rows, and bottom drawer actions keep their intended surfaces.
- Improved file/folder long-hold, file info, recent-folder clear, shortcut-remove, TXT rule, and actual TXT edit/delete confirmation dialogs for consistent themed card/dialog styling.
- Increased PIN lock number-pad digit and OK action text sizes for better readability while keeping the OK action text-only.
- Updated custom reading-theme creation so the editor follows the active main theme, the Save Theme button stays above the navigation bar, and Choose Image / Clear / Save Theme use flat card-colored rounded buttons without stroke or shadow.
- Added custom reading-theme toolbar background control with 6-digit RGB HEX/slider input, and applied it to the actual reader toolbar surfaces.
- TXT viewer title/page status text now follows the reading theme body text color.
- Changed the built-in Night Blue reading theme to Deep Navy with background `#050D23`, text `#C3D2EA`, and fixed toolbar color `#041630`; other built-in toolbar fallback colors preserve the reading background hue with bright-background 0.25 inverse 4:2:4 intensity reduction, dark-background simple hue-preserving intensity increase, and Light/Dark-only 0.17 equal gray fallback.
- Reading-theme selection cards now use tone-aware selected outlines: brighter than the normal outline on dark themes, darker than the normal outline on light themes, with the check mark using the same selected outline color.
- TXT Display Rule cards inside the TXT viewer now derive their contrast from the active reading theme, and inline Up / Down / Off / Delete controls are transparent and stroke-free.
- Large-TXT final-page navigation now targets the final exact page anchor consistently for Go to Page, toolbar/slider jumps, tap paging, queued partition jumps, and final-partition fallback paging.
- Removed unused private helper paths and redundant large-TXT wrapper code while preserving settings, bookmarks, reader state, backup/import data, and runtime behavior.

## Suggested commit message

```text
Update TextView Reader 2.2.2 source and docs
```

## Safe upload contents

Upload the contents of the clean source folder, not the outer folder itself.

The repository root should contain files and folders like:

```text
app/
gradle/
.gitignore
README.md
CHANGELOG.md
LICENSE
PRIVACY.md
CONTRIBUTING.md
ANDROID_STUDIO_SETUP_FOR_BEGINNERS.md
BUILD_FIX_NOTES.md
GITHUB_UPLOAD_NOTES.md
PATCHNOTES.md
build.gradle
gradle.properties
gradlew
gradlew.bat
settings.gradle
```

The Android source should remain under:

```text
app/src/main/java/
app/src/main/res/
app/src/main/AndroidManifest.xml
app/src/main/ic_launcher-playstore.png
```

Optional local-only instrumentation fixtures are not part of the upload. To rerun the real large-TXT validation locally, place the file at:

```text
app/src/androidTest/assets/large_txt_real_fixture.txt
```

If this file is absent, those real-fixture instrumentation cases are skipped.

## Do not upload

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

## Web upload steps

1. Unzip the clean source ZIP.
2. Open the extracted project folder.
3. Select the files and folders inside the extracted folder.
4. Do not select the outer extracted folder itself.
5. Open the GitHub repository root page.
6. Click **Add file > Upload files**.
7. Drag the selected contents into GitHub.
8. Wait until GitHub finishes processing the files.
9. Commit with the suggested message above, or a similarly concise 2.2.2 update message.

## Important limitation

GitHub web upload overwrites files with matching paths, but it does not automatically delete old files that are no longer in the upload. If obsolete files remain in the repository, delete them manually on GitHub and commit the deletion.

Root-level duplicates to delete if they ever appear:

```text
java/
res/
AndroidManifest.xml
ic_launcher-playstore.png
```

Keep the correct copies under `app/src/main/`.

## Privacy check before upload

Before uploading, confirm the package does not contain:

- local username paths from your computer;
- Android Studio workspace files;
- Gradle cache folders;
- generated APK/AAB files;
- local SDK path files;
- signing keys;
- secret/environment files.
