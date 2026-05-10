package com.simpletext.reader.util;

import android.content.Context;
import com.simpletext.reader.model.Theme;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages reading themes (built-in + custom).
 */
public class ThemeManager {
    private static final String THEMES_FILE = "custom_themes.json";

    private static ThemeManager instance;
    private final Context context;
    private List<Theme> customThemes;
    private String activeThemeId;

    private ThemeManager(Context context) {
        this.context = context.getApplicationContext();
        loadCustomThemes();
        activeThemeId = PrefsManager.getInstance(context)
                .getPrefs().getString("active_theme_id", "dark");
    }

    public static synchronized ThemeManager getInstance(Context context) {
        if (instance == null) {
            instance = new ThemeManager(context);
        }
        return instance;
    }

    /**
     * Re-read theme state that can be changed by another Activity before a viewer
     * resumes. This prevents viewer popups from using a stale cached theme after
     * returning from Settings or the theme editor.
     */
    public synchronized void reloadFromStorage() {
        loadCustomThemes();
        syncActiveThemeIdFromPrefs();
    }

    private void syncActiveThemeIdFromPrefs() {
        activeThemeId = PrefsManager.getInstance(context)
                .getPrefs().getString("active_theme_id", "dark");
    }

    public synchronized List<Theme> getAllThemes() {
        syncActiveThemeIdFromPrefs();
        List<Theme> all = new ArrayList<>();
        for (Theme t : Theme.BUILT_IN_THEMES) {
            all.add(t);
        }
        all.addAll(customThemes);
        return all;
    }

    public synchronized Theme getActiveTheme() {
        syncActiveThemeIdFromPrefs();
        for (Theme t : Theme.BUILT_IN_THEMES) {
            if (t.getId().equals(activeThemeId)) return t;
        }
        for (Theme t : customThemes) {
            if (t.getId().equals(activeThemeId)) return t;
        }
        return Theme.LIGHT;
    }

    public synchronized void setActiveTheme(String themeId) {
        this.activeThemeId = themeId;
        PrefsManager.getInstance(context).getPrefs().edit()
                .putString("active_theme_id", themeId).commit();
    }

    public synchronized void addCustomTheme(Theme theme) {
        customThemes.add(theme);
        saveCustomThemes();
    }

    public synchronized void updateCustomTheme(Theme theme) {
        for (int i = 0; i < customThemes.size(); i++) {
            if (customThemes.get(i).getId().equals(theme.getId())) {
                customThemes.set(i, theme);
                saveCustomThemes();
                return;
            }
        }
    }

    public synchronized void deleteCustomTheme(String themeId) {
        customThemes.removeIf(t -> t.getId().equals(themeId));
        if (activeThemeId.equals(themeId)) {
            setActiveTheme("light");
        }
        saveCustomThemes();
    }

    private void loadCustomThemes() {
        customThemes = new ArrayList<>();
        File file = new File(context.getFilesDir(), THEMES_FILE);
        if (!file.exists()) return;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);

            JSONObject root = new JSONObject(sb.toString());
            JSONArray arr = root.optJSONArray("themes");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    customThemes.add(Theme.fromJson(arr.getJSONObject(i)));
                }
            }
        } catch (Exception e) {
            // Do not write user file paths or local document/font names to release Logcat.
        }
    }

    private void saveCustomThemes() {
        try {
            JSONObject root = new JSONObject();
            JSONArray arr = new JSONArray();
            for (Theme t : customThemes) {
                arr.put(t.toJson());
            }
            root.put("themes", arr);

            File file = new File(context.getFilesDir(), THEMES_FILE);
            try (OutputStreamWriter writer = new OutputStreamWriter(
                    new FileOutputStream(file), StandardCharsets.UTF_8)) {
                writer.write(root.toString(2));
            }
        } catch (Exception e) {
            // Do not write user file paths or local document/font names to release Logcat.
        }
    }
}
