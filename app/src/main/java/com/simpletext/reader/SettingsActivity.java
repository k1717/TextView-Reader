package com.simpletext.reader;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.simpletext.reader.util.BookmarkManager;
import com.simpletext.reader.util.EdgeToEdgeUtil;
import com.simpletext.reader.util.FileUtils;
import com.simpletext.reader.util.PrefsManager;

import com.google.android.material.button.MaterialButton;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class SettingsActivity extends AppCompatActivity {

    private PrefsManager prefs;
    private BookmarkManager bookmarkManager;

    private final ActivityResultLauncher<String> exportLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"),
                    uri -> { if (uri != null) exportBookmarksTo(uri); });
    private final ActivityResultLauncher<String[]> importLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                    uri -> { if (uri != null) importBookmarksFrom(uri); });
    private final ActivityResultLauncher<Intent> lockSetLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Switch lockSwitch = findViewById(R.id.switch_lock);
                    if (lockSwitch != null) lockSwitch.setChecked(prefs.isLockEnabled());
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = PrefsManager.getInstance(this);
        prefs.applyLanguage(prefs.getLanguageMode());
        prefs.applyDarkMode(prefs.getDarkMode());
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        EdgeToEdgeUtil.applyStandardInsets(this, findViewById(R.id.settings_root),
                findViewById(R.id.settings_appbar), findViewById(R.id.settings_scroll));

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.title_settings));
        }
        tintToolbarNavigation(toolbar);

        bookmarkManager = BookmarkManager.getInstance(this);

        setupLanguage();
        setupDarkMode();
        setupFontSize();
        setupLineSpacing();
        setupSwitches();
        setupLock();
        setupExportImport();
        setupTheme();

        // Force readable colors for every control after Android/Material defaults are applied.
        applySettingsReadableTheme();
    }

    private void tintToolbarNavigation(Toolbar toolbar) {
        tintToolbarNavigation(toolbar, Color.WHITE);
    }


    private void setupLanguage() {
        RadioGroup group = findViewById(R.id.language_group);
        if (group == null) return;

        int current = prefs.getLanguageMode();
        if (current == PrefsManager.LANGUAGE_KOREAN) {
            ((RadioButton) findViewById(R.id.radio_language_korean)).setChecked(true);
        } else {
            ((RadioButton) findViewById(R.id.radio_language_english)).setChecked(true);
        }

        group.setOnCheckedChangeListener((g, checkedId) -> {
            int mode = checkedId == R.id.radio_language_korean
                    ? PrefsManager.LANGUAGE_KOREAN
                    : PrefsManager.LANGUAGE_ENGLISH;

            if (prefs.getLanguageMode() != mode) {
                prefs.setLanguageMode(mode);
                recreate();
            }
        });
    }

    private void setupDarkMode() {
        RadioGroup group = findViewById(R.id.dark_mode_group);
        int current = prefs.getDarkMode();
        if (current == PrefsManager.DARK_MODE_OFF) ((RadioButton) findViewById(R.id.radio_light)).setChecked(true);
        else if (current == PrefsManager.DARK_MODE_ON) ((RadioButton) findViewById(R.id.radio_dark)).setChecked(true);
        else ((RadioButton) findViewById(R.id.radio_system)).setChecked(true);

        group.setOnCheckedChangeListener((g, checkedId) -> {
            int mode;
            if (checkedId == R.id.radio_light) mode = PrefsManager.DARK_MODE_OFF;
            else if (checkedId == R.id.radio_dark) mode = PrefsManager.DARK_MODE_ON;
            else mode = PrefsManager.DARK_MODE_FOLLOW_SYSTEM;
            if (prefs.getDarkMode() != mode) {
                prefs.setDarkMode(mode);
                recreate();
            }
        });
    }

    private void setupFontSize() {
        SeekBar sb = findViewById(R.id.font_size_seekbar);
        TextView label = findViewById(R.id.font_size_label);
        float current = prefs.getFontSize();
        sb.setMax(40);
        sb.setProgress((int) (current - 8));
        label.setText(String.format(getString(R.string.font_size_format), current));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                float size = p + 8f;
                label.setText(String.format(getString(R.string.font_size_format), size));
                if (fromUser) prefs.setFontSize(size);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
    }

    private void setupLineSpacing() {
        SeekBar sb = findViewById(R.id.line_spacing_seekbar);
        TextView label = findViewById(R.id.line_spacing_label);
        float current = prefs.getLineSpacing();
        sb.setMax(20);
        sb.setProgress((int) ((current - 1.0f) * 10));
        label.setText(String.format(getString(R.string.line_spacing_format), current));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                float spacing = 1.0f + p / 10f;
                label.setText(String.format(getString(R.string.line_spacing_format), spacing));
                if (fromUser) prefs.setLineSpacing(spacing);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
    }

    private void setupSwitches() {
        Switch switchScreenOn = findViewById(R.id.switch_keep_screen_on);
        switchScreenOn.setChecked(prefs.getKeepScreenOn());
        switchScreenOn.setOnCheckedChangeListener((v, c) -> prefs.setKeepScreenOn(c));

        Switch switchAutoSave = findViewById(R.id.switch_auto_save);
        switchAutoSave.setChecked(prefs.getAutoSavePosition());
        switchAutoSave.setOnCheckedChangeListener((v, c) -> prefs.setAutoSavePosition(c));

        Switch switchStatusBar = findViewById(R.id.switch_status_bar);
        switchStatusBar.setChecked(prefs.getShowStatusBar());
        switchStatusBar.setOnCheckedChangeListener((v, c) -> prefs.setShowStatusBar(c));

        Switch switchNotification = findViewById(R.id.switch_notification);
        switchNotification.setChecked(prefs.getShowNotification());
        switchNotification.setOnCheckedChangeListener((v, c) -> prefs.setShowNotification(c));

        Switch switchVolume = findViewById(R.id.switch_volume_scroll);
        switchVolume.setChecked(prefs.getVolumeKeyScroll());
        switchVolume.setOnCheckedChangeListener((v, c) -> prefs.setVolumeKeyScroll(c));

        Switch switchTapPaging = findViewById(R.id.switch_tap_paging);
        switchTapPaging.setChecked(prefs.getTapPagingEnabled());
        switchTapPaging.setOnCheckedChangeListener((v, c) -> prefs.setTapPagingEnabled(c));

        Spinner overlapSpinner = findViewById(R.id.spinner_overlap_lines);
        String[] choices = {getString(R.string.no_overlap), getString(R.string.keep_1_line), getString(R.string.keep_2_lines), getString(R.string.keep_3_lines), getString(R.string.keep_4_lines)};
        ArrayAdapter<String> overlapAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, choices) {
            @NonNull @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                styleSpinnerText(view);
                return view;
            }
            @Override
            public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                styleSpinnerText(view);
                return view;
            }
        };
        overlapAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        overlapSpinner.setAdapter(overlapAdapter);
        overlapSpinner.setSelection(prefs.getPagingOverlapLines());
        overlapSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                styleSpinnerText(view);
                prefs.setPagingOverlapLines(position);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void styleSpinnerText(View view) {
        if (view instanceof TextView) {
            boolean dark = isDarkUi();
            int bg = dark ? Color.rgb(0, 0, 0) : Color.rgb(255, 255, 255);
            int text = dark ? Color.rgb(232, 234, 237) : Color.rgb(32, 33, 36);
            TextView tv = (TextView) view;
            tv.setTextColor(text);
            tv.setBackgroundColor(bg);
            tv.setTextSize(16f);
            int pad = Math.round(14 * getResources().getDisplayMetrics().density);
            tv.setPadding(pad, 0, pad, 0);
        }
    }

    private int resolveColor(int attr) {
        android.util.TypedValue out = new android.util.TypedValue();
        getTheme().resolveAttribute(attr, out, true);
        if (out.resourceId != 0) return ContextCompat.getColor(this, out.resourceId);
        return out.data;
    }


    private boolean isDarkUi() {
        int mask = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mask == Configuration.UI_MODE_NIGHT_YES;
    }

    private ColorStateList twoState(int checkedColor, int uncheckedColor) {
        return new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{}
                },
                new int[]{checkedColor, uncheckedColor}
        );
    }

    private void applySettingsReadableTheme() {
        boolean dark = isDarkUi();

        int bg = dark ? Color.rgb(0, 0, 0) : Color.rgb(255, 255, 255);
        int panel = dark ? Color.rgb(16, 16, 16) : Color.rgb(248, 249, 250);
        int text = dark ? Color.rgb(232, 234, 237) : Color.rgb(32, 33, 36);
        int sub = dark ? Color.rgb(176, 176, 176) : Color.rgb(95, 99, 104);
        int bar = dark ? Color.rgb(0, 0, 0) : Color.rgb(32, 33, 36);
        int outline = dark ? Color.rgb(70, 70, 70) : Color.rgb(210, 210, 210);

        // Monochrome / greyscale controls. No cyan/green accent.
        int checked = dark ? Color.rgb(210, 210, 210) : Color.rgb(80, 80, 80);
        int uncheckedThumb = dark ? Color.rgb(145, 145, 145) : Color.rgb(170, 170, 170);
        int uncheckedTrack = dark ? Color.rgb(70, 70, 70) : Color.rgb(210, 210, 210);
        int checkedTrack = dark ? Color.rgb(95, 95, 95) : Color.rgb(180, 180, 180);

        View root = findViewById(R.id.settings_root);
        View appbar = findViewById(R.id.settings_appbar);
        View scroll = findViewById(R.id.settings_scroll);
        Toolbar toolbar = findViewById(R.id.toolbar);

        if (root != null) root.setBackgroundColor(bg);
        if (scroll != null) scroll.setBackgroundColor(bg);
        if (appbar != null) appbar.setBackgroundColor(bar);
        if (toolbar != null) {
            toolbar.setBackgroundColor(bar);
            toolbar.setTitleTextColor(Color.WHITE);
            tintToolbarNavigation(toolbar, Color.WHITE);
        }

        getWindow().setStatusBarColor(bar);
        getWindow().setNavigationBarColor(bg);

        androidx.core.view.WindowInsetsControllerCompat controller =
                androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(!dark);

        ColorStateList radioButtonTint = twoState(checked, uncheckedThumb);
        ColorStateList switchThumbTint = twoState(checked, uncheckedThumb);
        ColorStateList switchTrackTint = twoState(checkedTrack, uncheckedTrack);
        ColorStateList seekTint = ColorStateList.valueOf(checked);
        ColorStateList seekBgTint = ColorStateList.valueOf(outline);
        ColorStateList outlineTint = ColorStateList.valueOf(outline);
        ColorStateList rippleTint = ColorStateList.valueOf(panel);

        applyReadableColorsRecursive(root, text, sub, bg,
                radioButtonTint, switchThumbTint, switchTrackTint,
                seekTint, seekBgTint, outlineTint, rippleTint);

        // Page Overlap spinner: no weird boxed field. Blend into background.
        Spinner spinner = findViewById(R.id.spinner_overlap_lines);
        if (spinner != null) {
            spinner.setBackgroundColor(bg);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                spinner.setBackgroundTintList(ColorStateList.valueOf(bg));
            }
            int spinPad = Math.round(14 * getResources().getDisplayMetrics().density);
            spinner.setPadding(spinPad, 0, spinPad, 0);
        }

        TextView font = findViewById(R.id.font_size_label);
        if (font != null) font.setTextColor(sub);
        TextView spacing = findViewById(R.id.line_spacing_label);
        if (spacing != null) spacing.setTextColor(sub);
    }

    private void applyReadableColorsRecursive(
            View view,
            int text,
            int sub,
            int bg,
            ColorStateList radioButtonTint,
            ColorStateList switchThumbTint,
            ColorStateList switchTrackTint,
            ColorStateList seekTint,
            ColorStateList seekBgTint,
            ColorStateList outlineTint,
            ColorStateList rippleTint
    ) {
        if (view == null) return;

        if (view instanceof EditText) {
            EditText et = (EditText) view;
            et.setTextColor(text);
            et.setHintTextColor(sub);
            et.setBackgroundColor(bg);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                et.setBackgroundTintList(outlineTint);
            }
        } else if (view instanceof Switch) {
            Switch sw = (Switch) view;
            sw.setTextColor(text);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                sw.setThumbTintList(switchThumbTint);
                sw.setTrackTintList(switchTrackTint);
            }
        } else if (view instanceof RadioButton) {
            RadioButton rb = (RadioButton) view;
            rb.setTextColor(text);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                rb.setButtonTintList(radioButtonTint);
            }
        } else if (view instanceof CompoundButton) {
            CompoundButton cb = (CompoundButton) view;
            cb.setTextColor(text);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                cb.setButtonTintList(radioButtonTint);
            }
        } else if (view instanceof MaterialButton) {
            MaterialButton mb = (MaterialButton) view;
            mb.setTextColor(text);
            mb.setStrokeColor(outlineTint);
            mb.setRippleColor(rippleTint);
        } else if (view instanceof TextView) {
            TextView tv = (TextView) view;
            tv.setTextColor(text);
        }

        if (view instanceof SeekBar && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            SeekBar sb = (SeekBar) view;
            sb.setThumbTintList(seekTint);
            sb.setProgressTintList(seekTint);
            sb.setProgressBackgroundTintList(seekBgTint);
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyReadableColorsRecursive(group.getChildAt(i), text, sub, bg,
                        radioButtonTint, switchThumbTint, switchTrackTint,
                        seekTint, seekBgTint, outlineTint, rippleTint);
            }
        }
    }

    private void tintToolbarNavigation(Toolbar toolbar, int color) {
        Drawable nav = toolbar.getNavigationIcon();
        if (nav != null) {
            Drawable wrapped = DrawableCompat.wrap(nav.mutate());
            DrawableCompat.setTint(wrapped, color);
            toolbar.setNavigationIcon(wrapped);
        }
    }


    private void setupLock() {
        Switch switchLock = findViewById(R.id.switch_lock);
        switchLock.setChecked(prefs.isLockEnabled());
        switchLock.setOnCheckedChangeListener((v, checked) -> {
            if (checked) {
                Intent intent = new Intent(this, LockActivity.class);
                intent.putExtra(LockActivity.EXTRA_MODE, LockActivity.MODE_SET_PIN);
                lockSetLauncher.launch(intent);
            } else {
                prefs.setLockEnabled(false);
                prefs.setLockPin("");
            }
        });

        findViewById(R.id.btn_change_pin).setOnClickListener(v -> {
            if (prefs.isLockEnabled()) {
                Intent intent = new Intent(this, LockActivity.class);
                intent.putExtra(LockActivity.EXTRA_MODE, LockActivity.MODE_CHANGE_PIN);
                lockSetLauncher.launch(intent);
            } else {
                Toast.makeText(this, getString(R.string.enable_lock_first), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupExportImport() {
        findViewById(R.id.btn_export).setOnClickListener(v -> exportLauncher.launch("simpletext_backup.json"));
        findViewById(R.id.btn_import).setOnClickListener(v -> importLauncher.launch(new String[]{"application/json", "text/*"}));
    }

    private void setupTheme() {
        findViewById(R.id.btn_manage_themes).setOnClickListener(v -> startActivity(new Intent(this, ThemeEditorActivity.class)));
    }

    private void exportBookmarksTo(Uri uri) {
        try {
            String json = bookmarkManager.exportAll();
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                if (os != null) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                    Toast.makeText(this, getString(R.string.exported_successfully), Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.export_failed_prefix) + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void importBookmarksFrom(Uri uri) {
        try {
            String json = FileUtils.readTextFromUri(this, uri);
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(getString(R.string.import_bookmarks))
                    .setMessage(getString(R.string.merge_or_replace))
                    .setPositiveButton(getString(R.string.merge), (d, w) -> {
                        bookmarkManager.importAll(json, true);
                        Toast.makeText(this, getString(R.string.imported_merged), Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton(getString(R.string.replace), (d, w) -> {
                        bookmarkManager.importAll(json, false);
                        Toast.makeText(this, getString(R.string.imported_replaced), Toast.LENGTH_SHORT).show();
                    })
                    .setNeutralButton(getString(R.string.cancel), null).show();
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.import_failed_prefix) + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
