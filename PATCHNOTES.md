# TextView Reader 2.1.2 Patch Notes

## 2.1.2 TXT display-rule workflow, actual-file editing, and auto page turn

This package is the GitHub-submittable 2.1.2 source snapshot. The complete previous-version history remains in `CHANGELOG.md`.

Highlights:

- Added viewing-only TXT display rules for masking/replacement without modifying source files.
- Added quick TXT display-rule creation from the TXT More popup, including long-press word prefill.
- Added regex mode, case-sensitive matching, rule ordering, file-origin labels, and enable/disable/delete controls for display rules.
- Added **Edit Actual TXT File** for deliberately writing enabled rule results into either the original TXT file or an overwritten `*_edited.txt` copy.
- Added explicit actual-file edit warnings: rule order matters, copy mode overwrites the same edited copy, original mode overwrites the source file, there is no internal undo, and large files may take extra time/memory.
- Added low-power automatic page turning as a TXT toolbar button for e-ink/low-end-device friendly reading; the interval is clearly labeled as seconds per page.
- Applied rounded popup styling to the newly added display-rule, actual-file edit, auto-page-turn, and settings-reset dialogs.
- Added Settings reset while preserving bookmarks/reading data and user-created rule/theme data.

## Functional difference from 2.1.1

### TXT display rules

2.1.2 adds and expands a viewing-only TXT replacement layer. It lets the user mask or replace text on screen without changing the original TXT file.

Functional result:

- simple literal find/replace rules can be managed from Settings;
- rules can be enabled or disabled;
- matching can be case-sensitive or case-insensitive;
- advanced regex mode is available while plain-text replacement remains the safer default;
- rules can apply to all TXT files or only one current TXT file when managed from the TXT reader;
- rules made from a TXT file can show which file they were created from;
- rules can be created directly from the TXT viewer More popup;
- long-pressing a visible word in the TXT viewer can prefill the find-text field for a new rule;
- rule order can be changed with move-up/move-down controls;
- rules are applied from top to bottom, so overlapping rules can produce different output after order changes;
- up/down order changes do not refresh the active TXT view by themselves;
- rules can be quickly enabled, disabled, or deleted from the rule list;
- replacement is applied before pagination, search, large-TXT partition rendering, and exact page indexing;
- related active rule changes are applied when the rule/add window closes and can invalidate stale page anchors;
- unrelated file-only rules do not reload the current TXT viewer;
- multi-line replacement is intentionally excluded in this version to avoid partition-seam ambiguity.

### Actual TXT file editing

2.1.2 also adds a separate, explicit action for converting display-rule results into real file content. This is not the normal masking layer.

Functional result:

- **Edit Actual TXT File** appears below **TXT Display Rules** in Settings when Settings is opened from a TXT viewer;
- the action applies all enabled rules that currently apply to the opened TXT file;
- rules are applied in saved top-to-bottom order;
- **Fix original file** overwrites the original TXT file using a temporary-file replacement path, then reloads and fully repaginates the opened viewer;
- **Copy original and fix copy** writes to `originalname_edited.txt`; if that edited copy already exists, it is overwritten;
- copy mode does not reload the original viewer;
- the confirmation flow uses rounded dialogs, colored warning boxes, and a second **Are you sure?** step;
- the final warning emphasizes **There is no turning back.** because the app does not provide internal undo for overwritten content.

### TXT auto page turn

2.1.2 adds a low-power automatic page-turn mode for TXT reading.

Functional result:

- the viewer advances by one full page after the user-specified number of seconds;
- this behaves like an e-ink/low-end-device friendly alternative to continuous auto-scroll;
- the interval is entered and saved in seconds per page;
- auto page turn stops at the final page;
- auto page turn stops when the TXT viewer leaves the foreground or is destroyed.

### Rounded popup styling

The newly added display-rule, actual-file edit, auto-page-turn, and settings-reset dialogs use the app's rounded bordered popup style instead of default sharp system AlertDialog styling.

### Settings reset

Settings now includes a reset action for reader/app preferences. It keeps user data such as bookmarks, reading positions, recent files, folder shortcuts, TXT display rules, custom reading themes, and PIN lock.

## Current metadata

- Android package/application ID: `com.textview.reader`
- Android namespace: `com.textview.reader`
- Java source package: `com.textview.reader`
- Android `versionCode`: `212`
- Android `versionName`: `2.1.2`
- Backup schema: `textview-full-backup-v9`
- Default backup filename format: `textview_backup_year_month_day_hour_minute_second.json`

## Migration note

Because the package identity already changed in 2.1.0, 2.1.2 follows the same migration rule: if coming from a legacy package build, export a TextView backup from the old app and import it in this app.

## Build note

Use Android Studio with JDK 17, compile SDK 35, and the included Gradle wrapper. In this sandbox, command-line Gradle verification cannot complete because the wrapper needs internet access to download Gradle.
