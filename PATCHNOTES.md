# Patch Notes

## 2.2.2

This 2.2.2 package adds TXT text-to-speech on top of the 2.2.1 GitHub-ready source, with Android metadata `versionCode 2220` and `versionName "2.2.2"`.

### TXT text-to-speech

- Added a TTS button to the TXT bottom toolbar and a Text to speech row in the More dialog.
- Added current-page reading and continuous page-by-page reading through Android's built-in TTS engine.
- Expanded the saved TTS language selector with mainstream language presets across East Asian, European, South Asian, Southeast Asian, and Arabic-language use cases.
- Added saved voice model selection, speech speed, pitch, and Android TTS settings shortcut controls.
- Added an Android voice-data install shortcut for adding/downloading personal or third-party TTS voice packs through the system TTS engine.
- Continuous reading advances with the same TXT page movement path used by normal navigation, preserving large-TXT partition and displayed-page behavior.
- TTS stops on manual navigation, pause, and reader destruction, and the TTS engine is released with the reader lifecycle.
- Reworked TXT TTS playback around sentence-like speech segments, highlights the active segment in the TXT view, and stores the latest TTS file/page/character state for resume and notification playback control.
- Added foreground TTS notification controls and media-button handling for play/pause, stop, next page, and previous page, with a resume-from-saved-page action in the TTS dialog.
- Added a one-time Android 13+ notification permission request when TTS starts so notification controls are available where the OS requires explicit permission.
- TXT search highlights now use theme-blended translucent colors instead of a fixed yellow accent.
- Other visible TXT search matches are again shown with a distinct theme-derived secondary highlight, while the active match uses a stronger related tone.
- Brightened the built-in Deep Navy reading-theme body text from `#C3D2EA` to `#D7E4FA`.

### Image viewer

- Image swipe and slider navigation now follow the active file/archive sort mode for local folders and archive image sequences.
- Opening an image from main search/filter results now uses the visible image result set as the viewer sequence, so the slider count matches the IMG/search result list.
- Image viewer startup now prioritizes decoding the selected image and attaches the full slider/swipe sequence afterward through an in-memory handoff; image bounds are reserved up front so the image does not shift when the slider count finishes loading.
- Archive image viewing opens the selected ZIP/CBZ entry first, keeps the rest as archive metadata, lazily extracts later images only when needed, and prefetches neighboring entries.
- Large images use a quick preview decode first, then request a higher-detail decode when the user zooms; normal-sized images still load directly at original quality.
- Fixed the archive image viewer handoff path so large archive image sequences do not depend on oversized Intent extras and still retain lazy placeholder entries for next/previous navigation.
- Image preview/detail decoding now uses the final 12MP preview cap and 48MP detail cap policy: small/medium images stay original, large previews stay bounded, and zoom requests attempt higher detail with fallback instead of crashing the viewer.
- Image viewer no longer builds a temporary previous/current/next sequence; adjacent movement stays disabled until the full deferred sequence is ready.
- Main and recent file lists now expose a right-edge drag fast scroller for large folders and unbounded search results.
- Current-folder type filters keep folders above matching files, so filtered browsing remains navigable.
- Back navigation in filtered folder browsing now distinguishes activation context: if the filter was enabled in the current folder, Back clears it and moves to the parent; if the folder was entered with the filter already active, Back keeps the filter while moving up.
- Wider folder search now walks downward from the current folder instead of scanning parent roots, reducing unexpectedly large searches when toggling scope.
- Current-folder typed searches now use the same progressive result publishing as wider folder searches.
- Empty-query `All` scans now progressively publish results when using the wider folder scope.
- Large folder navigation now publishes partial directory entries while loading and applies the final sorted list afterward.
- Image fit bounds now avoid the top toolbar and bottom image slider while controls are visible, making page changes more stable.
- New images apply their base matrix immediately, preventing a one-frame bottom placement during image changes.
- Date sorting and file-row dates now use Android MediaStore download/added time first, falling back to filesystem created time and then modified time.
- Image info now separates filesystem modified time, created/downloaded time, and EXIF taken time, and normalizes EXIF date formatting.
- Replaced the rotate-arrow toolbar glyph with a rectangular screen icon for the portrait/landscape toggle.
- The portrait/landscape toggle icon now switches between portrait and landscape screen shapes to match the current orientation.
- Removed the portrait/landscape toast from the image viewer.
- Slightly reduced the image viewer title/subtitle text size.

### File browser

- APK and common video files, including MPEG transport stream `.ts` files, now appear in the All filter.
- APK and video short taps are routed to Android/external apps rather than TextView's internal readers.
- Added an icon search-scope toggle next to the main file search field, before Sort.
- Redrew the All-folders scope icon to remove darker edge seams caused by overlapping vector paths.
- File-search results now show each item's parent-folder path beneath the metadata line.
- Current-folder and All-folders file search no longer apply a result cap, so large folders are not cut off at 300 items.
- Moved file-search loading feedback from the sort row to a centered spinner over the main screen.
- Folder-open requests now take priority during progressive folder loading by clearing stale queued work and starting the newly selected folder on a fresh load executor.
- Centralized app-wide short feedback toasts through `ShortToast` and set the effective display window to about 700ms.
- Button / icon order can now be configured in Settings for the main filter strip and TXT / EPUB-Word / PDF viewer controls. The order editor uses compact one-line rows, and TXT default-visible slots are highlighted without heavy dark accent cards.
- TXT search dialog bottom actions are now plain text-style buttons, recent-file mode hides the IMG filter chip, sorting no longer rescans the current folder just to change order, and recent/main fast-scroll tracks no longer appear as double dragbars.

### Main screen drawer

- Improved tablet and large-screen drawer dismissal so outside taps and left swipes from the outside scrim close the open drawer reliably.
- Kept the bottom file-type sliding chips and search controls explicitly excluded from the full-screen drawer-open gesture.
- Smoothed drawer bottom actions by running File Open, Bookmarks, and Settings after the drawer close transition begins.

## 2.2.1

This 2.2.1 package is prepared for GitHub upload with Android metadata `versionCode 2210` and `versionName "2.2.1"`.

### Main screen, drawer, and file operations

- Added long-press actions for containing-folder jump, cut/copy queueing, archive extraction queueing, overwrite/create-copy conflict handling, and folder move bookmark rebinding.
- Unified pending cut/copy/extract jobs under one top-bar pending-action dropdown with inline cancellation and clear-all behavior.
- Added ZIP/CBZ archive browsing, encrypted ZIP/7z password prompts, standard split ZIP handling, numeric `.001` split support for supported archive families, TAR-family extraction, and single-file compressor streams (`.gz`, `.bz2`, `.xz`, `.lzma`, `.Z`). RAR/CBR and lzip `.lz` are still unsupported.
- Added Archive and Image quick filters, compact five-slot quick-filter layout, full filter-strip drawer-gesture blocking, and release-only snapping.
- Fixed tablet/large-screen drawer selection behavior so drawer shortcuts, drawer recent folders, and recent-file taps close a stale visible drawer panel.
- Kept the multi-file selection dropdown compact, borderless, non-scrollable, and height-wrapped so all actions are visible at once.

### TXT reader continuity and bookmark model

- Kept first-open TXT content hidden behind the loading overlay until initial layout and saved-position restore are ready.
- Moved large-TXT final page model, exact-anchor lookup, partition switching, page jump, bookmark navigation, and related state into focused controllers.
- Added bookmark page-model routing so bookmark save/update/restore uses the same final large-TXT page model as slider and Go to Page whenever that exact model is ready.
- Preserved legacy bookmark fallback and anchor-context restoration for older metadata or stale layout signatures.
- Kept lookahead/lookbehind as manual-scroll continuity buffers only; they are not used as separate page-count models.

### Image/archive reading

- Archive image entries can open into continuous image viewing with recursive internal ordering and remembered CBZ reopen position.
- Zoomed image pan now supports smoother drag, fling inertia, bounds clamping, and touch/scale cancellation safety.
- Image information dialog styling is centralized in `ImageDialogStyleController`.

### Refactor and tests

- `MainActivity`, `ReaderActivity`, `DocumentPageActivity`, `PdfReaderActivity`, and `SettingsActivity` are now substantially controller-based. The largest remaining activities are around or below 2,000 lines, with `ReaderActivity` reduced to a compact TXT shell around reader controllers.
- Added/expanded unit and instrumentation coverage for large-TXT continuity math, exact page-index state, page direction state, page-model math, partition switching, tap zones, image sequence state, file utilities, and `CustomReaderView` continuity.

## 2.2.0

### CJK encoding disambiguation

- Chinese GBK / GB18030 / Big5 text is no longer misidentified as Korean `windows-949` when CP949 misdecoding happens to produce many incidental Hangul code points.
- Encoding-family conflict resolution now respects confident ICU/Mozilla family hints instead of forcing a fixed regional priority order. CJK hints still protect multibyte Chinese/Japanese/Korean text from single-byte theft, while confident single-byte hints for Western Latin, Greek, Cyrillic, Hebrew, Arabic, Thai, or Vietnamese are accepted when the detector path is not weak or internally conflicting. Weak US-ASCII fallback hints are still ignored. Concrete Western detector hints are accepted rather than being overridden by a Vietnamese heuristic; this matches the current policy of trusting explicit ICU/Mozilla family results while still ignoring weak US-ASCII-style fallbacks.
- Vietnamese remains supported when the detector/scorer path identifies `windows-1258`; however, if ICU/Mozilla explicitly reports a Western family such as `windows-1252`, the current policy accepts that detector family rather than applying a separate Vietnamese override.
- Korean scoring now cross-checks Chinese legacy decodes and detector family hints before accepting a strong Hangul signal, preventing fake-Hangul output from overpowering the correct Chinese family.
- When Android ICU or Mozilla/JUniversalChardet reports a multibyte CJK family, alphabetic single-byte candidates such as Cyrillic, Greek, Hebrew, Arabic, Thai, Western, and Vietnamese receive a length-scaled penalty so CJK text does not win as apparently clean single-byte text.
- Short high-byte samples now prefer a clean multibyte CJK interpretation over weak single-byte guesses. Short Korean CP949 titles and first lines now resolve to Korean instead of Thai / Greek / Cyrillic / Western / Vietnamese, while sufficiently long direct detector matches for real short single-byte texts such as Hebrew are not overridden by the CJK short-sample guard.
- High-confidence Android ICU hints are no longer discarded only because the decoded text still contains literal control bytes; stateful ISO-2022 encodings remain excluded from this leniency and still require strict signatures.

### Encoding detection hardening

- Stateful 7-bit East Asian auto-detection now requires strict signatures: HZ-GB-2312 needs HZ shift-in/out, ISO-2022-JP needs real JIS state transitions, and ISO-2022-KR needs ESC designation plus SO/SI shifted byte pairs.
- Korean Windows-949/CP949 and other legacy samples that merely contain `~{` or `ESC`-like ASCII no longer get accepted as HZ/ISO-2022 by automatic detection or detector-family hints.
- Manual HZ-GB-2312, ISO-2022-JP, and ISO-2022-KR remain available in the encoding picker.
- No-BOM UTF-16 detection now requires stronger zero-lane evidence, stronger decoded-text plausibility, and a clear endian winner before returning UTF-16LE/BE.

### Tests and known limitation

- Added regression coverage for Chinese GBK / GB18030 / Big5 across short, mid-length, and long samples; short Korean CP949 title/sentence samples; and existing Korean, Japanese, Cyrillic, and Unicode paths.
- The CJK disambiguation tests are intended to cover the Android-ICU-present path and the Android-ICU-absent path where Mozilla/JUniversalChardet still provides detector hints. The package does not claim that the internal fallback-only scorer can perfectly classify Chinese legacy text when both detector paths are unavailable.
- Two synthetic ESC-heavy CP949 tests remain `@Ignore`d because the base scorer can still prefer a windows-874 misdecode when hundreds of literal ESC bytes are injected. The ISO-2022 misdetection itself is blocked by the strict stateful-signature guard, and the pattern is tracked as a low-risk future base-scoring refinement.

### Brightness dialog

- The TXT brightness slider now changes the actual screen brightness only when **Override system brightness** is enabled.
- Enabling the switch applies the saved brightness immediately; disabling it clears the TXT window override and returns to system brightness.
- PDF, EPUB, and Word readers stay on system brightness instead of inheriting TXT brightness override settings.

### Recent-list progress and TXT auto-turn reading mode

- Recent-file rows now show a compact reading-progress percentage beside files with saved reading state, using the cached page/total or saved character position without affecting normal folder rows. Long filenames remain clipped inside their row text area so the progress badge does not cover partially visible title text.
- Starting TXT Auto Page Turn now closes the bottom toolbar and returns the TXT viewer to body reading mode before the timer begins.

### Main folder action UI

- Restored the main folder overflow as a compact 170dp popup anchored to the three-dot button, with theme-colored surfaces, lightly rounded corners, no outline stroke, and only folder actions inside. Sort remains on the existing bottom sort button.
- Changed New Folder so selecting it from the overflow popup opens the same rounded main-theme UI style used by the other file/folder action dialogs, while Show hidden files toggles on/off in place without closing the popup and uses a compact muted O/X indicator in a fixed label/indicator column so English and Korean layouts stay aligned, and toggles hidden files by refreshing the current directory without clearing the visible list first.

### Build metadata

- Android app metadata is `versionCode 2200` and `versionName "2.2.0"`.

## 2.1.9

### Reading theme and TXT rule UI polish

- Finalized the Deep Navy/custom-theme color alignment for main surfaces, drawer bottom icons, selected states, file-type buttons, reading-theme cards, and shortcut boxes.
- The custom reading-theme editor now follows the active main theme surface colors, avoids navigation-bar overlap on the Save Theme button, and styles Choose Image / Clear / Save Theme as flat card-colored buttons with reduced rounding, no stroke, and no elevation.
- Custom reading themes now include a toolbar background color, edited with the same RGB HEX/slider controls as background and text colors.
- TXT viewer title/page status text now matches the reading-theme body text, while the custom reading-theme toolbar color applies to the actual reader toolbar surface.
- The built-in Night Blue reading theme is now Deep Navy with background `#050D23`, text `#C3D2EA`, and fixed toolbar color `#041630`; other built-in toolbar fallback colors derive from the reading background hue with bright-background 0.25 inverse 4:2:4 intensity reduction, dark-background simple hue-preserving intensity increase, and Light/Dark-only 0.17 equal gray fallback.
- Custom main-theme HEX inputs now accept only 6-digit RGB values (`RRGGBB` or `#RRGGBB`), matching the 0-255 RGB slider model.
- Selected reading-theme cards now use tone-aware outlines: lighter on dark themes, darker on light themes, and synchronized with the adjacent check mark color.
- TXT Display Rules and actual TXT edit/delete confirmation flows now use consistent dialog/card coloring and no-stroke action buttons.
- Reader-side TXT Display Rule cards now derive their contrast from the active reading theme, while inline Up / Down / Off / Delete controls remain transparent and stroke-free.
- PIN lock number-pad digits and the OK action text are larger for easier readability while keeping the OK action text-only.

- Light main-theme panel/header surfaces now use a slightly grayer soft gray (`#F0F1F4`) for better separation from near-white content rows.
- Renamed the former Dark Navy theme label to **Deep Navy** / **짙은 남색 테마**.
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

This 2.1.9 package is prepared as the current final-page navigation patch. It uses Android metadata `versionCode 2190` and `versionName "2.1.9"`.

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

- README, changelog, and patch notes match the project build files: Android Gradle Plugin `9.1.1` and Gradle wrapper `9.3.1`.
- Android app metadata is `versionCode 2190` and `versionName "2.1.9"`.

- Deep Navy panel color changed to `#09122A`; Deep Navy outline/selection/file-type chip colors were tuned; light main cards use a slightly grayer surface; selected file-type chip text now chooses readable dark/light foreground; file long-press highlight follows the active theme; main file/folder action dialogs sit slightly higher above the bottom file-type button area.

## 2.1.8

This 2.1.8 package was prepared as a maintenance and refactoring patch. It used Android metadata `versionCode 2180` and `versionName "2.1.8"`.

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

- Android app metadata is `versionCode 2180` and `versionName "2.1.8"`.

## 2.1.7

This 2.1.7 package is prepared as the current release. It uses Android metadata `versionCode 2170` and `versionName "2.1.7"`.

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

### Code cleanup

- Removed unused private helper methods left over from older dialog positioning/styling, document spacing, bookmark beginner text, legacy encoding detection, and paging helper code paths.
- Kept this cleanup limited to dead private code so settings, bookmarks, reader state, backup/import data, and public runtime behavior remain unchanged.

### Build metadata

- README, changelog, and patch notes match the project build files: Android Gradle Plugin `9.1.1` and Gradle wrapper `9.3.1`.
- Android app metadata is `versionCode 2170` and `versionName "2.1.7"`.

## 2.1.6

This 2.1.5 package is prepared for GitHub upload. It uses Android metadata `versionCode 2160` and `versionName "2.1.6"`.

### TXT encoding coverage

- TXT legacy encoding detection now uses accuracy-first family-level scoring across Korean, CJK, Cyrillic, Western, Greek, Hebrew, Arabic, and Thai candidates instead of returning immediately from a Korean/Cyrillic priority pre-pass.
- Detection sampling was expanded to 1 MiB to improve script-distribution accuracy; manual encoding remains available as a last-resort override.
- TXT encoding detection is no longer limited around UTF/CJK legacy candidates.
- ISO-8859-5 Cyrillic TXT detection now runs a Cyrillic-specific pre-pass before ICU/general scoring so ISO-8859-5 files are not misread as Windows-1251/KOI8 mojibake. It now uses Android ICU-assisted detection where available and includes common Latin, Greek, Cyrillic, Turkish, Baltic, Hebrew, Arabic, and Thai single-byte encodings.
- Added heuristic detection for BOM-less UTF-16LE/UTF-16BE before accepting strict-valid UTF-8, reducing broken no-BOM UTF-16 output with embedded NUL bytes.
- Android ICU-assisted detection is tried before fallback scoring; fallback scoring also counts broad alphabetic-script characters and common book punctuation for old TXT files across more writing systems.
- Strict valid UTF-8 is still preferred for explicit Unicode text, and BOM-based Unicode detection remains first.
- Decode-time fallback now trusts UTF-8 BOM and selected encoding results instead of rescoring the full file and accidentally reopening valid UTF-8 Korean text as legacy mojibake.

### TXT Unicode safety

- Bookmark excerpt, anchor-before/anchor-after, large-TXT exact anchor context, generated page-anchor context, text chunk splitting, and long-press word extraction use surrogate-safe substring boundaries.
- This avoids splitting emoji or supplementary Unicode characters into invalid UTF-16 halves while saving, exporting, rebuilding, resolving bookmarks, or extracting selected text.

### Large TXT tap/slider skip guard

- Exact-completed tap paging chooses the target from the current exact-anchor interval, not by applying tolerance directly to the current character position.
- Forward tap moves to the immediate next exact anchor; previous tap snaps back to the current exact page start when the current top row is already inside that page, then moves to the previous page on the next tap.
- The pre-exact fallback path applies the same previous-tap rule with local page-start anchors, including the first-page case before a previous-partition handoff.
- This keeps tap movement aligned with the same exact anchor table used by the toolbar slider and Go to Page while preventing anchor-nearby one-page skips.

### Main dark navy theme and drawer polish

- Added a **Deep Navy** app/main UI theme option to the Settings theme radio group.
- Added a **Custom** main UI theme option with HEX color fields for background, panel, app bar, text, secondary text, and outline colors.
- Custom reading themes now accept direct HEX color input in addition to RGB sliders for background and text colors.
- The theme uses a dedicated navy palette for background, app bar, panels, search field, file type chips, drawer rows, file-list rows, settings controls, and main rounded dialogs.
- Text, secondary text, headers, strokes, icons, and selected controls are assigned explicit contrast-safe colors so labels remain readable and do not visually merge into the navy surfaces.
- Deep Navy reading-theme preview cards use a darker navy card surface. Drawer bottom actions use flat drawer rows across all main themes instead of separate rounded card blocks.
- Drawer swipes update the drawer offset proportionally during the drag, use a lower start threshold with mild drag gain, and keep direction consistent: the left drawer follows rightward horizontal swipes only.
- Drawer swipes are no longer limited to the left edge of the main screen. Releasing after pulling the drawer at least 30% open completes the open gesture; smaller partial pulls close cleanly back to the main screen.
- Starting a drawer swipe cancels pending main-list long-press actions and clears visible pressed row states.
- Drawer item taps start navigation immediately while the drawer closes.
- All Bookmarks page now follows the selected main theme palette, including Deep Navy, instead of staying on the old black surface.

### Build reliability

- Custom main theme now has a separate Reading theme card color option for the Settings reading-theme list rows/cards.
- Custom main theme HEX field previews now survive the global Settings recolor pass by excluding those six fields from generic EditText background tinting.
- Removed duplicate PrefsManager reader/layout/lock/sort/gesture preference methods from the restored API merge, fixing the duplicate-method Java compile errors.
- Restored the full PrefsManager API surface while keeping Custom main theme HEX/RGB color support, fixing missing-method Java compile errors.

- Custom main theme HEX fields now preview their own color directly, and the Settings text explains which UI surfaces each color controls.

### Code cleanup

- Removed unused private helper methods left over from older dialog positioning/styling, document spacing, bookmark beginner text, legacy encoding detection, and paging helper code paths.
- Kept this cleanup limited to dead private code so settings, bookmarks, reader state, backup/import data, and public runtime behavior remain unchanged.

### Build metadata

- README, changelog, and patch notes match the project build files: Android Gradle Plugin `9.1.1` and Gradle wrapper `9.3.1`.
- Android app metadata is `versionCode 2160` and `versionName "2.1.6"`.

## 2.1.5

This 2.1.5 package is prepared for GitHub upload. It uses Android metadata `versionCode 2150` and `versionName "2.1.5"`.

### Main file-browser delete confirmation

- The main file-browser long-press Delete action now opens a compact rounded reconfirmation dialog before deleting a file or folder from storage.
- The dialog is narrower than the earlier full-width version and uses stacked actions: **Delete** on top and **Cancel** below.
- The dialog includes the selected file/folder name and a warning that deletion cannot be undone.
- After deletion, the current screen state is refreshed: Recent/home reloads recent files, search results rerun the active query, folder browsing reloads the current directory, and drawer/recent-folder state is rebuilt.
- Deleted folders are removed from recent-folder, folder-shortcut, and last-directory state where applicable.

### Bookmark backup page-model summary

- Backup export includes `largeTxtBookmarkPageModelSummary` for current, stale/unknown, and not-yet-recalculated TXT bookmark page labels.
- Backup export does not open every TXT file to force page/total recalculation after a partition-mode change.
- TXT bookmark edit rows keep status fields including `cachedPageModelMatchesCurrentPartitionMode` and `requiresOpenFileRebuildForCurrentPageModel`.
- Stale or unknown TXT `Page X / Y` values are marked as display metadata and refresh after the relevant TXT file is opened and exact anchors rebuild.
- Bookmark location remains preserved through `charPosition`, line, and surrounding anchor text.

### Background memory handling

- TXT and PDF viewers use delayed background memory trimming instead of clearing heavy state immediately on a brief app switch.
- A 420-second grace delay protects short app switches, Settings visits, and quick Home/app-switcher returns.
- Returning before the delay cancels the trim and keeps the current view warm.
- If the app remains hidden beyond the grace window, or Android reports stronger memory pressure such as `TRIM_MEMORY_BACKGROUND` or `TRIM_MEMORY_RUNNING_LOW`, the viewer trims heavy state more aggressively.
- TXT saves the current character/page position and nearby anchor text, releases loaded text snapshots, large-TXT partition caches, exact page anchors, prefetch/search state, and in-flight generation tokens, then lazy-reloads the same file at the saved position on return.
- PDF recycles the current single-page bitmap and clears vertical-continuous bitmap caches while keeping the PDF renderer itself available for faster redraw.

### Code cleanup

- Removed unused private helper methods left over from older dialog positioning/styling, document spacing, bookmark beginner text, legacy encoding detection, and paging helper code paths.
- Kept this cleanup limited to dead private code so settings, bookmarks, reader state, backup/import data, and public runtime behavior remain unchanged.

### Build metadata

- README, changelog, and patch notes match the project build files: Android Gradle Plugin `9.1.1` and Gradle wrapper `9.3.1`.
- Android app metadata is `versionCode 2150` and `versionName "2.1.5"`.

## 2.1.4

This section remains the 2.1.4 release history. It is not folded into later release sections.

### Large TXT partition modes and exact page model

- Added a two-track large-TXT partition policy in Settings.
  - Standard: `4000/400` lines, used by default.
  - High buffer: `12000/600` lines.
- The second value is used for both lookahead and manual-scroll lookbehind.
- Moved the large-TXT partition-mode selector below the TXT boundary sliders.
- Large TXT exact anchors, tap paging, slider, Go to Page, bookmarks, search jumps, partition cache signatures, and runtime partition switching read the same selected partition policy.
- The selected runtime canonical partition model is the official large-TXT page model, so page count and navigation agree with the rendered partition model.
- Switching between Standard and High buffer modes invalidates the previous exact-page denominator, refreshes the current partition under the selected mode, and schedules a full exact page-index rebuild through the layout-stability gate.
- Partition mode, body-line count, and buffer-line count are included in page/index signatures so incompatible exact anchors are not reused.

### Large TXT tap, slider, Go to Page, and manual-scroll continuity

- Exact-completed tap next/previous movement uses the exact page-anchor table. The reader selects the next/previous exact anchor from the current absolute top character position.
- Exact-completed slider and Go to Page movement uses the raw exact page-anchor char position directly.
- Manual-scroll lookbehind windows are stored separately and are not reused for page-number jumps, bookmarks, search jumps, or tap-page movement.
- Manual scroll can hand off to a prefetched next/previous partition after scroll settles while preserving the same absolute top position.
- If the neighboring partition is not cached or cannot represent the current viewport safely, the conservative top-based handoff path remains in use.
- Runtime lookbehind is only for manual seam handoff and does not inflate exact page-count indexing.

### TXT bookmark and backup page-model handling

- TXT bookmarks now store the page-layout signature that produced their cached page/total values.
- Switching between Standard `4000/400` and High buffer `12000/600` does not reuse stale TXT bookmark totals.
- When the current large-TXT exact anchor table is ready, existing bookmarks for the opened file are refreshed to the current partition mode's page/total count.
- Bookmark lists show TXT `Page current / total` only when the saved page metadata matches the active page-model signature.
- Bookmark jumps keep absolute text/anchor restore and discard stale page-offset metadata from a different partition mode.
- Exported backups now include explicit large-TXT bookmark page-model state: active partition mode, lookahead/lookbehind size, and whether cached `Page X / Y` matches the current mode.
- TXT bookmark backup edits that move a bookmark by line/search/raw character clear stale cached page metadata/signatures so `Page X / Y` is rebuilt from the active model.
- Backup edit guidance explains that TXT `pageNumber` / `totalPages` are cached display metadata tied to the selected partition mode; normal TXT bookmark edits should use `setLine`, `moveByLines`, or `findText`.
- Full backup schema marker is now `textview-full-backup-v10` with format version `8`.

### TXT boundary note and page-count stability

- Added a TXT layout boundary note in Settings explaining that boundary sliders move the readable viewport inward, but rendering and pagination snap to complete line boundaries to avoid half-cut rows.
- Explicit TXT page-model changes invalidate stale exact indexes immediately and schedule exact-anchor rebuilds through the stable-layout gate.
- Large-TXT exact indexing still waits for stable layout geometry and discards stale-signature results.

### Main browser parent-folder button

- Added a right-side **← Parent folder** / **← 상위 폴더로** button in the main file-browser path bar.
- The button moves to the current folder's parent and returns to Recent/home at a storage root.
- The button is hidden on the Recent screen and search-result screen.

### TXT Display Rules

- Added non-destructive TXT display rules for masking/replacing text while reading TXT files.
- Rules support enabled/disabled state, case sensitivity, regex/plain-text mode, global TXT scope, and current-file-only scope.
- Rules can be created from Settings or directly from the TXT viewer through **More > Add display rule** and long-press word prefill.
- Rules can show the source file they were created from.
- Up/Down ordering controls preserve top-to-bottom rule application.
- Delete actions use rounded confirmation UI.
- Rule edits apply after the rule/add window closes, keeping the manager responsive during editing.
- Display rules are applied before pagination, partition rendering, exact page indexing, search, and bookmark positioning.

### Edit Actual TXT File

- Added **Edit Actual TXT File** below **TXT Display Rules** in Settings when Settings is opened from a TXT viewer.
- The action permanently applies all enabled rules that apply to the current TXT file.
- Users can overwrite the original TXT file or write to `originalname_edited.txt`.
- Copy mode overwrites the same `*_edited.txt` target instead of creating repeated numbered copies.
- Original mode reloads and fully repaginates the opened viewer after the write succeeds.
- The destructive flow uses rounded dialogs, warning boxes, a second **Are you sure?** step, and an emphasized **There is no turning back.** warning.
- Physical writes use a same-folder temporary file and replacement step to reduce partial-write risk.

### TXT Auto Page Turn

- Added low-power automatic page turning for TXT reading.
- The interval is entered in seconds per page.
- Auto Page Turn advances by one full page at each interval.
- It stops at the final page, when the viewer leaves the foreground, or when the user manually scrolls/moves the page.
- A stopped message is shown when manual page movement interrupts Auto Page Turn.

### Large TXT page movement, search, and final page

- Large TXT page movement no longer waits for exact full-page indexing.
- If exact anchors are still building or fail, Go to Page / slider movement falls back to an estimated selected-size partition jump.
- Replaced the memory-heavy full-file exact-index layout path with chunked line-based exact indexing.
- Exact page anchors are used automatically after background indexing is ready.
- Added a dedicated final-page path for partitioned TXT files so the EOF partition and physical visual EOF remain reachable.
- Large-TXT body search scans the display-rule-applied TXT stream instead of only the currently loaded partition.
- Search finds the matching logical line, loads the owning partition, and moves to the result without waiting for exact page indexing.
- Added **Nth / n번째** search to jump directly to a specific occurrence.

### EPUB, Word, and PDF reader cleanup

- EPUB and Word Find avoid JavaScript/DOM marker layout edits and rely on native WebView Find behavior.
- Word same-page Previous/Next search uses native WebView `findNext()` / `FindListener` behavior.
- Word and normal EPUB Find use an inline toolbar-level panel so controls do not float over the WebView search target.
- Fixed-layout EPUB Find uses an overlay panel so opening Find does not shrink the WebView or reflow fixed-size pages downward.
- Fixed-layout EPUB pages are detected through fixed-layout metadata / numeric viewport data and centered as fixed-size pages during normal reading.
- EPUB/Word bottom toolbar stays fixed while the soft keyboard is open.
- EPUB/Word double-tap reset is stabilized so app reset handling does not conflict with WebView native double-tap zoom.
- PDF horizontal page-swipe mode resets zoom to `1.0` when moving to another page, while vertical continuous scroll keeps the current zoom.
- PDF page movement preserves scroll offset instead of forcing a top-left snap.
- Increased zoomed PDF in-page panning acceleration for both horizontal and vertical movement in horizontal swipe mode.

### Rounded popup and Settings cleanup

- Applied rounded popup styling to the display-rule, actual-file edit, auto-page-turn, delete-confirmation, and settings-reset dialogs.
- Added **Reset settings** for restoring reader/app preferences while keeping bookmarks, reading positions, recent files, folder shortcuts, TXT Display Rules, custom themes, and PIN lock.
- Settings backup/export includes TXT Display Rules through the existing settings import/export path.
- Settings backup guidance now uses neutral “clear/간단한” wording.

### Kept compatibility notes

- Large TXT active rendering remains based on fixed selected-size logical-line partitions.
- TXT pagination keeps the status-bar-off content spacing model so status-bar visibility does not change total page count.
- Backup bookmark editing keeps the `bookmarkEdits.beginner` / `bookmarkEdits.developer` structure.
- PDF, EPUB, and Word/DOCX remain separate document-viewer paths.
- The package identity remains `com.textview.reader`; legacy-package users still need backup/export/import migration.

### Build toolchain metadata

- README, changelog, and patch notes match the project build files: Android Gradle Plugin `9.1.1` and Gradle wrapper `9.3.1`.
- Android app metadata is `versionCode 2140` and `versionName "2.1.4"`.

### Verification note

ZIP integrity and Markdown structure were checked. Full Gradle verification should be run locally in Android Studio or another network-enabled environment if the Gradle wrapper needs to download Gradle.
