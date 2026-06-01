
## 2.2.2 - 2026-05-31
This package uses Android metadata `versionCode 2220` and `versionName "2.2.2"`.

### TXT reader TTS

- Added TXT text-to-speech controls from the bottom toolbar and More dialog.
- Added current-page reading and continuous page-by-page reading through Android's built-in TTS engine.
- Added saved TTS language presets for system default and mainstream languages including English, Korean, Japanese, Simplified/Traditional Chinese, Spanish, French, German, Italian, Portuguese, Russian, Arabic, Hindi, Indonesian, Vietnamese, and Thai.
- Added saved TTS voice model selection from installed Android voices, 50-200% speed and pitch sliders, and a shortcut into Android's TTS settings.
- Added an Add/download voice models shortcut that opens Android's TTS voice-data installation flow when available, so newly installed voice packs can be selected from the app's voice list afterward.
- Continuous TTS uses the existing TXT page movement path, so large-TXT partition handoff and displayed page status stay aligned with normal reader navigation.
- TTS stops on manual page movement, manual scroll, pause, and reader destruction, and it releases the Android TTS engine with the reader lifecycle.
- Adjusted the TXT bottom-toolbar TTS position and compacted the TTS dialog action labels for cleaner Korean UI.
- Reworked TXT TTS playback around sentence-like speech segments, added active segment highlighting in the TXT view, and stores the latest TTS file/page/character state for resume and notification playback control.
- Added a foreground TTS playback service with notification controls, media-button handling for play/pause/stop/next/previous, one-time Android 13+ notification permission request, and resume-from-saved-page action in the TTS dialog.
- Changed TXT search highlights from a fixed yellow accent to theme-blended translucent colors that better match the active reading theme.
- Restored a clearly visible secondary TXT search highlight for other visible matches, using a theme-derived tone with a stronger related active-match tone.
- Brightened the built-in Deep Navy reading-theme body text from `#C3D2EA` to `#D7E4FA` for cleaner long-form readability.

### Image viewer

- Image viewer swipe/slider sequence order now follows the active main-folder sort mode for local images and the active archive sort mode for archive image sequences, instead of falling back to plain filename order.
- Opening an image from main search/filter results now uses the visible image result set as the viewer sequence, so the slider count matches the IMG/search result list.
- Image viewer startup now opens the selected image first, then attaches the full swipe/slider sequence through an in-memory handoff instead of sending huge path lists through the launch Intent; the viewer reserves the slider viewport while the sequence is pending so image placement stays fixed.
- Archive image sequences now open the selected entry first, keep the rest of the sequence as archive metadata, lazily extract images on demand, and prefetch nearby images in the background.
- Fixed the archive image viewer launch path to avoid oversized Intent payloads and to preserve lazy archive placeholder entries, reducing viewer-only crashes when opening or paging through large ZIP/CBZ image sets.
- Tuned progressive image decoding so preview decode uses original quality up to roughly 12MP, caps larger previews near 12MP, and zoom/detail decode uses an approximately 48MP original/detail cap with OOM-safe fallback.
- Large images now decode a quick preview first and request a higher-detail bitmap when the user zooms; reasonably sized images still load directly at original quality.
- Adjacent-image movement is disabled until the full image sequence is ready, avoiding inconsistent temporary ordering while keeping the initial image open path light.
- Reserved the image viewer's default fit bounds below the top toolbar and above the bottom image slider while controls are visible, reducing late bottom-up repositioning during image changes.
- Applied each new image's base matrix immediately so image changes do not briefly show the next image with the previous image's bottom-biased placement.
- Date sorting and file-row dates now use Android MediaStore download/added time first, falling back to filesystem created time and then modified time.
- Image info now separates filesystem modified time, created/downloaded time, and EXIF taken time, and normalizes EXIF date formatting.
- Replaced the rotate-arrow toolbar icon with a simple rectangular screen icon for the portrait/landscape toggle.
- The portrait/landscape toggle icon now switches between portrait and landscape screen shapes to match the current orientation.
- Removed the image viewer portrait/landscape toast so orientation changes do not interrupt viewing.
- Slightly reduced the image viewer toolbar title/subtitle text size for better fit.

### File browser

- The All file filter now includes APK and common video files, including MPEG transport stream `.ts` files, in addition to TextView-readable files.
- Short-tapping an APK opens it through Android's package installer path, and short-tapping a video opens it through an external video-capable app.
- Main file search now includes an icon scope toggle beside the search field so searches can stay within the current folder or expand across available storage roots.
- Redrew the All-folders search-scope icon to avoid darker edge seams from overlapping vector layers, and added a short scope-change toast.
- Current-folder type filters now keep folders above matching files, preserving normal folder navigation while filtering IMG/PDF/TXT/etc. files.
- Filtered folder navigation now remembers the activation folder: Back keeps the type filter while unwinding subfolders entered under that filter, but clears the filter and returns to the parent when pressed where the filter was originally enabled.
- Switching from current-folder search to the wider folder scope now searches only downward from the current folder instead of scanning parent storage roots and sibling folders.
- Current-folder typed searches now progressively update visible results while recursive scanning is still running, instead of waiting for the full scan to finish.
- Empty-query `All` scans also use progressive result publishing when the wider folder scope is enabled from a folder.
- Folder navigation now progressively shows discovered entries for large folders instead of holding the loading placeholder until listing and sorting are fully complete.
- New folder-open requests now interrupt stale queued folder-load work by clearing pending tasks, replacing the folder-load executor, and relying on generation checks so older work cannot overwrite the current folder.
- Search result rows now show the parent-folder path beneath the normal file metadata.
- Current-folder and All-folders file search no longer apply a result cap, so large folders are not cut off at the old 300-result limit.
- File-search loading now appears as a centered spinner over the main screen instead of a small progress indicator beside the sort button.
- Added right-edge drag fast scrolling to the main file list and recent-file list for large folders and unbounded search results.
- Centralized short feedback toasts through `ShortToast`, using an app-wide roughly 700ms display window and canceling stale short toasts before showing the next one.
- Added settings for main filter and viewer toolbar button/icon order. The main filter default order is now All / General / Archive / TXT / PDF / EPUB / Word / IMG, TXT default-visible toolbar slots are highlighted, and the order editor uses compact one-line rows with an internal scroll area.
- Simplified TXT search dialog bottom actions to plain text-style buttons and hid the IMG filter chip on the recent-files home list.
- Sort changes now reorder the currently loaded visible folder list instead of forcing a disk rescan, while active progressive folder loads read the latest sort mode before publishing final results.
- Cleaned recent/main fast-scroll track styling so the fast-scroll thumb remains available without a doubled scrollbar/dragbar track.

### Main screen drawer

- Improved tablet and large-screen drawer dismissal so tapping outside the open drawer or swiping left from the outside scrim closes the drawer reliably.
- Kept the bottom file-type sliding chips/search controls explicitly excluded from the full-screen drawer-open gesture so horizontal chip scrolling remains independent.
- Smoothed drawer bottom actions by letting File Open, Bookmarks, and Settings run after the drawer close transition starts instead of competing with the close animation.

## 2.2.1 - 2026-05-30
This package uses Android metadata `versionCode 2210` and `versionName "2.2.1"`.

### Main screen and file operations

- Added long-press actions for containing-folder jump, cut/copy queueing, archive extraction queueing, conflict handling, and same-folder overwrite/create-copy decisions.
- Unified pending cut/copy/extract jobs under one top-bar pending-action dropdown with inline cancellation and clear-all handling.
- Added archive browsing and extraction for ZIP/CBZ, encrypted ZIP/7z prompts, standard split ZIP handling, numeric `.001` split handling for supported archive families, TAR-family formats, and single-file compressor streams (`.gz`, `.bz2`, `.xz`, `.lzma`, `.Z`). RAR/CBR and lzip `.lz` remain unsupported.
- Added image entries from archive/comic flows into a continuous image sequence, including remembered CBZ reopen position and recursive image ordering under the current archive folder.
- Updated the main quick-search type chips with five compact visible slots, Archive and Image filters, drawer-gesture blocking across the full filter strip, and release-only snap behavior to avoid drag jitter.
- Fixed tablet/large-screen drawer selection behavior so drawer shortcuts, drawer recent folders, and recent-file taps close the drawer instead of leaving a stale visible drawer panel.
- Kept the multi-file selection dropdown compact at 150dp, removed its outline, removed internal scroll/height caps, and let all actions show at once.
- Tuned main long-hold action sheets so the regular file/folder popup uses the narrower stable-dialog width, preserves full long filenames by wrapping, and keeps action rows scrollable where needed.

### TXT reader continuity and bookmarks

- Reduced first-open TXT flicker by keeping the loading overlay until the initial text layout and saved-position restore pass complete.
- Consolidated large-TXT final page movement, slider/go-to-page movement, bookmark jumps, partition switching, exact-anchor lookup, and page-model math into dedicated controllers and utility logic.
- Added `ReaderBookmarkPageModelController` and `ReaderBookmarkNavigator` so bookmark save/update/restore paths prefer the same final large-TXT page model used by slider and Go to Page.
- Kept legacy bookmark anchor fallback behavior for stale layout signatures and older bookmark metadata.
- Preserved the rule that lookahead/lookbehind buffers are continuity aids for manual scroll, not independent page-count sources.

### Image viewer

- Improved zoomed-image movement with smoother pan handling, fling inertia, bounds clamping, parent-intercept suppression while zoomed, and fling cancellation on new touch/scale/image changes.
- Moved image-info dialog construction and styling into `ImageDialogStyleController`.

### Refactor and maintainability

- Refactored `MainActivity`, `ReaderActivity`, `DocumentPageActivity`, `PdfReaderActivity`, and `SettingsActivity` toward controller/helper-based shells while preserving existing user-facing behavior.
- Split main theme, selection mode, drawer rendering, drawer gesture handling, archive extraction, clipboard/pending actions, share, confirmation dialogs, image opening, and dropdown styling into focused controllers/helpers.
- Split TXT reader chrome/loading, lifecycle, memory, file loading/apply, large-text state/cache/read/prefetch/paging/jump/partition handoff, page jump, bookmark action/navigation/page-model, search, tap navigation, seek, preferences, toolbar, appearance, font, display-rule, and tools dialogs into dedicated controllers.
- Centralized adaptive bottom-dialog window creation and multi-window height limiting in `AdaptiveDialogLayoutHelper`.
- Current large activity sizes are roughly: `MainActivity` 1,900 lines, `ReaderActivity` 1,463 lines, `DocumentPageActivity` 1,937 lines, `PdfReaderActivity` 1,881 lines, and `SettingsActivity` 1,421 lines.

### Tests

- Added Android instrumentation coverage for `CustomReaderView` TXT paging continuity: forward taps, backward taps, exact page anchors, overlap behavior, and partition seam handoff.
- Added unit coverage for large-TXT continuity math, exact page index state, page direction state, page-model math, partition switching, tap zones, file utilities, and image-sequence state.
- Optional real large-TXT fixture checks remain local-only and are skipped when `app/src/androidTest/assets/large_txt_real_fixture.txt` is absent.

## 2.2.0 - 2026-05-28
This package uses Android metadata `versionCode 2200` and `versionName "2.2.0"`.

### CJK encoding disambiguation

- Vietnamese remains supported when the detector/scorer path identifies `windows-1258`; however, if ICU/Mozilla explicitly reports a Western family such as `windows-1252`, the current policy accepts that detector family rather than applying a separate Vietnamese override.
- Fixed a family of detection weaknesses where East Asian and other multibyte text could be misread as the wrong family because single-byte and CP949 misdecodes produce "clean"-looking but wrong characters that the scorer rewarded.
- Encoding-family conflict resolution now respects confident ICU/Mozilla family hints instead of forcing a fixed regional priority order. CJK hints still protect multibyte Chinese/Japanese/Korean text from single-byte theft, while confident single-byte hints for Western Latin, Greek, Cyrillic, Hebrew, Arabic, Thai, or Vietnamese are accepted when the detector path is not weak or internally conflicting. Weak US-ASCII fallback hints are still ignored.
- Chinese legacy text (GBK / GB18030 / Big5) is no longer misidentified as Korean `windows-949`. Reading Chinese bytes through CP949 yields many incidental Hangul code points; a cross-check now decodes the same bytes through the strongest available Chinese charset and, together with ICU/Mozilla detector agreement, suppresses the spurious Korean signal so the correct Chinese family wins. Genuine Hangul-dominant Korean text is unaffected.
- Generalized this into a detector-driven guard: when ICU or Mozilla reports a multibyte CJK encoding but a candidate is a single-byte alphabetic code page (Cyrillic, Greek, Hebrew, Arabic, Thai, Western, Vietnamese), that candidate is penalized by an amount that scales with length, so CJK text cannot win as "clean" single-byte text on raw character-count bonuses. GBK / GB18030 / Big5 now resolve to Chinese across short, medium, and long samples.
- Short high-byte samples (titles, first lines, memos) no longer scatter across single-byte code pages: when a short sample decodes cleanly as a multibyte CJK charset, single-byte families are penalized so the multibyte interpretation is preferred. Short Korean CP949 titles now resolve to Korean instead of Thai/Greek/Cyrillic/Western, while sufficiently long direct detector matches for real short single-byte text, such as Hebrew Windows-1255, are protected from the CJK short-sample guard.
- `detectWithAndroidIcu` no longer discards a high-confidence (>= 90) ICU result solely because the decoded text contains literal control bytes, which survive any decode and carry little discriminating power. Stateful ISO-2022 encodings remain excluded from this leniency.
- Extended detector-driven disambiguation to single-byte scripts without a fixed regional override: when ICU/Mozilla confidently identifies one single-byte family and the detector path is not conflicting, other single-byte families are penalized by sample length so the detector-identified script can win over raw character-count bonuses. If ICU and Mozilla disagree on different confident single-byte families, the fixed priority shortcut is not used and normal accuracy scoring decides. Weak US-ASCII-style Western fallback hints are still ignored.
- A confident Western pick (an explicitly named Windows-1252 / ISO-8859-1 / ISO-8859-15 / MacRoman hint) is respected so genuine Western Latin text is no longer pulled into Greek or Cyrillic. Weak US-ASCII-style fallback hints remain ignored, but concrete ICU/Mozilla single-byte family hints are not overridden by a separate regional priority rule. This intentionally favors detector-confirmed Western text over Southeast Asian-looking alternatives when the detector path is explicit.

### Tests

- Added regression coverage for Chinese GBK / GB18030 / Big5 across short, mid-length ~800 byte, and long samples; short Korean CP949 titles/sentences; and existing Korean, Japanese, Cyrillic, and Unicode paths. These tests cover the Android-ICU-present path and the Android-ICU-absent path where Mozilla/JUniversalChardet still supplies detector hints; they do not claim perfect Chinese classification from the internal fallback-only scorer when both detector paths are unavailable.
- Two pre-existing escape-sequence tests (`detect_koreanWindows949WithIso2022JpEscape`, `detect_koreanWindows949WithIso2022KrDesignation`) are `@Ignore`d. ICU now correctly identifies the input as EUC-KR, but base scoring still favors the windows-874 misdecode for text saturated with hundreds of literal ESC bytes — a synthetic pattern that does not occur in real Korean files. The ISO-2022 misdetection these tests guard against does not occur. Tracked for a future base-scoring refinement.

### Encoding detection hardening

- Tightened stateful 7-bit East Asian automatic detection so HZ-GB-2312, ISO-2022-JP, and ISO-2022-KR require their concrete shift/designation signatures and valid byte-pair structure before auto-selection.
- Prevented Korean Windows-949/CP949 and other legacy text containing short `~{` or `ESC`-like ASCII from being stolen by HZ/ISO-2022 detector hints or automatic legacy scoring.
- Manual HZ-GB-2312, ISO-2022-JP, and ISO-2022-KR selection remains available.
- Hardened no-BOM UTF-16 heuristics by requiring stronger zero-byte lane evidence, stronger decoded-text plausibility, and a clear endian winner before returning UTF-16LE/BE.

### Brightness override

- Fixed the TXT brightness dialog so moving the slider updates the TXT window brightness only while **Override system brightness** / **시스템 밝기 대신 사용** is enabled.
- Turning the override on immediately applies the saved app brightness, while turning it off clears the TXT window override back to system brightness.
- PDF, EPUB, and Word readers stay on system brightness instead of inheriting TXT brightness override settings.

### Recent-list progress and TXT auto-turn reading mode

- Recent-file rows now show a compact reading-progress percentage beside files with saved reading state, using the cached page/total or saved character position without affecting normal folder rows. Long filenames remain clipped inside their row text area so the progress badge does not cover partially visible title text.
- Starting TXT Auto Page Turn now closes the bottom toolbar and returns the TXT viewer to body reading mode before the timer begins.

### Main folder action UI

- Restored the main folder overflow as a compact 170dp popup anchored to the three-dot button, with theme-colored surfaces, lightly rounded corners, no outline stroke, and only folder actions inside. Sort remains on the existing bottom sort button.
- Changed New Folder so selecting it from the overflow popup opens the same rounded main-theme UI style used by the other file/folder action dialogs, while Show hidden files toggles on/off in place without closing the popup and uses a compact muted O/X indicator in a fixed label/indicator column so English and Korean layouts stay aligned, and toggles hidden files by refreshing the current directory without clearing the visible list first.

## 2.1.9 - 2026-05-26

### Reading theme and TXT rule UI polish

- Finalized the Deep Navy palette and custom-theme matching so the main UI, drawer actions, shortcut boxes, file-type buttons, reading-theme cards, and selected states use the intended navy surfaces and contrast colors.
- Updated the custom reading-theme editor to follow the active main theme, keep the Save Theme action clear of the navigation bar, and use flat card-colored Choose Image / Clear / Save Theme buttons with lower corner radius and no stroke or shadow.
- Added a custom reading-theme toolbar background color control, using the same 6-digit RGB HEX/slider model as the existing background and text controls.
- TXT viewer title/page status text now follows the active reading-theme body text color, while the custom reading-theme toolbar color applies to the actual reader toolbar surface.
- Renamed the built-in Night Blue reading theme to Deep Navy and updated it to background `#050D23`, text `#C3D2EA`, and fixed toolbar color `#041630`; other built-in toolbar fallback colors adjust from the reading background hue with bright-background 0.25 inverse 4:2:4 intensity reduction, dark-background simple hue-preserving intensity increase, and Light/Dark-only 0.17 equal gray fallback.
- Standardized custom main-theme HEX inputs to 6-digit RGB only (`RRGGBB` or `#RRGGBB`), matching the 0-255 RGB slider model.
- Reading-theme selection cards now use tone-aware selected outlines: brighter than the normal outline on dark themes and darker than the normal outline on light themes, with the check mark synchronized to that selected outline color.
- TXT Display Rules and actual TXT edit confirmation dialogs now use consistent card/dialog surfaces, no-stroke action buttons, and reading-theme-based rule-card contrast inside the TXT viewer.
- TXT Display Rule inline controls such as Up / Down / Off / Delete now use transparent, no-stroke styling so the rule card surface carries the visual weight.
- PIN lock number-pad digits and the OK action text are larger for easier readability while keeping the OK action text-only.

- drawer inset handling now uses top padding plus a layered drawer background so the status area uses the app-bar color, the Recent folders header stays below it, and bottom drawer actions keep their normal position.
- Drawer status/top inset coloring now uses the app-bar color without inserting an in-content spacer, preserving bottom action placement.
- Drawer bottom inset below the fixed File Open / Bookmarks / Settings actions now keeps the normal drawer background while the top inset remains app-bar colored.
- Drawer recent-folder top inset now uses the main app bar color, while the Recent folders header itself uses the same surface as the main Recent files header.

- Recent-folder drawer top inset/header now matches the main Recent files header surface while folder rows keep the normal row surface.
- Kept the recent-folder header on the same surface as the main Recent files header while recent-folder rows use the same plain surface treatment as recent-read file rows.

- Drawer recent-folder rows no longer inherit the shortcut-box color setting; only the Recent folders header strip and the drawer top inset above it use the main Recent files header surface across all themes.
- Tuned Deep Navy outline, selected, file-type button, and selected file-type button colors to `#042045`, `#0A1D42`, `#06163A`, and `#0A2455`.
- Raised main file/folder long-hold dialogs, file info, recent-folder clear, and shortcut-remove dialogs slightly above the bottom file-type button area.
- Added a custom main-theme Drawer action icon color for the bottom drawer File Open, Bookmarks, and Settings icons.
- Matched Deep Navy drawer bottom action icons to the adjacent action text color.
- Removed the visible ripple/effect from the top language radio rows during main-theme changes.
- Drawer Recent folders header and its top inset now match the main Recent files header surface across all themes while keeping readable text contrast.
- Custom-theme file long-hold highlights now use a softened theme-derived selected color instead of the raw selected color.
- Custom main-theme RGB slider rows have wider vertical spacing for easier adjustment.
- File-row long-hold highlight now rebinds on theme changes, preventing stale pressed colors from carrying across Light/Dark/Deep Navy/Custom modes.
- Custom main-theme default base colors now follow the Deep Navy palette, including reading-card `#00091D` and shortcut-box `#001530` defaults.
- Restored rounded progress mapping for font-size and line-spacing sliders so reopened settings match the value shown while adjusting.

This package uses Android metadata `versionCode 2190` and `versionName "2.1.9"`.

### Large TXT final-page navigation

- Canonicalized large-TXT final-page programmatic navigation to the final exact page anchor instead of the physical visual EOF.
- Kept Go to Page, toolbar/slider jumps, tap paging, queued partition jumps, and final-partition fallback paging aligned to the same exact-anchor page model.
- Removed the 2.1.8 candidate's visual-EOF final-page clamp from ReaderActivity so the last page target remains consistent with bookmarks and exact page anchors.

### Code cleanup

- Removed unused private helper methods left over from older dialog positioning/styling, document spacing, bookmark beginner text, legacy encoding detection, and paging helper code paths.
- Removed additional unused ReaderActivity exact-index estimate helpers and redundant large-TXT search wrapper methods after moving search logic into helper classes.
- Reused the LargeTextSearchEngine full-file scan path for background large-TXT match counting, avoiding a second duplicate count loop.
- Applied main-theme dialog colors, including dark navy, to PIN setup/change, settings import, and settings reset confirmation flows.
- Changed the PIN lock OK action to text-only styling so only the label remains instead of a filled dark button.
- Set Settings reading-theme card surfaces explicitly: Light uses a near-white soft gray card (`#FEFEFE`), Dark uses a deeper near-black `#030303` card, and Deep Navy remains `#00091D`.
- Set navigation-drawer shortcut icon boxes to theme-matched surfaces, with Deep Navy using the fixed navy shortcut-box color #001530, and Custom main themes gaining separate Selected and Shortcut box color options.
- Updated the drawer bottom Open File, Bookmarks, and Settings actions so their icon and text colors follow the active main theme instead of staying on fixed gray colors.
- Added persistent inner padding to custom main theme HEX color fields so preview-card backgrounds no longer crowd the text.
- Kept this cleanup limited to dead private/wrapper code so settings, bookmarks, reader state, backup/import data, and public runtime behavior remain unchanged.

### Build metadata

- Updated Android app metadata to `versionCode 2190` and `versionName "2.1.9"`.

- Adjusted Deep Navy panel color to `#09122A`, tuned Deep Navy outline/selection/file-type chip colors, improved light-theme card surfaces, fixed selected file-type chip text contrast, made file long-press highlight follow the active main theme, and raised main file/folder action dialogs slightly above the bottom file-type button area.

## 2.1.8 - 2026-05-26
This package used Android metadata `versionCode 2180` and `versionName "2.1.8"`.

### Encoding detection test coverage

- Added JVM unit tests for TXT encoding detection covering empty input, Korean UTF-8, UTF-8 sample-boundary truncation, Korean legacy EUC-KR normalization to windows-949, UTF-16LE/BE BOM handling, and BOM-less UTF-16LE detection.

### Font scanning reliability

- Updated asynchronous font scanning to shut down its worker executor after completion and to deliver callbacks through the main looper without casting the supplied context to an Activity.

### ReaderActivity structure

- Split ReaderActivity timer/search bookkeeping by moving auto page-turn timing, large-TXT search result/cache helpers, and full-file large-TXT search scanning into focused helper classes while preserving existing page-turn and search behavior.

### Encoding scorer maintainability

- Named the existing encoding scorer weights as constants so future TXT encoding tuning can be reviewed without changing current scoring behavior.

### Defensive exception handling

- Narrowed selected best-effort `Throwable` catches in TXT encoding detector reflection, font handling, TXT page-index cache bookkeeping, and large-TXT font/cache signatures so `Error` subclasses are not silently swallowed.

### Backup compatibility

- Kept the existing model ProGuard keep rule unchanged so bookmark, reader-state, and settings backup/import compatibility remain conservative for this patch.

### Build metadata

- Updated Android app metadata to `versionCode 2180` and `versionName "2.1.8"`.

## 2.1.7 - 2026-05-26
This package uses Android metadata `versionCode 2170` and `versionName "2.1.7"`.

### TXT encoding coverage

- TXT automatic encoding detection is hardened for UTF-8 sample-boundary cases, UTF-16/UTF-32 BOM cases, BOM-less UTF-16 heuristics, Korean legacy TXT, Cyrillic legacy encodings, and additional legacy code pages.
- Automatic detection now combines Android ICU, Mozilla/JUniversalChardet, an internal accuracy scorer, UTF-8 safe-boundary trimming, and a per-file auto-encoding cache keyed by path, file size, and last-modified time.
- Korean legacy auto-detection normalizes EUC-KR/MS949/CP949-compatible results to windows-949 while keeping manual EUC-KR selection available.

### TXT encoding UI

- The TXT manual encoding picker uses a compact two-column layout with a fixed current-encoding status label, scrollable encoding cards, and a plain text Close button.

### Viewer dialogs and PDF More

- TXT/PDF/EPUB/Word File Info and Page Move action buttons are centered consistently.
- The TXT Page Move Go button is slightly raised without changing the action-row layout.
- The PDF More dialog no longer shows divider lines between Read Mode / Zoom Out and Reset Zoom / Settings.

### TXT Display Rules

- The TXT Display Rule add dialog uses Save / Cancel action ordering.

### Build metadata

- Updated Android app metadata to `versionCode 2170` and `versionName "2.1.7"`.
- Kept documented build toolchain at Android Gradle Plugin `9.1.1` and Gradle wrapper `9.3.1`.

## 2.1.6 - 2026-05-24
This package uses Android metadata `versionCode 2160` and `versionName "2.1.6"`.

### TXT encoding coverage

- TXT legacy encoding detection now uses accuracy-first family-level scoring across Korean, CJK, Cyrillic, Western, Greek, Hebrew, Arabic, and Thai candidates instead of returning immediately from a Korean/Cyrillic priority pre-pass.
- Detection sampling was expanded to 1 MiB to improve script-distribution accuracy; manual encoding remains available as a last-resort override.
- ISO-8859-5 Cyrillic TXT detection now runs a Cyrillic-specific pre-pass before ICU/general scoring so ISO-8859-5 files are not misread as Windows-1251/KOI8 mojibake.
- Expanded TXT encoding detection beyond CJK-focused legacy encodings to include Android ICU-assisted detection plus common Latin, Greek, Cyrillic, Turkish, Baltic, Hebrew, Arabic, and Thai single-byte encodings.
- Added BOM-less UTF-16LE/UTF-16BE detection before strict UTF-8 acceptance so no-BOM UTF-16 TXT files with many embedded NUL bytes no longer open as broken UTF-8 text.
- Updated fallback encoding scoring to consider broad alphabetic-script characters and common book punctuation, improving fallback selection for old TXT files across more writing systems.
- Retained strict UTF-8 preference for valid Unicode text and BOM-based UTF-8/UTF-16 handling for explicit Unicode files.
- Fixed decode-time fallback so UTF-8 BOM and selected UTF-8 results are authoritative; broad legacy candidate rescoring can no longer override them and produce mojibake in Korean UTF-8 text.

### TXT Unicode safety

- TXT bookmark excerpts, before/after anchor text, large-TXT exact anchors, generated page-anchor context, chunk boundaries, and long-press word extraction now use surrogate-safe substring boundaries.
- This prevents emoji and supplementary Unicode characters from being split into invalid UTF-16 halves while saving, exporting, rebuilding, or resolving bookmarks.

### Large TXT navigation guard

- Exact-completed tap navigation now uses the current exact-anchor interval instead of searching past the current position with a forward/backward tolerance. Forward tap advances to the immediate next exact anchor, and previous tap first returns to the current page start when the viewport is already inside that page.
- The same previous-tap snap behavior now applies during the pre-exact fallback path, using the local page-start line before switching to the previous page or previous partition.
- This prevents one-page skips when the user manually scrolls very close to the next page anchor or slightly below the current page anchor before tapping. Slider and Go to Page continue to use the raw exact page anchor table.

### Main dark navy theme and drawer polish

- Added a **Deep Navy** main UI theme option alongside Follow system, Light, and Dark.
- Added a **Custom** main UI theme option with HEX color fields for background, panel, app bar, text, secondary text, and outline colors.
- Deep Navy uses a separate high-contrast navy palette for the main file browser, drawer, search bar, chips, settings screen, and main rounded dialogs instead of simply tinting the existing black theme.
- File names, secondary metadata, drawer labels, headers, icons, controls, outlines, and selected chips use explicit contrast-safe colors so text does not blend into the navy background.
- Custom reading themes can now be edited with direct HEX color inputs in addition to RGB sliders for background and text colors.
- Deep Navy reading-theme preview cards use a darker navy card surface while keeping preview text and selected outlines readable.
- Drawer bottom actions, including File Open, Bookmarks, and Settings, use flat navy drawer rows instead of separate rounded card blocks.
- Drawer swipes update the drawer offset proportionally during the drag, use a lower start threshold with a mild drag gain, and keep direction consistent: the left drawer follows rightward horizontal swipes only.
- Drawer swipes are no longer limited to the left edge of the main screen. Releasing after pulling the drawer at least 30% open completes the open gesture; smaller partial pulls close cleanly back to the main screen.
- Starting a drawer swipe cancels pending main-list long-press actions and clears visible pressed row states, preventing long-hold popups from appearing after the drawer opens.
- Drawer item taps start navigation immediately while the drawer closes instead of waiting for the close animation to finish first.
- All Bookmarks page now follows the selected main theme palette, including Deep Navy, instead of staying on the old black surface.

### Build reliability

- Custom main theme now has a separate Reading theme card color option for the Settings reading-theme list rows/cards.
- Custom main theme HEX field previews now survive the global Settings recolor pass by excluding those six fields from generic EditText background tinting.
- Removed duplicate PrefsManager reader/layout/lock/sort/gesture preference methods from the restored API merge, fixing the duplicate-method Java compile errors.
- Restored the full PrefsManager API surface while keeping Custom main theme HEX/RGB color support, fixing missing-method Java compile errors.

- Custom main theme HEX fields now preview their own color directly, and the Settings text explains which UI surfaces each color controls.

### Build metadata

- Updated Android app metadata to `versionCode 2160` and `versionName "2.1.6"`.
- Kept documented build toolchain at Android Gradle Plugin `9.1.1` and Gradle wrapper `9.3.1`.

## 2.1.5 - 2026-05-22
This package uses Android metadata `versionCode 2150` and `versionName "2.1.5"`.

### Main file browser

- The main file-browser long-press Delete action now opens a narrower compact rounded reconfirmation dialog before physically deleting a file or folder from storage.
- The confirmation dialog uses stacked buttons: **Delete** on top and **Cancel** below.
- After a successful delete, the current main-screen state is refreshed: Recent/home reloads recent files, search results rerun the active query, folder browsing reloads the current directory, and drawer/recent-folder state is rebuilt.
- Deleted folders are removed from recent-folder, folder-shortcut, and last-directory state where applicable.

### Bookmark backup page-model summary

- Backup export now includes a large-TXT bookmark page-model summary count for current, stale/unknown, and not-yet-recalculated TXT bookmark page labels.
- Backup export does not open every TXT file after a partition-mode change; stale or unknown cached `Page X / Y` labels are marked and refresh when the relevant file is opened and exact anchors rebuild.
- TXT bookmark edit rows keep page-model status fields such as `cachedPageModelMatchesCurrentPartitionMode` and `requiresOpenFileRebuildForCurrentPageModel`.
- Bookmark position remains preserved through `charPosition`, line, and surrounding anchor text; `Page X / Y` remains display metadata that is trusted only when it matches the active partition page model.

### Background memory handling

- TXT and PDF viewers now use delayed background memory trimming instead of clearing heavy state immediately when the app is briefly switched away.
- Short app switches, Settings visits, and quick Home/app-switcher returns are protected by a 420-second grace delay; returning before the delay cancels the trim and keeps the current view responsive.
- If the app remains hidden beyond the grace window, or Android reports stronger memory pressure such as `TRIM_MEMORY_BACKGROUND` or `TRIM_MEMORY_RUNNING_LOW`, the viewers trim heavy state more aggressively.
- TXT saves the current character/page position and nearby anchor text, releases loaded text snapshots, large-TXT partition caches, exact page anchors, prefetch/search cache state, and in-flight generation tokens, then lazy-reloads the same file at the saved position on return.
- PDF recycles the current single-page bitmap and clears vertical-continuous bitmap caches after the same grace delay while keeping the PDF renderer itself available for faster redraw on return.

### Build metadata

- Updated Android app metadata to `versionCode 2150` and `versionName "2.1.5"`.
- Kept documented build toolchain at Android Gradle Plugin `9.1.1` and Gradle wrapper `9.3.1`.

## 2.1.4 - 2026-05-17
This package uses Android metadata `versionCode 2140` and `versionName "2.1.4"`.

### Large TXT partition modes and page model

- Added two Settings-selectable large-TXT partition tracks: Standard `4000/400` by default and High buffer `12000/600`.
- The selected buffer size is applied consistently to both lookahead and manual-scroll lookbehind: `400` lines in Standard mode and `600` lines in High buffer mode.
- Moved the large-TXT partition-mode selector below the TXT boundary sliders in Settings.
- Made the selected runtime canonical partition mode the official large-TXT page model for exact page count, tap next/previous, slider, Go to Page, bookmark, search jump, exact-anchor cache, and partition cache signatures.
- Changing the partition mode while a large TXT is open now invalidates stale exact-page denominators, refreshes the active partition under the new mode, and schedules a full exact page-index rebuild through the stable-layout gate.
- Partition mode, partition body length, and buffer length are included in page/index cache signatures so exact anchors and cached bookmark page metadata are not reused across incompatible models.

### Large TXT navigation and manual-scroll continuity

- After exact indexing is ready, tap next/previous follows the exact page-anchor table directly, selecting the next/previous anchor from the current absolute top character position.
- Slider and Go to Page use canonical body-partition exact anchors; manual-scroll seam buffers are not reused as page-number jump targets.
- Exact-completed slider and Go to Page jumps land on the raw exact page anchor directly, avoiding context-based shifts when repeated text appears nearby.
- Manual scroll can hand off to a prefetched neighboring partition after scroll settles while preserving the same absolute top position.
- Conservative top-based partition handoff remains the fallback when the next partition is not cached or cannot represent the current viewport safely.
- Runtime lookbehind remains a seam-handoff buffer only and does not inflate exact page counts or page-number navigation targets.

### TXT bookmarks and backup page-model state

- TXT bookmarks now store the page-layout signature that produced their cached page/total metadata.
- When switching between Standard `4000/400` and High buffer `12000/600`, stale TXT bookmark page totals are not displayed as current.
- When the current large-TXT exact anchor table is ready, existing bookmarks for the opened file are refreshed to the active partition mode's page/total count.
- TXT bookmark jumps keep absolute text/anchor restoration and ignore stale page-offset metadata saved under a different partition mode.
- Exported backups now include large-TXT bookmark page-model state, including the active partition mode, lookahead/lookbehind size, and whether cached TXT page metadata matches the current mode.
- TXT bookmark backup edits that move a bookmark by line/search/raw character clear stale cached page metadata/signatures so the viewer recalculates `Page X / Y` from the active partition mode.
- Backup edit guidance now explains that TXT `pageNumber` / `totalPages` are mode-dependent display metadata and that normal TXT bookmark edits should use `setLine`, `moveByLines`, or `findText`.
- Full backup schema marker is now `textview-full-backup-v10` with format version `8`.

### TXT layout and page-count stability

- Added an in-app TXT boundary note explaining that boundary sliders move the readable viewport inward while rendering and pagination snap to complete line boundaries.
- Explicit page-model changes invalidate stale exact indexes immediately, while general large-TXT exact page-count rebuilds remain behind the debounced layout-stability gate.
- Large-TXT exact indexing still discards stale-signature results and schedules a fresh stable-layout rebuild when needed.

### Build metadata

- Kept documented build toolchain at Android Gradle Plugin `9.1.1` and Gradle wrapper `9.3.1`.

## 2.1.2b

This package uses Android metadata `versionCode 2122` and `versionName "2.1.2b"`. It consolidates TXT Display Rules, actual-file editing, low-power TXT Auto Page Turn, large-TXT paging/search work, EPUB/Word Find cleanup, fixed-layout EPUB handling, and PDF zoom/pan tuning into one 2.1.2b release.

### Main file browser

- Added a right-side **← Parent folder** / **← 상위 폴더로** button to the main file-browser path bar.
- Tapping it moves to the current folder's parent without relying on Android Back.
- At a storage root, it returns to the Recent/home view.
- The button is hidden on the Recent screen and search-result screen.

### TXT Display Rules

- Added viewing-only TXT masking/replacement rules. Normal TXT Display Rules do not modify the source TXT file.
- Rules support enabled/disabled state, case-sensitive matching, plain-text matching, regular-expression matching, all-TXT scope, and current-file-only scope.
- Added quick rule creation from the TXT viewer through **More > Add display rule** and long-press word prefill.
- Added rule-source labeling, rule ordering with Up/Down controls, quick enable/disable/delete controls, and rounded delete-confirmation UI.
- Rule changes from the TXT rule manager apply after the rule/add window closes, keeping the rule window responsive while editing.
- Display rules are applied before visible TXT rendering, pagination, large-TXT partition rendering, exact page-index construction, search, and bookmark positioning.

### Edit Actual TXT File

- Added **Edit Actual TXT File** below **TXT Display Rules** in Settings when Settings is opened from a TXT viewer.
- The action permanently applies all enabled rules that apply to the current TXT file.
- Users can choose either original overwrite or copy mode.
- Original mode overwrites the opened TXT file, reloads the viewer, clears stale page/index state, and fully repaginates the TXT.
- Copy mode writes to `originalname_edited.txt` and overwrites the same edited-copy target instead of creating repeated numbered copies.
- The destructive flow uses rounded dialogs, warning boxes, a second **Are you sure?** confirmation, and an emphasized **There is no turning back.** warning.
- Physical writes use a same-folder temporary file and replacement step to reduce partial-write risk.

### TXT Auto Page Turn

- Added low-power automatic page turning for TXT reading.
- The interval is entered in seconds per page.
- Auto Page Turn advances by one full page at each interval rather than continuously scrolling.
- Auto Page Turn stops at the final page, when the viewer leaves the foreground, or when the user manually scrolls, taps, drags, uses the slider, or uses Go to Page.
- A stopped message is shown when manual page movement interrupts Auto Page Turn.

### Large TXT page movement and exact indexing

- Large TXT active rendering remains based on fixed selected-size logical-line partitions.
- Page movement no longer waits for exact full-page indexing to finish.
- If exact anchors are still building or fail, Go to Page / slider movement falls back to an estimated selected-size partition jump.
- Replaced the memory-heavy full-file exact-index layout path with chunked line-based exact indexing.
- When chunked exact anchors finish, the viewer updates the total page count from estimated to exact.
- Added a layout-stability gate for large-TXT exact indexing: the first build and all rebuilds now wait until width, viewport height, vertical margin, overlap, and line spacing produce the same geometry signature across two debounce checks.
- If an exact-index build completes after the layout signature has changed, the result is discarded and a new stable-layout build is automatically scheduled instead of leaving the viewer on a stale or estimate-only total.
- Replaced `Typeface.hashCode()` in the large-TXT exact-index signature with a stable FontManager key based on the selected/backing font identity and style.
- Normalized TXT reader bottom padding with a canonical 60dp bottom band so navigation-bar inset timing does not change the pagination viewport used for large-TXT page counts.
- Manual scroll at large-TXT partition seams re-anchors lookahead text into its owning partition after scrolling settles, reducing repeated seam text during drag reading.
- Dragging downward at the top of a large-TXT partition can load the previous partition, matching backward tap behavior more closely.
- Sequential tap, volume-key, and auto-page forward movement use a guarded next-page anchor to reduce skip risk across partition seams while preserving configured overlap.
- The partition-aware exact map follows the sequential reading path instead of restoring the old full-file continuous count that was impractical for very large TXT files.

### Large TXT final-page fixes

- Added a dedicated final-page path for partitioned TXT files.
- Final-page movement loads or uses the real EOF partition instead of relying only on a global trailing-blank anchor.
- Fixed a case where manual scroll could reach the final page but tap/page-down stopped at the previous page.
- Final-page jumps can clamp to the physical visual EOF of the last partition when the final page is only a small EOF tail below the last anchor.
- If the final partition is already active, the viewer moves directly to visual EOF instead of reloading/rebuilding the partition, reducing final-page transition delay.

### TXT body search

- Large-TXT body search no longer searches only the currently loaded selected-size partition.
- Search scans the display-rule-applied TXT stream, finds the matching logical line, loads the owning partition, and moves to the result.
- Search does not need to wait for exact page indexing to finish.
- Large-TXT search uses a separate background executor so long exact-page-index builds do not block find-next/find-previous requests.
- Next/Previous search first checks the already-loaded partition and moves immediately when the next match is already in memory.
- When the total count is unknown, next/previous can show `n / …` first, then a separate background count pass updates it to `n / total` automatically.
- Added **Nth / n번째** search for jumping directly to a selected occurrence.
- Search-result jumps use a dedicated reveal position near the upper safe area, keeping matches below the top title/toolbar but high enough to avoid the lower Find popup.

### EPUB and Word reader

- Word same-page Previous/Next search uses native WebView `findNext()` / `FindListener` behavior only, with no custom reveal pass, forced ordinal correction, or DOM marker insertion.
- EPUB and Word Find avoid JavaScript/DOM marker layout edits and rely on native WebView Find behavior.
- Replaced Word and normal EPUB Find floating dialogs with an inline toolbar-level Find panel so search controls do not cover the WebView viewport.
- Kept fixed-layout EPUB Find as an overlay so opening Find does not shrink the WebView or push fixed-size pages down through layout reflow.
- Fixed-layout EPUB pages are detected through fixed-layout metadata / numeric viewport data, kept out of reader-theme reflow CSS, and centered as fixed-size pages during normal reading.
- While fixed-layout EPUB Find is open, the fixed page top is temporarily moved below the overlay panel; closing Find removes the temporary offset and restores normal centered placement.
- Kept the EPUB/Word bottom toolbar fixed while the soft keyboard is open in Find and other text-input dialogs.
- Stabilized EPUB/Word double-tap reset so app reset handling does not fight WebView native double-tap zoom behavior.

### PDF reader

- Corrected PDF zoom-reset scope: vertical continuous scroll keeps the current zoom, while horizontal page-swipe mode resets zoom to `1.0` when moving to another page.
- PDF page navigation preserves the current scroll offset, so page movement no longer forces the next page to top-left.
- Increased zoomed-page pan speed in PDF horizontal swipe mode.
- Horizontal and vertical in-page panning now both receive zoom-only acceleration.
- Kept the existing horizontal page-turn threshold unchanged.

### UI and Settings cleanup

- Applied rounded popup styling to the display-rule, actual-file edit, auto-page-turn, delete-confirmation, and settings-reset dialogs.
- Added **Reset settings** for restoring reader/app preferences while keeping user data such as bookmarks, reading positions, recent files, folder shortcuts, TXT Display Rules, custom themes, and PIN lock.
- Settings backup/export includes TXT Display Rules through the existing settings import/export path.

### Build toolchain

- Updated the documented Android build toolchain to match the project files: Android Gradle Plugin `9.1.1` and Gradle wrapper `9.3.1`.
- Kept Android metadata at `versionCode 2122` and `versionName "2.1.2b"`.

### Version metadata

- Updated Android version metadata to `versionCode 2122` and `versionName "2.1.2b"`.

## 2.1.1 - 2026-05-16
This entry shows the final functional difference from the uploaded **2.1.0** GitHub source package. It does not list every intermediate patch step, while older version entries remain below for full history.

### Large TXT paging and partitioning

- Changed large TXT active rendering to fixed **selected-size logical-line partitions** instead of the earlier estimated preview-window behavior.
- Expanded the lookahead region after each active partition according to the selected mode so partition-end pages can render smoothly.
- Added in-place partition switching so crossing a partition boundary behaves like a page turn instead of a visible file reload where possible.
- Added coverage-exact partition handoff: before the exact global index is ready, large-TXT forward page turns continue from the first line not displayed on the previous screen instead of jumping blindly to the next selected-size boundary. This prevents skipped content and prevents extra repeated displayed lines beyond the configured overlap at partition seams.
- Respected the configured page-overlap setting in large-TXT partition mode while preventing extra duplication at partition seams beyond the user-selected overlap.
- Preserved the displayed global page number during partition-boundary page turns when the exact large-TXT page index is still unavailable, preventing jumps such as `8082/8093` suddenly falling back to an earlier partition estimate.
- Hardened rapid backward page turns at partition boundaries by blocking stacked boundary reloads until the active partition swap finishes.
- Preserved the current displayed total page count during partition swaps instead of recomputing the denominator from the temporary active partition.
- Added neighbor partition prefetching and a small partition cache for smoother boundary crossing.
- Expanded the active partition cache and made prefetch direction-aware, so repeated forward paging prioritizes next/next-next partitions while repeated backward paging prioritizes previous/previous-previous partitions.
- Added exact-index-aware queued page taps while a partition swap is pending, so rapid page-key input can be resolved through exact anchors after the swap instead of stacking unsafe reloads.

### Large TXT exact page index

- Added a background exact page-anchor index for large TXT files.
- Optimized current-page lookup using page anchors instead of linear scanning.
- Improved toolbar slider, Go to Page, Page Up/Down, and bookmark jump accuracy after the exact index is ready.
- Prevented exact Go to Page from silently relying on estimated positions while the exact index is still unavailable.
- Added layout-signature and generation checks to discard stale exact page-index jobs when TXT layout geometry, font, overlap, or file metadata changes before indexing finishes.

### TXT toolbar, loading window, and status-bar spacing

- Fixed toolbar page slider snap-back during async large-TXT jumps.
- Added pending target-page state so slider and label stay on the user-selected destination while loading.
- Restyled the TXT loading window as a compact rounded, theme-aware panel.
- Used the loading window for uncached large-TXT slider, Go to Page, and bookmark jumps.
- Kept cached same-partition jumps immediate without unnecessary loading flashes.
- Stabilized TXT pagination against status-bar visibility changes by using status-bar-off top spacing as the canonical TXT content layout.
- Moved the TXT page-indicator row visually lower by one reader text row so the stable spacing does not make the indicator feel pinned to the top.

### Large TXT final-page behavior

- Kept the exact page index aligned with the full StaticLayout paging model used by the TXT renderer, so large-TXT page boundaries use the same visual layout model as the active viewer.
- Normalized EOF page status so the final visible document content reports the final page instead of stopping one page early.

### Bookmark backup editing

- Replaced the long `beginnerEditableBookmarks` export layout with a cleaner nested structure:

```json
"bookmarkEdits": {
  "beginner": [],
  "developer": []
}
```

- `bookmarkEdits.beginner` is intended for safe user edits such as memo, target line/page, relative movement, and TXT phrase search.
- `bookmarkEdits.developer` keeps repair-oriented fields such as raw character position, anchors, file identity, and internal metadata.
- Added shorter and clear bilingual English/Korean guidance for both beginner and developer sections.
- Kept import compatibility with the older 2.1.0 backup-edit fields.

### Bookmark jump anchoring

- Passed TXT bookmark anchor context during bookmark opening.
- Improved restoration to the intended passage after layout changes, large-TXT partition changes, or portable file rebinding.

### PDF viewer

- Made original-size PDF page swipes more sensitive.
- Relaxed original-size swipe direction strictness so slight diagonal motion is less likely to block a page turn.
- Removed unnecessary loading-spinner flashes during normal PDF page turns and zoom redraws.
- Kept initial PDF file loading feedback.
- Centered newly rendered pages after next/previous movement while zoomed in, instead of landing at the upper-left corner.

### EPUB reader

- Added EPUB page-direction settings for left-to-right and right-to-left reading.
- Renamed right-to-left EPUB wording to **Japanese-style** reading.
- Added EPUB transition-effect setting for slide or none.
- Made EPUB swipe direction and slide animation follow the selected reading direction.

### Memory and lifecycle hardening

- Cleared large-TXT partition caches, pending prefetch markers, exact page anchors, queued page deltas, and partition-switch state when the TXT reader releases memory.
- Invalidated large-TXT exact-index and partition-switch generations during TXT reader destruction so stale background work cannot reapply after the viewer closes.
- Switched background TXT file reads and exact-index reads to use the application context where possible, reducing temporary Activity retention risk without changing reader behavior.
- Cleared `CustomReaderView` page-anchor and search-highlight path state when text resources are released.

## 2.1.0 - 2026-05-15
This release consolidates the post-2.0.9 UI, package-name, bookmark, and backup-editing work into a GitHub-ready source package.

### Package identity

- Changed Android `namespace` and `applicationId` to `com.textview.reader`.
- Moved Java source packages to `com.textview.reader` and updated XML custom-view paths, ProGuard rules, README paths, and related references.
- Android treats this as a different app from legacy package builds. Use TextView backup/export in the old build and import it in this build to migrate app data.

### Dialog and popup UI

- Added adaptive rounded-popup sizing for constrained app windows such as split-screen, Samsung pop-up view, foldable half-window, and small-window modes.
- Preserved normal full-screen popup sizing; adaptive max-height/scroll behavior only activates when the app window is constrained.
- Centered popup/window headers across main-screen dialogs and TXT/PDF/EPUB/Word viewer dialogs.
- Removed shaded/ripple option-box effects from backup import and custom reading-theme action/delete dialogs.
- Made backup import and custom reading-theme action/delete dialogs compact at about 70% screen width.
- Kept rounded border-only styling for the affected Settings dialogs.

### Settings and update line

- Replaced the in-app GitHub update-check function with a static Settings release link: `Check updates at https://github.com/k1717/TextView-Reader/releases`.
- Tapping the release link copies it to the clipboard.
- Removed underline styling from the update link while keeping it pressable.
- Removed the in-app network update-check path; the app does not contact GitHub for updates.

### TXT reader and bookmark behavior

- Changed the default TXT tap-zone layout for new/fresh settings to horizontal: left = previous page, center = menu, right = next page.
- Fixed TXT bookmark saving so it samples the interior of the actual title-covered visual row instead of relying on raw scroll offsets.
- Avoided off-by-one bookmark saves caused by title-row boundary ambiguity.
- Added robust TXT bookmark anchors using saved character position plus nearby `anchorTextBefore` / `anchorTextAfter` context.
- TXT bookmark restore remains tied to the same text passage even after font size, line spacing, or boundary settings change.

### Portable bookmark identity and backup editing

- Added portable bookmark identity fields to backup JSON: `fileSizeBytes`, `quickFingerprint`, `fileIdentity`, and `localBindingPath`.
- Treats paths as local shortcuts; imported bookmarks can rebind to the same file after it is moved or restored on another device.
- Keeps normal bookmark loading fast: exact path lookup is used first, and quick fingerprint matching is lazy.
- Backup export filenames now use the timestamped format `textview_backup_year_month_day_hour_minute_second.json`.
- Backup schema updated to `textview-full-backup-v9`.
- Added bilingual read-first guidance and separated bookmark tutorial/edit sections.
- Added `beginnerEditableBookmarks` as the actual PC-edit area.
- Added beginner-edit support for TXT `setLine`, `moveByLines`, `findText`, `findOccurrence`, `findTextCaseSensitive`; PDF/Word `setPage`, `moveByPages`; and EPUB `setPageOrSection`, `moveByPages`.
- Import regenerates excerpts and anchor fields after beginner edits are applied.

## 2.0.9 - 2026-05-11
This entry lists the functional difference from **2.0.8** only. The 2.0.8 hardware-key, e-ink, toolbar animation, TXT reload, optimization, and Toast cleanup changes remain in the 2.0.8 entry below.

### TXT small-file row alignment

- Fixed very small TXT files so the first row aligns to the same visual row grid as normal TXT files instead of sitting lower when the content is shorter than the viewport.
- Kept very small TXT files as single-page content; this does not change paging logic for normal files.

### TXT page-boundary alignment

- Fixed a second-page-only TXT pagination case where page 2 could repeat page 1's last fully visible sentence after increasing the TXT bottom boundary.
- Page anchors now calculate page capacity from the actual visible layout top at each page anchor, including first-page visual compensation.

### Main-screen long-press dialog positioning

- Fixed hard-landing behavior for main-screen long-press follow-up windows: **Delete / 삭제**, **Rename / 이름 변경**, and **File Info / 파일 정보**.
- Moved the **Delete / 삭제** confirmation window slightly below center, while keeping it clearly above the **Rename / 이름 변경** window.
- Moved the **Rename / 이름 변경** window slightly upward from its previous bottom position.


### Main-screen folder loading

- Improved folder-opening responsiveness by loading directory contents on a background thread instead of blocking the UI thread.
- Added stale-load cancellation so older folder scans are ignored if the user navigates elsewhere before they finish.
- Switched full folder navigation updates to direct list replacement instead of DiffUtil animation to reduce delay on large folders.
- Kept existing sorting options, file-opening behavior, drawer shortcuts, and viewer logic unchanged.

## 2.0.8 - 2026-05-11
This entry lists the functional difference from **2.0.7** only. The full 2.0.7 UI/settings changes remain in the 2.0.7 entry below.

### E-ink and hardware page-turn support

- Hardened TXT bottom-toolbar tap handling for e-ink readers so toolbar buttons, including **More / 더보기**, are less likely to miss taps.
- Kept the TXT bottom toolbar in the front touch layer when visible.
- Expanded TXT hardware page-turn handling from volume keys only to common e-reader key codes, including Page Up/Down, D-pad keys, Space, media next/previous, L1/R1-style page buttons, and navigate previous/next.
- Consumed key-up events after page-turn handling to reduce accidental system volume changes on e-reader firmware.
- Applied the same **Volume Keys Page Up/Down** setting to **PDF, EPUB, and Word/DOCX** viewers.

### Toolbar interaction cleanup

- Removed hold/ripple animation from TXT, PDF, EPUB, and Word viewer bottom toolbar buttons.
- Disabled long-click/haptic behavior for the shared viewer toolbar button style while preserving normal tap actions.
- Kept the TXT e-ink touch fallback but removed the visible pressed-state animation.

### TXT viewer reload behavior

- Fixed TXT viewer return-from-theme/settings behavior so normal Activity recreation restores the loaded text from memory instead of reloading the text file from disk.
- Restored TXT state includes character position, search state, large-text preview state, file title, and page label.
- Kept disk reload as a fallback if the app process was killed and the memory snapshot is unavailable.

### Safe optimization cleanup

- Enabled release resource shrinking.
- Removed stale PdfBox ProGuard rules because the app uses Android `PdfRenderer`.
- Added a fast `hasRecentFiles()` check to avoid sorting all recent-file states when only checking whether any recent file exists.
- Replaced large-TXT preview stream skipping with direct file seeking.
- Avoided repeated full font-folder rescans once fonts have already been scanned in the current app session.
- Converted all remaining `Toast.LENGTH_LONG` messages to `Toast.LENGTH_SHORT`.

## 2.0.7 - 2026-05-11
### EPUB reader

- Moved EPUB boundary control from the viewer **More** menu into **Settings**.
- Added **Settings > EPUB layout** controls for separate left, right, top, and bottom EPUB reading boundaries.
- EPUB boundary sliders use raw px units, support **0px to 240px**, and move in **5px** steps.
- EPUB top/bottom boundaries now work as viewer-edge boundaries, matching the TXT boundary behavior instead of acting as HTML body padding.
- EPUB boundary changes apply when returning to the EPUB viewer from Settings.
- Fixed EPUB/Word page content so changing the reader theme in Settings refreshes the already-open WebView page after returning to the viewer, while preserving the current page/scroll position.
- Adjusted EPUB bottom boundary behavior so the visible bottom toolbar counts toward the requested boundary; extra bottom margin is only added when the boundary value is larger than the toolbar height.
- Added EPUB **More** menu controls for **Increase Font**, **Decrease Font**, and **Reset Font Size**; these adjust the EPUB WebView text zoom and refresh the current page.
- Moved shared **Font Size** and **Line Spacing** controls above the **TXT layout** section in Settings so TXT layout only contains TXT-specific boundary controls.
- Kept Word/DOCX padding unchanged; the EPUB layout controls only affect EPUB files.
- Added **Open File** next to **Close** in the PDF, EPUB, and Word **More** popups for quicker return to the file browser.
- Backup export/import now includes app settings and custom reading themes in addition to bookmarks and reading positions; lock PIN data is intentionally not written to plain JSON backups.
- Custom reading themes can now be long-pressed and removed through the same rounded popup UI used by the other theme windows.
- Fixed custom-theme option popups so they open directly in their final centered position instead of first appearing on the right and then snapping into place.
- Moved the custom-theme options and delete-confirmation popups slightly below center while keeping the no-jump rounded-window behavior.
- Centered custom-theme popup button labels inside their rounded button boxes.
- Centered main file-operation popup action labels inside their rounded boxes, including shortcut and clear/remove confirmation actions.
- Fixed the Theme Editor **Save Theme** button text contrast in light/main-white mode.

### TXT viewer UI

- Updated the TXT bottom overlay to better match the document viewer control style while keeping the TXT-specific Find, Page, Bookmark, Settings, and More controls.
- Kept the slider/button area continuous without adding a horizontal separator between the middle control area and the bottom button row.
- Removed the remaining horizontal divider from TXT popup bottom action areas so Page/More/File Info-style windows use the same continuous card surface as the PDF/EPUB/Word viewer dialogs.
- Matched TXT popup outer borders to the PDF/EPUB/Word viewer dialogs by using the same thin theme-derived outline instead of the heavier TXT-only border.
- Matched TXT popup text tone to PDF/EPUB/Word dialogs by using the active reader theme text color instead of a generic black/white readable fallback.
- Changed the TXT **Go to Page** popup action from **Close** to **Go**.
- Reordered TXT/PDF/EPUB/Word bookmark popups so the saved-bookmark list stays as the main section and the **Add current bookmark** button sits below it.
- Changed bookmark hints from inline collapse/expand text to a separate small rounded popup in TXT, PDF, EPUB, and Word viewers.
- Moved TXT toolbar-triggered popups and their More-menu subsections slightly lower while keeping bookmark windows at their existing bookmark position.
- Matched PDF/EPUB/Word toolbar-triggered Find, More, File Info, and font popup positions to the same bottom offset used by the Go to Page popup.
- Kept the Font picker horizontal header/action separators while reducing font-page outer and row-card borders to 1px so the outline looks thinner on high-density screens.
- Added **Reset Font Size** to the TXT viewer **More** popup.

## 2.0.6 - 2026-05-11
### TXT Viewer

- Added a file-title overlay under the top page indicator when the 5-button TXT control selector is visible.
- Kept the file title hidden in full viewer mode.
- Matched the title color to the active viewer font color and increased the title size to **14sp**.
- Reworked the title mask so it uses a stable first-row slot instead of following last-page scroll clamping.
- Fixed page-boundary behavior so the next page no longer repeats the previous page's last line.
- Fixed page-boundary behavior so page turns no longer skip a line between pages.
- Fixed first-page row-grid alignment so page 1 aligns more closely with page 2 and later pages.
- Fixed cases where the last page's upper text row could be slightly cut off.

### Loading Windows

- Updated TXT loading UI so the rotating loading window no longer appears as a hard black box.
- Made TXT loading background, text, and spinner tint follow the active viewer theme.
- Made PDF loading spinner background/tint blend with the active viewer theme.
- Made EPUB/Word loading spinner background/tint blend with the active viewer theme.

### PDF / EPUB / Word Toolbar Folding

- Added single-tap toolbar fold/return behavior for PDF, EPUB, and Word viewers.
- Single-tapping the document/page area now hides or restores the top toolbar and bottom control bar.
- Preserved existing double-tap behavior:
  - PDF double-tap zoom.
  - EPUB/Word double-tap reset/original-size behavior.
- Added folded-mode safe-area padding so content is not blocked by punch-hole/status-bar areas or the 3-button navigation bar.
- Added extra top/bottom folded-mode margin beyond the raw system insets for better readability; tuned to 6dp so it protects content without wasting too much screen space.

### PDF Zoom

- Improved PDF pinch-zoom so it preserves the selected pinch/focal spot instead of drifting toward the upper-left corner.
- Improved PDF More-menu Zoom In/Zoom Out/Reset behavior so zoom changes preserve the current viewport center.
- Preserved existing double-tap zoom focus behavior.

### TXT viewer UI

- Updated the TXT bottom overlay to better match the document viewer control style while keeping the TXT-specific Find, Page, Bookmark, Settings, and More controls.
- Kept the slider/button area continuous without adding a horizontal separator between the middle control area and the bottom button row.

## 2.0.5 - 2026-05-10
### Bookmark UI

- Added long-press bookmark-folder deletion in TXT, PDF, EPUB, and Word bookmark windows.
- Added rounded/bordered confirmation dialogs for bookmark-folder deletion.
- Kept viewer bookmark dialog height stable so adding the first bookmark does not bounce or resize the window.
- Kept empty bookmark dialogs at the same length, leaving the middle list area blank while keeping the Close button in place.
- Reduced TXT bookmark title/status spacing.
- Reordered viewer bookmark controls so the hint appears before the add-bookmark button.

### Dialog and card styling

- Retuned TXT bookmark cards to better match the tone used by PDF/EPUB/Word bookmark cards.
- Added spacing between expanded bookmark rows.
- Updated TXT More rows and subsections, including font windows, to use rounded card-style surfaces.
- Matched TXT Font/Add Font outer border width with the other stable reader dialogs.
- Retuned the Add Current Bookmark button style across TXT, PDF, EPUB, and Word.
- Stabilized the main Sort popup to avoid visible position movement after opening.

### Font management

- Added persistent multi-font support for user-added fonts.
- Added fonts now remain available after returning from the viewer and reopening the font picker.
- Added long-press removal for user-added fonts from the normal compact Font picker only.
- Kept the Add Font / All System Fonts window as add-only, so removed fonts can be retrieved later.
- Reset font selection to default if the selected added font is removed.

## 2.0.4 - 2026-05-10
### Bookmark stability and UI polish

- Main bookmark folders now default to collapsed/shrunk.
- Folder expand/shrink behavior remains fast with unnecessary list animation disabled.
- Bookmark edit memo dialogs use a more rounded custom bordered dialog style.
- TXT, PDF, EPUB, and Word bookmark memo edit dialogs now provide **Cancel**, **Clear memo**, and **Save** actions.
- Main bookmark delete/edit dialogs use the same stable custom dialog path to reduce first-open hard-edge or hard-landing glitches.
- Bookmark opening now uses a shared navigation path with null/empty file-path protection.

### Theme refresh and viewer popup fixes

- Viewers reload active theme state when returning from Settings or the theme editor.
- TXT, PDF, EPUB, and Word More dialogs refresh theme colors before drawing.
- PDF More now dismisses the old dialog before opening Settings or File Info, preventing stacked stale dialogs.
- Active theme saving now uses synchronous preference commit to avoid immediate-return race conditions.

## 2.0.3 - 2026-05-10
### EPUB / Word viewer

- Added a bottom-bar **Find/Search** button next to **Previous** / **Next**.
- Added EPUB/Word document search with a match counter, previous/next result navigation, and page-to-page wrapping.
- Updated the EPUB/Word search popup with TXT-style search input, cursor, and selection-handle behavior.
- Improved EPUB/Word font selection UI to follow the TXT reader font-window structure.
- Added **Default font** support.
  - EPUB prefers the file's declared font when available.
  - Word/DOCX detects document fonts and can use them as the default font.
- Removed unused More-menu zoom buttons from EPUB/Word while preserving double-tap reset/original-size behavior.
- Improved expanded-page edge swiping so page turns are more responsive while avoiding accidental same-drag page jumps.

### PDF viewer

- Improved vertical continuous-scroll mode.
- Fixed cases where vertical scrolling could show blank PDF pages.
- Improved zoom behavior in vertical PDF mode.
- Added horizontal panning for zoomed PDF pages in vertical mode.
- Increased horizontal pan speed for smoother movement.
- Improved bitmap/cache handling to reduce stale blank renders and memory pressure.
- Preserved the existing vertical Go-to-page behavior; the earlier repeated-snap Go-to-page change was not reintroduced.

### Popup / dialog UI

- Updated Word/EPUB/PDF popup widths to match the TXT viewer style, except bookmark dialogs.
- Improved More windows and subsection windows such as File information, Page move, Font picker, and the full system font list.
- Fixed popup hard-landing / diagonal-drop behavior.
- Fixed transparent popup background issues.

### Main file browser

- Improved the sort-window radio selection bubble placement so it sits more naturally inward.

## 2.0.2 - 2026-05-09
### Functional changes from 2.0.1

- Increased the huge TXT preview-only threshold from **20 MB** to **32 MB**.
  - This allows larger TXT files to stay in the fuller large-text path before falling back to preview-only mode.
- Added user folder shortcuts.
  - Long-press a folder in the file browser to add it as a drawer shortcut.
  - User-added shortcuts appear in the drawer below the built-in storage entries.
  - Long-press an added drawer shortcut to remove it.
  - Long-press the same folder again in the file browser to remove its shortcut.
- Improved drawer organization.
  - Built-in entries remain available.
  - User shortcuts are separated from recent folders.
  - Recent folders no longer duplicate user-added shortcuts.
  - Placeholder shortcut rows keep the drawer layout stable.
- Improved drawer navigation responsiveness.
  - Drawer entry navigation is queued until after the drawer close animation, reducing the perceived delay when opening heavy folders such as Downloads.
- Added PDF reading-mode control.
  - PDF can use horizontal page-slide mode.
  - PDF can use vertical continuous reading mode.
  - The selected PDF mode is saved in local preferences.
- Added TXT page indicator alignment controls.
  - Page indicator can be left, center, right, or hidden.
  - Hidden mode keeps the reserved indicator row so TXT content does not jump upward.
- Refined file search and file-type filtering.
  - The search clear button appears only when there is typed search text.
  - Selecting General/PDF/EPUB/Word without typed search no longer shows an unnecessary clear button.
  - Empty type filtering on the Recent page filters the recent list locally instead of starting a broad storage scan.
  - Returning from search/filter restores the prior home/folder location more reliably.
- Improved sorting behavior.
  - Folder sorting reloads the current directory instead of disturbing unrelated search roots.
  - Recent and folder lists scroll back to the top after sort/filter/list refreshes.
- Strengthened viewer lifecycle handling.
  - Added shared viewer registration to reduce stacked viewer instances across TXT, PDF, EPUB, and Word transitions.
  - TXT, PDF, and document viewers perform additional cleanup of callbacks, adapters, renderers, WebViews, and transient state.
- Improved document/PDF page swipe behavior.
  - Document page swipes better distinguish between page turns and internal page scrolling.
  - PDF page animation supports horizontal and vertical direction depending on reading mode.


### 2.0.2 stability refresh

- Fixed PDF **Go to Page** behavior in vertical continuous mode.
  - Page jumps now snap the RecyclerView directly to the requested page instead of landing between pages while page bitmaps are still binding.
  - Continuous PDF rows now keep stable estimated heights before their bitmaps finish rendering, reducing jump drift and visual reflow.
  - The current-page indicator is synchronized from the visible page closest to the viewport center.
- Improved PDF continuous-mode rendering stability.
  - Added bounded bitmap caching for continuous PDF pages.
  - Suppressed duplicate background renders for the same page/zoom/generation.
  - Prefetches the current page and neighboring pages around jumps.
  - Releases single-page bitmaps when switching to continuous mode and clears continuous-page bitmaps when switching back to horizontal mode.
- Strengthened PDF viewer lifecycle cleanup.
  - Removes RecyclerView scroll listeners on destroy.
  - Detaches the continuous adapter before releasing cached page bitmaps.
  - Cancels stale render generations so old background work cannot reattach bitmaps after the viewer is closed or a new PDF is opened.

## 2.0.1 - 2026-05-07
### Main file browser

- Home page recent files default to recently read first.
- Added a compact sort icon beside the file search field.
- Sort control works on both the home recent-files page and normal folder browsing.
- Added a separate recent-home sort preference.
- Kept drawer storage shortcuts fixed and made only recent-folder entries scrollable.
- Cached external storage detection to reduce drawer rebuild overhead.

### Reader and document viewer

- Reused viewer activities with `singleTop` / `onNewIntent` handling to avoid repeated viewer stacks.
- Improved lifecycle cleanup for handlers, executor services, PDF bitmaps/renderers, WebView resources, and selection state.
- Kept Word/DOCX rendering on WebView after testing showed selectable-TextView rendering degraded document layout.
- Improved DOCX/Word page behavior and left and right swipe page movement.

### Documentation and release hygiene

- Rebuilt the public documentation set with readable Markdown formatting.
- Added repository hygiene guidance and privacy notes.
- Expanded `.gitignore` for Android local/generated/private files.

### 2.1.0 - Beginner bookmark edit freedom expansion
- Expanded beginner bookmark backup editing fields beyond absolute position.
- TXT bookmark edits can now use exact line, relative line movement, or phrase search.
- Document bookmark edits can now use exact page/section plus relative movement.
- Added pending TXT edit resolution for imported backups when files are on a different device/path.

### Import dialog no-shade option boxes

- Removed the shade/ripple/elevation effect from the backup import confirmation option boxes.
- Kept the rounded box border style and Merge / Replace / Cancel behavior unchanged.

### Import dialog rounded UI
- Changed the backup import confirmation window from the default system AlertDialog to the same rounded custom settings dialog style used by the other Settings popups.
- Kept import behavior unchanged: Merge, Replace, and Cancel still perform the same actions.


### Settings dialog no-shade cleanup
- Removed the remaining shaded/ripple option-box style from custom reading-theme action/delete dialogs.
- Kept the rounded border-only style consistent with the import confirmation window.

### Compact custom-theme dialogs
- Made the custom reading-theme action and delete confirmation windows more compact horizontally, about 70% screen width.

### 2.1.0 import dialog compact width
- Made the Backup Import / 가져오기 confirmation dialog use the compact ~70% dialog width.
