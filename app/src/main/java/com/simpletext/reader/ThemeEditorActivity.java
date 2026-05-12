package com.simpletext.reader;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.simpletext.reader.model.Theme;
import com.simpletext.reader.util.ThemeManager;

import com.google.android.material.button.MaterialButton;

/**
 * Activity for creating and editing custom reading themes.
 * Provides RGB sliders for text and background colors,
 * optional background image selection, and live preview.
 */
public class ThemeEditorActivity extends AppCompatActivity {

    public static final String EXTRA_THEME_ID = "theme_id";

    private EditText nameInput;
    private View bgPreview, textPreview;
    private TextView previewText;
    private SeekBar bgR, bgG, bgB, txtR, txtG, txtB;

    private int currentBgColor = 0xFFFAFAFA;
    private int currentTextColor = 0xFF212121;
    private String backgroundImagePath = null;
    private Theme editingTheme = null;

    private final ActivityResultLauncher<String[]> imagePicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    getContentResolver().takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    backgroundImagePath = uri.toString();
                    Toast.makeText(this, "Background image set", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_theme_editor);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        nameInput = findViewById(R.id.theme_name_input);
        bgPreview = findViewById(R.id.bg_color_preview);
        textPreview = findViewById(R.id.text_color_preview);
        previewText = findViewById(R.id.preview_text);

        bgR = findViewById(R.id.bg_red);
        bgG = findViewById(R.id.bg_green);
        bgB = findViewById(R.id.bg_blue);
        txtR = findViewById(R.id.txt_red);
        txtG = findViewById(R.id.txt_green);
        txtB = findViewById(R.id.txt_blue);

        applyReadableThemeEditorColors();

        // Check if editing existing theme
        String themeId = getIntent().getStringExtra(EXTRA_THEME_ID);
        if (themeId != null) {
            ThemeManager tm = ThemeManager.getInstance(this);
            for (Theme t : tm.getAllThemes()) {
                if (t.getId().equals(themeId) && !t.isBuiltIn()) {
                    editingTheme = t;
                    break;
                }
            }
        }

        if (editingTheme != null) {
            getSupportActionBar().setTitle("Edit Theme");
            nameInput.setText(editingTheme.getName());
            currentBgColor = editingTheme.getBackgroundColor();
            currentTextColor = editingTheme.getTextColor();
            backgroundImagePath = editingTheme.getBackgroundImagePath();
        } else {
            getSupportActionBar().setTitle("New Theme");
            nameInput.setText("Custom Theme");
        }

        // Set slider positions from colors
        bgR.setProgress(Color.red(currentBgColor));
        bgG.setProgress(Color.green(currentBgColor));
        bgB.setProgress(Color.blue(currentBgColor));
        txtR.setProgress(Color.red(currentTextColor));
        txtG.setProgress(Color.green(currentTextColor));
        txtB.setProgress(Color.blue(currentTextColor));

        SeekBar.OnSeekBarChangeListener colorListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) { updatePreview(); }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        };

        bgR.setOnSeekBarChangeListener(colorListener);
        bgG.setOnSeekBarChangeListener(colorListener);
        bgB.setOnSeekBarChangeListener(colorListener);
        txtR.setOnSeekBarChangeListener(colorListener);
        txtG.setOnSeekBarChangeListener(colorListener);
        txtB.setOnSeekBarChangeListener(colorListener);

        findViewById(R.id.btn_pick_bg_image).setOnClickListener(v ->
                imagePicker.launch(new String[]{"image/*"}));

        findViewById(R.id.btn_clear_bg_image).setOnClickListener(v -> {
            backgroundImagePath = null;
            Toast.makeText(this, "Background image cleared", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btn_save_theme).setOnClickListener(v -> saveTheme());

        updatePreview();
    }


    private boolean isDarkUi() {
        int mask = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mask == Configuration.UI_MODE_NIGHT_YES;
    }

    private void applyReadableThemeEditorColors() {
        boolean dark = isDarkUi();
        int bg = dark ? Color.rgb(0, 0, 0) : Color.WHITE;
        int text = dark ? Color.rgb(232, 234, 237) : Color.rgb(32, 33, 36);
        int sub = dark ? Color.rgb(176, 176, 176) : Color.rgb(95, 99, 104);
        int bar = dark ? Color.rgb(0, 0, 0) : Color.rgb(32, 33, 36);
        int outline = dark ? Color.rgb(68, 68, 68) : Color.rgb(218, 220, 224);
        int accent = dark ? Color.rgb(189, 189, 189) : Color.rgb(70, 70, 70);

        getWindow().setStatusBarColor(bar);
        getWindow().setNavigationBarColor(bg);
        androidx.core.view.WindowInsetsControllerCompat controller =
                androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(!dark);

        Toolbar tb = findViewById(R.id.toolbar);
        if (tb != null) {
            tb.setBackgroundColor(bar);
            tb.setTitleTextColor(Color.WHITE);
        }

        View root = findViewById(android.R.id.content);
        if (root != null) root.setBackgroundColor(bg);
        applyThemeEditorColorsRecursive(root, text, sub,
                ColorStateList.valueOf(accent),
                ColorStateList.valueOf(outline));

        // The Save Theme button is a filled action button. Do not reuse the
        // page text color here, because in light/main-white mode that made
        // black text sit on a dark filled button. Give the filled button an
        // explicit high-contrast foreground/background pair instead.
        MaterialButton saveButton = findViewById(R.id.btn_save_theme);
        if (saveButton != null) {
            int saveBg = dark ? Color.rgb(224, 224, 224) : Color.rgb(32, 33, 36);
            int saveFg = dark ? Color.rgb(0, 0, 0) : Color.WHITE;
            saveButton.setTextColor(saveFg);
            saveButton.setBackgroundTintList(ColorStateList.valueOf(saveBg));
            saveButton.setStrokeWidth(0);
            saveButton.setRippleColor(ColorStateList.valueOf(
                    dark ? Color.rgb(200, 200, 200) : Color.rgb(68, 68, 68)));
        }
    }

    private void applyThemeEditorColorsRecursive(
            View view, int text, int sub, ColorStateList accentState, ColorStateList outlineState) {
        if (view == null) return;

        if (view instanceof EditText) {
            EditText et = (EditText) view;
            et.setTextColor(text);
            et.setHintTextColor(sub);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                et.setBackgroundTintList(outlineState);
            }
        } else if (view instanceof MaterialButton) {
            MaterialButton mb = (MaterialButton) view;
            mb.setTextColor(text);
            mb.setStrokeColor(outlineState);
        } else if (view instanceof TextView && view != previewText) {
            ((TextView) view).setTextColor(text);
        }

        if (view instanceof SeekBar && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            SeekBar sb = (SeekBar) view;
            sb.setThumbTintList(accentState);
            sb.setProgressTintList(accentState);
            sb.setProgressBackgroundTintList(outlineState);
        }

        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyThemeEditorColorsRecursive(group.getChildAt(i), text, sub, accentState, outlineState);
            }
        }
    }


    private void updatePreview() {
        currentBgColor = Color.rgb(bgR.getProgress(), bgG.getProgress(), bgB.getProgress());
        currentTextColor = Color.rgb(txtR.getProgress(), txtG.getProgress(), txtB.getProgress());

        bgPreview.setBackgroundColor(currentBgColor);
        textPreview.setBackgroundColor(currentTextColor);
        previewText.setBackgroundColor(currentBgColor);
        previewText.setTextColor(currentTextColor);
    }

    private void saveTheme() {
        String name = nameInput.getText().toString().trim();
        if (name.isEmpty()) {
            nameInput.setError("Name required");
            return;
        }

        ThemeManager tm = ThemeManager.getInstance(this);

        if (editingTheme != null) {
            editingTheme.setName(name);
            editingTheme.setBackgroundColor(currentBgColor);
            editingTheme.setTextColor(currentTextColor);
            editingTheme.setBackgroundImagePath(backgroundImagePath);
            tm.updateCustomTheme(editingTheme);
        } else {
            Theme newTheme = new Theme();
            newTheme.setName(name);
            newTheme.setBackgroundColor(currentBgColor);
            newTheme.setTextColor(currentTextColor);
            newTheme.setBackgroundImagePath(backgroundImagePath);
            tm.addCustomTheme(newTheme);
        }

        Toast.makeText(this, "Theme saved", Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
