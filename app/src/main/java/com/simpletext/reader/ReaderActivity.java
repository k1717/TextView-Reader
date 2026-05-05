package com.simpletext.reader;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Button;
import android.widget.CompoundButton;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.simpletext.reader.adapter.BookmarkFolderAdapter;
import com.simpletext.reader.model.Bookmark;
import com.simpletext.reader.model.ReaderState;
import com.simpletext.reader.model.Theme;
import com.simpletext.reader.util.BookmarkManager;
import com.simpletext.reader.util.FileUtils;
import com.simpletext.reader.util.FontManager;
import com.simpletext.reader.util.PrefsManager;
import com.simpletext.reader.util.ReadingNotificationHelper;
import com.simpletext.reader.util.ThemeManager;
import com.simpletext.reader.view.CustomReaderView;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReaderActivity extends AppCompatActivity {

    private static final long VIEWER_DOUBLE_BACK_TIMEOUT_MS = 1000L;
    private static final long VIEWER_BACK_TOAST_DURATION_MS = 650L;

    public static final String EXTRA_FILE_PATH = "file_path";
    public static final String EXTRA_FILE_URI = "file_uri";
    public static final String EXTRA_JUMP_TO_POSITION = "jump_position";

    private View readerRoot;
    private CustomReaderView readerView;
    private ProgressBar progressBar;
    private TextView progressText;
    private View bottomBar;
    private View navBarSpacer;
    private View pageDragPanel;
    private SeekBar seekBar;
    private TextView positionLabel;
    private TextView readerPageStatus;
    private Toolbar toolbar;

    private String filePath;
    private String fileName;
    private String fileContent = "";
    private int totalChars;
    private int totalLines;

    private BookmarkManager bookmarkManager;
    private PrefsManager prefs;
    private ThemeManager themeManager;
    private ReadingNotificationHelper notificationHelper;

    private boolean toolbarVisible = false;
    private int currentReaderBackgroundColor = Color.BLACK;
    private boolean scrollUpdateScheduled = false;
    private boolean suppressSeekCallback = false;
    private long lastViewerBackPressedTime = 0L;
    private Toast viewerBackToast;
    private String activeSearchQuery = "";
    private int activeSearchIndex = -1;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = PrefsManager.getInstance(this);
        prefs.applyLanguage(prefs.getLanguageMode());
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.BLACK);
        // navigation bar follows reader theme background; set in applyReaderSystemBarColors()
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        // status bar icon color follows reader theme background; set in applyTheme()
        controller.setAppearanceLightNavigationBars(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
        }

        setContentView(R.layout.activity_reader);

        readerRoot = findViewById(R.id.reader_root);
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setVisibility(View.GONE);

        readerView = findViewById(R.id.reader_view);
        progressBar = findViewById(R.id.loading_progress);
        progressText = findViewById(R.id.loading_text);
        bottomBar = findViewById(R.id.bottom_bar);
        navBarSpacer = findViewById(R.id.nav_bar_spacer);
        pageDragPanel = findViewById(R.id.page_drag_panel);
        seekBar = findViewById(R.id.seek_bar);
        positionLabel = findViewById(R.id.position_label);
        readerPageStatus = findViewById(R.id.reader_page_status);
        updateLoadingIndicatorColors(currentReaderBackgroundColor);

        bookmarkManager = BookmarkManager.getInstance(this);
        themeManager = ThemeManager.getInstance(this);
        notificationHelper = new ReadingNotificationHelper(this);

        readerView.setReaderListener(new CustomReaderView.ReaderListener() {
            @Override public void onSingleTap(float x, float y) { handleSingleTap(y); }
            @Override public void onReaderScrollChanged() { onScrollChanged(); }
        });

        applyPreferences();
        applyTheme();
        applyReaderInsets();
        setupBottomControls();
        setupSeekBar();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleViewerBackPressed();
            }
        });

        if (prefs.getBrightnessOverride()) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.screenBrightness = prefs.getBrightnessValue();
            getWindow().setAttributes(lp);
        }

        loadFileFromIntent();
    }

    private void setupSeekBar() {
        seekBar.setMax(0);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private int pendingPage = 1;

            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (!fromUser || suppressSeekCallback || fileContent.isEmpty()) return;
                pendingPage = progress + 1;
                setPageLabels(pendingPage, getTotalPageCount());
            }

            @Override public void onStartTrackingTouch(SeekBar sb) {}

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                if (!fileContent.isEmpty()) {
                    scrollToPageNumber(pendingPage);
                    updatePositionLabel();
                }
            }
        });
    }

    private void applyPreferences() {
        float fontSize = prefs.getFontSize();
        float lineSpacing = prefs.getLineSpacing();
        int marginH = dpToPx(prefs.getMarginHorizontal());
        int marginV = dpToPx(prefs.getMarginVertical());

        Typeface tf = Typeface.DEFAULT;
        String fontName = prefs.getFontFamily();
        if (fontName != null && !fontName.equals("default")) {
            tf = FontManager.getInstance().getTypeface(fontName);
        }

        Theme theme = themeManager != null ? themeManager.getActiveTheme() : null;
        int textColor = theme != null ? theme.getTextColor() : 0xFFE0E0E0;
        int bgColor = theme != null ? theme.getBackgroundColor() : Color.BLACK;

        if (readerView != null) {
            readerView.setOverlapLines(prefs.getPagingOverlapLines());
            readerView.setReaderStyle(fontSize, lineSpacing, textColor, bgColor, marginH, marginV, tf);
        }

        // The page-count/status strip and Android status bar follow the active reader theme.
        applyReaderSystemBarColors(bgColor, readableTextColorForBackground(bgColor));

        if (prefs.getKeepScreenOn()) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        getWindow().getDecorView().setSystemUiVisibility(0);
        if (readerRoot != null) ViewCompat.requestApplyInsets(readerRoot);
    }


    private void applyReaderSystemBarColors(int backgroundColor, int textColor) {
        currentReaderBackgroundColor = backgroundColor;
        getWindow().setStatusBarColor(backgroundColor);

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());

        // Status-bar icon color follows the reader background.
        controller.setAppearanceLightStatusBars(isLightColor(backgroundColor));

        if (readerPageStatus != null) {
            readerPageStatus.setBackgroundColor(backgroundColor);
            readerPageStatus.setTextColor(textColor);
        }

        updateLoadingIndicatorColors(backgroundColor);
        updateBottomMenuBackground();
        updateNavigationBarForBottomMenu();
    }

    private boolean isLightColor(int color) {
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        double luminance = (0.299 * r + 0.587 * g + 0.114 * b);
        return luminance > 160;
    }

    private int readableTextColorForBackground(int backgroundColor) {
        return isLightColor(backgroundColor) ? Color.rgb(32, 32, 32) : Color.rgb(224, 224, 224);
    }


    private void updateLoadingIndicatorColors(int backgroundColor) {
        int fg = readableTextColorForBackground(backgroundColor);

        if (progressText != null) {
            progressText.setTextColor(fg);
        }

        if (progressBar != null) {
            progressBar.setIndeterminateTintList(ColorStateList.valueOf(fg));
        }
    }

    private int readerDialogBgColor() {
        // Opaque, but theme-blended.
        // This keeps the Close/OK/Delete area non-transparent while avoiding a harsh flat gray block.
        boolean light = isLightColor(currentReaderBackgroundColor);
        int overlay = light ? Color.WHITE : Color.BLACK;
        float mix = light ? 0.10f : 0.18f;
        int blended = blendColors(currentReaderBackgroundColor, overlay, mix);
        return Color.rgb(Color.red(blended), Color.green(blended), Color.blue(blended));
    }

    private int readerDialogPanelColor() {
        // Slightly separated from the dialog background, still opaque and theme-derived.
        boolean light = isLightColor(currentReaderBackgroundColor);
        int overlay = light ? Color.WHITE : Color.BLACK;
        float mix = light ? 0.18f : 0.28f;
        int blended = blendColors(currentReaderBackgroundColor, overlay, mix);
        return Color.rgb(Color.red(blended), Color.green(blended), Color.blue(blended));
    }

    private int readerDialogTextColor(int bgColor) {
        return readableTextColorForBackground(bgColor);
    }

    private int readerDialogSubTextColor(int bgColor) {
        return isLightColor(bgColor) ? Color.rgb(78, 78, 78) : Color.rgb(190, 190, 190);
    }

    private int strongDialogBorderColor(int bgColor) {
        int fg = readableTextColorForBackground(bgColor);

        // Border for the MOST OUTER function dialog only.
        // Mild enough for 더보기, but visible on black/dark and light/sepia themes.
        return blendColors(bgColor, fg, isLightColor(bgColor) ? 0.44f : 0.62f);
    }

    private GradientDrawable pageMoveOuterBackground(int bgColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(bgColor);
        drawable.setCornerRadius(dpToPx(4));
        drawable.setStroke(dpToPx(1), strongDialogBorderColor(bgColor));
        return drawable;
    }

    private TextView makeReaderDialogTitle(String text, int bgColor, int fgColor) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextColor(fgColor);
        title.setTextSize(20f);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(dpToPx(22), dpToPx(18), dpToPx(22), dpToPx(8));
        title.setBackgroundColor(bgColor);
        return title;
    }

    private GradientDrawable largeFunctionBoxBackground(int bgColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(bgColor);
        drawable.setCornerRadius(dpToPx(4));

        // Only the most outer 더보기 box gets this border.
        drawable.setStroke(dpToPx(1), strongDialogBorderColor(bgColor));
        return drawable;
    }

    private TextView makeReaderActionRow(String text, int fgColor) {
        TextView row = new TextView(this);
        row.setText(text);
        row.setTextColor(fgColor);
        row.setTextSize(16f);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dpToPx(18), 0, dpToPx(18), 0);

        // No separate boxes in 더보기. The row background is unified with the dialog/theme.
        row.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(46));
        lp.setMargins(0, 0, 0, 0);
        row.setLayoutParams(lp);
        return row;
    }

    private void applyOuterBorderToDialogPanel(AlertDialog dialog, int bgColor, int borderColor) {
        if (dialog == null || dialog.getWindow() == null) return;

        // Transparent window + rounded panel background + rounded overlay border.
        // This keeps normal dialogs visually consistent with 더보기:
        // one rounded outer box with a visible border.
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        View decor = dialog.getWindow().getDecorView();

        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(bgColor);
        panelBg.setCornerRadius(dpToPx(4));

        if (decor instanceof ViewGroup && ((ViewGroup) decor).getChildCount() > 0) {
            View panel = ((ViewGroup) decor).getChildAt(0);
            panel.setBackground(panelBg);
            panel.setPadding(dpToPx(1), dpToPx(1), dpToPx(1), dpToPx(1));

            if (panel instanceof ViewGroup) {
                ((ViewGroup) panel).setClipChildren(false);
                ((ViewGroup) panel).setClipToPadding(false);
            }
        } else {
            decor.setBackground(panelBg);
        }

        decor.post(() -> {
            GradientDrawable overlayBorder = new GradientDrawable();
            overlayBorder.setColor(Color.TRANSPARENT);
            overlayBorder.setCornerRadius(dpToPx(4));
            overlayBorder.setStroke(dpToPx(1), borderColor);
            overlayBorder.setBounds(0, 0, decor.getWidth(), decor.getHeight());

            decor.getOverlay().clear();
            decor.getOverlay().add(overlayBorder);
        });
    }

    private void styleReaderDialogWindow(AlertDialog dialog, int bgColor, int fgColor, int subColor) {
        if (dialog.getWindow() != null) {
            applyOuterBorderToDialogPanel(dialog, bgColor, strongDialogBorderColor(bgColor));

            android.view.WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
            lp.dimAmount = 0.16f;
            dialog.getWindow().setAttributes(lp);
            dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }

        forceDialogButtonPanelBackground(dialog, bgColor);
        styleReaderDialogButton(dialog.getButton(AlertDialog.BUTTON_POSITIVE), fgColor);
        styleReaderDialogButton(dialog.getButton(AlertDialog.BUTTON_NEGATIVE), subColor);
        styleReaderDialogButton(dialog.getButton(AlertDialog.BUTTON_NEUTRAL), subColor);
    }



    private int dialogActionPanelFillColor(int bgColor) {
        return blendColors(bgColor, readableTextColorForBackground(bgColor),
                isLightColor(bgColor) ? 0.025f : 0.040f);
    }

    private int dialogActionPanelLineColor(int bgColor) {
        return blendColors(bgColor, readableTextColorForBackground(bgColor), 0.34f);
    }

    private Drawable actionPanelBackground(int fillColor, int lineColor) {
        return new Drawable() {
            private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            {
                linePaint.setColor(lineColor);
                linePaint.setStyle(Paint.Style.FILL);
            }

            @Override
            public void draw(Canvas canvas) {
                canvas.drawColor(fillColor);
                float h = getResources().getDisplayMetrics().density;
                canvas.drawRect(0, 0, getBounds().width(), h, linePaint);
            }

            @Override public void setAlpha(int alpha) {}
            @Override public void setColorFilter(ColorFilter colorFilter) { linePaint.setColorFilter(colorFilter); }
            @Override public int getOpacity() { return PixelFormat.OPAQUE; }
        };
    }

    private void forceDialogButtonPanelBackground(AlertDialog dialog, int bgColor) {
        if (dialog == null) return;

        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        Button neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);

        Button first = positive != null ? positive : (negative != null ? negative : neutral);
        if (first == null) return;

        View panel = first.getParent() instanceof View ? (View) first.getParent() : null;
        if (panel == null) return;

        int lineColor = dialogActionPanelLineColor(bgColor);
        int panelFill = dialogActionPanelFillColor(bgColor);

        // Natural fit: no outer rectangle. Use a full-width opaque action area
        // with only a top divider line, like a clean native dialog separator.
        panel.setBackground(actionPanelBackground(panelFill, lineColor));
        panel.setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6));
        panel.setMinimumHeight(dpToPx(50));
        panel.setClipToOutline(false);

        if (panel instanceof ViewGroup) {
            ((ViewGroup) panel).setClipChildren(false);
            ((ViewGroup) panel).setClipToPadding(false);
        }
    }

    private void styleReaderDialogButton(Button button, int textColor) {
        if (button == null) return;

        // The border belongs to the whole button panel, not to the text widget.
        // Keep the button itself transparent so the full rectangular panel does not get clipped.
        button.setBackgroundTintList(null);
        button.setStateListAnimator(null);
        button.setTextColor(textColor);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setAllCaps(false);
        button.setMinWidth(dpToPx(72));
        button.setMinimumWidth(dpToPx(72));
        button.setMinHeight(dpToPx(40));
        button.setMinimumHeight(dpToPx(40));
        button.setPadding(dpToPx(14), 0, dpToPx(14), 0);

        ViewGroup.LayoutParams rawLp = button.getLayoutParams();
        if (rawLp instanceof LinearLayout.LayoutParams lp) {
            lp.setMargins(dpToPx(2), 0, dpToPx(2), 0);
            button.setLayoutParams(lp);
        }
    }

    private void styleReaderBorderedDialogWindow(AlertDialog dialog, int bgColor, int fgColor, int subColor) {
        int borderColor = blendColors(bgColor, fgColor, 0.48f);

        if (dialog.getWindow() != null) {
            applyOuterBorderToDialogPanel(dialog, bgColor, borderColor);

            android.view.WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
            lp.dimAmount = 0.14f;
            dialog.getWindow().setAttributes(lp);
            dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }

        forceDialogButtonPanelBackground(dialog, bgColor);
        int deleteColor = isLightColor(bgColor) ? Color.rgb(95, 35, 35) : Color.rgb(255, 170, 170);
        styleReaderDialogButton(dialog.getButton(AlertDialog.BUTTON_POSITIVE), deleteColor);
        styleReaderDialogButton(dialog.getButton(AlertDialog.BUTTON_NEGATIVE), subColor);
        styleReaderDialogButton(dialog.getButton(AlertDialog.BUTTON_NEUTRAL), subColor);
    }

    private void showBookmarkDeleteConfirm(Bookmark bookmark, Runnable afterDelete) {
        final int bg = readerDialogBgColor();
        final int fg = readerDialogTextColor(bg);
        final int sub = readerDialogSubTextColor(bg);
        final int border = blendColors(bg, fg, 0.48f);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(bg);
        box.setPadding(dpToPx(22), dpToPx(12), dpToPx(22), dpToPx(8));

        TextView message = new TextView(this);
        String body = bookmark.getFileName() + "\n\n" + bookmark.getDisplayText();
        message.setText(body);
        message.setTextColor(fg);
        message.setTextSize(14f);
        message.setLineSpacing(0f, 1.15f);
        message.setPadding(0, dpToPx(4), 0, dpToPx(8));
        box.addView(message, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView warning = new TextView(this);
        warning.setText(getString(R.string.delete_this_bookmark));
        warning.setTextColor(sub);
        warning.setTextSize(13f);
        warning.setPadding(0, dpToPx(4), 0, 0);
        box.addView(warning, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView title = makeReaderDialogTitle(getString(R.string.delete_bookmark), bg, fg);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setCustomTitle(title)
                .setView(box)
                .setPositiveButton(getString(R.string.delete), (d, w) -> {
                    bookmarkManager.deleteBookmark(bookmark.getId());
                    if (afterDelete != null) afterDelete.run();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .create();

        dialog.setOnShowListener(d -> styleReaderBorderedDialogWindow(dialog, bg, fg, sub));
        dialog.show();
    }

    private void styleSeekBarForReaderDialog(SeekBar seekBar, int bgColor, int accentColor) {
        if (seekBar == null) return;
        int track = isLightColor(bgColor) ? Color.rgb(185, 185, 185) : Color.rgb(78, 78, 78);
        seekBar.setThumbTintList(ColorStateList.valueOf(accentColor));
        seekBar.setProgressTintList(ColorStateList.valueOf(accentColor));
        seekBar.setProgressBackgroundTintList(ColorStateList.valueOf(track));
    }

    private void styleCompoundForReaderDialog(CompoundButton button, int bgColor, int fgColor) {
        if (button == null) return;
        button.setTextColor(fgColor);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int checked = isLightColor(bgColor) ? Color.rgb(72, 72, 72) : Color.rgb(210, 210, 210);
            int unchecked = isLightColor(bgColor) ? Color.rgb(170, 170, 170) : Color.rgb(110, 110, 110);
            button.setButtonTintList(new ColorStateList(
                    new int[][]{
                            new int[]{android.R.attr.state_checked},
                            new int[]{}
                    },
                    new int[]{checked, unchecked}
            ));
        }
    }

    private void styleReaderDialogEditBox(EditText input, int bgColor, int fgColor, int subColor) {
        if (input == null) return;

        input.setTextColor(fgColor);
        input.setHintTextColor(subColor);
        input.setSingleLine(true);

        // Give text a real left gap so "검색할 텍스트" / "정확한 페이지 번호" is not
        // stuck to the beginning of the box.
        input.setPadding(dpToPx(16), 0, dpToPx(16), 0);

        int fill = blendColors(bgColor, fgColor, isLightColor(bgColor) ? 0.025f : 0.035f);
        int stroke = blendColors(bgColor, fgColor, isLightColor(bgColor) ? 0.10f : 0.14f);

        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dpToPx(2));
        // Subtle inner boundary only. The strong contrast border belongs to the OUTER dialog box.
        drawable.setStroke(dpToPx(1), stroke);

        input.setBackgroundTintList(null);
        input.setBackground(drawable);
    }

    private EditText makeReaderDialogEditText(String hint, int bgColor, int fgColor, int subColor) {
        EditText input = new EditText(this);
        input.setHint(hint);
        styleReaderDialogEditBox(input, bgColor, fgColor, subColor);
        return input;
    }

    private TextView makeReaderDialogLabel(String text, int color, float sp) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(color);
        label.setTextSize(sp);
        label.setGravity(Gravity.CENTER_VERTICAL);
        return label;
    }

    private int blendColors(int bottomColor, int topColor, float topAlpha) {
        topAlpha = Math.max(0f, Math.min(1f, topAlpha));
        float bottomAlpha = 1f - topAlpha;

        int r = Math.round(Color.red(topColor) * topAlpha + Color.red(bottomColor) * bottomAlpha);
        int g = Math.round(Color.green(topColor) * topAlpha + Color.green(bottomColor) * bottomAlpha);
        int b = Math.round(Color.blue(topColor) * topAlpha + Color.blue(bottomColor) * bottomAlpha);

        return Color.rgb(r, g, b);
    }

    private GradientDrawable bottomMenuRoundedBackground(int color) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);

        float r = dpToPx(12);
        bg.setCornerRadii(new float[]{
                r, r,   // top-left
                r, r,   // top-right
                0, 0,   // bottom-right
                0, 0    // bottom-left
        });

        return bg;
    }

    private int bottomMenuBlendColor() {
        // Theme-dependent middle-menu color.
        boolean light = isLightColor(currentReaderBackgroundColor);

        if (light) {
            return blendColors(currentReaderBackgroundColor, Color.BLACK, 0.58f);
        } else {
            return blendColors(currentReaderBackgroundColor, Color.WHITE, 0.12f);
        }
    }

    private boolean isBottomMenuOpen() {
        return toolbarVisible && bottomBar != null && bottomBar.getVisibility() == View.VISIBLE;
    }

    private int currentNavigationAreaColor() {
        return isBottomMenuOpen() ? bottomMenuBlendColor() : currentReaderBackgroundColor;
    }

    private void updateBottomMenuBackground() {
        if (bottomBar != null) {
            bottomBar.setBackground(bottomMenuRoundedBackground(bottomMenuBlendColor()));
        }
    }

    private void updateNavigationBarForBottomMenu() {
        int navColor = currentNavigationAreaColor();

        // System nav color alone is not enough on modern edge-to-edge Android,
        // but still set it for devices that honor it.
        getWindow().setNavigationBarColor(navColor);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
        }

        // Real view behind Android 3-button area. This is what makes the color
        // visibly toggle when the middle-tap menu opens/closes.
        if (navBarSpacer != null) {
            navBarSpacer.setBackgroundColor(navColor);
            navBarSpacer.setVisibility(View.VISIBLE);
            navBarSpacer.bringToFront();
        }

        // Keep the bottom menu above the spacer when open.
        if (bottomBar != null && isBottomMenuOpen()) {
            bottomBar.bringToFront();
        }

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightNavigationBars(isLightColor(navColor));
    }

    private void applyTheme() {
        Theme theme = themeManager.getActiveTheme();
        if (readerView != null && theme != null) {
            readerView.setBackgroundColor(theme.getBackgroundColor());
        }
        // navigation bar follows reader theme background; set in applyReaderSystemBarColors()
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightNavigationBars(false);
        applyPreferences();
    }

    private void applyReaderInsets() {
        if (readerRoot == null) return;
        ViewCompat.setOnApplyWindowInsetsListener(readerRoot, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int topInset = bars.top;
            int bottomInset = bars.bottom;

            if (navBarSpacer != null) {
                FrameLayout.LayoutParams spacerLp =
                        (FrameLayout.LayoutParams) navBarSpacer.getLayoutParams();
                spacerLp.height = bottomInset;
                spacerLp.gravity = Gravity.BOTTOM;
                navBarSpacer.setLayoutParams(spacerLp);
            }

            int pageStatusHeight = topInset + dpToPx(24);
            if (readerPageStatus != null) {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) readerPageStatus.getLayoutParams();
                lp.topMargin = 0;
                lp.height = pageStatusHeight;
                readerPageStatus.setLayoutParams(lp);
                readerPageStatus.setPadding(0, topInset, 0, dpToPx(4));
            }

            readerView.setPadding(
                    readerView.getPaddingLeft(),
                    pageStatusHeight + dpToPx(8),
                    readerView.getPaddingRight(),
                    bottomInset + dpToPx(12));

            bottomBar.setPadding(
                    dpToPx(20),
                    dpToPx(6),
                    dpToPx(20),
                    dpToPx(6));

            FrameLayout.LayoutParams bottomLp =
                    (FrameLayout.LayoutParams) bottomBar.getLayoutParams();
            bottomLp.bottomMargin = bottomInset;
            bottomBar.setLayoutParams(bottomLp);

            updateBottomMenuBackground();
            updateNavigationBarForBottomMenu();

            readerView.post(this::updatePositionLabel);
            return insets;
        });
        ViewCompat.requestApplyInsets(readerRoot);
    }

    private void openFileBrowserFromViewer() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_RETURN_TO_VIEWER, true);
        startActivity(intent);
    }

    private void setupBottomControls() {
        findViewById(R.id.btn_open_file).setOnClickListener(v -> showTextSearch());
        findViewById(R.id.btn_page_move).setOnClickListener(v -> showPageMoveBubble());
        findViewById(R.id.btn_bookmark).setOnClickListener(v -> showBookmarksForFile());
        findViewById(R.id.btn_settings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.btn_more).setOnClickListener(v -> showMoreDialog());
    }

    private void showPageMoveBubble() {
        if (fileContent == null || fileContent.isEmpty()) {
            Toast.makeText(this, getString(R.string.file_not_loaded), Toast.LENGTH_SHORT).show();
            return;
        }

        final int bubbleBg = readerDialogBgColor();
        final int bubbleFg = readerDialogTextColor(bubbleBg);
        final int bubbleSub = readerDialogSubTextColor(bubbleBg);
        int totalPages = getTotalPageCount();
        int currentPage = getCurrentPageNumber();

        TextView title = makeReaderDialogTitle(getString(R.string.page_move), bubbleBg, bubbleFg);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dpToPx(20);
        box.setPadding(pad, dpToPx(12), pad, dpToPx(12));
        // No inner/subsection border here.
        // Only the most outer dialog window gets the contrast border.
        box.setBackgroundColor(bubbleBg);

        TextView label = new TextView(this);
        label.setGravity(Gravity.CENTER);
        label.setTextSize(17f);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setTextColor(bubbleFg);
        label.setText(formatPageMoveLabel(currentPage, totalPages));
        box.addView(label, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        SeekBar dialogSeek = new SeekBar(this);
        dialogSeek.setMax(Math.max(0, totalPages - 1));
        dialogSeek.setProgress(Math.max(0, currentPage - 1));
        // Visible for every reader theme:
        // background track = medium contrast
        // progress/thumbnail = high contrast
        int trackColor = blendColors(bubbleBg, bubbleFg, 0.52f);
        int progressColor = blendColors(bubbleBg, bubbleFg, 0.82f);
        int thumbColor = blendColors(bubbleBg, bubbleFg, 0.94f);

        dialogSeek.setThumbTintList(ColorStateList.valueOf(thumbColor));
        dialogSeek.setProgressTintList(ColorStateList.valueOf(progressColor));
        dialogSeek.setProgressBackgroundTintList(ColorStateList.valueOf(trackColor));
        dialogSeek.setPadding(dpToPx(18), 0, dpToPx(18), 0);
        box.addView(dialogSeek, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(42)));

        TextView pageHint = new TextView(this);
        pageHint.setText(getString(R.string.exact_page_number));
        pageHint.setTextSize(13f);
        pageHint.setTextColor(bubbleSub);
        // Keep the label close to the slider, but leave enough room above the input box.
        pageHint.setPadding(0, dpToPx(3), 0, 0);
        box.addView(pageHint, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        EditText pageInput = makeReaderDialogEditText("1 - " + totalPages, bubbleBg, bubbleFg, bubbleSub);
        pageInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        pageInput.setText(String.valueOf(currentPage));
        pageInput.setSelectAllOnFocus(true);
        LinearLayout.LayoutParams pageInputLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(52));
        pageInputLp.setMargins(0, dpToPx(8), 0, 0);
        box.addView(pageInput, pageInputLp);

        final int[] pendingPage = new int[]{currentPage};
        dialogSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (!fromUser) return;
                int pages = getTotalPageCount();
                int page = Math.max(1, Math.min(pages, progress + 1));
                pendingPage[0] = page;
                label.setText(formatPageMoveLabel(page, pages));
                pageInput.setText(String.valueOf(page));
                pageInput.setSelection(pageInput.getText().length());
            }

            @Override public void onStartTrackingTouch(SeekBar sb) {}

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                scrollToPageNumber(pendingPage[0]);
                updatePositionLabel();
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setCustomTitle(title)
                .setView(box)
                .setPositiveButton(getString(R.string.go), null)
                .setNegativeButton(getString(R.string.close), null)
                .create();

        dialog.setOnShowListener(d -> {
            styleReaderDialogWindow(dialog, bubbleBg, bubbleFg, bubbleSub);
            if (dialog.getWindow() != null) {
                GradientDrawable outer = pageMoveOuterBackground(bubbleBg);
                dialog.getWindow().setBackgroundDrawable(outer);
                dialog.getWindow().getDecorView().setBackground(outer);
            }

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String raw = pageInput.getText().toString().trim();
                if (raw.isEmpty()) {
                    Toast.makeText(this, getString(R.string.enter_page_number), Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    int page = Integer.parseInt(raw);
                    if (page < 1 || page > getTotalPageCount()) {
                        Toast.makeText(this,
                                getString(R.string.page_range_error, getTotalPageCount()),
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    scrollToPageNumber(page);
                    updatePositionLabel();
                    dialog.dismiss();
                } catch (NumberFormatException ex) {
                    Toast.makeText(this, getString(R.string.invalid_page_number), Toast.LENGTH_SHORT).show();
                }
            });
        });

        dialog.show();
    }

    private String formatPageMoveLabel(int page, int totalPages) {
        return String.format(Locale.getDefault(), "Page %d / %d", page, Math.max(1, totalPages));
    }

    private void showMoreDialog() {
        final int bg = readerDialogBgColor();
        final int panel = readerDialogPanelColor();
        final int fg = readerDialogTextColor(bg);

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackground(largeFunctionBoxBackground(bg));
        outer.setPadding(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2));

        TextView title = makeReaderDialogTitle(getString(R.string.more), bg, fg);
        outer.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setBackgroundColor(bg);
        int pad = dpToPx(14);
        list.setPadding(pad, dpToPx(10), pad, dpToPx(10));
        list.setBackgroundColor(bg);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(bg);
        scroll.addView(list);

        final AlertDialog[] ref = new AlertDialog[1];

        // Removed from 더보기:
        // - 책갈피 추가: already has a bottom 책갈피 button
        // - 위치/줄 이동: already has a bottom 페이지 이동 button
        // - 검색: bottom-left button is now 찾기
        addMoreActionRow(list, getString(R.string.brightness), fg, panel, this::showBrightnessDialog, ref);
        addMoreActionRow(list, getString(R.string.reading_theme), fg, panel, this::showThemeDialog, ref);
        addMoreActionRow(list, getString(R.string.font), fg, panel, this::showFontDialog, ref);
        addMoreActionRow(list, getString(R.string.increase_font), fg, panel, () -> changeFontSize(2f), ref);
        addMoreActionRow(list, getString(R.string.decrease_font), fg, panel, () -> changeFontSize(-2f), ref);

        addMoreActionRow(list, getString(R.string.file_info), fg, panel, this::showFileInfoDialog, ref);

        outer.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f));

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        // Match the normal AlertDialog bottom action area used by 페이지 이동 / 책갈피:
        // subtle theme-blended fill + top divider line.
        actionRow.setBackground(actionPanelBackground(
                dialogActionPanelFillColor(bg),
                dialogActionPanelLineColor(bg)));
        actionRow.setPadding(dpToPx(30), 0, dpToPx(30), 0);

        TextView openFile = new TextView(this);
        openFile.setText(getString(R.string.action_open_file));
        openFile.setTextColor(fg);
        openFile.setTextSize(16f);
        openFile.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        openFile.setBackgroundColor(Color.TRANSPARENT);

        TextView close = new TextView(this);
        close.setText(getString(R.string.close));
        close.setTextColor(fg);
        close.setTextSize(16f);
        close.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        close.setBackgroundColor(Color.TRANSPARENT);

        actionRow.addView(openFile, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f));
        actionRow.addView(close, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f));

        outer.addView(actionRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(54)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(outer)
                .create();
        ref[0] = dialog;

        openFile.setOnClickListener(v -> {
            dialog.dismiss();
            openFileBrowserFromViewer();
        });
        close.setOnClickListener(v -> dialog.dismiss());

        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                // The custom root carries the visible border. Make the native window transparent
                // so the root border is not hidden by Android's default AlertDialog frame.
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

                android.view.WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
                lp.dimAmount = 0.16f;
                dialog.getWindow().setAttributes(lp);
                dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            }
        });

        dialog.show();
    }

    private void addMoreActionRow(
            LinearLayout list,
            String label,
            int fg,
            int panel,
            Runnable action,
            AlertDialog[] dialogRef
    ) {
        TextView row = makeReaderActionRow(label, fg);
        row.setOnClickListener(v -> {
            if (dialogRef[0] != null) dialogRef[0].dismiss();
            action.run();
        });
        list.addView(row);
    }

    private void loadFileFromIntent() {
        progressBar.setVisibility(View.VISIBLE);
        progressText.setVisibility(View.VISIBLE);
        progressText.setText(getString(R.string.loading));

        String path = getIntent().getStringExtra(EXTRA_FILE_PATH);
        String uriStr = getIntent().getStringExtra(EXTRA_FILE_URI);

        executor.execute(() -> {
            try {
                File fileToRead = null;
                if (path != null) {
                    File file = new File(path);
                    filePath = file.getAbsolutePath();
                    fileName = file.getName();
                    fileToRead = file;
                } else if (uriStr != null) {
                    Uri uri = Uri.parse(uriStr);
                    fileName = FileUtils.getFileNameFromUri(this, uri);
                    File localFile = FileUtils.copyUriToLocal(this, uri,
                            fileName != null ? fileName : "opened_file.txt");
                    filePath = localFile.getAbsolutePath();
                    fileToRead = localFile;
                }
                if (fileToRead == null) throw new IllegalArgumentException("No file selected");

                String content = FileUtils.readTextFile(fileToRead);
                int lineCount = countLines(content);
                handler.post(() -> onFileLoaded(content, lineCount));
            } catch (Exception e) {
                handler.post(() -> {
                    progressText.setText(String.format(Locale.getDefault(), "%s%s", getString(R.string.error_prefix), e.getMessage()));
                    Toast.makeText(this, getString(R.string.error_prefix) + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void onFileLoaded(String content, int lineCount) {
        progressBar.setVisibility(View.GONE);
        progressText.setVisibility(View.GONE);
        if (getSupportActionBar() != null) getSupportActionBar().setTitle(fileName);

        fileContent = content != null ? content : "";
        totalChars = fileContent.length();
        totalLines = lineCount;
        readerView.setTextContent(fileContent);

        readerView.post(() -> {
            int jumpPos = getIntent().getIntExtra(EXTRA_JUMP_TO_POSITION, -1);
            if (jumpPos >= 0) {
                scrollToCharPosition(jumpPos);
            } else if (prefs.getAutoSavePosition()) {
                ReaderState state = bookmarkManager.getReadingState(filePath);
                if (state != null) scrollToCharPosition(state.getCharPosition());
            }
            updatePositionLabel();
        });
    }

    // --- Scroll & position ---

    private void onScrollChanged() { schedulePositionUpdate(); }

    private void schedulePositionUpdate() {
        if (scrollUpdateScheduled) return;
        scrollUpdateScheduled = true;
        handler.postDelayed(() -> {
            scrollUpdateScheduled = false;
            updatePositionLabel();
            if (prefs.getShowNotification() && filePath != null) {
                notificationHelper.showReading(fileName, getProgressPercent(), filePath);
            }
        }, 80);
    }

    private int getProgressPercent() {
        int total = Math.max(1, getTotalPageCount());
        if (total <= 1) return 0;
        return Math.max(0, Math.min(100, Math.round(100f * (getCurrentPageNumber() - 1) / (total - 1))));
    }

    private int getTotalPageCount() { return readerView != null ? readerView.getTotalPageCount() : 1; }
    private int getCurrentPageNumber() { return readerView != null ? readerView.getCurrentPageNumber() : 1; }
    private void scrollToPageNumber(int page) { if (readerView != null) readerView.scrollToPage(page); }

    private void updatePositionLabel() {
        if (readerView == null) return;
        int totalPages = getTotalPageCount();
        int currentPage = getCurrentPageNumber();
        setPageLabels(currentPage, totalPages);

        suppressSeekCallback = true;
        seekBar.setMax(Math.max(0, totalPages - 1));
        seekBar.setProgress(Math.max(0, Math.min(totalPages - 1, currentPage - 1)));
        suppressSeekCallback = false;
    }

    private void setPageLabels(int currentPage, int totalPages) {
        totalPages = Math.max(1, totalPages);
        currentPage = Math.max(1, Math.min(totalPages, currentPage));
        String text = String.format(Locale.getDefault(), "%d / %d", currentPage, totalPages);
        if (positionLabel != null) positionLabel.setText(text);
        if (readerPageStatus != null) readerPageStatus.setText(text);
    }

    private void scrollToPercent(float percent) { if (readerView != null) readerView.scrollToPercent(percent); }
    private void scrollToCharPosition(int charPosition) {
        if (readerView != null) {
            readerView.scrollToCharPosition(charPosition);
            readerView.post(this::updatePositionLabel);
        }
    }

    private void handleSingleTap(float y) {
        if (fileContent.isEmpty() || !prefs.getTapPagingEnabled()) {
            toggleToolbar();
            return;
        }

        int height = readerView.getHeight();

        // Tap-zone ratio:
        // top 35%    -> previous page
        // middle 30% -> show/hide bottom menu
        // bottom 35% -> next page
        if (y < height * 0.35f) {
            pageUp();
        } else if (y > height * 0.65f) {
            pageDown();
        } else {
            toggleToolbar();
        }
    }

    private void pageDown() { pageBy(+1); }
    private void pageUp() { pageBy(-1); }
    private void pageBy(int direction) {
        if (readerView != null) {
            readerView.pageBy(direction);
            readerView.post(this::updatePositionLabel);
        }
    }

    private int getCurrentCharPosition() { return readerView != null ? readerView.getCurrentCharPosition() : 0; }

    private int getCurrentLineNumber() {
        int charPos = getCurrentCharPosition();
        return Math.max(1, countLinesUntilChar(charPos));
    }

    private String getExcerpt(int charPosition) {
        if (fileContent == null || fileContent.isEmpty()) return "";
        int start = Math.max(0, Math.min(fileContent.length(), charPosition));
        int end = Math.min(fileContent.length(), start + 90);
        return fileContent.substring(start, end).trim().replaceAll("[\\r\\n]+", " ");
    }

    private int countLines(String s) {
        if (s == null || s.isEmpty()) return 1;
        int lines = 1;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == '\n') lines++;
        return lines;
    }

    private int countLinesUntilChar(int charPosition) {
        if (fileContent == null || fileContent.isEmpty()) return 1;
        int end = Math.max(0, Math.min(fileContent.length(), charPosition));
        int lines = 1;
        for (int i = 0; i < end; i++) if (fileContent.charAt(i) == '\n') lines++;
        return lines;
    }

    private int findText(String query, int startPosition) {
        if (query == null || query.isEmpty() || fileContent == null) return -1;
        int start = Math.max(0, Math.min(fileContent.length(), startPosition));
        int idx = fileContent.indexOf(query, start);
        if (idx >= 0) return idx;
        return fileContent.indexOf(query);
    }

    private int findTextBackward(String query, int startPosition) {
        if (query == null || query.isEmpty() || fileContent == null) return -1;
        if (fileContent.isEmpty()) return -1;

        int start = Math.max(0, Math.min(fileContent.length() - 1, startPosition));
        int idx = fileContent.lastIndexOf(query, start);
        if (idx >= 0) return idx;
        return fileContent.lastIndexOf(query);
    }

    private int countTextMatches(String query) {
        if (query == null || query.isEmpty() || fileContent == null || fileContent.isEmpty()) return 0;

        int count = 0;
        int idx = 0;
        int step = Math.max(1, query.length());

        while (idx >= 0 && idx < fileContent.length()) {
            idx = fileContent.indexOf(query, idx);
            if (idx < 0) break;
            count++;
            idx += step;
        }

        return count;
    }

    private int matchIndexForPosition(String query, int position) {
        if (query == null || query.isEmpty() || fileContent == null || fileContent.isEmpty()) return 0;

        int count = 0;
        int idx = 0;
        int step = Math.max(1, query.length());

        while (idx >= 0 && idx < fileContent.length()) {
            idx = fileContent.indexOf(query, idx);
            if (idx < 0) break;
            count++;
            if (idx >= position) return count;
            idx += step;
        }

        return count;
    }

    private int findCharForLine(int targetLine) {
        if (targetLine <= 1 || fileContent == null) return 0;
        int line = 1;
        for (int i = 0; i < fileContent.length(); i++) {
            if (fileContent.charAt(i) == '\n') {
                line++;
                if (line >= targetLine) return i + 1;
            }
        }
        return totalChars;
    }

    // --- Toolbar toggle ---

    private void toggleToolbar() {
        toolbarVisible = !toolbarVisible;
        toolbar.setVisibility(View.GONE);
        bottomBar.setVisibility(toolbarVisible ? View.VISIBLE : View.GONE);
        if (readerPageStatus != null) readerPageStatus.setVisibility(View.VISIBLE);
        updateBottomMenuBackground();
        updateNavigationBarForBottomMenu();
        handler.postDelayed(this::updateNavigationBarForBottomMenu, 60);
    }

    // --- Volume key scrolling ---

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (prefs.getVolumeKeyScroll()) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) { pageDown(); return true; }
            else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) { pageUp(); return true; }
        }
        return super.onKeyDown(keyCode, event);
    }

    // --- Bookmark operations ---
    //
    // Tek View-style behavior:
    // - The 북마크 button is one bookmark manager, not separate save/load modes.
    // - It always shows "save current position" and the existing bookmark list together.
    // - A saved bookmark uses the nearby text excerpt as the main display text.
    // - Tapping an existing bookmark jumps there.
    // - Long press edits the optional memo/label.
    // - Delete button removes it.

    private void addBookmark() {
        saveCurrentBookmarkTekStyle(null);
    }

    private void saveCurrentBookmarkTekStyle(Runnable afterSave) {
        if (filePath == null || fileContent == null || fileContent.isEmpty()) {
            Toast.makeText(this, getString(R.string.file_not_loaded), Toast.LENGTH_SHORT).show();
            return;
        }

        int charPos = getCurrentCharPosition();
        int lineNum = getCurrentLineNumber();
        String excerpt = getExcerpt(charPos);
        int bookmarkEndPosition = Math.min(totalChars, charPos + Math.max(1, excerpt.length()));

        // Avoid stacking multiple identical bookmarks when the user taps save repeatedly
        // at almost the same location. Original Tek View-style bookmarks are position-based.
        List<Bookmark> existing = bookmarkManager.getBookmarksForFile(filePath);
        for (Bookmark b : existing) {
            if (Math.abs(b.getCharPosition() - charPos) <= 3) {
                b.setLineNumber(lineNum);
                b.setExcerpt(excerpt);
                b.setEndPosition(bookmarkEndPosition);
                bookmarkManager.updateBookmark(b);
                Toast.makeText(this, getString(R.string.bookmark_updated), Toast.LENGTH_SHORT).show();
                if (afterSave != null) afterSave.run();
                return;
            }
        }

        Bookmark bookmark = new Bookmark(filePath, fileName, charPos, lineNum, excerpt);
        bookmark.setEndPosition(bookmarkEndPosition);
        // No label prompt here. Original behavior is excerpt/position based.
        // Optional memo editing remains available by long-pressing a bookmark.
        bookmarkManager.addBookmark(bookmark);
        Toast.makeText(this, getString(R.string.bookmark_saved), Toast.LENGTH_SHORT).show();

        if (afterSave != null) afterSave.run();
    }

    private void showBookmarksForFile() {
        if (filePath == null || fileContent == null) {
            Toast.makeText(this, getString(R.string.file_not_loaded), Toast.LENGTH_SHORT).show();
            return;
        }

        final int bg = readerDialogBgColor();
        final int panel = readerDialogPanelColor();
        final int fg = readerDialogTextColor(bg);
        final int sub = readerDialogSubTextColor(bg);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(bg);
        int pad = dpToPx(12);
        // Keep content slightly inset inside the rounded outer frame.
        box.setPadding(pad, pad, pad, dpToPx(6));

        TextView currentInfo = new TextView(this);
        currentInfo.setTextColor(sub);
        currentInfo.setTextSize(12f);
        currentInfo.setPadding(0, 0, 0, dpToPx(8));
        box.addView(currentInfo, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView saveButton = new TextView(this);
        saveButton.setText(getString(R.string.add_current_bookmark));
        saveButton.setGravity(Gravity.CENTER);
        saveButton.setTextColor(fg);
        saveButton.setTextSize(16f);
        saveButton.setPadding(0, dpToPx(12), 0, dpToPx(12));
        saveButton.setBackgroundColor(Color.TRANSPARENT);
        box.addView(saveButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView hint = new TextView(this);
        hint.setText(getString(R.string.bookmark_folder_hint));
        hint.setTextColor(sub);
        hint.setTextSize(12f);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dpToPx(8), 0, dpToPx(6));
        box.addView(hint, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView emptyText = new TextView(this);
        emptyText.setText(getString(R.string.no_bookmarks_hint));
        emptyText.setTextColor(sub);
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setTextSize(14f);
        emptyText.setPadding(0, dpToPx(18), 0, dpToPx(18));

        RecyclerView rv = new RecyclerView(this);
        rv.setBackgroundColor(bg);
        rv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        rv.setLayoutManager(new LinearLayoutManager(this));

        BookmarkFolderAdapter adapter = new BookmarkFolderAdapter();
        adapter.setThemeColors(bg, fg, sub, panel);
        Set<String> expandedFolders = new HashSet<>();
        expandedFolders.add(filePath); // current file starts expanded

        rv.setAdapter(adapter);

        box.addView(emptyText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        box.addView(rv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(430)));

        Runnable refresh = () -> {
            List<Bookmark> allBookmarks = bookmarkManager.getAllBookmarks();
            adapter.setBookmarks(allBookmarks, expandedFolders, filePath);
            emptyText.setVisibility(allBookmarks.isEmpty() ? View.VISIBLE : View.GONE);
            currentInfo.setText(getString(R.string.all_bookmarks_status,
                    adapter.getFolderCount(),
                    allBookmarks.size(),
                    getCurrentPageNumber(),
                    getTotalPageCount()));
        };

        TextView title = makeReaderDialogTitle(getString(R.string.bookmark), bg, fg);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setCustomTitle(title)
                .setView(box)
                .setNegativeButton(getString(R.string.close), null)
                .create();

        saveButton.setOnClickListener(v -> saveCurrentBookmarkTekStyle(() -> {
            expandedFolders.add(filePath);
            refresh.run();
        }));

        adapter.setListener(new BookmarkFolderAdapter.Listener() {
            @Override
            public void onFolderClick(String folderFilePath) {
                if (expandedFolders.contains(folderFilePath)) {
                    expandedFolders.remove(folderFilePath);
                } else {
                    expandedFolders.add(folderFilePath);
                }
                refresh.run();
            }

            @Override
            public void onBookmarkClick(Bookmark b) {
                if (b.getFilePath() != null && b.getFilePath().equals(filePath)) {
                    scrollToCharPosition(b.getCharPosition());
                    dialog.dismiss();
                    return;
                }

                File targetFile = new File(b.getFilePath());
                if (!targetFile.exists()) {
                    Toast.makeText(ReaderActivity.this,
                            getString(R.string.file_not_found_prefix) + b.getFilePath(),
                            Toast.LENGTH_LONG).show();
                    return;
                }

                Intent intent = new Intent(ReaderActivity.this, ReaderActivity.class);
                intent.putExtra(EXTRA_FILE_PATH, b.getFilePath());
                intent.putExtra(EXTRA_JUMP_TO_POSITION, b.getCharPosition());
                startActivity(intent);
                dialog.dismiss();
            }

            @Override
            public void onBookmarkDelete(Bookmark b) {
                showBookmarkDeleteConfirm(b, refresh);
            }

            @Override
            public void onBookmarkEdit(Bookmark b) {
                EditText input = new EditText(ReaderActivity.this);
                input.setHint(getString(R.string.optional_memo));
                input.setText(b.getLabel());
                input.setSelectAllOnFocus(true);

                new AlertDialog.Builder(ReaderActivity.this)
                        .setTitle(getString(R.string.edit_bookmark_memo))
                        .setMessage(b.getFileName() + "\n\n" + b.getExcerpt())
                        .setView(input)
                        .setPositiveButton(getString(R.string.save), (d, w) -> {
                            b.setLabel(input.getText().toString().trim());
                            bookmarkManager.updateBookmark(b);
                            refresh.run();
                        })
                        .setNeutralButton(getString(R.string.clear), (d, w) -> {
                            b.setLabel("");
                            bookmarkManager.updateBookmark(b);
                            refresh.run();
                        })
                        .setNegativeButton(getString(R.string.cancel), null)
                        .show();
            }
        });

        dialog.setOnShowListener(d -> {
            styleReaderDialogWindow(dialog, bg, fg, sub);
            refresh.run();
        });

        dialog.show();
    }

    // --- Brightness ---


    private void showBrightnessDialog() {
        final int bg = readerDialogBgColor();
        final int fg = readerDialogTextColor(bg);
        final int sub = readerDialogSubTextColor(bg);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(bg);
        box.setPadding(dpToPx(24), dpToPx(12), dpToPx(24), dpToPx(10));

        TextView label = makeReaderDialogLabel(getString(R.string.screen_brightness), fg, 14f);
        label.setPadding(0, 0, 0, dpToPx(8));
        box.addView(label, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        SeekBar seekbar = new SeekBar(this);
        seekbar.setMax(100);
        seekbar.setProgress((int) (prefs.getBrightnessValue() * 100));
        styleSeekBarForReaderDialog(seekbar, bg, sub);
        box.addView(seekbar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(44)));

        SwitchCompat switchOverride = new SwitchCompat(this);
        switchOverride.setText(getString(R.string.override_system_brightness));
        switchOverride.setTextSize(14f);
        switchOverride.setChecked(prefs.getBrightnessOverride());
        styleCompoundForReaderDialog(switchOverride, bg, fg);
        box.addView(switchOverride, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(48)));

        seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) {
                    float brightness = progress / 100f;
                    prefs.setBrightnessValue(brightness);
                    WindowManager.LayoutParams lp = getWindow().getAttributes();
                    lp.screenBrightness = brightness;
                    getWindow().setAttributes(lp);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        switchOverride.setOnCheckedChangeListener((v, checked) -> {
            prefs.setBrightnessOverride(checked);
            if (!checked) {
                WindowManager.LayoutParams lp = getWindow().getAttributes();
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
                getWindow().setAttributes(lp);
            }
        });

        TextView title = makeReaderDialogTitle(getString(R.string.brightness), bg, fg);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setCustomTitle(title)
                .setView(box)
                .setPositiveButton(getString(R.string.ok), null)
                .create();

        dialog.setOnShowListener(d -> styleReaderDialogWindow(dialog, bg, fg, sub));
        dialog.show();
    }

    // --- Theme selection ---

    private void showThemeDialog() {
        final int bg = readerDialogBgColor();
        final int fg = readerDialogTextColor(bg);
        final int sub = readerDialogSubTextColor(bg);

        List<Theme> themes = themeManager.getAllThemes();

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setBackgroundColor(bg);
        int pad = dpToPx(14);
        list.setPadding(pad, dpToPx(8), pad, dpToPx(8));

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(bg);
        scroll.addView(list);

        final AlertDialog[] ref = new AlertDialog[1];

        for (Theme t : themes) {
            TextView row = makeReaderActionRow(t.getName(), fg);
            row.setOnClickListener(v -> {
                if (ref[0] != null) ref[0].dismiss();
                themeManager.setActiveTheme(t.getId());
                applyTheme();
            });
            list.addView(row);
        }

        TextView title = makeReaderDialogTitle(getString(R.string.reading_theme), bg, fg);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setCustomTitle(title)
                .setView(scroll)
                .setNeutralButton(getString(R.string.create_new),
                        (d, w) -> startActivity(new Intent(this, ThemeEditorActivity.class)))
                .setNegativeButton(getString(R.string.cancel), null)
                .create();
        ref[0] = dialog;

        dialog.setOnShowListener(d -> styleReaderDialogWindow(dialog, bg, fg, sub));
        dialog.show();
    }

    // --- Font selection ---

    private void showFontDialog() {
        FontManager fm = FontManager.getInstance();
        if (!fm.isScanned()) {
            Toast.makeText(this, getString(R.string.scanning_fonts), Toast.LENGTH_SHORT).show();
            fm.scanFonts(this, this::showFontPickerDialog);
        } else {
            showFontPickerDialog(fm.getFontNames());
        }
    }

    private void showFontPickerDialog(List<String> fontNames) {
        final int bg = readerDialogBgColor();
        final int fg = readerDialogTextColor(bg);
        final int sub = readerDialogSubTextColor(bg);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setBackgroundColor(bg);
        int pad = dpToPx(14);
        list.setPadding(pad, dpToPx(8), pad, dpToPx(8));

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(bg);
        scroll.addView(list);

        final AlertDialog[] ref = new AlertDialog[1];

        for (String name : fontNames) {
            TextView row = makeReaderActionRow(name, fg);
            row.setOnClickListener(v -> {
                if (ref[0] != null) ref[0].dismiss();
                prefs.setFontFamily(name);
                applyPreferences();
                updatePositionLabel();
            });
            list.addView(row);
        }

        TextView title = makeReaderDialogTitle(getString(R.string.select_font), bg, fg);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setCustomTitle(title)
                .setView(scroll)
                .setNegativeButton(getString(R.string.cancel), null)
                .create();
        ref[0] = dialog;

        dialog.setOnShowListener(d -> styleReaderDialogWindow(dialog, bg, fg, sub));
        dialog.show();
    }

    // --- Go To / Search ---

    private void showGoToDialog() {
        final int bg = readerDialogBgColor();
        final int fg = readerDialogTextColor(bg);
        final int sub = readerDialogSubTextColor(bg);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(bg);
        box.setPadding(dpToPx(24), dpToPx(12), dpToPx(24), dpToPx(8));

        TextView percentLabel = makeReaderDialogLabel(getString(R.string.go_to_percentage), fg, 14f);
        box.addView(percentLabel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        EditText inputPercent = makeReaderDialogEditText(getString(R.string.example_50), bg, fg, sub);
        inputPercent.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        box.addView(inputPercent, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(52)));

        TextView lineLabel = makeReaderDialogLabel(getString(R.string.or_go_to_line_number), fg, 14f);
        lineLabel.setPadding(0, dpToPx(6), 0, 0);
        box.addView(lineLabel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        EditText inputLine = makeReaderDialogEditText(getString(R.string.example_1000), bg, fg, sub);
        inputLine.setInputType(InputType.TYPE_CLASS_NUMBER);
        box.addView(inputLine, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(52)));

        TextView title = makeReaderDialogTitle(getString(R.string.go_to_position), bg, fg);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setCustomTitle(title)
                .setView(box)
                .setPositiveButton(getString(R.string.go), (d, w) -> {
                    String pStr = inputPercent.getText().toString().trim();
                    String lStr = inputLine.getText().toString().trim();
                    if (!pStr.isEmpty()) {
                        try { scrollToPercent(Float.parseFloat(pStr) / 100f); }
                        catch (NumberFormatException ignored) {}
                    } else if (!lStr.isEmpty()) {
                        try {
                            int targetLine = Math.max(1, Integer.parseInt(lStr));
                            scrollToCharPosition(findCharForLine(targetLine));
                        } catch (NumberFormatException ignored) {}
                    }
                    updatePositionLabel();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .create();

        dialog.setOnShowListener(d -> styleReaderDialogWindow(dialog, bg, fg, sub));
        dialog.show();
    }

    private void showTextSearch() {
        final int bg = readerDialogBgColor();
        final int fg = readerDialogTextColor(bg);
        final int sub = readerDialogSubTextColor(bg);

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.HORIZONTAL);
        titleBox.setGravity(Gravity.CENTER_VERTICAL);
        titleBox.setPadding(dpToPx(22), dpToPx(18), dpToPx(22), dpToPx(8));
        titleBox.setBackgroundColor(bg);

        TextView title = new TextView(this);
        title.setText(getString(R.string.find_in_text));
        title.setTextColor(fg);
        title.setTextSize(20f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        titleBox.addView(title, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f));

        TextView matchStatus = new TextView(this);
        matchStatus.setText("0 / 0");
        matchStatus.setTextColor(sub);
        matchStatus.setTextSize(12f);
        matchStatus.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        titleBox.addView(matchStatus, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(bg);
        box.setPadding(dpToPx(24), dpToPx(12), dpToPx(24), dpToPx(8));

        EditText input = makeReaderDialogEditText(getString(R.string.search_text_hint), bg, fg, sub);
        box.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(52)));

        TextView hint = makeReaderDialogLabel(getString(R.string.search_hint_multiple), sub, 12f);
        hint.setPadding(0, dpToPx(6), 0, dpToPx(8));
        box.addView(hint, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);
        buttons.setPadding(0, dpToPx(8), 0, 0);

        TextView prevButton = makeSearchDialogButton(getString(R.string.find_previous), bg, fg, sub);
        TextView closeButton = makeSearchDialogButton(getString(R.string.close), bg, fg, sub);
        TextView nextButton = makeSearchDialogButton(getString(R.string.find_next), bg, fg, sub);

        buttons.addView(prevButton, new LinearLayout.LayoutParams(0, dpToPx(44), 1f));
        buttons.addView(closeButton, new LinearLayout.LayoutParams(0, dpToPx(44), 1f));
        buttons.addView(nextButton, new LinearLayout.LayoutParams(0, dpToPx(44), 1f));
        box.addView(buttons, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setCustomTitle(titleBox)
                .setView(box)
                .create();

        prevButton.setOnClickListener(v -> performTextSearchMove(
                input.getText().toString(), false, matchStatus));
        nextButton.setOnClickListener(v -> performTextSearchMove(
                input.getText().toString(), true, matchStatus));
        closeButton.setOnClickListener(v -> dialog.dismiss());

        input.setOnEditorActionListener((v, actionId, event) -> {
            performTextSearchMove(input.getText().toString(), true, matchStatus);
            return true;
        });

        dialog.setOnShowListener(d -> styleReaderDialogWindow(dialog, bg, fg, sub));
        dialog.show();
    }

    private TextView makeSearchDialogButton(String label, int bg, int fg, int sub) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextColor(fg);
        button.setTextSize(14f);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dpToPx(4), 0, dpToPx(4), 0);

        // No individual boxes around 이전 찾기 / 닫기 / 다음 찾기.
        // The dialog itself provides the visual boundary.
        button.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dpToPx(42), 1f);
        lp.setMargins(dpToPx(3), 0, dpToPx(3), 0);
        button.setLayoutParams(lp);
        return button;
    }

    private void performTextSearchMove(String rawQuery, boolean forward, TextView matchStatus) {
        String query = rawQuery == null ? "" : rawQuery.trim();

        if (query.isEmpty()) {
            activeSearchQuery = "";
            activeSearchIndex = -1;
            if (matchStatus != null) matchStatus.setText("0 / 0");
            Toast.makeText(this, getString(R.string.enter_search_text), Toast.LENGTH_SHORT).show();
            return;
        }

        if (fileContent == null || fileContent.isEmpty()) return;

        int total = countTextMatches(query);
        if (total <= 0) {
            activeSearchQuery = query;
            activeSearchIndex = -1;
            if (matchStatus != null) matchStatus.setText("0 / 0");
            Toast.makeText(this, getString(R.string.not_found), Toast.LENGTH_SHORT).show();
            return;
        }

        if (!query.equals(activeSearchQuery)) {
            activeSearchQuery = query;
            activeSearchIndex = -1;
        }

        int idx;
        if (activeSearchIndex >= 0) {
            idx = forward
                    ? findText(query, activeSearchIndex + Math.max(1, query.length()))
                    : findTextBackward(query, activeSearchIndex - 1);
        } else {
            int currentPos = getCurrentCharPosition();
            idx = forward
                    ? findText(query, currentPos)
                    : findTextBackward(query, currentPos);
        }

        if (idx < 0) {
            idx = forward ? findText(query, 0) : findTextBackward(query, fileContent.length() - 1);
        }

        if (idx >= 0) {
            activeSearchIndex = idx;

            // Search movement should land on the actual match line, not keep reusing the
            // current top line as the next search base.
            scrollToCharPosition(idx);
            updatePositionLabel();

            int ordinal = matchIndexForPosition(query, idx);
            if (matchStatus != null) {
                matchStatus.setText(String.format(Locale.getDefault(), "%d / %d", ordinal, total));
            }

            Toast.makeText(this,
                    getString(R.string.found_match_status, ordinal, total, idx),
                    Toast.LENGTH_SHORT).show();
        } else {
            if (matchStatus != null) matchStatus.setText(String.format(Locale.getDefault(), "0 / %d", total));
            Toast.makeText(this, getString(R.string.not_found), Toast.LENGTH_SHORT).show();
        }
    }

    private void showFileInfoDialog() {
        if (filePath == null) return;

        final int bg = readerDialogBgColor();
        final int fg = readerDialogTextColor(bg);
        final int sub = readerDialogSubTextColor(bg);

        File file = new File(filePath);
        String encoding = FileUtils.detectEncoding(file);
        String info = getString(R.string.file_label) + ": " + fileName
                + "\n" + getString(R.string.file_info_path) + ": " + filePath
                + "\n" + getString(R.string.file_info_size) + ": " + FileUtils.formatFileSize(file.length())
                + "\n" + getString(R.string.file_info_encoding) + ": " + encoding
                + "\n" + getString(R.string.characters) + ": " + totalChars
                + "\n" + getString(R.string.lines) + ": " + totalLines
                + "\n" + getString(R.string.pages) + ": " + getTotalPageCount();

        TextView message = new TextView(this);
        message.setText(info);
        message.setTextColor(fg);
        message.setTextSize(14f);
        message.setLineSpacing(0f, 1.15f);
        message.setPadding(dpToPx(24), dpToPx(12), dpToPx(24), dpToPx(12));
        message.setBackgroundColor(bg);

        TextView title = makeReaderDialogTitle(getString(R.string.file_info), bg, fg);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setCustomTitle(title)
                .setView(message)
                .setPositiveButton(getString(R.string.ok), null)
                .create();

        dialog.setOnShowListener(d -> styleReaderDialogWindow(dialog, bg, fg, sub));
        dialog.show();
    }

    private void showShortViewerBackToast() {
        if (viewerBackToast != null) {
            viewerBackToast.cancel();
        }

        viewerBackToast = Toast.makeText(
                this,
                getString(R.string.press_back_again_exit),
                Toast.LENGTH_SHORT);
        viewerBackToast.show();

        handler.postDelayed(() -> {
            if (viewerBackToast != null) {
                viewerBackToast.cancel();
                viewerBackToast = null;
            }
        }, VIEWER_BACK_TOAST_DURATION_MS);
    }

    private void handleViewerBackPressed() {
        long now = System.currentTimeMillis();

        if (now - lastViewerBackPressedTime <= VIEWER_DOUBLE_BACK_TIMEOUT_MS) {
            if (viewerBackToast != null) {
                viewerBackToast.cancel();
                viewerBackToast = null;
            }
            finish();
            return;
        }

        lastViewerBackPressedTime = now;
        showShortViewerBackToast();
    }

    // --- Lifecycle ---

    @Override protected void onPause() { super.onPause(); saveReadingState(); }
    @Override protected void onDestroy() {
        if (viewerBackToast != null) {
            viewerBackToast.cancel();
            viewerBackToast = null;
        }
        super.onDestroy();
        saveReadingState();
        notificationHelper.dismiss();
        executor.shutdown();
    }

    private void saveReadingState() {
        if (filePath != null && prefs.getAutoSavePosition()) {
            ReaderState state = new ReaderState(filePath);
            state.setCharPosition(getCurrentCharPosition());
            state.setScrollY(readerView != null ? readerView.getReaderScrollY() : 0);
            bookmarkManager.saveReadingState(state);
        }
    }

    // --- Menu ---

    @Override public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        getMenuInflater().inflate(R.menu.menu_reader, menu);
        return true;
    }

    @Override public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) { finish(); return true; }
        else if (id == R.id.action_add_bookmark) { addBookmark(); return true; }
        else if (id == R.id.action_bookmarks) { showBookmarksForFile(); return true; }
        else if (id == R.id.action_go_to) { showGoToDialog(); return true; }
        else if (id == R.id.action_search) { showTextSearch(); return true; }
        else if (id == R.id.action_brightness) { showBrightnessDialog(); return true; }
        else if (id == R.id.action_theme) { showThemeDialog(); return true; }
        else if (id == R.id.action_font) { showFontDialog(); return true; }
        else if (id == R.id.action_font_increase) { changeFontSize(2f); return true; }
        else if (id == R.id.action_font_decrease) { changeFontSize(-2f); return true; }
        else if (id == R.id.action_file_info) { showFileInfoDialog(); return true; }
        return super.onOptionsItemSelected(item);
    }

    private void changeFontSize(float delta) {
        float newSize = Math.max(8f, Math.min(48f, prefs.getFontSize() + delta));
        prefs.setFontSize(newSize);
        applyPreferences();
        updatePositionLabel();
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }
}
