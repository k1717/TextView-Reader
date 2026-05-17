package com.textview.reader;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.annotation.SuppressLint;
import android.text.InputType;
import android.view.MenuItem;
import android.view.KeyEvent;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.widget.TextViewCompat;

import com.textview.reader.adapter.BookmarkFolderAdapter;
import com.textview.reader.model.Bookmark;
import com.textview.reader.model.ReaderState;
import com.textview.reader.model.Theme;
import com.textview.reader.util.BookmarkManager;
import com.textview.reader.util.FileUtils;
import com.textview.reader.util.FontManager;
import com.textview.reader.util.PrefsManager;
import com.textview.reader.util.ThemeManager;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.text.DateFormat;
import java.util.Date;
import java.util.Enumeration;
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
    private static final String FONT_PREFIX = "/font/";
    private static final String DOCUMENT_FONT_DEFAULT = "document_default";
    private static final String FONT_OPTION_SYSTEM_CURRENT = "system_current";
    private static final int WORD_PARAGRAPHS_PER_PAGE = 28;
    // Match toolbar-triggered document popups to the Go to Page bottom offset.
    private static final int DOCUMENT_TOOLBAR_POPUP_Y_DP = 74;

    private Toolbar toolbar;
    private View documentAppBar;
    private View documentBottomChrome;
    private boolean documentChromeVisible = true;
    private WebView webView;
    private ProgressBar progressBar;
    private TextView pageStatus;
    private TextView prevButton;
    private TextView nextButton;
    private TextView searchButton;
    private TextView pageButton;
    private TextView bookmarkButton;
    private TextView moreButton;
    private int readerBg = Color.rgb(18, 18, 18);
    private int readerFg = Color.rgb(232, 234, 237);
    private int readerSub = Color.rgb(176, 176, 176);
    private int readerPanel = Color.rgb(32, 33, 36);
    private int readerLine = Color.rgb(84, 86, 90);
    private String lastAppliedDocumentThemeSignature = null;
    private boolean restoreDocumentScrollAfterThemeRefresh = false;
    private int pendingThemeRefreshScrollX = 0;
    private int pendingThemeRefreshScrollY = 0;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<Page> pages = new ArrayList<>();
    private BookmarkManager bookmarkManager;
    private PrefsManager prefs;
    private ZipFile resourceZip;
    private File localFile;
    private String filePath;
    private String fileName;
    private String docType = "Document";
    private int lastAppliedEpubLeftPaddingDp = Integer.MIN_VALUE;
    private int lastAppliedEpubRightPaddingDp = Integer.MIN_VALUE;
    private int lastAppliedEpubTopPaddingDp = Integer.MIN_VALUE;
    private int lastAppliedEpubBottomPaddingDp = Integer.MIN_VALUE;
    private int lastAppliedEpubBottomToolbarHeightPx = Integer.MIN_VALUE;
    private int lastAppliedEpubEffectiveBottomMarginPx = Integer.MIN_VALUE;
    private int currentPage = 0;
    private int pendingSlideDirection = 0;
    private int wordSwipeTouchSlop = 0;
    private float wordSwipeStartX = 0f;
    private float wordSwipeStartY = 0f;
    private boolean wordSwipeTriggered = false;
    private boolean wordSwipeMovedEnoughForParentDisallow = false;
    private boolean pageTurnInFlight = false;
    private GestureDetector documentGestureDetector;
    private int armedDocumentEdgeDirection = 0;
    private long armedDocumentEdgeTimeMs = 0L;
    private boolean wordGestureStartedAtLeftEdge = true;
    private boolean wordGestureStartedAtRightEdge = true;
    private volatile boolean wordSelectionActive = false;
    private volatile boolean activityDestroyed = false;
    private int loadGeneration = 0;
    private File selectedDocumentFontFile = null;
    private boolean epubHasDocumentFont = false;
    private boolean wordHasDocumentFont = false;
    private String wordDefaultFontFamily = null;
    private String documentFontOverride = null;
    private String activeDocumentSearchQuery = "";
    private int activeDocumentSearchPage = -1;
    private int activeDocumentSearchOrdinal = 0;
    private int activeDocumentSearchCountOnPage = 0;
    private int activeDocumentSearchTotal = 0;
    private boolean documentSearchSelectLastAfterCount = false;
    private TextView documentSearchStatusView = null;
    private final Runnable checkWordSelectionAfterScrollRunnable = this::checkWordSelectionAfterScroll;
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
        ViewerRegistry.activate(this);

        resolveReaderThemeColors();
        setContentView(R.layout.activity_document_page);
        applyDocumentSystemBarColors();

        // targetSdk 35 forces edge-to-edge regardless of setDecorFitsSystemWindows.
        // Without inset padding, the toolbar sits under the status bar and the
        // bottom button row sits under the 3-button navigation bar.
        com.textview.reader.util.EdgeToEdgeUtil.applyFoldableChromeInsets(this,
                findViewById(R.id.document_root),
                findViewById(R.id.document_appbar),
                findViewById(R.id.document_bottom_scroller),
                findViewById(R.id.document_viewport),
                () -> documentChromeVisible);
        applyDocumentSystemBarColors();

        documentAppBar = findViewById(R.id.document_appbar);
        documentBottomChrome = findViewById(R.id.document_bottom_scroller);
        if (documentBottomChrome != null) {
            documentBottomChrome.addOnLayoutChangeListener((v, left, top, right, bottom,
                    oldLeft, oldTop, oldRight, oldBottom) -> {
                if ("EPUB".equals(docType) && (bottom - top) != (oldBottom - oldTop)) {
                    applyEpubBoundaryMarginsIfNeeded();
                }
            });
        }
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
        searchButton = findViewById(R.id.btn_document_search);
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
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        loadFromIntent(intent);
    }


    private void updateLoadingIndicatorTheme() {
        if (progressBar == null) return;
        progressBar.setBackgroundColor(Color.TRANSPARENT);
        progressBar.setIndeterminateTintList(ColorStateList.valueOf(readerFg));
    }

    @Override
    protected void onResume() {
        super.onResume();
        ThemeManager.getInstance(this).reloadFromStorage();
        String currentThemeSignature = documentThemeSignature();
        boolean pageThemeChanged = lastAppliedDocumentThemeSignature != null
                && !lastAppliedDocumentThemeSignature.equals(currentThemeSignature);
        if (webView != null) {
            webView.onResume();
            webView.resumeTimers();
        }
        applyDocumentSystemBarColors();
        applyDocumentThemeToViews();
        refreshEpubSpacingIfNeeded();
        refreshDocumentPageThemeIfNeeded(currentThemeSignature, pageThemeChanged);
    }

    @Override
    protected void onPause() {
        saveReadingState();
        if (webView != null) {
            webView.removeCallbacks(checkWordSelectionAfterScrollRunnable);
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

    private String documentThemeSignature() {
        Theme theme = ThemeManager.getInstance(this).getActiveTheme();
        if (theme == null) {
            return "theme:null:" + readerBg + ":" + readerFg + ":" + readerLine
                    + "|epubFontSize=" + (("EPUB".equals(docType) && prefs != null)
                    ? prefs.getFontSize() : PrefsManager.DEFAULT_FONT_SIZE);
        }
        String backgroundImagePath = theme.getBackgroundImagePath();
        return theme.getId()
                + "|fg=" + theme.getTextColor()
                + "|bg=" + theme.getBackgroundColor()
                + "|link=" + theme.getLinkColor()
                + "|img=" + (backgroundImagePath != null ? backgroundImagePath : "")
                + "|alpha=" + theme.getBackgroundImageAlpha()
                + "|epubFontSize=" + (("EPUB".equals(docType) && prefs != null)
                ? prefs.getFontSize() : PrefsManager.DEFAULT_FONT_SIZE);
    }

    private void refreshDocumentPageThemeIfNeeded(String currentThemeSignature, boolean pageThemeChanged) {
        if (!pageThemeChanged) {
            lastAppliedDocumentThemeSignature = currentThemeSignature;
            return;
        }
        lastAppliedDocumentThemeSignature = currentThemeSignature;
        if (webView == null || pages.isEmpty() || currentPage < 0 || currentPage >= pages.size()) return;
        pendingThemeRefreshScrollX = webView.getScrollX();
        pendingThemeRefreshScrollY = webView.getScrollY();
        restoreDocumentScrollAfterThemeRefresh = true;
        clearDocumentEdgeArm();
        showPage(currentPage, 0);
    }

    private void restoreDocumentScrollAfterThemeRefreshIfNeeded(@NonNull WebView view) {
        if (!restoreDocumentScrollAfterThemeRefresh) return;
        final int restoreX = pendingThemeRefreshScrollX;
        final int restoreY = pendingThemeRefreshScrollY;
        restoreDocumentScrollAfterThemeRefresh = false;
        view.postDelayed(() -> {
            if (!activityDestroyed && webView != null) {
                webView.scrollTo(restoreX, restoreY);
            }
        }, 60);
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
        updateLoadingIndicatorTheme();
        TextView[] buttons = {prevButton, nextButton, searchButton, pageButton, bookmarkButton, moreButton};
        for (TextView b : buttons) {
            if (b == null) continue;
            b.setTextColor(readerFg);
            TextViewCompat.setCompoundDrawableTintList(b, android.content.res.ColorStateList.valueOf(readerFg));
        }
    }

    @Override
    protected void onDestroy() {
        ViewerRegistry.unregister(this);
        activityDestroyed = true;
        loadGeneration++;
        saveReadingState();
        clearDocumentSearchState(true);
        destroyDocumentWebView();
        closeResourceZip();
        pages.clear();
        wordRelationships.clear();
        executor.shutdownNow();
        super.onDestroy();
    }

    private boolean handleDocumentTapGesture(@NonNull MotionEvent event) {
        if (documentGestureDetector == null) return false;
        boolean handled = documentGestureDetector.onTouchEvent(event);
        // Do not consume ACTION_DOWN; the WebView still needs the original down
        // event for scrolling, text selection, and edge-swipe tracking.
        return handled && event.getActionMasked() != MotionEvent.ACTION_DOWN;
    }

    private void toggleDocumentChrome() {
        setDocumentChromeVisible(!documentChromeVisible);
    }

    private void setDocumentChromeVisible(boolean visible) {
        documentChromeVisible = visible;
        int visibility = visible ? View.VISIBLE : View.GONE;
        if (documentAppBar != null && documentAppBar.getVisibility() != visibility) {
            documentAppBar.setVisibility(visibility);
        }
        if (documentBottomChrome != null && documentBottomChrome.getVisibility() != visibility) {
            documentBottomChrome.setVisibility(visibility);
        }
        View viewport = findViewById(R.id.document_viewport);
        androidx.core.view.ViewCompat.requestApplyInsets(findViewById(R.id.document_root));
        if (viewport != null) viewport.requestLayout();
        applyEpubBoundaryMarginsIfNeeded();
    }

    private void destroyDocumentWebView() {
        if (webView == null) return;
        try {
            webView.removeCallbacks(checkWordSelectionAfterScrollRunnable);
            webView.removeCallbacks(releasePageTurnRunnable);
            webView.animate().cancel();
            webView.setOnTouchListener(null);
            webView.setOnScrollChangeListener(null);
            webView.setWebViewClient(null);
            webView.removeJavascriptInterface("TekviewSelectionBridge");
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
        webView.addJavascriptInterface(new WordSelectionBridge(), "TekviewSelectionBridge");
        webView.setFindListener((activeMatchOrdinal, numberOfMatches, isDoneCounting) -> {
            if (!isDoneCounting) return;
            activeDocumentSearchPage = currentPage;
            activeDocumentSearchCountOnPage = Math.max(0, numberOfMatches);
            activeDocumentSearchOrdinal = numberOfMatches > 0 ? Math.max(1, activeMatchOrdinal + 1) : 0;

            if (documentSearchSelectLastAfterCount && numberOfMatches > 0 && webView != null) {
                documentSearchSelectLastAfterCount = false;
                webView.post(() -> {
                    if (!activityDestroyed && webView != null) webView.findNext(false);
                });
                return;
            }

            updateDocumentSearchStatus(documentSearchStatusView);
        });
        webView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if ("Word".equals(docType) && Math.abs(scrollY - oldScrollY) > dpToPx(1)) {
                webView.removeCallbacks(checkWordSelectionAfterScrollRunnable);
                webView.postDelayed(checkWordSelectionAfterScrollRunnable, 90);
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(@NonNull WebView view, @NonNull WebResourceRequest request) {
                return interceptLocalResource(request.getUrl());
            }

            @Override
            public void onPageFinished(@NonNull WebView view, @NonNull String url) {
                super.onPageFinished(view, url);
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                installWordSelectionCleanupScript();
                applyDocumentSearchHighlightAfterPageLoad();
                runDocumentSlideInAnimation();
                restoreDocumentScrollAfterThemeRefreshIfNeeded(view);
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
        if (searchButton != null) searchButton.setOnClickListener(v -> showDocumentSearchDialog());
        if (pageButton != null) pageButton.setOnClickListener(v -> showGoToPageDialog());
        bookmarkButton.setOnClickListener(v -> showBookmarksDialog());
        if (moreButton != null) {
            moreButton.setOnClickListener(v -> showMoreDialog());
        }
    }

    // --- Hardware page-turn keys ---

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (handleDocumentPageTurnKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Fallback for devices that route hardware keys through onKeyDown() instead
        // of dispatchKeyEvent(). dispatchKeyEvent() normally consumes these first.
        if (handleDocumentPageTurnKey(event)) return true;
        return super.onKeyDown(keyCode, event);
    }

    private boolean handleDocumentPageTurnKey(KeyEvent event) {
        if (event == null || prefs == null || !prefs.getVolumeKeyScroll()) return false;

        int direction = pageTurnDirectionForKey(event.getKeyCode());
        if (direction == 0) return false;

        int action = event.getAction();
        if (action == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) {
                pageDocumentBy(direction);
            }
            return true;
        }

        // Consume ACTION_UP too so Android/e-reader firmware does not also treat
        // volume keys as volume changes after the app has used them for paging.
        return action == KeyEvent.ACTION_UP;
    }

    private int pageTurnDirectionForKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_PAGE_DOWN:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_SPACE:
            case KeyEvent.KEYCODE_FORWARD:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_BUTTON_R1:
            case KeyEvent.KEYCODE_NAVIGATE_NEXT:
                return +1;

            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_BUTTON_L1:
            case KeyEvent.KEYCODE_NAVIGATE_PREVIOUS:
                return -1;

            default:
                return 0;
        }
    }

    private void pageDocumentBy(int direction) {
        if (pages == null || pages.isEmpty()) return;
        int target = Math.max(0, Math.min(pages.size() - 1, currentPage + direction));
        if (target != currentPage) {
            showPage(target, Integer.compare(target, currentPage));
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void installSwipePaging() {
        wordSwipeTouchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        documentGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(@NonNull MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                toggleDocumentChrome();
                return true;
            }

            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                resetDocumentZoom();
                clearDocumentEdgeArm();
                return true;
            }
        });

        webView.setOnTouchListener((v, event) -> {
            if (handleDocumentTapGesture(event)) {
                return true;
            }

            if (!isPagedWebDocument() || pages.size() <= 1 || pageTurnInFlight) return false;

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    wordSwipeStartX = event.getX();
                    wordSwipeStartY = event.getY();
                    wordSwipeTriggered = false;
                    wordSwipeMovedEnoughForParentDisallow = false;
                    wordGestureStartedAtLeftEdge = !webView.canScrollHorizontally(-1);
                    wordGestureStartedAtRightEdge = !webView.canScrollHorizontally(1);
                    webView.removeCallbacks(checkWordSelectionAfterScrollRunnable);
                    webView.removeCallbacks(releasePageTurnRunnable);
                    return false;

                case MotionEvent.ACTION_MOVE:
                    if (wordSelectionActive) return false;
                    float dx = event.getX() - wordSwipeStartX;
                    float dy = event.getY() - wordSwipeStartY;
                    if (!wordSwipeMovedEnoughForParentDisallow
                            && Math.abs(dx) > wordSwipeTouchSlop
                            && Math.abs(dx) > Math.abs(dy) * 1.35f) {
                        wordSwipeMovedEnoughForParentDisallow = true;
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    if (!wordSwipeTriggered && shouldTurnDocumentPageBySwipe(event)) {
                        wordSwipeTriggered = true;
                        turnDocumentPageBySwipe(pageDeltaForHorizontalSwipe(event.getX() - wordSwipeStartX));
                        clearDocumentEdgeArm();
                        return true;
                    }
                    return false;

                case MotionEvent.ACTION_UP:
                    if (Math.abs(event.getX() - wordSwipeStartX) < wordSwipeTouchSlop
                            && Math.abs(event.getY() - wordSwipeStartY) < wordSwipeTouchSlop) {
                        v.performClick();
                    }
                    if (!wordSwipeTriggered && shouldTurnDocumentPageBySwipe(event)) {
                        wordSwipeTriggered = true;
                        turnDocumentPageBySwipe(pageDeltaForHorizontalSwipe(event.getX() - wordSwipeStartX));
                        clearDocumentEdgeArm();
                        return true;
                    }
                    resetWordSwipeTracking();
                    webView.postDelayed(checkWordSelectionAfterScrollRunnable, 120);
                    return false;

                case MotionEvent.ACTION_CANCEL:
                    resetWordSwipeTracking();
                    webView.postDelayed(checkWordSelectionAfterScrollRunnable, 120);
                    return false;

                default:
                    return false;
            }
        });
    }

    private void resetWordSwipeTracking() {
        wordSwipeTriggered = false;
        wordSwipeMovedEnoughForParentDisallow = false;
        wordGestureStartedAtLeftEdge = true;
        wordGestureStartedAtRightEdge = true;
        if (webView != null && webView.getParent() != null) {
            webView.getParent().requestDisallowInterceptTouchEvent(false);
        }
    }

    private boolean isPagedWebDocument() {
        return "Word".equals(docType) || "EPUB".equals(docType);
    }

    private void clearDocumentEdgeArm() {
        armedDocumentEdgeDirection = 0;
        armedDocumentEdgeTimeMs = 0L;
    }

    private boolean shouldTurnDocumentPageBySwipe(@NonNull MotionEvent event) {
        if (activityDestroyed || wordSelectionActive || webView == null || pages.size() <= 1 || pageTurnInFlight) return false;

        float dx = event.getX() - wordSwipeStartX;
        float dy = event.getY() - wordSwipeStartY;
        float absX = Math.abs(dx);
        float absY = Math.abs(dy);
        long duration = event.getEventTime() - event.getDownTime();

        // Slightly lighter than before so zoomed Word/EPUB pages do not feel
        // like they need multiple hard swipes. The edge rule below still prevents
        // accidental page turns while the WebView can pan horizontally.
        float threshold = Math.max(dpToPx(28), webView.getWidth() * 0.06f);
        if (!(absX >= threshold
                && absX > absY * 1.28f
                && absY <= dpToPx(78)
                && duration <= 850)) {
            return false;
        }

        int horizontalScrollDirection = dx < 0 ? 1 : -1;

        // Non-zoomed / normally wrapped pages should turn immediately on the first
        // swipe. The two-step edge threshold is used only when WebView actually has
        // horizontal scroll range, matching the PDF behavior for zoomed pages.
        if (!webView.canScrollHorizontally(-1) && !webView.canScrollHorizontally(1)) {
            return true;
        }

        if (webView.canScrollHorizontally(horizontalScrollDirection)) {
            clearDocumentEdgeArm();
            return false;
        }

        // If this is a fresh gesture that already started at the matching WebView
        // edge, allow page turn immediately. This keeps the first swipe from
        // jumping while panning to the edge, but avoids needing another arm+turn
        // cycle after the user is already resting at that edge.
        if ((horizontalScrollDirection > 0 && wordGestureStartedAtRightEdge)
                || (horizontalScrollDirection < 0 && wordGestureStartedAtLeftEdge)) {
            return true;
        }

        long now = event.getEventTime();

        // When the WebView is zoomed/expanded and the user reaches the horizontal
        // edge, do not turn the document page during the same drag. The old logic
        // could arm the edge on one ACTION_MOVE and then satisfy the armed-edge
        // condition on a later ACTION_MOVE from the same finger gesture, which made
        // the Word viewer jump to the next/previous page instead of stopping at the
        // WebView edge. Only a fresh gesture that starts after the edge was armed is
        // allowed to turn the page.
        boolean armedFromPreviousGesture = armedDocumentEdgeTimeMs > 0L
                && armedDocumentEdgeTimeMs < event.getDownTime();
        if (armedDocumentEdgeDirection == horizontalScrollDirection
                && armedFromPreviousGesture
                && now - armedDocumentEdgeTimeMs <= 600L) {
            return true;
        }

        armedDocumentEdgeDirection = horizontalScrollDirection;
        armedDocumentEdgeTimeMs = now;
        return false;
    }

    private int pageDeltaForHorizontalSwipe(float dx) {
        // Default EPUB direction follows Korean/Western books: swipe left = next.
        // The optional RTL mode supports Japanese-style right-to-left books: swipe right = next.
        if ("EPUB".equals(docType)
                && prefs != null
                && prefs.getEpubPageDirection() == PrefsManager.EPUB_PAGE_DIRECTION_RTL) {
            return dx > 0 ? 1 : -1;
        }
        return dx < 0 ? 1 : -1;
    }

    private int visualSlideDirectionForPageDelta(int pageDelta) {
        if (pageDelta == 0) return 0;
        if ("EPUB".equals(docType)
                && prefs != null
                && prefs.getEpubPageEffect() == PrefsManager.EPUB_PAGE_EFFECT_NONE) {
            return 0;
        }
        if ("EPUB".equals(docType)
                && prefs != null
                && prefs.getEpubPageDirection() == PrefsManager.EPUB_PAGE_DIRECTION_RTL) {
            return -pageDelta;
        }
        return pageDelta;
    }

    private void turnDocumentPageBySwipe(int direction) {
        if (webView == null || pageTurnInFlight) return;
        if (direction > 0 && currentPage < pages.size() - 1) {
            showPage(currentPage + 1, 1);
        } else if (direction < 0 && currentPage > 0) {
            showPage(currentPage - 1, -1);
        }
    }

    private void checkWordSelectionAfterScroll() {
        if (activityDestroyed || !"Word".equals(docType) || webView == null) return;
        webView.evaluateJavascript(
                "(function(){try{return !!(window.__tekviewClearSelectionIfOffscreen&&window.__tekviewClearSelectionIfOffscreen());}catch(e){return false;}})()",
                value -> {
                    if ("true".equals(value)) {
                        wordSelectionActive = false;
                    }
                });
    }

    private void installWordSelectionCleanupScript() {
        if (activityDestroyed || !"Word".equals(docType) || webView == null) return;
        webView.evaluateJavascript(
                "(function(){try{"
                        + "if(window.__tekviewSelectionCleanupInstalled){return true;}"
                        + "window.__tekviewSelectionCleanupInstalled=true;"
                        + "function sel(){return window.getSelection?window.getSelection():null;}"
                        + "function active(){var s=sel();return !!(s&&!s.isCollapsed&&s.rangeCount>0&&String(s).length>0);}"
                        + "function notify(){try{if(window.TekviewSelectionBridge){window.TekviewSelectionBridge.onSelectionChanged(active());}}catch(e){}}"
                        + "window.__tekviewClearSelectionIfOffscreen=function(){"
                        + "try{var s=sel();if(!s||s.isCollapsed||s.rangeCount===0||String(s).length===0){notify();return false;}"
                        + "var r=s.getRangeAt(0);var rects=Array.prototype.slice.call(r.getClientRects()).filter(function(x){return x&&x.width>0&&x.height>0;});"
                        + "if(!rects.length){s.removeAllRanges();notify();return true;}"
                        + "var w=window.innerWidth||document.documentElement.clientWidth||0;var h=window.innerHeight||document.documentElement.clientHeight||0;var m=8;"
                        + "var visible=rects.some(function(x){return x.bottom>=m&&x.top<=h-m&&x.right>=m&&x.left<=w-m;});"
                        + "if(!visible){s.removeAllRanges();notify();return true;}notify();return false;}catch(e){return false;}};"
                        + "document.addEventListener('selectionchange',function(){setTimeout(notify,0);},true);"
                        + "document.addEventListener('touchend',function(){setTimeout(notify,70);},true);"
                        + "document.addEventListener('mouseup',function(){setTimeout(notify,70);},true);"
                        + "window.addEventListener('scroll',function(){clearTimeout(window.__tekviewScrollCleanupTimer);window.__tekviewScrollCleanupTimer=setTimeout(window.__tekviewClearSelectionIfOffscreen,80);},{passive:true});"
                        + "setTimeout(notify,120);return true;}catch(e){return false;}})()",
                null);
    }

    private class WordSelectionBridge {
        @JavascriptInterface
        public void onSelectionChanged(boolean active) {
            wordSelectionActive = active;
        }
    }

    private void showMoreDialog() {
        ThemeManager.getInstance(this).reloadFromStorage();
        resolveReaderThemeColors();
        final android.app.Dialog[] dialogRef = new android.app.Dialog[1];
        LinearLayout box = makeDialogBox();
        box.addView(makeDialogTitle(getString(R.string.more)));
        box.addView(makeDialogActionRow(getString(R.string.font), () -> {
            if (dialogRef[0] != null) dialogRef[0].dismiss();
            showDocumentFontDialog();
        }));
        if ("EPUB".equals(docType)) {
            box.addView(makeDialogActionRow(getString(R.string.increase_font), () -> {
                if (dialogRef[0] != null) dialogRef[0].dismiss();
                changeEpubFontSize(2f);
            }));
            box.addView(makeDialogActionRow(getString(R.string.decrease_font), () -> {
                if (dialogRef[0] != null) dialogRef[0].dismiss();
                changeEpubFontSize(-2f);
            }));
            box.addView(makeDialogActionRow(getString(R.string.reset_font_size), () -> {
                if (dialogRef[0] != null) dialogRef[0].dismiss();
                resetEpubFontSize();
            }));
        }
        box.addView(makeDialogActionRow(getString(R.string.settings), () -> {
            if (dialogRef[0] != null) dialogRef[0].dismiss();
            startActivity(new Intent(this, SettingsActivity.class));
        }));
        box.addView(makeDialogActionRow(getString(R.string.file_info), () -> {
            if (dialogRef[0] != null) dialogRef[0].dismiss();
            showFileInfoDialog();
        }));
        addDialogBottomActions(box,
                getString(R.string.action_open_file), () -> {
                    if (dialogRef[0] != null) dialogRef[0].dismiss();
                    openFileBrowserFromViewer();
                },
                getString(R.string.close), () -> {
                    if (dialogRef[0] != null) dialogRef[0].dismiss();
                });
        dialogRef[0] = createStablePositionedDialog(box, DOCUMENT_TOOLBAR_POPUP_Y_DP, false, false);
        dialogRef[0].show();
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

    private int getEpubLeftPaddingDp() {
        return prefs != null ? prefs.getEpubLeftPaddingDp() : 30;
    }

    private int getEpubRightPaddingDp() {
        return prefs != null ? prefs.getEpubRightPaddingDp() : 30;
    }

    private int getEpubTopPaddingDp() {
        return prefs != null ? prefs.getEpubTopPaddingDp() : 0;
    }

    private int getEpubBottomPaddingDp() {
        return prefs != null ? prefs.getEpubBottomPaddingDp() : 0;
    }

    private void refreshEpubSpacingIfNeeded() {
        applyEpubBoundaryMarginsIfNeeded();
    }

    private int clampEpubBoundaryPx(int px) {
        int clamped = Math.max(0, Math.min(240, px));
        return Math.round(clamped / 5f) * 5;
    }

    private int getVisibleDocumentBottomToolbarHeightPx() {
        if (documentBottomChrome == null || documentBottomChrome.getVisibility() != View.VISIBLE) {
            return 0;
        }
        int height = documentBottomChrome.getHeight();
        if (height <= 0) height = documentBottomChrome.getMeasuredHeight();
        return Math.max(0, height);
    }

    private boolean isDocumentBottomToolbarHeightPending() {
        return documentBottomChrome != null
                && documentBottomChrome.getVisibility() == View.VISIBLE
                && documentBottomChrome.getHeight() <= 0
                && documentBottomChrome.getMeasuredHeight() <= 0;
    }

    private int getEffectiveEpubBottomMarginPx(int requestedBottomBoundaryPx, int bottomToolbarHeightPx) {
        if (!"EPUB".equals(docType) || requestedBottomBoundaryPx <= 0) return 0;
        if (isDocumentBottomToolbarHeightPending()) {
            return 0;
        }
        return Math.max(0, requestedBottomBoundaryPx - Math.max(0, bottomToolbarHeightPx));
    }

    private void applyEpubBoundaryMarginsIfNeeded() {
        if (webView == null) return;
        int left = "EPUB".equals(docType) ? clampEpubBoundaryPx(getEpubLeftPaddingDp()) : 0;
        int right = "EPUB".equals(docType) ? clampEpubBoundaryPx(getEpubRightPaddingDp()) : 0;
        int top = "EPUB".equals(docType) ? clampEpubBoundaryPx(getEpubTopPaddingDp()) : 0;
        int bottom = "EPUB".equals(docType) ? clampEpubBoundaryPx(getEpubBottomPaddingDp()) : 0;
        int bottomToolbarHeightPx = "EPUB".equals(docType) ? getVisibleDocumentBottomToolbarHeightPx() : 0;
        int effectiveBottomMarginPx = getEffectiveEpubBottomMarginPx(bottom, bottomToolbarHeightPx);
        if (left == lastAppliedEpubLeftPaddingDp
                && right == lastAppliedEpubRightPaddingDp
                && top == lastAppliedEpubTopPaddingDp
                && bottom == lastAppliedEpubBottomPaddingDp
                && bottomToolbarHeightPx == lastAppliedEpubBottomToolbarHeightPx
                && effectiveBottomMarginPx == lastAppliedEpubEffectiveBottomMarginPx) {
            return;
        }
        lastAppliedEpubLeftPaddingDp = left;
        lastAppliedEpubRightPaddingDp = right;
        lastAppliedEpubTopPaddingDp = top;
        lastAppliedEpubBottomPaddingDp = bottom;
        lastAppliedEpubBottomToolbarHeightPx = bottomToolbarHeightPx;
        lastAppliedEpubEffectiveBottomMarginPx = effectiveBottomMarginPx;

        ViewGroup.LayoutParams rawLp = webView.getLayoutParams();
        if (rawLp instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) rawLp;
            int leftPx = left;
            int rightPx = right;
            int topPx = top;
            int bottomPx = effectiveBottomMarginPx;
            if (lp.leftMargin != leftPx || lp.rightMargin != rightPx
                    || lp.topMargin != topPx || lp.bottomMargin != bottomPx) {
                lp.setMargins(leftPx, topPx, rightPx, bottomPx);
                webView.setLayoutParams(lp);
            }
        }
    }

    private void resetDocumentZoom() {
        if (webView == null) return;
        WebSettings settings = webView.getSettings();
        settings.setTextZoom(documentTextZoomPercent());
        while (webView.canZoomOut()) {
            webView.zoomOut();
        }
        clearDocumentEdgeArm();
    }

    private int documentTextZoomPercent() {
        if (!"EPUB".equals(docType) || prefs == null) return 100;
        float size = Math.max(8f, Math.min(48f, prefs.getFontSize()));
        return Math.max(50, Math.min(267, Math.round(size / PrefsManager.DEFAULT_FONT_SIZE * 100f)));
    }

    private void applyDocumentTextZoom() {
        if (webView == null) return;
        webView.getSettings().setTextZoom(documentTextZoomPercent());
    }

    private void changeEpubFontSize(float delta) {
        if (prefs == null) return;
        float newSize = Math.max(8f, Math.min(48f, prefs.getFontSize() + delta));
        prefs.setFontSize(newSize);
        refreshCurrentEpubTextSize();
    }

    private void resetEpubFontSize() {
        if (prefs == null) return;
        prefs.setFontSize(PrefsManager.DEFAULT_FONT_SIZE);
        refreshCurrentEpubTextSize();
    }

    private void refreshCurrentEpubTextSize() {
        applyDocumentTextZoom();
        clearDocumentEdgeArm();
        if (!pages.isEmpty() && currentPage >= 0 && currentPage < pages.size()) {
            showPage(currentPage, 0);
        }
    }

    private void showDocumentFontDialog() {
        showDocumentFontPickerDialog(getReadingFontOptions());
    }

    private void showDocumentFontPickerDialog(List<ReadingFontOption> fontOptions) {
        final int bg = dialogBg();
        final int fg = dialogFg();

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

        TextView title = makeDocumentFontDialogTitle(getString(R.string.select_font), bg, fg);
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
        actionRow.setBackground(positionedActionPanelBackground(
                dialogActionPanelFillColor(bg),
                dialogActionPanelLineColor(bg)));
        actionRow.setPadding(dpToPx(30), 0, dpToPx(30), 0);

        TextView addFont = makeDocumentDialogActionText(
                localizedText("Add font", "글꼴 추가"),
                fg,
                Gravity.CENTER_VERTICAL | Gravity.START);
        TextView cancel = makeDocumentDialogActionText(getString(R.string.cancel), fg,
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

        android.app.Dialog dialog = createDocumentFontDialog(panel, 460);
        preserveFontDialogHeaderBarrier(panel, scrollClip);

        String currentFont = currentDocumentFontSelection();
        for (ReadingFontOption option : fontOptions) {
            String label = getReadingFontLabel(option);
            boolean selected = option.value.equals(currentFont);

            TextView row = makeDocumentFontActionRow(label, fg, selected);
            row.setOnClickListener(v -> {
                dialog.dismiss();
                setDocumentFontSelection(option.value);
            });
            if (isRemovableUserFontValue(option.value)) {
                row.setOnLongClickListener(v -> {
                    showUserFontRemoveConfirm(option.value, label, () -> {
                        dialog.dismiss();
                        showDocumentFontDialog();
                    });
                    return true;
                });
            }
            list.addView(row);
        }

        addFont.setOnClickListener(v -> {
            dialog.dismiss();
            showAllDocumentFontsDialog();
        });
        cancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showAllDocumentFontsDialog() {
        final int bg = dialogBg();
        final int fg = dialogFg();
        final int sub = dialogSub();

        List<String> fontNames = new ArrayList<>();
        try {
            FontManager fontManager = FontManager.getInstance();
            if (!fontManager.isScanned()) fontManager.scanFontsSync(this);
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

        TextView title = makeDocumentFontDialogTitle(
                localizedText("All system fonts", "전체 시스템 글꼴"),
                bg,
                fg);
        title.setBackgroundColor(Color.TRANSPARENT);
        title.setPadding(title.getPaddingLeft(), title.getPaddingTop(),
                title.getPaddingRight(), dpToPx(8));
        header.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView hint = makeDocumentDialogLabel(
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
        actionRow.setBackground(positionedActionPanelBackground(
                dialogActionPanelFillColor(bg),
                dialogActionPanelLineColor(bg)));
        actionRow.setPadding(dpToPx(30), 0, dpToPx(30), 0);

        TextView cancel = makeDocumentDialogActionText(getString(R.string.cancel), fg,
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

        android.app.Dialog dialog = createDocumentFontDialog(panel, 460);
        preserveFontDialogHeaderBarrier(panel, scrollClip);

        String currentFont = currentDocumentFontSelection();
        if (fontNames.isEmpty()) {
            TextView empty = makeDocumentDialogLabel(
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

                TextView row = makeDocumentFontActionRow(label, fg, selected);
                row.setOnClickListener(v -> {
                    try {
                        FontManager.getInstance().addUserFont(this, fontName);
                    } catch (Throwable ignored) {
                        // Selecting the font should still work even if persisting the shortcut fails.
                    }
                    dialog.dismiss();
                    setDocumentFontSelection(value);
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
        LinearLayout box = makeDialogBox();
        box.addView(makeDialogTitle(localizedText("Remove font", "글꼴 삭제")));

        TextView message = new TextView(this);
        String safeLabel = label != null && !label.trim().isEmpty() ? label.trim() : value;
        message.setText(safeLabel + "\n\n" + localizedText(
                "Remove this user-added font from TextView Reader?",
                "이 사용자 추가 글꼴을 TextView Reader에서 삭제할까요?")
                + "\n" + localizedText(
                "System fonts and document files are not affected.",
                "시스템 글꼴과 문서 파일은 영향받지 않습니다."));
        message.setTextColor(dialogSub());
        message.setTextSize(14f);
        message.setLineSpacing(0f, 1.15f);
        message.setPadding(0, 0, 0, dpToPx(12));
        box.addView(message, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        final android.app.Dialog[] dialogRef = new android.app.Dialog[1];
        LinearLayout actions = new LinearLayout(this);
        actions.setTag("dialog_actions");
        actions.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.END);
        actions.setPadding(0, dpToPx(8), 0, 0);

        TextView cancel = new TextView(this);
        cancel.setText(getString(R.string.cancel));
        cancel.setTextColor(dialogSub());
        cancel.setTextSize(16f);
        cancel.setGravity(android.view.Gravity.CENTER);
        cancel.setPadding(dpToPx(14), 0, dpToPx(14), 0);

        TextView delete = new TextView(this);
        delete.setText(getString(R.string.delete));
        delete.setTextColor(!isDarkColor(dialogBg()) ? Color.rgb(95, 35, 35) : Color.rgb(255, 170, 170));
        delete.setTextSize(16f);
        delete.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        delete.setGravity(android.view.Gravity.CENTER);
        delete.setPadding(dpToPx(14), 0, dpToPx(14), 0);

        actions.addView(cancel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(46)));
        actions.addView(delete, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(46)));
        box.addView(actions, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        cancel.setOnClickListener(v -> {
            if (dialogRef[0] != null) dialogRef[0].dismiss();
        });
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
                if (normalizeReadingFontValue(currentDocumentFontSelection()).equals(normalizeReadingFontValue(value))) {
                    setDocumentFontSelection("default");
                }
                Toast.makeText(this, localizedText("Font removed", "글꼴을 삭제했습니다"), Toast.LENGTH_SHORT).show();
                if (dialogRef[0] != null) dialogRef[0].dismiss();
                if (afterRemove != null) afterRemove.run();
            } else {
                Toast.makeText(this, localizedText(
                        "This font cannot be removed from inside the app.",
                        "이 글꼴은 앱 안에서 삭제할 수 없습니다."), Toast.LENGTH_SHORT).show();
            }
        });

        dialogRef[0] = createStablePositionedDialog(box, DOCUMENT_TOOLBAR_POPUP_Y_DP, false, false);
        dialogRef[0].show();
    }

    private boolean shouldOfferDocumentDefaultFont() {
        return ("EPUB".equals(docType) && epubHasDocumentFont)
                || ("Word".equals(docType) && wordHasDocumentFont);
    }

    private String currentDocumentFontSelection() {
        if (DOCUMENT_FONT_DEFAULT.equals(documentFontOverride)) return DOCUMENT_FONT_DEFAULT;
        if (documentFontOverride != null && !documentFontOverride.trim().isEmpty()) {
            return normalizeReadingFontValue(documentFontOverride);
        }
        if (shouldOfferDocumentDefaultFont()) return DOCUMENT_FONT_DEFAULT;
        return normalizeReadingFontValue(prefs != null ? prefs.getFontFamily() : "default");
    }

    private void setDocumentFontSelection(String value) {
        if (DOCUMENT_FONT_DEFAULT.equals(value)) {
            documentFontOverride = DOCUMENT_FONT_DEFAULT;
        } else {
            String normalized = normalizeReadingFontValue(value);
            documentFontOverride = normalized;
            if (prefs != null) prefs.setFontFamily(normalized);
        }
        refreshCurrentDocumentFont();
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
        // The outer font-dialog frame owns the rounded corners/border. Making
        // the header rectangular prevents the visible double-border/blended edge.
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
        panel.setClipChildren(true);
        panel.setClipToPadding(true);
        scrollClip.setClipToOutline(false);
        if (scrollClip instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) scrollClip;
            group.setClipChildren(true);
            group.setClipToPadding(true);
        }
    }

    private TextView makeDocumentFontDialogTitle(String text, int bgColor, int fgColor) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextColor(fgColor);
        title.setTextSize(22f);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        title.setPadding(dpToPx(18), dpToPx(18), dpToPx(18), dpToPx(14));
        return title;
    }

    private TextView makeDocumentDialogLabel(String text, int color, float sp) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(sp);
        tv.setLineSpacing(0f, 1.05f);
        return tv;
    }

    private TextView makeDocumentDialogActionText(String text, int fgColor, int gravity) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(fgColor);
        tv.setTextSize(16f);
        tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tv.setGravity(gravity);
        tv.setSingleLine(true);
        tv.setPadding(0, 0, 0, 0);
        return tv;
    }

    private TextView makeDocumentFontActionRow(String text, int fgColor) {
        return makeDocumentFontActionRow(text, fgColor, false);
    }

    private TextView makeDocumentFontActionRow(String text, int fgColor, boolean selected) {
        TextView row = new TextView(this);
        row.setText(text);
        row.setTextColor(fgColor);
        row.setTextSize(16f);
        row.setTypeface(selected ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
        row.setGravity(Gravity.CENTER_VERTICAL);
        // Keep the rounded row background but remove the radio/circle icon.
        // This matches the intended TXT/EPUB/Word font picker style.
        row.setPadding(dpToPx(18), 0, dpToPx(18), 0);
        row.setCompoundDrawables(null, null, null, null);
        row.setCompoundDrawablePadding(0);
        GradientDrawable bg = new GradientDrawable();
        int panel = dialogPanel();
        boolean darkPanel = isDarkColor(panel);
        int normalFill = blendColors(panel, fgColor, darkPanel ? 0.055f : 0.035f);
        int selectedFill = blendColors(panel, fgColor, darkPanel ? 0.120f : 0.075f);
        int normalStroke = blendColors(panel, fgColor, darkPanel ? 0.130f : 0.100f);
        int selectedStroke = blendColors(panel, fgColor, darkPanel ? 0.420f : 0.360f);

        // Keep every font row as a rounded card. Selection is indicated only by
        // stronger text weight and outline; no radio/circle icon is used.
        bg.setColor(selected ? selectedFill : normalFill);
        bg.setCornerRadius(dpToPx(10));
        bg.setStroke(1, selected ? selectedStroke : normalStroke);
        row.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(48));
        lp.setMargins(0, 0, 0, dpToPx(8));
        row.setLayoutParams(lp);
        return row;
    }

    private GradientDrawable positionedActionPanelBackground(int fill, int line) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        // Let the outer rounded frame clip the bottom corners. This removes the
        // extra color-blend strip between the border and the bottom action row.
        drawable.setCornerRadius(0f);
        return drawable;
    }

    private View fontDialogBottomSeparator(int bgColor) {
        View separator = new View(this);
        separator.setBackgroundColor(dialogActionPanelLineColor(bgColor));
        separator.setClickable(false);
        separator.setFocusable(false);
        return separator;
    }

    private int dialogActionPanelFillColor(int bgColor) {
        // Keep the bottom action panel the same color as the main font dialog.
        // A separate panel color created a visible blended strip near the bottom border.
        return bgColor;
    }

    private int dialogActionPanelLineColor(int bgColor) {
        return readerLine;
    }

    private Drawable fontDialogOuterBackground(int bgColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(bgColor);
        drawable.setCornerRadius(dpToPx(16));
        return drawable;
    }

    private Drawable fontDialogOuterBorderOverlay(int bgColor) {
        final int borderColor = dialogActionPanelLineColor(bgColor);
        final float strokeWidth = 1f;
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

    private android.app.Dialog createDocumentFontDialog(@NonNull View content, int maxWidthDp) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        final int bg = dialogBg();

        FrameLayout outerFrame = new FrameLayout(this);
        outerFrame.setBackground(fontDialogOuterBackground(bg));
        outerFrame.setForeground(fontDialogOuterBorderOverlay(bg));
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
            android.view.WindowManager.LayoutParams lp = new android.view.WindowManager.LayoutParams();
            lp.copyFrom(window.getAttributes());
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int cappedWidth = Math.min(Math.round(screenWidth * 0.85f), dpToPx(maxWidthDp));
            lp.width = Math.max(dpToPx(220), cappedWidth);
            lp.height = android.view.WindowManager.LayoutParams.WRAP_CONTENT;
            lp.y = dpToPx(DOCUMENT_TOOLBAR_POPUP_Y_DP);
            lp.dimAmount = 0.16f;
            window.setAttributes(lp);
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        return dialog;
    }

    private void positionFontDialogForThumbReach(@NonNull androidx.appcompat.app.AlertDialog dialog, int maxWidthDp) {
        if (dialog.getWindow() == null) return;
        android.view.Window window = dialog.getWindow();
        window.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        android.view.WindowManager.LayoutParams lp = new android.view.WindowManager.LayoutParams();
        lp.copyFrom(window.getAttributes());
        lp.width = txtReaderDialogWidthPx();
        lp.height = android.view.WindowManager.LayoutParams.WRAP_CONTENT;
        lp.y = dpToPx(DOCUMENT_TOOLBAR_POPUP_Y_DP);
        window.setAttributes(lp);
    }

    private TextView makeDialogActionRow(String text, Runnable action) {
        TextView row = new TextView(this);
        row.setText(text);
        row.setTextColor(dialogFg());
        row.setTextSize(16f);
        row.setGravity(android.view.Gravity.CENTER);
        row.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        row.setPadding(0, 0, 0, 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(dialogPanel());
        bg.setCornerRadius(dpToPx(10));
        row.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(48));
        lp.setMargins(0, 0, 0, dpToPx(8));
        row.setLayoutParams(lp);
        row.setOnClickListener(v -> action.run());
        return row;
    }

    private void refreshCurrentDocumentFont() {
        clearDocumentEdgeArm();
        if (!pages.isEmpty() && currentPage >= 0 && currentPage < pages.size()) {
            showPage(currentPage, 0);
        }
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

    private List<ReadingFontOption> getReadingFontOptions() {
        List<ReadingFontOption> options = new ArrayList<>();
        if (shouldOfferDocumentDefaultFont()) {
            options.add(new ReadingFontOption(DOCUMENT_FONT_DEFAULT, "Default font", "기본 글꼴"));
        }
        options.add(new ReadingFontOption("default", "System Sans (recommended)", "시스템 산세리프 (추천)"));
        options.add(new ReadingFontOption(FONT_OPTION_SYSTEM_CURRENT, "Current system font", "현재 시스템 글꼴"));
        options.add(new ReadingFontOption("korean_sans", "Korean/System Sans", "한글 산세리프"));
        options.add(new ReadingFontOption("korean_serif", "Korean/System Serif", "한글 명조/세리프"));
        options.add(new ReadingFontOption("serif", "Serif", "세리프"));
        options.add(new ReadingFontOption("monospace", "Monospace", "고정폭"));
        options.add(new ReadingFontOption("sans_medium", "Sans Medium", "산세리프 미디엄"));
        options.add(new ReadingFontOption("sans_condensed", "Sans Condensed", "산세리프 압축"));
        options.add(new ReadingFontOption("sans_light", "Sans Light", "산세리프 라이트"));
        addDocumentUserFontOptions(options);

        String current = currentDocumentFontSelection();
        if (!DOCUMENT_FONT_DEFAULT.equals(current) && !isCuratedReadingFontValue(current) && !containsReadingFontOption(options, current)) {
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
        return options;
    }

    private void addDocumentUserFontOptions(@NonNull List<ReadingFontOption> options) {
        try {
            FontManager fontManager = FontManager.getInstance();
            if (!fontManager.isScanned()) fontManager.scanFontsSync(this);

            for (String fontName : fontManager.getUserAddedFontNames(this)) {
                if (fontName == null || fontName.trim().isEmpty()) continue;
                String value = normalizeReadingFontValue(fontName);
                if (isCuratedReadingFontValue(value) || containsReadingFontOption(options, value)) continue;
                options.add(new ReadingFontOption(value,
                        "Added font: " + fontName,
                        "추가한 글꼴: " + fontName));
            }
        } catch (Throwable ignored) {
            // Font scanning should not block the Word/EPUB More menu.
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
            case DOCUMENT_FONT_DEFAULT:
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
        return localizedText(option.englishLabel, option.koreanLabel);
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
            case DOCUMENT_FONT_DEFAULT:
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

    private String buildDocumentFontCss() {
        selectedDocumentFontFile = null;
        String value = currentDocumentFontSelection();
        if (DOCUMENT_FONT_DEFAULT.equals(value)) {
            if ("Word".equals(docType)) return wordDefaultFontRule();
            return "";
        }
        String fallback = documentFontFallbackCss(value);
        String family = fallback;
        String filePath = resolveDocumentFontFilePath(value);
        if (filePath != null) {
            selectedDocumentFontFile = new File(filePath);
            family = "'TekviewSelectedDocumentFont', " + fallback;
            return "@font-face{font-family:'TekviewSelectedDocumentFont';src:url('https://" + LOCAL_HOST + FONT_PREFIX + "selected');}" +
                    documentFontRule(family);
        }
        if (FontManager.isSystemFamilyValue(value)) {
            String name = FontManager.getSystemFamilyName(value);
            if (!name.isEmpty()) family = "'" + cssQuote(name) + "', " + fallback;
        } else if (!isCuratedReadingFontValue(value)) {
            family = "'" + cssQuote(value) + "', " + fallback;
        }
        return documentFontRule(family);
    }

    private String documentFontRule(String family) {
        return "body,.page,p,div,span,td,th,li,pre{font-family:" + family + " !important;}";
    }

    private String wordDefaultFontRule() {
        if (wordDefaultFontFamily == null || wordDefaultFontFamily.trim().isEmpty()) return "";
        String family = "'" + cssQuote(wordDefaultFontFamily.trim()) + "', " + wordFontFallbackFamily(wordDefaultFontFamily);
        return "body,.page,p,div,span,td,th,li,pre{font-family:" + family + ";}";
    }

    private String wordFontFallbackFamily(String family) {
        String lower = family == null ? "" : family.toLowerCase(Locale.US);
        if (lower.contains("serif") || lower.contains("명조") || lower.contains("times") || lower.contains("batang")) {
            return "serif";
        }
        if (lower.contains("mono") || lower.contains("courier") || lower.contains("consolas")) {
            return "monospace";
        }
        return "sans-serif";
    }

    private String documentFontFallbackCss(String value) {
        switch (normalizeReadingFontValue(value)) {
            case "serif":
            case "korean_serif":
                return "serif";
            case "monospace":
                return "monospace";
            case "sans_medium":
                return "'sans-serif-medium', sans-serif";
            case "sans_condensed":
                return "'sans-serif-condensed', sans-serif";
            case "sans_light":
                return "'sans-serif-light', sans-serif";
            case "default":
            case DOCUMENT_FONT_DEFAULT:
            case "system_current":
            case "korean_sans":
            default:
                return "sans-serif";
        }
    }

    private String resolveDocumentFontFilePath(String value) {
        if (isCuratedReadingFontValue(value) || FontManager.isSystemFamilyValue(value)) return null;
        try {
            FontManager fontManager = FontManager.getInstance();
            if (!fontManager.isScanned()) fontManager.scanFontsSync(this);
            String path = fontManager.getFontPathForName(value);
            if (path != null && new File(path).isFile()) return path;
        } catch (Throwable ignored) {
            // Fall back to CSS family name if the font file is not directly readable.
        }
        return null;
    }

    private WebResourceResponse interceptSelectedDocumentFont() {
        File fontFile = selectedDocumentFontFile;
        if (fontFile == null || !fontFile.isFile()) return null;
        try {
            return new WebResourceResponse(
                    mimeForPath(fontFile.getName()),
                    "UTF-8",
                    new FileInputStream(fontFile));
        } catch (IOException e) {
            return null;
        }
    }

    private String cssQuote(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\").replace("'", "\\'");
    }

    private String localizedText(String english, String korean) {
        return "ko".equalsIgnoreCase(Locale.getDefault().getLanguage()) ? korean : english;
    }


    private void showDocumentSearchDialog() {
        final int bg = dialogBg();
        final int fg = dialogFg();
        final int sub = dialogSub();

        FrameLayout titleBox = new FrameLayout(this);
        titleBox.setPadding(dpToPx(22), dpToPx(18), dpToPx(22), dpToPx(8));
        titleBox.setBackgroundColor(Color.TRANSPARENT);

        TextView title = new TextView(this);
        title.setText(getString(R.string.find_in_text));
        title.setTextColor(fg);
        title.setTextSize(20f);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        titleBox.addView(title, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        TextView matchStatus = new TextView(this);
        matchStatus.setText("0 / 0");
        matchStatus.setTextColor(sub);
        matchStatus.setTextSize(12f);
        matchStatus.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        titleBox.addView(matchStatus, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL | Gravity.END));
        documentSearchStatusView = matchStatus;

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(Color.TRANSPARENT);
        box.setPadding(dpToPx(24), dpToPx(12), dpToPx(24), dpToPx(8));

        EditText input = makeDialogInput(getString(R.string.search_text_hint));
        String rememberedQuery = activeDocumentSearchQuery;
        if ((rememberedQuery == null || rememberedQuery.isEmpty()) && prefs != null) {
            rememberedQuery = prefs.getLastReaderSearchQuery();
        }
        if (rememberedQuery == null) rememberedQuery = "";
        input.setText(rememberedQuery);
        if (!rememberedQuery.isEmpty()) {
            input.setSelection(input.getText().length());
            activeDocumentSearchTotal = countDocumentMatches(rememberedQuery);
            updateDocumentSearchStatus(matchStatus);
        }
        box.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(52)));

        TextView hint = new TextView(this);
        hint.setText(getString(R.string.search_hint_multiple));
        hint.setTextColor(sub);
        hint.setTextSize(12f);
        hint.setGravity(Gravity.START);
        hint.setPadding(0, dpToPx(6), 0, dpToPx(8));
        box.addView(hint, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);
        buttons.setPadding(0, dpToPx(8), 0, 0);

        TextView prevButton = makeDocumentSearchDialogButton(getString(R.string.find_previous), fg);
        TextView closeButton = makeDocumentSearchDialogButton(getString(R.string.close), fg);
        TextView nextButton = makeDocumentSearchDialogButton(getString(R.string.find_next), fg);

        buttons.addView(prevButton, new LinearLayout.LayoutParams(0, dpToPx(44), 1f));
        buttons.addView(closeButton, new LinearLayout.LayoutParams(0, dpToPx(44), 1f));
        buttons.addView(nextButton, new LinearLayout.LayoutParams(0, dpToPx(44), 1f));
        box.addView(buttons, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout panel = makeDialogBox();
        panel.setPadding(0, 0, 0, dpToPx(8));
        panel.addView(titleBox, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        panel.addView(box, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        android.app.Dialog dialog = createStablePositionedDialog(panel, DOCUMENT_TOOLBAR_POPUP_Y_DP, true, false);

        prevButton.setOnClickListener(v -> performDocumentSearchMove(
                input.getText() != null ? input.getText().toString() : "", false, matchStatus));
        nextButton.setOnClickListener(v -> performDocumentSearchMove(
                input.getText() != null ? input.getText().toString() : "", true, matchStatus));
        closeButton.setOnClickListener(v -> dialog.dismiss());

        input.setOnEditorActionListener((v, actionId, event) -> {
            performDocumentSearchMove(input.getText() != null ? input.getText().toString() : "", true, matchStatus);
            return true;
        });

        dialog.setOnDismissListener(d -> {
            if (prefs != null) {
                prefs.setLastReaderSearchQuery(input.getText() != null ? input.getText().toString() : "");
            }
            documentSearchStatusView = null;
            clearDocumentSearchState(true);
        });
        dialog.show();
    }

    private TextView makeDocumentSearchDialogButton(String label, int fg) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextColor(fg);
        button.setTextSize(14f);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dpToPx(4), 0, dpToPx(4), 0);
        button.setBackgroundColor(Color.TRANSPARENT);
        return button;
    }

    private void performDocumentSearchMove(String rawQuery, boolean forward, TextView matchStatus) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.isEmpty()) {
            if (prefs != null) prefs.setLastReaderSearchQuery("");
            clearDocumentSearchState(true);
            if (matchStatus != null) matchStatus.setText("0 / 0");
            Toast.makeText(this, getString(R.string.enter_search_text), Toast.LENGTH_SHORT).show();
            return;
        }

        activeDocumentSearchTotal = countDocumentMatches(query);
        if (activeDocumentSearchTotal <= 0) {
            activeDocumentSearchQuery = query;
            activeDocumentSearchPage = -1;
            activeDocumentSearchOrdinal = 0;
            activeDocumentSearchCountOnPage = 0;
            if (prefs != null) prefs.setLastReaderSearchQuery(query);
            clearWebViewDocumentMatches();
            if (matchStatus != null) matchStatus.setText("0 / 0");
            Toast.makeText(this, getString(R.string.not_found), Toast.LENGTH_SHORT).show();
            return;
        }

        boolean queryChanged = !query.equalsIgnoreCase(activeDocumentSearchQuery == null ? "" : activeDocumentSearchQuery);
        activeDocumentSearchQuery = query;
        if (prefs != null) prefs.setLastReaderSearchQuery(query);

        if (queryChanged || activeDocumentSearchPage != currentPage || activeDocumentSearchCountOnPage <= 0) {
            if (pageContainsDocumentQuery(currentPage, query)) {
                applyDocumentSearchHighlight(query, forward);
                updateDocumentSearchStatus(matchStatus);
                return;
            }
        } else {
            boolean canMoveWithinPage = forward
                    ? activeDocumentSearchOrdinal < activeDocumentSearchCountOnPage
                    : activeDocumentSearchOrdinal > 1;
            if (canMoveWithinPage && webView != null) {
                webView.findNext(forward);
                return;
            }
        }

        int target = findNextDocumentSearchPage(query, currentPage, forward);
        if (target >= 0) {
            activeDocumentSearchPage = target;
            showPage(target, Integer.compare(target, currentPage));
            documentSearchSelectLastAfterCount = !forward;
            updateDocumentSearchStatus(matchStatus);
        } else {
            Toast.makeText(this, getString(R.string.not_found), Toast.LENGTH_SHORT).show();
            updateDocumentSearchStatus(matchStatus);
        }
    }

    private void applyDocumentSearchHighlightAfterPageLoad() {
        if (webView == null || activeDocumentSearchQuery == null || activeDocumentSearchQuery.trim().isEmpty()) return;
        if (!pageContainsDocumentQuery(currentPage, activeDocumentSearchQuery)) {
            clearWebViewDocumentMatches();
            updateDocumentSearchStatus(documentSearchStatusView);
            return;
        }
        webView.postDelayed(() -> {
            if (!activityDestroyed) applyDocumentSearchHighlight(activeDocumentSearchQuery, !documentSearchSelectLastAfterCount);
        }, 60);
    }

    private void applyDocumentSearchHighlight(String query, boolean forward) {
        if (webView == null || query == null || query.trim().isEmpty()) return;
        activeDocumentSearchPage = currentPage;
        documentSearchSelectLastAfterCount = !forward;
        webView.clearMatches();
        webView.findAllAsync(query);
    }

    private int findNextDocumentSearchPage(String query, int fromPage, boolean forward) {
        if (pages.isEmpty() || query == null || query.trim().isEmpty()) return -1;
        int count = pages.size();
        for (int step = 1; step <= count; step++) {
            int idx = forward
                    ? (fromPage + step) % count
                    : (fromPage - step + count) % count;
            if (pageContainsDocumentQuery(idx, query)) return idx;
        }
        return -1;
    }

    private boolean pageContainsDocumentQuery(int pageIndex, String query) {
        if (pageIndex < 0 || pageIndex >= pages.size() || query == null || query.trim().isEmpty()) return false;
        String text = htmlToText(pages.get(pageIndex).html).toLowerCase(Locale.ROOT);
        return text.contains(query.trim().toLowerCase(Locale.ROOT));
    }

    private int countDocumentMatches(String query) {
        if (query == null || query.trim().isEmpty()) return 0;
        String needle = query.trim().toLowerCase(Locale.ROOT);
        int total = 0;
        for (Page page : pages) {
            total += countOccurrencesIgnoreCase(htmlToText(page.html), needle);
        }
        return total;
    }

    private int countDocumentMatchesBeforePage(String query, int pageIndex) {
        if (query == null || query.trim().isEmpty()) return 0;
        String needle = query.trim().toLowerCase(Locale.ROOT);
        int total = 0;
        for (int i = 0; i < Math.min(pageIndex, pages.size()); i++) {
            total += countOccurrencesIgnoreCase(htmlToText(pages.get(i).html), needle);
        }
        return total;
    }

    private int countOccurrencesIgnoreCase(String text, String lowerNeedle) {
        if (text == null || lowerNeedle == null || lowerNeedle.isEmpty()) return 0;
        String haystack = text.toLowerCase(Locale.ROOT);
        int total = 0;
        int pos = 0;
        while ((pos = haystack.indexOf(lowerNeedle, pos)) >= 0) {
            total++;
            pos += Math.max(1, lowerNeedle.length());
        }
        return total;
    }

    private void updateDocumentSearchStatus(TextView matchStatus) {
        if (matchStatus == null) return;
        if (activeDocumentSearchQuery == null || activeDocumentSearchQuery.trim().isEmpty()) {
            matchStatus.setText("0 / 0");
            return;
        }
        if (activeDocumentSearchTotal <= 0) {
            activeDocumentSearchTotal = countDocumentMatches(activeDocumentSearchQuery);
        }
        int pageOrdinal = Math.max(0, activeDocumentSearchOrdinal);
        int globalOrdinal = pageOrdinal > 0
                ? countDocumentMatchesBeforePage(activeDocumentSearchQuery, currentPage) + pageOrdinal
                : 0;
        matchStatus.setText(String.format(Locale.getDefault(), "%d / %d", globalOrdinal, Math.max(0, activeDocumentSearchTotal)));
    }

    private void clearDocumentSearchState(boolean clearWebView) {
        activeDocumentSearchQuery = "";
        activeDocumentSearchPage = -1;
        activeDocumentSearchOrdinal = 0;
        activeDocumentSearchCountOnPage = 0;
        activeDocumentSearchTotal = 0;
        documentSearchSelectLastAfterCount = false;
        if (clearWebView) clearWebViewDocumentMatches();
    }

    private void clearWebViewDocumentMatches() {
        if (webView == null) return;
        try {
            webView.clearMatches();
        } catch (Throwable ignored) {
            // WebView search cleanup should not crash the viewer.
        }
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
        showCustomDialog(box, getString(R.string.close), false);
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

        final android.app.Dialog[] dialogRef = new android.app.Dialog[1];
        addDialogBottomActions(box, getString(R.string.go), () -> {
            try {
                int target = Integer.parseInt(input.getText().toString().trim());
                if (target < 1 || target > pages.size()) {
                    Toast.makeText(this, getString(R.string.page_range_error, pages.size()), Toast.LENGTH_SHORT).show();
                    return;
                }
                showPage(target - 1, Integer.compare(target - 1, currentPage));
                if (dialogRef[0] != null) dialogRef[0].dismiss();
            } catch (Exception ignored) {
                Toast.makeText(this, getString(R.string.invalid_page_number), Toast.LENGTH_SHORT).show();
            }
        });
        dialogRef[0] = createStablePositionedDialog(box, DOCUMENT_TOOLBAR_POPUP_Y_DP, true, false);
        dialogRef[0].show();
    }

    private int txtReaderDialogWidthPx() {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        return Math.max(dpToPx(220), Math.min(Math.round(screenWidth * 0.85f), dpToPx(460)));
    }

    private int legacyBookmarkDialogWidthPx() {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        return Math.min(screenWidth - dpToPx(14), dpToPx(460));
    }

    private void positionPageDialogForThumbReach(@NonNull androidx.appcompat.app.AlertDialog dialog) {
        if (dialog.getWindow() == null) return;
        android.view.Window window = dialog.getWindow();
        window.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
        android.view.WindowManager.LayoutParams lp = new android.view.WindowManager.LayoutParams();
        lp.copyFrom(window.getAttributes());
        lp.width = txtReaderDialogWidthPx();
        lp.height = android.view.WindowManager.LayoutParams.WRAP_CONTENT;
        lp.y = dpToPx(DOCUMENT_TOOLBAR_POPUP_Y_DP);
        window.setAttributes(lp);
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private void positionBookmarkDialogForThumbReach(@NonNull androidx.appcompat.app.AlertDialog dialog) {
        if (dialog.getWindow() == null) return;
        android.view.Window window = dialog.getWindow();
        window.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
        android.view.WindowManager.LayoutParams lp = new android.view.WindowManager.LayoutParams();
        lp.copyFrom(window.getAttributes());
        lp.width = legacyBookmarkDialogWidthPx();
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
        title.setGravity(Gravity.CENTER);
        title.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        title.setPadding(0, 0, 0, dpToPx(12));
        return title;
    }

    private EditText makeDialogInput(String hint) {
        int overlay = !isDarkColor(dialogBg())
                ? R.style.ThemeOverlay_TextView_ReaderDialogLight
                : R.style.ThemeOverlay_TextView_ReaderDialogDark;
        android.view.ContextThemeWrapper themed = new android.view.ContextThemeWrapper(this, overlay);

        EditText input = new EditText(themed);
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
        tintDocumentDialogEditHandles(input);
        return input;
    }

    private void tintDocumentDialogEditHandles(EditText input) {
        if (input == null) return;

        boolean lightDialog = !isDarkColor(dialogBg());
        int accent = lightDialog ? Color.rgb(34, 34, 34) : Color.WHITE;
        input.setHighlightColor(blendColors(dialogBg(), accent, lightDialog ? 0.24f : 0.42f));

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            GradientDrawable cursor = new GradientDrawable();
            cursor.setColor(accent);
            cursor.setSize(Math.max(2, dpToPx(2)), dpToPx(28));
            input.setTextCursorDrawable(cursor);
        }
    }

    private void tintSeekBar(SeekBar seekBar) {
        int accent = readerFg;
        int track = readerLine;
        seekBar.setThumbTintList(android.content.res.ColorStateList.valueOf(accent));
        seekBar.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
        seekBar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(track));
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

    private void showCustomDialog(LinearLayout box, String closeText) {
        showCustomDialog(box, closeText, false);
    }

    private void showCustomDialog(LinearLayout box, String closeText, boolean oneHandLower) {
        final android.app.Dialog[] dialogRef = new android.app.Dialog[1];
        addDialogBottomActions(box, closeText, () -> {
            if (dialogRef[0] != null) dialogRef[0].dismiss();
        });
        dialogRef[0] = createStablePositionedDialog(box, DOCUMENT_TOOLBAR_POPUP_Y_DP, false, false);
        dialogRef[0].show();
    }

    private android.app.Dialog createStablePositionedDialog(@NonNull View content,
                                                             int yDp,
                                                             boolean adjustResize,
                                                             boolean legacyBookmarkWidth) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        FrameLayout outerFrame = new FrameLayout(this);
        outerFrame.setBackgroundColor(Color.TRANSPARENT);
        outerFrame.setClipChildren(true);
        outerFrame.setClipToPadding(true);

        // Keep the content view's own rounded/background drawable.
        // Do not force it transparent here; the dialog window/background is already transparent
        // so clearing this background makes the popup body invisible.
        if (content instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) content;
            group.setClipChildren(true);
            group.setClipToPadding(true);
        }
        ScrollView adaptiveScroll = wrapAdaptiveDialogContent(content, outerFrame);
        dialog.setContentView(outerFrame);

        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            android.view.WindowManager.LayoutParams lp = new android.view.WindowManager.LayoutParams();
            lp.copyFrom(window.getAttributes());
            lp.width = legacyBookmarkWidth ? legacyBookmarkDialogWidthPx() : txtReaderDialogWidthPx();
            lp.height = android.view.WindowManager.LayoutParams.WRAP_CONTENT;
            lp.y = dpToPx(yDp);
            lp.dimAmount = 0.16f;
            window.setAttributes(lp);
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            if (adjustResize) {
                window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
        }
        applyAdaptiveDialogMaxHeight(dialog, adaptiveScroll, legacyBookmarkWidth ? legacyBookmarkDialogWidthPx() : txtReaderDialogWidthPx());
        return dialog;
    }

    private ScrollView wrapAdaptiveDialogContent(@NonNull View content, @NonNull ViewGroup outerFrame) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setClipChildren(true);
        scroll.setClipToPadding(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        scroll.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        outerFrame.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private void applyAdaptiveDialogMaxHeight(@NonNull android.app.Dialog dialog, @NonNull View adaptiveView, int widthPx) {
        // Apply the constrained-window cap before dialog.show().  Posting this work
        // after attach made bottom-positioned bookmark dialogs visibly drop/land in
        // split-screen and pop-up modes because the window height changed after it
        // was already on screen.  Normal full-screen mode still returns early below.
        int availableHeight = currentVisibleWindowHeightPx();
        if (availableHeight <= 0) return;
        if (!shouldApplyAdaptiveDialogMaxHeight(availableHeight)) return;

        int maxHeight = Math.max(dpToPx(220), Math.round(availableHeight * 0.88f) - dpToPx(24));
        adaptiveView.measure(
                View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
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
        Rect rect = new Rect();
        View decor = getWindow() != null ? getWindow().getDecorView() : null;
        if (decor != null) {
            decor.getWindowVisibleDisplayFrame(rect);
            if (rect.height() > dpToPx(240)) return rect.height();
            if (decor.getHeight() > dpToPx(240)) return decor.getHeight();
        }
        return getResources().getDisplayMetrics().heightPixels;
    }

    private void positionMoreDialogForThumbReach(@NonNull androidx.appcompat.app.AlertDialog dialog) {
        if (dialog.getWindow() == null) return;
        android.view.Window window = dialog.getWindow();
        window.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
        android.view.WindowManager.LayoutParams lp = new android.view.WindowManager.LayoutParams();
        lp.copyFrom(window.getAttributes());
        lp.width = txtReaderDialogWidthPx();
        lp.height = android.view.WindowManager.LayoutParams.WRAP_CONTENT;
        lp.y = dpToPx(34);
        window.setAttributes(lp);
    }

    private void addDialogBottomActions(LinearLayout box, String primaryText, Runnable primaryAction) {
        addDialogBottomActions(box, null, null, primaryText, primaryAction);
    }

    private void addDialogBottomActions(LinearLayout box,
                                        String secondaryText,
                                        Runnable secondaryAction,
                                        String primaryText,
                                        Runnable primaryAction) {
        if (box.findViewWithTag("dialog_actions") != null) return;
        LinearLayout actions = new LinearLayout(this);
        actions.setTag("dialog_actions");
        actions.setGravity(android.view.Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dpToPx(8), 0, 0);

        if (secondaryText != null && secondaryAction != null) {
            TextView secondary = new TextView(this);
            secondary.setText(secondaryText);
            secondary.setTextColor(dialogFg());
            secondary.setTextSize(16f);
            secondary.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START);
            secondary.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            secondary.setPadding(dpToPx(18), 0, dpToPx(18), 0);
            actions.addView(secondary, new LinearLayout.LayoutParams(0, dpToPx(46), 1f));
            secondary.setOnClickListener(v -> secondaryAction.run());
        } else {
            Space spacer = new Space(this);
            actions.addView(spacer, new LinearLayout.LayoutParams(0, dpToPx(46), 1f));
        }

        TextView primary = new TextView(this);
        primary.setText(primaryText);
        primary.setTextColor(dialogFg());
        primary.setTextSize(16f);
        primary.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.END);
        primary.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        primary.setPadding(dpToPx(18), 0, dpToPx(18), 0);
        actions.addView(primary, new LinearLayout.LayoutParams(0, dpToPx(46), 1f));
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
        updateLoadingIndicatorTheme();
        progressBar.setVisibility(View.VISIBLE);
        webView.setVisibility(View.INVISIBLE);
        closeResourceZip();
        pages.clear();
        wordRelationships.clear();
        epubHasDocumentFont = false;
        wordHasDocumentFont = false;
        wordDefaultFontFamily = null;
        documentFontOverride = null;
        clearDocumentSearchState(false);

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
        Toast.makeText(this, getString(R.string.error_prefix) + e.getMessage(), Toast.LENGTH_SHORT).show();
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
                buildDocumentFontCss() +
                "</style>";
        if (html == null) return css;
        int head = html.toLowerCase(Locale.US).indexOf("</head>");
        if (head >= 0) return html.substring(0, head) + css + html.substring(head);
        return css + html;
    }


    private String documentSideSpacingCss() {
        // EPUB boundaries are applied as WebView margins, not HTML padding.
        // Keeping this no-op method avoids reintroducing Word/DOCX page padding.
        return "";
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
        int visualSlideDirection = visualSlideDirectionForPageDelta(direction);
        pendingSlideDirection = visualSlideDirection;
        currentPage = page;
        Page p = pages.get(page);
        String baseUrl = "https://" + LOCAL_HOST + "/";
        if ("EPUB".equals(docType) && p.sourcePath != null) {
            String parent = parentPath(p.sourcePath);
            baseUrl = "https://" + LOCAL_HOST + EPUB_PREFIX + parent;
            if (!baseUrl.endsWith("/")) baseUrl += "/";
        }
        prepareDocumentSlide(visualSlideDirection);
        wordSelectionActive = false;
        webView.removeCallbacks(checkWordSelectionAfterScrollRunnable);
        webView.getSettings().setJavaScriptEnabled("Word".equals(docType));
        applyDocumentTextZoom();
        applyEpubBoundaryMarginsIfNeeded();
        lastAppliedDocumentThemeSignature = documentThemeSignature();
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
                b.setPageNumber(currentPage + 1);
                b.setTotalPages(pages.size());
                b.setExcerpt(excerpt);
                b.setEndPosition(currentPage);
                bookmarkManager.updateBookmark(b);
                Toast.makeText(this, getString(R.string.bookmark_updated), Toast.LENGTH_SHORT).show();
                return;
            }
        }

        Bookmark bookmark = new Bookmark(filePath, fileName, currentPage, currentPage + 1, excerpt);
        bookmark.setPageNumber(currentPage + 1);
        bookmark.setTotalPages(pages.size());
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
        title.setGravity(android.view.Gravity.CENTER);
        title.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        title.setPadding(0, 0, 0, dpToPx(4));
        box.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView currentInfo = new TextView(this);
        currentInfo.setTextColor(blendColors(bg, fg, 0.76f));
        currentInfo.setTextSize(12f);
        currentInfo.setGravity(android.view.Gravity.CENTER);
        currentInfo.setSingleLine(false);
        currentInfo.setLineSpacing(0f, 1.08f);
        currentInfo.setPadding(0, 0, 0, dpToPx(10));
        box.addView(currentInfo, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView hintButton = new TextView(this);
        hintButton.setText(getString(R.string.bookmark_hints_show));
        hintButton.setContentDescription(getString(R.string.bookmark_hints_show));
        hintButton.setTextColor(blendColors(bg, fg, 0.76f));
        hintButton.setTextSize(12f);
        hintButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        hintButton.setGravity(android.view.Gravity.CENTER);
        hintButton.setPadding(0, dpToPx(6), 0, dpToPx(4));
        hintButton.setOnClickListener(v -> showBookmarkHintsPopup());
        box.addView(hintButton, new LinearLayout.LayoutParams(
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
        saveLp.setMargins(0, dpToPx(8), 0, 0);

        RecyclerView rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        rv.setItemAnimator(null);
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
        LinearLayout.LayoutParams bookmarkListLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(430));
        box.addView(rv, bookmarkListLp);
        box.addView(saveButton, saveLp);

        TextView closeButton = new TextView(this);
        closeButton.setText(getString(R.string.close));
        closeButton.setGravity(android.view.Gravity.CENTER);
        closeButton.setTextColor(fg);
        closeButton.setTextSize(16f);
        closeButton.setPadding(0, dpToPx(14), 0, dpToPx(10));
        box.addView(closeButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        android.app.Dialog dialog = createStablePositionedDialog(box, 34, false, true);

        final Runnable[] refreshRef = new Runnable[1];
        refreshRef[0] = () -> {
            List<Bookmark> all = bookmarkManager.getAllBookmarks();
            adapter.setBookmarks(all, expandedFolders, filePath);
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

            @Override public void onFolderDelete(String folderFilePath, String expansionKey, String folderName, int bookmarkCount) {
                showBookmarkFolderDeleteConfirm(folderFilePath, folderName, bookmarkCount, () -> {
                    expandedFolders.remove(folderFilePath);
                    expandedFolders.remove(expansionKey);
                    refreshRef[0].run();
                });
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

        refreshRef[0].run();
        dialog.show();
    }

    private void showBookmarkHintsPopup() {
        LinearLayout box = makeDialogBox();
        box.addView(makeDialogTitle(getString(R.string.bookmark_hints_show)));

        TextView message = new TextView(this);
        message.setText(getString(R.string.bookmark_folder_hint));
        message.setTextColor(dialogSub());
        message.setTextSize(13f);
        message.setLineSpacing(0f, 1.12f);
        message.setPadding(0, 0, 0, dpToPx(12));
        box.addView(message, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        final android.app.Dialog[] dialogRef = new android.app.Dialog[1];
        addDialogBottomActions(box, getString(R.string.ok), () -> {
            if (dialogRef[0] != null) dialogRef[0].dismiss();
        });
        dialogRef[0] = createSmallBookmarkHintDialog(box);
        dialogRef[0].show();
    }

    private android.app.Dialog createSmallBookmarkHintDialog(@NonNull View content) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        FrameLayout outerFrame = new FrameLayout(this);
        outerFrame.setBackgroundColor(Color.TRANSPARENT);
        outerFrame.setClipChildren(true);
        outerFrame.setClipToPadding(true);
        if (content instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) content;
            group.setClipChildren(true);
            group.setClipToPadding(true);
        }
        ScrollView adaptiveScroll = wrapAdaptiveDialogContent(content, outerFrame);
        dialog.setContentView(outerFrame);

        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
            android.view.WindowManager.LayoutParams lp = new android.view.WindowManager.LayoutParams();
            lp.copyFrom(window.getAttributes());
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            lp.width = Math.max(dpToPx(240), Math.min(Math.round(screenWidth * 0.74f), dpToPx(360)));
            lp.height = android.view.WindowManager.LayoutParams.WRAP_CONTENT;
            lp.y = dpToPx(112);
            lp.dimAmount = 0.16f;
            window.setAttributes(lp);
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        applyAdaptiveDialogMaxHeight(dialog, adaptiveScroll, Math.max(dpToPx(240), Math.min(Math.round(getResources().getDisplayMetrics().widthPixels * 0.74f), dpToPx(360))));
        return dialog;
    }

    private void navigateToBookmark(@NonNull Bookmark b) {
        String path = b.getFilePath();
        if (path == null || path.trim().isEmpty()) {
            Toast.makeText(this, getString(R.string.file_not_found_prefix) + "(missing path)", Toast.LENGTH_SHORT).show();
            return;
        }

        File target = new File(path.trim());
        if (!target.exists()) {
            Toast.makeText(this, getString(R.string.file_not_found_prefix) + path, Toast.LENGTH_SHORT).show();
            return;
        }
        if (path.equals(filePath) || target.getAbsolutePath().equals(filePath)) {
            showPage(b.getCharPosition(), Integer.compare(b.getCharPosition(), currentPage));
            return;
        }
        Intent intent;
        String targetPath = target.getAbsolutePath();
        if (FileUtils.isPdfFile(target.getName())) {
            intent = new Intent(this, PdfReaderActivity.class);
            intent.putExtra(PdfReaderActivity.EXTRA_FILE_PATH, targetPath);
            intent.putExtra(PdfReaderActivity.EXTRA_JUMP_TO_PAGE, b.getCharPosition());
        } else if (FileUtils.isEpubFile(target.getName()) || FileUtils.isWordFile(target.getName())) {
            intent = new Intent(this, DocumentPageActivity.class);
            intent.putExtra(DocumentPageActivity.EXTRA_FILE_PATH, targetPath);
            intent.putExtra(DocumentPageActivity.EXTRA_JUMP_TO_PAGE, b.getCharPosition());
        } else {
            intent = new Intent(this, ReaderActivity.class);
            intent.putExtra(ReaderActivity.EXTRA_FILE_PATH, targetPath);
            intent.putExtra(ReaderActivity.EXTRA_JUMP_TO_POSITION, b.getCharPosition());
            intent.putExtra(ReaderActivity.EXTRA_JUMP_DISPLAY_PAGE, b.getPageNumber());
            intent.putExtra(ReaderActivity.EXTRA_JUMP_TOTAL_PAGES, b.getTotalPages());
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    private void showBookmarkDeleteConfirm(@NonNull Bookmark bookmark, @NonNull Runnable afterDelete) {
        LinearLayout box = makeDialogBox();
        box.addView(makeDialogTitle(getString(R.string.delete_bookmark)));

        TextView message = new TextView(this);
        message.setText(bookmark.getFileName() + "\n\n" + bookmark.getDisplayText());
        message.setTextColor(dialogSub());
        message.setTextSize(14f);
        message.setLineSpacing(0f, 1.15f);
        message.setPadding(0, 0, 0, dpToPx(12));
        box.addView(message, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        final android.app.Dialog[] dialogRef = new android.app.Dialog[1];
        addDialogBottomActions(box, getString(R.string.delete), () -> {
            bookmarkManager.deleteBookmark(bookmark.getId());
            afterDelete.run();
            if (dialogRef[0] != null) dialogRef[0].dismiss();
        });
        dialogRef[0] = createStablePositionedDialog(box, DOCUMENT_TOOLBAR_POPUP_Y_DP, false, false);
        dialogRef[0].show();
    }

    private void showBookmarkFolderDeleteConfirm(String folderFilePath, String folderName, int bookmarkCount, @NonNull Runnable afterDelete) {
        LinearLayout box = makeDialogBox();
        box.addView(makeDialogTitle(getString(R.string.delete_bookmark_folder)));

        TextView message = new TextView(this);
        String displayName = folderName != null && !folderName.trim().isEmpty() ? folderName.trim() : getString(R.string.bookmark);
        message.setText(displayName + "\n\n"
                + getString(R.string.delete_bookmark_folder_message, bookmarkCount)
                + "\n" + getString(R.string.delete_bookmark_folder_note));
        message.setTextColor(dialogSub());
        message.setTextSize(14f);
        message.setLineSpacing(0f, 1.15f);
        message.setPadding(0, 0, 0, dpToPx(12));
        box.addView(message, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        final android.app.Dialog[] dialogRef = new android.app.Dialog[1];
        addDialogBottomActions(box, getString(R.string.delete), () -> {
            bookmarkManager.deleteBookmarksForFile(folderFilePath);
            afterDelete.run();
            if (dialogRef[0] != null) dialogRef[0].dismiss();
        });
        dialogRef[0] = createStablePositionedDialog(box, DOCUMENT_TOOLBAR_POPUP_Y_DP, false, false);
        dialogRef[0].show();
    }

    private void showBookmarkMemoEditDialog(@NonNull Bookmark bookmark, @NonNull Runnable afterSave) {
        LinearLayout box = makeDialogBox();
        box.addView(makeDialogTitle(getString(R.string.edit_bookmark_memo)));

        EditText input = makeDialogInput(getString(R.string.optional_memo));
        input.setText(bookmark.getLabel());
        input.setSelectAllOnFocus(true);
        box.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(52)));

        final android.app.Dialog[] dialogRef = new android.app.Dialog[1];

        LinearLayout actions = new LinearLayout(this);
        actions.setTag("dialog_actions");
        actions.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.END);
        actions.setPadding(0, dpToPx(8), 0, 0);

        TextView cancel = new TextView(this);
        cancel.setText(getString(R.string.cancel));
        cancel.setTextColor(dialogSub());
        cancel.setTextSize(16f);
        cancel.setGravity(android.view.Gravity.CENTER);
        cancel.setPadding(dpToPx(14), 0, dpToPx(14), 0);

        TextView clear = new TextView(this);
        clear.setText(getString(R.string.clear_memo));
        clear.setTextColor(dialogSub());
        clear.setTextSize(16f);
        clear.setGravity(android.view.Gravity.CENTER);
        clear.setPadding(dpToPx(14), 0, dpToPx(14), 0);

        TextView save = new TextView(this);
        save.setText(getString(R.string.save));
        save.setTextColor(dialogFg());
        save.setTextSize(16f);
        save.setGravity(android.view.Gravity.CENTER);
        save.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        save.setPadding(dpToPx(18), 0, dpToPx(18), 0);

        actions.addView(cancel, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(46)));
        actions.addView(clear, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(46)));
        actions.addView(save, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(46)));
        box.addView(actions, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        cancel.setOnClickListener(v -> {
            if (dialogRef[0] != null) dialogRef[0].dismiss();
        });
        clear.setOnClickListener(v -> {
            bookmark.setLabel("");
            bookmarkManager.updateBookmark(bookmark);
            afterSave.run();
            if (dialogRef[0] != null) dialogRef[0].dismiss();
        });
        save.setOnClickListener(v -> {
            bookmark.setLabel(input.getText().toString().trim());
            bookmarkManager.updateBookmark(bookmark);
            afterSave.run();
            if (dialogRef[0] != null) dialogRef[0].dismiss();
        });

        dialogRef[0] = createStablePositionedDialog(box, DOCUMENT_TOOLBAR_POPUP_Y_DP, true, false);
        dialogRef[0].show();
    }

    private void saveReadingState() {
        if (filePath == null || prefs == null || !prefs.getAutoSavePosition()) return;
        ReaderState state = new ReaderState(filePath);
        state.setCharPosition(currentPage);
        state.setScrollY(0);
        state.setPageNumber(currentPage + 1);
        state.setTotalPages(pages.size());
        state.setFileLength(fileSizeBytes(filePath));
        state.setEncoding(docType + "_PAGE");
        bookmarkManager.saveReadingState(state);
    }

    private long fileSizeBytes(String path) {
        if (path == null || path.trim().isEmpty() || path.startsWith("content://")) return 0L;
        try {
            File file = new File(path);
            return file.exists() && file.isFile() ? file.length() : 0L;
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private void loadEpubPages(File file) throws Exception {
        closeResourceZip();
        resourceZip = new ZipFile(file);
        epubHasDocumentFont = detectEpubDeclaredFont(resourceZip);
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


    private boolean detectEpubDeclaredFont(@NonNull ZipFile zip) {
        try {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            int scanned = 0;
            while (entries.hasMoreElements() && scanned < 160) {
                ZipEntry entry = entries.nextElement();
                if (entry == null || entry.isDirectory()) continue;
                String name = entry.getName();
                if (name == null) continue;
                String lower = name.toLowerCase(Locale.US);
                if (!(lower.endsWith(".css") || lower.endsWith(".html") || lower.endsWith(".xhtml") || lower.endsWith(".htm"))) {
                    continue;
                }
                scanned++;
                String text = readZipEntryString(zip, entry);
                if (text == null) continue;
                String compact = text.toLowerCase(Locale.US);
                if (compact.contains("@font-face") || compact.contains("font-family")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
            // If detection fails, fall back to normal TextView Reader font handling.
        }
        return false;
    }

    private String detectWordDefaultFontFamily(@NonNull ZipFile zip) {
        String fromStyles = detectWordDefaultFontFromStyles(zip);
        if (fromStyles != null && !fromStyles.trim().isEmpty()) return fromStyles.trim();

        String fromDocument = detectWordFirstDeclaredFont(zip);
        if (fromDocument != null && !fromDocument.trim().isEmpty()) return fromDocument.trim();

        String fromFontTable = detectWordFirstFontTableName(zip);
        if (fromFontTable != null && !fromFontTable.trim().isEmpty()) return fromFontTable.trim();
        return null;
    }

    private String detectWordDefaultFontFromStyles(@NonNull ZipFile zip) {
        try {
            ZipEntry styles = zip.getEntry("word/styles.xml");
            if (styles == null) return null;
            Document stylesDoc;
            try (InputStream is = zip.getInputStream(styles)) {
                stylesDoc = secureDocumentBuilder().parse(is);
            }

            Node docDefaults = firstNodeByLocalName(stylesDoc, "docDefaults");
            String font = firstFontFamilyUnderNode(docDefaults);
            if (font != null) return font;

            Node normalStyle = findWordStyleById(stylesDoc, "Normal");
            font = firstFontFamilyUnderNode(normalStyle);
            if (font != null) return font;
        } catch (Throwable ignored) {
            // Fall back to scanning document/fontTable entries.
        }
        return null;
    }

    private Node findWordStyleById(@NonNull Document stylesDoc, @NonNull String styleId) {
        NodeList styles = stylesDoc.getElementsByTagNameNS("*", "style");
        if (styles.getLength() == 0) styles = stylesDoc.getElementsByTagName("w:style");
        if (styles.getLength() == 0) styles = stylesDoc.getElementsByTagName("style");
        for (int i = 0; i < styles.getLength(); i++) {
            Node style = styles.item(i);
            String id = attr(style, "w:styleId", "styleId");
            if (styleId.equalsIgnoreCase(id)) return style;
        }
        return null;
    }

    private String detectWordFirstDeclaredFont(@NonNull ZipFile zip) {
        try {
            ZipEntry documentXml = zip.getEntry("word/document.xml");
            if (documentXml == null) return null;
            Document doc;
            try (InputStream is = zip.getInputStream(documentXml)) {
                doc = secureDocumentBuilder().parse(is);
            }
            return firstFontFamilyUnderNode(doc.getDocumentElement());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String detectWordFirstFontTableName(@NonNull ZipFile zip) {
        try {
            ZipEntry fontTable = zip.getEntry("word/fontTable.xml");
            if (fontTable == null) return null;
            Document fontDoc;
            try (InputStream is = zip.getInputStream(fontTable)) {
                fontDoc = secureDocumentBuilder().parse(is);
            }
            NodeList fonts = fontDoc.getElementsByTagNameNS("*", "font");
            if (fonts.getLength() == 0) fonts = fontDoc.getElementsByTagName("w:font");
            if (fonts.getLength() == 0) fonts = fontDoc.getElementsByTagName("font");
            for (int i = 0; i < fonts.getLength(); i++) {
                String name = sanitizeWordFontName(attr(fonts.item(i), "w:name", "name"));
                if (name != null) return name;
            }
        } catch (Throwable ignored) {
            // Missing fontTable is normal for simple DOCX files.
        }
        return null;
    }

    private String firstFontFamilyUnderNode(Node root) {
        if (root == null) return null;
        if ("rFonts".equals(root.getLocalName()) || "w:rFonts".equals(root.getNodeName())) {
            String font = wordFontFromRFonts(root);
            if (font != null) return font;
        }
        NodeList nodes = root.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            String font = firstFontFamilyUnderNode(nodes.item(i));
            if (font != null) return font;
        }
        return null;
    }

    private String wordFontFromRFonts(Node rFonts) {
        String font = sanitizeWordFontName(attr(rFonts, "w:ascii", "ascii"));
        if (font != null) return font;
        font = sanitizeWordFontName(attr(rFonts, "w:hAnsi", "hAnsi"));
        if (font != null) return font;
        font = sanitizeWordFontName(attr(rFonts, "w:eastAsia", "eastAsia"));
        if (font != null) return font;
        font = sanitizeWordFontName(attr(rFonts, "w:cs", "cs"));
        if (font != null) return font;
        font = sanitizeWordFontName(attr(rFonts, "w:asciiTheme", "asciiTheme"));
        if (font != null) return font;
        font = sanitizeWordFontName(attr(rFonts, "w:hAnsiTheme", "hAnsiTheme"));
        if (font != null) return font;
        font = sanitizeWordFontName(attr(rFonts, "w:eastAsiaTheme", "eastAsiaTheme"));
        if (font != null) return font;
        return sanitizeWordFontName(attr(rFonts, "w:cstheme", "cstheme"));
    }

    private String sanitizeWordFontName(String name) {
        if (name == null) return null;
        String trimmed = name.trim();
        if (trimmed.isEmpty()) return null;
        if (trimmed.startsWith("+") || trimmed.toLowerCase(Locale.US).contains("theme")) return null;
        return trimmed;
    }

    private void loadWordPages(File file) throws Exception {
        closeResourceZip();
        resourceZip = new ZipFile(file);
        wordRelationships.clear();
        loadWordRelationships(resourceZip);
        wordDefaultFontFamily = detectWordDefaultFontFamily(resourceZip);
        wordHasDocumentFont = wordDefaultFontFamily != null && !wordDefaultFontFamily.trim().isEmpty();
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
        if (uri == null) return null;
        if (!LOCAL_HOST.equalsIgnoreCase(uri.getHost())) return null;
        String path = uri.getPath();
        if (path == null) return null;

        if (path.startsWith(FONT_PREFIX)) {
            return interceptSelectedDocumentFont();
        }

        if (resourceZip == null) return null;

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
                "body{line-height:1.55;padding:22px;box-sizing:border-box;}" +
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
