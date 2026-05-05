# TextView Reader

A lightweight TXT reader for Android, focused on clean reading, fast paging, bookmarks, themes, and broad CJK text encoding support.

TextView Reader is inspired by the usability of older TXT reader apps, but rebuilt for modern Android with scoped storage support, 64-bit compatibility, and a simpler interface.

---

## Features

### Reading

- TXT-focused reader interface
- Tap-zone paging:
  - top 35%: previous page
  - middle 30%: show/hide bottom menu
  - bottom 35%: next page
- Volume-key page up/down
- Auto-resume reading position per file
- Current page indicator
- Page move dialog with slider and exact page input
- Find-in-text with next/previous search and wrap-around

### Encoding Support

TextView Reader includes fallback decoding for common Korean, Japanese, Chinese, and Unicode TXT files.

Supported encoding candidates include:

- UTF-8
- UTF-16LE / UTF-16BE
- MS949 / Windows-949 / CP949
- EUC-KR
- Shift_JIS / Windows-31J
- EUC-JP
- ISO-2022-JP
- GB18030 / GBK
- Big5

Invalid or unmappable bytes are replaced safely instead of crashing the reader.

### Bookmarks

- Save current reading position
- Load existing bookmarks from the same bookmark button
- File-based bookmark grouping
- Expand/shrink bookmark folders
- Delete bookmarks
- Bookmark data stored in readable JSON format

### File Browser

- Built-in file browser
- Sort options
- Show/hide hidden files
- Open TXT files through Android Storage Access Framework
- File info dialog with path, size, and detected encoding

### Themes and Appearance

- Light, dark, black, sepia, and other reading themes
- Theme-aware dialogs and menus
- Custom font support
- Brightness override
- Rounded middle-tap bottom menu
- Korean and English language selection

### Android Compatibility

- Modern Android-compatible storage behavior
- 64-bit Android support
- Minimum SDK: 24
- Target SDK: 34

---

## Installation

Download the APK from the GitHub Releases page and install it on your Android device.

Because this app is not currently distributed through the Play Store, Android may ask you to allow installation from unknown sources.

---

## Build from Source

Open the project in Android Studio.

Then run:

```text
Build → Clean Project
Build → Generate App Bundles or APKs → Generate APKs