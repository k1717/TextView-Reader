package com.textview.reader;

import android.content.ClipData;
import android.content.ClipboardManager;
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

import com.textview.reader.model.Theme;
import com.textview.reader.util.BookmarkManager;
import com.textview.reader.util.EdgeToEdgeUtil;
import com.textview.reader.util.FileUtils;
import com.textview.reader.util.PrefsManager;
import com.textview.reader.util.ThemeManager;

import com.google.android.material.button.MaterialButton;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    private static final String RELEASES_URL = "https://github.com/k1717/TextView-Reader/releases";

    private PrefsManager prefs;
    private BookmarkManager bookmarkManager;
    private ThemeManager themeManager;

    private final ActivityResultLauncher<String> exportLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"),
                    uri -> { if (uri != null) exportBackupTo(uri); });
    private final ActivityResultLauncher<String[]> importLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                    uri -> { if (uri != null) importBackupFrom(uri); });
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
        setupEpubBoundary();
        setupEpubPageBehavior();
        setupSwitches();
        setupLock();
        setupExportImport();
        setupTheme();
        setupUpdateLink();

        // Force readable colors for every control after Android/Material defaults are applied.
        applySettingsReadableTheme();
        renderReadingThemeRows();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (themeManager != null) {
            themeManager.reloadFromStorage();
        }
        if (prefs != null && themeManager != null) {
            applySettingsReadableTheme();
            renderReadingThemeRows();
        }
    }

    private void tintToolbarNavigation(Toolbar toolbar) {
        tintToolbarNavigation(toolbar, Color.WHITE);
    }


    private void setupUpdateLink() {
        TextView updateLink = findViewById(R.id.update_release_link);
        if (updateLink == null) return;

        updateLink.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard == null) return;

            clipboard.setPrimaryClip(ClipData.newPlainText(
                    getString(R.string.settings_section_updates),
                    RELEASES_URL));
        });
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

    private void setupEpubBoundary() {
        SeekBar leftSeekBar = findViewById(R.id.epub_left_spacing_seekbar);
        TextView leftLabel = findViewById(R.id.epub_left_spacing_label);
        SeekBar rightSeekBar = findViewById(R.id.epub_right_spacing_seekbar);
        TextView rightLabel = findViewById(R.id.epub_right_spacing_label);
        SeekBar topSeekBar = findViewById(R.id.epub_top_spacing_seekbar);
        TextView topLabel = findViewById(R.id.epub_top_spacing_label);
        SeekBar bottomSeekBar = findViewById(R.id.epub_bottom_spacing_seekbar);
        TextView bottomLabel = findViewById(R.id.epub_bottom_spacing_label);
        if (leftSeekBar == null || leftLabel == null
                || rightSeekBar == null || rightLabel == null
                || topSeekBar == null || topLabel == null
                || bottomSeekBar == null || bottomLabel == null) {
            return;
        }

        final int epubBoundaryStepPx = 5;
        final int epubBoundaryMaxPx = 240;
        final int epubBoundaryMaxProgress = epubBoundaryMaxPx / epubBoundaryStepPx;

        leftSeekBar.setMax(epubBoundaryMaxProgress);
        rightSeekBar.setMax(epubBoundaryMaxProgress);
        topSeekBar.setMax(epubBoundaryMaxProgress);
        bottomSeekBar.setMax(epubBoundaryMaxProgress);

        int left = clampEpubBoundaryPx(prefs.getEpubLeftPaddingDp());
        int right = clampEpubBoundaryPx(prefs.getEpubRightPaddingDp());
        int top = clampEpubBoundaryPx(prefs.getEpubTopPaddingDp());
        int bottom = clampEpubBoundaryPx(prefs.getEpubBottomPaddingDp());

        leftSeekBar.setProgress(left / epubBoundaryStepPx);
        rightSeekBar.setProgress(right / epubBoundaryStepPx);
        topSeekBar.setProgress(top / epubBoundaryStepPx);
        bottomSeekBar.setProgress(bottom / epubBoundaryStepPx);

        leftLabel.setText(getString(R.string.epub_left_spacing_format, left));
        rightLabel.setText(getString(R.string.epub_right_spacing_format, right));
        topLabel.setText(getString(R.string.epub_top_spacing_format, top));
        bottomLabel.setText(getString(R.string.epub_bottom_spacing_format, bottom));

        leftSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = clampEpubBoundaryPx(progress * epubBoundaryStepPx);
                leftLabel.setText(getString(R.string.epub_left_spacing_format, value));
                if (fromUser) prefs.setEpubLeftPaddingDp(value);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                prefs.setEpubLeftPaddingDp(clampEpubBoundaryPx(seekBar.getProgress() * epubBoundaryStepPx));
            }
        });

        rightSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = clampEpubBoundaryPx(progress * epubBoundaryStepPx);
                rightLabel.setText(getString(R.string.epub_right_spacing_format, value));
                if (fromUser) prefs.setEpubRightPaddingDp(value);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                prefs.setEpubRightPaddingDp(clampEpubBoundaryPx(seekBar.getProgress() * epubBoundaryStepPx));
            }
        });

        topSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = clampEpubBoundaryPx(progress * epubBoundaryStepPx);
                topLabel.setText(getString(R.string.epub_top_spacing_format, value));
                if (fromUser) prefs.setEpubTopPaddingDp(value);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                prefs.setEpubTopPaddingDp(clampEpubBoundaryPx(seekBar.getProgress() * epubBoundaryStepPx));
            }
        });

        bottomSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = clampEpubBoundaryPx(progress * epubBoundaryStepPx);
                bottomLabel.setText(getString(R.string.epub_bottom_spacing_format, value));
                if (fromUser) prefs.setEpubBottomPaddingDp(value);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                prefs.setEpubBottomPaddingDp(clampEpubBoundaryPx(seekBar.getProgress() * epubBoundaryStepPx));
            }
        });
    }

    private int clampEpubBoundaryPx(int px) {
        int clamped = Math.max(0, Math.min(240, px));
        return Math.round(clamped / 5f) * 5;
    }

    private void setupEpubPageBehavior() {
        Spinner directionSpinner = findViewById(R.id.spinner_epub_page_direction);
        Spinner effectSpinner = findViewById(R.id.spinner_epub_page_effect);

        if (directionSpinner != null) {
            String[] directionChoices = {
                    getString(R.string.epub_page_direction_ltr),
                    getString(R.string.epub_page_direction_rtl)
            };
            ArrayAdapter<String> adapter = makeSettingsSpinnerAdapter(directionChoices);
            directionSpinner.setAdapter(adapter);
            directionSpinner.setSelection(prefs.getEpubPageDirection());
            directionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    styleSpinnerText(view);
                    prefs.setEpubPageDirection(position == PrefsManager.EPUB_PAGE_DIRECTION_RTL
                            ? PrefsManager.EPUB_PAGE_DIRECTION_RTL
                            : PrefsManager.EPUB_PAGE_DIRECTION_LTR);
                }
                @Override public void onNothingSelected(AdapterView<?> parent) {}
            });
        }

        if (effectSpinner != null) {
            String[] effectChoices = {
                    getString(R.string.epub_page_effect_slide),
                    getString(R.string.epub_page_effect_none)
            };
            ArrayAdapter<String> adapter = makeSettingsSpinnerAdapter(effectChoices);
            effectSpinner.setAdapter(adapter);
            effectSpinner.setSelection(prefs.getEpubPageEffect());
            effectSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    styleSpinnerText(view);
                    prefs.setEpubPageEffect(position == PrefsManager.EPUB_PAGE_EFFECT_NONE
                            ? PrefsManager.EPUB_PAGE_EFFECT_NONE
                            : PrefsManager.EPUB_PAGE_EFFECT_SLIDE);
                }
                @Override public void onNothingSelected(AdapterView<?> parent) {}
            });
        }
    }

    private ArrayAdapter<String> makeSettingsSpinnerAdapter(String[] choices) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
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
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
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

        Spinner pageStatusAlignSpinner = findViewById(R.id.spinner_page_status_alignment);
        String[] pageStatusAlignChoices = {
                getString(R.string.page_status_align_left),
                getString(R.string.page_status_align_center),
                getString(R.string.page_status_align_right),
                getString(R.string.page_status_align_hidden)
        };
        ArrayAdapter<String> pageStatusAlignAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, pageStatusAlignChoices) {
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
        pageStatusAlignAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        pageStatusAlignSpinner.setAdapter(pageStatusAlignAdapter);
        pageStatusAlignSpinner.setSelection(prefs.getPageStatusAlignment());
        pageStatusAlignSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                styleSpinnerText(view);
                prefs.setPageStatusAlignment(position);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

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

        TextView updateLink = findViewById(R.id.update_release_link);
        if (updateLink != null) {
            updateLink.setTextColor(checked);
            }

        // Page Overlap spinner: no weird boxed field. Blend into background.
        Spinner overlapSpinner = findViewById(R.id.spinner_overlap_lines);
        Spinner tapZoneSpinner = findViewById(R.id.spinner_tap_zone_mode);
        Spinner epubDirectionSpinner = findViewById(R.id.spinner_epub_page_direction);
        Spinner epubEffectSpinner = findViewById(R.id.spinner_epub_page_effect);
        Spinner[] spinners = new Spinner[]{overlapSpinner, tapZoneSpinner, epubDirectionSpinner, epubEffectSpinner};
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
        TextView epubLeft = findViewById(R.id.epub_left_spacing_label);
        if (epubLeft != null) epubLeft.setTextColor(sub);
        TextView epubRight = findViewById(R.id.epub_right_spacing_label);
        if (epubRight != null) epubRight.setTextColor(sub);
        TextView epubTop = findViewById(R.id.epub_top_spacing_label);
        if (epubTop != null) epubTop.setTextColor(sub);
        TextView epubBottom = findViewById(R.id.epub_bottom_spacing_label);
        if (epubBottom != null) epubBottom.setTextColor(sub);
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
        findViewById(R.id.btn_export).setOnClickListener(v -> exportLauncher.launch(makeBackupFileName()));
        findViewById(R.id.btn_import).setOnClickListener(v -> importLauncher.launch(new String[]{"application/json", "text/*"}));
    }

    private String makeBackupFileName() {
        String timestamp = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.US).format(new Date());
        return "textview_backup_" + timestamp + ".json";
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
                    showCustomThemeActionsDialog(theme);
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

    private void showCustomThemeActionsDialog(@NonNull Theme theme) {
        final android.app.Dialog dialog = createRoundedSettingsDialog();
        LinearLayout panel = createRoundedSettingsDialogPanel();

        int text = dialogTextColor();
        int sub = dialogSubTextColor();
        int outline = dialogOutlineColor();

        TextView title = makeSettingsDialogTitle(theme.getName(), text);
        panel.addView(title);

        TextView message = makeSettingsDialogMessage(getString(R.string.custom_theme_options_message), sub);
        panel.addView(message);

        MaterialButton edit = makeSettingsDialogButtonNoShade(getString(R.string.edit_theme), text, outline);
        edit.setOnClickListener(v -> {
            dialog.dismiss();
            Intent editIntent = new Intent(this, ThemeEditorActivity.class);
            editIntent.putExtra(ThemeEditorActivity.EXTRA_THEME_ID, theme.getId());
            startActivity(editIntent);
        });
        panel.addView(edit);

        MaterialButton delete = makeSettingsDialogButtonNoShade(getString(R.string.delete_theme), text, outline);
        delete.setOnClickListener(v -> {
            dialog.dismiss();
            showDeleteCustomThemeDialog(theme);
        });
        panel.addView(delete);

        MaterialButton cancel = makeSettingsDialogButtonNoShade(getString(R.string.cancel), text, outline);
        cancel.setOnClickListener(v -> dialog.dismiss());
        panel.addView(cancel);

        showRoundedSettingsDialog(dialog, panel, true);
    }

    private void showDeleteCustomThemeDialog(@NonNull Theme theme) {
        final android.app.Dialog dialog = createRoundedSettingsDialog();
        LinearLayout panel = createRoundedSettingsDialogPanel();

        int text = dialogTextColor();
        int sub = dialogSubTextColor();
        int outline = dialogOutlineColor();

        TextView title = makeSettingsDialogTitle(getString(R.string.delete_theme), text);
        panel.addView(title);

        TextView message = makeSettingsDialogMessage(
                getString(R.string.delete_theme_confirm, theme.getName()), sub);
        panel.addView(message);

        MaterialButton delete = makeSettingsDialogButtonNoShade(getString(R.string.delete), text, outline);
        delete.setOnClickListener(v -> {
            dialog.dismiss();
            themeManager.deleteCustomTheme(theme.getId());
            themeManager.reloadFromStorage();
            applySettingsReadableTheme();
            renderReadingThemeRows();
            Toast.makeText(this, getString(R.string.theme_deleted), Toast.LENGTH_SHORT).show();
        });
        panel.addView(delete);

        MaterialButton cancel = makeSettingsDialogButtonNoShade(getString(R.string.cancel), text, outline);
        cancel.setOnClickListener(v -> dialog.dismiss());
        panel.addView(cancel);

        showRoundedSettingsDialog(dialog, panel, true);
    }

    private android.app.Dialog createRoundedSettingsDialog() {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        return dialog;
    }

    private LinearLayout createRoundedSettingsDialogPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = dpToPx(18);
        panel.setPadding(pad, pad, pad, pad);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(dialogPanelBackgroundColor());
        bg.setCornerRadius(dpToPx(22));
        bg.setStroke(dpToPx(1), dialogOutlineColor());
        panel.setBackground(bg);

        return panel;
    }

    private void showRoundedSettingsDialog(android.app.Dialog dialog, LinearLayout panel) {
        showRoundedSettingsDialog(dialog, panel, false);
    }

    private void showRoundedSettingsDialog(android.app.Dialog dialog, LinearLayout panel, boolean compactWidth) {
        ScrollView adaptiveScroll = new ScrollView(this);
        adaptiveScroll.setFillViewport(false);
        adaptiveScroll.setClipChildren(true);
        adaptiveScroll.setClipToPadding(true);
        adaptiveScroll.setVerticalScrollBarEnabled(false);
        adaptiveScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        adaptiveScroll.addView(panel, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        dialog.setContentView(adaptiveScroll);
        prepareRoundedSettingsDialogWindow(dialog, true, compactWidth);
        dialog.setOnShowListener(d -> {
            prepareRoundedSettingsDialogWindow(dialog, false, compactWidth);
            applyAdaptiveSettingsDialogMaxHeight(adaptiveScroll);
            android.view.Window shownWindow = dialog.getWindow();
            if (shownWindow != null) {
                shownWindow.getDecorView().post(() -> shownWindow.getDecorView().setAlpha(1f));
            }
        });
        dialog.show();
        prepareRoundedSettingsDialogWindow(dialog, false, compactWidth);
    }

    private void applyAdaptiveSettingsDialogMaxHeight(@NonNull View adaptiveView) {
        // Pre-apply constrained-window sizing before the dialog is shown.
        // This prevents rounded settings/theme popups from resizing into place.
        int availableHeight = currentVisibleWindowHeightPx();
        if (availableHeight <= 0) return;
        if (!shouldApplyAdaptiveDialogMaxHeight(availableHeight)) return;

        int maxHeight = Math.max(dpToPx(220), Math.round(availableHeight * 0.88f) - dpToPx(24));
        adaptiveView.measure(
                View.MeasureSpec.makeMeasureSpec(Math.min(getResources().getDisplayMetrics().widthPixels - dpToPx(40), dpToPx(420)), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int measured = adaptiveView.getMeasuredHeight();
        if (measured > maxHeight) {
            ViewGroup.LayoutParams lp = adaptiveView.getLayoutParams();
            lp.height = maxHeight;
            adaptiveView.setLayoutParams(lp);
        }
    }

    private boolean shouldApplyAdaptiveDialogMaxHeight(int availableHeightPx) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N && isInMultiWindowMode()) {
            return true;
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && isInPictureInPictureMode()) {
            return true;
        }
        int fullHeightPx = getResources().getDisplayMetrics().heightPixels;
        return fullHeightPx > 0 && availableHeightPx < Math.round(fullHeightPx * 0.82f);
    }

    private int currentVisibleWindowHeightPx() {
        android.graphics.Rect rect = new android.graphics.Rect();
        View decor = getWindow() != null ? getWindow().getDecorView() : null;
        if (decor != null) {
            decor.getWindowVisibleDisplayFrame(rect);
            if (rect.height() > dpToPx(240)) return rect.height();
            if (decor.getHeight() > dpToPx(240)) return decor.getHeight();
        }
        return getResources().getDisplayMetrics().heightPixels;
    }

    private void prepareRoundedSettingsDialogWindow(android.app.Dialog dialog, boolean hideUntilLaidOut) {
        prepareRoundedSettingsDialogWindow(dialog, hideUntilLaidOut, false);
    }

    private void prepareRoundedSettingsDialogWindow(android.app.Dialog dialog, boolean hideUntilLaidOut, boolean compactWidth) {
        android.view.Window window = dialog.getWindow();
        if (window == null) return;

        window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        window.setGravity(android.view.Gravity.CENTER);
        window.setWindowAnimations(0);
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);

        android.view.WindowManager.LayoutParams lp = new android.view.WindowManager.LayoutParams();
        lp.copyFrom(window.getAttributes());
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        if (compactWidth) {
            // Short custom-theme action/delete popups should be visibly compact.
            // Use the actual screen percentage, not the normal 420dp settings-dialog cap.
            lp.width = Math.max(dpToPx(240), Math.round(screenWidth * 0.70f));
        } else {
            lp.width = Math.min(
                    screenWidth - dpToPx(40),
                    dpToPx(420));
        }
        lp.height = android.view.WindowManager.LayoutParams.WRAP_CONTENT;
        lp.dimAmount = 0.18f;
        lp.x = 0;
        lp.y = dpToPx(44);
        window.setAttributes(lp);
        window.setLayout(lp.width, android.view.WindowManager.LayoutParams.WRAP_CONTENT);

        if (hideUntilLaidOut) {
            window.getDecorView().setAlpha(0f);
        }
    }

    private TextView makeSettingsDialogTitle(String value, int color) {
        TextView title = new TextView(this);
        title.setText(value);
        title.setTextColor(color);
        title.setTextSize(18f);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(android.view.Gravity.CENTER);
        title.setSingleLine(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dpToPx(10));
        title.setLayoutParams(lp);
        return title;
    }

    private TextView makeSettingsDialogMessage(String value, int color) {
        TextView message = new TextView(this);
        message.setText(value);
        message.setTextColor(color);
        message.setTextSize(14f);
        message.setGravity(android.view.Gravity.CENTER);
        message.setLineSpacing(0, 1.08f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dpToPx(12));
        message.setLayoutParams(lp);
        return message;
    }

    private MaterialButton makeSettingsDialogButton(String label, int text, int bg, int outline) {
        return makeSettingsDialogButton(label, text, bg, outline, false);
    }

    private MaterialButton makeSettingsDialogButtonNoShade(String label, int text, int outline) {
        return makeSettingsDialogButton(label, text, dialogPanelBackgroundColor(), outline, true);
    }

    private MaterialButton makeSettingsDialogButton(String label, int text, int bg, int outline,
                                                    boolean noShade) {
        MaterialButton button = new MaterialButton(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(15f);
        button.setTextColor(text);
        button.setGravity(android.view.Gravity.CENTER);
        button.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        button.setIncludeFontPadding(false);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setMinHeight(dpToPx(44));
        button.setMinimumHeight(dpToPx(44));
        button.setCornerRadius(dpToPx(14));
        button.setStrokeWidth(dpToPx(1));
        button.setStrokeColor(ColorStateList.valueOf(outline));
        button.setBackgroundTintList(ColorStateList.valueOf(bg));
        button.setElevation(0f);
        button.setTranslationZ(0f);
        button.setStateListAnimator(null);
        button.setRippleColor(ColorStateList.valueOf(noShade ? Color.TRANSPARENT :
                (isDarkUi() ? Color.rgb(58, 58, 58) : Color.rgb(238, 238, 238))));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dpToPx(6), 0, 0);
        button.setLayoutParams(lp);
        return button;
    }

    private int dialogPanelBackgroundColor() {
        return isDarkUi() ? Color.rgb(16, 16, 16) : Color.rgb(255, 255, 255);
    }

    private int dialogRowBackgroundColor() {
        return isDarkUi() ? Color.rgb(24, 24, 24) : Color.rgb(248, 249, 250);
    }

    private int dialogTextColor() {
        return isDarkUi() ? Color.rgb(232, 234, 237) : Color.rgb(32, 33, 36);
    }

    private int dialogSubTextColor() {
        return isDarkUi() ? Color.rgb(176, 176, 176) : Color.rgb(95, 99, 104);
    }

    private int dialogOutlineColor() {
        return isDarkUi() ? Color.rgb(70, 70, 70) : Color.rgb(218, 220, 224);
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void exportBackupTo(Uri uri) {
        try {
            String json = bookmarkManager.exportAll();
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                if (os != null) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                    Toast.makeText(this, getString(R.string.exported_successfully), Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.export_failed_prefix) + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void importBackupFrom(Uri uri) {
        try {
            String json = FileUtils.readTextFromUri(this, uri);
            showImportBackupConfirmDialog(json);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.import_failed_prefix) + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showImportBackupConfirmDialog(@NonNull String json) {
        final android.app.Dialog dialog = createRoundedSettingsDialog();
        LinearLayout panel = createRoundedSettingsDialogPanel();

        int text = dialogTextColor();
        int sub = dialogSubTextColor();
        int outline = dialogOutlineColor();

        TextView title = makeSettingsDialogTitle(getString(R.string.import_backup), text);
        panel.addView(title);

        TextView message = makeSettingsDialogMessage(getString(R.string.merge_or_replace), sub);
        panel.addView(message);

        MaterialButton merge = makeSettingsDialogButtonNoShade(getString(R.string.merge), text, outline);
        merge.setOnClickListener(v -> {
            dialog.dismiss();
            bookmarkManager.importAll(json, true);
            finishImportBackup(getString(R.string.imported_merged));
        });
        panel.addView(merge);

        MaterialButton replace = makeSettingsDialogButtonNoShade(getString(R.string.replace), text, outline);
        replace.setOnClickListener(v -> {
            dialog.dismiss();
            bookmarkManager.importAll(json, false);
            finishImportBackup(getString(R.string.imported_replaced));
        });
        panel.addView(replace);

        MaterialButton cancel = makeSettingsDialogButtonNoShade(getString(R.string.cancel), text, outline);
        cancel.setOnClickListener(v -> dialog.dismiss());
        panel.addView(cancel);

        showRoundedSettingsDialog(dialog, panel, true);
    }


    private void finishImportBackup(String message) {
        if (prefs != null) {
            prefs.applyLanguage(prefs.getLanguageMode());
            prefs.applyDarkMode(prefs.getDarkMode());
        }
        if (themeManager != null) {
            themeManager.reloadFromStorage();
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        recreate();
    }

    @Override public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
