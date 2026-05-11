package com.simpletext.reader;

import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.text.InputType;
import android.util.LruCache;
import android.util.SparseIntArray;
import android.view.MenuItem;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.ViewConfiguration;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.simpletext.reader.adapter.BookmarkFolderAdapter;
import com.simpletext.reader.model.Bookmark;
import com.simpletext.reader.model.ReaderState;
import com.simpletext.reader.model.Theme;
import com.simpletext.reader.util.BookmarkManager;
import com.simpletext.reader.util.FileUtils;
import com.simpletext.reader.util.PrefsManager;
import com.simpletext.reader.util.ThemeManager;

import java.io.File;
import java.text.DateFormat;
import java.util.Date;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Native PDF page viewer.
 *
 * This intentionally renders the original PDF page using Android's PdfRenderer
 * instead of extracting plain text into the text reader. Bookmarks and recent-file
 * state store the current page index in ReaderState.charPosition / Bookmark.charPosition.
 */
public class PdfReaderActivity extends AppCompatActivity {

    public static final String EXTRA_FILE_PATH = ReaderActivity.EXTRA_FILE_PATH;
    public static final String EXTRA_FILE_URI = ReaderActivity.EXTRA_FILE_URI;
    public static final String EXTRA_JUMP_TO_PAGE = ReaderActivity.EXTRA_JUMP_TO_POSITION;

    private View root;
    private View pdfAppBar;
    private View pdfBottomBar;
    private boolean pdfChromeVisible = true;
    private ImageView pageImage;
    private RecyclerView pdfContinuousList;
    private PdfContinuousPageAdapter pdfContinuousAdapter;
    private RecyclerView.OnScrollListener continuousScrollListener;
    private boolean suppressContinuousScrollSync = false;
    private ProgressBar progressBar;
    private TextView pageStatus;
    private TextView prevButton;
    private TextView nextButton;
    private TextView slideModeButton;
    private TextView pageButton;
    private TextView bookmarkButton;
    private TextView zoomMoreButton;
    private View pdfViewport;
    private HorizontalScrollView pdfHScroll;
    private ScrollView pdfVScroll;
    private ScaleGestureDetector scaleGestureDetector;
    private GestureDetector gestureDetector;
    private float touchStartX;
    private float touchStartY;
    private boolean pinchZoomChanged = false;
    private float gestureStartRawX;
    private float gestureStartRawY;
    private boolean gestureStartedInViewport = false;
    private boolean gestureSawMultiTouch = false;
    private boolean viewportPanConsumed = false;
    private float lastPanRawX;
    private float lastPanRawY;
    private int touchSlop;
    private boolean gestureStartedWithHorizontalScrollable = false;
    private boolean gestureStartedWithVerticalScrollable = false;
    private boolean gestureStartedAtLeftEdge = true;
    private boolean gestureStartedAtRightEdge = true;
    private boolean gestureStartedAtTopEdge = true;
    private boolean gestureStartedAtBottomEdge = true;
    private boolean verticalPageSlideMode = false;

    private boolean pendingZoomFocus = false;
    private float pendingZoomFocusXRatio = 0.5f;
    private float pendingZoomFocusYRatio = 0.5f;
    private float pendingZoomViewportX = 0.5f;
    private float pendingZoomViewportY = 0.5f;
    private float activePinchFocusRawX = -1f;
    private float activePinchFocusRawY = -1f;

    private int readerBg = Color.rgb(18, 18, 18);
    private int readerFg = Color.rgb(232, 234, 237);
    private int readerSub = Color.rgb(176, 176, 176);
    private int readerPanel = Color.rgb(32, 33, 36);
    private int readerLine = Color.rgb(84, 86, 90);

    private PrefsManager prefs;
    private BookmarkManager bookmarkManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean activityDestroyed = false;
    private final Object rendererLock = new Object();

    private ParcelFileDescriptor parcelFileDescriptor;
    private PdfRenderer pdfRenderer;
    private Bitmap currentBitmap;
    private File localFile;
    private String filePath;
    private String fileName;
    private int pageCount = 0;
    private int currentPage = 0;
    private float zoom = 1.0f;
    private float renderedZoom = 1.0f;
    private int pendingPageSlideDirection = 0;
    private int renderGeneration = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = PrefsManager.getInstance(this);
        prefs.applyLanguage(prefs.getLanguageMode());
        super.onCreate(savedInstanceState);
        ViewerRegistry.activate(this);

        resolveReaderThemeColors();
        touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();

        setContentView(R.layout.activity_pdf_reader);
        applyDocumentSystemBarColors();

        // targetSdk 35 forces edge-to-edge regardless of setDecorFitsSystemWindows,
        // so the toolbar would otherwise be hidden under the status bar and the
        // page-control buttons under the navigation bar. The shared helper
        // installs an OnApplyWindowInsetsListener that pads the AppBarLayout and
        // bottom bar by the matching system inset, which is exactly what is
        // needed here.
        com.simpletext.reader.util.EdgeToEdgeUtil.applyFoldableChromeInsets(this,
                findViewById(R.id.pdf_root),
                findViewById(R.id.pdf_appbar),
                findViewById(R.id.pdf_bottom_bar),
                findViewById(R.id.pdf_viewport),
                () -> pdfChromeVisible);
        applyDocumentSystemBarColors();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setTitleTextColor(Color.WHITE);
        toolbar.setNavigationIcon(tintedBackIcon());

        root = findViewById(R.id.pdf_root);
        pdfAppBar = findViewById(R.id.pdf_appbar);
        pdfBottomBar = findViewById(R.id.pdf_bottom_bar);
        pageImage = findViewById(R.id.pdf_page_image);
        pdfContinuousList = findViewById(R.id.pdf_continuous_list);
        progressBar = findViewById(R.id.pdf_progress);
        pageStatus = findViewById(R.id.pdf_page_status);
        prevButton = findViewById(R.id.pdf_prev);
        nextButton = findViewById(R.id.pdf_next);
        slideModeButton = findViewById(R.id.pdf_slide_toggle);
        pageButton = findViewById(R.id.pdf_page);
        bookmarkButton = findViewById(R.id.pdf_bookmark);
        zoomMoreButton = findViewById(R.id.pdf_zoom_more);
        pdfViewport = findViewById(R.id.pdf_viewport);
        pdfHScroll = findViewById(R.id.pdf_h_scroll);
        pdfVScroll = findViewById(R.id.pdf_v_scroll);
        setupContinuousPdfList();

        bookmarkManager = BookmarkManager.getInstance(this);
        verticalPageSlideMode = getSharedPreferences("pdf_reader", MODE_PRIVATE)
                .getBoolean("vertical_page_slide_mode", false);
        styleControls();
        setupControls();
        installPdfGestures();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { finish(); }
        });

        if (prefs.getBrightnessOverride()) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.screenBrightness = prefs.getBrightnessValue();
            getWindow().setAttributes(lp);
        }

        loadPdfFromIntent();
    }


    private void setupContinuousPdfList() {
        if (pdfContinuousList == null) return;

        pdfContinuousAdapter = new PdfContinuousPageAdapter();
        LinearLayoutManager lm = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        pdfContinuousList.setLayoutManager(lm);
        pdfContinuousList.setAdapter(pdfContinuousAdapter);
        pdfContinuousList.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        pdfContinuousList.setBackgroundColor(readerPanel);
        continuousScrollListener = new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                syncCurrentPageFromContinuousList(false);
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    syncCurrentPageFromContinuousList(true);
                }
            }
        };
        pdfContinuousList.addOnScrollListener(continuousScrollListener);
    }

    private void syncCurrentPageFromContinuousList(boolean force) {
        if (suppressContinuousScrollSync || !verticalPageSlideMode || pageCount <= 0 || pdfContinuousList == null) {
            return;
        }

        RecyclerView.LayoutManager manager = pdfContinuousList.getLayoutManager();
        if (!(manager instanceof LinearLayoutManager)) return;

        LinearLayoutManager lm = (LinearLayoutManager) manager;
        int first = lm.findFirstVisibleItemPosition();
        int last = lm.findLastVisibleItemPosition();
        if (first == RecyclerView.NO_POSITION || last == RecyclerView.NO_POSITION) return;

        int viewportCenter = pdfContinuousList.getHeight() / 2;
        int bestPage = first;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = first; i <= last; i++) {
            View child = lm.findViewByPosition(i);
            if (child == null) continue;
            int childCenter = (child.getTop() + child.getBottom()) / 2;
            int distance = Math.abs(childCenter - viewportCenter);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestPage = i;
            }
        }

        int nextPage = clampPage(bestPage);
        if (force || nextPage != currentPage) {
            currentPage = nextPage;
            saveReadingState();
            updatePageStatus();
        }
    }

    private void applyPdfDisplayMode() {
        boolean continuous = verticalPageSlideMode;
        pendingPageSlideDirection = 0;
        resetViewportGesture();

        if (pdfContinuousList != null) {
            pdfContinuousList.stopScroll();
            pdfContinuousList.setVisibility(continuous ? View.VISIBLE : View.GONE);
        }
        if (pdfHScroll != null) {
            pdfHScroll.setVisibility(continuous ? View.GONE : View.VISIBLE);
        }

        if (continuous) {
            releaseSinglePageBitmap();
            renderContinuousPages();
        } else {
            resetContinuousPageViews(true);
            renderCurrentPage();
        }
    }

    private void resetContinuousPageViews(boolean keepAdapterAttached) {
        if (pdfContinuousList != null) {
            pdfContinuousList.stopScroll();
            pdfContinuousList.setAdapter(null);
        }
        if (pdfContinuousAdapter != null) {
            pdfContinuousAdapter.clearBitmaps();
        }
        if (keepAdapterAttached && !activityDestroyed && pdfContinuousList != null && pdfContinuousAdapter != null) {
            pdfContinuousList.setAdapter(pdfContinuousAdapter);
        }
    }

    private void renderContinuousPages() {
        if (!ensureContinuousPagesConfigured()) return;
        progressBar.setVisibility(View.GONE);
        updatePageStatus();
        scrollContinuousListToCurrentPage(false);
        prefetchContinuousPagesAround(currentPage);
    }

    private boolean ensureContinuousPagesConfigured() {
        if (pdfContinuousList == null || pdfContinuousAdapter == null
                || pdfRenderer == null || pageCount <= 0 || pdfViewport == null) {
            return false;
        }

        if (pdfContinuousList.getAdapter() != pdfContinuousAdapter) {
            pdfContinuousList.setAdapter(pdfContinuousAdapter);
        }

        int viewportWidth = pdfViewport.getWidth();
        if (viewportWidth <= 0) {
            pdfViewport.post(this::renderContinuousPages);
            return false;
        }

        pdfContinuousAdapter.configure(pageCount, viewportWidth, zoom);
        return true;
    }

    private void prefetchContinuousPagesAround(int pageIndex) {
        if (pdfContinuousAdapter == null || !verticalPageSlideMode || pageCount <= 0) return;
        int center = clampPage(pageIndex);
        for (int page = Math.max(0, center - 1); page <= Math.min(pageCount - 1, center + 1); page++) {
            pdfContinuousAdapter.prefetchPage(page);
        }
    }

    private void scrollContinuousListToCurrentPage(boolean smooth) {
        if (pdfContinuousList == null || pageCount <= 0) return;
        final int target = clampPage(currentPage);
        suppressContinuousScrollSync = true;
        pdfContinuousList.stopScroll();

        pdfContinuousList.post(() -> {
            if (activityDestroyed || pdfContinuousList == null) return;

            pdfContinuousList.stopScroll();
            RecyclerView.LayoutManager manager = pdfContinuousList.getLayoutManager();
            if (manager instanceof LinearLayoutManager) {
                if (smooth) {
                    pdfContinuousList.smoothScrollToPosition(target);
                } else {
                    ((LinearLayoutManager) manager).scrollToPositionWithOffset(target, 0);
                }
            } else {
                pdfContinuousList.scrollToPosition(target);
            }

            pdfContinuousList.postDelayed(() -> {
                suppressContinuousScrollSync = false;
                updatePageStatus();
            }, smooth ? 360L : 220L);
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (handlePdfViewportGesture(event)) return true;
        return super.dispatchTouchEvent(event);
    }

    private boolean handlePdfViewportGesture(@NonNull MotionEvent event) {
        if (pdfViewport == null || pageCount <= 0) return false;

        boolean insideViewport = isEventInsideView(pdfViewport, event);
        if (verticalPageSlideMode) {
            // Continuous vertical PDF mode is still vertically scrolled by the
            // RecyclerView. When pages are zoomed wider than the viewport, horizontal
            // drags are intercepted here so the user can pan across the enlarged
            // page instead of being stuck at the centered crop.
            if (insideViewport && scaleGestureDetector != null) {
                scaleGestureDetector.onTouchEvent(event);
                if (event.getPointerCount() > 1 || scaleGestureDetector.isInProgress()) {
                    return true;
                }
            }
            if (handlePdfTapGesture(event, insideViewport)) {
                resetViewportGesture();
                return true;
            }

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    gestureStartRawX = event.getRawX();
                    gestureStartRawY = event.getRawY();
                    lastPanRawX = gestureStartRawX;
                    lastPanRawY = gestureStartRawY;
                    viewportPanConsumed = false;
                    gestureStartedInViewport = insideViewport;
                    gestureSawMultiTouch = event.getPointerCount() > 1;
                    return false;

                case MotionEvent.ACTION_POINTER_DOWN:
                    if (gestureStartedInViewport) gestureSawMultiTouch = true;
                    return false;

                case MotionEvent.ACTION_MOVE:
                    if (!gestureStartedInViewport || gestureSawMultiTouch
                            || (scaleGestureDetector != null && scaleGestureDetector.isInProgress())) {
                        return false;
                    }
                    if (pdfContinuousAdapter != null && pdfContinuousAdapter.canPanVisiblePageHorizontally()) {
                        float rawX = event.getRawX();
                        float rawY = event.getRawY();
                        float stepDx = rawX - lastPanRawX;
                        float totalDx = rawX - gestureStartRawX;
                        float totalDy = rawY - gestureStartRawY;
                        boolean horizontalPanGesture = viewportPanConsumed
                                || (Math.abs(totalDx) > touchSlop && Math.abs(totalDx) > Math.abs(totalDy) * 1.12f);
                        if (horizontalPanGesture) {
                            boolean moved = pdfContinuousAdapter.panVisiblePageHorizontally(-stepDx * 1.65f);
                            viewportPanConsumed = true;
                            lastPanRawX = rawX;
                            lastPanRawY = rawY;
                            return moved || Math.abs(stepDx) > 0.5f;
                        }
                    }
                    return false;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    boolean consumed = viewportPanConsumed;
                    resetViewportGesture();
                    return consumed;

                default:
                    return false;
            }
        }
        if (handlePdfTapGesture(event, insideViewport)) {
            resetViewportGesture();
            return true;
        }
        if (insideViewport && scaleGestureDetector != null) {
            scaleGestureDetector.onTouchEvent(event);
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                gestureStartRawX = event.getRawX();
                gestureStartRawY = event.getRawY();
                lastPanRawX = gestureStartRawX;
                lastPanRawY = gestureStartRawY;
                viewportPanConsumed = false;
                gestureStartedInViewport = insideViewport;
                gestureSawMultiTouch = event.getPointerCount() > 1;
                gestureStartedWithHorizontalScrollable = insideViewport && isPdfHorizontallyScrollable();
                gestureStartedWithVerticalScrollable = insideViewport && isPdfVerticallyScrollable();
                gestureStartedAtLeftEdge = !gestureStartedWithHorizontalScrollable || isPdfAtLeftEdge(dpToPx(3));
                gestureStartedAtRightEdge = !gestureStartedWithHorizontalScrollable || isPdfAtRightEdge(dpToPx(3));
                gestureStartedAtTopEdge = !gestureStartedWithVerticalScrollable || isPdfAtTopEdge(dpToPx(3));
                gestureStartedAtBottomEdge = !gestureStartedWithVerticalScrollable || isPdfAtBottomEdge(dpToPx(3));
                return false;

            case MotionEvent.ACTION_POINTER_DOWN:
                if (gestureStartedInViewport) gestureSawMultiTouch = true;
                return false;

            case MotionEvent.ACTION_MOVE:
                if (!gestureStartedInViewport || gestureSawMultiTouch
                        || (scaleGestureDetector != null && scaleGestureDetector.isInProgress())) {
                    return false;
                }
                if (isPdfContentScrollable()) {
                    float rawX = event.getRawX();
                    float rawY = event.getRawY();
                    float stepDx = rawX - lastPanRawX;
                    float stepDy = rawY - lastPanRawY;
                    float totalDx = rawX - gestureStartRawX;
                    float totalDy = rawY - gestureStartRawY;
                    if (viewportPanConsumed || Math.hypot(totalDx, totalDy) > touchSlop) {
                        panPdfContent(-stepDx, -stepDy);
                        viewportPanConsumed = true;
                        lastPanRawX = rawX;
                        lastPanRawY = rawY;
                        return true;
                    }
                }
                return false;

            case MotionEvent.ACTION_UP:
                if (!gestureStartedInViewport || gestureSawMultiTouch
                        || (scaleGestureDetector != null && scaleGestureDetector.isInProgress())) {
                    resetViewportGesture();
                    return false;
                }
                float dx = event.getRawX() - gestureStartRawX;
                float dy = event.getRawY() - gestureStartRawY;
                if (verticalPageSlideMode) {
                    boolean strongVerticalPageSwipe = Math.abs(dy) >= dpToPx(82)
                            && Math.abs(dy) > Math.abs(dx) * 1.25f;
                    if (strongVerticalPageSwipe && canTurnPdfPageFromVerticalSwipe(dy)) {
                        if (dy < 0) goToPage(currentPage + 1, 1);
                        else goToPage(currentPage - 1, -1);
                        resetViewportGesture();
                        return true;
                    }
                } else {
                    boolean strongPageSwipe = Math.abs(dx) >= dpToPx(82)
                            && Math.abs(dx) > Math.abs(dy) * 1.35f;
                    if (strongPageSwipe && canTurnPdfPageFromSwipe(dx)) {
                        if (dx < 0) goToPage(currentPage + 1, 1);
                        else goToPage(currentPage - 1, -1);
                        resetViewportGesture();
                        return true;
                    }
                }
                boolean consumed = viewportPanConsumed;
                resetViewportGesture();
                return consumed;

            case MotionEvent.ACTION_CANCEL:
                resetViewportGesture();
                return false;

            default:
                return false;
        }
    }

    private void resetViewportGesture() {
        gestureStartedInViewport = false;
        gestureSawMultiTouch = false;
        viewportPanConsumed = false;
        gestureStartedWithHorizontalScrollable = false;
        gestureStartedWithVerticalScrollable = false;
        gestureStartedAtLeftEdge = true;
        gestureStartedAtRightEdge = true;
        gestureStartedAtTopEdge = true;
        gestureStartedAtBottomEdge = true;
    }

    private boolean handlePdfTapGesture(@NonNull MotionEvent event, boolean insideViewport) {
        if (!insideViewport || gestureDetector == null) return false;
        boolean handled = gestureDetector.onTouchEvent(event);
        // Keep ACTION_DOWN available for the pan/swipe tracker. GestureDetector may
        // return true on down once onDown() is enabled for reliable single-tap
        // confirmation, but consuming down here breaks page swipes and zoom panning.
        return handled && event.getActionMasked() != MotionEvent.ACTION_DOWN;
    }

    private void togglePdfChrome() {
        setPdfChromeVisible(!pdfChromeVisible);
    }

    private void setPdfChromeVisible(boolean visible) {
        pdfChromeVisible = visible;
        int visibility = visible ? View.VISIBLE : View.GONE;
        if (pdfAppBar != null && pdfAppBar.getVisibility() != visibility) {
            pdfAppBar.setVisibility(visibility);
        }
        if (pdfBottomBar != null && pdfBottomBar.getVisibility() != visibility) {
            pdfBottomBar.setVisibility(visibility);
        }
        androidx.core.view.ViewCompat.requestApplyInsets(root);
        if (pdfViewport != null) pdfViewport.requestLayout();
    }

    private boolean isPdfContentScrollable() {
        return isPdfHorizontallyScrollable() || isPdfVerticallyScrollable();
    }

    private boolean isPdfVerticallyScrollable() {
        if (pdfVScroll == null || pdfVScroll.getChildCount() == 0) return false;
        int childHeight = pdfVScroll.getChildAt(0).getHeight();
        int viewportHeight = pdfVScroll.getHeight();
        return childHeight > viewportHeight + dpToPx(4);
    }

    private void panPdfContent(float deltaX, float deltaY) {
        if (pdfHScroll != null && isPdfHorizontallyScrollable()) {
            int maxX = Math.max(0, pdfHScroll.getChildAt(0).getWidth() - pdfHScroll.getWidth());
            int nextX = Math.max(0, Math.min(maxX, pdfHScroll.getScrollX() + Math.round(deltaX)));
            pdfHScroll.scrollTo(nextX, pdfHScroll.getScrollY());
        }
        if (pdfVScroll != null && isPdfVerticallyScrollable()) {
            int maxY = Math.max(0, pdfVScroll.getChildAt(0).getHeight() - pdfVScroll.getHeight());
            int nextY = Math.max(0, Math.min(maxY, pdfVScroll.getScrollY() + Math.round(deltaY)));
            pdfVScroll.scrollTo(pdfVScroll.getScrollX(), nextY);
        }
    }

    /**
     * When the PDF is zoomed wider than the viewport, horizontal swipes should pan
     * around the enlarged page first. Page turn is allowed only after the scroll
     * position reaches the matching horizontal edge. When the page fits the screen,
     * swipes behave as normal page turns.
     */
    private boolean canTurnPdfPageFromSwipe(float dx) {
        if (pageCount <= 1 || pdfHScroll == null) return false;
        if (!gestureStartedWithHorizontalScrollable) return true;

        // If this gesture merely panned the zoomed page into the edge, stop there.
        // Turn the page only when the gesture started while already resting on that edge.
        if (dx < 0) {
            return gestureStartedAtRightEdge && currentPage < pageCount - 1;
        } else {
            return gestureStartedAtLeftEdge && currentPage > 0;
        }
    }

    private boolean isPdfHorizontallyScrollable() {
        if (pdfHScroll == null || pdfHScroll.getChildCount() == 0) return false;
        int childWidth = pdfHScroll.getChildAt(0).getWidth();
        int viewportWidth = pdfHScroll.getWidth();
        return childWidth > viewportWidth + dpToPx(4);
    }

    private boolean isPdfAtLeftEdge(int tolerancePx) {
        return pdfHScroll == null || pdfHScroll.getScrollX() <= tolerancePx;
    }

    private boolean isPdfAtRightEdge(int tolerancePx) {
        if (pdfHScroll == null || pdfHScroll.getChildCount() == 0) return true;
        int maxScroll = Math.max(0, pdfHScroll.getChildAt(0).getWidth() - pdfHScroll.getWidth());
        return pdfHScroll.getScrollX() >= maxScroll - tolerancePx;
    }

    private boolean isPdfAtTopEdge(int tolerancePx) {
        return pdfVScroll == null || pdfVScroll.getScrollY() <= tolerancePx;
    }

    private boolean isPdfAtBottomEdge(int tolerancePx) {
        if (pdfVScroll == null || pdfVScroll.getChildCount() == 0) return true;
        int maxScroll = Math.max(0, pdfVScroll.getChildAt(0).getHeight() - pdfVScroll.getHeight());
        return pdfVScroll.getScrollY() >= maxScroll - tolerancePx;
    }

    private boolean canTurnPdfPageFromVerticalSwipe(float dy) {
        if (pageCount <= 1 || pdfVScroll == null) return false;
        if (!gestureStartedWithVerticalScrollable) return true;

        // Same edge rule as horizontal zoomed-page panning:
        // first drag pans the enlarged page to the edge; a drag that starts at the
        // edge turns to the neighboring page.
        if (dy < 0) {
            return gestureStartedAtBottomEdge && currentPage < pageCount - 1;
        } else {
            return gestureStartedAtTopEdge && currentPage > 0;
        }
    }

    private boolean isEventInsideView(View view, @NonNull MotionEvent event) {
        if (view == null || view.getVisibility() != View.VISIBLE) return false;
        int[] loc = new int[2];
        view.getLocationOnScreen(loc);
        float x = event.getRawX();
        float y = event.getRawY();
        return x >= loc[0] && x <= loc[0] + view.getWidth()
                && y >= loc[1] && y <= loc[1] + view.getHeight();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ThemeManager.getInstance(this).reloadFromStorage();
        applyDocumentSystemBarColors();
        styleControls();
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) toolbar.setNavigationIcon(tintedBackIcon());
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
        if (controller != null) {
            boolean light = !isDarkColor(bg);
            controller.setAppearanceLightStatusBars(light);
            controller.setAppearanceLightNavigationBars(light);
        }
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

    @Override
    protected void onNewIntent(@NonNull android.content.Intent intent) {
        super.onNewIntent(intent);
        saveReadingState();
        setIntent(intent);
        loadPdfFromIntent();
    }

    private android.graphics.drawable.Drawable tintedBackIcon() {
        android.graphics.drawable.Drawable icon = androidx.core.content.ContextCompat.getDrawable(
                this, androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        if (icon == null) return null;
        android.graphics.drawable.Drawable wrapped = androidx.core.graphics.drawable.DrawableCompat.wrap(icon.mutate());
        androidx.core.graphics.drawable.DrawableCompat.setTint(wrapped, readerFg);
        return wrapped;
    }

    private void styleControls() {
        resolveReaderThemeColors();
        if (root != null) root.setBackgroundColor(readerBg);
        if (pdfAppBar == null) pdfAppBar = findViewById(R.id.pdf_appbar);
        if (pdfAppBar != null) pdfAppBar.setBackgroundColor(readerBg);
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setBackgroundColor(readerBg);
            toolbar.setTitleTextColor(readerFg);
            toolbar.setNavigationIcon(tintedBackIcon());
        }
        if (pdfBottomBar == null) pdfBottomBar = findViewById(R.id.pdf_bottom_bar);
        if (pdfBottomBar != null) pdfBottomBar.setBackgroundColor(readerPanel);
        if (pdfViewport != null) pdfViewport.setBackgroundColor(readerPanel);
        if (pageStatus != null) pageStatus.setTextColor(readerFg);
        updateLoadingIndicatorTheme();

        TextView[] buttons = {prevButton, nextButton, slideModeButton, pageButton, bookmarkButton, zoomMoreButton};
        for (TextView b : buttons) {
            if (b == null) continue;
            b.setTextColor(readerFg);
            b.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(readerFg));
        }
    }


    private void updateLoadingIndicatorTheme() {
        if (progressBar == null) return;
        progressBar.setBackgroundColor(Color.TRANSPARENT);
        progressBar.setIndeterminateTintList(ColorStateList.valueOf(readerFg));
    }

    private void setupControls() {
        prevButton.setOnClickListener(v -> goToPage(currentPage - 1, -1));
        nextButton.setOnClickListener(v -> goToPage(currentPage + 1, 1));
        if (slideModeButton != null) {
            slideModeButton.setOnClickListener(v -> togglePdfSlideMode());
            updatePdfSlideModeButton();
        }
        if (pageButton != null) pageButton.setOnClickListener(v -> showGoToPageDialog());
        bookmarkButton.setOnClickListener(v -> showBookmarksDialog());
        if (zoomMoreButton != null) zoomMoreButton.setOnClickListener(v -> showMoreDialog());
    }


    private void installPdfGestures() {
        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                capturePinchZoomFocus(detector);
                applyPageImagePivotFromRaw(activePinchFocusRawX, activePinchFocusRawY);
                return true;
            }

            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float nextZoom = Math.max(0.55f, Math.min(4.5f, zoom * detector.getScaleFactor()));
                if (Math.abs(nextZoom - zoom) > 0.004f) {
                    zoom = nextZoom;
                    pinchZoomChanged = true;
                    if (!verticalPageSlideMode && pageImage != null) {
                        float displayScale = Math.max(0.55f, Math.min(4.5f, zoom / Math.max(0.1f, renderedZoom)));
                        pageImage.setScaleX(displayScale);
                        pageImage.setScaleY(displayScale);
                    }
                }
                return true;
            }

            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
                if (pinchZoomChanged) {
                    pinchZoomChanged = false;
                    activePinchFocusRawX = -1f;
                    activePinchFocusRawY = -1f;
                    renderCurrentPage();
                }
            }
        });

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                togglePdfChrome();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                toggleDoubleTapZoom(e);
                return true;
            }
        });
    }

    private void setZoomSmooth(float targetZoom) {
        setZoomSmooth(targetZoom, null);
    }

    private void setZoomSmooth(float targetZoom, MotionEvent focusEvent) {
        zoom = Math.max(0.55f, Math.min(4.5f, targetZoom));
        if (verticalPageSlideMode) {
            if (pdfContinuousAdapter != null) pdfContinuousAdapter.clearBitmaps();
            renderContinuousPages();
            return;
        }
        if (focusEvent != null) {
            prepareDoubleTapZoomFocus(focusEvent);
            applyPageImagePivotFromRaw(focusEvent.getRawX(), focusEvent.getRawY());
        } else {
            prepareZoomFocusFromViewportCenter();
            applyPageImagePivotFromPreparedFocus();
        }
        if (pageImage != null) {
            float displayScale = Math.max(0.55f, Math.min(4.5f, zoom / Math.max(0.1f, renderedZoom)));
            pageImage.animate().cancel();
            pageImage.animate()
                    .scaleX(displayScale)
                    .scaleY(displayScale)
                    .setDuration(90)
                    .withEndAction(this::renderCurrentPage)
                    .start();
        } else {
            renderCurrentPage();
        }
    }

    private void prepareDoubleTapZoomFocus(MotionEvent focusEvent) {
        if (focusEvent == null) {
            pendingZoomFocus = false;
            return;
        }
        prepareZoomFocusFromRaw(focusEvent.getRawX(), focusEvent.getRawY());
    }

    private void prepareZoomFocusFromViewportCenter() {
        if (pdfViewport == null) {
            pendingZoomFocus = false;
            return;
        }
        int[] viewportLoc = new int[2];
        pdfViewport.getLocationOnScreen(viewportLoc);
        prepareZoomFocusFromRaw(
                viewportLoc[0] + pdfViewport.getWidth() * 0.5f,
                viewportLoc[1] + pdfViewport.getHeight() * 0.5f);
    }

    private void prepareZoomFocusFromRaw(float rawX, float rawY) {
        if (rawX < 0f || rawY < 0f || pageImage == null || pdfViewport == null) {
            pendingZoomFocus = false;
            return;
        }

        int imageWidth = Math.max(1, pageImage.getWidth());
        int imageHeight = Math.max(1, pageImage.getHeight());

        int[] imageLoc = new int[2];
        int[] viewportLoc = new int[2];
        pageImage.getLocationOnScreen(imageLoc);
        pdfViewport.getLocationOnScreen(viewportLoc);

        float localX = Math.max(0f, Math.min(imageWidth, rawX - imageLoc[0]));
        float localY = Math.max(0f, Math.min(imageHeight, rawY - imageLoc[1]));

        pendingZoomFocusXRatio = localX / imageWidth;
        pendingZoomFocusYRatio = localY / imageHeight;
        pendingZoomViewportX = Math.max(0f, Math.min(pdfViewport.getWidth(), rawX - viewportLoc[0]));
        pendingZoomViewportY = Math.max(0f, Math.min(pdfViewport.getHeight(), rawY - viewportLoc[1]));
        pendingZoomFocus = true;
    }

    private void capturePinchZoomFocus(ScaleGestureDetector detector) {
        if (detector == null || root == null) {
            activePinchFocusRawX = -1f;
            activePinchFocusRawY = -1f;
            pendingZoomFocus = false;
            return;
        }
        int[] rootLoc = new int[2];
        root.getLocationOnScreen(rootLoc);
        activePinchFocusRawX = rootLoc[0] + detector.getFocusX();
        activePinchFocusRawY = rootLoc[1] + detector.getFocusY();
        prepareZoomFocusFromRaw(activePinchFocusRawX, activePinchFocusRawY);
    }

    private void applyPageImagePivotFromPreparedFocus() {
        if (!pendingZoomFocus || pdfViewport == null) return;
        int[] viewportLoc = new int[2];
        pdfViewport.getLocationOnScreen(viewportLoc);
        applyPageImagePivotFromRaw(
                viewportLoc[0] + pendingZoomViewportX,
                viewportLoc[1] + pendingZoomViewportY);
    }

    private void applyPageImagePivotFromRaw(float rawX, float rawY) {
        if (rawX < 0f || rawY < 0f || pageImage == null) return;
        pageImage.animate().cancel();
        int[] imageLoc = new int[2];
        pageImage.getLocationOnScreen(imageLoc);
        pageImage.setPivotX(Math.max(0f, Math.min(pageImage.getWidth(), rawX - imageLoc[0])));
        pageImage.setPivotY(Math.max(0f, Math.min(pageImage.getHeight(), rawY - imageLoc[1])));
    }

    private void showMoreDialog() {
        ThemeManager.getInstance(this).reloadFromStorage();
        resolveReaderThemeColors();

        final android.app.Dialog[] dialogRef = new android.app.Dialog[1];
        LinearLayout box = makeDialogBox();
        box.addView(makeDialogTitle(getString(R.string.more)));

        final TextView[] slideModeRowRef = new TextView[1];
        slideModeRowRef[0] = addDialogActionView(box, pdfSlideModeDialogLabel(), () -> {
            togglePdfSlideMode();
            refreshPdfSlideModeDialogRow(slideModeRowRef[0]);
        });

        addDialogDivider(box);
        addDialogAction(box, getString(R.string.zoom_out), () -> setZoomSmooth(Math.max(0.55f, zoom - 0.2f)));
        addDialogAction(box, getString(R.string.zoom_in), () -> setZoomSmooth(Math.min(4.5f, zoom + 0.2f)));
        addDialogAction(box, getString(R.string.reset_zoom), this::resetZoomToOriginal);
        addDialogDivider(box);
        addDialogAction(box, getString(R.string.settings), () -> {
            if (dialogRef[0] != null) dialogRef[0].dismiss();
            startActivity(new android.content.Intent(this, SettingsActivity.class));
        });
        addDialogAction(box, getString(R.string.file_info), () -> {
            if (dialogRef[0] != null) dialogRef[0].dismiss();
            showFileInfoDialog();
        });
        dialogRef[0] = showCustomDialog(box, getString(R.string.close), true);
    }

    private void refreshPdfSlideModeDialogRow(TextView slideModeRow) {
        if (slideModeRow == null) return;
        slideModeRow.setText(pdfSlideModeDialogLabel());
        slideModeRow.setContentDescription(pdfSlideModeDialogLabel());
    }

    private void toggleDoubleTapZoom(MotionEvent focusEvent) {
        if (zoom <= 1.08f) {
            setZoomSmooth(2.35f, focusEvent);
        } else {
            resetZoomToOriginal();
        }
    }

    private void togglePdfSlideMode() {
        verticalPageSlideMode = !verticalPageSlideMode;
        getSharedPreferences("pdf_reader", MODE_PRIVATE)
                .edit()
                .putBoolean("vertical_page_slide_mode", verticalPageSlideMode)
                .apply();
        updatePdfSlideModeButton();
        applyPdfDisplayMode();
        Toast.makeText(this, pdfSlideModeToastLabel(), Toast.LENGTH_SHORT).show();
    }

    private void updatePdfSlideModeButton() {
        if (slideModeButton == null) return;

        slideModeButton.setText(pdfSlideModeButtonLabel());
        slideModeButton.setCompoundDrawablesWithIntrinsicBounds(
                0,
                verticalPageSlideMode
                        ? R.drawable.ic_pdf_slide_up_down
                        : R.drawable.ic_pdf_slide_left_right,
                0,
                0);
        slideModeButton.setContentDescription(pdfSlideModeDialogLabel());
    }

    private boolean isKoreanUi() {
        return Locale.getDefault().getLanguage().toLowerCase(Locale.ROOT).startsWith("ko");
    }

    private String pdfSlideModeButtonLabel() {
        if (verticalPageSlideMode) return isKoreanUi() ? "세로" : "V";
        return isKoreanUi() ? "가로" : "H";
    }

    private String pdfSlideModeDialogLabel() {
        if (verticalPageSlideMode) {
            return isKoreanUi()
                    ? "읽기 방식: 세로 연속 스크롤"
                    : "Read mode: Vertical continuous scroll";
        }
        return isKoreanUi()
                ? "슬라이드 방식: 가로로 다음/이전 페이지"
                : "Slide mode: Horizontal page swipe";
    }

    private String pdfSlideModeToastLabel() {
        if (verticalPageSlideMode) {
            return isKoreanUi()
                    ? "PDF 세로 연속 스크롤 모드"
                    : "PDF vertical continuous mode";
        }
        return isKoreanUi()
                ? "PDF 가로 슬라이드 모드"
                : "PDF horizontal slide mode";
    }

    private void resetZoomToOriginal() {
        setZoomSmooth(1.0f);
    }

    private void showFileInfoDialog() {
        LinearLayout box = makeDialogBox();
        box.addView(makeDialogTitle(getString(R.string.file_info)));
        addInfoRow(box, getString(R.string.file_info_name), fileName != null ? fileName : "");
        addInfoRow(box, getString(R.string.file_info_type), "PDF");
        addInfoRow(box, getString(R.string.file_info_path), filePath != null ? filePath : "");
        if (localFile != null) {
            addInfoRow(box, getString(R.string.file_info_size), FileUtils.formatFileSize(localFile.length()));
            addInfoRow(box, getString(R.string.file_info_modified), DateFormat.getDateTimeInstance().format(new Date(localFile.lastModified())));
        }
        addInfoRow(box, getString(R.string.bottom_page), String.format(Locale.getDefault(), "%d / %d", currentPage + 1, pageCount));
        showCustomDialog(box, getString(R.string.close), true);
    }

    private void showGoToPageDialog() {
        if (pageCount <= 0) return;

        LinearLayout box = makeDialogBox();
        box.addView(makeDialogTitle(getString(R.string.page_move)));

        TextView label = new TextView(this);
        label.setTextColor(dialogFg());
        label.setTextSize(17f);
        label.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        label.setGravity(android.view.Gravity.CENTER);
        label.setText(formatPageMoveLabel(currentPage + 1, pageCount));
        label.setPadding(0, dpToPx(4), 0, dpToPx(8));
        box.addView(label, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        SeekBar slider = new SeekBar(this);
        slider.setMax(Math.max(0, pageCount - 1));
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

        EditText input = makeDialogInput("1 - " + pageCount);
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
                label.setText(formatPageMoveLabel(progress + 1, pageCount));
                input.setText(String.valueOf(progress + 1));
                input.setSelection(input.getText().length());
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {
                goToPage(pending[0], Integer.compare(pending[0], currentPage));
            }
        });

        final android.app.Dialog[] dialogRef = new android.app.Dialog[1];
        addDialogBottomActions(box, null, getString(R.string.go), () -> {
            try {
                int target = Integer.parseInt(input.getText().toString().trim());
                if (target < 1 || target > pageCount) {
                    Toast.makeText(this, getString(R.string.page_range_error, pageCount), Toast.LENGTH_SHORT).show();
                    return;
                }
                goToPage(target - 1, Integer.compare(target - 1, currentPage));
                if (dialogRef[0] != null) dialogRef[0].dismiss();
            } catch (Exception ignored) {
                Toast.makeText(this, getString(R.string.invalid_page_number), Toast.LENGTH_SHORT).show();
            }
        });
        dialogRef[0] = createStablePositionedDialog(box, 74, true, false);
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

    private void positionPageDialogForThumbReach(@NonNull AlertDialog dialog) {
        if (dialog.getWindow() == null) return;
        android.view.Window window = dialog.getWindow();
        window.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.copyFrom(window.getAttributes());
        lp.width = txtReaderDialogWidthPx();
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        lp.y = dpToPx(74);
        window.setAttributes(lp);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private void positionBookmarkDialogForThumbReach(@NonNull AlertDialog dialog) {
        if (dialog.getWindow() == null) return;
        android.view.Window window = dialog.getWindow();
        window.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.copyFrom(window.getAttributes());
        lp.width = legacyBookmarkDialogWidthPx();
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        lp.y = dpToPx(74);
        window.setAttributes(lp);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
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
        addDialogActionView(box, text, action);
    }

    private TextView addDialogActionView(LinearLayout box, String text, Runnable action) {
        TextView row = new TextView(this);
        row.setText(text);
        row.setContentDescription(text);
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
        return row;
    }

    private TextView makeDialogActionRow(String text, Runnable action) {
        TextView row = new TextView(this);
        row.setText(text);
        row.setContentDescription(text);
        row.setTextColor(dialogFg());
        row.setTextSize(16f);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dpToPx(14), 0, dpToPx(14), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(dialogPanel());
        bg.setCornerRadius(dpToPx(10));
        row.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(48));
        lp.setMargins(0, 0, 0, dpToPx(8));
        row.setLayoutParams(lp);
        row.setOnClickListener(v -> action.run());
        return row;
    }

    private void addInfoRow(LinearLayout box, String label, String value) {
        TextView row = new TextView(this);
        row.setText(label + "\n" + (value != null ? value : ""));
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

    private android.app.Dialog showCustomDialog(LinearLayout box, String closeText) {
        return showCustomDialog(box, closeText, false);
    }

    private android.app.Dialog showCustomDialog(LinearLayout box, String closeText, boolean oneHandLower) {
        final android.app.Dialog[] dialogRef = new android.app.Dialog[1];
        addDialogBottomActions(box, null, closeText, () -> {
            if (dialogRef[0] != null) dialogRef[0].dismiss();
        });
        dialogRef[0] = createStablePositionedDialog(box, oneHandLower ? 34 : 74, false, false);
        dialogRef[0].show();
        return dialogRef[0];
    }

    private android.app.Dialog createStablePositionedDialog(@NonNull View content,
                                                             int yDp,
                                                             boolean adjustResize,
                                                             boolean legacyBookmarkWidth) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        android.widget.FrameLayout outerFrame = new android.widget.FrameLayout(this);
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
        outerFrame.addView(content, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT));
        dialog.setContentView(outerFrame);

        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.copyFrom(window.getAttributes());
            lp.width = legacyBookmarkWidth ? legacyBookmarkDialogWidthPx() : txtReaderDialogWidthPx();
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

    private void positionMoreDialogForThumbReach(@NonNull AlertDialog dialog) {
        if (dialog.getWindow() == null) return;
        android.view.Window window = dialog.getWindow();
        window.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.copyFrom(window.getAttributes());
        lp.width = txtReaderDialogWidthPx();
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        lp.y = dpToPx(34);
        window.setAttributes(lp);
    }

    private void addDialogBottomActions(LinearLayout box, android.app.Dialog dialog, String primaryText, Runnable primaryAction) {
        if (box.findViewWithTag("dialog_actions") != null) return;
        LinearLayout actions = new LinearLayout(this);
        actions.setTag("dialog_actions");
        actions.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.RIGHT);
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

    private void styleDialogWindow(AlertDialog dialog) {
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private void loadPdfFromIntent() {
        if (activityDestroyed) return;
        updateLoadingIndicatorTheme();
        progressBar.setVisibility(View.VISIBLE);
        pageStatus.setText(getString(R.string.loading));

        String path = getIntent().getStringExtra(EXTRA_FILE_PATH);
        String uriStr = getIntent().getStringExtra(EXTRA_FILE_URI);
        int jumpPage = getIntent().getIntExtra(EXTRA_JUMP_TO_PAGE, -1);

        executor.execute(() -> {
            try {
                File pdfFile;
                String loadedName;
                if (path != null) {
                    pdfFile = new File(path);
                    loadedName = pdfFile.getName();
                } else if (uriStr != null) {
                    Uri uri = Uri.parse(uriStr);
                    loadedName = FileUtils.getFileNameFromUri(this, uri);
                    if (loadedName == null || loadedName.trim().isEmpty()) loadedName = "opened.pdf";
                    pdfFile = FileUtils.copyUriToLocal(this, uri, loadedName);
                } else {
                    throw new IllegalArgumentException("No PDF selected");
                }

                final File finalFile = pdfFile;
                final String finalName = loadedName;
                handler.post(() -> {
                    if (!activityDestroyed) openPdfFile(finalFile, finalName, jumpPage);
                });
            } catch (Exception e) {
                handler.post(() -> {
                    if (!activityDestroyed) showLoadError(e);
                });
            }
        });
    }

    private void openPdfFile(@NonNull File pdfFile, String loadedName, int jumpPage) {
        try {
            closeRenderer();
            localFile = pdfFile;
            filePath = pdfFile.getAbsolutePath();
            fileName = loadedName != null ? loadedName : pdfFile.getName();

            if (getSupportActionBar() != null) getSupportActionBar().setTitle(fileName);

            parcelFileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY);
            pdfRenderer = new PdfRenderer(parcelFileDescriptor);
            pageCount = pdfRenderer.getPageCount();
            if (pageCount <= 0) throw new IllegalStateException("PDF has no pages");

            int restored = 0;
            if (jumpPage >= 0) {
                restored = jumpPage;
            } else if (prefs.getAutoSavePosition()) {
                ReaderState state = bookmarkManager.getReadingState(filePath);
                if (state != null) restored = state.getCharPosition();
            }
            currentPage = clampPage(restored);
            saveReadingState();
            applyPdfDisplayMode();
        } catch (Exception e) {
            showLoadError(e);
        }
    }

    private void showLoadError(Exception e) {
        if (activityDestroyed) return;
        progressBar.setVisibility(View.GONE);
        String message = getString(R.string.error_prefix) + (e.getMessage() != null ? e.getMessage() : e.toString());
        pageStatus.setText(message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private int clampPage(int page) {
        if (page < 0) return 0;
        if (pageCount <= 0) return 0;
        return Math.min(page, pageCount - 1);
    }

    private void goToPage(int page) {
        goToPage(page, Integer.compare(page, currentPage));
    }

    private void goToPage(int page, int direction) {
        int target = clampPage(page);
        if (target == currentPage) {
            if (verticalPageSlideMode) {
                scrollContinuousListToCurrentPage(false);
            }
            return;
        }

        pendingPageSlideDirection = direction == 0 ? Integer.compare(target, currentPage) : direction;
        currentPage = target;
        saveReadingState();
        updatePageStatus();

        if (verticalPageSlideMode) {
            // In continuous vertical mode, Go-to-page must jump the RecyclerView to
            // the target page position. Re-rendering the whole continuous list and
            // then smooth-scrolling can leave the list at an approximate/intermediate
            // position, especially while page bitmaps are still binding.
            ensureContinuousPagesConfigured();
            scrollContinuousListToCurrentPage(false);
            pendingPageSlideDirection = 0;
        } else {
            renderCurrentPage();
        }
    }

    private void releaseSinglePageBitmap() {
        ++renderGeneration;
        if (pageImage != null) {
            pageImage.animate().cancel();
            pageImage.setImageDrawable(null);
        }
        if (currentBitmap != null && !currentBitmap.isRecycled()) {
            currentBitmap.recycle();
        }
        currentBitmap = null;
        renderedZoom = 1.0f;
        pendingZoomFocus = false;
    }

    private void renderCurrentPage() {
        if (verticalPageSlideMode) {
            renderContinuousPages();
            return;
        }
        if (pdfRenderer == null || pageCount <= 0 || pdfViewport == null) return;
        final int pageToRender = currentPage;
        final float zoomToRender = zoom;
        final int generation = ++renderGeneration;

        updateLoadingIndicatorTheme();
        progressBar.setVisibility(View.VISIBLE);
        updatePageStatus();

        int viewportWidth = pdfViewport.getWidth();
        if (viewportWidth <= 0) {
            pdfViewport.post(this::renderCurrentPage);
            return;
        }

        executor.execute(() -> {
            Bitmap bitmap = null;
            try {
                synchronized (rendererLock) {
                    if (pdfRenderer == null) return;
                    PdfRenderer.Page page = pdfRenderer.openPage(pageToRender);
                    try {
                        int baseWidth = Math.max(1, viewportWidth - dpToPx(24));
                        float fitScale = baseWidth / (float) page.getWidth();
                        float renderScale = Math.max(0.2f, fitScale * zoomToRender);
                        int width = Math.max(1, Math.round(page.getWidth() * renderScale));
                        int height = Math.max(1, Math.round(page.getHeight() * renderScale));

                        long pixels = (long) width * (long) height;
                        long maxPixels = 22000000L;
                        if (pixels > maxPixels) {
                            float shrink = (float) Math.sqrt(maxPixels / (double) pixels);
                            width = Math.max(1, Math.round(width * shrink));
                            height = Math.max(1, Math.round(height * shrink));
                        }

                        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                        bitmap.eraseColor(Color.WHITE);
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    } finally {
                        page.close();
                    }
                }

                Bitmap finalBitmap = bitmap;
                handler.post(() -> {
                    if (activityDestroyed || generation != renderGeneration) {
                        if (finalBitmap != null && !finalBitmap.isRecycled()) finalBitmap.recycle();
                        return;
                    }
                    Bitmap old = currentBitmap;
                    currentBitmap = finalBitmap;
                    renderedZoom = zoomToRender;
                    pageImage.animate().cancel();
                    pageImage.setScaleX(1.0f);
                    pageImage.setScaleY(1.0f);
                    pageImage.setImageBitmap(finalBitmap);
                    if (pendingPageSlideDirection != 0) {
                        if (pdfHScroll != null) pdfHScroll.post(() -> pdfHScroll.scrollTo(0, 0));
                        if (pdfVScroll != null) pdfVScroll.post(() -> pdfVScroll.scrollTo(0, 0));
                    } else {
                        restoreDoubleTapZoomFocusAfterRender();
                    }
                    runPageSlideInAnimation();
                    if (old != null && old != finalBitmap && !old.isRecycled()) old.recycle();
                    progressBar.setVisibility(View.GONE);
                    updatePageStatus();
                });
            } catch (Exception e) {
                if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
                handler.post(() -> {
                    if (!activityDestroyed) showLoadError(e);
                });
            }
        });
    }

    private void restoreDoubleTapZoomFocusAfterRender() {
        if (!pendingZoomFocus || pageImage == null || pdfViewport == null) return;

        final float xRatio = pendingZoomFocusXRatio;
        final float yRatio = pendingZoomFocusYRatio;
        final float viewportX = pendingZoomViewportX;
        final float viewportY = pendingZoomViewportY;
        pendingZoomFocus = false;

        pageImage.post(() -> {
            if (pageImage == null || pdfViewport == null) return;
            int targetX = Math.round(pageImage.getWidth() * xRatio - viewportX);
            int targetY = Math.round(pageImage.getHeight() * yRatio - viewportY);

            if (pdfHScroll != null) {
                int maxX = 0;
                if (pdfHScroll.getChildCount() > 0) {
                    maxX = Math.max(0, pdfHScroll.getChildAt(0).getWidth() - pdfHScroll.getWidth());
                }
                pdfHScroll.scrollTo(Math.max(0, Math.min(maxX, targetX)), pdfHScroll.getScrollY());
            }
            if (pdfVScroll != null) {
                int maxY = 0;
                if (pdfVScroll.getChildCount() > 0) {
                    maxY = Math.max(0, pdfVScroll.getChildAt(0).getHeight() - pdfVScroll.getHeight());
                }
                pdfVScroll.scrollTo(pdfVScroll.getScrollX(), Math.max(0, Math.min(maxY, targetY)));
            }
        });
    }

    private void runPageSlideInAnimation() {
        if (pageImage == null) return;
        int direction = pendingPageSlideDirection;
        pendingPageSlideDirection = 0;
        if (direction == 0) {
            pageImage.setAlpha(1.0f);
            pageImage.setTranslationX(0f);
            pageImage.setTranslationY(0f);
            return;
        }
        float distance = Math.max(dpToPx(56),
                (verticalPageSlideMode ? pageImage.getHeight() : pageImage.getWidth()) * 0.18f);
        pageImage.setTranslationX(0f);
        pageImage.setTranslationY(0f);
        if (verticalPageSlideMode) {
            pageImage.setTranslationY(direction > 0 ? distance : -distance);
        } else {
            pageImage.setTranslationX(direction > 0 ? distance : -distance);
        }
        pageImage.setAlpha(0.72f);
        pageImage.animate()
                .translationX(0f)
                .translationY(0f)
                .alpha(1.0f)
                .setDuration(150)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
    }

    private void updatePageStatus() {
        if (pageStatus == null) return;
        if (pageCount <= 0) {
            pageStatus.setText("");
            return;
        }
        pageStatus.setText(String.format(Locale.getDefault(), "%d / %d", currentPage + 1, pageCount));
        updatePdfSlideModeButton();
        prevButton.setEnabled(currentPage > 0);
        nextButton.setEnabled(currentPage < pageCount - 1);
    }

    private void addBookmarkForCurrentPage() {
        if (filePath == null || pageCount <= 0) {
            Toast.makeText(this, getString(R.string.file_not_loaded), Toast.LENGTH_SHORT).show();
            return;
        }

        List<Bookmark> existing = bookmarkManager.getBookmarksForFile(filePath);
        for (Bookmark b : existing) {
            if (b.getCharPosition() == currentPage) {
                b.setLineNumber(currentPage + 1);
                b.setExcerpt(pageLabel(currentPage));
                b.setEndPosition(currentPage);
                bookmarkManager.updateBookmark(b);
                Toast.makeText(this, getString(R.string.bookmark_updated), Toast.LENGTH_SHORT).show();
                return;
            }
        }

        Bookmark bookmark = new Bookmark(filePath, fileName, currentPage, currentPage + 1, pageLabel(currentPage));
        bookmark.setEndPosition(currentPage);
        bookmarkManager.addBookmark(bookmark);
        Toast.makeText(this, getString(R.string.bookmark_saved), Toast.LENGTH_SHORT).show();
    }

    private String pageLabel(int zeroBasedPage) {
        return String.format(Locale.getDefault(), "Page %d", zeroBasedPage + 1);
    }

    private void showBookmarksDialog() {
        if (filePath == null) {
            Toast.makeText(this, getString(R.string.file_not_loaded), Toast.LENGTH_SHORT).show();
            return;
        }

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
        title.setPadding(0, 0, 0, dpToPx(4));
        box.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView currentInfo = new TextView(this);
        currentInfo.setTextColor(sub);
        currentInfo.setTextSize(12f);
        currentInfo.setGravity(android.view.Gravity.CENTER);
        currentInfo.setSingleLine(false);
        currentInfo.setLineSpacing(0f, 1.08f);
        currentInfo.setPadding(0, 0, 0, dpToPx(10));
        box.addView(currentInfo, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView hint = new TextView(this);
        hint.setText(getString(R.string.bookmark_folder_hint));
        hint.setTextColor(sub);
        hint.setTextSize(12f);
        hint.setGravity(android.view.Gravity.CENTER);
        hint.setSingleLine(false);
        hint.setLineSpacing(0f, 1.08f);
        hint.setPadding(0, dpToPx(8), 0, dpToPx(6));
        box.addView(hint, new LinearLayout.LayoutParams(
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
        box.addView(saveButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

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
        emptyText.setTextColor(sub);
        emptyText.setGravity(android.view.Gravity.CENTER);
        emptyText.setPadding(0, dpToPx(18), 0, dpToPx(18));
        box.addView(emptyText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams bookmarkListLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(430));
        box.addView(rv, bookmarkListLp);

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
                    adapter.getFolderCount(), all.size(), currentPage + 1, pageCount));
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

        dialog.setOnDismissListener(d -> {});
        refreshRef[0].run();
        dialog.show();
    }

    private void navigateToBookmark(@NonNull Bookmark b) {
        String path = b.getFilePath();
        if (path == null || path.trim().isEmpty()) {
            Toast.makeText(this, getString(R.string.file_not_found_prefix) + "(missing path)", Toast.LENGTH_LONG).show();
            return;
        }

        File target = new File(path.trim());
        if (!target.exists()) {
            Toast.makeText(this, getString(R.string.file_not_found_prefix) + path, Toast.LENGTH_LONG).show();
            return;
        }
        if (path.equals(filePath) || target.getAbsolutePath().equals(filePath)) {
            goToPage(b.getCharPosition(), Integer.compare(b.getCharPosition(), currentPage));
            return;
        }
        android.content.Intent intent;
        String targetPath = target.getAbsolutePath();
        if (FileUtils.isPdfFile(target.getName())) {
            intent = new android.content.Intent(this, PdfReaderActivity.class);
            intent.putExtra(PdfReaderActivity.EXTRA_FILE_PATH, targetPath);
            intent.putExtra(PdfReaderActivity.EXTRA_JUMP_TO_PAGE, b.getCharPosition());
        } else if (FileUtils.isEpubFile(target.getName()) || FileUtils.isWordFile(target.getName())) {
            intent = new android.content.Intent(this, DocumentPageActivity.class);
            intent.putExtra(DocumentPageActivity.EXTRA_FILE_PATH, targetPath);
            intent.putExtra(DocumentPageActivity.EXTRA_JUMP_TO_PAGE, b.getCharPosition());
        } else {
            intent = new android.content.Intent(this, ReaderActivity.class);
            intent.putExtra(ReaderActivity.EXTRA_FILE_PATH, targetPath);
            intent.putExtra(ReaderActivity.EXTRA_JUMP_TO_POSITION, b.getCharPosition());
            intent.putExtra(ReaderActivity.EXTRA_JUMP_DISPLAY_PAGE, b.getPageNumber());
            intent.putExtra(ReaderActivity.EXTRA_JUMP_TOTAL_PAGES, b.getTotalPages());
        }
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
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
        addDialogBottomActions(box, null, getString(R.string.delete), () -> {
            bookmarkManager.deleteBookmark(bookmark.getId());
            afterDelete.run();
            if (dialogRef[0] != null) dialogRef[0].dismiss();
        });
        dialogRef[0] = createStablePositionedDialog(box, 74, false, false);
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
        addDialogBottomActions(box, null, getString(R.string.delete), () -> {
            bookmarkManager.deleteBookmarksForFile(folderFilePath);
            afterDelete.run();
            if (dialogRef[0] != null) dialogRef[0].dismiss();
        });
        dialogRef[0] = createStablePositionedDialog(box, 74, false, false);
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

        dialogRef[0] = createStablePositionedDialog(box, 74, true, false);
        dialogRef[0].show();
    }

    private void saveReadingState() {
        if (filePath == null || !prefs.getAutoSavePosition()) return;
        ReaderState state = new ReaderState(filePath);
        state.setCharPosition(currentPage);
        state.setScrollY(0);
        state.setEncoding("PDF_PAGE");
        bookmarkManager.saveReadingState(state);
    }

    private int dpToPx(float dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void closeRenderer() {
        renderGeneration++;
        resetContinuousPageViews(false);
        synchronized (rendererLock) {
            if (currentBitmap != null && !currentBitmap.isRecycled()) {
                currentBitmap.recycle();
                currentBitmap = null;
            }
            if (pageImage != null) {
                pageImage.setImageDrawable(null);
            }
            if (pdfRenderer != null) {
                pdfRenderer.close();
                pdfRenderer = null;
            }
            if (parcelFileDescriptor != null) {
                try { parcelFileDescriptor.close(); } catch (Exception ignored) {}
                parcelFileDescriptor = null;
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveReadingState();
    }


    private class PdfContinuousPageAdapter extends RecyclerView.Adapter<PdfContinuousPageAdapter.PageViewHolder> {
        private static final float DEFAULT_PDF_PAGE_RATIO = 1.4142f;
        private static final int PAGE_VERTICAL_GAP_DP = 10;

        private int count = 0;
        private int viewportWidth = 0;
        private float adapterZoom = 1.0f;
        private int adapterGeneration = 0;
        private final SparseIntArray pageHeightCache = new SparseIntArray();
        private final SparseIntArray pagePanXCache = new SparseIntArray();
        private final Set<String> pagesRendering = new HashSet<>();
        private final Set<Bitmap> displayedBitmaps = Collections.newSetFromMap(new IdentityHashMap<>());
        private final int cacheMaxKb;
        private final LruCache<Integer, Bitmap> bitmapCache;

        PdfContinuousPageAdapter() {
            setHasStableIds(true);
            cacheMaxKb = calculatePdfContinuousCacheKb();
            bitmapCache = new LruCache<Integer, Bitmap>(cacheMaxKb) {
                @Override
                protected int sizeOf(Integer key, Bitmap value) {
                    if (value == null || value.isRecycled()) return 0;
                    return Math.max(1, value.getByteCount() / 1024);
                }

                @Override
                protected void entryRemoved(boolean evicted, Integer key, Bitmap oldValue, Bitmap newValue) {
                    if (oldValue != null && oldValue != newValue && !oldValue.isRecycled()) {
                        if (displayedBitmaps.contains(oldValue)) {
                            return;
                        }
                        oldValue.recycle();
                    }
                }
            };
        }

        void configure(int newCount, int newViewportWidth, float newZoom) {
            int clampedCount = Math.max(0, newCount);
            int clampedWidth = Math.max(1, newViewportWidth);
            float clampedZoom = Math.max(0.55f, Math.min(4.5f, newZoom));
            boolean changed = count != clampedCount
                    || viewportWidth != clampedWidth
                    || Math.abs(adapterZoom - clampedZoom) > 0.01f;
            count = clampedCount;
            viewportWidth = clampedWidth;
            adapterZoom = clampedZoom;
            if (changed) {
                adapterGeneration++;
                clearCacheAndRenderingState();
                notifyDataSetChanged();
            }
        }

        void prefetchPage(int pageIndex) {
            if (pageIndex < 0 || pageIndex >= count) return;
            if (bitmapCache.get(pageIndex) != null) return;
            startRender(pageIndex, null, adapterGeneration);
        }

        void clearBitmaps() {
            adapterGeneration++;
            clearCacheAndRenderingState();
            if (!activityDestroyed) notifyDataSetChanged();
        }

        void release() {
            adapterGeneration++;
            count = 0;
            clearCacheAndRenderingState();
        }

        private void clearCacheAndRenderingState() {
            synchronized (pagesRendering) {
                pagesRendering.clear();
            }
            pageHeightCache.clear();
            pagePanXCache.clear();
            bitmapCache.evictAll();
        }

        private boolean isBitmapStillCached(@NonNull Bitmap bitmap) {
            for (Bitmap cached : bitmapCache.snapshot().values()) {
                if (cached == bitmap) return true;
            }
            return false;
        }

        private void markBitmapDetached(Bitmap bitmap) {
            if (bitmap == null) return;
            displayedBitmaps.remove(bitmap);
            if (!isBitmapStillCached(bitmap) && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }

        @NonNull
        @Override
        public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ImageView image = new ImageView(parent.getContext());
            image.setAdjustViewBounds(false);
            image.setBackgroundColor(Color.WHITE);
            // Do not use FIT_CENTER here: when zoom > 1.0 the rendered bitmap is
            // intentionally wider/taller than the viewport. FIT_CENTER scales that
            // bitmap back down to the row width and makes vertical-mode zoom look
            // like it did not work. CENTER preserves the rendered zoom size.
            image.setScaleType(ImageView.ScaleType.CENTER);
            image.setContentDescription(getString(R.string.pdf_page));
            image.setPadding(0, 0, 0, 0);

            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    estimatePageRowHeight());
            lp.setMargins(0, 0, 0, dpToPx(PAGE_VERTICAL_GAP_DP));
            image.setLayoutParams(lp);
            return new PageViewHolder(image);
        }

        @Override
        public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
            holder.bind(position, adapterGeneration);
        }

        @Override
        public int getItemCount() {
            return count;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public void onViewRecycled(@NonNull PageViewHolder holder) {
            holder.clear();
            super.onViewRecycled(holder);
        }

        private int estimatePageRowHeight() {
            int cached = pageHeightCache.get(Math.max(0, currentPage), 0);
            if (cached > 0) return cached;
            int baseWidth = Math.max(1, viewportWidth - dpToPx(24));
            int estimated = Math.round(baseWidth * DEFAULT_PDF_PAGE_RATIO * adapterZoom);
            long pixels = (long) baseWidth * (long) estimated;
            if (pixels > getContinuousPageMaxPixels()) {
                float shrink = (float) Math.sqrt(getContinuousPageMaxPixels() / (double) pixels);
                estimated = Math.max(1, Math.round(estimated * shrink));
            }
            return Math.max(dpToPx(220), estimated);
        }

        private int estimatedHeightForPage(int pageIndex) {
            int cached = pageHeightCache.get(pageIndex, 0);
            return cached > 0 ? cached : estimatePageRowHeight();
        }

        private void rememberPageHeight(int pageIndex, int height) {
            if (pageIndex < 0 || height <= 0) return;
            int old = pageHeightCache.get(pageIndex, 0);
            if (Math.abs(old - height) > dpToPx(2)) {
                pageHeightCache.put(pageIndex, height);
            }
        }

        private int bitmapSizeKb(Bitmap bitmap) {
            if (bitmap == null || bitmap.isRecycled()) return 0;
            return Math.max(1, bitmap.getByteCount() / 1024);
        }

        private boolean canCacheBitmap(Bitmap bitmap) {
            return bitmapSizeKb(bitmap) <= Math.max(1, cacheMaxKb);
        }

        private void deliverRenderedBitmap(int pageIndex, int generation, int renderedHeight,
                                           @NonNull Bitmap bitmap, PageViewHolder originalHolder) {
            if (bitmap.isRecycled()) return;
            rememberPageHeight(pageIndex, renderedHeight);

            boolean applied = false;
            if (originalHolder != null) {
                applied = originalHolder.setBitmapIfStillBound(bitmap, pageIndex, generation);
            }

            if (pdfContinuousList != null) {
                RecyclerView.ViewHolder visibleHolder = pdfContinuousList.findViewHolderForAdapterPosition(pageIndex);
                if (visibleHolder instanceof PageViewHolder && visibleHolder != originalHolder) {
                    applied = ((PageViewHolder) visibleHolder).setBitmapIfStillBound(bitmap, pageIndex, generation) || applied;
                }
            }

            if (canCacheBitmap(bitmap)) {
                bitmapCache.put(pageIndex, bitmap);
            } else if (!applied) {
                bitmap.recycle();
                return;
            }

            if (!applied) {
                notifyItemChanged(pageIndex);
            }
        }

        private String renderKeyFor(int pageIndex, int generation) {
            return generation + ":" + pageIndex + ":" + viewportWidth + ":" + Math.round(adapterZoom * 100f);
        }

        private void startRender(int pageIndex, PageViewHolder holder, int generation) {
            if (pdfRenderer == null || pageIndex < 0 || pageIndex >= pageCount) return;
            String key = renderKeyFor(pageIndex, generation);
            synchronized (pagesRendering) {
                if (pagesRendering.contains(key)) return;
                pagesRendering.add(key);
            }
            renderContinuousPageIntoHolder(holder, pageIndex, generation, key,
                    Math.max(1, viewportWidth), adapterZoom);
        }

        private PageViewHolder findBestVisibleHolder() {
            if (pdfContinuousList == null) return null;
            RecyclerView.LayoutManager manager = pdfContinuousList.getLayoutManager();
            if (!(manager instanceof LinearLayoutManager)) return null;
            LinearLayoutManager lm = (LinearLayoutManager) manager;
            int first = lm.findFirstVisibleItemPosition();
            int last = lm.findLastVisibleItemPosition();
            if (first == RecyclerView.NO_POSITION || last == RecyclerView.NO_POSITION) return null;

            int viewportCenter = pdfContinuousList.getHeight() / 2;
            PageViewHolder bestHolder = null;
            int bestDistance = Integer.MAX_VALUE;
            for (int i = first; i <= last; i++) {
                View child = lm.findViewByPosition(i);
                RecyclerView.ViewHolder vh = pdfContinuousList.findViewHolderForAdapterPosition(i);
                if (child == null || !(vh instanceof PageViewHolder)) continue;
                int distance = Math.abs(((child.getTop() + child.getBottom()) / 2) - viewportCenter);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestHolder = (PageViewHolder) vh;
                }
            }
            return bestHolder;
        }

        boolean canPanVisiblePageHorizontally() {
            PageViewHolder holder = findBestVisibleHolder();
            return holder != null && holder.canPanHorizontally();
        }

        boolean panVisiblePageHorizontally(float deltaX) {
            PageViewHolder holder = findBestVisibleHolder();
            return holder != null && holder.panHorizontally(deltaX);
        }

        class PageViewHolder extends RecyclerView.ViewHolder {
            private final ImageView image;
            private Bitmap displayedBitmap;
            private int boundPage = RecyclerView.NO_POSITION;
            private int boundGeneration = -1;
            private int imageWidth = 0;

            PageViewHolder(@NonNull ImageView image) {
                super(image);
                this.image = image;
            }

            void bind(int pageIndex, int generation) {
                clear();
                boundPage = pageIndex;
                boundGeneration = generation;
                image.setBackgroundColor(Color.WHITE);
                setRowHeight(estimatedHeightForPage(pageIndex));

                Bitmap cached = bitmapCache.get(pageIndex);
                if (cached != null && !cached.isRecycled()) {
                    setBitmapIfStillBound(cached, pageIndex, generation);
                    return;
                }

                image.setImageDrawable(null);
                startRender(pageIndex, this, generation);
            }

            boolean setBitmapIfStillBound(Bitmap nextBitmap, int pageIndex, int generation) {
                if (boundPage != pageIndex || boundGeneration != generation || activityDestroyed) {
                    return false;
                }
                if (nextBitmap == null || nextBitmap.isRecycled()) {
                    image.setImageDrawable(null);
                    return false;
                }
                if (displayedBitmap != nextBitmap) {
                    markBitmapDetached(displayedBitmap);
                    displayedBitmap = nextBitmap;
                    displayedBitmaps.add(nextBitmap);
                }
                setImageFrame(nextBitmap.getWidth(), nextBitmap.getHeight());
                image.setImageBitmap(nextBitmap);
                applyHorizontalPan();
                return true;
            }

            void setRowHeight(int height) {
                setImageFrame(Math.max(1, viewportWidth), height);
            }

            private void setImageFrame(int width, int height) {
                ViewGroup.LayoutParams lp = image.getLayoutParams();
                if (lp == null) return;
                int nextWidth = Math.max(Math.max(1, viewportWidth), width);
                int nextHeight = Math.max(dpToPx(180), height);
                imageWidth = nextWidth;
                if (lp.width != nextWidth || lp.height != nextHeight) {
                    lp.width = nextWidth;
                    lp.height = nextHeight;
                    image.setLayoutParams(lp);
                }
            }

            boolean canPanHorizontally() {
                return getHorizontalPanRange() > 0;
            }

            boolean panHorizontally(float deltaX) {
                int range = getHorizontalPanRange();
                if (range <= 0 || boundPage == RecyclerView.NO_POSITION) return false;
                int current = getHorizontalPanOffset(range);
                int next = Math.max(0, Math.min(range, current + Math.round(deltaX)));
                pagePanXCache.put(boundPage, next);
                applyHorizontalPan();
                return next != current;
            }

            private int getHorizontalPanRange() {
                if (pdfContinuousList == null) return 0;
                int viewport = Math.max(1, pdfContinuousList.getWidth());
                return Math.max(0, imageWidth - viewport);
            }

            private int getHorizontalPanOffset(int range) {
                if (boundPage == RecyclerView.NO_POSITION) return 0;
                int stored = pagePanXCache.get(boundPage, Integer.MIN_VALUE);
                if (stored == Integer.MIN_VALUE) {
                    stored = range / 2;
                    pagePanXCache.put(boundPage, stored);
                }
                return Math.max(0, Math.min(range, stored));
            }

            private void applyHorizontalPan() {
                int range = getHorizontalPanRange();
                int offset = range > 0 ? getHorizontalPanOffset(range) : 0;
                image.setTranslationX(-offset);
            }

            void clear() {
                image.setImageDrawable(null);
                image.setTranslationX(0f);
                imageWidth = 0;
                markBitmapDetached(displayedBitmap);
                displayedBitmap = null;
                boundPage = RecyclerView.NO_POSITION;
                boundGeneration = -1;
            }
        }
    }

    private int calculatePdfContinuousCacheKb() {
        long maxMemoryKb = Runtime.getRuntime().maxMemory() / 1024L;
        long targetKb = Math.max(12L * 1024L, Math.min(64L * 1024L, maxMemoryKb / 8L));
        return (int) Math.max(8L * 1024L, targetKb);
    }

    private long getContinuousPageMaxPixels() {
        return 12000000L;
    }

    private void renderContinuousPageIntoHolder(
            PdfContinuousPageAdapter.PageViewHolder holder,
            int pageIndex,
            int generation,
            @NonNull String renderKey,
            int widthForRender,
            float zoomForRender
    ) {
        final int pageToRender = pageIndex;

        executor.execute(() -> {
            Bitmap bitmap = null;
            int renderedHeight = 0;
            try {
                synchronized (rendererLock) {
                    if (activityDestroyed || pdfRenderer == null || pageToRender >= pageCount) {
                        throw new IllegalStateException("PDF renderer is closed");
                    }
                    PdfRenderer.Page page = pdfRenderer.openPage(pageToRender);
                    try {
                        int baseWidth = Math.max(1, widthForRender - dpToPx(24));
                        float fitScale = baseWidth / (float) page.getWidth();
                        float renderScale = Math.max(0.2f, fitScale * zoomForRender);
                        int width = Math.max(1, Math.round(page.getWidth() * renderScale));
                        int height = Math.max(1, Math.round(page.getHeight() * renderScale));

                        long pixels = (long) width * (long) height;
                        long maxPixels = getContinuousPageMaxPixels();
                        if (pixels > maxPixels) {
                            float shrink = (float) Math.sqrt(maxPixels / (double) pixels);
                            width = Math.max(1, Math.round(width * shrink));
                            height = Math.max(1, Math.round(height * shrink));
                        }

                        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                        bitmap.eraseColor(Color.WHITE);
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                        renderedHeight = height;
                    } finally {
                        page.close();
                    }
                }

                Bitmap finalBitmap = bitmap;
                int finalRenderedHeight = renderedHeight;
                handler.post(() -> {
                    if (pdfContinuousAdapter != null) {
                        synchronized (pdfContinuousAdapter.pagesRendering) {
                            pdfContinuousAdapter.pagesRendering.remove(renderKey);
                        }
                    }
                    if (activityDestroyed || pdfContinuousAdapter == null || generation != pdfContinuousAdapter.adapterGeneration) {
                        if (finalBitmap != null && !finalBitmap.isRecycled()) finalBitmap.recycle();
                        return;
                    }
                    if (finalBitmap == null || finalBitmap.isRecycled()) return;
                    pdfContinuousAdapter.deliverRenderedBitmap(pageToRender, generation,
                            finalRenderedHeight, finalBitmap, holder);
                    if (verticalPageSlideMode && pageToRender == currentPage) {
                        updatePageStatus();
                    }
                });
            } catch (Exception e) {
                if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
                handler.post(() -> {
                    if (pdfContinuousAdapter != null) {
                        synchronized (pdfContinuousAdapter.pagesRendering) {
                            pdfContinuousAdapter.pagesRendering.remove(renderKey);
                        }
                    }
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        ViewerRegistry.unregister(this);
        activityDestroyed = true;
        ++renderGeneration;
        handler.removeCallbacksAndMessages(null);
        saveReadingState();
        if (pageImage != null) {
            pageImage.animate().cancel();
            pageImage.setImageDrawable(null);
        }
        if (pdfContinuousList != null) {
            pdfContinuousList.stopScroll();
            if (continuousScrollListener != null) {
                pdfContinuousList.removeOnScrollListener(continuousScrollListener);
            }
            pdfContinuousList.setAdapter(null);
        }
        if (pdfContinuousAdapter != null) {
            pdfContinuousAdapter.release();
        }
        continuousScrollListener = null;
        closeRenderer();
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
