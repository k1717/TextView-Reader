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

Folder shortcuts store local folder paths selected by the user. They are used only to show faster navigation entries in the app drawer.

Removing a folder shortcut removes only the shortcut entry. It does not delete the folder or files.

## Document search

TXT, EPUB, and Word search behavior is local to the app session and document being viewed. Search text is used to highlight and navigate matches locally. It is not sent to a server.

## Font handling

Imported fonts and scanned system-font references are used locally for reader display.

For EPUB/Word WebView rendering, imported/custom fonts may be exposed to the internal local WebView route used by the app. This is used only for rendering document pages inside the app.

If an EPUB or Word file declares its own font, the app may show a **Default font** option so the file's own font information can take priority.

## Bookmark export/import

Bookmark backup/export uses JSON. The exported JSON can include file paths, file names, reading positions, bookmark labels, and excerpts. Treat exported bookmark files as user data.

## File permissions

The app requests storage access so it can open local documents selected by the user. On Android versions that require scoped-storage handling, the app may also request broader storage access for file-browser behavior.

## PIN lock limitation

The optional PIN lock is an app-level convenience lock. It is not a substitute for Android device encryption, lock-screen security, or secure storage of sensitive documents.

## Generated cache data

Disposable TXT page/index cache bookkeeping is used only for generated cache data under app cache storage.

Cache cleanup must not remove:

- bookmarks;
- reading history;
- saved reading position;
- folder shortcuts;
- user documents.
