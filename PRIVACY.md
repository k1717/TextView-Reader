# Privacy

TextView Reader is designed as an offline local reader.

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
- disposable TXT page/index cache metadata for large-file handling.

This data stays on the device unless the user manually exports, backs up, shares, deletes, or transfers it.

## Folder shortcuts

Folder shortcuts store local folder paths selected by the user. They are used only to show faster navigation entries in the app drawer. Removing a folder shortcut removes the shortcut entry; it does not delete the folder or files.

## Bookmark export/import

Backup/export uses JSON. The exported JSON can include file paths, file names, reading positions, bookmark labels, excerpts, app settings, layout settings, and custom reading themes. Treat exported backup files as user data. Lock PIN data is intentionally excluded from the plain JSON backup.

## File permissions

The app requests storage access so it can open local documents selected by the user. On Android versions that require scoped-storage handling, the app may also request broader storage access for file-browser behavior.

## PIN lock limitation

The optional PIN lock is an app-level convenience lock. It is not a substitute for Android device encryption, lock-screen security, or secure storage of sensitive documents.

## Generated cache data

Disposable TXT page/index cache bookkeeping is used only for generated cache data under app cache storage. Cache cleanup must not remove bookmarks, reading history, saved reading position, folder shortcuts, or user documents.
