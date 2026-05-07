package com.simpletext.reader.util;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class PrefsManager {
    private static final String PREFS_NAME = "simple_text_reader_prefs";
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

    public float getFontSize() { return prefs.getFloat("font_size", DEFAULT_FONT_SIZE); }
    public void setFontSize(float s) { prefs.edit().putFloat("font_size", Math.max(8f, Math.min(48f, s))).apply(); }
    public float getLineSpacing() { return prefs.getFloat("line_spacing", DEFAULT_LINE_SPACING); }
    public void setLineSpacing(float s) { prefs.edit().putFloat("line_spacing", s).apply(); }
    public String getFontFamily() { return prefs.getString("font_family", "default"); }
    public void setFontFamily(String f) { prefs.edit().putString("font_family", f).apply(); }

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
    public int getLanguageMode() { return normalizeLanguageMode(prefs.getInt("language_mode", LANGUAGE_ENGLISH)); }

    public void setLanguageMode(int mode) {
        int normalized = normalizeLanguageMode(mode);

        // Use commit(), not apply(), because changing AppCompat locale can recreate the
        // activity immediately. With apply(), a recreated SettingsActivity can sometimes
        // read the old language value and appear to flip to the opposite selection.
        prefs.edit().putInt("language_mode", normalized).commit();
        applyLanguage(normalized);
    }

    public void applyLanguage(int mode) {
        String tag = languageTagForMode(normalizeLanguageMode(mode));
        LocaleListCompat target = LocaleListCompat.forLanguageTags(tag);
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

    private String languageTagForMode(int mode) {
        return mode == LANGUAGE_KOREAN ? "ko" : "en";
    }

    public boolean getKeepScreenOn() { return prefs.getBoolean("keep_screen_on", true); }
    public void setKeepScreenOn(boolean v) { prefs.edit().putBoolean("keep_screen_on", v).apply(); }
    public boolean getShowStatusBar() { return prefs.getBoolean("show_status_bar", false); }
    public void setShowStatusBar(boolean v) { prefs.edit().putBoolean("show_status_bar", v).apply(); }
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

        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        ordered.add(clean);
        for (String old : getRecentFolders(32)) {
            if (old != null && !old.trim().isEmpty()) ordered.add(old.trim());
            if (ordered.size() >= 20) break;
        }

        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String item : ordered) {
            if (count++ > 0) sb.append('\n');
            sb.append(item);
            if (count >= 20) break;
        }
        prefs.edit().putString("recent_folders", sb.toString()).apply();
    }
    public int getMarginHorizontal() { return prefs.getInt("page_margin_h", 24); }
    public void setMarginHorizontal(int dp) { prefs.edit().putInt("page_margin_h", dp).apply(); }
    public int getMarginVertical() { return prefs.getInt("page_margin_v", 16); }
    public void setMarginVertical(int dp) { prefs.edit().putInt("page_margin_v", dp).apply(); }

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
    public int getTapZoneMode() { return prefs.getInt("tap_zone_mode", TAP_ZONE_VERTICAL); }
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
