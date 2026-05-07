package com.simpletext.reader;

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
import android.view.MenuItem;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
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
import java.util.ArrayList;
import java.util.HashSet;
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
    private ImageView pageImage;
    private ProgressBar progressBar;
    private TextView pageStatus;
    private TextView prevButton;
    private TextView nextButton;
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
    private boolean gestureStartedAtLeftEdge = true;
    private boolean gestureStartedAtRightEdge = true;

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
        com.simpletext.reader.util.EdgeToEdgeUtil.applyStandardInsets(this,
                findViewById(R.id.pdf_root),
                findViewById(R.id.pdf_appbar),
                findViewById(R.id.pdf_bottom_bar));
        applyDocumentSystemBarColors();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setTitleTextColor(Color.WHITE);
        toolbar.setNavigationIcon(tintedBackIcon());

        root = findViewById(R.id.pdf_root);
        pageImage = findViewById(R.id.pdf_page_image);
        progressBar = findViewById(R.id.pdf_progress);
        pageStatus = findViewById(R.id.pdf_page_status);
        prevButton = findViewById(R.id.pdf_prev);
        nextButton = findViewById(R.id.pdf_next);
        pageButton = findViewById(R.id.pdf_page);
        bookmarkButton = findViewById(R.id.pdf_bookmark);
        zoomMoreButton = findViewById(R.id.pdf_zoom_more);
        pdfViewport = findViewById(R.id.pdf_viewport);
        pdfHScroll = findViewById(R.id.pdf_h_scroll);
        pdfVScroll = findViewById(R.id.pdf_v_scroll);

        bookmarkManager = BookmarkManager.getInstance(this);
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

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (handlePdfViewportGesture(event)) return true;
        return super.dispatchTouchEvent(event);
    }

    private boolean handlePdfViewportGesture(@NonNull MotionEvent event) {
        if (pdfViewport == null || pageCount <= 0) return false;

        boolean insideViewport = isEventInsideView(pdfViewport, event);
        if (insideViewport && gestureDetector != null && gestureDetector.onTouchEvent(event)) {
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
                gestureStartedAtLeftEdge = !gestureStartedWithHorizontalScrollable || isPdfAtLeftEdge(dpToPx(3));
                gestureStartedAtRightEdge = !gestureStartedWithHorizontalScrollable || isPdfAtRightEdge(dpToPx(3));
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
                boolean strongPageSwipe = Math.abs(dx) >= dpToPx(82) && Math.abs(dx) > Math.abs(dy) * 1.35f;
                if (strongPageSwipe && canTurnPdfPageFromSwipe(dx)) {
                    if (dx < 0) goToPage(currentPage + 1, 1);
                    else goToPage(currentPage - 1, -1);
                    resetViewportGesture();
                    return true;
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
        gestureStartedAtLeftEdge = true;
        gestureStartedAtRightEdge = true;
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
        View appbar = findViewById(R.id.pdf_appbar);
        if (appbar != null) appbar.setBackgroundColor(readerBg);
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setBackgroundColor(readerBg);
            toolbar.setTitleTextColor(readerFg);
            toolbar.setNavigationIcon(tintedBackIcon());
        }
        View bottom = findViewById(R.id.pdf_bottom_bar);
        if (bottom != null) bottom.setBackgroundColor(readerPanel);
        if (pdfViewport != null) pdfViewport.setBackgroundColor(readerPanel);
        if (pageStatus != null) pageStatus.setTextColor(readerFg);

        TextView[] buttons = {prevButton, nextButton, pageButton, bookmarkButton, zoomMoreButton};
        for (TextView b : buttons) {
            if (b == null) continue;
            b.setTextColor(readerFg);
            b.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(readerFg));
        }
    }

    private void setupControls() {
        prevButton.setOnClickListener(v -> goToPage(currentPage - 1, -1));
        nextButton.setOnClickListener(v -> goToPage(currentPage + 1, 1));
        if (pageButton != null) pageButton.setOnClickListener(v -> showGoToPageDialog());
        bookmarkButton.setOnClickListener(v -> showBookmarksDialog());
        if (zoomMoreButton != null) zoomMoreButton.setOnClickListener(v -> showMoreDialog());
    }


    private void installPdfGestures() {
        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                if (pageImage != null) {
                    pageImage.animate().cancel();
                    pageImage.setPivotX(detector.getFocusX());
                    pageImage.setPivotY(detector.getFocusY());
                }
                return true;
            }

            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float nextZoom = Math.max(0.55f, Math.min(4.5f, zoom * detector.getScaleFactor()));
                if (Math.abs(nextZoom - zoom) > 0.004f) {
                    zoom = nextZoom;
                    pinchZoomChanged = true;
                    if (pageImage != null) {
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
                    renderCurrentPage();
                }
            }
        });

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                toggleDoubleTapZoom();
                return true;
            }
        });
    }

    private void setZoomSmooth(float targetZoom) {
        zoom = Math.max(0.55f, Math.min(4.5f, targetZoom));
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

    private void showMoreDialog() {
        final int bg = dialogBg();
        final int fg = dialogFg();
        LinearLayout box = makeDialogBox();
        box.addView(makeDialogTitle(getString(R.string.more)));
        addDialogAction(box, getString(R.string.zoom_out), () -> setZoomSmooth(Math.max(0.55f, zoom - 0.2f)));
        addDialogAction(box, getString(R.string.zoom_in), () -> setZoomSmooth(Math.min(4.5f, zoom + 0.2f)));
        addDialogAction(box, getString(R.string.reset_zoom), this::resetZoomToOriginal);
        addDialogDivider(box);
        addDialogAction(box, getString(R.string.settings), () -> startActivity(new android.content.Intent(this, SettingsActivity.class)));
        addDialogAction(box, getString(R.string.file_info), this::showFileInfoDialog);
        showCustomDialog(box, getString(R.string.close), true);
    }

    private void toggleDoubleTapZoom() {
        if (zoom <= 1.08f) {
            setZoomSmooth(2.35f);
        } else {
            resetZoomToOriginal();
        }
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
        showCustomDialog(box, getString(R.string.close));
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

        AlertDialog dialog = new AlertDialog.Builder(this).create();
        dialog.setView(box);
        dialog.setOnShowListener(d -> {
            styleDialogWindow(dialog);
            addDialogBottomActions(box, dialog, getString(R.string.go), () -> {
                try {
                    int target = Integer.parseInt(input.getText().toString().trim());
                    if (target < 1 || target > pageCount) {
                        Toast.makeText(this, getString(R.string.page_range_error, pageCount), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    goToPage(target - 1, Integer.compare(target - 1, currentPage));
                    dialog.dismiss();
                } catch (Exception ignored) {
                    Toast.makeText(this, getString(R.string.invalid_page_number), Toast.LENGTH_SHORT).show();
                }
            });
        });
        dialog.show();
        positionPageDialogForThumbReach(dialog);
    }

    private void positionPageDialogForThumbReach(@NonNull AlertDialog dialog) {
        if (dialog.getWindow() == null) return;
        android.view.Window window = dialog.getWindow();
        window.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.copyFrom(window.getAttributes());
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        lp.width = Math.min(screenWidth - dpToPx(14), dpToPx(460));
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

    private void showCustomDialog(LinearLayout box, String closeText) {
        showCustomDialog(box, closeText, false);
    }

    private void showCustomDialog(LinearLayout box, String closeText, boolean oneHandLower) {
        AlertDialog dialog = new AlertDialog.Builder(this).create();
        dialog.setView(box);
        dialog.setOnShowListener(d -> {
            styleDialogWindow(dialog);
            addDialogBottomActions(box, dialog, closeText, dialog::dismiss);
        });
        dialog.show();
        if (oneHandLower) positionMoreDialogForThumbReach(dialog);
    }

    private void positionMoreDialogForThumbReach(@NonNull AlertDialog dialog) {
        if (dialog.getWindow() == null) return;
        android.view.Window window = dialog.getWindow();
        window.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.copyFrom(window.getAttributes());
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        lp.width = Math.min(screenWidth - dpToPx(16), dpToPx(460));
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        lp.y = dpToPx(34);
        window.setAttributes(lp);
    }

    private void addDialogBottomActions(LinearLayout box, AlertDialog dialog, String primaryText, Runnable primaryAction) {
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
            renderCurrentPage();
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
        if (target == currentPage) return;
        pendingPageSlideDirection = direction == 0 ? Integer.compare(target, currentPage) : direction;
        currentPage = target;
        saveReadingState();
        renderCurrentPage();
    }

    private void renderCurrentPage() {
        if (pdfRenderer == null || pageCount <= 0 || pdfViewport == null) return;
        final int pageToRender = currentPage;
        final float zoomToRender = zoom;
        final int generation = ++renderGeneration;

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

    private void runPageSlideInAnimation() {
        if (pageImage == null) return;
        int direction = pendingPageSlideDirection;
        pendingPageSlideDirection = 0;
        if (direction == 0) {
            pageImage.setAlpha(1.0f);
            pageImage.setTranslationX(0f);
            return;
        }
        float distance = Math.max(dpToPx(56), pageImage.getWidth() * 0.18f);
        pageImage.setTranslationX(direction > 0 ? distance : -distance);
        pageImage.setAlpha(0.72f);
        pageImage.animate()
                .translationX(0f)
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
        title.setPadding(0, 0, 0, dpToPx(12));
        box.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView currentInfo = new TextView(this);
        currentInfo.setTextColor(sub);
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
        emptyText.setTextColor(sub);
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

        AlertDialog dialog = new AlertDialog.Builder(this).create();
        dialog.setView(box);
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }
        });

        final Runnable[] refreshRef = new Runnable[1];
        refreshRef[0] = () -> {
            List<Bookmark> all = bookmarkManager.getAllBookmarks();
            adapter.setBookmarks(all, expandedFolders, filePath);
            emptyText.setVisibility(all.isEmpty() ? View.VISIBLE : View.GONE);
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
            goToPage(b.getCharPosition(), Integer.compare(b.getCharPosition(), currentPage));
            return;
        }
        android.content.Intent intent;
        if (FileUtils.isPdfFile(target.getName())) {
            intent = new android.content.Intent(this, PdfReaderActivity.class);
            intent.putExtra(PdfReaderActivity.EXTRA_FILE_PATH, b.getFilePath());
            intent.putExtra(PdfReaderActivity.EXTRA_JUMP_TO_PAGE, b.getCharPosition());
        } else if (FileUtils.isEpubFile(target.getName()) || FileUtils.isWordFile(target.getName())) {
            intent = new android.content.Intent(this, DocumentPageActivity.class);
            intent.putExtra(DocumentPageActivity.EXTRA_FILE_PATH, b.getFilePath());
            intent.putExtra(DocumentPageActivity.EXTRA_JUMP_TO_PAGE, b.getCharPosition());
        } else {
            intent = new android.content.Intent(this, ReaderActivity.class);
            intent.putExtra(ReaderActivity.EXTRA_FILE_PATH, b.getFilePath());
            intent.putExtra(ReaderActivity.EXTRA_JUMP_TO_POSITION, b.getCharPosition());
        }
        startActivity(intent);
    }

    private void showBookmarkDeleteConfirm(@NonNull Bookmark bookmark, @NonNull Runnable afterDelete) {
        new AlertDialog.Builder(this)
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
        new AlertDialog.Builder(this)
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
        synchronized (rendererLock) {
            if (currentBitmap != null && !currentBitmap.isRecycled()) {
                currentBitmap.recycle();
                currentBitmap = null;
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

    @Override
    protected void onDestroy() {
        activityDestroyed = true;
        ++renderGeneration;
        handler.removeCallbacksAndMessages(null);
        saveReadingState();
        if (pageImage != null) {
            pageImage.animate().cancel();
            pageImage.setImageDrawable(null);
        }
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
