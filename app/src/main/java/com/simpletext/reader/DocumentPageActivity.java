package com.simpletext.reader;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.annotation.SuppressLint;
import android.text.InputType;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.widget.TextViewCompat;

import com.simpletext.reader.adapter.BookmarkFolderAdapter;
import com.simpletext.reader.model.Bookmark;
import com.simpletext.reader.model.ReaderState;
import com.simpletext.reader.model.Theme;
import com.simpletext.reader.util.BookmarkManager;
import com.simpletext.reader.util.FileUtils;
import com.simpletext.reader.util.PrefsManager;
import com.simpletext.reader.util.ThemeManager;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.text.DateFormat;
import java.util.Date;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Page-style viewer for EPUB and OOXML Word files.
 *
 * EPUB: renders each original spine XHTML/HTML document inside WebView, preserving
 * the EPUB's markup/CSS/images as much as Android WebView allows.
 *
 * Word: renders OOXML document content as formatted HTML pages. Explicit Word page
 * breaks are respected; when a document has no page breaks, the content is split
 * into readable page-sized chunks instead of flattened into plain text.
 */
public class DocumentPageActivity extends AppCompatActivity {
    public static final String EXTRA_FILE_PATH = "file_path";
    public static final String EXTRA_FILE_URI = "file_uri";
    public static final String EXTRA_JUMP_TO_PAGE = "jump_page";

    private static final String LOCAL_HOST = "tekview.local";
    private static final String EPUB_PREFIX = "/epub/";
    private static final String WORD_PREFIX = "/word/";
    private static final int WORD_PARAGRAPHS_PER_PAGE = 28;

    private Toolbar toolbar;
    private WebView webView;
    private ProgressBar progressBar;
    private TextView pageStatus;
    private TextView prevButton;
    private TextView nextButton;
    private TextView pageButton;
    private TextView bookmarkButton;
    private TextView moreButton;
    private int readerBg = Color.rgb(18, 18, 18);
    private int readerFg = Color.rgb(232, 234, 237);
    private int readerSub = Color.rgb(176, 176, 176);
    private int readerPanel = Color.rgb(32, 33, 36);
    private int readerLine = Color.rgb(84, 86, 90);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<Page> pages = new ArrayList<>();
    private BookmarkManager bookmarkManager;
    private PrefsManager prefs;
    private ZipFile resourceZip;
    private File localFile;
    private String filePath;
    private String fileName;
    private String docType = "Document";
    private int currentPage = 0;
    private int pendingSlideDirection = 0;
    private int wordSwipeTouchSlop = 0;
    private float wordSwipeStartX = 0f;
    private float wordSwipeStartY = 0f;
    private boolean wordSwipeTriggered = false;
    private boolean pageTurnInFlight = false;
    private volatile boolean activityDestroyed = false;
    private int loadGeneration = 0;
    private final Runnable releasePageTurnRunnable = () -> pageTurnInFlight = false;
    private final Map<String, String> wordRelationships = new LinkedHashMap<>();

    private static class Page {
        final String title;
        final String html;
        final String sourcePath;

        Page(String title, String html, String sourcePath) {
            this.title = title;
            this.html = html;
            this.sourcePath = sourcePath;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = PrefsManager.getInstance(this);
        prefs.applyLanguage(prefs.getLanguageMode());

        // Do NOT force MODE_NIGHT_YES here. The previous attempt to keep the
        // floating selection toolbar in "dark bubble" style by forcing night mode
        // caused the WebView to resolve its text-selection handle drawables and
        // its floating action-mode layout against a dark Material configuration
        // while the actual document content kept the user's reader background
        // (often a light Cream / Sepia). The mismatch produced the malformed
        // teardrop handle and the toolbar bubble appearing pinned to the top of
        // the screen instead of next to the selection. The Samsung / system UI
        // already renders its own dark floating toolbar on Android 13+, so the
        // dark-bubble look is preserved even without forcing night mode.
        super.onCreate(savedInstanceState);

        resolveReaderThemeColors();
        setContentView(R.layout.activity_document_page);
        applyDocumentSystemBarColors();

        // targetSdk 35 forces edge-to-edge regardless of setDecorFitsSystemWindows.
        // Without inset padding, the toolbar sits under the status bar and the
        // bottom button row sits under the 3-button navigation bar.
        com.simpletext.reader.util.EdgeToEdgeUtil.applyStandardInsets(this,
                findViewById(R.id.document_root),
                findViewById(R.id.document_appbar),
                findViewById(R.id.document_bottom_scroller));
        applyDocumentSystemBarColors();

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setTitleTextColor(Color.WHITE);
        toolbar.setBackgroundColor(Color.BLACK);

        webView = findViewById(R.id.document_webview);
        progressBar = findViewById(R.id.loading_progress);
        pageStatus = findViewById(R.id.document_page_status);
        prevButton = findViewById(R.id.btn_prev_page);
        nextButton = findViewById(R.id.btn_next_page);
        pageButton = findViewById(R.id.btn_page_move);
        bookmarkButton = findViewById(R.id.btn_bookmarks);
        moreButton = findViewById(R.id.btn_more);

        bookmarkManager = BookmarkManager.getInstance(this);
        applyDocumentThemeToViews();
        setupWebView();
        setupButtons();
        installSwipePaging();
        loadFromIntent(getIntent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
            webView.resumeTimers();
        }
        applyDocumentSystemBarColors();
        applyDocumentThemeToViews();
    }

    @Override
    protected void onPause() {
        saveReadingState();
        if (webView != null) {
            webView.removeCallbacks(releasePageTurnRunnable);
            webView.onPause();
            webView.pauseTimers();
        }
        super.onPause();
    }

    private void resolveReaderThemeColors() {
        Theme theme = ThemeManager.getInstance(this).getActiveTheme();
        if (theme != null) {
            readerBg = theme.getBackgroundColor();
            readerFg = theme.getTextColor();
        }
        readerSub = blendColors(readerBg, readerFg, isDarkColor(readerBg) ? 0.72f : 0.64f);
        readerPanel = blendColors(readerBg, readerFg, isDarkColor(readerBg) ? 0.10f : 0.08f);
        readerLine = blendColors(readerBg, readerFg, isDarkColor(readerBg) ? 0.28f : 0.20f);
    }

    private void applyDocumentSystemBarColors() {
        resolveReaderThemeColors();
        int bg = readerBg;
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            getWindow().setNavigationBarDividerColor(bg);
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
            getWindow().setStatusBarContrastEnforced(false);
        }
        androidx.core.view.WindowInsetsControllerCompat controller =
                androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        boolean light = !isDarkColor(bg);
        controller.setAppearanceLightStatusBars(light);
        controller.setAppearanceLightNavigationBars(light);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            int flags = getWindow().getDecorView().getSystemUiVisibility();
            if (!isDarkColor(bg)) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            else flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    private boolean isDarkColor(int color) {
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        double luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
        return luminance < 0.5;
    }

    private int blendColors(int base, int overlay, float overlayAlpha) {
        overlayAlpha = Math.max(0f, Math.min(1f, overlayAlpha));
        float inv = 1f - overlayAlpha;
        return Color.rgb(
                Math.round(Color.red(base) * inv + Color.red(overlay) * overlayAlpha),
                Math.round(Color.green(base) * inv + Color.green(overlay) * overlayAlpha),
                Math.round(Color.blue(base) * inv + Color.blue(overlay) * overlayAlpha));
    }

    private void applyDocumentThemeToViews() {
        resolveReaderThemeColors();
        View root = findViewById(R.id.document_root);
        View viewport = findViewById(R.id.document_viewport);
        View appbar = findViewById(R.id.document_appbar);
        View bottom = findViewById(R.id.document_bottom_scroller);
        if (root != null) root.setBackgroundColor(readerBg);
        if (viewport != null) viewport.setBackgroundColor(readerBg);
        if (appbar != null) appbar.setBackgroundColor(readerBg);
        if (bottom != null) bottom.setBackgroundColor(readerPanel);
        if (toolbar != null) {
            toolbar.setBackgroundColor(readerBg);
            toolbar.setTitleTextColor(readerFg);
            android.graphics.drawable.Drawable nav = toolbar.getNavigationIcon();
            if (nav != null) {
                android.graphics.drawable.Drawable wrapped = androidx.core.graphics.drawable.DrawableCompat.wrap(nav.mutate());
                androidx.core.graphics.drawable.DrawableCompat.setTint(wrapped, readerFg);
                toolbar.setNavigationIcon(wrapped);
            }
        }
        if (webView != null) webView.setBackgroundColor(readerBg);
        if (pageStatus != null) pageStatus.setTextColor(readerFg);
        TextView[] buttons = {prevButton, nextButton, pageButton, bookmarkButton, moreButton};
        for (TextView b : buttons) {
            if (b == null) continue;
            b.setTextColor(readerFg);
            TextViewCompat.setCompoundDrawableTintList(b, android.content.res.ColorStateList.valueOf(readerFg));
        }
    }

    @Override
    protected void onDestroy() {
        activityDestroyed = true;
        loadGeneration++;
        saveReadingState();
        destroyDocumentWebView();
        closeResourceZip();
        pages.clear();
        wordRelationships.clear();
        executor.shutdownNow();
        super.onDestroy();
    }

    private void destroyDocumentWebView() {
        if (webView == null) return;
        try {
            webView.removeCallbacks(releasePageTurnRunnable);
            webView.animate().cancel();
            webView.setOnTouchListener(null);
            webView.setOnScrollChangeListener(null);
            webView.setWebViewClient(null);
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.clearHistory();
            webView.removeAllViews();
            webView.destroy();
        } catch (Throwable ignored) {
            // WebView teardown should never crash the Activity during system cleanup.
        } finally {
            webView = null;
        }
    }

    private void closeResourceZip() {
        if (resourceZip != null) {
            try { resourceZip.close(); } catch (IOException ignored) {}
            resourceZip = null;
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(false);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(true);
        settings.setTextZoom(100);
        // Keep generated Word pages at native screen scale. Wide-viewport overview
        // scaling makes WebView text hit-testing and selection-handle dragging feel
        // delayed or off-position on DOCX pages.
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(false);
        settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setDomStorageEnabled(false);

        webView.setBackgroundColor(readerBg);
        webView.setLongClickable(true);
        webView.setHapticFeedbackEnabled(true);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(@NonNull WebView view, @NonNull WebResourceRequest request) {
                return interceptLocalResource(request.getUrl());
            }

            @Override
            public void onPageFinished(@NonNull WebView view, @NonNull String url) {
                super.onPageFinished(view, url);
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                runDocumentSlideInAnimation();
            }
        });
    }

    private void setupButtons() {
        prevButton.setOnClickListener(v -> {
            if (currentPage > 0) showPage(currentPage - 1, -1);
        });
        nextButton.setOnClickListener(v -> {
            if (currentPage < pages.size() - 1) showPage(currentPage + 1, 1);
        });
        if (pageButton != null) pageButton.setOnClickListener(v -> showGoToPageDialog());
        bookmarkButton.setOnClickListener(v -> showBookmarksDialog());
        if (moreButton != null) {
            moreButton.setOnClickListener(v -> showMoreDialog());
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void installSwipePaging() {
        wordSwipeTouchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        webView.setOnTouchListener((v, event) -> {
            if (!"Word".equals(docType) || pages.size() <= 1 || pageTurnInFlight) return false;

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    wordSwipeStartX = event.getX();
                    wordSwipeStartY = event.getY();
                    wordSwipeTriggered = false;
                    webView.removeCallbacks(releasePageTurnRunnable);
                    return false;

                case MotionEvent.ACTION_MOVE:
                    // Do not intercept movement. WebView must receive the full MOVE
                    // stream for native Android/Samsung text selection and handle
                    // dragging to work correctly.
                    return false;

                case MotionEvent.ACTION_UP:
                    if (Math.abs(event.getX() - wordSwipeStartX) < wordSwipeTouchSlop
                            && Math.abs(event.getY() - wordSwipeStartY) < wordSwipeTouchSlop) {
                        v.performClick();
                    }
                    if (!wordSwipeTriggered && shouldTurnWordPageBySwipe(event)) {
                        wordSwipeTriggered = true;
                        turnDocumentPageBySwipe(event.getX() < wordSwipeStartX ? 1 : -1);
                        resetWordSwipeTracking();
                        return true;
                    }
                    resetWordSwipeTracking();
                    return false;

                case MotionEvent.ACTION_CANCEL:
                    resetWordSwipeTracking();
                    return false;

                default:
                    return false;
            }
        });
    }

    private void resetWordSwipeTracking() {
        wordSwipeTriggered = false;
    }

    private boolean shouldTurnWordPageBySwipe(@NonNull MotionEvent event) {
        if (activityDestroyed || webView == null || pages.size() <= 1 || pageTurnInFlight) return false;

        float dx = event.getX() - wordSwipeStartX;
        float dy = event.getY() - wordSwipeStartY;
        float absX = Math.abs(dx);
        float absY = Math.abs(dy);
        long duration = event.getEventTime() - event.getDownTime();

        // Fast but guarded: horizontal page turn should feel immediate, but must
        // not steal normal vertical page scrolling or text-selection handle drags.
        float threshold = Math.max(dpToPx(34), webView.getWidth() * 0.075f);
        return absX >= threshold
                && absX > absY * 1.45f
                && absY <= dpToPx(64)
                && duration <= 750;
    }

    private void turnDocumentPageBySwipe(int direction) {
        if (webView == null || pageTurnInFlight) return;
        if (direction > 0 && currentPage < pages.size() - 1) {
            showPage(currentPage + 1, 1);
        } else if (direction < 0 && currentPage > 0) {
            showPage(currentPage - 1, -1);
        }
    }


    private void showMoreDialog() {
        LinearLayout box = makeDialogBox();
        box.addView(makeDialogTitle(getString(R.string.more)));
        addDialogAction(box, getString(R.string.zoom_out), () -> changeDocumentZoom(-10));
        addDialogAction(box, getString(R.string.zoom_in), () -> changeDocumentZoom(10));
        addDialogAction(box, getString(R.string.reset_zoom), this::resetDocumentZoom);
        addDialogDivider(box);
        addDialogAction(box, getString(R.string.settings), () -> startActivity(new Intent(this, SettingsActivity.class)));
        addDialogAction(box, getString(R.string.file_info), this::showFileInfoDialog);
        showCustomDialog(box, getString(R.string.close), true);
    }

    private void toggleDoubleTapDocumentZoom() {
        WebSettings settings = webView.getSettings();
        if (settings.getTextZoom() <= 110) {
            settings.setTextZoom(165);
        } else {
            resetDocumentZoom();
        }
    }

    private void changeDocumentZoom(int delta) {
        WebSettings settings = webView.getSettings();
        settings.setTextZoom(Math.max(70, Math.min(190, settings.getTextZoom() + delta)));
    }

    private void resetDocumentZoom() {
        WebSettings settings = webView.getSettings();
        settings.setTextZoom(100);
        webView.evaluateJavascript("window.scrollTo(0,0);", null);
    }

    private void showFileInfoDialog() {
        LinearLayout box = makeDialogBox();
        box.addView(makeDialogTitle(getString(R.string.file_info)));
        addInfoRow(box, getString(R.string.file_info_name), fileName != null ? fileName : "");
        addInfoRow(box, getString(R.string.file_info_type), docType);
        addInfoRow(box, getString(R.string.file_info_path), filePath != null ? filePath : "");
        if (localFile != null) {
            addInfoRow(box, getString(R.string.file_info_size), FileUtils.formatFileSize(localFile.length()));
            addInfoRow(box, getString(R.string.file_info_modified), DateFormat.getDateTimeInstance().format(new Date(localFile.lastModified())));
        }
        addInfoRow(box, getString(R.string.bottom_page), String.format(Locale.getDefault(), "%d / %d", currentPage + 1, pages.size()));
        showCustomDialog(box, getString(R.string.close));
    }

    private void showGoToPageDialog() {
        if (pages.isEmpty()) return;

        LinearLayout box = makeDialogBox();
        box.addView(makeDialogTitle(getString(R.string.page_move)));

        TextView label = new TextView(this);
        label.setTextColor(dialogFg());
        label.setTextSize(17f);
        label.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        label.setGravity(android.view.Gravity.CENTER);
        label.setText(formatPageMoveLabel(currentPage + 1, pages.size()));
        label.setPadding(0, dpToPx(4), 0, dpToPx(8));
        box.addView(label, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        SeekBar slider = new SeekBar(this);
        slider.setMax(Math.max(0, pages.size() - 1));
        slider.setProgress(currentPage);
        tintSeekBar(slider);
        box.addView(slider, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(44)));

        TextView hint = new TextView(this);
        hint.setText(getString(R.string.exact_page_number));
        hint.setTextColor(blendColors(dialogBg(), dialogFg(), 0.78f));
        hint.setTextSize(13f);
        hint.setGravity(android.view.Gravity.CENTER);
        hint.setPadding(0, dpToPx(4), 0, dpToPx(6));
        box.addView(hint);

        EditText input = makeDialogInput("1 - " + pages.size());
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setGravity(android.view.Gravity.CENTER);
        input.setText(String.valueOf(currentPage + 1));
        input.setSelectAllOnFocus(true);
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(dpToPx(132), dpToPx(52));
        inputLp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        box.addView(input, inputLp);

        final int[] pending = new int[]{currentPage};
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (!fromUser) return;
                pending[0] = progress;
                label.setText(formatPageMoveLabel(progress + 1, pages.size()));
                input.setText(String.valueOf(progress + 1));
                input.setSelection(input.getText().length());
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {
                showPage(pending[0], Integer.compare(pending[0], currentPage));
            }
        });

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this).create();
        dialog.setView(box);
        dialog.setOnShowListener(d -> {
            styleDialogWindow(dialog);
            addDialogBottomActions(box, getString(R.string.go), () -> {
                try {
                    int target = Integer.parseInt(input.getText().toString().trim());
                    if (target < 1 || target > pages.size()) {
                        Toast.makeText(this, getString(R.string.page_range_error, pages.size()), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    showPage(target - 1, Integer.compare(target - 1, currentPage));
                    dialog.dismiss();
                } catch (Exception ignored) {
                    Toast.makeText(this, getString(R.string.invalid_page_number), Toast.LENGTH_SHORT).show();
                }
            });
        });
        dialog.show();
        positionPageDialogForThumbReach(dialog);
    }

    private void positionPageDialogForThumbReach(@NonNull androidx.appcompat.app.AlertDialog dialog) {
        if (dialog.getWindow() == null) return;
        android.view.Window window = dialog.getWindow();
        window.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
        android.view.WindowManager.LayoutParams lp = new android.view.WindowManager.LayoutParams();
        lp.copyFrom(window.getAttributes());
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        lp.width = Math.min(screenWidth - dpToPx(14), dpToPx(460));
        lp.height = android.view.WindowManager.LayoutParams.WRAP_CONTENT;
        lp.y = dpToPx(74);
        window.setAttributes(lp);
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private String formatPageMoveLabel(int page, int totalPages) {
        return String.format(Locale.getDefault(), "Page %d / %d", page, Math.max(1, totalPages));
    }

    private int dialogBg() { return readerBg; }
    private int dialogPanel() { return readerPanel; }
    private int dialogFg() { return readerFg; }
    private int dialogSub() { return readerSub; }

    private LinearLayout makeDialogBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dpToPx(18), dpToPx(14), dpToPx(18), dpToPx(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(dialogBg());
        bg.setCornerRadius(dpToPx(14));
        bg.setStroke(Math.max(1, dpToPx(1)), readerLine);
        box.setBackground(bg);
        return box;
    }

    private TextView makeDialogTitle(String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextColor(dialogFg());
        title.setTextSize(22f);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setPadding(0, 0, 0, dpToPx(12));
        return title;
    }

    private EditText makeDialogInput(String hint) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setTextColor(dialogFg());
        input.setHintTextColor(dialogSub());
        input.setPadding(dpToPx(14), 0, dpToPx(14), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(dialogPanel());
        bg.setCornerRadius(dpToPx(8));
        bg.setStroke(Math.max(1, dpToPx(1)), readerLine);
        input.setBackground(bg);
        return input;
    }

    private void tintSeekBar(SeekBar seekBar) {
        int accent = readerFg;
        int track = readerLine;
        seekBar.setThumbTintList(android.content.res.ColorStateList.valueOf(accent));
        seekBar.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
        seekBar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(track));
    }

    private void addDialogAction(LinearLayout box, String text, Runnable action) {
        TextView row = new TextView(this);
        row.setText(text);
        row.setTextColor(dialogFg());
        row.setTextSize(16f);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dpToPx(14), 0, dpToPx(14), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(dialogPanel());
        bg.setCornerRadius(dpToPx(10));
        row.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(48));
        lp.setMargins(0, 0, 0, dpToPx(8));
        box.addView(row, lp);
        row.setOnClickListener(v -> action.run());
    }

    private void addInfoRow(LinearLayout box, String label, String value) {
        TextView row = new TextView(this);
        String safeValue = value != null ? value : "";
        row.setText(safeValue.isEmpty()
                ? label
                : String.format(Locale.getDefault(), "%s\n%s", label, safeValue));
        row.setTextColor(dialogFg());
        row.setTextSize(14f);
        row.setPadding(0, dpToPx(5), 0, dpToPx(7));
        box.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private void addDialogDivider(LinearLayout box) {
        View line = new View(this);
        line.setBackgroundColor(readerLine);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dpToPx(1)));
        lp.setMargins(0, dpToPx(4), 0, dpToPx(10));
        box.addView(line, lp);
    }

    private void showCustomDialog(LinearLayout box, String closeText) {
        showCustomDialog(box, closeText, false);
    }

    private void showCustomDialog(LinearLayout box, String closeText, boolean oneHandLower) {
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this).create();
        dialog.setView(box);
        dialog.setOnShowListener(d -> {
            styleDialogWindow(dialog);
            addDialogBottomActions(box, closeText, dialog::dismiss);
        });
        dialog.show();
        if (oneHandLower) positionMoreDialogForThumbReach(dialog);
    }

    private void positionMoreDialogForThumbReach(@NonNull androidx.appcompat.app.AlertDialog dialog) {
        if (dialog.getWindow() == null) return;
        android.view.Window window = dialog.getWindow();
        window.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
        android.view.WindowManager.LayoutParams lp = new android.view.WindowManager.LayoutParams();
        lp.copyFrom(window.getAttributes());
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        lp.width = Math.min(screenWidth - dpToPx(16), dpToPx(460));
        lp.height = android.view.WindowManager.LayoutParams.WRAP_CONTENT;
        lp.y = dpToPx(34);
        window.setAttributes(lp);
    }

    private void addDialogBottomActions(LinearLayout box, String primaryText, Runnable primaryAction) {
        if (box.findViewWithTag("dialog_actions") != null) return;
        LinearLayout actions = new LinearLayout(this);
        actions.setTag("dialog_actions");
        actions.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.END);
        actions.setPadding(0, dpToPx(8), 0, 0);
        TextView primary = new TextView(this);
        primary.setText(primaryText);
        primary.setTextColor(dialogFg());
        primary.setTextSize(16f);
        primary.setGravity(android.view.Gravity.CENTER);
        primary.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        primary.setPadding(dpToPx(18), 0, dpToPx(18), 0);
        actions.addView(primary, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(46)));
        box.addView(actions, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        primary.setOnClickListener(v -> primaryAction.run());
    }

    private void styleDialogWindow(androidx.appcompat.app.AlertDialog dialog) {
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private void loadFromIntent(Intent intent) {
        final int generation = ++loadGeneration;
        progressBar.setVisibility(View.VISIBLE);
        webView.setVisibility(View.INVISIBLE);
        closeResourceZip();
        pages.clear();
        wordRelationships.clear();

        executor.execute(() -> {
            try {
                String path = intent.getStringExtra(EXTRA_FILE_PATH);
                String uriString = intent.getStringExtra(EXTRA_FILE_URI);
                if (path != null && !path.isEmpty()) {
                    localFile = new File(path);
                } else if (uriString != null && !uriString.isEmpty()) {
                    Uri uri = Uri.parse(uriString);
                    String displayName = FileUtils.getFileNameFromUri(this, uri);
                    if (displayName == null || displayName.trim().isEmpty()) displayName = "document";
                    localFile = FileUtils.copyUriToLocal(this, uri, displayName);
                } else {
                    throw new IOException("No file path or URI supplied");
                }

                filePath = localFile.getAbsolutePath();
                fileName = localFile.getName();
                String lower = fileName.toLowerCase(Locale.ROOT);
                pages.clear();

                if (lower.endsWith(".epub")) {
                    docType = "EPUB";
                    loadEpubPages(localFile);
                } else if (FileUtils.isWordFile(fileName)) {
                    docType = "Word";
                    loadWordPages(localFile);
                } else {
                    throw new IOException("Unsupported document type: " + fileName);
                }

                if (pages.isEmpty()) throw new IOException("No renderable pages found");

                int jump = intent.getIntExtra(EXTRA_JUMP_TO_PAGE, -1);
                if (jump >= 0 && jump < pages.size()) {
                    currentPage = jump;
                } else {
                    ReaderState state = bookmarkManager.getReadingState(filePath);
                    if (state != null && state.getCharPosition() >= 0 && state.getCharPosition() < pages.size()) {
                        currentPage = state.getCharPosition();
                    } else {
                        currentPage = 0;
                    }
                }

                if (activityDestroyed || generation != loadGeneration) return;
                runOnUiThread(() -> {
                    if (activityDestroyed || generation != loadGeneration) return;
                    if (getSupportActionBar() != null) getSupportActionBar().setTitle(fileName);
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    if (webView != null) webView.setVisibility(View.VISIBLE);
                    showPage(currentPage, 0);
                });
            } catch (Exception e) {
                if (activityDestroyed || generation != loadGeneration) return;
                runOnUiThread(() -> {
                    if (!activityDestroyed) showLoadError(e);
                });
            }
        });
    }

    private void showLoadError(Exception e) {
        if (activityDestroyed) return;
        if (progressBar != null) progressBar.setVisibility(View.GONE);
        Toast.makeText(this, getString(R.string.error_prefix) + e.getMessage(), Toast.LENGTH_LONG).show();
        finish();
    }

    private String applyReaderThemeCss(String html) {
        int linkColor = ThemeManager.getInstance(this).getActiveTheme().getLinkColor();

        // Keep CSS minimal.  Do not force user-select/caret/handle behavior here:
        // WebView must own selection geometry or selection handles drift while the
        // document scrolls.
        String css = "<style id=\"tekview-reader-theme\">" +
                "html,body{background:" + cssColor(readerBg) + " !important;color:" + cssColor(readerFg) +
                " !important;}" +
                "a{color:" + cssColor(linkColor) + " !important;}" +
                "table,td,th{border-color:" + cssColor(readerLine) + " !important;}" +
                "html,body{max-width:100%;overflow-x:hidden;}" +
                "*{box-sizing:border-box;}" +
                "body,.page,p,div,span,td,th,li{max-width:100%;overflow-wrap:anywhere;word-break:break-word;}" +
                "img,svg,video,.word-img,.textbox,table{max-width:100%;}" +
                "pre{white-space:pre-wrap;overflow-wrap:anywhere;word-break:break-word;}" +
                "</style>";
        if (html == null) return css;
        int head = html.toLowerCase(Locale.US).indexOf("</head>");
        if (head >= 0) return html.substring(0, head) + css + html.substring(head);
        return css + html;
    }

    private String cssColor(int color) {
        return String.format(Locale.US, "#%06X", 0xFFFFFF & color);
    }

    private void showPage(int page, int direction) {
        if (activityDestroyed || webView == null || page < 0 || page >= pages.size()) return;
        if (direction != 0 && pageTurnInFlight) return;
        if (direction != 0) {
            pageTurnInFlight = true;
            webView.removeCallbacks(releasePageTurnRunnable);
            webView.postDelayed(releasePageTurnRunnable, 190);
        }
        pendingSlideDirection = direction;
        currentPage = page;
        Page p = pages.get(page);
        String baseUrl = "https://" + LOCAL_HOST + "/";
        if ("EPUB".equals(docType) && p.sourcePath != null) {
            String parent = parentPath(p.sourcePath);
            baseUrl = "https://" + LOCAL_HOST + EPUB_PREFIX + parent;
            if (!baseUrl.endsWith("/")) baseUrl += "/";
        }
        prepareDocumentSlide(direction);
        webView.getSettings().setJavaScriptEnabled("Word".equals(docType));
        webView.loadDataWithBaseURL(baseUrl, applyReaderThemeCss(p.html), "text/html", "UTF-8", null);
        updateStatus();
        saveReadingState();
    }

    private void prepareDocumentSlide(int direction) {
        if (webView == null || direction == 0) return;
        webView.animate().cancel();
        float distance = Math.max(dpToPx(56), webView.getWidth() * 0.18f);
        webView.setTranslationX(direction > 0 ? distance : -distance);
        webView.setAlpha(0.72f);
    }

    private void runDocumentSlideInAnimation() {
        if (webView == null) return;
        int direction = pendingSlideDirection;
        pendingSlideDirection = 0;
        if (direction == 0) {
            webView.setTranslationX(0f);
            webView.setAlpha(1.0f);
            pageTurnInFlight = false;
            return;
        }
        webView.animate()
                .translationX(0f)
                .alpha(1.0f)
                .setDuration(135)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .withEndAction(() -> pageTurnInFlight = false)
                .start();
    }

    private void updateStatus() {
        pageStatus.setText(String.format(Locale.getDefault(), "%s %d / %d", docType, currentPage + 1, pages.size()));
        prevButton.setEnabled(currentPage > 0);
        nextButton.setEnabled(currentPage < pages.size() - 1);
    }

    private void addBookmarkForCurrentPage() {
        if (filePath == null || pages.isEmpty()) return;

        String excerpt = String.format(Locale.getDefault(), "%s %d / %d", docType, currentPage + 1, pages.size());
        for (Bookmark b : bookmarkManager.getBookmarksForFile(filePath)) {
            if (b.getCharPosition() == currentPage) {
                b.setLineNumber(currentPage + 1);
                b.setExcerpt(excerpt);
                b.setEndPosition(currentPage);
                bookmarkManager.updateBookmark(b);
                Toast.makeText(this, getString(R.string.bookmark_updated), Toast.LENGTH_SHORT).show();
                return;
            }
        }

        Bookmark bookmark = new Bookmark(filePath, fileName, currentPage, currentPage + 1, excerpt);
        bookmark.setEndPosition(currentPage);
        bookmarkManager.addBookmark(bookmark);
        Toast.makeText(this, getString(R.string.bookmark_saved), Toast.LENGTH_SHORT).show();
    }

    private void showBookmarksDialog() {
        if (filePath == null) return;

        final int bg = readerBg;
        final int panel = readerPanel;
        final int fg = readerFg;
        final int sub = readerSub;
        final int line = readerLine;

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dpToPx(18), dpToPx(16), dpToPx(18), dpToPx(10));
        GradientDrawable boxBg = new GradientDrawable();
        boxBg.setColor(bg);
        boxBg.setCornerRadius(dpToPx(16));
        boxBg.setStroke(Math.max(1, dpToPx(1)), line);
        box.setBackground(boxBg);

        TextView title = new TextView(this);
        title.setText(getString(R.string.bookmark));
        title.setTextColor(fg);
        title.setTextSize(22f);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setPadding(0, 0, 0, dpToPx(12));
        box.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView currentInfo = new TextView(this);
        currentInfo.setTextColor(blendColors(bg, fg, 0.76f));
        currentInfo.setTextSize(12f);
        currentInfo.setPadding(0, 0, 0, dpToPx(10));
        box.addView(currentInfo, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView saveButton = new TextView(this);
        saveButton.setText(getString(R.string.add_current_bookmark));
        saveButton.setGravity(android.view.Gravity.CENTER);
        saveButton.setTextColor(fg);
        saveButton.setTextSize(16f);
        saveButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        saveButton.setPadding(0, dpToPx(12), 0, dpToPx(12));
        android.graphics.drawable.GradientDrawable saveBg = new android.graphics.drawable.GradientDrawable();
        saveBg.setColor(panel);
        saveBg.setCornerRadius(dpToPx(8));
        saveBg.setStroke(Math.max(1, dpToPx(1)), line);
        saveButton.setBackground(saveBg);
        box.addView(saveButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        RecyclerView rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        rv.setBackgroundColor(Color.TRANSPARENT);
        rv.setPadding(0, dpToPx(8), 0, 0);
        rv.setClipToPadding(false);
        BookmarkFolderAdapter adapter = new BookmarkFolderAdapter();
        adapter.setThemeColors(bg, fg, sub, panel);
        Set<String> expandedFolders = new HashSet<>();
        expandedFolders.add(filePath);
        rv.setAdapter(adapter);

        TextView emptyText = new TextView(this);
        emptyText.setText(getString(R.string.no_bookmarks_hint));
        emptyText.setTextColor(blendColors(bg, fg, 0.76f));
        emptyText.setGravity(android.view.Gravity.CENTER);
        emptyText.setPadding(0, dpToPx(18), 0, dpToPx(18));
        box.addView(emptyText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        box.addView(rv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(430)));

        TextView closeButton = new TextView(this);
        closeButton.setText(getString(R.string.close));
        closeButton.setGravity(android.view.Gravity.CENTER);
        closeButton.setTextColor(fg);
        closeButton.setTextSize(16f);
        closeButton.setPadding(0, dpToPx(14), 0, dpToPx(10));
        box.addView(closeButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this).create();
        dialog.setView(box);

        final Runnable[] refreshRef = new Runnable[1];
        refreshRef[0] = () -> {
            List<Bookmark> all = bookmarkManager.getAllBookmarks();
            adapter.setBookmarks(all, expandedFolders, filePath);
            emptyText.setVisibility(all.isEmpty() ? View.VISIBLE : View.GONE);
            currentInfo.setText(getString(R.string.all_bookmarks_status,
                    adapter.getFolderCount(), all.size(), currentPage + 1, pages.size()));
        };

        saveButton.setOnClickListener(v -> {
            addBookmarkForCurrentPage();
            expandedFolders.add(filePath);
            refreshRef[0].run();
        });
        closeButton.setOnClickListener(v -> dialog.dismiss());

        adapter.setListener(new BookmarkFolderAdapter.Listener() {
            @Override public void onFolderClick(String folderFilePath) {
                if (expandedFolders.contains(folderFilePath)) expandedFolders.remove(folderFilePath);
                else expandedFolders.add(folderFilePath);
                refreshRef[0].run();
            }

            @Override public void onBookmarkClick(Bookmark b) {
                navigateToBookmark(b);
                dialog.dismiss();
            }

            @Override public void onBookmarkDelete(Bookmark b) {
                showBookmarkDeleteConfirm(b, refreshRef[0]);
            }

            @Override public void onBookmarkEdit(Bookmark b) {
                showBookmarkMemoEditDialog(b, refreshRef[0]);
            }
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        refreshRef[0].run();
    }

    private void navigateToBookmark(@NonNull Bookmark b) {
        File target = new File(b.getFilePath());
        if (!target.exists()) {
            Toast.makeText(this, getString(R.string.file_not_found_prefix) + b.getFilePath(), Toast.LENGTH_LONG).show();
            return;
        }
        if (b.getFilePath().equals(filePath)) {
            showPage(b.getCharPosition(), Integer.compare(b.getCharPosition(), currentPage));
            return;
        }
        Intent intent;
        if (FileUtils.isPdfFile(target.getName())) {
            intent = new Intent(this, PdfReaderActivity.class);
            intent.putExtra(PdfReaderActivity.EXTRA_FILE_PATH, b.getFilePath());
            intent.putExtra(PdfReaderActivity.EXTRA_JUMP_TO_PAGE, b.getCharPosition());
        } else if (FileUtils.isEpubFile(target.getName()) || FileUtils.isWordFile(target.getName())) {
            intent = new Intent(this, DocumentPageActivity.class);
            intent.putExtra(DocumentPageActivity.EXTRA_FILE_PATH, b.getFilePath());
            intent.putExtra(DocumentPageActivity.EXTRA_JUMP_TO_PAGE, b.getCharPosition());
        } else {
            intent = new Intent(this, ReaderActivity.class);
            intent.putExtra(ReaderActivity.EXTRA_FILE_PATH, b.getFilePath());
            intent.putExtra(ReaderActivity.EXTRA_JUMP_TO_POSITION, b.getCharPosition());
        }
        startActivity(intent);
    }

    private void showBookmarkDeleteConfirm(@NonNull Bookmark bookmark, @NonNull Runnable afterDelete) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.delete_bookmark))
                .setMessage(bookmark.getFileName() + "\n\n" + bookmark.getDisplayText())
                .setPositiveButton(getString(R.string.delete), (d, w) -> {
                    bookmarkManager.deleteBookmark(bookmark.getId());
                    afterDelete.run();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void showBookmarkMemoEditDialog(@NonNull Bookmark bookmark, @NonNull Runnable afterSave) {
        EditText input = new EditText(this);
        input.setText(bookmark.getLabel());
        input.setHint(getString(R.string.optional_memo));
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.edit_bookmark_memo))
                .setView(input)
                .setPositiveButton(getString(R.string.save), (d, w) -> {
                    bookmark.setLabel(input.getText().toString().trim());
                    bookmarkManager.updateBookmark(bookmark);
                    afterSave.run();
                })
                .setNeutralButton(getString(R.string.clear), (d, w) -> {
                    bookmark.setLabel("");
                    bookmarkManager.updateBookmark(bookmark);
                    afterSave.run();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void saveReadingState() {
        if (filePath == null || prefs == null || !prefs.getAutoSavePosition()) return;
        ReaderState state = new ReaderState(filePath);
        state.setCharPosition(currentPage);
        state.setScrollY(0);
        state.setEncoding(docType + "_PAGE");
        bookmarkManager.saveReadingState(state);
    }

    private void loadEpubPages(File file) throws Exception {
        closeResourceZip();
        resourceZip = new ZipFile(file);
        List<String> spine = findEpubSpinePaths(resourceZip);
        if (spine.isEmpty()) spine = findEpubHtmlEntries(resourceZip);

        for (String path : spine) {
            ZipEntry entry = resourceZip.getEntry(path);
            if (entry == null || entry.isDirectory()) continue;
            String html = readZipEntryString(resourceZip, entry);
            String title = titleFromHtml(html);
            if (title.isEmpty()) title = fileNameFromPath(path);
            pages.add(new Page(title, prepareEpubHtml(html), path));
        }
    }

    private void loadWordPages(File file) throws Exception {
        closeResourceZip();
        resourceZip = new ZipFile(file);
        wordRelationships.clear();
        loadWordRelationships(resourceZip);
        ZipEntry documentXml = resourceZip.getEntry("word/document.xml");
        if (documentXml == null) throw new IOException("Unsupported Word file");

        Document doc;
        try (InputStream is = resourceZip.getInputStream(documentXml)) {
            doc = secureDocumentBuilder().parse(is);
        }

        Node body = firstNodeByLocalName(doc, "body");
        if (body == null) body = doc.getDocumentElement();

        List<String> pageBodies = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int paragraphCount = 0;

        NodeList children = body.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            String local = child.getLocalName();
            String name = child.getNodeName();
            boolean paragraph = "p".equals(local) || "w:p".equals(name);
            boolean table = "tbl".equals(local) || "w:tbl".equals(name);
            if (!paragraph && !table) continue;

            if (table) {
                current.append(renderWordTable(child));
                paragraphCount += 4;
            } else {
                current.append(renderWordParagraph(child));
                paragraphCount++;
            }

            if (containsWordPageBreak(child) || paragraphCount >= WORD_PARAGRAPHS_PER_PAGE) {
                if (current.length() > 0) pageBodies.add(current.toString());
                current.setLength(0);
                paragraphCount = 0;
            }
        }

        if (current.length() > 0 || pageBodies.isEmpty()) pageBodies.add(current.toString());

        for (int i = 0; i < pageBodies.size(); i++) {
            pages.add(new Page("Word page " + (i + 1), wrapWordPage(pageBodies.get(i), i + 1), null));
        }
    }

    private int dpToPx(float dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private WebResourceResponse interceptLocalResource(Uri uri) {
        if (uri == null || resourceZip == null) return null;
        if (!LOCAL_HOST.equalsIgnoreCase(uri.getHost())) return null;
        String path = uri.getPath();
        if (path == null) return null;

        String zipPath;
        if (path.startsWith(EPUB_PREFIX)) {
            zipPath = path.substring(EPUB_PREFIX.length());
        } else if (path.startsWith(WORD_PREFIX)) {
            zipPath = path.substring(1);
        } else {
            return null;
        }

        try {
            zipPath = URLDecoder.decode(zipPath, "UTF-8");
        } catch (Exception ignored) {}
        zipPath = normalizeZipPath(zipPath);

        ZipEntry entry = resourceZip.getEntry(zipPath);
        if (entry == null || entry.isDirectory()) return null;
        try {
            byte[] data;
            try (InputStream is = resourceZip.getInputStream(entry)) {
                data = readAllBytes(is);
            }
            return new WebResourceResponse(
                    mimeForPath(zipPath),
                    "UTF-8",
                    new ByteArrayInputStream(data));
        } catch (IOException e) {
            return null;
        }
    }

    private String prepareEpubHtml(String html) {
        if (html == null) html = "";
        String css = "<style>" +
                "html,body{margin:0;padding:0;background:#121212;color:#e8eaed;}" +
                "body{font-family:serif;line-height:1.55;padding:22px;box-sizing:border-box;}" +
                "img,svg,video{max-width:100%;height:auto;}" +
                "a{color:#8ab4f8;}" +
                "pre{white-space:pre-wrap;}" +
                "</style>";
        String viewport = "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">";
        if (html.toLowerCase(Locale.ROOT).contains("<head")) {
            return html.replaceFirst("(?i)<head[^>]*>", "$0" + viewport + css);
        }
        return "<!doctype html><html><head>" + viewport + css + "</head><body>" + html + "</body></html>";
    }

    private String wrapWordPage(String body, int pageNumber) {
        return "<!doctype html><html><head>" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, user-scalable=yes\">" +
                "<style>" +
                "html,body{margin:0;padding:0;background:#202124;overflow-x:hidden;-webkit-text-size-adjust:100%;max-width:100%;}" +
                "body{font-family:Arial,Helvetica,sans-serif;}" +
                "*{box-sizing:border-box;max-width:100%;}" +
                ".page{background:#fff;color:#111;margin:12px auto;padding:26px 24px;width:calc(100% - 24px);max-width:816px;" +
                "min-height:92vh;box-shadow:0 2px 14px rgba(0,0,0,.35);overflow-x:hidden;box-sizing:border-box;}" +
                ".pageNo{color:#777;text-align:right;font-size:12px;margin-bottom:14px;}" +
                "p{margin:0 0 8px 0;line-height:1.28;font-size:15.5px;white-space:normal;overflow-wrap:anywhere;word-break:break-word;}" +
                "div,span,li,td,th{overflow-wrap:anywhere;word-break:break-word;}" +
                "pre{white-space:pre-wrap;overflow-wrap:anywhere;word-break:break-word;}" +
                ".textbox{border:1px solid #ddd;background:#fff;margin:8px 0;padding:8px;box-sizing:border-box;overflow:hidden;}" +
                ".word-img,img,svg,video{max-width:100%;height:auto;display:block;margin:8px auto;}" +
                "table{width:100%;max-width:100%;table-layout:fixed;border-collapse:collapse;margin:10px 0;}" +
                "td,th{border:1px solid #777;padding:5px;vertical-align:top;min-width:0;}" +
                "b,strong{font-weight:700;}i,em{font-style:italic;}u{text-decoration:underline;}" +
                "</style></head><body><div class=\"page\"><div class=\"pageNo\">Word page " + pageNumber +
                "</div>" + body + "</div></body></html>";
    }

    private String renderWordParagraph(Node p) {
        StringBuilder inline = new StringBuilder();
        appendWordInlineHtmlLimited(p, inline, p);

        StringBuilder out = new StringBuilder();
        if (hasVisibleHtml(inline)) {
            out.append("<p").append(wordParagraphStyle(p)).append(">");
            out.append(inline);
            out.append("</p>");
        }
        appendWordTextBoxes(p, out);
        return out.toString();
    }

    private String renderWordTable(Node table) {
        StringBuilder out = new StringBuilder();
        out.append("<table>");
        NodeList rows = table.getChildNodes();
        for (int i = 0; i < rows.getLength(); i++) {
            Node row = rows.item(i);
            String local = row.getLocalName();
            String name = row.getNodeName();
            if (!"tr".equals(local) && !"w:tr".equals(name)) continue;
            out.append("<tr>");
            NodeList cells = row.getChildNodes();
            for (int j = 0; j < cells.getLength(); j++) {
                Node cell = cells.item(j);
                String cl = cell.getLocalName();
                String cn = cell.getNodeName();
                if (!"tc".equals(cl) && !"w:tc".equals(cn)) continue;
                out.append("<td>");
                NodeList ps = cell.getChildNodes();
                for (int k = 0; k < ps.getLength(); k++) {
                    Node cp = ps.item(k);
                    String pl = cp.getLocalName();
                    String pn = cp.getNodeName();
                    if ("p".equals(pl) || "w:p".equals(pn)) out.append(renderWordParagraph(cp));
                    else if ("tbl".equals(pl) || "w:tbl".equals(pn)) out.append(renderWordTable(cp));
                }
                out.append("</td>");
            }
            out.append("</tr>");
        }
        out.append("</table>");
        return out.toString();
    }

    private void appendWordInlineHtmlLimited(Node node, StringBuilder out, Node rootParagraph) {
        if (node == null) return;
        String local = node.getLocalName();
        String name = node.getNodeName();

        if (node != rootParagraph && ("p".equals(local) || "w:p".equals(name)
                || "tbl".equals(local) || "w:tbl".equals(name)
                || "txbxContent".equals(local) || "w:txbxContent".equals(name))) {
            return;
        }

        if ("t".equals(local) || "w:t".equals(name)) {
            out.append(escapeWordText(node));
            return;
        }
        if ("tab".equals(local) || "w:tab".equals(name)) {
            out.append("&emsp;");
            return;
        }
        if ("br".equals(local) || "w:br".equals(name) || "cr".equals(local) || "w:cr".equals(name)) {
            if (!isPageBreakNode(node)) out.append("<br>");
            return;
        }
        if ("blip".equals(local) || "a:blip".equals(name)) {
            String img = imageSourceForBlip(node);
            if (img != null) out.append("<img class=\"word-img\" src=\"").append(img).append("\"/>");
            return;
        }

        boolean run = "r".equals(local) || "w:r".equals(name);
        String runSuffix = "";
        if (run) {
            WordRunStyle style = readRunStyle(node);
            if (style.hasStyle()) {
                out.append("<span style=\"").append(style.css).append("\">");
                runSuffix = "</span>" + runSuffix;
            }
            if (style.bold) { out.append("<b>"); runSuffix = "</b>" + runSuffix; }
            if (style.italic) { out.append("<i>"); runSuffix = "</i>" + runSuffix; }
            if (style.underline) { out.append("<u>"); runSuffix = "</u>" + runSuffix; }
        }

        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            appendWordInlineHtmlLimited(children.item(i), out, rootParagraph);
        }

        if (run) out.append(runSuffix);
    }

    private void appendWordTextBoxes(Node node, StringBuilder out) {
        if (node == null) return;
        String local = node.getLocalName();
        String name = node.getNodeName();
        if ("txbxContent".equals(local) || "w:txbxContent".equals(name)) {
            StringBuilder box = new StringBuilder();
            NodeList children = node.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                String cl = child.getLocalName();
                String cn = child.getNodeName();
                if ("p".equals(cl) || "w:p".equals(cn)) box.append(renderWordParagraph(child));
                else if ("tbl".equals(cl) || "w:tbl".equals(cn)) box.append(renderWordTable(child));
            }
            if (box.length() > 0) out.append("<div class=\"textbox\">").append(box).append("</div>");
            return;
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) appendWordTextBoxes(children.item(i), out);
    }

    private boolean hasVisibleHtml(StringBuilder html) {
        if (html == null || html.length() == 0) return false;
        String text = html.toString().replaceAll("(?is)<[^>]+>", "")
                .replace("&nbsp;", " ").replace("&emsp;", " ").trim();
        return !text.isEmpty() || html.indexOf("<img") >= 0;
    }

    private String wordParagraphStyle(Node p) {
        StringBuilder css = new StringBuilder();
        Node pPr = firstDirectChildByLocalName(p, "pPr");
        if (pPr != null) {
            Node jc = firstDirectChildByLocalName(pPr, "jc");
            String align = attr(jc, "w:val", "val");
            if (align != null) {
                if ("center".equalsIgnoreCase(align)) css.append("text-align:center;");
                else if ("right".equalsIgnoreCase(align) || "end".equalsIgnoreCase(align)) css.append("text-align:right;");
                else if ("both".equalsIgnoreCase(align)) css.append("text-align:justify;");
            }
        }
        return css.length() > 0 ? " style=\"" + css + "\"" : "";
    }

    private static class WordRunStyle {
        boolean bold;
        boolean italic;
        boolean underline;
        String css = "";
        boolean hasStyle() { return css != null && css.length() > 0; }
    }

    private WordRunStyle readRunStyle(Node run) {
        WordRunStyle style = new WordRunStyle();
        Node props = firstDirectChildByLocalName(run, "rPr");
        if (props != null) {
            style.bold = firstDirectChildByLocalName(props, "b") != null;
            style.italic = firstDirectChildByLocalName(props, "i") != null;
            style.underline = firstDirectChildByLocalName(props, "u") != null;
            StringBuilder css = new StringBuilder();
            Node color = firstDirectChildByLocalName(props, "color");
            String colorVal = attr(color, "w:val", "val");
            if (colorVal != null && colorVal.matches("[0-9A-Fa-f]{6}")) css.append("color:#").append(colorVal).append(";");
            Node sz = firstDirectChildByLocalName(props, "sz");
            String szVal = attr(sz, "w:val", "val");
            if (szVal != null) {
                try {
                    double halfPoints = Double.parseDouble(szVal);
                    double px = Math.max(8.0, halfPoints / 2.0 * 1.3333);
                    css.append("font-size:").append(String.format(Locale.US, "%.1f", px)).append("px;");
                } catch (Exception ignored) {}
            }
            style.css = css.toString();
        }
        return style;
    }

    private String escapeWordText(Node textNode) {
        String raw = textNode.getTextContent();
        if (raw == null) return "";
        String escaped = escapeHtml(raw).replace("  ", " &nbsp;");
        if (raw.startsWith(" ")) escaped = "&nbsp;" + escaped.substring(1);
        if (raw.endsWith(" ") && escaped.length() > 0) escaped = escaped.substring(0, escaped.length() - 1) + "&nbsp;";
        return escaped;
    }

    private boolean isPageBreakNode(Node node) {
        if (node == null) return false;
        if (!"br".equals(node.getLocalName()) && !"w:br".equals(node.getNodeName())) return false;
        String type = attr(node, "w:type", "type");
        return "page".equalsIgnoreCase(type);
    }

    private String imageSourceForBlip(Node blip) {
        String rid = attr(blip, "r:embed", "embed");
        if (rid == null) rid = attr(blip, "r:link", "link");
        if (rid == null) return null;
        String target = wordRelationships.get(rid);
        if (target == null) return null;
        return "https://" + LOCAL_HOST + "/" + target;
    }

    private String attr(Node node, String qualified, String localName) {
        if (node == null || node.getAttributes() == null) return null;
        Node a = node.getAttributes().getNamedItem(qualified);
        if (a == null) a = node.getAttributes().getNamedItem(localName);
        if (a == null) a = node.getAttributes().getNamedItemNS("*", localName);
        return a != null ? a.getNodeValue() : null;
    }

    private void loadWordRelationships(ZipFile zip) {
        try {
            ZipEntry rels = zip.getEntry("word/_rels/document.xml.rels");
            if (rels == null) return;
            Document relDoc;
            try (InputStream is = zip.getInputStream(rels)) {
                relDoc = secureDocumentBuilder().parse(is);
            }
            NodeList relsList = relDoc.getElementsByTagName("Relationship");
            if (relsList.getLength() == 0) relsList = relDoc.getElementsByTagNameNS("*", "Relationship");
            for (int i = 0; i < relsList.getLength(); i++) {
                Node r = relsList.item(i);
                String id = attr(r, "Id", "Id");
                String target = attr(r, "Target", "Target");
                String mode = attr(r, "TargetMode", "TargetMode");
                if (id == null || target == null || "External".equalsIgnoreCase(mode)) continue;
                if (target.startsWith("/")) target = target.substring(1);
                else target = "word/" + target;
                wordRelationships.put(id, normalizeZipPath(target));
            }
        } catch (Exception ignored) {}
    }

    private boolean containsWordPageBreak(Node node) {
        if (node == null) return false;
        String local = node.getLocalName();
        String name = node.getNodeName();
        if ("lastRenderedPageBreak".equals(local) || "w:lastRenderedPageBreak".equals(name)) return true;
        if ("br".equals(local) || "w:br".equals(name)) {
            NamedNodeMap attrs = node.getAttributes();
            Node type = attrs != null ? attrs.getNamedItem("w:type") : null;
            if (type == null && attrs != null) type = attrs.getNamedItem("type");
            if ("page".equalsIgnoreCase(type != null ? type.getNodeValue() : null)) return true;
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (containsWordPageBreak(children.item(i))) return true;
        }
        return false;
    }

    private List<String> findEpubSpinePaths(ZipFile zip) {
        ArrayList<String> result = new ArrayList<>();
        try {
            ZipEntry containerEntry = zip.getEntry("META-INF/container.xml");
            if (containerEntry == null) return result;

            Document containerDoc;
            try (InputStream is = zip.getInputStream(containerEntry)) {
                containerDoc = secureDocumentBuilder().parse(is);
            }

            NodeList rootFiles = containerDoc.getElementsByTagName("rootfile");
            if (rootFiles.getLength() == 0) rootFiles = containerDoc.getElementsByTagNameNS("*", "rootfile");
            if (rootFiles.getLength() == 0) return result;

            Node rootFile = rootFiles.item(0);
            NamedNodeMap rootAttrs = rootFile.getAttributes();
            Node fullPathAttr = rootAttrs != null ? rootAttrs.getNamedItem("full-path") : null;
            if (fullPathAttr == null) return result;

            String opfPath = fullPathAttr.getNodeValue();
            ZipEntry opfEntry = zip.getEntry(opfPath);
            if (opfEntry == null) return result;

            Document opfDoc;
            try (InputStream is = zip.getInputStream(opfEntry)) {
                opfDoc = secureDocumentBuilder().parse(is);
            }

            String basePath = parentPath(opfPath);
            Map<String, String> manifest = new LinkedHashMap<>();
            NodeList items = opfDoc.getElementsByTagName("item");
            if (items.getLength() == 0) items = opfDoc.getElementsByTagNameNS("*", "item");
            for (int i = 0; i < items.getLength(); i++) {
                Node item = items.item(i);
                NamedNodeMap attrs = item.getAttributes();
                if (attrs == null) continue;
                Node id = attrs.getNamedItem("id");
                Node href = attrs.getNamedItem("href");
                if (id != null && href != null) {
                    manifest.put(id.getNodeValue(), normalizeZipPath(basePath + "/" + decodeHref(href.getNodeValue())));
                }
            }

            NodeList itemRefs = opfDoc.getElementsByTagName("itemref");
            if (itemRefs.getLength() == 0) itemRefs = opfDoc.getElementsByTagNameNS("*", "itemref");
            for (int i = 0; i < itemRefs.getLength(); i++) {
                Node itemRef = itemRefs.item(i);
                NamedNodeMap attrs = itemRef.getAttributes();
                if (attrs == null) continue;
                Node idRef = attrs.getNamedItem("idref");
                if (idRef == null) continue;
                String path = manifest.get(idRef.getNodeValue());
                if (path != null && isEpubHtmlPath(path) && zip.getEntry(path) != null) result.add(path);
            }
        } catch (Exception ignored) {}
        return result;
    }

    private List<String> findEpubHtmlEntries(ZipFile zip) {
        ArrayList<String> result = new ArrayList<>();
        java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (!entry.isDirectory() && isEpubHtmlPath(entry.getName())) result.add(entry.getName());
        }
        java.util.Collections.sort(result);
        return result;
    }

    private String readZipEntryString(ZipFile zip, ZipEntry entry) throws IOException {
        try (InputStream is = zip.getInputStream(entry)) {
            byte[] data = readAllBytes(is);
            return new String(data, StandardCharsets.UTF_8);
        }
    }

    private byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
        return out.toByteArray();
    }

    private DocumentBuilder secureDocumentBuilder() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setExpandEntityReferences(false);
        try { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); } catch (Exception ignored) {}
        try { factory.setFeature("http://xml.org/sax/features/external-general-entities", false); } catch (Exception ignored) {}
        try { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false); } catch (Exception ignored) {}
        try { factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false); } catch (Exception ignored) {}
        return factory.newDocumentBuilder();
    }

    private Node firstNodeByLocalName(Document doc, String localName) {
        NodeList list = doc.getElementsByTagNameNS("*", localName);
        if (list.getLength() > 0) return list.item(0);
        list = doc.getElementsByTagName("w:" + localName);
        if (list.getLength() > 0) return list.item(0);
        list = doc.getElementsByTagName(localName);
        if (list.getLength() > 0) return list.item(0);
        return null;
    }

    private Node firstDirectChildByLocalName(Node node, String localName) {
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (localName.equals(child.getLocalName()) || ("w:" + localName).equals(child.getNodeName())) return child;
        }
        return null;
    }

    private String titleFromHtml(String html) {
        if (html == null) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?is)<title[^>]*>(.*?)</title>").matcher(html);
        if (m.find()) return htmlToText(m.group(1)).trim();
        m = java.util.regex.Pattern.compile("(?is)<h1[^>]*>(.*?)</h1>").matcher(html);
        if (m.find()) return htmlToText(m.group(1)).trim();
        return "";
    }

    private String htmlToText(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("(?is)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replaceAll("\\s+", " ");
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private boolean isEpubHtmlPath(String path) {
        String lower = path == null ? "" : path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".xhtml") || lower.endsWith(".html") || lower.endsWith(".htm");
    }

    private String parentPath(String path) {
        if (path == null) return "";
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(0, slash) : "";
    }

    private String fileNameFromPath(String path) {
        if (path == null) return "";
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private String decodeHref(String href) {
        if (href == null) return "";
        try { return URLDecoder.decode(href, "UTF-8"); } catch (Exception e) { return href; }
    }

    private String normalizeZipPath(String path) {
        if (path == null) return "";
        String normalized = path.replace('\\', '/');
        while (normalized.contains("//")) normalized = normalized.replace("//", "/");
        ArrayList<String> parts = new ArrayList<>();
        for (String part : normalized.split("/")) {
            if (part.isEmpty() || ".".equals(part)) continue;
            if ("..".equals(part)) {
                if (!parts.isEmpty()) parts.remove(parts.size() - 1);
            } else {
                parts.add(part);
            }
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) out.append('/');
            out.append(parts.get(i));
        }
        return out.toString();
    }

    private String mimeForPath(String path) {
        String lower = path == null ? "" : path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".css")) return "text/css";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".ttf")) return "font/ttf";
        if (lower.endsWith(".otf")) return "font/otf";
        if (lower.endsWith(".woff")) return "font/woff";
        if (lower.endsWith(".woff2")) return "font/woff2";
        return "application/octet-stream";
    }
}
