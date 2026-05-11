package com.simpletext.reader;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.Rect;
import android.graphics.Path;
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
import com.simpletext.reader.util.PageIndexCacheManager;
import com.simpletext.reader.util.ReadingNotificationHelper;
import com.simpletext.reader.util.ThemeManager;
import com.simpletext.reader.view.CustomReaderView;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class ReaderActivity extends AppCompatActivity {

    private static final long VIEWER_DOUBLE_BACK_TIMEOUT_MS = 1000L;
    private static final long VIEWER_BACK_TOAST_DURATION_MS = 650L;
    private static final long LARGE_TEXT_FAST_OPEN_THRESHOLD_BYTES = 3L * 1024L * 1024L;
    private static final long HUGE_TEXT_PREVIEW_ONLY_THRESHOLD_BYTES = 32L * 1024L * 1024L;
    private static final int LARGE_TEXT_PREVIEW_BYTES = 768 * 1024;
    private static final String FONT_OPTION_SYSTEM_CURRENT = "system_current";

    public static final String EXTRA_FILE_PATH = "file_path";
    public static final String EXTRA_FILE_URI = "file_uri";
    public static final String EXTRA_JUMP_TO_POSITION = "jump_position";
    public static final String EXTRA_JUMP_DISPLAY_PAGE = "jump_display_page";
    public static final String EXTRA_JUMP_TOTAL_PAGES = "jump_total_pages";

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

    private float appliedFontSize = Float.NaN;
    private float appliedLineSpacing = Float.NaN;
    private int appliedTextColor = Integer.MIN_VALUE;
    private int appliedBackgroundColor = Integer.MIN_VALUE;
    private int appliedMarginHorizontalPx = Integer.MIN_VALUE;
    private int appliedMarginVerticalPx = Integer.MIN_VALUE;
    private int appliedTopTextZoneOffsetPx = Integer.MIN_VALUE;
    private int appliedBottomTextZoneOffsetPx = Integer.MIN_VALUE;
    private int appliedLeftTextInsetPx = Integer.MIN_VALUE;
    private int appliedRightTextInsetPx = Integer.MIN_VALUE;
    private Typeface appliedTypeface = null;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean activityDestroyed = false;
    private final AtomicInteger loadGeneration = new AtomicInteger(0);
    private boolean largeTextEstimateActive = false;
    private int largeTextEstimatedTotalPages = 0;
    private int pendingLargeTextRestorePosition = -1;
    private int largeTextPreviewBaseCharOffset = 0;
    private int largeTextEstimatedBasePageOffset = 0;
    private int largeTextEstimatedTotalChars = 0;
    private boolean hugeTextPreviewOnly = false;
    private int pendingLargeTextCachedDisplayPage = 0;
    private int pendingLargeTextCachedTotalPages = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = PrefsManager.getInstance(this);
        prefs.applyLanguage(prefs.getLanguageMode());
        super.onCreate(savedInstanceState);
        ViewerRegistry.activate(this);

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
            @Override public void onSingleTap(float x, float y) { handleSingleTap(x, y); }
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

        loadFileFromIntent(getIntent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (themeManager != null) {
            themeManager.reloadFromStorage();
        }
        if (readerView != null && prefs != null && themeManager != null) {
            applyTheme();
            updatePositionLabel();
        }
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);

        // Single-viewer mode:
        // when a new TXT file is opened while a viewer already exists in this task,
        // reuse this ReaderActivity instead of stacking another ReaderActivity.
        saveReadingState();
        setIntent(intent);
        activeSearchQuery = "";
        activeSearchIndex = -1;
        applySearchHighlight();
        loadFileFromIntent(intent);
    }

    private void setupSeekBar() {
        seekBar.setMax(0);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private int pendingPage = 1;

            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (!fromUser || suppressSeekCallback || fileContent.isEmpty()) return;
                pendingPage = progress + 1;
                setPageLabels(pendingPage, getDisplayedTotalPageCount());
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
        int topTextZoneOffsetPx = prefs.getReaderTextTopOffsetPx();
        int bottomTextZoneOffsetPx = prefs.getReaderTextBottomOffsetPx();
        int leftTextInsetPx = prefs.getReaderTextLeftInsetPx();
        int rightTextInsetPx = prefs.getReaderTextRightInsetPx();

        Typeface tf = resolveReadingTypeface(prefs.getFontFamily());

        Theme theme = themeManager != null ? themeManager.getActiveTheme() : null;
        int textColor = theme != null ? theme.getTextColor() : 0xFFE0E0E0;
        int bgColor = theme != null ? theme.getBackgroundColor() : Color.BLACK;

        if (readerView != null) {
            readerView.setOverlapLines(prefs.getPagingOverlapLines());

            if (appliedTopTextZoneOffsetPx != topTextZoneOffsetPx
                    || appliedBottomTextZoneOffsetPx != bottomTextZoneOffsetPx
                    || appliedLeftTextInsetPx != leftTextInsetPx
                    || appliedRightTextInsetPx != rightTextInsetPx) {
                readerView.setTextZoneAdjustments(topTextZoneOffsetPx, bottomTextZoneOffsetPx,
                        leftTextInsetPx, rightTextInsetPx);
                appliedTopTextZoneOffsetPx = topTextZoneOffsetPx;
                appliedBottomTextZoneOffsetPx = bottomTextZoneOffsetPx;
                appliedLeftTextInsetPx = leftTextInsetPx;
                appliedRightTextInsetPx = rightTextInsetPx;
            }

            boolean styleChanged = Float.compare(appliedFontSize, fontSize) != 0
                    || Float.compare(appliedLineSpacing, lineSpacing) != 0
                    || appliedTextColor != textColor
                    || appliedBackgroundColor != bgColor
                    || appliedMarginHorizontalPx != marginH
                    || appliedMarginVerticalPx != marginV
                    || appliedTypeface != tf;

            if (styleChanged) {
                readerView.setReaderStyle(fontSize, lineSpacing, textColor, bgColor, marginH, marginV, tf);
                appliedFontSize = fontSize;
                appliedLineSpacing = lineSpacing;
                appliedTextColor = textColor;
                appliedBackgroundColor = bgColor;
                appliedMarginHorizontalPx = marginH;
                appliedMarginVerticalPx = marginV;
                appliedTypeface = tf;
            }
        }

        // The page-count/status strip and Android status bar follow the active reader theme.
        applyReaderSystemBarColors(bgColor, readableTextColorForBackground(bgColor));

        if (prefs.getKeepScreenOn()) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        applyStatusBarVisibilityPreference();
        applyPageStatusAlignment(0);
        if (readerRoot != null) ViewCompat.requestApplyInsets(readerRoot);
    }

    private void applyStatusBarVisibilityPreference() {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());

        if (prefs != null && prefs.getShowStatusBar()) {
            controller.show(WindowInsetsCompat.Type.statusBars());
        } else {
            controller.hide(WindowInsetsCompat.Type.statusBars());
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
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
        applyStatusBarVisibilityPreference();
    }

    private boolean isLightColor(int color) {
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        double luminance = (0.299 * r + 0.587 * g + 0.114 * b);
        return luminance > 160;
    }

    private boolean isDarkColor(int color) {
        return !isLightColor(color);
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

    private void syncReaderDialogThemeSnapshot() {
        if (themeManager == null) {
            themeManager = ThemeManager.getInstance(this);
        }
        Theme theme = themeManager.getActiveTheme();
        if (theme != null) {
            currentReaderBackgroundColor = theme.getBackgroundColor();
        }
    }

    private int readerDialogBgColor() {
        syncReaderDialogThemeSnapshot();
        // Opaque, but theme-blended.
        // This keeps the Close/OK/Delete area non-transparent while avoiding a harsh flat gray block.
        boolean light = isLightColor(currentReaderBackgroundColor);
        int overlay = light ? Color.WHITE : Color.BLACK;
        float mix = light ? 0.10f : 0.18f;
        int blended = blendColors(currentReaderBackgroundColor, overlay, mix);
        return Color.rgb(Color.red(blended), Color.green(blended), Color.blue(blended));
    }

    private int readerDialogPanelColor() {
        syncReaderDialogThemeSnapshot();
        // Match the PDF/EPUB/Word dialog card tone: start from the actual
        // dialog surface and separate cards by blending toward the readable
        // foreground, not by adding a white overlay. This keeps TXT More/Font
        // cards in the same tone family as the document viewers.
        int bg = readerDialogBgColor();
        int fg = readableTextColorForBackground(bg);
        int blended = blendColors(bg, fg, isDarkColor(bg) ? 0.10f : 0.08f);
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
        return blendColors(bgColor, fg, isLightColor(bgColor) ? 0.58f : 0.78f);
    }

    private GradientDrawable pageMoveOuterBackground(int bgColor) {
    GradientDrawable drawable = new GradientDrawable();
    drawable.setColor(bgColor);
    drawable.setCornerRadius(dpToPx(16));
    drawable.setStroke(0, Color.TRANSPARENT);
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
        title.setBackgroundColor(Color.TRANSPARENT);
        return title;
    }

    private GradientDrawable largeFunctionBoxBackground(int bgColor) {
    GradientDrawable drawable = new GradientDrawable();
    drawable.setColor(bgColor);
    drawable.setCornerRadius(dpToPx(16));
    drawable.setStroke(0, Color.TRANSPARENT);
    return drawable;
}



    private TextView makeReaderActionRow(String text, int fgColor) {
        TextView row = new TextView(this);
        row.setText(text);
        row.setTextColor(fgColor);
        row.setTextSize(16f);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dpToPx(18), 0, dpToPx(18), 0);

        // Match the EPUB/Word/PDF "More" window style: each TXT More row is a
        // separate rounded card, not a flat/bland text row.  The fill and stroke
        // are theme-derived so light themes get a soft gray card and dark themes
        // get a visible but not harsh raised card.
        int panel = readerDialogPanelColor();
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(panel);
        bg.setCornerRadius(dpToPx(10));
        bg.setStroke(0, Color.TRANSPARENT);
        row.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(48));
        lp.setMargins(0, 0, 0, dpToPx(8));
        row.setLayoutParams(lp);
        return row;
    }

    private TextView makeReaderFontDialogTitle(String text, int bgColor, int fgColor) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextColor(fgColor);
        title.setTextSize(22f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(dpToPx(18), dpToPx(18), dpToPx(18), dpToPx(14));
        title.setBackgroundColor(Color.TRANSPARENT);
        return title;
    }

    private TextView makeReaderFontActionRow(String text, int fgColor) {
        return makeReaderFontActionRow(text, fgColor, false);
    }

    private TextView makeReaderFontActionRow(String text, int fgColor, boolean selected) {
        TextView row = new TextView(this);
        row.setText(text);
        row.setTextColor(fgColor);
        row.setTextSize(16f);
        row.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        row.setGravity(Gravity.CENTER_VERTICAL);
        // Keep the rounded row "bubble" used by EPUB/Word, but remove the small
        // radio/circle bubble. Selection is shown by text weight and row outline.
        row.setPadding(dpToPx(18), 0, dpToPx(18), 0);
        row.setCompoundDrawables(null, null, null, null);
        row.setCompoundDrawablePadding(0);

        GradientDrawable bg = new GradientDrawable();
        int panel = readerDialogPanelColor();
        boolean darkPanel = isDarkColor(panel);
        int normalFill = blendColors(panel, fgColor, darkPanel ? 0.055f : 0.035f);
        int selectedFill = blendColors(panel, fgColor, darkPanel ? 0.120f : 0.075f);
        int normalStroke = blendColors(panel, fgColor, darkPanel ? 0.130f : 0.100f);
        int selectedStroke = blendColors(panel, fgColor, darkPanel ? 0.420f : 0.360f);

        // Every font row should look like the EPUB/Word rounded card row, not only
        // the selected row. Selection is shown by stronger text + stronger outline.
        bg.setColor(selected ? selectedFill : normalFill);
        bg.setCornerRadius(dpToPx(10));
        bg.setStroke(Math.max(1, dpToPx(1)), selected ? selectedStroke : normalStroke);
        row.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(48));
        lp.setMargins(0, 0, 0, dpToPx(8));
        row.setLayoutParams(lp);
        return row;
    }

    private void applyOuterBorderToDialogPanel(AlertDialog dialog, int bgColor, int borderColor) {
        if (dialog == null || dialog.getWindow() == null) return;

        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        View decor = dialog.getWindow().getDecorView();
        View panel = decor;

        if (decor instanceof ViewGroup && ((ViewGroup) decor).getChildCount() > 0) {
            panel = ((ViewGroup) decor).getChildAt(0);
        }

        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(bgColor);
        panelBg.setCornerRadius(dpToPx(16));
        panelBg.setStroke(dpToPx(1), borderColor);

        GradientDrawable foregroundBorder = new GradientDrawable();
        foregroundBorder.setColor(Color.TRANSPARENT);
        foregroundBorder.setCornerRadius(dpToPx(16));
        foregroundBorder.setStroke(dpToPx(2), borderColor);

        // No inner padding here. The overlay stroke sits exactly on the panel edge,
        // so any 2dp inset would push the content panel inward and cause a sub-pixel
        // mismatch where the inner rounded fill no longer aligns with the outer
        // rounded outline (the chipping seen at 북마크 / 글꼴 corners).
        panel.setBackground(panelBg);
        panel.setForeground(foregroundBorder);
        panel.setPadding(0, 0, 0, 0);
        panel.setClipToOutline(true);

        if (panel instanceof ViewGroup) {
            ((ViewGroup) panel).setClipChildren(true);
            ((ViewGroup) panel).setClipToPadding(true);
            // Make sure every nested AlertDialog sub-panel (topPanel / contentPanel /
            // buttonPanel) is also clipped to its parent. Without this, the bottom
            // action panel's rectangular fill paints past the rounded corner before
            // the overlay border is drawn, which is visible as a tiny straight
            // edge poking out from behind the curve.
            forceClipChildrenRecursive((ViewGroup) panel);
        }

        decor.addOnLayoutChangeListener((v, left, top, right, bottom,
                                          oldLeft, oldTop, oldRight, oldBottom) ->
                redrawDialogOuterBorder(dialog, borderColor));

        redrawDialogOuterBorder(dialog, borderColor);
    }

    private void forceClipChildrenRecursive(ViewGroup group) {
        if (group == null) return;
        group.setClipChildren(true);
        group.setClipToPadding(true);
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof ViewGroup) {
                forceClipChildrenRecursive((ViewGroup) child);
            }
        }
    }



    private void redrawDialogOuterBorder(android.app.Dialog dialog, int borderColor) {
    if (dialog == null || dialog.getWindow() == null) return;

    View decor = dialog.getWindow().getDecorView();
    decor.post(() -> {
        if (decor.getWidth() <= 0 || decor.getHeight() <= 0) return;

        final float density = getResources().getDisplayMetrics().density;
        final float strokePx = Math.max(1f, 1.5f * density);
        final float outerRadiusPx = dpToPx(16);

        Drawable overlayBorder = new Drawable() {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF rect = new RectF();

            {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(strokePx);
                paint.setColor(borderColor);
            }

            @Override
            public void draw(Canvas canvas) {
                float half = strokePx / 2f;
                Rect bounds = getBounds();

                // Center the stroke half a stroke inside the window bounds.
                // This makes the OUTER edge of the stroke sit on the dialog/window edge,
                // instead of being pushed inward.
                rect.set(
                        bounds.left + half,
                        bounds.top + half,
                        bounds.right - half,
                        bounds.bottom - half
                );

                float centerRadius = Math.max(0f, outerRadiusPx - half);
                canvas.drawRoundRect(rect, centerRadius, centerRadius, paint);
            }

            @Override public void setAlpha(int alpha) {
                paint.setAlpha(alpha);
            }

            @Override public void setColorFilter(ColorFilter colorFilter) {
                paint.setColorFilter(colorFilter);
            }

            @Override public int getOpacity() {
                return PixelFormat.TRANSLUCENT;
            }
        };

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

    private GradientDrawable positionedReaderDialogBackground(int bgColor) {
        // Fill only. The visible border is drawn as a foreground overlay below.
        // Drawing the stroke in the background can lose the top rounded corner
        // because half of the stroke is clipped by the rounded outline.
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(bgColor);
        drawable.setCornerRadius(dpToPx(16));
        return drawable;
    }

    private Drawable positionedReaderDialogBorderOverlay(int bgColor) {
        final int borderColor = strongDialogBorderColor(bgColor);
        return new Drawable() {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF rect = new RectF();

            {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dpToPx(2));
                paint.setColor(borderColor);
            }

            @Override
            public void draw(Canvas canvas) {
                Rect bounds = getBounds();
                float half = paint.getStrokeWidth() / 2f;
                rect.set(bounds.left + half, bounds.top + half,
                        bounds.right - half, bounds.bottom - half);
                canvas.drawRoundRect(rect, dpToPx(16) - half, dpToPx(16) - half, paint);
            }

            @Override public void setAlpha(int alpha) {
                paint.setAlpha(alpha);
            }

            @Override public void setColorFilter(ColorFilter colorFilter) {
                paint.setColorFilter(colorFilter);
            }

            @Override public int getOpacity() {
                return PixelFormat.TRANSLUCENT;
            }
        };
    }

    private android.app.Dialog createPositionedReaderDialog(@NonNull View content,
                                                            int bgColor,
                                                            int gravity,
                                                            int yDp,
                                                            int horizontalMarginDp,
                                                            int maxWidthDp,
                                                            boolean adjustResize) {
        return createPositionedReaderDialog(content, bgColor, gravity, yDp,
                horizontalMarginDp, maxWidthDp, 0f, adjustResize);
    }

    private android.app.Dialog createNarrowPositionedReaderDialog(@NonNull View content,
                                                                  int bgColor,
                                                                  int gravity,
                                                                  int yDp,
                                                                  float widthFraction,
                                                                  int maxWidthDp,
                                                                  boolean adjustResize) {
        return createPositionedReaderDialog(content, bgColor, gravity, yDp,
                0, maxWidthDp, widthFraction, adjustResize);
    }

    private android.app.Dialog createPositionedReaderDialog(@NonNull View content,
                                                            int bgColor,
                                                            int gravity,
                                                            int yDp,
                                                            int horizontalMarginDp,
                                                            int maxWidthDp,
                                                            float widthFraction,
                                                            boolean adjustResize) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        FrameLayout outerFrame = new FrameLayout(this);
        outerFrame.setBackground(positionedReaderDialogBackground(bgColor));
        // Draw the rounded border above the title/list contents. This restores the
        // visible upper-left/upper-right rounded barrier on font dialogs even when
        // the scroll list/header use their own opaque backgrounds.
        outerFrame.setForeground(positionedReaderDialogBorderOverlay(bgColor));
        outerFrame.setClipToOutline(true);
        outerFrame.setClipChildren(true);
        outerFrame.setClipToPadding(true);
        int borderPad = 0;
        outerFrame.setPadding(borderPad, borderPad, borderPad, borderPad);

        content.setBackgroundColor(Color.TRANSPARENT);
        if (content instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) content;
            group.setClipChildren(true);
            group.setClipToPadding(true);
        }

        outerFrame.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));

        dialog.setContentView(outerFrame);

        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(gravity);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.copyFrom(window.getAttributes());
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int cappedWidth = Math.min(screenWidth - dpToPx(horizontalMarginDp), dpToPx(maxWidthDp));
            if (widthFraction > 0f && widthFraction < 1f) {
                cappedWidth = Math.min(Math.round(screenWidth * widthFraction), dpToPx(maxWidthDp));
            }
            lp.width = Math.max(dpToPx(220), cappedWidth);
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            lp.y = dpToPx(yDp);
            lp.dimAmount = 0.16f;
            window.setAttributes(lp);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            if (adjustResize) {
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
        }
        return dialog;
    }

    private void updatePositionedReaderDialogYOffset(@NonNull android.app.Dialog dialog, int yDp) {
        android.view.Window window = dialog.getWindow();
        if (window == null) return;

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.copyFrom(window.getAttributes());
        lp.y = dpToPx(yDp);
        window.setAttributes(lp);
    }

    private TextView makeReaderDialogActionText(String label, int textColor, int gravity) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextColor(textColor);
        button.setTextSize(16f);
        button.setGravity(gravity);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setPadding(dpToPx(18), 0, dpToPx(18), 0);
        return button;
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
            private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            {
                fillPaint.setColor(fillColor);
                fillPaint.setStyle(Paint.Style.FILL);
                linePaint.setColor(lineColor);
                linePaint.setStyle(Paint.Style.FILL);
            }

            @Override
            public void draw(Canvas canvas) {
                Rect bounds = getBounds();

                // Flat fill + top divider line only.
                // The dialog's outer rounded panel clips its children to the rounded
                // outline (clipToOutline = true), so this rectangle naturally takes
                // on the rounded-bottom shape from the parent's clip with NO second
                // rounded path drawing on top of it. This removes the AA "chipping"
                // that appeared where the panel's own curve met the parent's curve.
                canvas.drawRect(bounds, fillPaint);

                float h = getResources().getDisplayMetrics().density;
                canvas.drawRect(bounds.left, bounds.top, bounds.right, bounds.top + h, linePaint);
            }

            @Override public void setAlpha(int alpha) {
                fillPaint.setAlpha(alpha);
                linePaint.setAlpha(alpha);
            }

            @Override public void setColorFilter(ColorFilter colorFilter) {
                fillPaint.setColorFilter(colorFilter);
                linePaint.setColorFilter(colorFilter);
            }

            @Override public int getOpacity() {
                return PixelFormat.TRANSLUCENT;
            }
        };
    }

    private Drawable positionedActionPanelBackground(int fillColor, int lineColor) {
        return new Drawable() {
            private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF rect = new RectF();
            private final Path path = new Path();

            {
                fillPaint.setColor(fillColor);
                fillPaint.setStyle(Paint.Style.FILL);
                linePaint.setColor(lineColor);
                linePaint.setStyle(Paint.Style.FILL);
            }

            @Override
            public void draw(Canvas canvas) {
                Rect bounds = getBounds();
                rect.set(bounds.left, bounds.top, bounds.right, bounds.bottom);

                float r = dpToPx(16);
                path.reset();
                path.addRoundRect(rect, new float[]{
                        0, 0,   // top-left
                        0, 0,   // top-right
                        r, r,   // bottom-right
                        r, r    // bottom-left
                }, Path.Direction.CW);
                canvas.drawPath(path, fillPaint);

                float h = getResources().getDisplayMetrics().density;
                canvas.drawRect(bounds.left, bounds.top, bounds.right, bounds.top + h, linePaint);
            }

            @Override public void setAlpha(int alpha) {
                fillPaint.setAlpha(alpha);
                linePaint.setAlpha(alpha);
            }

            @Override public void setColorFilter(ColorFilter colorFilter) {
                fillPaint.setColorFilter(colorFilter);
                linePaint.setColorFilter(colorFilter);
            }

            @Override public int getOpacity() {
                return PixelFormat.TRANSLUCENT;
            }
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
        final int danger = isLightColor(bg) ? Color.rgb(95, 35, 35) : Color.rgb(255, 170, 170);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Color.TRANSPARENT);

        TextView title = makeReaderDialogTitle(getString(R.string.delete_bookmark), bg, fg);
        panel.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(Color.TRANSPARENT);
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
        panel.addView(box, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        actions.setBackground(actionPanelBackground(
                dialogActionPanelFillColor(bg), dialogActionPanelLineColor(bg)));
        actions.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        TextView cancel = makeReaderDialogActionText(getString(R.string.cancel), sub, Gravity.CENTER);
        TextView delete = makeReaderDialogActionText(getString(R.string.delete), danger, Gravity.CENTER);
        actions.addView(cancel, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(46)));
        actions.addView(delete, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(46)));
        panel.addView(actions, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        android.app.Dialog dialog = createPositionedReaderDialog(panel, bg,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 74, 14, 460, false);
        cancel.setOnClickListener(v -> dialog.dismiss());
        delete.setOnClickListener(v -> {
            bookmarkManager.deleteBookmark(bookmark.getId());
            if (afterDelete != null) afterDelete.run();
            dialog.dismiss();
        });
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
        drawable.setCornerRadius(dpToPx(6));
        // Subtle inner boundary only. The strong contrast border belongs to the OUTER dialog box.
        drawable.setStroke(dpToPx(1), stroke);

        input.setBackgroundTintList(null);
        input.setBackground(drawable);
        tintReaderDialogEditHandles(input, bgColor, fgColor);
    }

    private void tintReaderDialogEditHandles(EditText input, int bgColor, int fgColor) {
        if (input == null) return;

        boolean lightReaderDialog = isLightColor(bgColor);
        int accent = lightReaderDialog ? Color.rgb(34, 34, 34) : Color.WHITE;

        input.setHighlightColor(blendColors(bgColor, accent, lightReaderDialog ? 0.24f : 0.42f));

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            GradientDrawable cursor = new GradientDrawable();
            cursor.setColor(accent);
            cursor.setSize(Math.max(2, dpToPx(2)), dpToPx(28));
            input.setTextCursorDrawable(cursor);
        }

    }

    private EditText makeReaderDialogEditText(String hint, int bgColor, int fgColor, int subColor) {
        // Build the EditText from a ContextThemeWrapper whose theme matches the
        // READER theme's background brightness, NOT the system night mode. The reader
        // has its own theme (ThemeManager.getActiveTheme()), so a Cream-on-Dark-system
        // configuration must still draw a light-mode dialog with a dark caret/handle.
        // Picking the local theme via ContextThemeWrapper makes the caret bar, the
        // selection-handle teardrop, and the floating action-mode toolbar (the
        // "복사 / 번역 / 모두 선택 / 공유" or paste tooltip popup) all inherit a
        // consistent high-contrast palette regardless of which system mode is active.
        int overlay = isLightColor(bgColor)
                ? R.style.ThemeOverlay_SimpleText_ReaderDialogLight
                : R.style.ThemeOverlay_SimpleText_ReaderDialogDark;
        android.view.ContextThemeWrapper themed = new android.view.ContextThemeWrapper(this, overlay);
        EditText input = new EditText(themed);
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
        if (theme != null) {
            currentReaderBackgroundColor = theme.getBackgroundColor();
        }
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
            int topInset = (prefs != null && prefs.getShowStatusBar()) ? bars.top : 0;
            int bottomInset = bars.bottom;

            if (navBarSpacer != null) {
                FrameLayout.LayoutParams spacerLp =
                        (FrameLayout.LayoutParams) navBarSpacer.getLayoutParams();
                spacerLp.height = bottomInset;
                spacerLp.gravity = Gravity.BOTTOM;
                navBarSpacer.setLayoutParams(spacerLp);
            }

            // Always reserve the page-indicator row height. The "Do not show" option
            // makes the indicator invisible, but it should not move the text upward.
            int pageStatusHeight = topInset + dpToPx(28);
            if (readerPageStatus != null) {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) readerPageStatus.getLayoutParams();
                lp.topMargin = 0;
                lp.height = pageStatusHeight;
                readerPageStatus.setLayoutParams(lp);
                applyPageStatusAlignment(topInset);
            }

            int statusOffExtraTopPadding = 0;
            if (prefs != null && !prefs.getShowStatusBar()) {
                // When the Android status bar is hidden, the content can start too close
                // to the punch-hole/cutout area. Add roughly one text row of top padding.
                statusOffExtraTopPadding = Math.round(prefs.getFontSize()
                        * prefs.getLineSpacing()
                        * getResources().getDisplayMetrics().scaledDensity);
            }

            readerView.setPadding(
                    readerView.getPaddingLeft(),
                    pageStatusHeight + dpToPx(8) + statusOffExtraTopPadding,
                    readerView.getPaddingRight(),
                    bottomInset + dpToPx(12));

            bottomBar.setPadding(
                    dpToPx(20),
                    dpToPx(10),
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

    private void applyPageStatusAlignment(int topInset) {
        if (readerPageStatus == null) return;

        int alignment = prefs != null
                ? prefs.getPageStatusAlignment()
                : PrefsManager.PAGE_STATUS_ALIGN_CENTER;

        if (alignment == PrefsManager.PAGE_STATUS_ALIGN_HIDDEN) {
            readerPageStatus.setVisibility(View.INVISIBLE);
            return;
        }

        readerPageStatus.setVisibility(View.VISIBLE);

        int horizontalGravity;
        int startPadding;
        int endPadding;

        // Extra side padding keeps left/right indicators away from curved edges,
        // punch-hole/camera cutouts, and gesture-status areas.
        int sideInset = Math.max(dpToPx(36), topInset + dpToPx(18));
        int nearSideInset = dpToPx(16);

        if (alignment == PrefsManager.PAGE_STATUS_ALIGN_LEFT) {
            horizontalGravity = Gravity.START;
            startPadding = sideInset;
            endPadding = nearSideInset;
        } else if (alignment == PrefsManager.PAGE_STATUS_ALIGN_RIGHT) {
            horizontalGravity = Gravity.END;
            startPadding = nearSideInset;
            endPadding = sideInset;
        } else {
            horizontalGravity = Gravity.CENTER_HORIZONTAL;
            startPadding = sideInset;
            endPadding = sideInset;
        }

        readerPageStatus.setGravity(Gravity.BOTTOM | horizontalGravity);
        // Smaller bottom padding moves the page indicator slightly downward.
        readerPageStatus.setPadding(startPadding, topInset, endPadding, dpToPx(1));
    }

    private void openFileBrowserFromViewer() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_RETURN_TO_VIEWER, true);
        File current = filePath != null ? new File(filePath) : null;
        File parent = current != null ? current.getParentFile() : null;
        if (parent != null && parent.exists() && parent.isDirectory()) {
            intent.putExtra(MainActivity.EXTRA_START_DIRECTORY, parent.getAbsolutePath());
        }
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

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Color.TRANSPARENT);
        panel.setClipChildren(true);
        panel.setClipToPadding(true);

        TextView title = makeReaderDialogTitle(getString(R.string.page_move), bubbleBg, bubbleFg);
        panel.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dpToPx(20);
        box.setPadding(pad, dpToPx(8), pad, dpToPx(12));
        box.setBackgroundColor(Color.TRANSPARENT);

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
        pageHint.setTextColor(blendColors(bubbleBg, bubbleFg, 0.78f));
        pageHint.setGravity(Gravity.CENTER);
        pageHint.setPadding(0, dpToPx(3), 0, 0);
        box.addView(pageHint, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        EditText pageInput = makeReaderDialogEditText("1 - " + totalPages, bubbleBg, bubbleFg, bubbleSub);
        pageInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        pageInput.setGravity(Gravity.CENTER);
        pageInput.setText(String.valueOf(currentPage));
        pageInput.setSelectAllOnFocus(true);
        LinearLayout.LayoutParams pageInputLp = new LinearLayout.LayoutParams(
                dpToPx(132),
                dpToPx(52));
        pageInputLp.gravity = Gravity.CENTER_HORIZONTAL;
        pageInputLp.setMargins(0, dpToPx(8), 0, 0);
        box.addView(pageInput, pageInputLp);

        panel.addView(box, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        actionRow.setBackground(positionedActionPanelBackground(
                dialogActionPanelFillColor(bubbleBg),
                dialogActionPanelLineColor(bubbleBg)));
        actionRow.setPadding(dpToPx(30), 0, dpToPx(30), 0);

        TextView closeButton = makeReaderDialogActionText(getString(R.string.close), bubbleFg,
                Gravity.CENTER_VERTICAL | Gravity.END);
        actionRow.addView(closeButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));
        panel.addView(actionRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(54)));

        android.app.Dialog dialog = createNarrowPositionedReaderDialog(
                panel,
                bubbleBg,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL,
                88,
                0.85f,
                460,
                true);

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

        closeButton.setOnClickListener(v -> {
            String raw = pageInput.getText().toString().trim();
            if (raw.isEmpty()) {
                dialog.dismiss();
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

        dialog.show();
    }

    private String formatPageMoveLabel(int page, int totalPages) {
        return String.format(Locale.getDefault(), "Page %d / %d", page, Math.max(1, totalPages));
    }

    private void showMoreDialog() {
        syncReaderDialogThemeSnapshot();
        final int bg = readerDialogBgColor();
        final int panel = readerDialogPanelColor();
        final int fg = readerDialogTextColor(bg);
        final int sub = readerDialogSubTextColor(bg);

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(Color.TRANSPARENT);

        TextView title = makeReaderDialogTitle(getString(R.string.more), bg, fg);
        outer.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setBackgroundColor(Color.TRANSPARENT);
        int pad = dpToPx(14);
        list.setPadding(pad, dpToPx(10), pad, dpToPx(10));

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.TRANSPARENT);
        constrainDialogScrollArea(scroll, list);
        scroll.addView(list);

        final android.app.Dialog[] ref = new android.app.Dialog[1];

        addMoreActionRow(list, getString(R.string.brightness), fg, panel, this::showBrightnessDialog, ref);
        addMoreActionRow(list, getString(R.string.font), fg, panel, this::showFontDialog, ref);
        addMoreActionRow(list, getString(R.string.increase_font), fg, panel, () -> changeFontSize(2f), ref);
        addMoreActionRow(list, getString(R.string.decrease_font), fg, panel, () -> changeFontSize(-2f), ref);
        addMoreActionRow(list, getString(R.string.file_info), fg, panel, this::showFileInfoDialog, ref);

        outer.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        actionRow.setBackground(positionedActionPanelBackground(
                dialogActionPanelFillColor(bg),
                dialogActionPanelLineColor(bg)));
        actionRow.setPadding(dpToPx(30), 0, dpToPx(30), 0);

        TextView openFile = makeReaderDialogActionText(getString(R.string.action_open_file), fg,
                Gravity.CENTER_VERTICAL | Gravity.START);
        TextView close = makeReaderDialogActionText(getString(R.string.close), fg,
                Gravity.CENTER_VERTICAL | Gravity.END);

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

        android.app.Dialog dialog = createNarrowPositionedReaderDialog(
                outer,
                bg,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL,
                88,
                0.85f,
                460,
                false);
        ref[0] = dialog;

        openFile.setOnClickListener(v -> {
            dialog.dismiss();
            openFileBrowserFromViewer();
        });
        close.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void addMoreActionRow(
            LinearLayout list,
            String label,
            int fg,
            int panel,
            Runnable action,
            android.app.Dialog[] dialogRef
    ) {
        TextView row = makeReaderActionRow(label, fg);
        row.setOnClickListener(v -> {
            if (dialogRef[0] != null) dialogRef[0].dismiss();
            action.run();
        });
        list.addView(row);
    }

    private void loadFileFromIntent(@NonNull Intent sourceIntent) {
        final int generation = loadGeneration.incrementAndGet();
        activityDestroyed = false;
        progressBar.setVisibility(View.VISIBLE);
        progressText.setVisibility(View.VISIBLE);
        progressText.setText(getString(R.string.loading));

        // Clear old viewer state immediately so opening a different file does not
        // briefly keep old search/bookmark/page state around.
        fileContent = "";
        activeSearchQuery = "";
        activeSearchIndex = -1;
        largeTextEstimateActive = false;
        largeTextEstimatedTotalPages = 0;
        pendingLargeTextRestorePosition = -1;
        largeTextPreviewBaseCharOffset = 0;
        largeTextEstimatedBasePageOffset = 0;
        largeTextEstimatedTotalChars = 0;
        hugeTextPreviewOnly = false;
        pendingLargeTextCachedDisplayPage = 0;
        pendingLargeTextCachedTotalPages = 0;
        applySearchHighlight();
        updatePositionLabel();

        String path = sourceIntent.getStringExtra(EXTRA_FILE_PATH);
        String uriStr = sourceIntent.getStringExtra(EXTRA_FILE_URI);
        int jumpPosition = sourceIntent.getIntExtra(EXTRA_JUMP_TO_POSITION, -1);
        int jumpDisplayPage = sourceIntent.getIntExtra(EXTRA_JUMP_DISPLAY_PAGE, 0);
        int jumpTotalPages = sourceIntent.getIntExtra(EXTRA_JUMP_TOTAL_PAGES, 0);

        executor.execute(() -> {
            if (activityDestroyed || generation != loadGeneration.get()) return;
            try {
                File fileToRead = null;
                String loadedFilePath = null;
                String loadedFileName = null;

                if (path != null) {
                    File file = new File(path);
                    loadedFilePath = file.getAbsolutePath();
                    loadedFileName = file.getName();
                    fileToRead = file;
                } else if (uriStr != null) {
                    Uri uri = Uri.parse(uriStr);
                    loadedFileName = FileUtils.getFileNameFromUri(this, uri);
                    File localFile = FileUtils.copyUriToLocal(this, uri,
                            loadedFileName != null ? loadedFileName : "opened_file.txt");
                    loadedFilePath = localFile.getAbsolutePath();
                    fileToRead = localFile;
                }

                if (fileToRead == null) throw new IllegalArgumentException("No file selected");

                final String finalFilePath = loadedFilePath;
                final String finalFileName = loadedFileName;
                final boolean useLargeTextFastOpen = shouldUseLargeTextFastOpen(fileToRead);
                if (useLargeTextFastOpen) {
                    recordLargeTextCacheAccess(fileToRead);
                }

                if (useLargeTextFastOpen) {
                    CachedRestoreTarget restoreTarget = resolveInitialRestoreTarget(
                            finalFilePath, jumpPosition, jumpDisplayPage, jumpTotalPages, fileToRead.length());
                    int initialRestorePosition = restoreTarget.charPosition;
                    float estimatedBytesPerChar = estimateBytesPerChar(fileToRead);
                    long fullByteLength = Math.max(1L, fileToRead.length());
                    long previewStartByte = estimatePreviewStartByte(
                            fileToRead, initialRestorePosition, estimatedBytesPerChar);
                    int previewBaseCharOffset = estimateCharOffsetForByte(previewStartByte, estimatedBytesPerChar);

                    byte[] previewBytes = readPreviewBytesAt(fileToRead, previewStartByte, LARGE_TEXT_PREVIEW_BYTES);
                    String previewContent = decodePreviewBytes(fileToRead, previewBytes);
                    int previewLineCount = countLines(previewContent);
                    int previewByteCount = Math.max(1, previewBytes.length);

                    int estimatedTotalChars = Math.max(previewContent.length(),
                            Math.round(fullByteLength / Math.max(1f, estimatedBytesPerChar)));
                    boolean previewOnly = fileToRead.length() >= HUGE_TEXT_PREVIEW_ONLY_THRESHOLD_BYTES;

                    handler.post(() -> {
                        if (!activityDestroyed && generation == loadGeneration.get()) {
                            onLargeTextPreviewLoaded(previewContent, previewLineCount,
                                    finalFilePath, finalFileName, jumpPosition,
                                    fullByteLength, previewByteCount,
                                    previewStartByte, previewBaseCharOffset,
                                    estimatedTotalChars, previewOnly,
                                    restoreTarget.displayPage, restoreTarget.totalPages);
                        }
                    });

                    if (activityDestroyed || generation != loadGeneration.get() || previewOnly) return;
                }

                String content = FileUtils.readReadableFile(this, fileToRead);
                int lineCount = countLines(content);

                handler.post(() -> {
                    if (!activityDestroyed && generation == loadGeneration.get()) {
                        onFileLoaded(content, lineCount, finalFilePath, finalFileName, jumpPosition);
                    }
                });
            } catch (Exception e) {
                handler.post(() -> {
                    if (activityDestroyed || generation != loadGeneration.get()) return;
                    progressText.setText(String.format(Locale.getDefault(), "%s%s", getString(R.string.error_prefix), e.getMessage()));
                    Toast.makeText(this, getString(R.string.error_prefix) + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void recordLargeTextCacheAccess(@NonNull File file) {
        try {
            PageIndexCacheManager.getInstance(this)
                    .recordFileAccess(file, buildTextPageCacheLayoutSignature());
        } catch (Throwable ignored) {
            // Cache bookkeeping is best-effort and must never break file opening.
        }
    }

    private String buildTextPageCacheLayoutSignature() {
        if (prefs == null) return "unknown";

        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        String fontName = prefs.getFontFamily();
        if (fontName == null) fontName = "default";

        return "txt-v1"
                + "|fontSize=" + prefs.getFontSize()
                + "|lineSpacing=" + prefs.getLineSpacing()
                + "|marginH=" + prefs.getMarginHorizontal()
                + "|marginV=" + prefs.getMarginVertical()
                + "|top=" + prefs.getReaderTextTopOffsetPx()
                + "|bottom=" + prefs.getReaderTextBottomOffsetPx()
                + "|left=" + prefs.getReaderTextLeftInsetPx()
                + "|right=" + prefs.getReaderTextRightInsetPx()
                + "|overlap=" + prefs.getPagingOverlapLines()
                + "|font=" + fontName
                + "|screen=" + dm.widthPixels + "x" + dm.heightPixels;
    }

    private boolean shouldUseLargeTextFastOpen(@NonNull File file) {
        return file.isFile()
                && file.length() >= LARGE_TEXT_FAST_OPEN_THRESHOLD_BYTES
                && FileUtils.isTextFile(file.getName());
    }

    private static final class CachedRestoreTarget {
        final int charPosition;
        final int displayPage;
        final int totalPages;

        CachedRestoreTarget(int charPosition, int displayPage, int totalPages) {
            this.charPosition = Math.max(0, charPosition);
            this.displayPage = Math.max(0, displayPage);
            this.totalPages = Math.max(0, totalPages);
        }
    }

    private CachedRestoreTarget resolveInitialRestoreTarget(String loadedFilePath,
                                                            int jumpPosition,
                                                            int jumpDisplayPage,
                                                            int jumpTotalPages,
                                                            long fileLength) {
        if (jumpPosition >= 0) {
            return new CachedRestoreTarget(jumpPosition, jumpDisplayPage, jumpTotalPages);
        }

        if (prefs != null && prefs.getAutoSavePosition() && loadedFilePath != null) {
            ReaderState state = bookmarkManager != null ? bookmarkManager.getReadingState(loadedFilePath) : null;
            if (state != null) {
                boolean sameLength = state.getFileLength() <= 0L
                        || fileLength <= 0L
                        || state.getFileLength() == fileLength;
                int page = sameLength ? state.getPageNumber() : 0;
                int total = sameLength ? state.getTotalPages() : 0;
                return new CachedRestoreTarget(state.getCharPosition(), page, total);
            }
        }

        return new CachedRestoreTarget(0, 0, 0);
    }

    private float estimateBytesPerChar(@NonNull File file) {
        try {
            byte[] sample = readPreviewBytesAt(file, 0L, Math.min(128 * 1024, LARGE_TEXT_PREVIEW_BYTES));
            String decoded = decodePreviewBytes(file, sample);
            if (decoded == null || decoded.isEmpty()) return 1f;
            return Math.max(1f, sample.length / (float) Math.max(1, decoded.length()));
        } catch (Exception ignored) {
            return 1f;
        }
    }

    private long estimatePreviewStartByte(@NonNull File file, int restoreCharPosition, float estimatedBytesPerChar) {
        if (restoreCharPosition <= 0) return 0L;

        long targetByte = Math.round(restoreCharPosition * Math.max(1f, estimatedBytesPerChar));
        long contextBefore = LARGE_TEXT_PREVIEW_BYTES / 3L;
        long maxStart = Math.max(0L, file.length() - LARGE_TEXT_PREVIEW_BYTES);
        long start = Math.max(0L, targetByte - contextBefore);
        return Math.max(0L, Math.min(maxStart, start));
    }

    private int estimateCharOffsetForByte(long byteOffset, float estimatedBytesPerChar) {
        if (byteOffset <= 0L) return 0;
        return Math.max(0, Math.round(byteOffset / Math.max(1f, estimatedBytesPerChar)));
    }

    private byte[] readPreviewBytesAt(@NonNull File file, long startByte, int maxBytes) throws IOException {
        long clampedStart = Math.max(0L, Math.min(startByte, Math.max(0L, file.length())));
        int limit = (int) Math.max(1, Math.min(file.length() - clampedStart, maxBytes));
        ByteArrayOutputStream out = new ByteArrayOutputStream(limit);

        byte[] buffer = new byte[Math.min(64 * 1024, limit)];
        int remaining = limit;
        try (FileInputStream input = new FileInputStream(file)) {
            long skipped = 0L;
            while (skipped < clampedStart) {
                long n = input.skip(clampedStart - skipped);
                if (n <= 0L) {
                    if (input.read() < 0) break;
                    n = 1L;
                }
                skipped += n;
            }

            while (remaining > 0) {
                int read = input.read(buffer, 0, Math.min(buffer.length, remaining));
                if (read < 0) break;
                out.write(buffer, 0, read);
                remaining -= read;
            }
        }

        return out.toByteArray();
    }

    private String decodePreviewBytes(@NonNull File file, @NonNull byte[] data) throws IOException {
        String encoding = FileUtils.detectEncoding(file);
        try {
            CharsetDecoder decoder = Charset.forName(encoding)
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE);
            String decoded = decoder.decode(ByteBuffer.wrap(data)).toString();
            if (!decoded.isEmpty() && decoded.charAt(0) == '\uFEFF') {
                decoded = decoded.substring(1);
            }
            return FileUtils.enforceTextPresentationSelectors(decoded);
        } catch (Exception e) {
            throw new IOException("Cannot decode text preview", e);
        }
    }

    private void onLargeTextPreviewLoaded(String previewContent,
                                          int previewLineCount,
                                          String loadedFilePath,
                                          String loadedFileName,
                                          int jumpPosition,
                                          long fullByteLength,
                                          int previewByteCount,
                                          long previewStartByte,
                                          int previewBaseCharOffset,
                                          int estimatedTotalChars,
                                          boolean previewOnly,
                                          int cachedDisplayPage,
                                          int cachedTotalPages) {
        progressBar.setVisibility(View.GONE);
        progressText.setVisibility(View.GONE);

        filePath = loadedFilePath;
        fileName = loadedFileName != null ? loadedFileName : getString(R.string.app_name);

        if (getSupportActionBar() != null) getSupportActionBar().setTitle(fileName);

        fileContent = previewContent != null ? previewContent : "";
        totalChars = fileContent.length();
        totalLines = previewLineCount;
        largeTextEstimateActive = true;
        largeTextEstimatedTotalPages = 0;
        pendingLargeTextRestorePosition = -1;
        largeTextPreviewBaseCharOffset = Math.max(0, previewBaseCharOffset);
        largeTextEstimatedBasePageOffset = 0;
        largeTextEstimatedTotalChars = Math.max(fileContent.length(), estimatedTotalChars);
        hugeTextPreviewOnly = previewOnly;
        pendingLargeTextCachedDisplayPage = Math.max(0, cachedDisplayPage);
        pendingLargeTextCachedTotalPages = Math.max(0, cachedTotalPages);

        readerView.setTextContent(fileContent);
        applySearchHighlight();

        readerView.post(() -> {
            if (activityDestroyed) return;

            int restorePosition = -1;
            if (jumpPosition >= 0) {
                restorePosition = jumpPosition;
            } else if (prefs.getAutoSavePosition() && filePath != null) {
                ReaderState state = bookmarkManager.getReadingState(filePath);
                if (state != null) restorePosition = state.getCharPosition();
            }

            if (restorePosition >= 0) {
                int localRestorePosition = restorePosition - largeTextPreviewBaseCharOffset;
                if (localRestorePosition >= 0 && localRestorePosition < fileContent.length()) {
                    scrollToCharPosition(restorePosition);
                } else {
                    pendingLargeTextRestorePosition = restorePosition;
                }
            }

            int previewPages = Math.max(1, readerView.getTotalPageCount());
            float ratio = fullByteLength / (float) Math.max(1, previewByteCount);
            largeTextEstimatedTotalPages = Math.max(previewPages, Math.round(previewPages * ratio));
            largeTextEstimatedBasePageOffset = Math.max(0, Math.min(
                    Math.max(0, largeTextEstimatedTotalPages - 1),
                    Math.round((previewStartByte / (float) Math.max(1L, fullByteLength))
                            * largeTextEstimatedTotalPages)));

            if (pendingLargeTextCachedDisplayPage > 0) {
                int localPage = Math.max(1, readerView.getCurrentPageNumber());
                int cachedTotal = pendingLargeTextCachedTotalPages > 0
                        ? pendingLargeTextCachedTotalPages
                        : largeTextEstimatedTotalPages;
                largeTextEstimatedTotalPages = Math.max(localPage, cachedTotal);
                largeTextEstimatedBasePageOffset = Math.max(0,
                        Math.min(Math.max(0, largeTextEstimatedTotalPages - localPage),
                                pendingLargeTextCachedDisplayPage - localPage));
            }

            updatePositionLabel();
        });
    }

    private void onFileLoaded(String content, int lineCount,
                              String loadedFilePath,
                              String loadedFileName,
                              int jumpPosition) {
        boolean replacingLargePreview = largeTextEstimateActive;
        int preservePosition = replacingLargePreview ? getCurrentCharPosition() : -1;
        int deferredRestorePosition = pendingLargeTextRestorePosition;

        progressBar.setVisibility(View.GONE);
        progressText.setVisibility(View.GONE);

        filePath = loadedFilePath;
        fileName = loadedFileName != null ? loadedFileName : getString(R.string.app_name);

        if (getSupportActionBar() != null) getSupportActionBar().setTitle(fileName);

        fileContent = content != null ? content : "";
        totalChars = fileContent.length();
        totalLines = lineCount;
        largeTextEstimateActive = false;
        largeTextEstimatedTotalPages = 0;
        pendingLargeTextRestorePosition = -1;
        largeTextPreviewBaseCharOffset = 0;
        largeTextEstimatedBasePageOffset = 0;
        largeTextEstimatedTotalChars = 0;
        hugeTextPreviewOnly = false;
        pendingLargeTextCachedDisplayPage = 0;
        pendingLargeTextCachedTotalPages = 0;

        readerView.setTextContent(fileContent);
        applySearchHighlight();

        readerView.post(() -> {
            if (jumpPosition >= 0) {
                scrollToCharPosition(jumpPosition);
            } else if (deferredRestorePosition >= 0) {
                scrollToCharPosition(deferredRestorePosition);
            } else if (replacingLargePreview && preservePosition >= 0) {
                scrollToCharPosition(preservePosition);
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
        int total = Math.max(1, getDisplayedTotalPageCount());
        if (total <= 1) return 0;
        return Math.max(0, Math.min(100, Math.round(100f * (getDisplayedCurrentPageNumber() - 1) / (total - 1))));
    }

    private int getTotalPageCount() { return readerView != null ? readerView.getTotalPageCount() : 1; }
    private int getDisplayedTotalPageCount() {
        int exactLoadedPages = getTotalPageCount();
        if (largeTextEstimateActive && largeTextEstimatedTotalPages > exactLoadedPages) {
            return largeTextEstimatedTotalPages;
        }
        return exactLoadedPages;
    }
    private int getCurrentPageNumber() { return readerView != null ? readerView.getCurrentPageNumber() : 1; }
    private int getDisplayedCurrentPageNumber() {
        int localPage = getCurrentPageNumber();
        if (largeTextEstimateActive) {
            return Math.max(1, Math.min(getDisplayedTotalPageCount(),
                    largeTextEstimatedBasePageOffset + localPage));
        }
        return localPage;
    }
    private void scrollToPageNumber(int page) {
        if (readerView == null) return;

        int targetPage = page;
        if (largeTextEstimateActive) {
            targetPage = page - largeTextEstimatedBasePageOffset;
            if (targetPage < 1 || targetPage > getTotalPageCount()) {
                reloadLargeTextPreviewAround(estimateCharPositionForDisplayedPage(page));
                return;
            }
        }
        readerView.scrollToPage(targetPage);
    }

    private void updatePositionLabel() {
        if (readerView == null) return;
        int totalPages = getDisplayedTotalPageCount();
        int currentPage = getDisplayedCurrentPageNumber();
        setPageLabels(currentPage, totalPages);

        suppressSeekCallback = true;
        seekBar.setMax(Math.max(0, totalPages - 1));
        seekBar.setProgress(Math.max(0, Math.min(totalPages - 1, currentPage - 1)));
        suppressSeekCallback = false;
    }

    private void setPageLabels(int currentPage, int totalPages) {
        totalPages = Math.max(1, totalPages);
        currentPage = Math.max(1, Math.min(totalPages, currentPage));
        String totalText = (largeTextEstimateActive && largeTextEstimatedTotalPages > 0)
                ? "~" + totalPages
                : String.valueOf(totalPages);
        String text = String.format(Locale.getDefault(), "%d / %s", currentPage, totalText);
        if (positionLabel != null) positionLabel.setText(text);
        if (readerPageStatus != null) readerPageStatus.setText(text);
    }

    private void scrollToPercent(float percent) { if (readerView != null) readerView.scrollToPercent(percent); }
    private void scrollToCharPosition(int charPosition) {
        if (readerView != null) {
            int localPosition = largeTextEstimateActive
                    ? charPosition - largeTextPreviewBaseCharOffset
                    : charPosition;
            localPosition = Math.max(0, Math.min(fileContent != null ? fileContent.length() : 0, localPosition));
            readerView.scrollToCharPosition(localPosition);
            readerView.post(this::updatePositionLabel);
        }
    }

    private void jumpToAbsoluteCharPosition(int charPosition) {
        jumpToAbsoluteCharPosition(charPosition, 0, 0);
    }

    private void jumpToAbsoluteCharPosition(int charPosition, int displayPage, int totalPages) {
        if (!largeTextEstimateActive) {
            scrollToCharPosition(charPosition);
            return;
        }

        int localPosition = charPosition - largeTextPreviewBaseCharOffset;
        boolean insidePreview = localPosition >= 0
                && fileContent != null
                && localPosition < fileContent.length();

        if (insidePreview) {
            if (displayPage > 0) {
                int localPage = Math.max(1, readerView != null ? readerView.getCurrentPageNumber() : 1);
                int displayedTotal = totalPages > 0 ? totalPages : getDisplayedTotalPageCount();
                largeTextEstimatedTotalPages = Math.max(displayedTotal, localPage);
                largeTextEstimatedBasePageOffset = Math.max(0, displayPage - localPage);
            }
            scrollToCharPosition(charPosition);
            return;
        }

        reloadLargeTextPreviewAround(charPosition, displayPage, totalPages);
    }

    private void reloadLargeTextPreviewAround(int charPosition) {
        reloadLargeTextPreviewAround(charPosition, 0, 0);
    }

    private void reloadLargeTextPreviewAround(int charPosition, int displayPage, int totalPages) {
        if (filePath == null) return;
        Intent intent = new Intent(this, ReaderActivity.class);
        intent.putExtra(EXTRA_FILE_PATH, filePath);
        intent.putExtra(EXTRA_JUMP_TO_POSITION, Math.max(0, charPosition));
        intent.putExtra(EXTRA_JUMP_DISPLAY_PAGE, Math.max(0, displayPage));
        intent.putExtra(EXTRA_JUMP_TOTAL_PAGES, Math.max(0, totalPages));
        loadFileFromIntent(intent);
    }

    private int estimateCharPositionForDisplayedPage(int page) {
        int totalPages = Math.max(1, getDisplayedTotalPageCount());
        int totalChars = Math.max(1, largeTextEstimatedTotalChars);
        float ratio = (page - 1) / (float) Math.max(1, totalPages - 1);
        return Math.max(0, Math.min(totalChars - 1, Math.round(ratio * totalChars)));
    }

    private void applySearchHighlight() {
        if (readerView != null) {
            readerView.setSearchHighlight(activeSearchQuery, activeSearchIndex);
        }
    }

    private void handleSingleTap(float x, float y) {
        if (fileContent.isEmpty() || !prefs.getTapPagingEnabled()) {
            toggleToolbar();
            return;
        }

        int leadingPercent = Math.max(5, Math.min(80, prefs.getTapLeadingZonePercent()));
        int trailingPercent = Math.max(5, Math.min(80, prefs.getTapTrailingZonePercent()));
        if (leadingPercent + trailingPercent > 90) {
            trailingPercent = Math.max(5, 90 - leadingPercent);
        }

        float leadingRatio = leadingPercent / 100f;
        float trailingStartRatio = 1f - (trailingPercent / 100f);

        int tapZoneMode = prefs.getTapZoneMode();
        if (tapZoneMode == PrefsManager.TAP_ZONE_HORIZONTAL) {
            int width = readerView.getWidth();
            float leftBoundary = width * leadingRatio;
            float rightBoundary = width * trailingStartRatio;

            // Horizontal one-hand mode:
            // left zone -> previous page, middle zone -> menu, right zone -> next page.
            if (x < leftBoundary) {
                pageUp();
            } else if (x > rightBoundary) {
                pageDown();
            } else {
                toggleToolbar();
            }
            return;
        }

        int height = readerView.getHeight();
        float topBoundary = height * leadingRatio;
        float bottomBoundary = height * trailingStartRatio;

        // Vertical default mode:
        // top zone -> previous page, middle zone -> menu, bottom zone -> next page.
        if (y < topBoundary) {
            pageUp();
        } else if (y > bottomBoundary) {
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

    private int getCurrentCharPosition() {
        int localPosition = readerView != null ? readerView.getCurrentCharPosition() : 0;
        return largeTextEstimateActive
                ? Math.max(0, largeTextPreviewBaseCharOffset + localPosition)
                : localPosition;
    }

    private int getCurrentLineNumber() {
        int charPos = getCurrentCharPosition();
        return Math.max(1, countLinesUntilChar(charPos));
    }

    private String getExcerpt(int charPosition) {
        if (fileContent == null || fileContent.isEmpty()) return "";
        int localPosition = largeTextEstimateActive
                ? charPosition - largeTextPreviewBaseCharOffset
                : charPosition;
        int start = Math.max(0, Math.min(fileContent.length(), localPosition));
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
        int localPosition = largeTextEstimateActive
                ? charPosition - largeTextPreviewBaseCharOffset
                : charPosition;
        int end = Math.max(0, Math.min(fileContent.length(), localPosition));
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
        int bookmarkEndPosition = largeTextEstimateActive
                ? charPos + Math.max(1, excerpt.length())
                : Math.min(totalChars, charPos + Math.max(1, excerpt.length()));

        // Avoid stacking multiple identical bookmarks when the user taps save repeatedly
        // at almost the same location. Original Tek View-style bookmarks are position-based.
        List<Bookmark> existing = bookmarkManager.getBookmarksForFile(filePath);
        for (Bookmark b : existing) {
            if (Math.abs(b.getCharPosition() - charPos) <= 3) {
                b.setLineNumber(lineNum);
                b.setExcerpt(excerpt);
                b.setEndPosition(bookmarkEndPosition);
                b.setPageNumber(getDisplayedCurrentPageNumber());
                b.setTotalPages(getDisplayedTotalPageCount());
                bookmarkManager.updateBookmark(b);
                Toast.makeText(this, getString(R.string.bookmark_updated), Toast.LENGTH_SHORT).show();
                if (afterSave != null) afterSave.run();
                return;
            }
        }

        Bookmark bookmark = new Bookmark(filePath, fileName, charPos, lineNum, excerpt);
        bookmark.setEndPosition(bookmarkEndPosition);
        bookmark.setPageNumber(getDisplayedCurrentPageNumber());
        bookmark.setTotalPages(getDisplayedTotalPageCount());
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
        box.setBackgroundColor(Color.TRANSPARENT);
        int pad = dpToPx(12);
        // Keep content slightly inset inside the rounded outer frame.
        box.setPadding(pad, pad, pad, dpToPx(6));

        TextView currentInfo = new TextView(this);
        currentInfo.setTextColor(sub);
        currentInfo.setTextSize(12f);
        currentInfo.setGravity(Gravity.CENTER);
        currentInfo.setSingleLine(false);
        currentInfo.setIncludeFontPadding(false);
        currentInfo.setLineSpacing(0f, 1.08f);
        currentInfo.setPadding(0, 0, 0, dpToPx(8));
        box.addView(currentInfo, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView hint = new TextView(this);
        hint.setText(getString(R.string.bookmark_folder_hint));
        hint.setTextColor(sub);
        hint.setTextSize(12f);
        hint.setGravity(Gravity.CENTER);
        hint.setSingleLine(false);
        hint.setLineSpacing(0f, 1.08f);
        hint.setPadding(0, dpToPx(8), 0, dpToPx(6));
        box.addView(hint, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView saveButton = new TextView(this);
        saveButton.setText(getString(R.string.add_current_bookmark));
        saveButton.setGravity(Gravity.CENTER);
        saveButton.setTextColor(fg);
        saveButton.setTextSize(16f);
        saveButton.setTypeface(Typeface.DEFAULT_BOLD);
        saveButton.setPadding(0, dpToPx(12), 0, dpToPx(12));
        GradientDrawable saveBg = new GradientDrawable();
        boolean darkBookmarkDialog = isDarkColor(bg);
        int saveFill = blendColors(bg, fg, darkBookmarkDialog ? 0.135f : 0.085f);
        int saveStroke = blendColors(bg, fg, darkBookmarkDialog ? 0.460f : 0.360f);
        saveBg.setColor(saveFill);
        saveBg.setCornerRadius(dpToPx(14));
        saveBg.setStroke(Math.max(1, dpToPx(1)), saveStroke);
        saveButton.setBackground(saveBg);
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        saveLp.setMargins(0, 0, 0, dpToPx(2));
        box.addView(saveButton, saveLp);

        TextView emptyText = new TextView(this);
        emptyText.setText(getString(R.string.no_bookmarks_hint));
        emptyText.setTextColor(sub);
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setTextSize(14f);
        emptyText.setPadding(0, dpToPx(18), 0, dpToPx(18));

        RecyclerView rv = new RecyclerView(this);
        rv.setBackgroundColor(Color.TRANSPARENT);
        rv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        rv.setLayoutManager(new LinearLayoutManager(this));
        // Keep bookmark folder expand/collapse immediate instead of the slow default item animation.
        rv.setItemAnimator(null);

        BookmarkFolderAdapter adapter = new BookmarkFolderAdapter();
        adapter.setThemeColors(bg, fg, sub, panel);
        Set<String> expandedFolders = new HashSet<>();
        expandedFolders.add(filePath); // current file starts expanded

        rv.setAdapter(adapter);

        box.addView(emptyText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams bookmarkListLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(430));
        box.addView(rv, bookmarkListLp);

        final android.app.Dialog[] dialogRef = new android.app.Dialog[1];

        Runnable refresh = () -> {
            List<Bookmark> allBookmarks = bookmarkManager.getAllBookmarks();
            adapter.setBookmarks(allBookmarks, expandedFolders, filePath);
            // Keep the bookmark dialog height stable even when the list is empty.
            // This prevents the window from bouncing when the first bookmark is added.
            emptyText.setVisibility(View.GONE);
            rv.setVisibility(View.VISIBLE);
            LinearLayout.LayoutParams rvLp = (LinearLayout.LayoutParams) rv.getLayoutParams();
            if (rvLp != null && rvLp.height != dpToPx(430)) {
                rvLp.height = dpToPx(430);
                rv.setLayoutParams(rvLp);
            }
            currentInfo.setText(getString(R.string.all_bookmarks_status,
                    adapter.getFolderCount(),
                    allBookmarks.size(),
                    getCurrentPageNumber(),
                    getTotalPageCount()));

            // When the first bookmark is added, RecyclerView/content height can change.
            // Redraw the outer rounded overlay after the layout settles so the bottom
            // rounded border does not disappear until reopening the dialog.
            box.requestLayout();
            rv.requestLayout();
            if (dialogRef[0] != null) {
                redrawDialogOuterBorder(dialogRef[0], strongDialogBorderColor(bg));
            }
        };

        TextView title = makeReaderDialogTitle(getString(R.string.bookmark), bg, fg);
        title.setGravity(Gravity.CENTER);
        title.setIncludeFontPadding(false);
        title.setPadding(dpToPx(22), dpToPx(12), dpToPx(22), 0);
        LinearLayout dialogPanel = new LinearLayout(this);
        dialogPanel.setOrientation(LinearLayout.VERTICAL);
        dialogPanel.setBackgroundColor(Color.TRANSPARENT);
        dialogPanel.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        dialogPanel.addView(box, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView closeButton = makeReaderDialogActionText(getString(R.string.close), sub, Gravity.CENTER);
        dialogPanel.addView(closeButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(50)));

        // Fill the bookmark list before the dialog is shown.  This keeps the
        // initial content height stable, so the bottom-positioned window does not
        // appear first and then jump/land after the adapter refresh.
        refresh.run();

        android.app.Dialog dialog = createPositionedReaderDialog(dialogPanel, bg,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 34, 14, 460, false);
        dialogRef[0] = dialog;
        closeButton.setOnClickListener(v -> dialog.dismiss());

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
            public void onFolderDelete(String folderFilePath, String expansionKey, String folderName, int bookmarkCount) {
                showBookmarkFolderDeleteConfirm(folderFilePath, folderName, bookmarkCount, () -> {
                    expandedFolders.remove(folderFilePath);
                    expandedFolders.remove(expansionKey);
                    refresh.run();
                });
            }

            @Override
            public void onBookmarkClick(Bookmark b) {
                if (b.getFilePath() != null && b.getFilePath().equals(filePath)) {
                    jumpToAbsoluteCharPosition(b.getCharPosition(), b.getPageNumber(), b.getTotalPages());
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

                Intent intent;
                if (FileUtils.isPdfFile(targetFile.getName())) {
                    intent = new Intent(ReaderActivity.this, PdfReaderActivity.class);
                    intent.putExtra(PdfReaderActivity.EXTRA_FILE_PATH, b.getFilePath());
                    intent.putExtra(PdfReaderActivity.EXTRA_JUMP_TO_PAGE, b.getCharPosition());
                } else if (FileUtils.isEpubFile(targetFile.getName()) || FileUtils.isWordFile(targetFile.getName())) {
                    intent = new Intent(ReaderActivity.this, DocumentPageActivity.class);
                    intent.putExtra(DocumentPageActivity.EXTRA_FILE_PATH, b.getFilePath());
                    intent.putExtra(DocumentPageActivity.EXTRA_JUMP_TO_PAGE, b.getCharPosition());
                } else {
                    intent = new Intent(ReaderActivity.this, ReaderActivity.class);
                    intent.putExtra(EXTRA_FILE_PATH, b.getFilePath());
                    intent.putExtra(EXTRA_JUMP_TO_POSITION, b.getCharPosition());
                    intent.putExtra(EXTRA_JUMP_DISPLAY_PAGE, b.getPageNumber());
                    intent.putExtra(EXTRA_JUMP_TOTAL_PAGES, b.getTotalPages());
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                dialog.dismiss();
            }

            @Override
            public void onBookmarkDelete(Bookmark b) {
                showBookmarkDeleteConfirm(b, refresh);
            }

            @Override
            public void onBookmarkEdit(Bookmark b) {
                showBookmarkMemoEditDialog(b, refresh);
            }
        });

        dialog.show();
    }


    private void showBookmarkFolderDeleteConfirm(String folderFilePath, String folderName, int bookmarkCount, Runnable afterDelete) {
        final int bg = readerDialogBgColor();
        final int fg = readerDialogTextColor(bg);
        final int sub = readerDialogSubTextColor(bg);
        final int danger = isLightColor(bg) ? Color.rgb(95, 35, 35) : Color.rgb(255, 170, 170);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Color.TRANSPARENT);

        TextView title = makeReaderDialogTitle(getString(R.string.delete_bookmark_folder), bg, fg);
        panel.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(Color.TRANSPARENT);
        box.setPadding(dpToPx(22), dpToPx(12), dpToPx(22), dpToPx(8));

        TextView message = new TextView(this);
        String displayName = folderName != null && !folderName.trim().isEmpty() ? folderName.trim() : getString(R.string.bookmark);
        message.setText(displayName + "\n\n"
                + getString(R.string.delete_bookmark_folder_message, bookmarkCount)
                + "\n" + getString(R.string.delete_bookmark_folder_note));
        message.setTextColor(fg);
        message.setTextSize(14f);
        message.setLineSpacing(0f, 1.15f);
        message.setPadding(0, dpToPx(4), 0, dpToPx(8));
        box.addView(message, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        panel.addView(box, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        actions.setBackground(actionPanelBackground(
                dialogActionPanelFillColor(bg), dialogActionPanelLineColor(bg)));
        actions.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        TextView cancel = makeReaderDialogActionText(getString(R.string.cancel), sub, Gravity.CENTER);
        TextView delete = makeReaderDialogActionText(getString(R.string.delete), danger, Gravity.CENTER);
        actions.addView(cancel, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(46)));
        actions.addView(delete, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(46)));
        panel.addView(actions, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        android.app.Dialog dialog = createPositionedReaderDialog(panel, bg,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 74, 14, 460, false);
        cancel.setOnClickListener(v -> dialog.dismiss());
        delete.setOnClickListener(v -> {
            bookmarkManager.deleteBookmarksForFile(folderFilePath);
            if (afterDelete != null) afterDelete.run();
            dialog.dismiss();
        });
        dialog.show();
    }

    private void showBookmarkMemoEditDialog(Bookmark bookmark, Runnable afterSave) {
        final int bg = readerDialogBgColor();
        final int fg = readerDialogTextColor(bg);
        final int sub = readerDialogSubTextColor(bg);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Color.TRANSPARENT);

        TextView title = makeReaderDialogTitle(getString(R.string.edit_bookmark_memo), bg, fg);
        panel.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(Color.TRANSPARENT);
        box.setPadding(dpToPx(22), dpToPx(12), dpToPx(22), dpToPx(8));

        TextView message = new TextView(this);
        message.setText(bookmark.getFileName() + "\n\n" + bookmark.getExcerpt());
        message.setTextColor(sub);
        message.setTextSize(13f);
        message.setLineSpacing(0f, 1.15f);
        message.setPadding(0, 0, 0, dpToPx(10));
        box.addView(message, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        EditText input = makeReaderDialogEditText(getString(R.string.optional_memo), bg, fg, sub);
        input.setText(bookmark.getLabel());
        input.setSelectAllOnFocus(true);
        box.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(52)));
        panel.addView(box, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        actions.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        TextView cancel = makeReaderDialogActionText(getString(R.string.cancel), sub, Gravity.CENTER);
        TextView clear = makeReaderDialogActionText(getString(R.string.clear_memo), sub, Gravity.CENTER);
        TextView save = makeReaderDialogActionText(getString(R.string.save), fg, Gravity.CENTER);
        actions.addView(cancel, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(46)));
        actions.addView(clear, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(46)));
        actions.addView(save, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(46)));
        panel.addView(actions, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        android.app.Dialog dialog = createPositionedReaderDialog(panel, bg,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 74, 14, 460, true);
        cancel.setOnClickListener(v -> dialog.dismiss());
        clear.setOnClickListener(v -> {
            bookmark.setLabel("");
            bookmarkManager.updateBookmark(bookmark);
            if (afterSave != null) afterSave.run();
            dialog.dismiss();
        });
        save.setOnClickListener(v -> {
            bookmark.setLabel(input.getText().toString().trim());
            bookmarkManager.updateBookmark(bookmark);
            if (afterSave != null) afterSave.run();
            dialog.dismiss();
        });
        dialog.show();
    }

    // --- Brightness ---


    private void showBrightnessDialog() {
        final int bg = readerDialogBgColor();
        final int fg = readerDialogTextColor(bg);
        final int sub = readerDialogSubTextColor(bg);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Color.TRANSPARENT);

        TextView title = makeReaderDialogTitle(getString(R.string.brightness), bg, fg);
        panel.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(Color.TRANSPARENT);
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

        panel.addView(box, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        actionRow.setBackground(positionedActionPanelBackground(
                dialogActionPanelFillColor(bg),
                dialogActionPanelLineColor(bg)));
        actionRow.setPadding(dpToPx(30), 0, dpToPx(30), 0);

        TextView ok = makeReaderDialogActionText(getString(R.string.ok), fg,
                Gravity.CENTER_VERTICAL | Gravity.END);
        actionRow.addView(ok, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f));
        panel.addView(actionRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(54)));

        android.app.Dialog dialog = createNarrowPositionedReaderDialog(
                panel,
                bg,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL,
                88,
                0.85f,
                360,
                false);
        panel.setClipChildren(true);
        panel.setClipToPadding(true);

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

        ok.setOnClickListener(v -> dialog.dismiss());
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
        list.setBackgroundColor(Color.TRANSPARENT);
        int pad = dpToPx(14);
        list.setPadding(pad, dpToPx(8), pad, dpToPx(8));

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.TRANSPARENT);
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
        showFontPickerDialog(getReadingFontOptions());
    }

    private static final class ReadingFontOption {
        final String value;
        final String englishLabel;
        final String koreanLabel;

        ReadingFontOption(String value, String englishLabel, String koreanLabel) {
            this.value = value;
            this.englishLabel = englishLabel;
            this.koreanLabel = koreanLabel;
        }
    }

    private ReadingFontOption[] getReadingFontOptions() {
        List<ReadingFontOption> options = new ArrayList<>();

        options.add(new ReadingFontOption("default",
                "System Sans (recommended)",
                "시스템 산세리프 (추천)"));
        options.add(new ReadingFontOption(FONT_OPTION_SYSTEM_CURRENT,
                "Current system font",
                "현재 시스템 글꼴"));
        options.add(new ReadingFontOption("korean_sans",
                "Korean/System Sans",
                "한글 산세리프"));
        options.add(new ReadingFontOption("korean_serif",
                "Korean/System Serif",
                "한글 명조/세리프"));
        options.add(new ReadingFontOption("serif",
                "Serif",
                "세리프"));
        options.add(new ReadingFontOption("monospace",
                "Monospace",
                "고정폭"));
        options.add(new ReadingFontOption("sans_medium",
                "Sans Medium",
                "산세리프 미디엄"));
        options.add(new ReadingFontOption("sans_condensed",
                "Sans Condensed",
                "산세리프 압축"));
        options.add(new ReadingFontOption("sans_light",
                "Sans Light",
                "산세리프 라이트"));

        addUserFontOptions(options);


        String current = normalizeReadingFontValue(prefs != null ? prefs.getFontFamily() : "default");
        if (!isCuratedReadingFontValue(current) && !containsReadingFontOption(options, current)) {
            if (FontManager.isSystemFamilyValue(current)) {
                String familyName = FontManager.getSystemFamilyName(current);
                options.add(new ReadingFontOption(current,
                        "Saved system font: " + familyName,
                        "저장된 시스템 글꼴: " + familyName));
            } else {
                options.add(new ReadingFontOption(current,
                        "Installed/Custom: " + current,
                        "설치/사용자 글꼴: " + current));
            }
        }

        return options.toArray(new ReadingFontOption[0]);
    }

    private void addUserFontOptions(@NonNull List<ReadingFontOption> options) {
        try {
            FontManager fontManager = FontManager.getInstance();
            if (!fontManager.isScanned()) {
                fontManager.scanFontsSync(this);
            }

            for (String fontName : fontManager.getUserAddedFontNames(this)) {
                if (fontName == null || fontName.trim().isEmpty()) continue;
                String value = normalizeReadingFontValue(fontName);
                if (isCuratedReadingFontValue(value) || containsReadingFontOption(options, value)) continue;
                options.add(new ReadingFontOption(value,
                        "Added font: " + fontName,
                        "추가한 글꼴: " + fontName));
            }
        } catch (Throwable ignored) {
            // Font scanning should never block opening the font picker.
        }
    }

    private boolean containsReadingFontOption(@NonNull List<ReadingFontOption> options, String value) {
        for (ReadingFontOption option : options) {
            if (option.value.equals(value)) return true;
        }
        return false;
    }

    private boolean isCuratedReadingFontValue(String value) {
        switch (normalizeReadingFontValue(value)) {
            case "default":
            case "system_current":
            case "korean_sans":
            case "korean_serif":
            case "serif":
            case "monospace":
            case "sans_medium":
            case "sans_condensed":
            case "sans_light":
                return true;
            default:
                return false;
        }
    }

    private String getReadingFontLabel(@NonNull ReadingFontOption option) {
        String lang = Locale.getDefault().getLanguage();
        return "ko".equalsIgnoreCase(lang) ? option.koreanLabel : option.englishLabel;
    }

    private String normalizeReadingFontValue(String fontName) {
        if (fontName == null || fontName.trim().isEmpty()) return "default";

        String trimmed = fontName.trim();
        if (FontManager.isSystemFamilyValue(trimmed)) return trimmed;

        switch (trimmed) {
            case "Default (Sans-serif)":
            case "DEFAULT":
                return "default";
            case "Serif":
            case "SERIF":
                return "serif";
            case "Monospace":
            case "MONOSPACE":
                return "monospace";
            case "default":
            case "system_current":
            case "korean_sans":
            case "korean_serif":
            case "serif":
            case "monospace":
            case "sans_medium":
            case "sans_condensed":
            case "sans_light":
                return trimmed;
            default:
                return trimmed;
        }
    }

    private Typeface resolveReadingTypeface(String fontName) {
        String value = normalizeReadingFontValue(fontName);

        switch (value) {
            case "default":
            case "system_current":
                return Typeface.DEFAULT;
            case "korean_sans":
                // Android's system sans family uses the device CJK fallback chain,
                // so Korean glyphs are handled by Noto/Samsung CJK fonts without
                // exposing dozens of raw system font files to the user.
                return Typeface.create("sans-serif", Typeface.NORMAL);
            case "korean_serif":
                return Typeface.create("serif", Typeface.NORMAL);
            case "serif":
                return Typeface.SERIF;
            case "monospace":
                return Typeface.MONOSPACE;
            case "sans_medium":
                return Typeface.create("sans-serif-medium", Typeface.NORMAL);
            case "sans_condensed":
                return Typeface.create("sans-serif-condensed", Typeface.NORMAL);
            case "sans_light":
                return Typeface.create("sans-serif-light", Typeface.NORMAL);
            default:
                // Backward compatibility for a previously saved imported/scanned font.
                return FontManager.getInstance().getTypeface(value);
        }
    }

    private void constrainDialogScrollArea(@NonNull View scrollContainer, @NonNull ViewGroup contentList) {
        scrollContainer.setClipToOutline(false);
        if (scrollContainer instanceof ScrollView) {
            ScrollView scrollView = (ScrollView) scrollContainer;
            scrollView.setClipToPadding(true);
            scrollView.setFillViewport(false);
            scrollView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        }
        if (scrollContainer instanceof ViewGroup) {
            ViewGroup scrollGroup = (ViewGroup) scrollContainer;
            scrollGroup.setClipChildren(true);
            scrollGroup.setClipToPadding(true);
        }

        contentList.setClipChildren(true);
        contentList.setClipToPadding(true);
    }

    private Drawable fontDialogHeaderBackground(int bgColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(bgColor);
        // The outer font-dialog frame owns the rounded corners and border.
        // Keep the header rectangular so the top border is not doubled or clipped.
        drawable.setCornerRadius(0f);
        return drawable;
    }

    private View fontDialogHeaderSeparator(int bgColor) {
        View separator = new View(this);
        separator.setBackgroundColor(dialogActionPanelLineColor(bgColor));
        separator.setClickable(false);
        separator.setFocusable(false);
        return separator;
    }

    private View fontDialogBottomSeparator(int bgColor) {
        View separator = new View(this);
        separator.setBackgroundColor(dialogActionPanelLineColor(bgColor));
        separator.setClickable(false);
        separator.setFocusable(false);
        return separator;
    }

    private FrameLayout makeClippedDialogScrollFrame(@NonNull ScrollView scroll,
                                                     @NonNull ViewGroup contentList,
                                                     int bgColor) {
        constrainDialogScrollArea(scroll, contentList);

        FrameLayout clipFrame = new FrameLayout(this);
        clipFrame.setBackgroundColor(bgColor);
        clipFrame.setClipChildren(true);
        clipFrame.setClipToPadding(true);
        clipFrame.setOverScrollMode(View.OVER_SCROLL_NEVER);

        scroll.setBackgroundColor(bgColor);
        scroll.setFillViewport(false);
        scroll.setClipChildren(true);
        scroll.setClipToPadding(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setVerticalFadingEdgeEnabled(false);
        scroll.setPadding(0, 0, 0, 0);

        contentList.setBackgroundColor(bgColor);
        contentList.setClipChildren(true);
        contentList.setClipToPadding(true);
        scroll.addView(contentList, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        clipFrame.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        return clipFrame;
    }

    private void preserveFontDialogHeaderBarrier(@NonNull ViewGroup panel, @NonNull View scrollClip) {
        // The font rows were bleeding over the fixed header because the dialog panel
        // was allowed to draw children outside their own bounds. Keep clipping enabled
        // on the panel and on the scroll viewport; the outer rounded frame/background is
        // drawn by createPositionedReaderDialog(), so this does not cut the dialog border.
        panel.setClipChildren(true);
        panel.setClipToPadding(true);

        scrollClip.setClipToOutline(false);
        if (scrollClip instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) scrollClip;
            group.setClipChildren(true);
            group.setClipToPadding(true);
        }
    }

    private Drawable fontDialogFrameBackground(int bgColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(bgColor);
        drawable.setCornerRadius(dpToPx(16));
        return drawable;
    }

    private Drawable fontDialogFrameBorderOverlay(int bgColor) {
        final int borderColor = strongDialogBorderColor(bgColor);
        // Match the 2dp outer boundary used by the other stable reader dialogs.
        // The font picker and full-system-font dialog share this frame, so this
        // keeps both windows from looking thinner than More/Page/Bookmark.
        final float strokeWidth = Math.max(1f, dpToPx(2));
        final float radius = dpToPx(16);
        return new Drawable() {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF rect = new RectF();

            {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(strokeWidth);
                paint.setColor(borderColor);
            }

            @Override
            public void draw(Canvas canvas) {
                Rect bounds = getBounds();
                float half = strokeWidth / 2f;
                rect.set(bounds.left + half, bounds.top + half,
                        bounds.right - half, bounds.bottom - half);
                canvas.drawRoundRect(rect, Math.max(0f, radius - half),
                        Math.max(0f, radius - half), paint);
            }

            @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
            @Override public void setColorFilter(ColorFilter colorFilter) { paint.setColorFilter(colorFilter); }
            @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        };
    }

    private Drawable fontDialogActionPanelBackground(int bgColor) {
        GradientDrawable drawable = new GradientDrawable();
        // Use the same fill as the main dialog. A different bottom fill created
        // the visible blended strip between the frame and the action bar.
        drawable.setColor(bgColor);
        drawable.setCornerRadius(0f);
        return drawable;
    }

    private android.app.Dialog createReaderFontDialog(@NonNull View content, int bgColor, int maxWidthDp) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        FrameLayout outerFrame = new FrameLayout(this);
        outerFrame.setBackground(fontDialogFrameBackground(bgColor));
        outerFrame.setForeground(fontDialogFrameBorderOverlay(bgColor));
        outerFrame.setClipChildren(true);
        outerFrame.setClipToPadding(true);
        outerFrame.setClipToOutline(true);
        outerFrame.setPadding(0, 0, 0, 0);

        content.setBackgroundColor(Color.TRANSPARENT);
        if (content instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) content;
            group.setClipChildren(true);
            group.setClipToPadding(true);
        }

        outerFrame.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));

        dialog.setContentView(outerFrame);

        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.copyFrom(window.getAttributes());
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int cappedWidth = Math.min(Math.round(screenWidth * 0.85f), dpToPx(maxWidthDp));
            lp.width = Math.max(dpToPx(220), cappedWidth);
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            lp.y = dpToPx(88);
            lp.dimAmount = 0.16f;
            window.setAttributes(lp);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        return dialog;
    }

    private void showFontPickerDialog(ReadingFontOption[] fontOptions) {
        final int bg = readerDialogBgColor();
        final int fg = readerDialogTextColor(bg);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Color.TRANSPARENT);
        panel.setClipChildren(true);
        panel.setClipToPadding(true);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackground(fontDialogHeaderBackground(bg));
        header.setClipChildren(true);
        header.setClipToPadding(true);

        TextView title = makeReaderFontDialogTitle(getString(R.string.select_font), bg, fg);
        title.setBackgroundColor(Color.TRANSPARENT);
        title.setPadding(title.getPaddingLeft(), title.getPaddingTop(),
                title.getPaddingRight(), dpToPx(18));
        header.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        panel.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        panel.addView(fontDialogHeaderSeparator(bg), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.max(1, dpToPx(1))));

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setBackgroundColor(Color.TRANSPARENT);
        int pad = dpToPx(14);
        list.setPadding(pad, dpToPx(8), pad, dpToPx(8));

        ScrollView scroll = new ScrollView(this);
        FrameLayout scrollClip = makeClippedDialogScrollFrame(scroll, list, bg);
        panel.addView(scrollClip, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.min(dpToPx(360), getResources().getDisplayMetrics().heightPixels / 2)));

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        actionRow.setBackground(fontDialogActionPanelBackground(bg));
        actionRow.setPadding(dpToPx(30), 0, dpToPx(30), 0);

        TextView addFont = makeReaderDialogActionText(
                localizedText("Add font", "글꼴 추가"),
                fg,
                Gravity.CENTER_VERTICAL | Gravity.START);
        TextView cancel = makeReaderDialogActionText(getString(R.string.cancel), fg,
                Gravity.CENTER_VERTICAL | Gravity.END);

        actionRow.addView(addFont, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f));
        actionRow.addView(cancel, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f));
        panel.addView(fontDialogBottomSeparator(bg), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.max(1, dpToPx(1))));
        panel.addView(actionRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(54)));

        android.app.Dialog dialog = createReaderFontDialog(panel, bg, 460);
        preserveFontDialogHeaderBarrier(panel, scrollClip);

        String currentFont = normalizeReadingFontValue(prefs.getFontFamily());
        for (ReadingFontOption option : fontOptions) {
            String label = getReadingFontLabel(option);
            boolean selected = option.value.equals(currentFont);

            TextView row = makeReaderFontActionRow(label, fg, selected);
            row.setOnClickListener(v -> {
                dialog.dismiss();
                prefs.setFontFamily(option.value);
                applyPreferences();
                updatePositionLabel();
            });
            if (isRemovableUserFontValue(option.value)) {
                row.setOnLongClickListener(v -> {
                    showUserFontRemoveConfirm(option.value, label, () -> {
                        dialog.dismiss();
                        showFontDialog();
                    });
                    return true;
                });
            }
            list.addView(row);
        }

        addFont.setOnClickListener(v -> {
            dialog.dismiss();
            showAllSystemFontsDialog();
        });
        cancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showAllSystemFontsDialog() {
        final int bg = readerDialogBgColor();
        final int fg = readerDialogTextColor(bg);
        final int sub = readerDialogSubTextColor(bg);

        List<String> fontNames = new ArrayList<>();
        try {
            FontManager fontManager = FontManager.getInstance();
            fontManager.scanFontsSync(this);
            fontNames.addAll(fontManager.getFontNames());
        } catch (Throwable ignored) {
            // Keep the dialog usable even if a device blocks one of the font paths.
        }

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Color.TRANSPARENT);
        panel.setClipChildren(true);
        panel.setClipToPadding(true);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackground(fontDialogHeaderBackground(bg));
        header.setClipChildren(true);
        header.setClipToPadding(true);

        TextView title = makeReaderFontDialogTitle(
                localizedText("All system fonts", "전체 시스템 글꼴"),
                bg,
                fg);
        title.setBackgroundColor(Color.TRANSPARENT);
        title.setPadding(title.getPaddingLeft(), title.getPaddingTop(),
                title.getPaddingRight(), dpToPx(8));
        header.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView hint = makeReaderDialogLabel(
                localizedText(
                        "Select a font found from Android/system font folders.",
                        "Android/시스템 글꼴 폴더에서 찾은 글꼴을 선택합니다."),
                sub,
                12f);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(dpToPx(18), dpToPx(4), dpToPx(18), dpToPx(16));
        hint.setBackgroundColor(Color.TRANSPARENT);
        header.addView(hint, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        panel.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        panel.addView(fontDialogHeaderSeparator(bg), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.max(1, dpToPx(1))));

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setBackgroundColor(Color.TRANSPARENT);
        int pad = dpToPx(14);
        list.setPadding(pad, dpToPx(8), pad, dpToPx(8));

        ScrollView scroll = new ScrollView(this);
        FrameLayout scrollClip = makeClippedDialogScrollFrame(scroll, list, bg);
        panel.addView(scrollClip, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.min(dpToPx(420), getResources().getDisplayMetrics().heightPixels / 2)));

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        actionRow.setBackground(fontDialogActionPanelBackground(bg));
        actionRow.setPadding(dpToPx(30), 0, dpToPx(30), 0);

        TextView cancel = makeReaderDialogActionText(getString(R.string.cancel), fg,
                Gravity.CENTER_VERTICAL | Gravity.END);
        actionRow.addView(cancel, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f));
        panel.addView(fontDialogBottomSeparator(bg), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.max(1, dpToPx(1))));
        panel.addView(actionRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(54)));

        android.app.Dialog dialog = createReaderFontDialog(panel, bg, 460);
        preserveFontDialogHeaderBarrier(panel, scrollClip);

        String currentFont = normalizeReadingFontValue(prefs.getFontFamily());
        if (fontNames.isEmpty()) {
            TextView empty = makeReaderDialogLabel(
                    localizedText("No readable system fonts found.", "읽을 수 있는 시스템 글꼴을 찾지 못했습니다."),
                    sub,
                    14f);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dpToPx(12), dpToPx(16), dpToPx(12), dpToPx(16));
            list.addView(empty, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        } else {
            for (String fontName : fontNames) {
                if (fontName == null || fontName.trim().isEmpty()) continue;

                String value = normalizeReadingFontValue(fontName);
                String label = fontName;
                boolean selected = value.equals(currentFont);

                TextView row = makeReaderFontActionRow(label, fg, selected);
                row.setOnClickListener(v -> {
                    try {
                        FontManager.getInstance().addUserFont(this, fontName);
                    } catch (Throwable ignored) {
                        // Selecting the font should still work even if persisting the shortcut fails.
                    }
                    dialog.dismiss();
                    prefs.setFontFamily(value);
                    applyPreferences();
                    updatePositionLabel();
                });
                list.addView(row);
            }
        }

        cancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private boolean isRemovableUserFontValue(String value) {
        try {
            FontManager fontManager = FontManager.getInstance();
            if (!fontManager.isScanned()) fontManager.scanFontsSync(this);
            return fontManager.isRemovableUserFont(this, value);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void showUserFontRemoveConfirm(String value, String label, Runnable afterRemove) {
        final int bg = readerDialogBgColor();
        final int fg = readerDialogTextColor(bg);
        final int sub = readerDialogSubTextColor(bg);
        final int danger = isLightColor(bg) ? Color.rgb(95, 35, 35) : Color.rgb(255, 170, 170);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Color.TRANSPARENT);

        TextView title = makeReaderDialogTitle(localizedText("Remove font", "글꼴 삭제"), bg, fg);
        panel.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(Color.TRANSPARENT);
        box.setPadding(dpToPx(22), dpToPx(10), dpToPx(22), dpToPx(10));

        TextView message = new TextView(this);
        String safeLabel = label != null && !label.trim().isEmpty() ? label.trim() : value;
        message.setText(safeLabel + "\n\n" + localizedText(
                "Remove this user-added font from TextView Reader?",
                "이 사용자 추가 글꼴을 TextView Reader에서 삭제할까요?")
                + "\n" + localizedText(
                "System fonts and document files are not affected.",
                "시스템 글꼴과 문서 파일은 영향받지 않습니다."));
        message.setTextColor(fg);
        message.setTextSize(14f);
        message.setLineSpacing(0f, 1.15f);
        message.setPadding(0, dpToPx(4), 0, dpToPx(8));
        box.addView(message, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        panel.addView(box, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        actions.setBackground(actionPanelBackground(
                dialogActionPanelFillColor(bg), dialogActionPanelLineColor(bg)));
        actions.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        TextView cancel = makeReaderDialogActionText(getString(R.string.cancel), sub, Gravity.CENTER);
        TextView delete = makeReaderDialogActionText(getString(R.string.delete), danger, Gravity.CENTER);
        actions.addView(cancel, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(46)));
        actions.addView(delete, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(46)));
        panel.addView(actions, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        android.app.Dialog dialog = createPositionedReaderDialog(panel, bg,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 74, 14, 460, false);
        cancel.setOnClickListener(v -> dialog.dismiss());
        delete.setOnClickListener(v -> {
            boolean removed = false;
            try {
                FontManager fontManager = FontManager.getInstance();
                if (!fontManager.isScanned()) fontManager.scanFontsSync(this);
                removed = fontManager.removeUserFont(this, value);
            } catch (Throwable ignored) {
                removed = false;
            }

            if (removed) {
                if (normalizeReadingFontValue(prefs.getFontFamily()).equals(normalizeReadingFontValue(value))) {
                    prefs.setFontFamily("default");
                    applyPreferences();
                    updatePositionLabel();
                }
                Toast.makeText(this, localizedText("Font removed", "글꼴을 삭제했습니다"), Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                if (afterRemove != null) afterRemove.run();
            } else {
                Toast.makeText(this, localizedText(
                        "This font cannot be removed from inside the app.",
                        "이 글꼴은 앱 안에서 삭제할 수 없습니다."), Toast.LENGTH_SHORT).show();
            }
        });
        dialog.show();
    }

    private String localizedText(String english, String korean) {
        return "ko".equalsIgnoreCase(Locale.getDefault().getLanguage()) ? korean : english;
    }

    // --- Go To / Search ---

    private void showGoToDialog() {
        final int bg = readerDialogBgColor();
        final int fg = readerDialogTextColor(bg);
        final int sub = readerDialogSubTextColor(bg);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(Color.TRANSPARENT);
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
        titleBox.setBackgroundColor(Color.TRANSPARENT);

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
        box.setBackgroundColor(Color.TRANSPARENT);
        box.setPadding(dpToPx(24), dpToPx(12), dpToPx(24), dpToPx(8));

        EditText input = makeReaderDialogEditText(getString(R.string.search_text_hint), bg, fg, sub);
        String rememberedQuery = activeSearchQuery;
        if ((rememberedQuery == null || rememberedQuery.isEmpty()) && prefs != null) {
            rememberedQuery = prefs.getLastReaderSearchQuery();
        }
        if (rememberedQuery == null) rememberedQuery = "";
        input.setText(rememberedQuery);
        if (!rememberedQuery.isEmpty()) {
            input.setSelection(input.getText().length());
            int total = countTextMatches(rememberedQuery);
            int ordinal = activeSearchIndex >= 0 ? matchIndexForPosition(rememberedQuery, activeSearchIndex) : 0;
            matchStatus.setText(String.format(Locale.getDefault(), "%d / %d", ordinal, total));
        }
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

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Color.TRANSPARENT);
        panel.addView(titleBox, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        panel.addView(box, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        android.app.Dialog dialog = createNarrowPositionedReaderDialog(
                panel,
                bg,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL,
                88,
                0.85f,
                460,
                true);

        // Keep the find dialog at one stable position. Do not move it in response
        // to keyboard visibility; the previous delayed restore caused bounce.
        updatePositionedReaderDialogYOffset(dialog, 88);

        prevButton.setOnClickListener(v -> performTextSearchMove(
                input.getText().toString(), false, matchStatus));
        nextButton.setOnClickListener(v -> performTextSearchMove(
                input.getText().toString(), true, matchStatus));
        closeButton.setOnClickListener(v -> dialog.dismiss());

        input.setOnEditorActionListener((v, actionId, event) -> {
            performTextSearchMove(input.getText().toString(), true, matchStatus);
            return true;
        });

        dialog.setOnDismissListener(d -> {
            if (prefs != null) {
                prefs.setLastReaderSearchQuery(input.getText() != null ? input.getText().toString() : "");
            }
            activeSearchQuery = "";
            activeSearchIndex = -1;
            applySearchHighlight();
        });
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
            if (prefs != null) prefs.setLastReaderSearchQuery("");
            activeSearchQuery = "";
            activeSearchIndex = -1;
            applySearchHighlight();
            if (matchStatus != null) matchStatus.setText("0 / 0");
            Toast.makeText(this, getString(R.string.enter_search_text), Toast.LENGTH_SHORT).show();
            return;
        }

        if (fileContent == null || fileContent.isEmpty()) return;

        int total = countTextMatches(query);
        if (total <= 0) {
            if (prefs != null) prefs.setLastReaderSearchQuery(query);
            activeSearchQuery = query;
            activeSearchIndex = -1;
            applySearchHighlight();
            if (matchStatus != null) matchStatus.setText("0 / 0");
            Toast.makeText(this, getString(R.string.not_found), Toast.LENGTH_SHORT).show();
            return;
        }

        if (!query.equals(activeSearchQuery)) {
            activeSearchQuery = query;
            activeSearchIndex = -1;
            applySearchHighlight();
        }
        if (prefs != null) prefs.setLastReaderSearchQuery(query);

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
            applySearchHighlight();

            // Search movement should land on the actual match line, not keep reusing the
            // current top line as the next search base.
            scrollToCharPosition(idx);
            updatePositionLabel();

            int ordinal = matchIndexForPosition(query, idx);
            if (matchStatus != null) {
                matchStatus.setText(String.format(Locale.getDefault(), "%d / %d", ordinal, total));
            }

        } else {
            activeSearchIndex = -1;
            applySearchHighlight();
            if (matchStatus != null) matchStatus.setText(String.format(Locale.getDefault(), "0 / %d", total));
            Toast.makeText(this, getString(R.string.not_found), Toast.LENGTH_SHORT).show();
        }
    }

    private void showFileInfoDialog() {
        if (filePath == null) return;

        final int bg = readerDialogBgColor();
        final int fg = readerDialogTextColor(bg);

        File file = new File(filePath);
        String info = getString(R.string.file_label) + ": " + fileName
                + "\n" + getString(R.string.file_info_path) + ": " + filePath
                + "\n" + getString(R.string.file_info_size) + ": " + FileUtils.formatFileSize(file.length())
                + "\n" + getString(R.string.file_info_type) + ": " + FileUtils.getReadableFileType(fileName);
        if (FileUtils.isTextFile(fileName)) {
            info += "\n" + getString(R.string.file_info_encoding) + ": " + FileUtils.detectEncoding(file);
        }
        info += "\n" + getString(R.string.characters) + ": " + totalChars
                + "\n" + getString(R.string.lines) + ": " + totalLines
                + "\n" + getString(R.string.pages) + ": " + getTotalPageCount();

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Color.TRANSPARENT);

        TextView title = makeReaderDialogTitle(getString(R.string.file_info), bg, fg);
        panel.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView message = new TextView(this);
        message.setText(info);
        message.setTextColor(fg);
        message.setTextSize(14f);
        message.setLineSpacing(0f, 1.15f);
        message.setPadding(dpToPx(24), dpToPx(12), dpToPx(24), dpToPx(12));
        message.setBackgroundColor(Color.TRANSPARENT);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.TRANSPARENT);
        scroll.addView(message);
        panel.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        actionRow.setBackground(positionedActionPanelBackground(
                dialogActionPanelFillColor(bg),
                dialogActionPanelLineColor(bg)));
        actionRow.setPadding(dpToPx(30), 0, dpToPx(30), 0);

        TextView ok = makeReaderDialogActionText(getString(R.string.ok), fg,
                Gravity.CENTER_VERTICAL | Gravity.END);
        actionRow.addView(ok, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f));
        panel.addView(actionRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(54)));

        android.app.Dialog dialog = createNarrowPositionedReaderDialog(
                panel,
                bg,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL,
                88,
                0.85f,
                360,
                false);

        ok.setOnClickListener(v -> dialog.dismiss());
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
        activityDestroyed = true;
        loadGeneration.incrementAndGet();
        handler.removeCallbacksAndMessages(null);
        if (viewerBackToast != null) {
            viewerBackToast.cancel();
            viewerBackToast = null;
        }
        saveReadingState();
        if (notificationHelper != null) notificationHelper.dismiss();
        releaseReaderMemory();
        executor.shutdownNow();
        ViewerRegistry.unregister(this);
        super.onDestroy();
    }

    private void releaseReaderMemory() {
        activeSearchQuery = "";
        activeSearchIndex = -1;
        fileContent = "";
        totalChars = 0;
        totalLines = 0;
        largeTextEstimateActive = false;
        largeTextEstimatedTotalPages = 0;
        hugeTextPreviewOnly = false;
        pendingLargeTextRestorePosition = -1;
        pendingLargeTextCachedDisplayPage = 0;
        pendingLargeTextCachedTotalPages = 0;

        if (readerView != null) {
            readerView.releaseTextResources();
        }
    }

    private void saveReadingState() {
        if (filePath != null && prefs.getAutoSavePosition()) {
            ReaderState state = new ReaderState(filePath);
            state.setCharPosition(getCurrentCharPosition());
            state.setScrollY(readerView != null ? readerView.getReaderScrollY() : 0);
            state.setPageNumber(getDisplayedCurrentPageNumber());
            state.setTotalPages(getDisplayedTotalPageCount());
            if (filePath != null) {
                File f = new File(filePath);
                if (f.exists()) state.setFileLength(f.length());
            }
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

    private int dpToPx(float dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                getResources().getDisplayMetrics()));
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }
}
