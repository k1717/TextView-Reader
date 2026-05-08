package com.simpletext.reader;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
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

import com.simpletext.reader.model.Theme;
import com.simpletext.reader.util.BookmarkManager;
import com.simpletext.reader.util.EdgeToEdgeUtil;
import com.simpletext.reader.util.FileUtils;
import com.simpletext.reader.util.PrefsManager;
import com.simpletext.reader.util.ThemeManager;

import com.google.android.material.button.MaterialButton;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class SettingsActivity extends AppCompatActivity {

    private PrefsManager prefs;
    private BookmarkManager bookmarkManager;
    private ThemeManager themeManager;

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
        themeManager = ThemeManager.getInstance(this);

        setupLanguage();
        setupDarkMode();
        setupFontSize();
        setupLineSpacing();
        setupTextZoneTuning();
        setupSwitches();
        setupLock();
        setupExportImport();
        setupTheme();

        // Force readable colors for every control after Android/Material defaults are applied.
        applySettingsReadableTheme();
        renderReadingThemeRows();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (prefs != null && themeManager != null) {
            applySettingsReadableTheme();
            renderReadingThemeRows();
        }
    }

    private void tintToolbarNavigation(Toolbar toolbar) {
        tintToolbarNavigation(toolbar, Color.WHITE);
    }


    private void setupLanguage() {
        RadioGroup group = findViewById(R.id.language_group);
        if (group == null) return;

        group.setOnCheckedChangeListener(null);

        int current = prefs.getLanguageMode();
        group.check(current == PrefsManager.LANGUAGE_KOREAN
                ? R.id.radio_language_korean
                : R.id.radio_language_english);

        group.setOnCheckedChangeListener((g, checkedId) -> {
            int mode = checkedId == R.id.radio_language_korean
                    ? PrefsManager.LANGUAGE_KOREAN
                    : PrefsManager.LANGUAGE_ENGLISH;

            if (prefs.getLanguageMode() != mode) {
                // AppCompatDelegate recreates the visible activities after setApplicationLocales().
                // Calling recreate() manually here can race with that recreation and produce
                // the "opposite language" glitch.
                prefs.setLanguageMode(mode);
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

    private void setupTapZoneRatioControl() {
        SeekBar leadingSeekBar = findViewById(R.id.tap_zone_leading_seekbar);
        SeekBar trailingSeekBar = findViewById(R.id.tap_zone_trailing_seekbar);
        TextView ratioLabel = findViewById(R.id.tap_zone_ratio_label);
        TextView leadingLabel = findViewById(R.id.tap_zone_leading_label);
        TextView trailingLabel = findViewById(R.id.tap_zone_trailing_label);
        if (leadingSeekBar == null || trailingSeekBar == null || ratioLabel == null
                || leadingLabel == null || trailingLabel == null) return;

        // Two tunable endpoints:
        // leading = top/left previous-page zone, trailing = bottom/right next-page zone,
        // middle/menu = 100 - leading - trailing.
        final boolean[] suppress = new boolean[]{false};

        int leading = clampTapEdgePercent(prefs.getTapLeadingZonePercent());
        int trailing = clampTapEdgePercent(prefs.getTapTrailingZonePercent());
        if (leading + trailing > 90) trailing = Math.max(5, 90 - leading);

        leadingSeekBar.setMax(75);    // progress 0..75 => 5..80%
        trailingSeekBar.setMax(75);   // progress 0..75 => 5..80%

        leadingSeekBar.setProgress(leading - 5);
        trailingSeekBar.setProgress(trailing - 5);
        updateTapZoneRatioLabels(ratioLabel, leadingLabel, trailingLabel, leading, trailing);

        leadingSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (suppress[0]) return;
                int leading = progress + 5;
                int trailing = trailingSeekBar.getProgress() + 5;
                if (leading + trailing > 90) {
                    trailing = Math.max(5, 90 - leading);
                    suppress[0] = true;
                    trailingSeekBar.setProgress(trailing - 5);
                    suppress[0] = false;
                }
                updateTapZoneRatioLabels(ratioLabel, leadingLabel, trailingLabel, leading, trailing);
                if (fromUser) prefs.setTapZonePercents(leading, trailing);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                prefs.setTapZonePercents(seekBar.getProgress() + 5, trailingSeekBar.getProgress() + 5);
            }
        });

        trailingSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (suppress[0]) return;
                int leading = leadingSeekBar.getProgress() + 5;
                int trailing = progress + 5;
                if (leading + trailing > 90) {
                    leading = Math.max(5, 90 - trailing);
                    suppress[0] = true;
                    leadingSeekBar.setProgress(leading - 5);
                    suppress[0] = false;
                }
                updateTapZoneRatioLabels(ratioLabel, leadingLabel, trailingLabel, leading, trailing);
                if (fromUser) prefs.setTapZonePercents(leading, trailing);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                prefs.setTapZonePercents(leadingSeekBar.getProgress() + 5, seekBar.getProgress() + 5);
            }
        });
    }

    private int clampTapEdgePercent(int percent) {
        return Math.max(5, Math.min(80, percent));
    }

    private void updateTapZoneRatioLabels(TextView ratioLabel, TextView leadingLabel,
                                          TextView trailingLabel, int leadingPercent,
                                          int trailingPercent) {
        int leading = clampTapEdgePercent(leadingPercent);
        int trailing = clampTapEdgePercent(trailingPercent);
        if (leading + trailing > 90) trailing = Math.max(5, 90 - leading);
        int middle = Math.max(10, 100 - leading - trailing);

        ratioLabel.setText(getString(R.string.tap_zone_ratio_format, leading, middle, trailing));
        leadingLabel.setText(getString(R.string.tap_zone_leading_format, leading));
        trailingLabel.setText(getString(R.string.tap_zone_trailing_format, trailing));
    }


    private void setupTextZoneTuning() {
        SeekBar topSeekBar = findViewById(R.id.txt_top_offset_seekbar);
        TextView topLabel = findViewById(R.id.txt_top_offset_label);
        SeekBar bottomSeekBar = findViewById(R.id.txt_bottom_offset_seekbar);
        TextView bottomLabel = findViewById(R.id.txt_bottom_offset_label);
        SeekBar leftSeekBar = findViewById(R.id.txt_left_inset_seekbar);
        TextView leftLabel = findViewById(R.id.txt_left_inset_label);
        SeekBar rightSeekBar = findViewById(R.id.txt_right_inset_seekbar);
        TextView rightLabel = findViewById(R.id.txt_right_inset_label);
        if (topSeekBar == null || topLabel == null
                || bottomSeekBar == null || bottomLabel == null
                || leftSeekBar == null || leftLabel == null
                || rightSeekBar == null || rightLabel == null) {
            return;
        }

        final int textZoneStepPx = 5;
        final int textZoneMaxPx = 240;
        final int textZoneMaxProgress = textZoneMaxPx / textZoneStepPx;

        int topOffset = roundToTextZoneStep(prefs.getReaderTextTopOffsetPx(), textZoneStepPx);
        topSeekBar.setMax(textZoneMaxProgress); // 0px .. 240px, 5px steps
        topSeekBar.setProgress(topOffset / textZoneStepPx);
        topLabel.setText(getString(R.string.txt_top_offset_format, topOffset));
        topSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = progress * textZoneStepPx;
                topLabel.setText(getString(R.string.txt_top_offset_format, value));
                if (fromUser) prefs.setReaderTextTopOffsetPx(value);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                prefs.setReaderTextTopOffsetPx(seekBar.getProgress() * textZoneStepPx);
            }
        });

        int bottomOffset = roundToTextZoneStep(prefs.getReaderTextBottomOffsetPx(), textZoneStepPx);
        bottomSeekBar.setMax(textZoneMaxProgress); // 0px .. 240px, 5px steps
        bottomSeekBar.setProgress(bottomOffset / textZoneStepPx);
        bottomLabel.setText(getString(R.string.txt_bottom_offset_format, bottomOffset));
        bottomSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = progress * textZoneStepPx;
                bottomLabel.setText(getString(R.string.txt_bottom_offset_format, value));
                if (fromUser) prefs.setReaderTextBottomOffsetPx(value);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                prefs.setReaderTextBottomOffsetPx(seekBar.getProgress() * textZoneStepPx);
            }
        });

        int leftInset = roundToTextZoneStep(prefs.getReaderTextLeftInsetPx(), textZoneStepPx);
        leftSeekBar.setMax(textZoneMaxProgress); // 0px .. 240px, 5px steps
        leftSeekBar.setProgress(leftInset / textZoneStepPx);
        leftLabel.setText(getString(R.string.txt_left_inset_format, leftInset));
        leftSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = progress * textZoneStepPx;
                leftLabel.setText(getString(R.string.txt_left_inset_format, value));
                if (fromUser) prefs.setReaderTextLeftInsetPx(value);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                prefs.setReaderTextLeftInsetPx(seekBar.getProgress() * textZoneStepPx);
            }
        });

        int rightInset = roundToTextZoneStep(prefs.getReaderTextRightInsetPx(), textZoneStepPx);
        rightSeekBar.setMax(textZoneMaxProgress); // 0px .. 240px, 5px steps
        rightSeekBar.setProgress(rightInset / textZoneStepPx);
        rightLabel.setText(getString(R.string.txt_right_inset_format, rightInset));
        rightSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = progress * textZoneStepPx;
                rightLabel.setText(getString(R.string.txt_right_inset_format, value));
                if (fromUser) prefs.setReaderTextRightInsetPx(value);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                prefs.setReaderTextRightInsetPx(seekBar.getProgress() * textZoneStepPx);
            }
        });
    }

    private int roundToTextZoneStep(int px, int stepPx) {
        int clamped = Math.max(0, Math.min(240, px));
        return Math.round(clamped / (float) stepPx) * stepPx;
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

        Spinner tapZoneModeSpinner = findViewById(R.id.spinner_tap_zone_mode);
        String[] tapZoneChoices = {
                getString(R.string.tap_zone_vertical),
                getString(R.string.tap_zone_horizontal)
        };
        ArrayAdapter<String> tapZoneAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, tapZoneChoices) {
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
        tapZoneAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        tapZoneModeSpinner.setAdapter(tapZoneAdapter);
        tapZoneModeSpinner.setSelection(prefs.getTapZoneMode());
        tapZoneModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                styleSpinnerText(view);
                prefs.setTapZoneMode(position);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        setupTapZoneRatioControl();

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
        Spinner overlapSpinner = findViewById(R.id.spinner_overlap_lines);
        Spinner tapZoneSpinner = findViewById(R.id.spinner_tap_zone_mode);
        Spinner[] spinners = new Spinner[]{overlapSpinner, tapZoneSpinner};
        for (Spinner spinner : spinners) {
            if (spinner == null) continue;
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
        TextView topOffset = findViewById(R.id.txt_top_offset_label);
        if (topOffset != null) topOffset.setTextColor(sub);
        TextView bottomOffset = findViewById(R.id.txt_bottom_offset_label);
        if (bottomOffset != null) bottomOffset.setTextColor(sub);
        TextView leftInset = findViewById(R.id.txt_left_inset_label);
        if (leftInset != null) leftInset.setTextColor(sub);
        TextView rightInset = findViewById(R.id.txt_right_inset_label);
        if (rightInset != null) rightInset.setTextColor(sub);
        TextView ratio = findViewById(R.id.tap_zone_ratio_label);
        if (ratio != null) ratio.setTextColor(sub);
        TextView leading = findViewById(R.id.tap_zone_leading_label);
        if (leading != null) leading.setTextColor(sub);
        TextView trailing = findViewById(R.id.tap_zone_trailing_label);
        if (trailing != null) trailing.setTextColor(sub);
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
        findViewById(R.id.btn_manage_themes).setOnClickListener(v ->
                startActivity(new Intent(this, ThemeEditorActivity.class)));
    }

    private void renderReadingThemeRows() {
        LinearLayout container = findViewById(R.id.reading_theme_list);
        if (container == null || themeManager == null) return;

        container.removeAllViews();

        List<Theme> themes = themeManager.getAllThemes();
        Theme activeTheme = themeManager.getActiveTheme();
        String activeId = activeTheme != null ? activeTheme.getId() : "";

        boolean dark = isDarkUi();
        int rowBg = dark ? Color.rgb(10, 10, 10) : Color.rgb(255, 255, 255);
        int text = dark ? Color.rgb(232, 234, 237) : Color.rgb(32, 33, 36);
        int sub = dark ? Color.rgb(176, 176, 176) : Color.rgb(95, 99, 104);
        int outline = dark ? Color.rgb(70, 70, 70) : Color.rgb(218, 220, 224);
        int selectedOutline = dark ? Color.rgb(210, 210, 210) : Color.rgb(80, 80, 80);

        for (Theme theme : themes) {
            boolean selected = theme.getId().equals(activeId);
            View row = makeReadingThemeRow(theme, selected, rowBg, text, sub,
                    outline, selectedOutline);
            row.setOnClickListener(v -> {
                themeManager.setActiveTheme(theme.getId());
                renderReadingThemeRows();
            });
            if (!theme.isBuiltIn()) {
                row.setOnLongClickListener(v -> {
                    Intent edit = new Intent(this, ThemeEditorActivity.class);
                    edit.putExtra(ThemeEditorActivity.EXTRA_THEME_ID, theme.getId());
                    startActivity(edit);
                    return true;
                });
            }
            container.addView(row);
        }
    }

    private View makeReadingThemeRow(Theme theme, boolean selected, int rowBg,
                                     int text, int sub, int outline, int selectedOutline) {
        int pad12 = dpToPx(12);
        int pad14 = dpToPx(14);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(pad14, pad12, pad14, pad12);

        GradientDrawable rowDrawable = new GradientDrawable();
        rowDrawable.setColor(rowBg);
        rowDrawable.setCornerRadius(dpToPx(12));
        rowDrawable.setStroke(dpToPx(selected ? 2 : 1), selected ? selectedOutline : outline);
        row.setBackground(rowDrawable);

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(0, 0, 0, dpToPx(8));
        row.setLayoutParams(rowLp);
        row.setClickable(true);
        row.setFocusable(true);

        View preview = new View(this);
        GradientDrawable previewDrawable = new GradientDrawable();
        previewDrawable.setColor(theme.getBackgroundColor());
        previewDrawable.setCornerRadius(dpToPx(8));
        previewDrawable.setStroke(dpToPx(1), outline);
        preview.setBackground(previewDrawable);
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(dpToPx(48), dpToPx(48));
        previewLp.setMarginEnd(pad12);
        row.addView(preview, previewLp);

        LinearLayout textBox = new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);
        row.addView(textBox, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView name = new TextView(this);
        name.setText(theme.getName());
        name.setTextColor(text);
        name.setTextSize(16f);
        name.setSingleLine(true);
        textBox.addView(name, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView sample = new TextView(this);
        sample.setText("가나다라 ABC abc 123");
        sample.setTextColor(theme.getTextColor());
        sample.setTextSize(12f);
        sample.setSingleLine(true);
        sample.setPadding(pad12, dpToPx(4), pad12, dpToPx(4));
        GradientDrawable sampleBg = new GradientDrawable();
        sampleBg.setColor(theme.getBackgroundColor());
        sampleBg.setCornerRadius(dpToPx(6));
        sample.setBackground(sampleBg);
        LinearLayout.LayoutParams sampleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        sampleLp.setMargins(0, dpToPx(4), 0, 0);
        textBox.addView(sample, sampleLp);

        TextView check = new TextView(this);
        check.setText(selected ? "✓" : "");
        check.setTextColor(text);
        check.setTextSize(24f);
        check.setGravity(android.view.Gravity.CENTER);
        row.addView(check, new LinearLayout.LayoutParams(dpToPx(36),
                LinearLayout.LayoutParams.MATCH_PARENT));


        return row;
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
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
