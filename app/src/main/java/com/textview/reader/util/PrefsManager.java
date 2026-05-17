package com.textview.reader.util;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class PrefsManager {
    private static final String PREFS_NAME = "textview_reader_prefs";
    public static final float DEFAULT_FONT_SIZE = 18f;
    public static final float DEFAULT_LINE_SPACING = 1.5f;
    public static final int DARK_MODE_FOLLOW_SYSTEM = 0;
    public static final int DARK_MODE_OFF = 1;
    public static final int DARK_MODE_ON = 2;
    public static final int SORT_RECENT_READ = -1;
    public static final int SORT_NAME_ASC = 0;
    public static final int SORT_NAME_DESC = 1;
    public static final int SORT_DATE_NEW = 2;
    public static final int SORT_DATE_OLD = 3;
    public static final int SORT_SIZE_LARGE = 4;
    public static final int SORT_SIZE_SMALL = 5;
    public static final int SORT_TYPE = 6;
    public static final int LANGUAGE_ENGLISH = 0;
    public static final int LANGUAGE_KOREAN = 1;
    public static final int TAP_ZONE_VERTICAL = 0;
    public static final int TAP_ZONE_HORIZONTAL = 1;
    public static final int PAGE_STATUS_ALIGN_LEFT = 0;
    public static final int PAGE_STATUS_ALIGN_CENTER = 1;
    public static final int PAGE_STATUS_ALIGN_RIGHT = 2;
    public static final int PAGE_STATUS_ALIGN_HIDDEN = 3;
    public static final int EPUB_PAGE_DIRECTION_LTR = 0;
    public static final int EPUB_PAGE_DIRECTION_RTL = 1;
    public static final int EPUB_PAGE_EFFECT_SLIDE = 0;
    public static final int EPUB_PAGE_EFFECT_NONE = 1;

    private final SharedPreferences prefs;
    private static PrefsManager instance;

    private PrefsManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    public static synchronized PrefsManager getInstance(Context context) {
        if (instance == null) instance = new PrefsManager(context);
        return instance;
    }
    public SharedPreferences getPrefs() { return prefs; }

    // ========== Backup / restore settings ==========
    // Security PINs are intentionally not exported/imported. Restoring lock_enabled
    // without a matching PIN can lock the user into a broken state, and exporting the
    // PIN would place sensitive data in a plain JSON backup file.
    private boolean isBackupExcludedKey(String key) {
        return "lock_pin".equals(key) || "lock_enabled".equals(key);
    }

    public JSONObject exportSettingsToJson() throws JSONException {
        JSONObject root = new JSONObject();
        root.put("version", 1);

        JSONObject values = new JSONObject();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (key == null || value == null || isBackupExcludedKey(key)) continue;

            JSONObject item = new JSONObject();
            if (value instanceof Boolean) {
                item.put("type", "boolean");
                item.put("value", value);
            } else if (value instanceof Float) {
                item.put("type", "float");
                item.put("value", ((Float) value).doubleValue());
            } else if (value instanceof Integer) {
                item.put("type", "int");
                item.put("value", value);
            } else if (value instanceof Long) {
                item.put("type", "long");
                item.put("value", value);
            } else if (value instanceof String) {
                item.put("type", "string");
                item.put("value", value);
            } else if (value instanceof Set) {
                item.put("type", "stringSet");
                JSONArray arr = new JSONArray();
                for (Object setItem : (Set<?>) value) {
                    if (setItem != null) arr.put(String.valueOf(setItem));
                }
                item.put("value", arr);
            } else {
                item.put("type", "string");
                item.put("value", String.valueOf(value));
            }
            values.put(key, item);
        }

        root.put("values", values);
        return root;
    }

    public void importSettingsFromJson(JSONObject root, boolean merge) throws JSONException {
        if (root == null) return;
        JSONObject values = root.optJSONObject("values");
        if (values == null) return;

        SharedPreferences.Editor editor = prefs.edit();
        if (!merge) {
            for (String key : prefs.getAll().keySet()) {
                if (!isBackupExcludedKey(key)) editor.remove(key);
            }
        }

        Iterator<String> keys = values.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (key == null || isBackupExcludedKey(key)) continue;

            JSONObject item = values.optJSONObject(key);
            if (item == null) continue;
            String type = item.optString("type", "string");

            if ("boolean".equals(type)) {
                editor.putBoolean(key, item.optBoolean("value", false));
            } else if ("float".equals(type)) {
                editor.putFloat(key, (float) item.optDouble("value", 0.0));
            } else if ("int".equals(type)) {
                editor.putInt(key, item.optInt("value", 0));
            } else if ("long".equals(type)) {
                editor.putLong(key, item.optLong("value", 0L));
            } else if ("stringSet".equals(type)) {
                JSONArray arr = item.optJSONArray("value");
                LinkedHashSet<String> set = new LinkedHashSet<>();
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        String setItem = arr.optString(i, null);
                        if (setItem != null) set.add(setItem);
                    }
                }
                editor.putStringSet(key, set);
            } else {
                editor.putString(key, item.optString("value", ""));
            }
        }

        editor.commit();
    }

    public float getFontSize() { return prefs.getFloat("font_size", DEFAULT_FONT_SIZE); }
    public void setFontSize(float s) { prefs.edit().putFloat("font_size", Math.max(8f, Math.min(48f, s))).apply(); }
    public float getLineSpacing() { return prefs.getFloat("line_spacing", DEFAULT_LINE_SPACING); }
    public void setLineSpacing(float s) { prefs.edit().putFloat("line_spacing", s).apply(); }
    public String getFontFamily() { return prefs.getString("font_family", "default"); }
    public void setFontFamily(String f) { prefs.edit().putString("font_family", f).apply(); }

    // EPUB WebView reader boundary. Stored in raw px units.
    private int clampEpubPaddingDp(int px) {
        int clamped = Math.max(0, Math.min(240, px));
        return Math.round(clamped / 5f) * 5;
    }

    public int getEpubLeftPaddingDp() {
        return clampEpubPaddingDp(prefs.getInt("epub_left_padding_dp",
                prefs.getInt("epub_side_padding_dp",
                        prefs.getInt("document_side_padding_dp", 30))));
    }
    public void setEpubLeftPaddingDp(int dp) {
        prefs.edit().putInt("epub_left_padding_dp", clampEpubPaddingDp(dp)).apply();
    }
    public int getEpubRightPaddingDp() {
        return clampEpubPaddingDp(prefs.getInt("epub_right_padding_dp",
                prefs.getInt("epub_side_padding_dp",
                        prefs.getInt("document_side_padding_dp", 30))));
    }
    public void setEpubRightPaddingDp(int dp) {
        prefs.edit().putInt("epub_right_padding_dp", clampEpubPaddingDp(dp)).apply();
    }

    // Kept for migration/compatibility with older 2.0.7 builds that stored one side value.
    public int getEpubSidePaddingDp() {
        return Math.round((getEpubLeftPaddingDp() + getEpubRightPaddingDp()) / 2f);
    }
    public void setEpubSidePaddingDp(int dp) {
        int value = clampEpubPaddingDp(dp);
        prefs.edit()
                .putInt("epub_left_padding_dp", value)
                .putInt("epub_right_padding_dp", value)
                .putInt("epub_side_padding_dp", value)
                .apply();
    }
    public int getEpubTopPaddingDp() {
        return clampEpubPaddingDp(prefs.getInt("epub_top_padding_dp", 0));
    }
    public void setEpubTopPaddingDp(int dp) {
        prefs.edit().putInt("epub_top_padding_dp", clampEpubPaddingDp(dp)).apply();
    }
    public int getEpubBottomPaddingDp() {
        return clampEpubPaddingDp(prefs.getInt("epub_bottom_padding_dp", 0));
    }
    public void setEpubBottomPaddingDp(int dp) {
        prefs.edit().putInt("epub_bottom_padding_dp", clampEpubPaddingDp(dp)).apply();
    }

    public int getEpubPageDirection() {
        int value = prefs.getInt("epub_page_direction", EPUB_PAGE_DIRECTION_LTR);
        return value == EPUB_PAGE_DIRECTION_RTL ? EPUB_PAGE_DIRECTION_RTL : EPUB_PAGE_DIRECTION_LTR;
    }
    public void setEpubPageDirection(int direction) {
        prefs.edit().putInt("epub_page_direction",
                direction == EPUB_PAGE_DIRECTION_RTL ? EPUB_PAGE_DIRECTION_RTL : EPUB_PAGE_DIRECTION_LTR).apply();
    }
    public int getEpubPageEffect() {
        int value = prefs.getInt("epub_page_effect", EPUB_PAGE_EFFECT_SLIDE);
        return value == EPUB_PAGE_EFFECT_NONE ? EPUB_PAGE_EFFECT_NONE : EPUB_PAGE_EFFECT_SLIDE;
    }
    public void setEpubPageEffect(int effect) {
        prefs.edit().putInt("epub_page_effect",
                effect == EPUB_PAGE_EFFECT_NONE ? EPUB_PAGE_EFFECT_NONE : EPUB_PAGE_EFFECT_SLIDE).apply();
    }

    public int getDarkMode() { return prefs.getInt("dark_mode", DARK_MODE_FOLLOW_SYSTEM); }
    public void setDarkMode(int m) { prefs.edit().putInt("dark_mode", m).apply(); applyDarkMode(m); }
    public void applyDarkMode(int mode) {
        switch (mode) {
            case DARK_MODE_OFF: AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO); break;
            case DARK_MODE_ON: AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES); break;
            default: AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM); break;
        }
    }

    public boolean shouldUseDarkColors(Context context) {
        int mode = getDarkMode();
        if (mode == DARK_MODE_ON) return true;
        if (mode == DARK_MODE_OFF) return false;
        int mask = context.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return mask == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }


    // Language
    public int getLanguageMode() {
        if (!prefs.contains("language_mode")) {
            return detectSystemLanguageMode();
        }
        return normalizeLanguageMode(prefs.getInt("language_mode", detectSystemLanguageMode()));
    }

    public void setLanguageMode(int mode) {
        int normalized = normalizeLanguageMode(mode);

        // Use commit(), not apply(), because changing AppCompat locale can recreate the
        // activity immediately. With apply(), a recreated SettingsActivity can sometimes
        // read the old language value and appear to flip to the opposite selection.
        prefs.edit().putInt("language_mode", normalized).commit();
        applyLanguage(normalized);
    }

    public void applyLanguage(int mode) {
        LocaleListCompat target;
        if (!prefs.contains("language_mode")) {
            // First install / untouched language setting: let Android/AppCompat follow
            // the system locale instead of forcing English.
            target = LocaleListCompat.getEmptyLocaleList();
        } else {
            String tag = languageTagForMode(normalizeLanguageMode(mode));
            target = LocaleListCompat.forLanguageTags(tag);
        }

        LocaleListCompat current = AppCompatDelegate.getApplicationLocales();

        // Avoid redundant locale sets. Re-setting the same locale can cause unnecessary
        // activity recreation and intermittent stale UI state around the language radio group.
        if (!target.toLanguageTags().equals(current.toLanguageTags())) {
            AppCompatDelegate.setApplicationLocales(target);
        }
    }

    private int normalizeLanguageMode(int mode) {
        return mode == LANGUAGE_KOREAN ? LANGUAGE_KOREAN : LANGUAGE_ENGLISH;
    }

    private int detectSystemLanguageMode() {
        Locale locale = Locale.getDefault();
        String language = locale != null ? locale.getLanguage() : "";
        return "ko".equalsIgnoreCase(language) ? LANGUAGE_KOREAN : LANGUAGE_ENGLISH;
    }

    private String languageTagForMode(int mode) {
        return mode == LANGUAGE_KOREAN ? "ko" : "en";
    }

    public boolean getKeepScreenOn() { return prefs.getBoolean("keep_screen_on", true); }
    public void setKeepScreenOn(boolean v) { prefs.edit().putBoolean("keep_screen_on", v).apply(); }
    public boolean getShowStatusBar() { return prefs.getBoolean("show_status_bar", false); }
    public void setShowStatusBar(boolean v) { prefs.edit().putBoolean("show_status_bar", v).apply(); }
    public int getPageStatusAlignment() {
        return normalizePageStatusAlignment(prefs.getInt("page_status_alignment", PAGE_STATUS_ALIGN_CENTER));
    }
    public void setPageStatusAlignment(int alignment) {
        prefs.edit().putInt("page_status_alignment", normalizePageStatusAlignment(alignment)).apply();
    }
    private int normalizePageStatusAlignment(int alignment) {
        if (alignment == PAGE_STATUS_ALIGN_LEFT
                || alignment == PAGE_STATUS_ALIGN_RIGHT
                || alignment == PAGE_STATUS_ALIGN_HIDDEN) return alignment;
        return PAGE_STATUS_ALIGN_CENTER;
    }
    public boolean getAutoSavePosition() { return prefs.getBoolean("auto_save_position", true); }
    public void setAutoSavePosition(boolean v) { prefs.edit().putBoolean("auto_save_position", v).apply(); }
    public String getLastDirectory() { return prefs.getString("last_directory", null); }

    public String getLastReaderSearchQuery() { return prefs.getString("last_reader_search_query", ""); }
    public void setLastReaderSearchQuery(String query) {
        prefs.edit().putString("last_reader_search_query", query == null ? "" : query.trim()).apply();
    }
    public void setLastDirectory(String p) { prefs.edit().putString("last_directory", p).apply(); }

    public List<String> getRecentFolders(int limit) {
        String raw = prefs.getString("recent_folders", "");
        ArrayList<String> result = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return result;
        String[] parts = raw.split("\n");
        for (String part : parts) {
            if (part == null) continue;
            String path = part.trim();
            if (path.isEmpty()) continue;
            result.add(path);
            if (limit > 0 && result.size() >= limit) break;
        }
        return result;
    }

    public void addRecentFolder(String path) {
        if (path == null) return;
        String clean = path.trim();
        if (clean.isEmpty()) return;

        // If the user cleared this folder from the recent-folder list before,
        // opening it again should make it eligible to appear again.
        removeHiddenRecentFolder(clean);

        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        ordered.add(clean);
        for (String old : getRecentFolders(32)) {
            if (old != null && !old.trim().isEmpty()) ordered.add(old.trim());
            if (ordered.size() >= 20) break;
        }

        saveRecentFolders(ordered, 20);
    }

    public void removeRecentFolder(String path) {
        if (path == null) return;
        String clean = path.trim();
        if (clean.isEmpty()) return;

        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        for (String old : getRecentFolders(64)) {
            if (old == null) continue;
            String item = old.trim();
            if (item.isEmpty() || item.equals(clean)) continue;
            ordered.add(item);
        }

        SharedPreferences.Editor editor = prefs.edit();
        putJoinedPaths(editor, "recent_folders", ordered, 20);
        String last = getLastDirectory();
        if (last != null && last.trim().equals(clean)) editor.remove("last_directory");
        editor.apply();
        hideRecentFolder(clean);
    }

    public void clearRecentFolders(Collection<String> pathsToHide) {
        LinkedHashSet<String> hidden = getHiddenRecentFolderSet();
        if (pathsToHide != null) {
            for (String path : pathsToHide) {
                if (path == null) continue;
                String clean = path.trim();
                if (!clean.isEmpty()) hidden.add(clean);
            }
        }

        SharedPreferences.Editor editor = prefs.edit()
                .remove("recent_folders")
                .remove("last_directory");
        putJoinedPaths(editor, "hidden_recent_folders", hidden, 256);
        editor.apply();
    }

    public boolean isRecentFolderHidden(String path) {
        if (path == null) return false;
        String clean = path.trim();
        if (clean.isEmpty()) return false;
        return getHiddenRecentFolderSet().contains(clean);
    }

    private void hideRecentFolder(String path) {
        if (path == null) return;
        String clean = path.trim();
        if (clean.isEmpty()) return;
        LinkedHashSet<String> hidden = getHiddenRecentFolderSet();
        hidden.add(clean);
        SharedPreferences.Editor editor = prefs.edit();
        putJoinedPaths(editor, "hidden_recent_folders", hidden, 256);
        editor.apply();
    }

    private void removeHiddenRecentFolder(String path) {
        if (path == null) return;
        String clean = path.trim();
        if (clean.isEmpty()) return;
        LinkedHashSet<String> hidden = getHiddenRecentFolderSet();
        if (!hidden.remove(clean)) return;
        SharedPreferences.Editor editor = prefs.edit();
        putJoinedPaths(editor, "hidden_recent_folders", hidden, 256);
        editor.apply();
    }

    private LinkedHashSet<String> getHiddenRecentFolderSet() {
        return readPathSet("hidden_recent_folders", 256);
    }

    private void saveRecentFolders(LinkedHashSet<String> ordered, int limit) {
        SharedPreferences.Editor editor = prefs.edit();
        putJoinedPaths(editor, "recent_folders", ordered, limit);
        editor.apply();
    }

    private LinkedHashSet<String> readPathSet(String key, int limit) {
        String raw = prefs.getString(key, "");
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (raw == null || raw.isEmpty()) return result;
        String[] parts = raw.split("\n");
        for (String part : parts) {
            if (part == null) continue;
            String path = part.trim();
            if (path.isEmpty()) continue;
            result.add(path);
            if (limit > 0 && result.size() >= limit) break;
        }
        return result;
    }

    private void putJoinedPaths(SharedPreferences.Editor editor, String key, Collection<String> paths, int limit) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        if (paths != null) {
            for (String item : paths) {
                if (item == null || item.trim().isEmpty()) continue;
                if (count++ > 0) sb.append('\n');
                sb.append(item.trim());
                if (limit > 0 && count >= limit) break;
            }
        }
        if (sb.length() == 0) editor.remove(key);
        else editor.putString(key, sb.toString());
    }
    public List<String> getFolderShortcuts(int limit) {
        String raw = prefs.getString("folder_shortcuts", "");
        ArrayList<String> result = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return result;
        String[] parts = raw.split("\n");
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String part : parts) {
            if (part == null) continue;
            String path = part.trim();
            if (path.isEmpty() || !seen.add(path)) continue;
            result.add(path);
            if (limit > 0 && result.size() >= limit) break;
        }
        return result;
    }

    public boolean isFolderShortcut(String path) {
        if (path == null) return false;
        String clean = path.trim();
        if (clean.isEmpty()) return false;
        for (String shortcut : getFolderShortcuts(0)) {
            if (clean.equals(shortcut)) return true;
        }
        return false;
    }

    public void addFolderShortcut(String path) {
        if (path == null) return;
        String clean = path.trim();
        if (clean.isEmpty()) return;

        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        ordered.add(clean);
        for (String old : getFolderShortcuts(64)) {
            if (old != null && !old.trim().isEmpty()) ordered.add(old.trim());
            if (ordered.size() >= 30) break;
        }
        saveFolderShortcuts(ordered, 30);
    }

    public void removeFolderShortcut(String path) {
        if (path == null) return;
        String clean = path.trim();
        if (clean.isEmpty()) return;

        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        for (String old : getFolderShortcuts(64)) {
            if (old == null) continue;
            String item = old.trim();
            if (item.isEmpty() || item.equals(clean)) continue;
            ordered.add(item);
        }
        saveFolderShortcuts(ordered, 30);
    }

    private void saveFolderShortcuts(LinkedHashSet<String> ordered, int limit) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String item : ordered) {
            if (item == null || item.trim().isEmpty()) continue;
            if (count++ > 0) sb.append('\n');
            sb.append(item.trim());
            if (limit > 0 && count >= limit) break;
        }
        prefs.edit().putString("folder_shortcuts", sb.toString()).apply();
    }

    public int getMarginHorizontal() { return prefs.getInt("page_margin_h", 24); }
    public void setMarginHorizontal(int dp) { prefs.edit().putInt("page_margin_h", dp).apply(); }
    public int getMarginVertical() { return prefs.getInt("page_margin_v", 16); }
    public void setMarginVertical(int dp) { prefs.edit().putInt("page_margin_v", dp).apply(); }

    // TXT reader layout tuning. Offsets/insets are raw pixels.
    // Top: positive moves the top boundary down. Bottom: negative moves the bottom boundary up.
    // Left/right: positive shrinks the readable width from that side.
    public int getReaderTextTopOffsetPx() { return prefs.getInt("reader_text_top_offset_px", 0); }
    public void setReaderTextTopOffsetPx(int px) {
        prefs.edit().putInt("reader_text_top_offset_px", Math.max(0, Math.min(240, px))).apply();
    }
    public int getReaderTextBottomOffsetPx() { return prefs.getInt("reader_text_bottom_offset_px", 0); }
    public void setReaderTextBottomOffsetPx(int px) {
        prefs.edit().putInt("reader_text_bottom_offset_px", Math.max(0, Math.min(240, px))).apply();
    }
    public int getReaderTextLeftInsetPx() { return prefs.getInt("reader_text_left_inset_px", 0); }
    public void setReaderTextLeftInsetPx(int px) {
        prefs.edit().putInt("reader_text_left_inset_px", Math.max(0, Math.min(240, px))).apply();
    }
    public int getReaderTextRightInsetPx() { return prefs.getInt("reader_text_right_inset_px", 0); }
    public void setReaderTextRightInsetPx(int px) {
        prefs.edit().putInt("reader_text_right_inset_px", Math.max(0, Math.min(240, px))).apply();
    }

    // Lock
    public boolean isLockEnabled() { return prefs.getBoolean("lock_enabled", false); }
    public void setLockEnabled(boolean v) { prefs.edit().putBoolean("lock_enabled", v).apply(); }
    public String getLockPin() { return prefs.getString("lock_pin", ""); }
    public void setLockPin(String pin) { prefs.edit().putString("lock_pin", pin).apply(); }

    // Sort
    public int getSortMode() { return prefs.getInt("sort_mode", SORT_NAME_ASC); }
    public void setSortMode(int m) { prefs.edit().putInt("sort_mode", m).apply(); }
    public int getRecentSortMode() { return prefs.getInt("recent_sort_mode", SORT_RECENT_READ); }
    public void setRecentSortMode(int m) { prefs.edit().putInt("recent_sort_mode", m).apply(); }
    public boolean getShowHiddenFiles() { return prefs.getBoolean("show_hidden", false); }
    public void setShowHiddenFiles(boolean v) { prefs.edit().putBoolean("show_hidden", v).apply(); }

    // Brightness
    public boolean getBrightnessOverride() { return prefs.getBoolean("brightness_override", false); }
    public void setBrightnessOverride(boolean v) { prefs.edit().putBoolean("brightness_override", v).apply(); }
    public float getBrightnessValue() { return prefs.getFloat("brightness_value", 0.5f); }
    public void setBrightnessValue(float v) { prefs.edit().putFloat("brightness_value", v).apply(); }

    // Notification
    public boolean getShowNotification() { return prefs.getBoolean("show_notification", false); }
    public void setShowNotification(boolean v) { prefs.edit().putBoolean("show_notification", v).apply(); }

    // Volume key paging
    public boolean getVolumeKeyScroll() { return prefs.getBoolean("volume_key_scroll", false); }
    public void setVolumeKeyScroll(boolean v) { prefs.edit().putBoolean("volume_key_scroll", v).apply(); }

    // TekView-style tap paging
    public boolean getTapPagingEnabled() { return prefs.getBoolean("tap_paging_enabled", true); }
    public void setTapPagingEnabled(boolean v) { prefs.edit().putBoolean("tap_paging_enabled", v).apply(); }
    public int getTapZoneMode() { return prefs.getInt("tap_zone_mode", TAP_ZONE_HORIZONTAL); }
    public void setTapZoneMode(int mode) {
        int clamped = (mode == TAP_ZONE_HORIZONTAL) ? TAP_ZONE_HORIZONTAL : TAP_ZONE_VERTICAL;
        prefs.edit().putInt("tap_zone_mode", clamped).apply();
    }
    public int getTapLeadingZonePercent() { return prefs.getInt("tap_leading_zone_percent", 35); }
    public int getTapTrailingZonePercent() { return prefs.getInt("tap_trailing_zone_percent", 35); }
    public void setTapZonePercents(int leadingPercent, int trailingPercent) {
        int leading = Math.max(5, Math.min(80, leadingPercent));
        int trailing = Math.max(5, Math.min(80, trailingPercent));

        // Keep at least 10% for the middle/menu zone.
        if (leading + trailing > 90) {
            int overflow = leading + trailing - 90;
            if (leading >= trailing) {
                leading = Math.max(5, leading - overflow);
            } else {
                trailing = Math.max(5, trailing - overflow);
            }
        }

        prefs.edit()
                .putInt("tap_leading_zone_percent", leading)
                .putInt("tap_trailing_zone_percent", trailing)
                .apply();
    }
    public int getPagingOverlapLines() { return prefs.getInt("paging_overlap_lines", 0); }
    public void setPagingOverlapLines(int lines) { prefs.edit().putInt("paging_overlap_lines", Math.max(0, Math.min(4, lines))).apply(); }
}
