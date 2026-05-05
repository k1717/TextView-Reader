package com.simpletext.reader.util;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Scans for and manages fonts available on the device.
 */
public class FontManager {
    private static final String TAG = "FontManager";
    private static FontManager instance;

    private final Map<String, String> fontPaths = new HashMap<>(); // displayName -> path
    private final Map<String, Typeface> fontCache = new HashMap<>();
    private boolean scanned = false;

    // Standard Android font directories
    private static final String[] FONT_DIRS = {
            "/system/fonts",
            "/system/font",
            "/data/fonts"
    };

    private static final String[] FONT_EXTENSIONS = {".ttf", ".otf", ".ttc"};

    public interface OnScanCompleteListener {
        void onScanComplete(List<String> fontNames);
    }

    private FontManager() {}

    public static synchronized FontManager getInstance() {
        if (instance == null) {
            instance = new FontManager();
        }
        return instance;
    }

    /**
     * Scan for fonts asynchronously.
     */
    public void scanFonts(Context context, OnScanCompleteListener listener) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            doScan(context);
            List<String> names = getFontNames();
            if (listener != null) {
                ((android.app.Activity) context).runOnUiThread(() -> listener.onScanComplete(names));
            }
        });
    }

    /**
     * Scan fonts synchronously.
     */
    public void scanFontsSync(Context context) {
        doScan(context);
    }

    private void doScan(Context context) {
        fontPaths.clear();

        // Always include defaults
        fontPaths.put("Default (Sans-serif)", "DEFAULT");
        fontPaths.put("Serif", "SERIF");
        fontPaths.put("Monospace", "MONOSPACE");

        // Scan system fonts
        for (String dir : FONT_DIRS) {
            scanDirectory(new File(dir));
        }

        // Scan user font directories
        File externalFonts = new File(Environment.getExternalStorageDirectory(), "Fonts");
        if (externalFonts.exists()) {
            scanDirectory(externalFonts);
        }

        File downloadFonts = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), "Fonts");
        if (downloadFonts.exists()) {
            scanDirectory(downloadFonts);
        }

        // App-specific font directory
        File appFonts = new File(context.getFilesDir(), "fonts");
        if (appFonts.exists()) {
            scanDirectory(appFonts);
        }

        scanned = true;
        Log.i(TAG, "Font scan complete: " + fontPaths.size() + " fonts found");
    }

    private void scanDirectory(File dir) {
        if (!dir.exists() || !dir.isDirectory() || !dir.canRead()) return;

        File[] files = dir.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (f.isDirectory()) {
                scanDirectory(f); // recursive
                continue;
            }
            String name = f.getName().toLowerCase();
            for (String ext : FONT_EXTENSIONS) {
                if (name.endsWith(ext)) {
                    try {
                        // Try to load it to verify it's valid
                        Typeface tf = Typeface.createFromFile(f);
                        if (tf != null) {
                            String displayName = f.getName();
                            // Remove extension for display
                            int dotIdx = displayName.lastIndexOf('.');
                            if (dotIdx > 0) displayName = displayName.substring(0, dotIdx);
                            // Replace hyphens/underscores with spaces
                            displayName = displayName.replace('-', ' ').replace('_', ' ');
                            fontPaths.put(displayName, f.getAbsolutePath());
                            fontCache.put(f.getAbsolutePath(), tf);
                        }
                    } catch (Exception e) {
                        // Skip invalid font files
                    }
                    break;
                }
            }
        }
    }

    public List<String> getFontNames() {
        List<String> names = new ArrayList<>(fontPaths.keySet());
        Collections.sort(names, (a, b) -> {
            // Keep defaults at top
            boolean aDefault = a.startsWith("Default") || a.equals("Serif") || a.equals("Monospace");
            boolean bDefault = b.startsWith("Default") || b.equals("Serif") || b.equals("Monospace");
            if (aDefault && !bDefault) return -1;
            if (!aDefault && bDefault) return 1;
            return a.compareToIgnoreCase(b);
        });
        return names;
    }

    /**
     * Get Typeface for a font name.
     */
    public Typeface getTypeface(String fontName) {
        if (fontName == null || fontName.equals("default") || fontName.equals("DEFAULT")) {
            return Typeface.DEFAULT;
        }

        String path = fontPaths.get(fontName);
        if (path == null) {
            // Try matching by stored path directly
            path = fontName;
        }

        switch (path) {
            case "DEFAULT":
                return Typeface.DEFAULT;
            case "SERIF":
                return Typeface.SERIF;
            case "MONOSPACE":
                return Typeface.MONOSPACE;
            default:
                // Load from file
                if (fontCache.containsKey(path)) {
                    return fontCache.get(path);
                }
                try {
                    Typeface tf = Typeface.createFromFile(path);
                    fontCache.put(path, tf);
                    return tf;
                } catch (Exception e) {
                    Log.e(TAG, "Failed to load font: " + path, e);
                    return Typeface.DEFAULT;
                }
        }
    }

    public boolean isScanned() { return scanned; }

    /**
     * Copy a font file to the app's internal font directory.
     */
    public String importFont(Context context, File sourceFile) {
        File fontDir = new File(context.getFilesDir(), "fonts");
        if (!fontDir.exists()) fontDir.mkdirs();

        File destFile = new File(fontDir, sourceFile.getName());
        try {
            java.nio.file.Files.copy(sourceFile.toPath(), destFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            // Verify and register
            Typeface tf = Typeface.createFromFile(destFile);
            if (tf != null) {
                String displayName = destFile.getName();
                int dotIdx = displayName.lastIndexOf('.');
                if (dotIdx > 0) displayName = displayName.substring(0, dotIdx);
                displayName = displayName.replace('-', ' ').replace('_', ' ');
                fontPaths.put(displayName, destFile.getAbsolutePath());
                fontCache.put(destFile.getAbsolutePath(), tf);
                return displayName;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to import font", e);
        }
        return null;
    }
}
