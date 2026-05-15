package com.textview.reader.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/**
 * Custom reading theme with text/background colors and optional background image.
 */
public class Theme {
    private String id;
    private String name;
    private int textColor;
    private int backgroundColor;
    private int linkColor;
    private String backgroundImagePath; // null if no image
    private float backgroundImageAlpha; // 0.0 - 1.0
    private boolean isBuiltIn;

    // Built-in themes
    public static final Theme LIGHT = new Theme("light", "Light",
            0xFF212121, 0xFFFAFAFA, 0xFF1976D2, true);
    public static final Theme DARK = new Theme("dark", "Dark",
            0xFFE0E0E0, 0xFF000000, 0xFF9E9E9E, true);
    public static final Theme SEPIA = new Theme("sepia", "Sepia",
            0xFF5B4636, 0xFFF4ECD8, 0xFF8B5E3C, true);
    public static final Theme DARK_BLUE = new Theme("dark_blue", "Night Blue",
            0xFFB0BEC5, 0xFF1A237E, 0xFF82B1FF, true);
    public static final Theme GREEN_EYE = new Theme("green_eye", "Eye Care",
            0xFF2E4A2E, 0xFFC8E6C9, 0xFF1B5E20, true);
    public static final Theme CREAM = new Theme("cream", "Cream",
            0xFF3E2723, 0xFFFFF8E1, 0xFF6D4C41, true);

    public static final Theme[] BUILT_IN_THEMES = {LIGHT, DARK, SEPIA, DARK_BLUE, GREEN_EYE, CREAM};

    private Theme(String id, String name, int textColor, int bgColor, int linkColor, boolean builtIn) {
        this.id = id;
        this.name = name;
        this.textColor = textColor;
        this.backgroundColor = bgColor;
        this.linkColor = linkColor;
        this.backgroundImageAlpha = 0.3f;
        this.isBuiltIn = builtIn;
    }

    public Theme() {
        this.id = UUID.randomUUID().toString();
        this.backgroundImageAlpha = 0.3f;
        this.isBuiltIn = false;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("name", name);
        obj.put("textColor", textColor);
        obj.put("backgroundColor", backgroundColor);
        obj.put("linkColor", linkColor);
        obj.put("backgroundImagePath", backgroundImagePath != null ? backgroundImagePath : "");
        obj.put("backgroundImageAlpha", backgroundImageAlpha);
        obj.put("isBuiltIn", isBuiltIn);
        return obj;
    }

    public static Theme fromJson(JSONObject obj) throws JSONException {
        Theme t = new Theme();
        t.id = obj.getString("id");
        t.name = obj.optString("name", "Custom");
        t.textColor = obj.getInt("textColor");
        t.backgroundColor = obj.getInt("backgroundColor");
        t.linkColor = obj.optInt("linkColor", 0xFF1976D2);
        String imgPath = obj.optString("backgroundImagePath", "");
        t.backgroundImagePath = imgPath.isEmpty() ? null : imgPath;
        t.backgroundImageAlpha = (float) obj.optDouble("backgroundImageAlpha", 0.3);
        t.isBuiltIn = obj.optBoolean("isBuiltIn", false);
        return t;
    }

    // Getters/Setters
    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getTextColor() { return textColor; }
    public void setTextColor(int c) { this.textColor = c; }
    public int getBackgroundColor() { return backgroundColor; }
    public void setBackgroundColor(int c) { this.backgroundColor = c; }
    public int getLinkColor() { return linkColor; }
    public void setLinkColor(int c) { this.linkColor = c; }
    public String getBackgroundImagePath() { return backgroundImagePath; }
    public void setBackgroundImagePath(String p) { this.backgroundImagePath = p; }
    public float getBackgroundImageAlpha() { return backgroundImageAlpha; }
    public void setBackgroundImageAlpha(float a) { this.backgroundImageAlpha = a; }
    public boolean isBuiltIn() { return isBuiltIn; }

    public boolean isDark() {
        // Simple luminance check
        int r = (backgroundColor >> 16) & 0xFF;
        int g = (backgroundColor >> 8) & 0xFF;
        int b = backgroundColor & 0xFF;
        double luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
        return luminance < 0.5;
    }
}
