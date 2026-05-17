# Privacy

TextView Reader is designed as an offline local reader. Settings shows a static GitHub releases link for manual update checking, but the app does not contact GitHub or any update server itself.

## What the app does not include

- No analytics SDK.
- No advertising SDK.
- No account system.
- No cloud sync backend.
- No remote telemetry collection.

## Data stored locally

The app may store local-only data needed for reading behavior:

- recent files;
- recent folders;
- user-added folder shortcuts;
- reading position;
- bookmarks and bookmark labels;
- reader settings;
- theme settings;
- optional imported fonts;
- optional PIN-lock state;
- PDF reading-mode preference;
- saved TXT display rules, including rule text, scope, enabled state, case-sensitivity setting, regex setting, ordering, and source-file labels/paths used for current-file-only rules;
- disposable TXT page/index cache metadata for large-file handling.

This data stays on the device unless the user manually exports, backs up, shares, deletes, or transfers it.

## Folder shortcuts

Folder shortcuts store local folder paths selected by the user. They are used only to show faster navigation entries in the app drawer. Removing a folder shortcut removes the shortcut entry; it does not delete the folder or files.

## TXT display rules and actual-file editing

TXT display rules are stored locally and may be included in settings backup/import. Normal display rules change only the viewer output and do not edit the source TXT file.

The separate **Edit Actual TXT File** action is user-triggered and can write changed text into local storage. Original mode overwrites the current TXT file. Copy mode writes to `*_edited.txt` and overwrites that edited copy if it already exists. These file writes happen only after explicit confirmation in the app. The app writes through a temporary file in the same folder before replacing the target, but the user should still treat original-file editing as destructive because there is no internal undo.

## Bookmark export/import

Backup/export uses JSON. The exported JSON can include file paths, file names, reading positions, bookmark labels, excerpts, app settings, layout settings, and custom reading themes. Treat exported backup files as user data. Lock PIN data is intentionally excluded from the plain JSON backup.

## Manual update link

Settings shows the static line `Check updates at https://github.com/k1717/TextView-Reader/releases`. Tapping that line copies the release URL to the clipboard. The app does not call the GitHub API, does not perform in-app update checks, and does not run background update checks.

## File permissions

The app requests storage access so it can open local documents selected by the user. On Android versions that require scoped-storage handling, the app may also request broader storage access for file-browser behavior.

## PIN lock limitation

The optional PIN lock is an app-level convenience lock. It is not a substitute for Android device encryption, lock-screen security, or secure storage of sensitive documents.

## Generated cache data

Disposable TXT page/index cache bookkeeping is used only for generated cache data under app cache storage. Cache cleanup must not remove bookmarks, reading history, saved reading position, folder shortcuts, or user documents.
