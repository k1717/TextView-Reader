package com.simpletext.reader.util;

import android.content.Context;
import android.util.Log;

import com.simpletext.reader.model.Bookmark;
import com.simpletext.reader.model.ReaderState;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Manages bookmarks and reading states.
 *
 * Storage format: plain JSON files (no compression, easy to edit manually).
 *
 * bookmarks.json:
 * {
 *   "version": 1,
 *   "bookmarks": [ ... ]
 * }
 *
 * reading_states.json:
 * {
 *   "version": 1,
 *   "states": { "filePath": { ... }, ... }
 * }
 */
public class BookmarkManager {
    private static final String TAG = "BookmarkManager";
    private static final String BOOKMARKS_FILE = "bookmarks.json";
    private static final String STATES_FILE = "reading_states.json";
    private static final int FORMAT_VERSION = 2;

    private static BookmarkManager instance;
    private final Context context;
    private List<Bookmark> bookmarks;
    private Map<String, ReaderState> readingStates;

    private BookmarkManager(Context context) {
        this.context = context.getApplicationContext();
        loadBookmarks();
        loadReadingStates();
    }

    public static synchronized BookmarkManager getInstance(Context context) {
        if (instance == null) {
            instance = new BookmarkManager(context);
        }
        return instance;
    }

    // ========== Bookmark Operations ==========

    public List<Bookmark> getAllBookmarks() {
        return new ArrayList<>(bookmarks);
    }

    public List<Bookmark> getBookmarksForFile(String filePath) {
        List<Bookmark> result = new ArrayList<>();
        for (Bookmark b : bookmarks) {
            if (b.getFilePath().equals(filePath)) {
                result.add(b);
            }
        }
        // Sort by position
        Collections.sort(result, Comparator.comparingInt(Bookmark::getCharPosition));
        return result;
    }

    public void addBookmark(Bookmark bookmark) {
        bookmarks.add(bookmark);
        saveBookmarks();
    }

    public void updateBookmark(Bookmark bookmark) {
        for (int i = 0; i < bookmarks.size(); i++) {
            if (bookmarks.get(i).getId().equals(bookmark.getId())) {
                bookmark.setUpdatedAt(System.currentTimeMillis());
                bookmarks.set(i, bookmark);
                saveBookmarks();
                return;
            }
        }
    }

    public void deleteBookmark(String bookmarkId) {
        Iterator<Bookmark> it = bookmarks.iterator();
        while (it.hasNext()) {
            if (it.next().getId().equals(bookmarkId)) {
                it.remove();
                saveBookmarks();
                return;
            }
        }
    }

    public void deleteBookmarksForFile(String filePath) {
        Iterator<Bookmark> it = bookmarks.iterator();
        while (it.hasNext()) {
            Bookmark bookmark = it.next();
            String path = bookmark.getFilePath();
            if (filePath == null ? path == null : filePath.equals(path)) {
                it.remove();
            }
        }
        saveBookmarks();
    }

    // ========== Reading State Operations ==========

    public ReaderState getReadingState(String filePath) {
        return readingStates.get(filePath);
    }

    public void saveReadingState(ReaderState state) {
        state.setLastReadAt(System.currentTimeMillis());
        readingStates.put(state.getFilePath(), state);
        saveReadingStates();
    }

    public void deleteReadingState(String filePath) {
        readingStates.remove(filePath);
        saveReadingStates();
    }

    /**
     * Clear all recent-file entries / saved reading states without touching bookmarks.
     */
    public void clearReadingStates() {
        readingStates.clear();
        saveReadingStates();
    }

    /**
     * Get recently read files, sorted by last read time (most recent first).
     */
    public List<ReaderState> getRecentFiles(int limit) {
        List<ReaderState> states = new ArrayList<>(readingStates.values());
        Collections.sort(states, (a, b) -> Long.compare(b.getLastReadAt(), a.getLastReadAt()));
        if (states.size() > limit) {
            states = states.subList(0, limit);
        }
        return states;
    }

    // ========== Export / Import ==========

    /**
     * Export all bookmarks and states to a single JSON string.
     * User can save this to a file for backup.
     */
    public String exportAll() {
        try {
            JSONObject root = new JSONObject();
            root.put("version", FORMAT_VERSION);
            root.put("exportedAt", System.currentTimeMillis());

            // Bookmarks
            JSONArray arr = new JSONArray();
            for (Bookmark b : bookmarks) {
                arr.put(b.toJson());
            }
            root.put("bookmarks", arr);

            // Reading states
            JSONObject statesObj = new JSONObject();
            for (Map.Entry<String, ReaderState> entry : readingStates.entrySet()) {
                statesObj.put(entry.getKey(), entry.getValue().toJson());
            }
            root.put("readingStates", statesObj);

            // App settings, including layout, theme selection, behavior, sorting,
            // brightness, TXT/EPUB boundaries, font family, and recent folder prefs.
            // Security PIN data is skipped inside PrefsManager.
            root.put("settings", PrefsManager.getInstance(context).exportSettingsToJson());

            // Custom reading themes are stored outside SharedPreferences, so keep
            // them in the same backup JSON as the active_theme_id setting.
            root.put("customThemes", ThemeManager.getInstance(context).exportCustomThemesToJson());

            return root.toString(2); // pretty-printed for readability
        } catch (JSONException e) {
            Log.e(TAG, "Export failed", e);
            return "{}";
        }
    }

    /**
     * Import bookmarks and states from a JSON string.
     * @param merge if true, merge with existing; if false, replace all
     */
    public void importAll(String jsonString, boolean merge) {
        try {
            JSONObject root = new JSONObject(jsonString);

            // Import bookmarks
            JSONArray arr = root.optJSONArray("bookmarks");
            if (arr != null) {
                if (!merge) {
                    bookmarks.clear();
                }
                for (int i = 0; i < arr.length(); i++) {
                    Bookmark b = Bookmark.fromJson(arr.getJSONObject(i));
                    if (merge) {
                        // Skip duplicates (same file + same position)
                        boolean duplicate = false;
                        for (Bookmark existing : bookmarks) {
                            if (existing.getFilePath().equals(b.getFilePath())
                                    && existing.getCharPosition() == b.getCharPosition()) {
                                duplicate = true;
                                break;
                            }
                        }
                        if (!duplicate) {
                            bookmarks.add(b);
                        }
                    } else {
                        bookmarks.add(b);
                    }
                }
                saveBookmarks();
            }

            // Import reading states
            JSONObject statesObj = root.optJSONObject("readingStates");
            if (statesObj != null) {
                if (!merge) {
                    readingStates.clear();
                }
                Iterator<String> keys = statesObj.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    ReaderState state = ReaderState.fromJson(statesObj.getJSONObject(key));
                    readingStates.put(key, state);
                }
                saveReadingStates();
            }

            // Import settings and custom themes when present. Older backups that
            // only contain bookmarks/reading states remain valid.
            JSONObject settingsObj = root.optJSONObject("settings");
            if (settingsObj != null) {
                PrefsManager.getInstance(context).importSettingsFromJson(settingsObj, merge);
            }

            JSONArray themesArr = root.optJSONArray("customThemes");
            if (themesArr != null) {
                ThemeManager.getInstance(context).importCustomThemesFromJson(themesArr, merge);
            }
        } catch (Exception e) {
            Log.e(TAG, "Import failed", e);
        }
    }

    /**
     * Batch replace path prefix in all bookmarks and reading states.
     * Useful when moving files to a new SD card or device.
     * e.g., replacePathPrefix("/storage/AAAA-BBBB", "/storage/CCCC-DDDD")
     */
    public int replacePathPrefix(String oldPrefix, String newPrefix) {
        int count = 0;

        // Update bookmarks
        for (Bookmark b : bookmarks) {
            if (b.getFilePath().startsWith(oldPrefix)) {
                b.setFilePath(b.getFilePath().replace(oldPrefix, newPrefix));
                b.setUpdatedAt(System.currentTimeMillis());
                count++;
            }
        }
        saveBookmarks();

        // Update reading states
        Map<String, ReaderState> updated = new HashMap<>();
        for (Map.Entry<String, ReaderState> entry : readingStates.entrySet()) {
            String path = entry.getKey();
            ReaderState state = entry.getValue();
            if (path.startsWith(oldPrefix)) {
                String newPath = path.replace(oldPrefix, newPrefix);
                state.setFilePath(newPath);
                updated.put(newPath, state);
                count++;
            } else {
                updated.put(path, state);
            }
        }
        readingStates = updated;
        saveReadingStates();

        return count;
    }

    // ========== Private I/O ==========

    private void loadBookmarks() {
        bookmarks = new ArrayList<>();
        String json = readFile(BOOKMARKS_FILE);
        if (json == null) return;

        try {
            JSONObject root = new JSONObject(json);
            JSONArray arr = root.optJSONArray("bookmarks");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    bookmarks.add(Bookmark.fromJson(arr.getJSONObject(i)));
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to load bookmarks", e);
        }
    }

    private void saveBookmarks() {
        try {
            JSONObject root = new JSONObject();
            root.put("version", FORMAT_VERSION);
            JSONArray arr = new JSONArray();
            for (Bookmark b : bookmarks) {
                arr.put(b.toJson());
            }
            root.put("bookmarks", arr);
            writeFile(BOOKMARKS_FILE, root.toString(2));
        } catch (JSONException e) {
            Log.e(TAG, "Failed to save bookmarks", e);
        }
    }

    private void loadReadingStates() {
        readingStates = new HashMap<>();
        String json = readFile(STATES_FILE);
        if (json == null) return;

        try {
            JSONObject root = new JSONObject(json);
            JSONObject statesObj = root.optJSONObject("states");
            if (statesObj != null) {
                Iterator<String> keys = statesObj.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    readingStates.put(key, ReaderState.fromJson(statesObj.getJSONObject(key)));
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to load reading states", e);
        }
    }

    private void saveReadingStates() {
        try {
            JSONObject root = new JSONObject();
            root.put("version", FORMAT_VERSION);
            JSONObject statesObj = new JSONObject();
            for (Map.Entry<String, ReaderState> entry : readingStates.entrySet()) {
                statesObj.put(entry.getKey(), entry.getValue().toJson());
            }
            root.put("states", statesObj);
            writeFile(STATES_FILE, root.toString(2));
        } catch (JSONException e) {
            Log.e(TAG, "Failed to save reading states", e);
        }
    }

    private String readFile(String fileName) {
        File file = new File(context.getFilesDir(), fileName);
        if (!file.exists()) return null;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "Failed to read " + fileName, e);
            return null;
        }
    }

    private void writeFile(String fileName, String content) {
        File file = new File(context.getFilesDir(), fileName);
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(content);
        } catch (Exception e) {
            Log.e(TAG, "Failed to write " + fileName, e);
        }
    }
}
