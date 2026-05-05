package com.simpletext.reader.util;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

public class PrefsManager {
    private static final String PREFS_NAME = "simple_text_reader_prefs";
    public static final float DEFAULT_FONT_SIZE = 18f;
    public static final float DEFAULT_LINE_SPACING = 1.5f;
    public static final int DARK_MODE_FOLLOW_SYSTEM = 0;
    public static final int DARK_MODE_OFF = 1;
    public static final int DARK_MODE_ON = 2;
    public static final int SORT_NAME_ASC = 0;
    public static final int SORT_NAME_DESC = 1;
    public static final int SORT_DATE_NEW = 2;
    public static final int SORT_DATE_OLD = 3;
    public static final int SORT_SIZE_LARGE = 4;
    public static final int SORT_SIZE_SMALL = 5;
    public static final int SORT_TYPE = 6;
    public static final int LANGUAGE_ENGLISH = 0;
    public static final int LANGUAGE_KOREAN = 1;

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


    // Language
    public int getLanguageMode() { return prefs.getInt("language_mode", LANGUAGE_ENGLISH); }
    public void setLanguageMode(int mode) {
        prefs.edit().putInt("language_mode", mode).apply();
        applyLanguage(mode);
    }
    public void applyLanguage(int mode) {
        String tag = mode == LANGUAGE_KOREAN ? "ko" : "en";
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag));
    }

    public boolean getKeepScreenOn() { return prefs.getBoolean("keep_screen_on", true); }
    public void setKeepScreenOn(boolean v) { prefs.edit().putBoolean("keep_screen_on", v).apply(); }
    public boolean getShowStatusBar() { return prefs.getBoolean("show_status_bar", false); }
    public void setShowStatusBar(boolean v) { prefs.edit().putBoolean("show_status_bar", v).apply(); }
    public boolean getAutoSavePosition() { return prefs.getBoolean("auto_save_position", true); }
    public void setAutoSavePosition(boolean v) { prefs.edit().putBoolean("auto_save_position", v).apply(); }
    public String getLastDirectory() { return prefs.getString("last_directory", null); }
    public void setLastDirectory(String p) { prefs.edit().putString("last_directory", p).apply(); }
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
    public int getPagingOverlapLines() { return prefs.getInt("paging_overlap_lines", 0); }
    public void setPagingOverlapLines(int lines) { prefs.edit().putInt("paging_overlap_lines", Math.max(0, Math.min(4, lines))).apply(); }
}
