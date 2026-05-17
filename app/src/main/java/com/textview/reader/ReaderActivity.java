package com.textview.reader;

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
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Button;
import android.widget.CompoundButton;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

import com.textview.reader.adapter.BookmarkFolderAdapter;
import com.textview.reader.model.Bookmark;
import com.textview.reader.model.ReaderState;
import com.textview.reader.model.Theme;
import com.textview.reader.util.BookmarkManager;
import com.textview.reader.util.FileUtils;
import com.textview.reader.util.FontManager;
import com.textview.reader.util.PrefsManager;
import com.textview.reader.util.PageIndexCacheManager;
import com.textview.reader.util.ReadingNotificationHelper;
import com.textview.reader.util.ThemeManager;
import com.textview.reader.util.TextDisplayRule;
import com.textview.reader.util.TextDisplayRuleManager;
import com.textview.reader.view.CustomReaderView;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.RandomAccessFile;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.CoderResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class ReaderActivity extends AppCompatActivity {

    private static final long VIEWER_DOUBLE_BACK_TIMEOUT_MS = 1000L;
    private static final long VIEWER_BACK_TOAST_DURATION_MS = 650L;
    private static final long LARGE_TEXT_FAST_OPEN_THRESHOLD_BYTES = 3L * 1024L * 1024L;
    private static final long HUGE_TEXT_PREVIEW_ONLY_THRESHOLD_BYTES = 32L * 1024L * 1024L;
    private static final int LARGE_TEXT_PARTITION_LINES = 4000;
    private static final int LARGE_TEXT_PARTITION_LOOKAHEAD_LINES = 120;
    // Kept only for sampling/legacy estimate paths; active large-TXT windows are line-based.
    private static final int LARGE_TEXT_PARTITION_BYTES = 3 * 1024 * 1024;
    private static final int LARGE_TEXT_PREVIEW_BYTES = LARGE_TEXT_PARTITION_BYTES;
    private static final int TXT_TOOLBAR_POPUP_Y_DP = 74;
    private static final String TXT_ACTUAL_FILE_EDIT_PREFS = "txt_actual_file_edit";
    private static final String KEY_TXT_ACTUAL_FILE_EDIT_PATH = "modified_path";
    private static final String KEY_TXT_ACTUAL_FILE_EDIT_TOKEN = "modified_token";
    private static final String KEY_TXT_ACTUAL_FILE_EDIT_LENGTH = "modified_length";
    private static final String KEY_TXT_ACTUAL_FILE_EDIT_LAST_MODIFIED = "modified_last_modified";
    private static final int TXT_BOOKMARK_POPUP_Y_DP = 34;
    private static final int TXT_BOOKMARK_HINT_POPUP_Y_DP = 112;
    private static final String FONT_OPTION_SYSTEM_CURRENT = "system_current";

    public static final String EXTRA_FILE_PATH = "file_path";
    public static final String EXTRA_FILE_URI = "file_uri";
    public static final String EXTRA_JUMP_TO_POSITION = "jump_position";
    public static final String EXTRA_JUMP_DISPLAY_PAGE = "jump_display_page";
    public static final String EXTRA_JUMP_TOTAL_PAGES = "jump_total_pages";
    public static final String EXTRA_JUMP_PARTITION_START_BYTE = "jump_partition_start_byte";
    public static final String EXTRA_JUMP_PARTITION_START_LINE = "jump_partition_start_line";
    public static final String EXTRA_JUMP_ANCHOR_BEFORE = "jump_anchor_before";
    public static final String EXTRA_JUMP_ANCHOR_AFTER = "jump_anchor_after";

    private static final String STATE_RESTORE_FROM_MEMORY = "restore_txt_from_memory";
    private static volatile LoadedTextSnapshot lastLoadedTextSnapshot;

    private static final class LoadedTextSnapshot {
        final String sourcePath;
        final String sourceUri;
        final String filePath;
        final String fileName;
        final String fileContent;
        final int totalChars;
        final int totalLines;
        final int charPosition;
        final String activeSearchQuery;
        final int activeSearchIndex;
        final boolean largeTextEstimateActive;
        final int largeTextEstimatedTotalPages;
        final int pendingLargeTextRestorePosition;
        final int largeTextPreviewBaseCharOffset;
        final int largeTextEstimatedBasePageOffset;
        final int largeTextEstimatedTotalChars;
        final boolean hugeTextPreviewOnly;
        final int pendingLargeTextCachedDisplayPage;
        final int pendingLargeTextCachedTotalPages;
        final long largeTextPartitionStartByte;
        final long largeTextPartitionEndByte;
        final long largeTextFileByteLength;
        final float largeTextEstimatedBytesPerChar;
        final int largeTextPartitionBodyCharCount;
        final int largeTextPartitionStartLine;
        final int largeTextPartitionEndLine;
        final int largeTextTotalLogicalLines;

        LoadedTextSnapshot(String sourcePath,
                           String sourceUri,
                           String filePath,
                           String fileName,
                           String fileContent,
                           int totalChars,
                           int totalLines,
                           int charPosition,
                           String activeSearchQuery,
                           int activeSearchIndex,
                           boolean largeTextEstimateActive,
                           int largeTextEstimatedTotalPages,
                           int pendingLargeTextRestorePosition,
                           int largeTextPreviewBaseCharOffset,
                           int largeTextEstimatedBasePageOffset,
                           int largeTextEstimatedTotalChars,
                           boolean hugeTextPreviewOnly,
                           int pendingLargeTextCachedDisplayPage,
                           int pendingLargeTextCachedTotalPages,
                           long largeTextPartitionStartByte,
                           long largeTextPartitionEndByte,
                           long largeTextFileByteLength,
                           float largeTextEstimatedBytesPerChar,
                           int largeTextPartitionBodyCharCount,
                           int largeTextPartitionStartLine,
                           int largeTextPartitionEndLine,
                           int largeTextTotalLogicalLines) {
            this.sourcePath = sourcePath;
            this.sourceUri = sourceUri;
            this.filePath = filePath;
            this.fileName = fileName;
            this.fileContent = fileContent != null ? fileContent : "";
            this.totalChars = totalChars;
            this.totalLines = totalLines;
            this.charPosition = Math.max(0, charPosition);
            this.activeSearchQuery = activeSearchQuery != null ? activeSearchQuery : "";
            this.activeSearchIndex = activeSearchIndex;
            this.largeTextEstimateActive = largeTextEstimateActive;
            this.largeTextEstimatedTotalPages = largeTextEstimatedTotalPages;
            this.pendingLargeTextRestorePosition = pendingLargeTextRestorePosition;
            this.largeTextPreviewBaseCharOffset = largeTextPreviewBaseCharOffset;
            this.largeTextEstimatedBasePageOffset = largeTextEstimatedBasePageOffset;
            this.largeTextEstimatedTotalChars = largeTextEstimatedTotalChars;
            this.hugeTextPreviewOnly = hugeTextPreviewOnly;
            this.pendingLargeTextCachedDisplayPage = pendingLargeTextCachedDisplayPage;
            this.pendingLargeTextCachedTotalPages = pendingLargeTextCachedTotalPages;
            this.largeTextPartitionStartByte = Math.max(0L, largeTextPartitionStartByte);
            this.largeTextPartitionEndByte = Math.max(0L, largeTextPartitionEndByte);
            this.largeTextFileByteLength = Math.max(0L, largeTextFileByteLength);
            this.largeTextEstimatedBytesPerChar = Math.max(1f, largeTextEstimatedBytesPerChar);
            this.largeTextPartitionBodyCharCount = Math.max(0, largeTextPartitionBodyCharCount);
            this.largeTextPartitionStartLine = Math.max(1, largeTextPartitionStartLine);
            this.largeTextPartitionEndLine = Math.max(this.largeTextPartitionStartLine, largeTextPartitionEndLine);
            this.largeTextTotalLogicalLines = Math.max(1, largeTextTotalLogicalLines);
        }

        boolean matches(@NonNull Intent intent) {
            String path = intent.getStringExtra(EXTRA_FILE_PATH);
            String uri = intent.getStringExtra(EXTRA_FILE_URI);

            if (path != null && sourcePath != null) {
                return path.equals(sourcePath) || path.equals(filePath);
            }
            if (uri != null && sourceUri != null) {
                return uri.equals(sourceUri);
            }
            if (path != null && filePath != null) {
                return path.equals(filePath);
            }
            return false;
        }
    }

    private View readerRoot;
    private CustomReaderView readerView;
    private ProgressBar progressBar;
    private TextView progressText;
    private View loadingBox;
    private View bottomBar;
    private View navBarSpacer;
    private View pageDragPanel;
    private SeekBar seekBar;
    private TextView positionLabel;
    private TextView readerPageStatus;
    private TextView readerFileTitle;
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
    private int currentReaderTextColor = Color.rgb(224, 224, 224);
    private int lastReaderTopInset = 0;
    private int lastReaderBottomInset = 0;
    private int lastStatusOffExtraTopPadding = 0;
    private boolean scrollUpdateScheduled = false;
    private boolean suppressSeekCallback = false;
    private boolean toolbarSeekTracking = false;
    private boolean pendingToolbarSeekJump = false;
    private int pendingToolbarSeekPage = -1;
    private int pendingToolbarSeekTotalPages = 1;
    private int loadingWindowPartitionJumpGeneration = -1;
    private final Runnable pendingToolbarSeekTimeoutRunnable = this::clearStalePendingToolbarSeekJump;
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
    private final ExecutorService largeTextPartitionExecutor = Executors.newSingleThreadExecutor();
    private volatile boolean activityDestroyed = false;
    private final AtomicInteger loadGeneration = new AtomicInteger(0);
    private final AtomicInteger largeTextPartitionSwitchGeneration = new AtomicInteger(0);
    private final Object largeTextPartitionCacheLock = new Object();
    private final LinkedHashMap<Integer, LargeTextLinePartitionResult> largeTextPartitionCache =
            new LinkedHashMap<Integer, LargeTextLinePartitionResult>(7, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, LargeTextLinePartitionResult> eldest) {
                    return size() > 7;
                }
            };
    private final Set<Integer> largeTextPendingPartitionPrefetches = new HashSet<>();
    private boolean largeTextEstimateActive = false;
    private int largeTextEstimatedTotalPages = 0;
    private boolean largeTextPartitionSwitchInProgress = false;
    private int largeTextPartitionPendingDisplayPage = 0;
    private int largeTextPartitionPendingTotalPages = 0;
    private int pendingLargeTextRestorePosition = -1;
    private int largeTextPreviewBaseCharOffset = 0;
    private int largeTextEstimatedBasePageOffset = 0;
    private int largeTextEstimatedTotalChars = 0;
    private boolean hugeTextPreviewOnly = false;
    private int pendingLargeTextCachedDisplayPage = 0;
    private int pendingLargeTextCachedTotalPages = 0;
    private long largeTextPartitionStartByte = 0L;
    private long largeTextPartitionEndByte = 0L;
    private long largeTextFileByteLength = 0L;
    private float largeTextEstimatedBytesPerChar = 1f;
    private int largeTextPartitionBodyCharCount = 0;
    private int largeTextPartitionStartLine = 1;
    private int largeTextPartitionEndLine = 1;
    private int largeTextTotalLogicalLines = 1;
    private final Object largeTextExactPageIndexLock = new Object();
    private ArrayList<CustomReaderView.PageTextAnchor> largeTextExactPageAnchors = new ArrayList<>();
    private boolean largeTextExactPageIndexReady = false;
    private boolean largeTextExactPageIndexBuilding = false;
    private final AtomicInteger largeTextExactPageIndexGeneration = new AtomicInteger(0);
    private String largeTextExactPageIndexSignature = "";
    private String appliedTextDisplayRuleSignature = "none";
    private boolean pendingTxtDisplayRuleContentRefresh = false;
    private int largeTextLastPageDirection = 0;
    private int largeTextSameDirectionPageCount = 0;
    private int queuedLargeTextPageDelta = 0;
    private boolean autoPageTurnRunning = false;
    private final Runnable autoPageTurnRunnable = new Runnable() {
        @Override public void run() {
            if (!autoPageTurnRunning || activityDestroyed) return;
            int total = Math.max(1, getDisplayedTotalPageCount());
            int current = Math.max(1, getDisplayedCurrentPageNumber());
            if (current >= total) {
                stopAutoPageTurn(true);
                Toast.makeText(ReaderActivity.this, R.string.auto_page_turn_end_reached, Toast.LENGTH_SHORT).show();
                return;
            }
            pageBy(+1, true);
            scheduleNextAutoPageTurn();
        }
    };

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
        loadingBox = findViewById(R.id.loading_box);
        progressBar = findViewById(R.id.loading_progress);
        progressText = findViewById(R.id.loading_text);
        bottomBar = findViewById(R.id.bottom_bar);
        navBarSpacer = findViewById(R.id.nav_bar_spacer);
        pageDragPanel = findViewById(R.id.page_drag_panel);
        seekBar = findViewById(R.id.seek_bar);
        positionLabel = findViewById(R.id.position_label);
        readerPageStatus = findViewById(R.id.reader_page_status);
        readerFileTitle = findViewById(R.id.reader_file_title);
        updateReaderFileTitle();
        updateReaderFileTitleVisibility();
        updateLoadingIndicatorColors(currentReaderBackgroundColor);

        bookmarkManager = BookmarkManager.getInstance(this);
        themeManager = ThemeManager.getInstance(this);
        notificationHelper = new ReadingNotificationHelper(this);

        readerView.setReaderListener(new CustomReaderView.ReaderListener() {
            @Override public void onSingleTap(float x, float y) { handleSingleTap(x, y); }
            @Override public void onTextLongPress(String selectedText, int charPosition, float x, float y) {
                showQuickTextDisplayRuleDialog(selectedText, true);
            }
            @Override public void onReaderScrollChanged() { onScrollChanged(); }
            @Override public void onReaderManualScroll() { stopAutoPageTurnForManualNavigation(); }
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

        if (!restoreLoadedTextSnapshotIfAvailable(getIntent(), savedInstanceState)) {
            loadFileFromIntent(getIntent());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (themeManager != null) {
            themeManager.reloadFromStorage();
        }
        if (readerView != null && prefs != null && themeManager != null) {
            applyTheme();
            if (maybeReloadForPhysicallyEditedOriginalTxtFile()) return;
            maybeReloadForTextDisplayRuleChange();
            updatePositionLabel();
        }
    }

    private boolean maybeReloadForPhysicallyEditedOriginalTxtFile() {
        if (filePath == null || filePath.isEmpty() || readerView == null) return false;
        android.content.SharedPreferences markerPrefs = getSharedPreferences(TXT_ACTUAL_FILE_EDIT_PREFS, MODE_PRIVATE);
        String modifiedPath = markerPrefs.getString(KEY_TXT_ACTUAL_FILE_EDIT_PATH, "");
        long token = markerPrefs.getLong(KEY_TXT_ACTUAL_FILE_EDIT_TOKEN, 0L);
        if (token <= 0L || modifiedPath == null || modifiedPath.isEmpty()) return false;

        File currentFile = new File(filePath).getAbsoluteFile();
        File modifiedFile = new File(modifiedPath).getAbsoluteFile();
        if (!currentFile.getAbsolutePath().equals(modifiedFile.getAbsolutePath())) return false;

        markerPrefs.edit()
                .remove(KEY_TXT_ACTUAL_FILE_EDIT_PATH)
                .remove(KEY_TXT_ACTUAL_FILE_EDIT_TOKEN)
                .remove(KEY_TXT_ACTUAL_FILE_EDIT_LENGTH)
                .remove(KEY_TXT_ACTUAL_FILE_EDIT_LAST_MODIFIED)
                .apply();

        int currentPosition = Math.max(0, getCurrentCharPosition());
        Intent reloadIntent = new Intent(getIntent());
        reloadIntent.putExtra(EXTRA_FILE_PATH, currentFile.getAbsolutePath());
        reloadIntent.removeExtra(EXTRA_FILE_URI);
        reloadIntent.putExtra(EXTRA_JUMP_TO_POSITION, currentPosition);
        reloadIntent.putExtra(EXTRA_JUMP_DISPLAY_PAGE, getDisplayedCurrentPageNumber());
        reloadIntent.putExtra(EXTRA_JUMP_TOTAL_PAGES, getDisplayedTotalPageCount());
        clearLoadedTextSnapshot();
        resetLargeTextExactPageIndex();
        clearLargeTextPartitionCache();
        loadFileFromIntent(reloadIntent);
        return true;
    }

    private void maybeReloadForTextDisplayRuleChange() {
        if (filePath == null || filePath.isEmpty() || readerView == null) return;
        String current = TextDisplayRuleManager.getSignature(getApplicationContext(), filePath);
        if (current.equals(appliedTextDisplayRuleSignature)) return;
        int currentPosition = Math.max(0, getCurrentCharPosition());
        Intent reloadIntent = new Intent(getIntent());
        reloadIntent.putExtra(EXTRA_FILE_PATH, filePath);
        reloadIntent.removeExtra(EXTRA_FILE_URI);
        reloadIntent.putExtra(EXTRA_JUMP_TO_POSITION, currentPosition);
        reloadIntent.putExtra(EXTRA_JUMP_DISPLAY_PAGE, getDisplayedCurrentPageNumber());
        reloadIntent.putExtra(EXTRA_JUMP_TOTAL_PAGES, getDisplayedTotalPageCount());
        clearLoadedTextSnapshot();
        loadFileFromIntent(reloadIntent);
    }

    private String currentTextDisplayRuleSignature() {
        if (filePath == null || filePath.isEmpty()) return "none";
        return TextDisplayRuleManager.getSignature(getApplicationContext(), filePath);
    }

    private void requestTextDisplayRuleContentRefreshOnWindowClose() {
        pendingTxtDisplayRuleContentRefresh = true;
    }

    private void acknowledgeTextDisplayRuleWindowNoContentChange() {
        if (!pendingTxtDisplayRuleContentRefresh) {
            appliedTextDisplayRuleSignature = currentTextDisplayRuleSignature();
        }
    }

    private void applyPendingTextDisplayRuleWindowRefresh() {
        if (!pendingTxtDisplayRuleContentRefresh) return;
        pendingTxtDisplayRuleContentRefresh = false;
        maybeReloadForTextDisplayRuleChange();
    }

    private boolean sameTextDisplayRuleValue(String a, String b) {
        return TextUtils.equals(a != null ? a : "", b != null ? b : "");
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);

        // Single-viewer mode:
        // when a new TXT file is opened while a viewer already exists in this task,
        // reuse this ReaderActivity instead of stacking another ReaderActivity.
        saveReadingState();
        setIntent(intent);
        clearPendingToolbarSeekJump();
        activeSearchQuery = "";
        activeSearchIndex = -1;
        applySearchHighlight();
        clearLoadedTextSnapshot();
        loadFileFromIntent(intent);
    }

    private void cacheLoadedTextSnapshot() {
        if (readerView == null || fileContent == null || fileContent.isEmpty() || filePath == null) {
            return;
        }

        Intent intent = getIntent();
        lastLoadedTextSnapshot = new LoadedTextSnapshot(
                intent != null ? intent.getStringExtra(EXTRA_FILE_PATH) : null,
                intent != null ? intent.getStringExtra(EXTRA_FILE_URI) : null,
                filePath,
                fileName,
                fileContent,
                totalChars,
                totalLines,
                getCurrentCharPosition(),
                activeSearchQuery,
                activeSearchIndex,
                largeTextEstimateActive,
                largeTextEstimatedTotalPages,
                pendingLargeTextRestorePosition,
                largeTextPreviewBaseCharOffset,
                largeTextEstimatedBasePageOffset,
                largeTextEstimatedTotalChars,
                hugeTextPreviewOnly,
                pendingLargeTextCachedDisplayPage,
                pendingLargeTextCachedTotalPages,
                largeTextPartitionStartByte,
                largeTextPartitionEndByte,
                largeTextFileByteLength,
                largeTextEstimatedBytesPerChar,
                largeTextPartitionBodyCharCount,
                largeTextPartitionStartLine,
                largeTextPartitionEndLine,
                largeTextTotalLogicalLines);
    }

    private void clearLoadedTextSnapshot() {
        LoadedTextSnapshot snapshot = lastLoadedTextSnapshot;
        if (snapshot == null) return;
        if (filePath == null || filePath.equals(snapshot.filePath)) {
            lastLoadedTextSnapshot = null;
        }
    }

    private boolean restoreLoadedTextSnapshotIfAvailable(@NonNull Intent intent, Bundle savedInstanceState) {
        if (savedInstanceState == null || !savedInstanceState.getBoolean(STATE_RESTORE_FROM_MEMORY, false)) {
            return false;
        }

        LoadedTextSnapshot snapshot = lastLoadedTextSnapshot;
        if (snapshot == null || !snapshot.matches(intent)) return false;

        activityDestroyed = false;
        hideLoadingWindow();

        filePath = snapshot.filePath;
        appliedTextDisplayRuleSignature = TextDisplayRuleManager.getSignature(getApplicationContext(), filePath);
        fileName = snapshot.fileName != null ? snapshot.fileName : getString(R.string.app_name);
        fileContent = snapshot.fileContent;
        totalChars = snapshot.totalChars;
        totalLines = snapshot.totalLines;
        activeSearchQuery = snapshot.activeSearchQuery;
        activeSearchIndex = snapshot.activeSearchIndex;
        largeTextEstimateActive = snapshot.largeTextEstimateActive;
        largeTextEstimatedTotalPages = snapshot.largeTextEstimatedTotalPages;
        pendingLargeTextRestorePosition = snapshot.pendingLargeTextRestorePosition;
        largeTextPreviewBaseCharOffset = snapshot.largeTextPreviewBaseCharOffset;
        largeTextEstimatedBasePageOffset = snapshot.largeTextEstimatedBasePageOffset;
        largeTextEstimatedTotalChars = snapshot.largeTextEstimatedTotalChars;
        hugeTextPreviewOnly = snapshot.hugeTextPreviewOnly;
        pendingLargeTextCachedDisplayPage = snapshot.pendingLargeTextCachedDisplayPage;
        pendingLargeTextCachedTotalPages = snapshot.pendingLargeTextCachedTotalPages;
        largeTextPartitionStartByte = snapshot.largeTextPartitionStartByte;
        largeTextPartitionEndByte = snapshot.largeTextPartitionEndByte;
        largeTextFileByteLength = snapshot.largeTextFileByteLength;
        largeTextEstimatedBytesPerChar = snapshot.largeTextEstimatedBytesPerChar;
        largeTextPartitionBodyCharCount = snapshot.largeTextPartitionBodyCharCount;
        largeTextPartitionStartLine = snapshot.largeTextPartitionStartLine;
        largeTextPartitionEndLine = snapshot.largeTextPartitionEndLine;
        largeTextTotalLogicalLines = snapshot.largeTextTotalLogicalLines;

        updateReaderFileTitle();
        if (getSupportActionBar() != null) getSupportActionBar().setTitle(fileName);

        readerView.setTextContent(fileContent);
        applySearchHighlight();
        readerView.post(() -> {
            if (activityDestroyed) return;
            scrollToCharPosition(snapshot.charPosition);
            updatePositionLabel();
        });
        return true;
    }

    private void setupSeekBar() {
        seekBar.setMax(0);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private int pendingPage = 1;

            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (!fromUser || suppressSeekCallback || fileContent.isEmpty()) return;
                int pages = getDisplayedTotalPageCount();
                pendingPage = Math.max(1, Math.min(pages, progress + 1));
                previewToolbarSeekPage(pendingPage, pages);
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
                if (fileContent.isEmpty()) return;
                toolbarSeekTracking = true;
                pendingToolbarSeekJump = false;
                handler.removeCallbacks(pendingToolbarSeekTimeoutRunnable);
                int pages = getDisplayedTotalPageCount();
                pendingPage = Math.max(1, Math.min(pages, getDisplayedCurrentPageNumber()));
                previewToolbarSeekPage(pendingPage, pages);
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                toolbarSeekTracking = false;
                if (fileContent.isEmpty()) {
                    clearPendingToolbarSeekJump();
                    return;
                }

                int pages = getDisplayedTotalPageCount();
                pendingPage = Math.max(1, Math.min(pages, pendingPage));
                previewToolbarSeekPage(pendingPage, pages);

                boolean accepted = scrollToPageNumber(pendingPage, true, true);
                if (accepted) {
                    beginPendingToolbarSeekJump(pendingPage, pages);
                } else {
                    clearPendingToolbarSeekJump();
                    updatePositionLabel();
                }
            }
        });
    }


    private void previewToolbarSeekPage(int page, int totalPages) {
        if (seekBar == null) return;
        totalPages = Math.max(1, totalPages);
        page = Math.max(1, Math.min(totalPages, page));
        pendingToolbarSeekPage = page;
        pendingToolbarSeekTotalPages = totalPages;
        setPageLabels(page, totalPages);
        suppressSeekCallback = true;
        seekBar.setMax(Math.max(0, totalPages - 1));
        seekBar.setProgress(Math.max(0, Math.min(totalPages - 1, page - 1)));
        suppressSeekCallback = false;
    }

    private void beginPendingToolbarSeekJump(int page, int totalPages) {
        pendingToolbarSeekJump = true;
        previewToolbarSeekPage(page, totalPages);
        handler.removeCallbacks(pendingToolbarSeekTimeoutRunnable);
        handler.postDelayed(pendingToolbarSeekTimeoutRunnable, 10000L);
    }

    private void clearPendingToolbarSeekJump() {
        toolbarSeekTracking = false;
        pendingToolbarSeekJump = false;
        pendingToolbarSeekPage = -1;
        pendingToolbarSeekTotalPages = 1;
        handler.removeCallbacks(pendingToolbarSeekTimeoutRunnable);
    }

    private void clearStalePendingToolbarSeekJump() {
        if (!pendingToolbarSeekJump) return;
        pendingToolbarSeekJump = false;
        pendingToolbarSeekPage = -1;
        pendingToolbarSeekTotalPages = 1;
        updatePositionLabel();
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
            lastStatusOffExtraTopPadding = getStableStatusOffTopPaddingPx();
            if (readerPageStatus != null) {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) readerPageStatus.getLayoutParams();
                lp.height = getReaderPageStatusVisualHeight();
                readerPageStatus.setLayoutParams(lp);
                applyPageStatusAlignment(lastReaderTopInset);
            }
            updateReaderContentTopPadding();

            readerView.setLargeTextPartitionMode(largeTextEstimateActive);
            readerView.setOverlapLines(prefs.getPagingOverlapLines());

            if (appliedTopTextZoneOffsetPx != topTextZoneOffsetPx
                    || appliedBottomTextZoneOffsetPx != bottomTextZoneOffsetPx
                    || appliedLeftTextInsetPx != leftTextInsetPx
                    || appliedRightTextInsetPx != rightTextInsetPx) {
                readerView.setTextZoneAdjustments(topTextZoneOffsetPx, bottomTextZoneOffsetPx,
                        leftTextInsetPx, rightTextInsetPx);
                if (largeTextEstimateActive) {
                    resetLargeTextExactPageIndex();
                    if (filePath != null) startLargeTextExactPageIndexing(filePath);
                }
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
                if (largeTextEstimateActive) {
                    resetLargeTextExactPageIndex();
                    if (filePath != null) startLargeTextExactPageIndexing(filePath);
                }
            }
        }

        // The page-count/status strip and Android status bar follow the active reader theme.
        applyReaderSystemBarColors(bgColor, readableTextColorForBackground(bgColor));

        if (prefs.getKeepScreenOn()) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        applyStatusBarVisibilityPreference();
        applyPageStatusAlignment(lastReaderTopInset);
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
        if (readerFileTitle != null) {
            readerFileTitle.setBackgroundColor(backgroundColor);
            readerFileTitle.setTextColor(textColor);
            readerFileTitle.setTextSize(14f);
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


    private GradientDrawable loadingBoxBackground(int backgroundColor, int fgColor) {
        GradientDrawable drawable = new GradientDrawable();
        int panelColor = blendColors(backgroundColor, fgColor, isDarkColor(backgroundColor) ? 0.08f : 0.05f);
        int borderColor = blendColors(backgroundColor, fgColor, isDarkColor(backgroundColor) ? 0.24f : 0.18f);
        drawable.setColor(panelColor);
        drawable.setCornerRadius(dpToPx(24));
        drawable.setStroke(dpToPx(1), borderColor);
        return drawable;
    }

    private void updateLoadingIndicatorColors(int backgroundColor) {
        int fg = readableTextColorForBackground(backgroundColor);

        if (loadingBox != null) {
            loadingBox.setBackground(loadingBoxBackground(backgroundColor, fg));
        }

        if (progressText != null) {
            progressText.setTextColor(fg);
            progressText.setBackgroundColor(Color.TRANSPARENT);
        }

        if (progressBar != null) {
            progressBar.setBackgroundColor(Color.TRANSPARENT);
            progressBar.setIndeterminateTintList(ColorStateList.valueOf(fg));
        }
    }

    private void showLoadingWindow() {
        updateLoadingIndicatorColors(currentReaderBackgroundColor);
        if (loadingBox != null) {
            loadingBox.setVisibility(View.VISIBLE);
            loadingBox.bringToFront();
        }
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }
        if (progressText != null) {
            progressText.setText(getString(R.string.loading));
            progressText.setVisibility(View.VISIBLE);
        }
    }

    private void hideLoadingWindow() {
        loadingWindowPartitionJumpGeneration = -1;
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
        if (progressText != null) {
            progressText.setVisibility(View.GONE);
        }
        if (loadingBox != null) {
            loadingBox.setVisibility(View.GONE);
        }
    }

    private void showLoadingWindowForPartitionJump(int switchGeneration) {
        loadingWindowPartitionJumpGeneration = switchGeneration;
        showLoadingWindow();
    }

    private void hideLoadingWindowForPartitionJumpIfCurrent(boolean shouldHide, int switchGeneration) {
        if (!shouldHide) return;
        if (loadingWindowPartitionJumpGeneration == switchGeneration) {
            hideLoadingWindow();
        }
    }

    private void syncReaderDialogThemeSnapshot() {
        if (themeManager == null) {
            themeManager = ThemeManager.getInstance(this);
        }
        Theme theme = themeManager.getActiveTheme();
        if (theme != null) {
            currentReaderBackgroundColor = theme.getBackgroundColor();
            currentReaderTextColor = theme.getTextColor();
        } else {
            currentReaderTextColor = readableTextColorForBackground(currentReaderBackgroundColor);
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
        int fg = readerDialogTextColor(bg);
        int blended = blendColors(bg, fg, isDarkColor(bg) ? 0.10f : 0.08f);
        return Color.rgb(Color.red(blended), Color.green(blended), Color.blue(blended));
    }

    private int readerDialogTextColor(int bgColor) {
        syncReaderDialogThemeSnapshot();
        return currentReaderTextColor;
    }

    private int readerDialogSubTextColor(int bgColor) {
        syncReaderDialogThemeSnapshot();
        return blendColors(currentReaderBackgroundColor, currentReaderTextColor,
                isDarkColor(currentReaderBackgroundColor) ? 0.72f : 0.64f);
    }

    private int strongDialogBorderColor(int bgColor) {
        int fg = readerDialogTextColor(bgColor);

        // Match the PDF/EPUB/Word viewer dialog border tone.  The previous TXT
        // border used a much stronger foreground blend, which made the outline
        // look too heavy and separate from the document-viewer popups.
        return blendColors(bgColor, fg, isDarkColor(bgColor) ? 0.28f : 0.20f);
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
        title.setTextSize(22f);
        title.setGravity(Gravity.CENTER);
        title.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(dpToPx(18), dpToPx(14), dpToPx(18), dpToPx(12));
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
        row.setPadding(dpToPx(14), 0, dpToPx(14), 0);

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

    private TextView makeReaderCenteredActionButton(String text, int fgColor) {
        TextView row = makeReaderActionRow(text, fgColor);
        row.setGravity(Gravity.CENTER);
        row.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        row.setIncludeFontPadding(false);
        row.setPadding(0, 0, 0, 0);
        return row;
    }

    private TextView makeReaderFontDialogTitle(String text, int bgColor, int fgColor) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextColor(fgColor);
        title.setTextSize(22f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
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
        bg.setStroke(1, selected ? selectedStroke : normalStroke);
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
        panelBg.setCornerRadius(dpToPx(14));
        panelBg.setStroke(Math.max(1, dpToPx(1)), borderColor);

        GradientDrawable foregroundBorder = new GradientDrawable();
        foregroundBorder.setColor(Color.TRANSPARENT);
        foregroundBorder.setCornerRadius(dpToPx(14));
        foregroundBorder.setStroke(Math.max(1, dpToPx(1)), borderColor);

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
        final float strokePx = Math.max(1f, dpToPx(1));
        final float outerRadiusPx = dpToPx(14);

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

    private void prepareReaderAlertDialogWindowNoJump(AlertDialog dialog, boolean hideUntilLaidOut) {
        if (dialog == null || dialog.getWindow() == null) return;

        android.view.Window window = dialog.getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setGravity(Gravity.CENTER);
        window.setWindowAnimations(0);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.copyFrom(window.getAttributes());
        lp.width = Math.min(
                getResources().getDisplayMetrics().widthPixels - dpToPx(40),
                dpToPx(420));
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        lp.dimAmount = 0.16f;
        lp.x = 0;
        lp.y = 0;
        window.setAttributes(lp);
        window.setLayout(lp.width, WindowManager.LayoutParams.WRAP_CONTENT);

        if (hideUntilLaidOut) {
            window.getDecorView().setAlpha(0f);
        }
    }

    private void prepareReaderCustomThemeDialogWindowNoJump(AlertDialog dialog, boolean hideUntilLaidOut) {
        prepareReaderAlertDialogWindowNoJump(dialog, hideUntilLaidOut);
        if (dialog == null || dialog.getWindow() == null) return;

        android.view.Window window = dialog.getWindow();
        WindowManager.LayoutParams lp = window.getAttributes();

        // Custom reading-theme action/delete dialogs are short confirmation boxes.
        // Keep them visibly narrower than the full Settings-style dialogs.
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int compactWidth = Math.max(dpToPx(240), Math.round(screenWidth * 0.70f));
        lp.width = compactWidth;
        lp.y = dpToPx(44);
        window.setAttributes(lp);
        window.setLayout(compactWidth, WindowManager.LayoutParams.WRAP_CONTENT);
    }


    private GradientDrawable positionedReaderDialogBackground(int bgColor) {
        // Same outer card geometry as the PDF/EPUB/Word viewer popups: the
        // border is a thin theme-derived line, not the older heavy TXT outline.
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(bgColor);
        drawable.setCornerRadius(dpToPx(14));
        return drawable;
    }

    private Drawable positionedReaderDialogBorderOverlay(int bgColor) {
        final int borderColor = strongDialogBorderColor(bgColor);
        final float strokeWidth = Math.max(1f, dpToPx(1));
        final float radius = dpToPx(14);
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

        ScrollView adaptiveScroll = wrapAdaptiveDialogContent(content, outerFrame);

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
        int widthPx = Math.max(dpToPx(220), lpWidthForAdaptiveDialog(horizontalMarginDp, maxWidthDp, widthFraction));
        applyAdaptiveDialogMaxHeight(dialog, adaptiveScroll, widthPx);
        return dialog;
    }

    private int lpWidthForAdaptiveDialog(int horizontalMarginDp, int maxWidthDp, float widthFraction) {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int cappedWidth = Math.min(screenWidth - dpToPx(horizontalMarginDp), dpToPx(maxWidthDp));
        if (widthFraction > 0f && widthFraction < 1f) {
            cappedWidth = Math.min(Math.round(screenWidth * widthFraction), dpToPx(maxWidthDp));
        }
        return cappedWidth;
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

    private TextView makeCompactReaderDialogActionText(String label, int textColor) {
        TextView button = makeReaderDialogActionText(label, textColor, Gravity.CENTER);
        button.setTextSize(12f);
        button.setSingleLine(true);
        button.setMaxLines(1);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setIncludeFontPadding(false);
        button.setPadding(dpToPx(4), 0, dpToPx(4), 0);
        return button;
    }



    private int dialogActionPanelFillColor(int bgColor) {
        // Match EPUB/Word/PDF positioned dialogs: the action area is part of the
        // same rounded card, not a separately tinted bottom zone.
        return bgColor;
    }

    private int dialogActionPanelLineColor(int bgColor) {
        return strongDialogBorderColor(bgColor);
    }

    private Drawable actionPanelBackground(int fillColor, int lineColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(0f);
        return drawable;
    }

    private Drawable positionedActionPanelBackground(int fillColor, int lineColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        // The parent rounded dialog frame clips the lower corners. Keeping this
        // rectangular removes the visible horizontal separator/strip that TXT
        // dialogs had but the other document viewers do not.
        drawable.setCornerRadius(0f);
        return drawable;
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

        // Natural fit: no outer rectangle or horizontal separator. The action
        // buttons sit on the same card background as the rest of the TXT dialog.
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
        int borderColor = strongDialogBorderColor(bgColor);

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
                ? R.style.ThemeOverlay_TextView_ReaderDialogLight
                : R.style.ThemeOverlay_TextView_ReaderDialogDark;
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
            lastReaderTopInset = topInset;
            lastReaderBottomInset = bottomInset;

            if (navBarSpacer != null) {
                FrameLayout.LayoutParams spacerLp =
                        (FrameLayout.LayoutParams) navBarSpacer.getLayoutParams();
                spacerLp.height = bottomInset;
                spacerLp.gravity = Gravity.BOTTOM;
                navBarSpacer.setLayoutParams(spacerLp);
            }

            int readerLineHeight = getStableStatusOffTopPaddingPx();

            // Option B for TXT pagination stability: use the status-bar-OFF top
            // spacing as the canonical layout in both status-bar modes. This keeps
            // page anchors/page count stable when the user toggles the Android
            // status bar.  The page indicator itself is given that extra row of
            // visual height, so the number appears one text row lower instead of
            // stealing or returning vertical space from the paginated TXT body.
            lastStatusOffExtraTopPadding = Math.max(0, readerLineHeight);

            if (readerPageStatus != null) {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) readerPageStatus.getLayoutParams();
                lp.topMargin = 0;
                lp.height = getReaderPageStatusVisualHeight();
                readerPageStatus.setLayoutParams(lp);
                applyPageStatusAlignment(topInset);
            }
            if (readerFileTitle != null) {
                readerFileTitle.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                readerFileTitle.setIncludeFontPadding(false);
                // Keep the mask at the first-line row, but pin the title text to the
                // top of that row so it sits closer to the page indicator.
                readerFileTitle.setPadding(dpToPx(36), 0, dpToPx(36), 0);
                updateReaderFileTitleMaskBounds();
                updateReaderFileTitleVisibility();
            }

            updateReaderContentTopPadding();

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

    private void updateReaderFileTitleMaskBounds() {
        if (readerFileTitle == null || readerView == null) return;
        if (readerView.getWidth() <= 0 || readerView.getHeight() <= 0) {
            readerView.post(this::updateReaderFileTitleMaskBounds);
            return;
        }

        FrameLayout.LayoutParams titleLp = (FrameLayout.LayoutParams) readerFileTitle.getLayoutParams();

        // Keep the filename overlay in a fixed visual first-row slot.  Do not
        // follow getFirstVisibleLineTopInView(): on the final page, readerScrollY
        // can be clamped to maxScrollY and the actual first visible line shifts,
        // which made the title jump upward only on the last page.
        int pageStatusBottom = getReaderPageStatusVisualHeight();
        int rowTop = readerView.getStableFirstRowTopInView();
        int rowBottom = readerView.getStableFirstRowBottomInView();
        int top = Math.max(pageStatusBottom, rowTop);
        int bottom = Math.max(top + dpToPx(24), rowBottom + dpToPx(2));
        bottom = Math.min(readerView.getHeight(), bottom);

        titleLp.topMargin = top;
        titleLp.height = Math.max(dpToPx(24), bottom - top);
        readerFileTitle.setLayoutParams(titleLp);
    }

    private boolean shouldShowReaderFileTitle() {
        if (readerFileTitle == null) return false;
        boolean hasTitle = readerFileTitle.getText() != null
                && readerFileTitle.getText().toString().trim().length() > 0;
        return toolbarVisible && hasTitle;
    }

    private void updateReaderContentTopPadding() {
        if (readerView == null) return;

        // The title is an overlay strip in the existing top gap.
        // TXT pagination intentionally uses the same top padding whether the
        // Android status bar is visible or hidden. The page indicator is moved
        // one text row lower visually, but the paginated TXT body always starts
        // from this canonical status-bar-OFF spacing.
        readerView.setPadding(
                readerView.getPaddingLeft(),
                getReaderContentTopPadding(),
                readerView.getPaddingRight(),
                lastReaderBottomInset + dpToPx(12));
    }

    private int getStableStatusOffTopPaddingPx() {
        if (prefs == null) return 0;
        return Math.max(0, Math.round(
                prefs.getFontSize()
                        * prefs.getLineSpacing()
                        * getResources().getDisplayMetrics().scaledDensity));
    }

    private int getReaderPageStatusBaseHeight() {
        return dpToPx(28);
    }

    private int getReaderPageStatusVisualHeight() {
        return getReaderPageStatusBaseHeight() + Math.max(0, lastStatusOffExtraTopPadding);
    }

    private int getReaderContentTopPadding() {
        return getReaderPageStatusVisualHeight() + dpToPx(8);
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
        // Keep vertical placement independent from the Android status-bar inset.
        // The TextView is already one reader-row taller, so bottom gravity moves
        // the page indicator down while preserving stable TXT pagination.
        readerPageStatus.setPadding(startPadding, 0, endPadding, dpToPx(1));
    }

    private void updateReaderFileTitle() {
        if (readerFileTitle == null) return;
        String title = fileName;
        if ((title == null || title.trim().isEmpty()) && filePath != null) {
            title = new File(filePath).getName();
        }
        readerFileTitle.setText(title != null ? title : "");
        updateReaderFileTitleVisibility();
    }

    private void updateReaderFileTitleVisibility() {
        if (readerFileTitle == null) return;
        boolean showTitle = shouldShowReaderFileTitle();
        if (showTitle) updateReaderFileTitleMaskBounds();
        readerFileTitle.setVisibility(showTitle ? View.VISIBLE : View.GONE);
        updateReaderContentTopPadding();
        if (showTitle) {
            readerFileTitle.bringToFront();
            if (readerPageStatus != null) readerPageStatus.bringToFront();
            if (bottomBar != null) bottomBar.bringToFront();
        }
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
        if (bottomBar != null) {
            // E-ink devices can have slower touch dispatch and occasional parent-intercept
            // timing issues. Keep the whole toolbar in the touch layer and make every
            // action consume its own tap from ACTION_DOWN through ACTION_UP.
            bottomBar.setClickable(true);
            bottomBar.setFocusable(false);
            bottomBar.bringToFront();
        }

        setupBottomToolbarButton(R.id.btn_open_file, this::showTextSearch);
        setupBottomToolbarButton(R.id.btn_page_move, this::showPageMoveBubble);
        setupBottomToolbarButton(R.id.btn_bookmark, this::showBookmarksForFile);
        setupBottomToolbarButton(R.id.btn_auto_page, this::showAutoPageTurnDialog);
        setupBottomToolbarButton(R.id.btn_settings, () -> {
            Intent settingsIntent = new Intent(this, SettingsActivity.class);
            if (filePath != null) settingsIntent.putExtra("txt_file_path", filePath);
            startActivity(settingsIntent);
        });
        setupBottomToolbarButton(R.id.btn_more, this::showMoreDialog);
    }

    private void setupBottomToolbarButton(int viewId, Runnable action) {
        View button = findViewById(viewId);
        if (button == null) return;

        button.setClickable(true);
        button.setFocusable(true);
        button.setLongClickable(false);
        button.setHapticFeedbackEnabled(false);
        button.setOnClickListener(v -> {
            if (action != null) action.run();
        });
        button.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (v.getParent() != null) {
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    if (bottomBar != null) bottomBar.bringToFront();
                    // Keep the e-ink tap-consumption fallback, but do not enter the
                    // pressed state. This removes the toolbar hold/ripple animation.
                    return true;

                case MotionEvent.ACTION_UP:
                    if (v.getParent() != null) {
                        v.getParent().requestDisallowInterceptTouchEvent(false);
                    }
                    if (isTouchInsideView(v, event)) {
                        v.performClick();
                    }
                    return true;

                case MotionEvent.ACTION_CANCEL:
                    if (v.getParent() != null) {
                        v.getParent().requestDisallowInterceptTouchEvent(false);
                    }
                    return true;

                default:
                    return true;
            }
        });
    }

    private boolean isTouchInsideView(View view, MotionEvent event) {
        int slop = dpToPx(10);
        return event.getX() >= -slop
                && event.getX() <= view.getWidth() + slop
                && event.getY() >= -slop
                && event.getY() <= view.getHeight() + slop;
    }

    private void showPageMoveBubble() {
        if (fileContent == null || fileContent.isEmpty()) {
            Toast.makeText(this, getString(R.string.file_not_loaded), Toast.LENGTH_SHORT).show();
            return;
        }

        final int bubbleBg = readerDialogBgColor();
        final int bubbleFg = readerDialogTextColor(bubbleBg);
        final int bubbleSub = readerDialogSubTextColor(bubbleBg);
        int totalPages = getDisplayedTotalPageCount();
        int currentPage = getDisplayedCurrentPageNumber();

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

        TextView closeButton = makeReaderDialogActionText(getString(R.string.go), bubbleFg,
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
                TXT_TOOLBAR_POPUP_Y_DP,
                0.85f,
                460,
                true);

        final int[] pendingPage = new int[]{currentPage};
        dialogSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (!fromUser) return;
                int pages = getDisplayedTotalPageCount();
                int page = Math.max(1, Math.min(pages, progress + 1));
                pendingPage[0] = page;
                label.setText(formatPageMoveLabel(page, pages));
                pageInput.setText(String.valueOf(page));
                pageInput.setSelection(pageInput.getText().length());
            }

            @Override public void onStartTrackingTouch(SeekBar sb) {}

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                int pages = getDisplayedTotalPageCount();
                int page = Math.max(1, Math.min(pages, pendingPage[0]));
                previewToolbarSeekPage(page, pages);
                boolean accepted = scrollToPageNumber(page, true, true);
                if (accepted) {
                    beginPendingToolbarSeekJump(page, pages);
                } else {
                    clearPendingToolbarSeekJump();
                    updatePositionLabel();
                }
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
                if (page < 1 || page > getDisplayedTotalPageCount()) {
                    Toast.makeText(this,
                            getString(R.string.page_range_error, getDisplayedTotalPageCount()),
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                int pages = getDisplayedTotalPageCount();
                page = Math.max(1, Math.min(pages, page));
                previewToolbarSeekPage(page, pages);
                boolean accepted = scrollToPageNumber(page, true, true);
                if (accepted) {
                    beginPendingToolbarSeekJump(page, pages);
                } else {
                    clearPendingToolbarSeekJump();
                    updatePositionLabel();
                }
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
        addMoreActionRow(list, getString(R.string.reset_font_size), fg, panel, this::resetFontSize, ref);
        addMoreActionRow(list, getString(R.string.txt_display_rule_quick_add), fg, panel, () -> showQuickTextDisplayRuleDialog("", true), ref);
        addMoreActionRow(list, getString(R.string.txt_display_rule_manage), fg, panel, this::showReaderTextDisplayRulesManagerDialog, ref);
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
        // Match the PDF/EPUB/Word More dialog bottom action spacing: the
        // document viewers have box padding before the button text padding.
        // Use the same effective proportion instead of pushing labels to the edge.
        actionRow.setPadding(dpToPx(14), 0, dpToPx(14), 0);

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
                TXT_TOOLBAR_POPUP_Y_DP,
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

    private void openTextDisplayRuleSettings() {
        Intent settingsIntent = new Intent(this, SettingsActivity.class);
        if (filePath != null) settingsIntent.putExtra("txt_file_path", filePath);
        startActivity(settingsIntent);
    }

    private void showQuickTextDisplayRuleDialog(String prefillFind, boolean defaultCurrentFileOnly) {
        showReaderTextDisplayRuleEditDialog(prefillFind, defaultCurrentFileOnly, -1);
    }

    private void showReaderTextDisplayRuleEditDialog(String prefillFind, boolean defaultCurrentFileOnly, int editIndex) {
        showReaderTextDisplayRuleEditDialog(prefillFind, defaultCurrentFileOnly, editIndex, null);
    }

    private void showReaderTextDisplayRuleEditDialog(String prefillFind, boolean defaultCurrentFileOnly, int editIndex, @Nullable Runnable onSaved) {
        syncReaderDialogThemeSnapshot();
        final int bg = readerDialogBgColor();
        final int fg = readerDialogTextColor(bg);
        final int sub = readerDialogSubTextColor(bg);
        ArrayList<TextDisplayRule> rules = new ArrayList<>(TextDisplayRuleManager.getRules(getApplicationContext()));
        TextDisplayRule editingRule = editIndex >= 0 && editIndex < rules.size() ? rules.get(editIndex) : null;
        boolean editingExistingRule = editingRule != null;

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Color.TRANSPARENT);
        panel.setClipChildren(true);
        panel.setClipToPadding(true);

        TextView title = makeReaderDialogTitle(
                editingExistingRule ? getString(R.string.edit) : getString(R.string.txt_display_rule_quick_add),
                bg,
                fg);
        panel.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dpToPx(16);
        box.setPadding(pad, dpToPx(2), pad, dpToPx(10));
        box.setBackgroundColor(Color.TRANSPARENT);

        TextView tip = new TextView(this);
        tip.setText(R.string.txt_display_rule_quick_tip);
        tip.setTextSize(12f);
        tip.setTextColor(sub);
        tip.setLineSpacing(0, 1.08f);
        tip.setPadding(0, 0, 0, dpToPx(8));
        box.addView(tip, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        EditText findInput = makeReaderDialogEditText(getString(R.string.txt_display_rule_find_hint), bg, fg, sub);
        findInput.setSingleLine(true);
        findInput.setTextSize(14f);
        findInput.setPadding(dpToPx(12), 0, dpToPx(12), 0);
        findInput.setText(editingExistingRule ? editingRule.findText : (prefillFind != null ? prefillFind : ""));
        findInput.setSelectAllOnFocus(true);
        box.addView(findInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(46)));

        EditText replaceInput = makeReaderDialogEditText(getString(R.string.txt_display_rule_replace_hint), bg, fg, sub);
        replaceInput.setSingleLine(true);
        replaceInput.setTextSize(14f);
        replaceInput.setPadding(dpToPx(12), 0, dpToPx(12), 0);
        if (editingExistingRule) replaceInput.setText(editingRule.replacementText);
        LinearLayout.LayoutParams replaceLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(46));
        replaceLp.setMargins(0, dpToPx(6), 0, 0);
        box.addView(replaceInput, replaceLp);

        CompoundButton enabledBox = new SwitchCompat(this);
        enabledBox.setText(R.string.enabled);
        enabledBox.setChecked(editingExistingRule ? editingRule.enabled : true);
        styleQuickTextDisplayRuleOption(enabledBox, fg);
        box.addView((View) enabledBox);

        CompoundButton caseBox = new SwitchCompat(this);
        caseBox.setText(R.string.txt_display_rule_case_sensitive);
        caseBox.setChecked(editingExistingRule && editingRule.caseSensitive);
        styleQuickTextDisplayRuleOption(caseBox, fg);
        box.addView((View) caseBox);

        CompoundButton regexBox = new SwitchCompat(this);
        regexBox.setText(R.string.txt_display_rule_use_regex);
        regexBox.setChecked(editingExistingRule && editingRule.useRegex);
        styleQuickTextDisplayRuleOption(regexBox, fg);
        box.addView((View) regexBox);

        CompoundButton fileOnlyBox = new SwitchCompat(this);
        fileOnlyBox.setText(R.string.txt_display_rule_current_file_only);
        fileOnlyBox.setChecked(editingExistingRule
                ? TextDisplayRule.SCOPE_FILE.equals(editingRule.scope)
                : (defaultCurrentFileOnly && filePath != null && !filePath.isEmpty()));
        fileOnlyBox.setEnabled(filePath != null && !filePath.isEmpty());
        styleQuickTextDisplayRuleOption(fileOnlyBox, fg);
        box.addView((View) fileOnlyBox);

        TextView manageButton = makeReaderCenteredActionButton(getString(R.string.txt_display_rule_manage), fg);
        LinearLayout.LayoutParams manageLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(48));
        manageLp.setMargins(0, dpToPx(10), 0, 0);
        box.addView(manageButton, manageLp);

        panel.addView(box, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        actionRow.setBackground(positionedActionPanelBackground(
                dialogActionPanelFillColor(bg),
                dialogActionPanelLineColor(bg)));
        actionRow.setPadding(dpToPx(8), 0, dpToPx(8), 0);

        TextView cancelButton = makeReaderDialogActionText(getString(R.string.cancel), sub, Gravity.CENTER);
        TextView saveButton = makeReaderDialogActionText(getString(R.string.save), fg, Gravity.CENTER);
        actionRow.addView(cancelButton, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        actionRow.addView(saveButton, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        panel.addView(actionRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(54)));

        android.app.Dialog dialog = createNarrowPositionedReaderDialog(
                panel,
                bg,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL,
                TXT_TOOLBAR_POPUP_Y_DP,
                0.80f,
                420,
                true);

        manageButton.setOnClickListener(v -> {
            dialog.dismiss();
            showReaderTextDisplayRulesManagerDialog();
        });
        cancelButton.setOnClickListener(v -> dialog.dismiss());
        saveButton.setOnClickListener(v -> {
            String find = findInput.getText() != null ? findInput.getText().toString() : "";
            if (find.trim().isEmpty()) {
                Toast.makeText(this, R.string.txt_display_rule_find_required, Toast.LENGTH_SHORT).show();
                return;
            }
            String beforeSignature = currentTextDisplayRuleSignature();
            boolean oldEnabled = editingExistingRule && editingRule.enabled;
            String oldFindText = editingExistingRule ? editingRule.findText : "";
            String oldReplacementText = editingExistingRule ? editingRule.replacementText : "";
            boolean oldCaseSensitive = editingExistingRule && editingRule.caseSensitive;
            boolean oldUseRegex = editingExistingRule && editingRule.useRegex;
            String oldScope = editingExistingRule ? editingRule.scope : TextDisplayRule.SCOPE_ALL_TXT;
            String oldFilePath = editingExistingRule ? (editingRule.filePath != null ? editingRule.filePath : "") : "";
            boolean oldAppliesToCurrentFile = editingExistingRule && editingRule.appliesTo(filePath);

            TextDisplayRule rule = editingExistingRule ? editingRule : new TextDisplayRule();
            rule.enabled = enabledBox.isChecked();
            rule.findText = find;
            rule.replacementText = replaceInput.getText() != null ? replaceInput.getText().toString() : "";
            rule.caseSensitive = caseBox.isChecked();
            rule.useRegex = regexBox.isChecked();
            if ((rule.sourceFilePath == null || rule.sourceFilePath.isEmpty()) && filePath != null && !filePath.isEmpty()) {
                rule.sourceFilePath = filePath;
            }
            if (fileOnlyBox.isChecked() && filePath != null && !filePath.isEmpty()) {
                rule.scope = TextDisplayRule.SCOPE_FILE;
                if (editingExistingRule && TextDisplayRule.SCOPE_FILE.equals(oldScope) && !oldFilePath.isEmpty()) {
                    // Editing a rule that was made for another TXT file must keep that original
                    // file binding. Otherwise merely opening the rule editor from this viewer
                    // would silently retarget the rule to the currently opened file and cause
                    // an unnecessary TXT reload.
                    rule.filePath = oldFilePath;
                } else {
                    rule.filePath = filePath;
                }
            } else {
                rule.scope = TextDisplayRule.SCOPE_ALL_TXT;
                rule.filePath = "";
            }

            boolean newAppliesToCurrentFile = rule.appliesTo(filePath);
            boolean enabledChanged = editingExistingRule && oldEnabled != rule.enabled;
            boolean textOrModeChanged = editingExistingRule
                    && (!sameTextDisplayRuleValue(oldFindText, rule.findText)
                    || !sameTextDisplayRuleValue(oldReplacementText, rule.replacementText)
                    || oldCaseSensitive != rule.caseSensitive
                    || oldUseRegex != rule.useRegex);
            boolean shouldRefreshTxtContent = editingExistingRule
                    ? ((enabledChanged || textOrModeChanged) && (oldAppliesToCurrentFile || newAppliesToCurrentFile))
                    : newAppliesToCurrentFile;

            if (editingExistingRule && editIndex >= 0 && editIndex < rules.size()) {
                rules.set(editIndex, rule);
            } else {
                rules.add(rule);
            }
            TextDisplayRuleManager.saveRules(getApplicationContext(), rules);
            String afterSignature = currentTextDisplayRuleSignature();
            dialog.dismiss();
            if (onSaved != null) handler.post(onSaved);
            Toast.makeText(this, editingExistingRule ? R.string.txt_display_rule_saved : R.string.txt_display_rule_added, Toast.LENGTH_SHORT).show();
            if (!beforeSignature.equals(afterSignature)) {
                if (shouldRefreshTxtContent) {
                    requestTextDisplayRuleContentRefreshOnWindowClose();
                    if (onSaved == null) {
                        // Direct quick-add/edit dialog: apply after this dialog has closed.
                        // When opened from the rule manager, defer until the manager closes.
                        handler.post(this::applyPendingTextDisplayRuleWindowRefresh);
                    }
                } else {
                    // Scope-only changes such as All files <-> Current file only should update
                    // the rule list immediately, but they should not reload the opened TXT.
                    acknowledgeTextDisplayRuleWindowNoContentChange();
                }
            }
        });
        dialog.show();
    }

    private void styleQuickTextDisplayRuleOption(CompoundButton option, int textColor) {
        option.setTextColor(textColor);
        option.setTextSize(14f);
        option.setGravity(Gravity.CENTER_VERTICAL);
        option.setMinHeight(dpToPx(38));
        option.setPadding(dpToPx(12), 0, dpToPx(8), 0);
        option.setCompoundDrawablePadding(dpToPx(8));
    }

    private void showReaderTextDisplayRulesManagerDialog() {
        syncReaderDialogThemeSnapshot();
        final int bg = readerDialogBgColor();
        final int fg = readerDialogTextColor(bg);
        final int sub = readerDialogSubTextColor(bg);
        final int outline = blendColors(bg, fg, isLightColor(bg) ? 0.12f : 0.18f);
        final ArrayList<TextDisplayRule> rules = new ArrayList<>(TextDisplayRuleManager.getRules(getApplicationContext()));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Color.TRANSPARENT);
        panel.setClipChildren(true);
        panel.setClipToPadding(true);

        TextView title = makeReaderDialogTitle(getString(R.string.txt_display_rules), bg, fg);
        panel.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dpToPx(14), dpToPx(4), dpToPx(14), dpToPx(8));
        body.setBackgroundColor(Color.TRANSPARENT);

        TextView guide = new TextView(this);
        guide.setText(R.string.txt_display_rules_dialog_description);
        guide.setTextColor(sub);
        guide.setTextSize(12f);
        guide.setGravity(Gravity.CENTER);
        guide.setLineSpacing(0, 1.08f);
        guide.setPadding(0, 0, 0, dpToPx(8));
        body.addView(guide, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        body.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.max(dpToPx(150), Math.min(dpToPx(300), currentVisibleWindowHeightPx() - dpToPx(320)))));

        final Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            list.removeAllViews();
            if (rules.isEmpty()) {
                TextView empty = new TextView(this);
                empty.setText(R.string.txt_display_rules_empty);
                empty.setTextColor(sub);
                empty.setTextSize(14f);
                empty.setGravity(Gravity.CENTER);
                empty.setPadding(0, dpToPx(20), 0, dpToPx(20));
                list.addView(empty, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
                return;
            }
            for (int i = 0; i < rules.size(); i++) {
                TextDisplayRule rule = rules.get(i);
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8));
                GradientDrawable rowBg = new GradientDrawable();
                rowBg.setColor(blendColors(bg, fg, isLightColor(bg) ? 0.025f : 0.04f));
                rowBg.setStroke(dpToPx(1), outline);
                rowBg.setCornerRadius(dpToPx(12));
                row.setBackground(rowBg);

                TextView name = new TextView(this);
                name.setText((rule.enabled ? "✓ " : "○ ")
                        + safeRulePreview(rule.findText) + " → " + safeRulePreview(rule.replacementText));
                name.setTextColor(fg);
                name.setTextSize(14f);
                name.setTypeface(Typeface.DEFAULT_BOLD);
                name.setSingleLine(true);
                name.setEllipsize(TextUtils.TruncateAt.END);
                row.addView(name, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));

                String scope = TextDisplayRule.SCOPE_FILE.equals(rule.scope)
                        ? getString(R.string.txt_display_rule_scope_current_file)
                        : getString(R.string.txt_display_rule_scope_all_txt);
                String caseMode = rule.caseSensitive
                        ? getString(R.string.txt_display_rule_case_sensitive)
                        : getString(R.string.txt_display_rule_case_insensitive);
                String mode = rule.useRegex
                        ? getString(R.string.txt_display_rule_regex_mode)
                        : getString(R.string.txt_display_rule_plain_mode);
                TextView meta = new TextView(this);
                meta.setText(scope + " · " + caseMode + " · " + mode);
                meta.setTextColor(sub);
                meta.setTextSize(11f);
                meta.setSingleLine(true);
                meta.setEllipsize(TextUtils.TruncateAt.END);
                meta.setPadding(0, dpToPx(3), 0, 0);
                row.addView(meta, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));

                String sourceFileLabel = makeTextDisplayRuleSourceFileLabel(rule);
                if (!sourceFileLabel.isEmpty()) {
                    TextView source = new TextView(this);
                    source.setText(sourceFileLabel);
                    source.setTextColor(sub);
                    source.setTextSize(11f);
                    source.setSingleLine(true);
                    source.setEllipsize(TextUtils.TruncateAt.END);
                    source.setPadding(0, dpToPx(2), 0, 0);
                    row.addView(source, new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT));
                }

                LinearLayout controls = new LinearLayout(this);
                controls.setOrientation(LinearLayout.HORIZONTAL);
                controls.setGravity(Gravity.CENTER_VERTICAL);
                controls.setPadding(0, dpToPx(6), 0, 0);
                TextView up = makeReaderMiniTextButton(getString(R.string.move_up), fg, outline, bg);
                TextView down = makeReaderMiniTextButton(getString(R.string.move_down), fg, outline, bg);
                TextView toggle = makeReaderMiniTextButton(rule.enabled ? getString(R.string.disable) : getString(R.string.enable), fg, outline, bg);
                TextView delete = makeReaderMiniTextButton(getString(R.string.delete), fg, outline, bg);
                controls.addView(up, new LinearLayout.LayoutParams(0, dpToPx(32), 1f));
                controls.addView(down, new LinearLayout.LayoutParams(0, dpToPx(32), 1f));
                controls.addView(toggle, new LinearLayout.LayoutParams(0, dpToPx(32), 1f));
                controls.addView(delete, new LinearLayout.LayoutParams(0, dpToPx(32), 1f));
                row.addView(controls, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));

                final int index = i;
                up.setOnClickListener(v -> {
                    if (index <= 0) return;
                    TextDisplayRule moved = rules.remove(index);
                    rules.add(index - 1, moved);
                    TextDisplayRuleManager.saveRules(getApplicationContext(), rules);
                    refresh[0].run();
                    // Reordering only changes display priority in the manager. Do not reload TXT.
                    acknowledgeTextDisplayRuleWindowNoContentChange();
                });
                down.setOnClickListener(v -> {
                    if (index >= rules.size() - 1) return;
                    TextDisplayRule moved = rules.remove(index);
                    rules.add(index + 1, moved);
                    TextDisplayRuleManager.saveRules(getApplicationContext(), rules);
                    refresh[0].run();
                    // Reordering only changes display priority in the manager. Do not reload TXT.
                    acknowledgeTextDisplayRuleWindowNoContentChange();
                });
                toggle.setOnClickListener(v -> {
                    String beforeSignature = currentTextDisplayRuleSignature();
                    rule.enabled = !rule.enabled;
                    TextDisplayRuleManager.saveRules(getApplicationContext(), rules);
                    refresh[0].run();
                    if (!beforeSignature.equals(currentTextDisplayRuleSignature())) {
                        requestTextDisplayRuleContentRefreshOnWindowClose();
                    } else {
                        acknowledgeTextDisplayRuleWindowNoContentChange();
                    }
                });
                delete.setOnClickListener(v -> showReaderDeleteTextDisplayRuleConfirmDialog(rule, () -> {
                    if (index < 0 || index >= rules.size()) return;
                    String beforeSignature = currentTextDisplayRuleSignature();
                    rules.remove(index);
                    TextDisplayRuleManager.saveRules(getApplicationContext(), rules);
                    refresh[0].run();
                    if (!beforeSignature.equals(currentTextDisplayRuleSignature())) {
                        requestTextDisplayRuleContentRefreshOnWindowClose();
                    } else {
                        acknowledgeTextDisplayRuleWindowNoContentChange();
                    }
                }));
                row.setOnLongClickListener(v -> {
                    showReaderTextDisplayRuleEditDialog("", true, index, () -> {
                        rules.clear();
                        rules.addAll(TextDisplayRuleManager.getRules(getApplicationContext()));
                        refresh[0].run();
                    });
                    return true;
                });

                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                rowLp.setMargins(0, 0, 0, dpToPx(8));
                list.addView(row, rowLp);
            }
        };
        refresh[0].run();

        panel.addView(body, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        actionRow.setBackground(positionedActionPanelBackground(
                dialogActionPanelFillColor(bg),
                dialogActionPanelLineColor(bg)));
        actionRow.setPadding(dpToPx(8), 0, dpToPx(8), 0);
        TextView addButton = makeReaderDialogActionText(getString(R.string.add), fg, Gravity.CENTER);
        TextView closeButton = makeReaderDialogActionText(getString(R.string.close), sub, Gravity.CENTER);
        actionRow.addView(addButton, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        actionRow.addView(closeButton, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        panel.addView(actionRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(54)));

        android.app.Dialog dialog = createNarrowPositionedReaderDialog(
                panel,
                bg,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL,
                TXT_TOOLBAR_POPUP_Y_DP,
                0.80f,
                420,
                true);
        addButton.setOnClickListener(v -> showReaderTextDisplayRuleEditDialog("", true, -1, () -> {
            rules.clear();
            rules.addAll(TextDisplayRuleManager.getRules(getApplicationContext()));
            refresh[0].run();
        }));
        closeButton.setOnClickListener(v -> dialog.dismiss());
        dialog.setOnDismissListener(d -> applyPendingTextDisplayRuleWindowRefresh());
        dialog.show();
    }

    private void showReaderDeleteTextDisplayRuleConfirmDialog(@NonNull TextDisplayRule rule, @NonNull Runnable onDelete) {
        syncReaderDialogThemeSnapshot();
        final int bg = readerDialogBgColor();
        final int fg = readerDialogTextColor(bg);
        final int sub = readerDialogSubTextColor(bg);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Color.TRANSPARENT);
        panel.setClipChildren(true);
        panel.setClipToPadding(true);

        TextView title = makeReaderDialogTitle(getString(R.string.delete), bg, fg);
        panel.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dpToPx(14), dpToPx(6), dpToPx(14), dpToPx(10));
        body.setBackgroundColor(Color.TRANSPARENT);

        TextView message = new TextView(this);
        String preview = safeRulePreview(rule.findText) + " → " + safeRulePreview(rule.replacementText);
        message.setText(getString(R.string.txt_display_rule_delete_confirm, preview));
        message.setTextColor(sub);
        message.setTextSize(13f);
        message.setGravity(Gravity.CENTER);
        message.setLineSpacing(0, 1.08f);
        body.addView(message, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        panel.addView(body, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        actionRow.setBackground(positionedActionPanelBackground(
                dialogActionPanelFillColor(bg),
                dialogActionPanelLineColor(bg)));
        actionRow.setPadding(dpToPx(8), 0, dpToPx(8), 0);

        TextView cancelButton = makeReaderDialogActionText(getString(R.string.cancel), sub, Gravity.CENTER);
        TextView deleteButton = makeReaderDialogActionText(getString(R.string.delete), fg, Gravity.CENTER);
        actionRow.addView(cancelButton, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        actionRow.addView(deleteButton, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        panel.addView(actionRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(54)));

        android.app.Dialog dialog = createNarrowPositionedReaderDialog(
                panel,
                bg,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL,
                TXT_TOOLBAR_POPUP_Y_DP + 104,
                0.72f,
                340,
                true);
        cancelButton.setOnClickListener(v -> dialog.dismiss());
        deleteButton.setOnClickListener(v -> {
            dialog.dismiss();
            onDelete.run();
        });
        dialog.show();
    }

    private TextView makeReaderMiniTextButton(String label, int fg, int outline, int bg) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextColor(fg);
        button.setTextSize(11f);
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setPadding(dpToPx(3), 0, dpToPx(3), 0);
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(blendColors(bg, fg, isLightColor(bg) ? 0.02f : 0.035f));
        drawable.setStroke(dpToPx(1), outline);
        drawable.setCornerRadius(dpToPx(9));
        button.setBackground(drawable);
        return button;
    }

    private String makeTextDisplayRuleSourceFileLabel(@NonNull TextDisplayRule rule) {
        String sourcePath = rule.sourceFilePath;
        if ((sourcePath == null || sourcePath.isEmpty()) && rule.filePath != null && !rule.filePath.isEmpty()) {
            sourcePath = rule.filePath;
        }
        if (sourcePath == null || sourcePath.isEmpty()) return "";
        String fileName = new File(sourcePath).getName();
        if (fileName == null || fileName.isEmpty()) fileName = sourcePath;
        return getString(R.string.txt_display_rule_source_file, fileName);
    }

    private String safeRulePreview(String value) {
        if (value == null) return "";
        String oneLine = value.replace('\n', ' ').replace('\r', ' ');
        return oneLine.length() > 24 ? oneLine.substring(0, 24) + "…" : oneLine;
    }

    private void showAutoPageTurnDialog() {
        syncReaderDialogThemeSnapshot();
        final int bg = readerDialogBgColor();
        final int fg = readerDialogTextColor(bg);
        final int sub = readerDialogSubTextColor(bg);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Color.TRANSPARENT);
        panel.setClipChildren(true);
        panel.setClipToPadding(true);

        TextView title = makeReaderDialogTitle(getString(R.string.auto_page_turn), bg, fg);
        panel.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dpToPx(18);
        box.setPadding(pad, dpToPx(4), pad, dpToPx(12));
        box.setBackgroundColor(Color.TRANSPARENT);

        TextView desc = new TextView(this);
        desc.setText(R.string.auto_page_turn_description);
        desc.setTextColor(sub);
        desc.setTextSize(13f);
        desc.setLineSpacing(0, 1.08f);
        desc.setPadding(0, 0, 0, dpToPx(10));
        box.addView(desc, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        EditText secondsInput = makeReaderDialogEditText(getString(R.string.auto_page_turn_interval_hint), bg, fg, sub);
        secondsInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        secondsInput.setSingleLine(true);
        secondsInput.setGravity(Gravity.CENTER);
        secondsInput.setText(String.valueOf(prefs != null ? prefs.getAutoPageTurnIntervalSeconds() : 8));
        secondsInput.setSelectAllOnFocus(true);

        LinearLayout intervalRow = new LinearLayout(this);
        intervalRow.setOrientation(LinearLayout.HORIZONTAL);
        intervalRow.setGravity(Gravity.CENTER);
        intervalRow.setPadding(0, 0, 0, 0);
        intervalRow.addView(new Space(this), new LinearLayout.LayoutParams(0, 1, 0.30f));
        intervalRow.addView(secondsInput, new LinearLayout.LayoutParams(0, dpToPx(52), 0.40f));
        intervalRow.addView(new Space(this), new LinearLayout.LayoutParams(0, 1, 0.30f));
        box.addView(intervalRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(52)));

        panel.addView(box, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        actionRow.setBackground(positionedActionPanelBackground(
                dialogActionPanelFillColor(bg),
                dialogActionPanelLineColor(bg)));
        actionRow.setPadding(dpToPx(8), 0, dpToPx(8), 0);

        TextView stopButton = makeReaderDialogActionText(getString(R.string.auto_page_turn_stop), sub, Gravity.CENTER);
        TextView cancelButton = makeReaderDialogActionText(getString(R.string.cancel), sub, Gravity.CENTER);
        TextView startButton = makeReaderDialogActionText(getString(R.string.auto_page_turn_start), fg, Gravity.CENTER);
        actionRow.addView(stopButton, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        actionRow.addView(cancelButton, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        actionRow.addView(startButton, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        panel.addView(actionRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(54)));

        android.app.Dialog dialog = createNarrowPositionedReaderDialog(
                panel,
                bg,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL,
                TXT_TOOLBAR_POPUP_Y_DP,
                0.70f,
                420,
                true);

        startButton.setOnClickListener(v -> {
            int seconds = parseAutoPageTurnSeconds(secondsInput.getText() != null ? secondsInput.getText().toString() : "");
            if (prefs != null) prefs.setAutoPageTurnIntervalSeconds(seconds);
            startAutoPageTurn();
            dialog.dismiss();
        });
        stopButton.setOnClickListener(v -> {
            stopAutoPageTurn(true);
            dialog.dismiss();
        });
        cancelButton.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private int parseAutoPageTurnSeconds(String raw) {
        try {
            return Math.max(2, Math.min(120, Integer.parseInt(raw.trim())));
        } catch (Exception ignored) {
            return prefs != null ? prefs.getAutoPageTurnIntervalSeconds() : 8;
        }
    }

    private void startAutoPageTurn() {
        autoPageTurnRunning = true;
        handler.removeCallbacks(autoPageTurnRunnable);
        Toast.makeText(this, R.string.auto_page_turn_started, Toast.LENGTH_SHORT).show();
        scheduleNextAutoPageTurn();
    }

    private void stopAutoPageTurn(boolean showToast) {
        if (!autoPageTurnRunning && !showToast) return;
        autoPageTurnRunning = false;
        handler.removeCallbacks(autoPageTurnRunnable);
        if (showToast) Toast.makeText(this, R.string.auto_page_turn_stopped, Toast.LENGTH_SHORT).show();
    }

    private void stopAutoPageTurnForManualNavigation() {
        if (autoPageTurnRunning) {
            stopAutoPageTurn(true);
        }
    }

    private void scheduleNextAutoPageTurn() {
        if (!autoPageTurnRunning) return;
        int seconds = prefs != null ? prefs.getAutoPageTurnIntervalSeconds() : 8;
        handler.removeCallbacks(autoPageTurnRunnable);
        handler.postDelayed(autoPageTurnRunnable, Math.max(2, seconds) * 1000L);
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
        row.setGravity(Gravity.CENTER);
        row.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        row.setPadding(0, 0, 0, 0);
        row.setOnClickListener(v -> {
            if (dialogRef[0] != null) dialogRef[0].dismiss();
            action.run();
        });
        list.addView(row);
    }

    private void loadFileFromIntent(@NonNull Intent sourceIntent) {
        clearPendingToolbarSeekJump();
        String path = sourceIntent.getStringExtra(EXTRA_FILE_PATH);
        String uriStr = sourceIntent.getStringExtra(EXTRA_FILE_URI);
        boolean samePathReload = path != null && filePath != null
                && new File(path).getAbsolutePath().equals(filePath);

        final int generation = loadGeneration.incrementAndGet();
        largeTextPartitionSwitchGeneration.incrementAndGet();
        loadingWindowPartitionJumpGeneration = -1;
        clearLargeTextPartitionCache();
        activityDestroyed = false;
        showLoadingWindow();

        // Clear old viewer state immediately so opening a different file does not
        // briefly keep old search/bookmark/page state around.  Keep the exact
        // large-TXT page index when this is only a same-file chunk/window reload.
        fileContent = "";
        appliedTextDisplayRuleSignature = TextDisplayRuleManager.getSignature(getApplicationContext(), path != null ? new File(path).getAbsolutePath() : filePath);
        activeSearchQuery = "";
        activeSearchIndex = -1;
        largeTextEstimateActive = false;
        largeTextEstimatedTotalPages = 0;
        clearLargeTextPartitionSwitchPending();
        clearLargeTextQueuedPageDelta();
        resetLargeTextPageDirectionTracking();
        pendingLargeTextRestorePosition = -1;
        largeTextPreviewBaseCharOffset = 0;
        largeTextEstimatedBasePageOffset = 0;
        largeTextEstimatedTotalChars = 0;
        hugeTextPreviewOnly = false;
        pendingLargeTextCachedDisplayPage = 0;
        pendingLargeTextCachedTotalPages = 0;
        largeTextPartitionStartByte = 0L;
        largeTextPartitionEndByte = 0L;
        largeTextFileByteLength = 0L;
        largeTextEstimatedBytesPerChar = 1f;
        largeTextPartitionBodyCharCount = 0;
        largeTextPartitionStartLine = 1;
        largeTextPartitionEndLine = 1;
        largeTextTotalLogicalLines = 1;
        if (!samePathReload) {
            resetLargeTextExactPageIndex();
        }
        applySearchHighlight();
        updatePositionLabel();

        int jumpPosition = sourceIntent.getIntExtra(EXTRA_JUMP_TO_POSITION, -1);
        int jumpDisplayPage = sourceIntent.getIntExtra(EXTRA_JUMP_DISPLAY_PAGE, 0);
        int jumpTotalPages = sourceIntent.getIntExtra(EXTRA_JUMP_TOTAL_PAGES, 0);
        long requestedPartitionStartByte = sourceIntent.getLongExtra(EXTRA_JUMP_PARTITION_START_BYTE, -1L);
        int requestedPartitionStartLine = sourceIntent.getIntExtra(EXTRA_JUMP_PARTITION_START_LINE, -1);
        String jumpAnchorBefore = sourceIntent.getStringExtra(EXTRA_JUMP_ANCHOR_BEFORE);
        String jumpAnchorAfter = sourceIntent.getStringExtra(EXTRA_JUMP_ANCHOR_AFTER);
        final android.content.Context appContext = getApplicationContext();

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
                    loadedFileName = FileUtils.getFileNameFromUri(appContext, uri);
                    File localFile = FileUtils.copyUriToLocal(appContext, uri,
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
                    LargeTextLinePartitionResult partition = requestedPartitionStartLine > 0
                            ? readLargeTextLinePartitionAtStartLine(fileToRead, requestedPartitionStartLine)
                            : readLargeTextLinePartitionForChar(fileToRead, initialRestorePosition);

                    String previewContent = partition.content;
                    int previewLineCount = partition.lineCount;
                    int previewByteCount = Math.max(1, previewContent.length());
                    int previewBaseCharOffset = partition.baseCharOffset;
                    int partitionBodyCharCount = partition.bodyCharCount;
                    int estimatedTotalChars = Math.max(partition.totalChars,
                            previewBaseCharOffset + previewContent.length());
                    long previewStartByte = requestedPartitionStartByte >= 0L ? requestedPartitionStartByte : 0L;
                    long partitionEndByte = fullByteLength;
                    int partitionStartLine = partition.startLine;
                    int partitionEndLine = partition.endLine;
                    int partitionTotalLines = partition.totalLines;
                    boolean previewOnly = true;

                    handler.post(() -> {
                        if (!activityDestroyed && generation == loadGeneration.get()) {
                            onLargeTextPreviewLoaded(previewContent, previewLineCount,
                                    finalFilePath, finalFileName, jumpPosition,
                                    fullByteLength, previewByteCount,
                                    previewStartByte, partitionEndByte, previewBaseCharOffset,
                                    estimatedTotalChars, previewOnly,
                                    restoreTarget.displayPage, restoreTarget.totalPages,
                                    jumpAnchorBefore, jumpAnchorAfter,
                                    estimatedBytesPerChar, partitionBodyCharCount,
                                    partitionStartLine, partitionEndLine, partitionTotalLines);
                        }
                    });

                    if (activityDestroyed || generation != loadGeneration.get() || previewOnly) return;
                }

                String rawContent = FileUtils.readReadableFile(appContext, fileToRead);
                final String content = TextDisplayRuleManager.apply(appContext, rawContent, finalFilePath);
                final int lineCount = countLines(content);

                handler.post(() -> {
                    if (!activityDestroyed && generation == loadGeneration.get()) {
                        onFileLoaded(content, lineCount, finalFilePath, finalFileName, jumpPosition,
                                jumpAnchorBefore, jumpAnchorAfter);
                    }
                });
            } catch (Exception e) {
                handler.post(() -> {
                    if (activityDestroyed || generation != loadGeneration.get()) return;
                    progressText.setText(String.format(Locale.getDefault(), "%s%s", getString(R.string.error_prefix), e.getMessage()));
                    Toast.makeText(this, getString(R.string.error_prefix) + e.getMessage(), Toast.LENGTH_SHORT).show();
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
                + "|displayRules=" + TextDisplayRuleManager.getSignature(getApplicationContext(), filePath)
                + "|screen=" + dm.widthPixels + "x" + dm.heightPixels;
    }

    private boolean shouldUseLargeTextFastOpen(@NonNull File file) {
        return file.isFile()
                && file.length() >= LARGE_TEXT_FAST_OPEN_THRESHOLD_BYTES
                && FileUtils.isTextFile(file.getName());
    }

    private void resetLargeTextExactPageIndex() {
        largeTextExactPageIndexGeneration.incrementAndGet();
        synchronized (largeTextExactPageIndexLock) {
            largeTextExactPageAnchors = new ArrayList<>();
            largeTextExactPageIndexReady = false;
            largeTextExactPageIndexBuilding = false;
            largeTextExactPageIndexSignature = "";
        }
    }

    private String buildLargeTextExactPageIndexSignature(@NonNull String loadedFilePath,
                                                        int layoutWidth,
                                                        int viewportHeight,
                                                        int marginVertical,
                                                        int overlap,
                                                        float lineSpacing,
                                                        @NonNull TextPaint paintSnapshot) {
        File source = new File(loadedFilePath);
        Typeface typeface = paintSnapshot.getTypeface();
        String typefaceKey = typeface == null ? "default" : typeface.getStyle() + "/" + typeface.hashCode();
        return source.getAbsolutePath()
                + "|len=" + source.length()
                + "|mod=" + source.lastModified()
                + "|w=" + layoutWidth
                + "|h=" + viewportHeight
                + "|mv=" + marginVertical
                + "|ol=" + overlap
                + "|ls=" + lineSpacing
                + "|ts=" + paintSnapshot.getTextSize()
                + "|sx=" + paintSnapshot.getTextScaleX()
                + "|tf=" + typefaceKey
                + "|displayRules=" + TextDisplayRuleManager.getSignature(getApplicationContext(), loadedFilePath);
    }

    private String buildCurrentLargeTextExactPageIndexSignature(@NonNull String loadedFilePath) {
        if (readerView == null) return "";
        return buildLargeTextExactPageIndexSignature(
                loadedFilePath,
                readerView.getTextLayoutWidthForIndex(),
                readerView.getViewportHeight(),
                readerView.getMarginVerticalPxForIndex(),
                readerView.getOverlapLinesForIndex(),
                readerView.getLineSpacingMultiplierForIndex(),
                readerView.copyTextPaintForIndex());
    }

    private void clearLargeTextPartitionCache() {
        synchronized (largeTextPartitionCacheLock) {
            largeTextPartitionCache.clear();
            largeTextPendingPartitionPrefetches.clear();
        }
    }

    private void cacheLargeTextPartition(@NonNull LargeTextLinePartitionResult partition) {
        synchronized (largeTextPartitionCacheLock) {
            largeTextPartitionCache.put(partition.startLine, partition);
            largeTextPendingPartitionPrefetches.remove(partition.startLine);
        }
    }

    private LargeTextLinePartitionResult getCachedLargeTextPartitionByStartLine(int startLine) {
        int normalizedStart = getLargeTextPartitionStartLineForLine(startLine);
        synchronized (largeTextPartitionCacheLock) {
            return largeTextPartitionCache.get(normalizedStart);
        }
    }

    private LargeTextLinePartitionResult getCachedLargeTextPartitionForChar(int absoluteCharPosition) {
        int target = Math.max(0, absoluteCharPosition);
        synchronized (largeTextPartitionCacheLock) {
            for (LargeTextLinePartitionResult partition : largeTextPartitionCache.values()) {
                int bodyStart = partition.baseCharOffset;
                int bodyEnd = partition.bodyCharCount > 0
                        ? bodyStart + partition.bodyCharCount
                        : bodyStart + partition.content.length();
                if (target >= bodyStart && target < Math.max(bodyStart + 1, bodyEnd)) {
                    return partition;
                }
            }
            return null;
        }
    }

    private boolean markLargeTextPartitionPrefetchPending(int startLine) {
        int normalizedStart = getLargeTextPartitionStartLineForLine(startLine);
        synchronized (largeTextPartitionCacheLock) {
            if (largeTextPartitionCache.containsKey(normalizedStart)
                    || largeTextPendingPartitionPrefetches.contains(normalizedStart)) {
                return false;
            }
            largeTextPendingPartitionPrefetches.add(normalizedStart);
            return true;
        }
    }

    private void unmarkLargeTextPartitionPrefetchPending(int startLine) {
        int normalizedStart = getLargeTextPartitionStartLineForLine(startLine);
        synchronized (largeTextPartitionCacheLock) {
            largeTextPendingPartitionPrefetches.remove(normalizedStart);
        }
    }

    private boolean isLargeTextExactPageIndexReady() {
        synchronized (largeTextExactPageIndexLock) {
            return largeTextExactPageIndexReady && !largeTextExactPageAnchors.isEmpty();
        }
    }

    private ArrayList<CustomReaderView.PageTextAnchor> copyLargeTextExactPageAnchors() {
        synchronized (largeTextExactPageIndexLock) {
            return new ArrayList<>(largeTextExactPageAnchors);
        }
    }

    private int findExactLargeTextPageForChar(int charPosition) {
        ArrayList<CustomReaderView.PageTextAnchor> anchors = copyLargeTextExactPageAnchors();
        if (anchors.isEmpty()) return 1;
        int target = Math.max(0, charPosition);
        int lo = 0;
        int hi = anchors.size() - 1;
        int best = 0;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int anchor = anchors.get(mid).charPosition;
            if (target >= anchor) {
                best = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return Math.max(1, Math.min(anchors.size(), best + 1));
    }

    private CustomReaderView.PageTextAnchor getExactLargeTextAnchorForPage(int page) {
        ArrayList<CustomReaderView.PageTextAnchor> anchors = copyLargeTextExactPageAnchors();
        if (anchors.isEmpty()) return null;
        int index = Math.max(0, Math.min(anchors.size() - 1, page - 1));
        return anchors.get(index);
    }

    private void startLargeTextExactPageIndexing(@NonNull String loadedFilePath) {
        if (readerView == null || loadedFilePath == null) return;

        final int generation = loadGeneration.get();
        final int layoutWidth = readerView.getTextLayoutWidthForIndex();
        final int viewportHeight = readerView.getViewportHeight();
        final int marginVertical = readerView.getMarginVerticalPxForIndex();
        final int overlap = readerView.getOverlapLinesForIndex();
        final float lineSpacing = readerView.getLineSpacingMultiplierForIndex();
        final TextPaint paintSnapshot = readerView.copyTextPaintForIndex();
        final File source = new File(loadedFilePath);
        final android.content.Context appContext = getApplicationContext();
        final String indexSignature = buildLargeTextExactPageIndexSignature(
                loadedFilePath, layoutWidth, viewportHeight, marginVertical, overlap, lineSpacing, paintSnapshot);
        final int indexGeneration;

        synchronized (largeTextExactPageIndexLock) {
            if (indexSignature.equals(largeTextExactPageIndexSignature)
                    && (largeTextExactPageIndexBuilding || largeTextExactPageIndexReady)) {
                return;
            }
            largeTextExactPageIndexReady = false;
            largeTextExactPageAnchors = new ArrayList<>();
            largeTextExactPageIndexBuilding = true;
            largeTextExactPageIndexSignature = indexSignature;
            indexGeneration = largeTextExactPageIndexGeneration.incrementAndGet();
        }

        executor.execute(() -> {
            ArrayList<CustomReaderView.PageTextAnchor> builtAnchors = new ArrayList<>();
            try {
                String fullText = FileUtils.readReadableFile(appContext, source);
                fullText = TextDisplayRuleManager.apply(appContext, fullText, loadedFilePath);
                builtAnchors = CustomReaderView.buildPageTextAnchors(
                        fullText,
                        paintSnapshot,
                        layoutWidth,
                        viewportHeight,
                        marginVertical,
                        overlap,
                        lineSpacing);
            } catch (Throwable ignored) {
                builtAnchors.clear();
            }

            final ArrayList<CustomReaderView.PageTextAnchor> finalAnchors = builtAnchors;
            handler.post(() -> {
                String currentSignature = loadedFilePath.equals(filePath)
                        ? buildCurrentLargeTextExactPageIndexSignature(loadedFilePath)
                        : "";
                synchronized (largeTextExactPageIndexLock) {
                    if (indexGeneration != largeTextExactPageIndexGeneration.get()
                            || !indexSignature.equals(largeTextExactPageIndexSignature)) {
                        return;
                    }
                    largeTextExactPageIndexBuilding = false;
                    if (activityDestroyed
                            || !loadedFilePath.equals(filePath)
                            || finalAnchors.isEmpty()
                            || !indexSignature.equals(currentSignature)) {
                        largeTextExactPageIndexReady = false;
                        largeTextExactPageAnchors = new ArrayList<>();
                        return;
                    }
                    largeTextExactPageAnchors = finalAnchors;
                    largeTextExactPageIndexReady = true;
                    largeTextEstimatedTotalPages = Math.max(1, finalAnchors.size());
                }
                updatePositionLabel();
                prefetchNeighborLargeTextPartitions();
                processQueuedLargeTextPageDeltaAfterPartitionApply();
            });
        });
    }


    private static final class LargeTextLineStats {
        final int targetLine;
        final int totalLines;
        final int totalChars;

        LargeTextLineStats(int targetLine, int totalLines, int totalChars) {
            this.targetLine = Math.max(1, targetLine);
            this.totalLines = Math.max(1, totalLines);
            this.totalChars = Math.max(0, totalChars);
        }
    }

    private static final class LargeTextLinePartitionResult {
        final String content;
        final int lineCount;
        final int startLine;
        final int endLine;
        final int totalLines;
        final int baseCharOffset;
        final int bodyCharCount;
        final int totalChars;

        LargeTextLinePartitionResult(String content,
                                     int lineCount,
                                     int startLine,
                                     int endLine,
                                     int totalLines,
                                     int baseCharOffset,
                                     int bodyCharCount,
                                     int totalChars) {
            this.content = content != null ? content : "";
            this.lineCount = Math.max(1, lineCount);
            this.startLine = Math.max(1, startLine);
            this.endLine = Math.max(this.startLine, endLine);
            this.totalLines = Math.max(1, totalLines);
            this.baseCharOffset = Math.max(0, baseCharOffset);
            this.bodyCharCount = Math.max(0, bodyCharCount);
            this.totalChars = Math.max(this.baseCharOffset + this.content.length(), totalChars);
        }
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


    private int getLargeTextPartitionStartLineForLine(int line) {
        int clampedLine = Math.max(1, line);
        return ((clampedLine - 1) / LARGE_TEXT_PARTITION_LINES) * LARGE_TEXT_PARTITION_LINES + 1;
    }

    private BufferedReader openLargeTextReader(@NonNull File file) throws IOException {
        String encoding = FileUtils.detectEncoding(file);
        return new BufferedReader(new InputStreamReader(new FileInputStream(file), Charset.forName(encoding)), 64 * 1024);
    }

    private LargeTextLineStats scanLargeTextLineStats(@NonNull File file, int targetCharPosition) throws IOException {
        int targetChar = Math.max(0, targetCharPosition);
        long charCount = 0L;
        int line = 1;
        int targetLine = -1;
        boolean sawAnyLine = false;

        List<TextDisplayRule> activeRules = TextDisplayRuleManager.getActiveRules(getApplicationContext(), file.getAbsolutePath());
        try (BufferedReader reader = openLargeTextReader(file)) {
            String lineText;
            while ((lineText = reader.readLine()) != null) {
                sawAnyLine = true;
                String normalized = FileUtils.enforceTextPresentationSelectors(lineText);
                normalized = TextDisplayRuleManager.apply(normalized, activeRules);
                int lineChars = normalized.length() + 1; // TextView normalizes line breaks to '\n'.
                if (targetLine < 0 && targetChar >= charCount && targetChar < charCount + lineChars) {
                    targetLine = line;
                }
                charCount += lineChars;
                line++;
            }
        }

        int totalLines = sawAnyLine ? Math.max(1, line - 1) : 1;
        if (targetLine < 0) targetLine = totalLines;
        return new LargeTextLineStats(targetLine, totalLines,
                (int) Math.max(0L, Math.min(Integer.MAX_VALUE, charCount)));
    }

    private LargeTextLinePartitionResult readLargeTextLinePartitionForChar(@NonNull File file,
                                                                           int targetCharPosition) throws IOException {
        LargeTextLineStats stats = scanLargeTextLineStats(file, targetCharPosition);
        int startLine = getLargeTextPartitionStartLineForLine(stats.targetLine);
        return readLargeTextLinePartitionAtStartLine(file, startLine, stats.totalLines, stats.totalChars);
    }

    private LargeTextLinePartitionResult readLargeTextLinePartitionAtStartLine(@NonNull File file,
                                                                               int requestedStartLine) throws IOException {
        LargeTextLineStats stats = scanLargeTextLineStats(file, 0);
        int startLine = getLargeTextPartitionStartLineForLine(requestedStartLine);
        if (startLine > stats.totalLines) {
            startLine = getLargeTextPartitionStartLineForLine(stats.totalLines);
        }
        return readLargeTextLinePartitionAtStartLine(file, startLine, stats.totalLines, stats.totalChars);
    }

    private LargeTextLinePartitionResult readLargeTextLinePartitionAtStartLine(@NonNull File file,
                                                                               int requestedStartLine,
                                                                               int knownTotalLines,
                                                                               int knownTotalChars) throws IOException {
        int startLine = getLargeTextPartitionStartLineForLine(requestedStartLine);
        int bodyEndLine = Math.min(Math.max(1, knownTotalLines), startLine + LARGE_TEXT_PARTITION_LINES - 1);
        int captureEndLine = Math.min(Math.max(1, knownTotalLines), bodyEndLine + LARGE_TEXT_PARTITION_LOOKAHEAD_LINES);

        StringBuilder out = new StringBuilder();
        long baseChars = 0L;
        int line = 1;
        int bodyCharCount = 0;
        boolean capturedAny = false;

        List<TextDisplayRule> activeRules = TextDisplayRuleManager.getActiveRules(getApplicationContext(), file.getAbsolutePath());
        try (BufferedReader reader = openLargeTextReader(file)) {
            String lineText;
            boolean firstCapturedLine = true;
            while ((lineText = reader.readLine()) != null) {
                String normalized = FileUtils.enforceTextPresentationSelectors(lineText);
                normalized = TextDisplayRuleManager.apply(normalized, activeRules);
                int lineChars = normalized.length() + 1;

                if (line < startLine) {
                    baseChars += lineChars;
                } else if (line <= captureEndLine) {
                    capturedAny = true;
                    // Rebuild the partition with line breaks between captured lines,
                    // but do not append a synthetic trailing newline at the very end
                    // of the active buffer.  The old unconditional append('\n') made
                    // StaticLayout create an extra blank terminal row in some files;
                    // at EOF that could make the exact total show one page past the
                    // page that already contains the document's final sentence.
                    if (!firstCapturedLine) {
                        out.append('\n');
                    }
                    firstCapturedLine = false;
                    out.append(normalized);
                    if (line <= bodyEndLine) {
                        bodyCharCount = out.length();
                    }
                } else {
                    break;
                }
                line++;
            }
        }

        String content = out.toString();
        if (!capturedAny) {
            content = "";
            bodyCharCount = 0;
        }
        return new LargeTextLinePartitionResult(
                content,
                countLines(content),
                startLine,
                bodyEndLine,
                knownTotalLines,
                (int) Math.max(0L, Math.min(Integer.MAX_VALUE, baseChars)),
                bodyCharCount,
                knownTotalChars);
    }

    private long getLargeTextPartitionStartByte(@NonNull File file, int restoreCharPosition, float estimatedBytesPerChar) {
        long targetByte = Math.round(Math.max(0, restoreCharPosition) * Math.max(1f, estimatedBytesPerChar));
        return getLargeTextPartitionStartByteForByte(file, targetByte);
    }

    private long getLargeTextPartitionStartByteForByte(@NonNull File file, long targetByte) {
        long length = Math.max(1L, file.length());
        long clampedByte = Math.max(0L, Math.min(length - 1L, targetByte));
        long partition = clampedByte / LARGE_TEXT_PARTITION_BYTES;
        long start = partition * LARGE_TEXT_PARTITION_BYTES;
        long maxStart = ((length - 1L) / LARGE_TEXT_PARTITION_BYTES) * (long) LARGE_TEXT_PARTITION_BYTES;
        return Math.max(0L, Math.min(maxStart, start));
    }

    private int countDecodedCharsBeforeByte(@NonNull File file, long byteOffset) throws IOException {
        long remaining = Math.max(0L, Math.min(byteOffset, Math.max(0L, file.length())));
        if (remaining <= 0L) return 0;

        String encoding = FileUtils.detectEncoding(file);
        CharsetDecoder decoder = Charset.forName(encoding)
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);

        byte[] buffer = new byte[64 * 1024];
        CharBuffer chars = CharBuffer.allocate(64 * 1024);
        long count = 0L;
        boolean firstOutput = true;

        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            while (remaining > 0L) {
                int toRead = (int) Math.min(buffer.length, remaining);
                int read = input.read(buffer, 0, toRead);
                if (read < 0) break;
                remaining -= read;

                ByteBuffer bytes = ByteBuffer.wrap(buffer, 0, read);
                while (true) {
                    CoderResult result = decoder.decode(bytes, chars, false);
                    chars.flip();
                    while (chars.hasRemaining()) {
                        char c = chars.get();
                        if (firstOutput && c == '\uFEFF') {
                            firstOutput = false;
                            continue;
                        }
                        firstOutput = false;
                        count++;
                    }
                    chars.clear();

                    if (result.isUnderflow()) break;
                    if (result.isError()) result.throwException();
                }
            }
        }

        ByteBuffer empty = ByteBuffer.allocate(0);
        while (true) {
            CoderResult result = decoder.decode(empty, chars, true);
            chars.flip();
            while (chars.hasRemaining()) {
                char c = chars.get();
                if (firstOutput && c == '\uFEFF') {
                    firstOutput = false;
                    continue;
                }
                firstOutput = false;
                count++;
            }
            chars.clear();
            if (result.isUnderflow()) break;
            if (result.isError()) result.throwException();
        }
        while (true) {
            CoderResult result = decoder.flush(chars);
            chars.flip();
            count += chars.remaining();
            chars.clear();
            if (result.isUnderflow()) break;
            if (result.isError()) result.throwException();
        }

        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, count));
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
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            input.seek(clampedStart);

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
                                          long partitionEndByte,
                                          int previewBaseCharOffset,
                                          int estimatedTotalChars,
                                          boolean previewOnly,
                                          int cachedDisplayPage,
                                          int cachedTotalPages,
                                          String jumpAnchorBefore,
                                          String jumpAnchorAfter,
                                          float estimatedBytesPerChar,
                                          int partitionBodyCharCount,
                                          int partitionStartLine,
                                          int partitionEndLine,
                                          int partitionTotalLines) {
        hideLoadingWindow();

        filePath = loadedFilePath;
        appliedTextDisplayRuleSignature = TextDisplayRuleManager.getSignature(getApplicationContext(), filePath);
        fileName = loadedFileName != null ? loadedFileName : getString(R.string.app_name);
        updateReaderFileTitle();

        if (getSupportActionBar() != null) getSupportActionBar().setTitle(fileName);

        fileContent = previewContent != null ? previewContent : "";
        totalChars = fileContent.length();
        totalLines = previewLineCount;
        largeTextEstimateActive = true;
        largeTextEstimatedTotalPages = 0;
        clearLargeTextPartitionSwitchPending();
        pendingLargeTextRestorePosition = -1;
        largeTextPreviewBaseCharOffset = Math.max(0, previewBaseCharOffset);
        largeTextEstimatedBasePageOffset = 0;
        largeTextEstimatedTotalChars = Math.max(fileContent.length(), estimatedTotalChars);
        hugeTextPreviewOnly = previewOnly;
        pendingLargeTextCachedDisplayPage = Math.max(0, cachedDisplayPage);
        pendingLargeTextCachedTotalPages = Math.max(0, cachedTotalPages);
        largeTextPartitionStartByte = Math.max(0L, previewStartByte);
        largeTextPartitionEndByte = Math.max(largeTextPartitionStartByte, partitionEndByte);
        largeTextFileByteLength = Math.max(1L, fullByteLength);
        largeTextEstimatedBytesPerChar = Math.max(1f, estimatedBytesPerChar);
        largeTextPartitionBodyCharCount = Math.max(0, Math.min(fileContent.length(), partitionBodyCharCount));
        largeTextPartitionStartLine = Math.max(1, partitionStartLine);
        largeTextPartitionEndLine = Math.max(largeTextPartitionStartLine, partitionEndLine);
        largeTextTotalLogicalLines = Math.max(1, partitionTotalLines);
        cacheLargeTextPartition(new LargeTextLinePartitionResult(
                fileContent,
                totalLines,
                largeTextPartitionStartLine,
                largeTextPartitionEndLine,
                largeTextTotalLogicalLines,
                largeTextPreviewBaseCharOffset,
                largeTextPartitionBodyCharCount,
                largeTextEstimatedTotalChars));

        readerView.setLargeTextPartitionMode(true);
        readerView.setOverlapLines(prefs.getPagingOverlapLines());
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
                int anchoredRestorePosition = resolveAnchoredAbsolutePosition(
                        fileContent,
                        largeTextPreviewBaseCharOffset,
                        restorePosition,
                        jumpAnchorBefore,
                        jumpAnchorAfter);
                int localRestorePosition = anchoredRestorePosition - largeTextPreviewBaseCharOffset;
                if (localRestorePosition >= 0 && localRestorePosition < fileContent.length()) {
                    scrollToCharPosition(anchoredRestorePosition);
                } else if (localRestorePosition < 0 && hasPreviousLargeTextPartition()) {
                    reloadLargeTextPreviewAround(
                            anchoredRestorePosition,
                            cachedDisplayPage,
                            cachedTotalPages,
                            jumpAnchorBefore,
                            jumpAnchorAfter,
                            getLargeTextPartitionStartLineForLine(largeTextPartitionStartLine - LARGE_TEXT_PARTITION_LINES));
                    return;
                } else if (localRestorePosition >= fileContent.length() && hasNextLargeTextPartition()) {
                    reloadLargeTextPreviewAround(
                            anchoredRestorePosition,
                            cachedDisplayPage,
                            cachedTotalPages,
                            jumpAnchorBefore,
                            jumpAnchorAfter,
                            getLargeTextPartitionStartLineForLine(largeTextPartitionEndLine + 1));
                    return;
                } else {
                    pendingLargeTextRestorePosition = anchoredRestorePosition;
                }
            }

            int previewPages = Math.max(1, readerView.getTotalPageCount());
            int bodyPages = getLastLocalPageStartingInsideLargeTextPartition();
            float ratio = largeTextTotalLogicalLines / (float) Math.max(1, LARGE_TEXT_PARTITION_LINES);
            largeTextEstimatedTotalPages = Math.max(previewPages, Math.round(Math.max(1, bodyPages) * ratio));
            largeTextEstimatedBasePageOffset = Math.max(0, Math.min(
                    Math.max(0, largeTextEstimatedTotalPages - 1),
                    Math.round(((largeTextPartitionStartLine - 1) / (float) Math.max(1, largeTextTotalLogicalLines))
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
            startLargeTextExactPageIndexing(filePath);
            prefetchNeighborLargeTextPartitions();
        });
    }

    private void onFileLoaded(String content, int lineCount,
                              String loadedFilePath,
                              String loadedFileName,
                              int jumpPosition,
                              String jumpAnchorBefore,
                              String jumpAnchorAfter) {
        boolean replacingLargePreview = largeTextEstimateActive;
        int preservePosition = replacingLargePreview ? getCurrentCharPosition() : -1;
        int deferredRestorePosition = pendingLargeTextRestorePosition;

        hideLoadingWindow();

        filePath = loadedFilePath;
        appliedTextDisplayRuleSignature = TextDisplayRuleManager.getSignature(getApplicationContext(), filePath);
        fileName = loadedFileName != null ? loadedFileName : getString(R.string.app_name);
        updateReaderFileTitle();

        if (getSupportActionBar() != null) getSupportActionBar().setTitle(fileName);

        fileContent = content != null ? content : "";
        totalChars = fileContent.length();
        totalLines = lineCount;
        largeTextEstimateActive = false;
        largeTextEstimatedTotalPages = 0;
        clearLargeTextPartitionSwitchPending();
        pendingLargeTextRestorePosition = -1;
        largeTextPreviewBaseCharOffset = 0;
        largeTextEstimatedBasePageOffset = 0;
        largeTextEstimatedTotalChars = 0;
        hugeTextPreviewOnly = false;
        pendingLargeTextCachedDisplayPage = 0;
        pendingLargeTextCachedTotalPages = 0;
        largeTextPartitionStartByte = 0L;
        largeTextPartitionEndByte = 0L;
        largeTextFileByteLength = 0L;
        largeTextEstimatedBytesPerChar = 1f;
        largeTextPartitionBodyCharCount = 0;
        largeTextPartitionStartLine = 1;
        largeTextPartitionEndLine = 1;
        largeTextTotalLogicalLines = 1;
        resetLargeTextExactPageIndex();
        clearLargeTextPartitionCache();

        readerView.setLargeTextPartitionMode(false);
        readerView.setOverlapLines(prefs.getPagingOverlapLines());
        readerView.setTextContent(fileContent);
        applySearchHighlight();

        readerView.post(() -> {
            if (jumpPosition >= 0) {
                int anchoredJumpPosition = resolveAnchoredAbsolutePosition(
                        fileContent, 0, jumpPosition, jumpAnchorBefore, jumpAnchorAfter);
                scrollToCharPosition(anchoredJumpPosition);
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

    private void onScrollChanged() {
        updateReaderFileTitleMaskBounds();
        schedulePositionUpdate();
    }

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


    private boolean hasNextLargeTextPartition() {
        return largeTextEstimateActive
                && largeTextTotalLogicalLines > 0
                && largeTextPartitionEndLine < largeTextTotalLogicalLines;
    }

    private boolean hasPreviousLargeTextPartition() {
        return largeTextEstimateActive && largeTextPartitionStartLine > 1;
    }

    private int getLastLocalPageStartingInsideLargeTextPartition() {
        if (readerView == null || !largeTextEstimateActive || fileContent == null || fileContent.isEmpty()) {
            return 1;
        }
        int bodyEnd = largeTextPartitionBodyCharCount > 0
                ? Math.min(fileContent.length(), largeTextPartitionBodyCharCount)
                : fileContent.length();
        int lastBodyChar = Math.max(0, bodyEnd - 1);
        return Math.max(1, readerView.getPageNumberForCharPosition(lastBodyChar));
    }

    private void reloadLargeTextPartitionByStartLine(int startLine, int displayPage, int totalPages) {
        if (!largeTextEstimateActive || filePath == null) return;
        int partitionStartLine = getLargeTextPartitionStartLineForLine(startLine);
        int targetChar = largeTextPreviewBaseCharOffset;
        LargeTextLinePartitionResult cached = getCachedLargeTextPartitionByStartLine(partitionStartLine);
        if (partitionStartLine > largeTextPartitionStartLine) {
            // Next partition: land on the next partition base offset. The current
            // partition body does not contain the separator newline after its final
            // body line, so bodyCharCount alone points one char too early.
            targetChar = cached != null
                    ? cached.baseCharOffset
                    : largeTextPreviewBaseCharOffset + Math.max(0, largeTextPartitionBodyCharCount) + 1;
        } else if (partitionStartLine < largeTextPartitionStartLine) {
            // Previous partition: target the previous body end and let the reader
            // clamp to the final visual page of that partition.
            targetChar = cached != null
                    ? cached.baseCharOffset + Math.max(0, cached.bodyCharCount - 1)
                    : Math.max(0, largeTextPreviewBaseCharOffset - 1);
        }

        int preservedDisplayPage = displayPage;
        int preservedTotalPages = totalPages;
        if (preservedDisplayPage <= 0) {
            int currentDisplayPage = getDisplayedCurrentPageNumber();
            if (partitionStartLine > largeTextPartitionStartLine) {
                preservedDisplayPage = currentDisplayPage + 1;
            } else if (partitionStartLine < largeTextPartitionStartLine) {
                preservedDisplayPage = currentDisplayPage - 1;
            } else {
                preservedDisplayPage = currentDisplayPage;
            }
        }
        if (preservedTotalPages <= 0) {
            preservedTotalPages = getDisplayedTotalPageCount();
        }
        preservedTotalPages = Math.max(1, preservedTotalPages);
        preservedDisplayPage = Math.max(1, Math.min(preservedTotalPages, preservedDisplayPage));

        switchLargeTextPartitionInPlace(targetChar, preservedDisplayPage, preservedTotalPages, null, null, partitionStartLine);
    }

    private void prefetchNeighborLargeTextPartitions() {
        if (!largeTextEstimateActive || filePath == null) return;

        int nextStart = getLargeTextPartitionStartLineForLine(largeTextPartitionEndLine + 1);
        int previousStart = getLargeTextPartitionStartLineForLine(largeTextPartitionStartLine - LARGE_TEXT_PARTITION_LINES);
        boolean preferPrevious = largeTextLastPageDirection < 0;

        if (preferPrevious) {
            if (hasPreviousLargeTextPartition()) {
                prefetchLargeTextPartitionByStartLine(previousStart);
                if (largeTextSameDirectionPageCount >= 2) {
                    int previousPrevious = getLargeTextPartitionStartLineForLine(previousStart - LARGE_TEXT_PARTITION_LINES);
                    if (previousPrevious >= 1 && previousPrevious < previousStart) {
                        prefetchLargeTextPartitionByStartLine(previousPrevious);
                    }
                }
            }
            if (hasNextLargeTextPartition()) {
                prefetchLargeTextPartitionByStartLine(nextStart);
            }
        } else {
            if (hasNextLargeTextPartition()) {
                prefetchLargeTextPartitionByStartLine(nextStart);
                if (largeTextSameDirectionPageCount >= 2) {
                    int nextNext = getLargeTextPartitionStartLineForLine(nextStart + LARGE_TEXT_PARTITION_LINES);
                    if (nextNext <= largeTextTotalLogicalLines && nextNext > nextStart) {
                        prefetchLargeTextPartitionByStartLine(nextNext);
                    }
                }
            }
            if (hasPreviousLargeTextPartition()) {
                prefetchLargeTextPartitionByStartLine(previousStart);
            }
        }
    }


    private void prefetchLargeTextPartitionByStartLine(int requestedStartLine) {
        if (filePath == null || !largeTextEstimateActive) return;
        final int startLine = getLargeTextPartitionStartLineForLine(requestedStartLine);
        if (!markLargeTextPartitionPrefetchPending(startLine)) return;

        final int generation = loadGeneration.get();
        final String expectedPath = filePath;
        final int knownTotalLines = Math.max(1, largeTextTotalLogicalLines);
        final int knownTotalChars = Math.max(0, largeTextEstimatedTotalChars);

        largeTextPartitionExecutor.execute(() -> {
            try {
                File source = new File(expectedPath);
                LargeTextLinePartitionResult partition = readLargeTextLinePartitionAtStartLine(
                        source, startLine, knownTotalLines, knownTotalChars);
                if (!activityDestroyed
                        && generation == loadGeneration.get()
                        && expectedPath.equals(filePath)) {
                    cacheLargeTextPartition(partition);
                } else {
                    unmarkLargeTextPartitionPrefetchPending(startLine);
                }
            } catch (Throwable ignored) {
                unmarkLargeTextPartitionPrefetchPending(startLine);
            }
        });
    }

    private void beginLargeTextPartitionSwitchPending(int displayPage, int totalPages) {
        largeTextPartitionSwitchInProgress = true;
        int stableTotal = totalPages > 0 ? totalPages : getDisplayedTotalPageCount();
        stableTotal = Math.max(1, stableTotal);
        int stablePage = displayPage > 0 ? displayPage : getDisplayedCurrentPageNumber();
        largeTextPartitionPendingTotalPages = stableTotal;
        largeTextPartitionPendingDisplayPage = Math.max(1, Math.min(stableTotal, stablePage));
    }

    private void clearLargeTextPartitionSwitchPending() {
        largeTextPartitionSwitchInProgress = false;
        largeTextPartitionPendingDisplayPage = 0;
        largeTextPartitionPendingTotalPages = 0;
    }

    private void switchLargeTextPartitionInPlace(int charPosition,
                                                int displayPage,
                                                int totalPages,
                                                String anchorBefore,
                                                String anchorAfter,
                                                int partitionStartLine) {
        switchLargeTextPartitionInPlace(charPosition, displayPage, totalPages, anchorBefore, anchorAfter,
                partitionStartLine, false);
    }

    private void switchLargeTextPartitionInPlace(int charPosition,
                                                int displayPage,
                                                int totalPages,
                                                String anchorBefore,
                                                String anchorAfter,
                                                int partitionStartLine,
                                                boolean showLoadingForAsyncPartitionJump) {
        if (filePath == null || !largeTextEstimateActive) return;

        final int targetChar = Math.max(0, charPosition);
        final int targetStartLine = partitionStartLine > 0
                ? getLargeTextPartitionStartLineForLine(partitionStartLine)
                : -1;
        final int switchGeneration = largeTextPartitionSwitchGeneration.incrementAndGet();
        beginLargeTextPartitionSwitchPending(displayPage, totalPages);

        LargeTextLinePartitionResult cached = targetStartLine > 0
                ? getCachedLargeTextPartitionByStartLine(targetStartLine)
                : getCachedLargeTextPartitionForChar(targetChar);
        if (cached != null) {
            applyLargeTextPartitionInPlace(
                    cached, targetChar, displayPage, totalPages, anchorBefore, anchorAfter, false, switchGeneration);
            return;
        }

        final int generation = loadGeneration.get();
        final String expectedPath = filePath;
        final int knownTotalLines = Math.max(1, largeTextTotalLogicalLines);
        final int knownTotalChars = Math.max(0, largeTextEstimatedTotalChars);

        if (showLoadingForAsyncPartitionJump) {
            showLoadingWindowForPartitionJump(switchGeneration);
        }

        largeTextPartitionExecutor.execute(() -> {
            try {
                File source = new File(expectedPath);
                LargeTextLinePartitionResult partition = targetStartLine > 0
                        ? readLargeTextLinePartitionAtStartLine(source, targetStartLine, knownTotalLines, knownTotalChars)
                        : readLargeTextLinePartitionForChar(source, targetChar);
                cacheLargeTextPartition(partition);

                handler.post(() -> {
                    if (activityDestroyed
                            || generation != loadGeneration.get()
                            || switchGeneration != largeTextPartitionSwitchGeneration.get()
                            || !expectedPath.equals(filePath)) {
                        if (switchGeneration == largeTextPartitionSwitchGeneration.get()) {
                            clearLargeTextPartitionSwitchPending();
                            clearLargeTextQueuedPageDelta();
                        }
                        hideLoadingWindowForPartitionJumpIfCurrent(showLoadingForAsyncPartitionJump, switchGeneration);
                        return;
                    }
                    applyLargeTextPartitionInPlace(
                            partition, targetChar, displayPage, totalPages, anchorBefore, anchorAfter,
                            showLoadingForAsyncPartitionJump, switchGeneration);
                });
            } catch (Throwable t) {
                handler.post(() -> {
                    if (switchGeneration == largeTextPartitionSwitchGeneration.get()) {
                        clearLargeTextPartitionSwitchPending();
                        clearLargeTextQueuedPageDelta();
                    }
                    hideLoadingWindowForPartitionJumpIfCurrent(showLoadingForAsyncPartitionJump, switchGeneration);
                    if (!activityDestroyed
                            && generation == loadGeneration.get()
                            && switchGeneration == largeTextPartitionSwitchGeneration.get()) {
                        Toast.makeText(this,
                                getString(R.string.error_prefix) + t.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void applyLargeTextPartitionInPlace(@NonNull LargeTextLinePartitionResult partition,
                                                int targetCharPosition,
                                                int displayPage,
                                                int totalPages,
                                                String anchorBefore,
                                                String anchorAfter) {
        applyLargeTextPartitionInPlace(partition, targetCharPosition, displayPage, totalPages,
                anchorBefore, anchorAfter, false, largeTextPartitionSwitchGeneration.get());
    }

    private void applyLargeTextPartitionInPlace(@NonNull LargeTextLinePartitionResult partition,
                                                int targetCharPosition,
                                                int displayPage,
                                                int totalPages,
                                                String anchorBefore,
                                                String anchorAfter,
                                                boolean hideLoadingAfterApply,
                                                int switchGeneration) {
        if (readerView == null || filePath == null || activityDestroyed) {
            if (switchGeneration == largeTextPartitionSwitchGeneration.get()) {
                clearLargeTextPartitionSwitchPending();
            }
            hideLoadingWindowForPartitionJumpIfCurrent(hideLoadingAfterApply, switchGeneration);
            return;
        }

        cacheLargeTextPartition(partition);

        fileContent = partition.content != null ? partition.content : "";
        totalChars = fileContent.length();
        totalLines = partition.lineCount;
        largeTextEstimateActive = true;
        pendingLargeTextRestorePosition = -1;
        largeTextPreviewBaseCharOffset = Math.max(0, partition.baseCharOffset);
        largeTextEstimatedTotalChars = Math.max(fileContent.length(), partition.totalChars);
        hugeTextPreviewOnly = true;
        largeTextPartitionStartByte = 0L;
        largeTextPartitionEndByte = Math.max(largeTextPartitionStartByte, largeTextFileByteLength);
        largeTextPartitionBodyCharCount = Math.max(0, Math.min(fileContent.length(), partition.bodyCharCount));
        largeTextPartitionStartLine = Math.max(1, partition.startLine);
        largeTextPartitionEndLine = Math.max(largeTextPartitionStartLine, partition.endLine);
        largeTextTotalLogicalLines = Math.max(1, partition.totalLines);

        readerView.setLargeTextPartitionMode(true);
        readerView.setOverlapLines(prefs.getPagingOverlapLines());
        readerView.setTextContent(fileContent);
        applySearchHighlight();

        readerView.post(() -> {
            if (activityDestroyed || readerView == null) {
                if (switchGeneration == largeTextPartitionSwitchGeneration.get()) {
                    clearLargeTextPartitionSwitchPending();
                }
                hideLoadingWindowForPartitionJumpIfCurrent(hideLoadingAfterApply, switchGeneration);
                return;
            }

            int resolvedPosition = resolveAnchoredAbsolutePosition(
                    fileContent,
                    largeTextPreviewBaseCharOffset,
                    Math.max(0, targetCharPosition),
                    anchorBefore,
                    anchorAfter);
            int localPosition = resolvedPosition - largeTextPreviewBaseCharOffset;
            if (localPosition < 0 || localPosition >= fileContent.length()) {
                int clampedLocal = Math.max(0, Math.min(Math.max(0, fileContent.length() - 1), localPosition));
                resolvedPosition = largeTextPreviewBaseCharOffset + clampedLocal;
            }

            scrollToCharPosition(resolvedPosition);
            recomputeLargeTextDisplayPageOffset(displayPage, totalPages);
            if (switchGeneration == largeTextPartitionSwitchGeneration.get()) {
                clearLargeTextPartitionSwitchPending();
            }
            updatePositionLabel();
            hideLoadingWindowForPartitionJumpIfCurrent(hideLoadingAfterApply, switchGeneration);
            prefetchNeighborLargeTextPartitions();
            processQueuedLargeTextPageDeltaAfterPartitionApply();
        });
    }

    private void recomputeLargeTextDisplayPageOffset(int displayPage, int totalPages) {
        if (readerView == null || !largeTextEstimateActive) return;

        int localPage = Math.max(1, readerView.getCurrentPageNumber());

        // If the caller already knows the intended global page/total, preserve it.
        // Do not recompute the denominator from the transient active partition here;
        // that was the source of 8082/8093 -> 7449/8093-style jumps and changing
        // total-page counts during rapid partition-boundary paging.
        if (displayPage > 0 || totalPages > 0) {
            int stableTotal = totalPages > 0
                    ? totalPages
                    : Math.max(largeTextEstimatedTotalPages, getDisplayedTotalPageCount());
            stableTotal = Math.max(1, Math.max(stableTotal, displayPage));
            int stablePage = displayPage > 0
                    ? Math.max(1, Math.min(stableTotal, displayPage))
                    : Math.max(1, Math.min(stableTotal, largeTextEstimatedBasePageOffset + localPage));
            largeTextEstimatedTotalPages = stableTotal;
            largeTextEstimatedBasePageOffset = Math.max(0,
                    Math.min(Math.max(0, stableTotal - localPage), stablePage - localPage));
            return;
        }

        int previewPages = Math.max(1, readerView.getTotalPageCount());
        int bodyPages = getLastLocalPageStartingInsideLargeTextPartition();
        int exactTotal = isLargeTextExactPageIndexReady() ? copyLargeTextExactPageAnchors().size() : 0;
        if (exactTotal > 0) {
            largeTextEstimatedTotalPages = Math.max(1, exactTotal);
        } else if (largeTextEstimatedTotalPages <= 0) {
            float ratio = largeTextTotalLogicalLines / (float) Math.max(1, LARGE_TEXT_PARTITION_LINES);
            largeTextEstimatedTotalPages = Math.max(previewPages, Math.round(Math.max(1, bodyPages) * ratio));
        }

        largeTextEstimatedBasePageOffset = Math.max(0, Math.min(
                Math.max(0, largeTextEstimatedTotalPages - 1),
                Math.round(((largeTextPartitionStartLine - 1) / (float) Math.max(1, largeTextTotalLogicalLines))
                        * largeTextEstimatedTotalPages)));
    }

    private int getProgressPercent() {
        int total = Math.max(1, getDisplayedTotalPageCount());
        if (total <= 1) return 0;
        return Math.max(0, Math.min(100, Math.round(100f * (getDisplayedCurrentPageNumber() - 1) / (total - 1))));
    }

    private int getTotalPageCount() { return readerView != null ? readerView.getTotalPageCount() : 1; }
    private int getDisplayedTotalPageCount() {
        if (largeTextEstimateActive) {
            if (largeTextPartitionSwitchInProgress && largeTextPartitionPendingTotalPages > 0) {
                return Math.max(1, largeTextPartitionPendingTotalPages);
            }
            if (isLargeTextExactPageIndexReady()) {
                return Math.max(1, copyLargeTextExactPageAnchors().size());
            }
            if (largeTextEstimatedTotalPages > 0) {
                return Math.max(1, largeTextEstimatedTotalPages);
            }
        }
        return getTotalPageCount();
    }
    private int getCurrentPageNumber() { return readerView != null ? readerView.getCurrentPageNumber() : 1; }
    private int getDisplayedCurrentPageNumber() {
        int localPage = getCurrentPageNumber();
        if (largeTextEstimateActive) {
            int total = getDisplayedTotalPageCount();
            if (largeTextPartitionSwitchInProgress && largeTextPartitionPendingDisplayPage > 0) {
                return Math.max(1, Math.min(total, largeTextPartitionPendingDisplayPage));
            }
            // Final fixed-line partition can legitimately show the document's last
            // sentence while the global char-position lookup still maps to the
            // previous page anchor.  When the active partition is the real EOF
            // partition and the renderer is physically at its bottom, normalize the
            // status to the final page instead of leaving it at total-1.
            if (isAtEndOfLargeTextDocument()) {
                return total;
            }
            if (isLargeTextExactPageIndexReady()) {
                return Math.max(1, Math.min(total, findExactLargeTextPageForChar(getCurrentCharPosition())));
            }
            return Math.max(1, Math.min(total,
                    largeTextEstimatedBasePageOffset + localPage));
        }
        return localPage;
    }

    private boolean isAtEndOfLargeTextDocument() {
        return largeTextEstimateActive
                && largeTextTotalLogicalLines > 0
                && largeTextPartitionEndLine >= largeTextTotalLogicalLines
                && readerView != null
                && readerView.isAtVisualEndOfText();
    }
    private boolean scrollToPageNumber(int page) {
        return scrollToPageNumber(page, false, true);
    }

    private boolean scrollToPageNumber(int page, boolean showLoadingForAsyncPartitionJump) {
        return scrollToPageNumber(page, showLoadingForAsyncPartitionJump, true);
    }

    private boolean scrollToPageNumber(int page, boolean showLoadingForAsyncPartitionJump, boolean manualNavigation) {
        if (manualNavigation) stopAutoPageTurnForManualNavigation();
        if (readerView == null) return false;

        if (largeTextEstimateActive) {
            if (isLargeTextExactPageIndexReady()) {
                int total = getDisplayedTotalPageCount();
                int target = Math.max(1, Math.min(total, page));
                CustomReaderView.PageTextAnchor anchor = getExactLargeTextAnchorForPage(target);
                if (anchor != null) {
                    jumpToAbsoluteCharPosition(anchor.charPosition, target, total,
                            anchor.anchorTextBefore, anchor.anchorTextAfter,
                            showLoadingForAsyncPartitionJump);
                    return true;
                }
                return false;
            }
            Toast.makeText(this,
                    prefs.getLanguageMode() == PrefsManager.LANGUAGE_KOREAN
                            ? "대용량 TXT 페이지 인덱스를 계산 중입니다. 잠시 후 정확한 페이지 이동이 가능합니다."
                            : "Large TXT page index is still calculating. Exact page jump will be available shortly.",
                    Toast.LENGTH_SHORT).show();
            return false;
        }

        int total = Math.max(1, getTotalPageCount());
        readerView.scrollToPage(Math.max(1, Math.min(total, page)));
        return true;
    }

    private void updatePositionLabel() {
        if (readerView == null) return;
        int totalPages = getDisplayedTotalPageCount();
        int currentPage = getDisplayedCurrentPageNumber();

        if ((toolbarSeekTracking || pendingToolbarSeekJump) && pendingToolbarSeekPage > 0) {
            int pendingTotal = Math.max(1, Math.max(totalPages, pendingToolbarSeekTotalPages));
            int pendingPage = Math.max(1, Math.min(pendingTotal, pendingToolbarSeekPage));
            if (!toolbarSeekTracking && currentPage == pendingPage) {
                clearPendingToolbarSeekJump();
            } else {
                setPageLabels(pendingPage, pendingTotal);
                if (seekBar != null) {
                    suppressSeekCallback = true;
                    seekBar.setMax(Math.max(0, pendingTotal - 1));
                    seekBar.setProgress(Math.max(0, Math.min(pendingTotal - 1, pendingPage - 1)));
                    suppressSeekCallback = false;
                }
                return;
            }
        }

        setPageLabels(currentPage, totalPages);

        if (seekBar != null) {
            suppressSeekCallback = true;
            seekBar.setMax(Math.max(0, totalPages - 1));
            seekBar.setProgress(Math.max(0, Math.min(totalPages - 1, currentPage - 1)));
            suppressSeekCallback = false;
        }
    }

    private void setPageLabels(int currentPage, int totalPages) {
        totalPages = Math.max(1, totalPages);
        currentPage = Math.max(1, Math.min(totalPages, currentPage));
        String totalText = (largeTextEstimateActive
                && !isLargeTextExactPageIndexReady()
                && largeTextEstimatedTotalPages > 0)
                ? "~" + totalPages
                : String.valueOf(totalPages);
        String text = String.format(Locale.getDefault(), "%d / %s", currentPage, totalText);
        if (positionLabel != null) positionLabel.setText(text);
        if (readerPageStatus != null) readerPageStatus.setText(text);
    }

    private void scrollToPercent(float percent) { if (readerView != null) readerView.scrollToPercent(percent); }
    private int toLocalCharPosition(int charPosition) {
        int localPosition = largeTextEstimateActive
                ? charPosition - largeTextPreviewBaseCharOffset
                : charPosition;
        return Math.max(0, Math.min(fileContent != null ? fileContent.length() : 0, localPosition));
    }

    private void scrollToCharPosition(int charPosition) {
        if (readerView != null) {
            readerView.scrollToCharPosition(toLocalCharPosition(charPosition));
            readerView.post(this::updatePositionLabel);
        }
    }

    private void scrollToCharPositionWithContext(int charPosition) {
        if (readerView != null) {
            readerView.scrollToCharPositionWithContext(toLocalCharPosition(charPosition));
            readerView.post(this::updatePositionLabel);
        }
    }

    private void jumpToAbsoluteCharPosition(int charPosition) {
        jumpToAbsoluteCharPosition(charPosition, 0, 0, null, null);
    }

    private void jumpToAbsoluteCharPosition(int charPosition, int displayPage, int totalPages) {
        jumpToAbsoluteCharPosition(charPosition, displayPage, totalPages, null, null);
    }

    private void jumpToAbsoluteCharPosition(int charPosition,
                                            int displayPage,
                                            int totalPages,
                                            String anchorBefore,
                                            String anchorAfter,
                                            boolean showLoadingForAsyncPartitionJump) {
        jumpToAbsoluteCharPositionInternal(
                charPosition,
                displayPage,
                totalPages,
                anchorBefore,
                anchorAfter,
                showLoadingForAsyncPartitionJump);
    }

    private boolean isAbsoluteCharPositionInCurrentLargeTextBody(int absolutePosition) {
        if (!largeTextEstimateActive || fileContent == null) return false;
        int localPosition = absolutePosition - largeTextPreviewBaseCharOffset;
        int bodyEnd = largeTextPartitionBodyCharCount > 0
                ? Math.min(fileContent.length(), largeTextPartitionBodyCharCount)
                : fileContent.length();
        return localPosition >= 0 && localPosition < bodyEnd;
    }

    private void jumpToAbsoluteCharPosition(int charPosition,
                                            int displayPage,
                                            int totalPages,
                                            String anchorBefore,
                                            String anchorAfter) {
        jumpToAbsoluteCharPositionInternal(
                charPosition,
                displayPage,
                totalPages,
                anchorBefore,
                anchorAfter,
                false);
    }

    private void jumpToAbsoluteCharPositionInternal(int charPosition,
                                                    int displayPage,
                                                    int totalPages,
                                                    String anchorBefore,
                                                    String anchorAfter,
                                                    boolean showLoadingForAsyncPartitionJump) {
        if (!largeTextEstimateActive) {
            scrollToCharPosition(charPosition);
            return;
        }

        // In fixed-line partition mode, the lookahead text is only a render aid for
        // pages that cross the partition edge.  A bookmark/page target that belongs
        // to the next partition must reload its owning partition; otherwise the
        // local page and estimated base page can be taken from the previous
        // partition and the bookmark appears to open on the wrong page.
        if (isAbsoluteCharPositionInCurrentLargeTextBody(charPosition)) {
            int resolvedPosition = resolveAnchoredAbsolutePosition(
                    fileContent,
                    largeTextPreviewBaseCharOffset,
                    charPosition,
                    anchorBefore,
                    anchorAfter);

            if (!isAbsoluteCharPositionInCurrentLargeTextBody(resolvedPosition)) {
                reloadLargeTextPreviewAround(resolvedPosition, displayPage, totalPages, anchorBefore, anchorAfter, showLoadingForAsyncPartitionJump);
                return;
            }

            if (displayPage > 0) {
                int localPage = Math.max(1, readerView != null ? readerView.getCurrentPageNumber() : 1);
                int displayedTotal = totalPages > 0 ? totalPages : getDisplayedTotalPageCount();
                largeTextEstimatedTotalPages = Math.max(displayedTotal, localPage);
                largeTextEstimatedBasePageOffset = Math.max(0, displayPage - localPage);
            }
            scrollToCharPosition(resolvedPosition);
            return;
        }

        reloadLargeTextPreviewAround(charPosition, displayPage, totalPages, anchorBefore, anchorAfter, showLoadingForAsyncPartitionJump);
    }

    private void reloadLargeTextPreviewAround(int charPosition) {
        reloadLargeTextPreviewAround(charPosition, 0, 0, null, null);
    }

    private void reloadLargeTextPreviewAround(int charPosition, int displayPage, int totalPages) {
        reloadLargeTextPreviewAround(charPosition, displayPage, totalPages, null, null);
    }

    private void reloadLargeTextPreviewAround(int charPosition,
                                              int displayPage,
                                              int totalPages,
                                              String anchorBefore,
                                              String anchorAfter) {
        reloadLargeTextPreviewAround(charPosition, displayPage, totalPages, anchorBefore, anchorAfter, false);
    }

    private void reloadLargeTextPreviewAround(int charPosition,
                                              int displayPage,
                                              int totalPages,
                                              String anchorBefore,
                                              String anchorAfter,
                                              boolean showLoadingForAsyncPartitionJump) {
        reloadLargeTextPreviewAround(charPosition, displayPage, totalPages, anchorBefore, anchorAfter, -1,
                showLoadingForAsyncPartitionJump);
    }

    private void reloadLargeTextPreviewAround(int charPosition,
                                              int displayPage,
                                              int totalPages,
                                              String anchorBefore,
                                              String anchorAfter,
                                              int partitionStartLine) {
        reloadLargeTextPreviewAround(charPosition, displayPage, totalPages, anchorBefore, anchorAfter,
                partitionStartLine, false);
    }

    private void reloadLargeTextPreviewAround(int charPosition,
                                              int displayPage,
                                              int totalPages,
                                              String anchorBefore,
                                              String anchorAfter,
                                              int partitionStartLine,
                                              boolean showLoadingForAsyncPartitionJump) {
        if (filePath == null) return;
        if (largeTextEstimateActive) {
            switchLargeTextPartitionInPlace(
                    charPosition, displayPage, totalPages, anchorBefore, anchorAfter, partitionStartLine,
                    showLoadingForAsyncPartitionJump);
            return;
        }

        Intent intent = new Intent(this, ReaderActivity.class);
        intent.putExtra(EXTRA_FILE_PATH, filePath);
        intent.putExtra(EXTRA_JUMP_TO_POSITION, Math.max(0, charPosition));
        intent.putExtra(EXTRA_JUMP_DISPLAY_PAGE, Math.max(0, displayPage));
        intent.putExtra(EXTRA_JUMP_TOTAL_PAGES, Math.max(0, totalPages));
        if (partitionStartLine > 0) intent.putExtra(EXTRA_JUMP_PARTITION_START_LINE, partitionStartLine);
        if (anchorBefore != null) intent.putExtra(EXTRA_JUMP_ANCHOR_BEFORE, anchorBefore);
        if (anchorAfter != null) intent.putExtra(EXTRA_JUMP_ANCHOR_AFTER, anchorAfter);
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

        // Vertical mode:
        // top zone -> previous page, middle zone -> menu, bottom zone -> next page.
        if (y < topBoundary) {
            pageUp();
        } else if (y > bottomBoundary) {
            pageDown();
        } else {
            toggleToolbar();
        }
    }

    private void pageDown() { pageBy(+1, false); }
    private void pageUp() { pageBy(-1, false); }

    private void recordLargeTextPageDirection(int direction) {
        if (!largeTextEstimateActive || direction == 0) return;
        if (largeTextLastPageDirection == direction) {
            largeTextSameDirectionPageCount = Math.min(8, largeTextSameDirectionPageCount + 1);
        } else {
            largeTextLastPageDirection = direction;
            largeTextSameDirectionPageCount = 1;
        }
    }

    private void resetLargeTextPageDirectionTracking() {
        largeTextLastPageDirection = 0;
        largeTextSameDirectionPageCount = 0;
    }

    private void clearLargeTextQueuedPageDelta() {
        queuedLargeTextPageDelta = 0;
    }

    private void queueLargeTextPageDeltaWhileSwitching(int direction) {
        if (!largeTextEstimateActive || !isLargeTextExactPageIndexReady() || direction == 0) return;
        int total = Math.max(1, getDisplayedTotalPageCount());
        int pendingPage = largeTextPartitionPendingDisplayPage > 0
                ? largeTextPartitionPendingDisplayPage
                : getDisplayedCurrentPageNumber();
        pendingPage = Math.max(1, Math.min(total, pendingPage));
        int target = Math.max(1, Math.min(total, pendingPage + direction));
        queuedLargeTextPageDelta += target - pendingPage;
        largeTextPartitionPendingTotalPages = total;
        largeTextPartitionPendingDisplayPage = target;
    }

    private void processQueuedLargeTextPageDeltaAfterPartitionApply() {
        if (!largeTextEstimateActive || queuedLargeTextPageDelta == 0 || largeTextPartitionSwitchInProgress) return;
        if (!isLargeTextExactPageIndexReady()) {
            clearLargeTextQueuedPageDelta();
            return;
        }
        int delta = queuedLargeTextPageDelta;
        clearLargeTextQueuedPageDelta();
        int total = Math.max(1, getDisplayedTotalPageCount());
        int current = Math.max(1, Math.min(total, getDisplayedCurrentPageNumber()));
        int target = Math.max(1, Math.min(total, current + delta));
        if (target != current) {
            scrollToPageNumber(target, false, false);
        }
    }
    private void pageBy(int direction) {
        pageBy(direction, false);
    }

    private void pageBy(int direction, boolean fromAutoPageTurn) {
        if (!fromAutoPageTurn) stopAutoPageTurnForManualNavigation();
        if (readerView == null || direction == 0) return;

        if (largeTextEstimateActive && largeTextPartitionSwitchInProgress) {
            queueLargeTextPageDeltaWhileSwitching(direction);
            updatePositionLabel();
            return;
        }

        if (largeTextEstimateActive) {
            recordLargeTextPageDirection(direction);
        }

        if (largeTextEstimateActive && isLargeTextExactPageIndexReady()) {
            int total = getDisplayedTotalPageCount();
            int current = getDisplayedCurrentPageNumber();
            int target = Math.max(1, Math.min(total, current + direction));
            if (target != current) {
                scrollToPageNumber(target, false, false);
            } else {
                updatePositionLabel();
            }
            return;
        }

        if (largeTextEstimateActive) {
            int localPage = Math.max(1, readerView.getCurrentPageNumber());
            int displayedTotal = getDisplayedTotalPageCount();
            int displayedCurrent = getDisplayedCurrentPageNumber();
            if (direction > 0 && hasNextLargeTextPartition()) {
                int bodyEnd = largeTextPartitionBodyCharCount > 0
                        ? Math.min(fileContent != null ? fileContent.length() : 0, largeTextPartitionBodyCharCount)
                        : (fileContent != null ? fileContent.length() : 0);
                int nextPageStartLocal = Math.max(0, Math.min(
                        fileContent != null ? fileContent.length() : 0,
                        readerView.getCharPositionForNextPageStartRespectingOverlap()));

                // Partition handoff must follow the same visual page-start rule as
                // normal TXT paging.  That means user-configured overlap lines are
                // honored, but the seam must not add any extra duplicate lines.  If
                // the next page still starts inside the current body/lookahead buffer,
                // stay local; switch partitions only when that normal next-page start
                // is outside the current partition body.
                if (nextPageStartLocal >= bodyEnd) {
                    int targetAbs = largeTextPreviewBaseCharOffset + Math.max(0, nextPageStartLocal);
                    // If the next-page start is exactly the body edge, move past the
                    // separator newline that exists in the full file but is not stored
                    // at the end of the current partition body.
                    if (nextPageStartLocal == bodyEnd) {
                        targetAbs += 1;
                    }
                    int displayedTarget = Math.max(1, Math.min(displayedTotal, displayedCurrent + 1));
                    reloadLargeTextPreviewAround(targetAbs, displayedTarget, displayedTotal, null, null, -1, false);
                    return;
                }
            }
            if (direction < 0 && localPage <= 1 && hasPreviousLargeTextPartition()) {
                int previousStart = getLargeTextPartitionStartLineForLine(largeTextPartitionStartLine - LARGE_TEXT_PARTITION_LINES);
                int displayedTarget = Math.max(1, Math.min(displayedTotal, displayedCurrent - 1));
                reloadLargeTextPartitionByStartLine(previousStart, displayedTarget, displayedTotal);
                return;
            }
        }

        readerView.pageBy(direction);
        readerView.post(() -> {
            updatePositionLabel();
            if (largeTextEstimateActive) prefetchNeighborLargeTextPartitions();
        });
    }

    private int getCurrentCharPosition() {
        int localPosition = readerView != null ? readerView.getCurrentCharPosition() : 0;
        return largeTextEstimateActive
                ? Math.max(0, largeTextPreviewBaseCharOffset + localPosition)
                : localPosition;
    }

    private int getBookmarkSaveCharPosition() {
        if (readerView == null) return 0;

        // TXT bookmark save targets the actual row covered by the filename/title
        // strip using the same visual coordinate system used for drawing.  This
        // avoids status-bar/font/boundary-related off-by-one saves from raw scrollY.
        int localPosition = readerView.getCharPositionAtTitleCoveredRow();

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

    private String getAnchorTextBefore(int charPosition) {
        if (fileContent == null || fileContent.isEmpty()) return "";
        int localPosition = largeTextEstimateActive
                ? charPosition - largeTextPreviewBaseCharOffset
                : charPosition;
        int pos = Math.max(0, Math.min(fileContent.length(), localPosition));
        int start = Math.max(0, pos - 80);
        return fileContent.substring(start, pos);
    }

    private String getAnchorTextAfter(int charPosition) {
        if (fileContent == null || fileContent.isEmpty()) return "";
        int localPosition = largeTextEstimateActive
                ? charPosition - largeTextPreviewBaseCharOffset
                : charPosition;
        int pos = Math.max(0, Math.min(fileContent.length(), localPosition));
        int end = Math.min(fileContent.length(), pos + 120);
        return fileContent.substring(pos, end);
    }

    private int resolveAnchoredAbsolutePosition(String content,
                                                int baseCharOffset,
                                                int fallbackAbsolutePosition,
                                                String anchorBefore,
                                                String anchorAfter) {
        if (content == null || content.isEmpty()) {
            return Math.max(0, fallbackAbsolutePosition);
        }

        int fallbackLocal = Math.max(0, Math.min(content.length(),
                fallbackAbsolutePosition - Math.max(0, baseCharOffset)));

        int resolvedLocal = findBestAnchorPosition(content, fallbackLocal, anchorBefore, anchorAfter);
        if (resolvedLocal < 0) {
            resolvedLocal = fallbackLocal;
        }

        return Math.max(0, baseCharOffset) + Math.max(0, Math.min(content.length(), resolvedLocal));
    }

    private int findBestAnchorPosition(String content,
                                       int fallbackLocalPosition,
                                       String anchorBefore,
                                       String anchorAfter) {
        String before = anchorBefore != null ? anchorBefore : "";
        String after = anchorAfter != null ? anchorAfter : "";

        // Prefer the exact text starting at the saved bookmark. This keeps the
        // bookmark tied to the same character/passage even when font size,
        // wrapping width, boundary offsets, or line spacing change.
        if (after.length() >= 8) {
            int bestIndex = -1;
            int bestScore = Integer.MAX_VALUE;
            int searchFrom = 0;
            while (searchFrom <= content.length()) {
                int idx = content.indexOf(after, searchFrom);
                if (idx < 0) break;

                int score = Math.abs(idx - fallbackLocalPosition);
                if (!before.isEmpty()) {
                    int beforeStart = Math.max(0, idx - before.length());
                    String actualBefore = content.substring(beforeStart, idx);
                    if (actualBefore.equals(before)) {
                        score -= 1_000_000;
                    } else if (!actualBefore.endsWith(lastChars(before, Math.min(24, before.length())))) {
                        score += 250_000;
                    }
                }

                if (score < bestScore) {
                    bestScore = score;
                    bestIndex = idx;
                }
                searchFrom = idx + Math.max(1, after.length());
            }
            if (bestIndex >= 0) return bestIndex;
        }

        // If the text after the bookmark cannot be found, fall back to the
        // preceding anchor and restore immediately after it. This is useful if
        // the file was slightly edited at the bookmarked text.
        if (before.length() >= 8) {
            int bestIndex = -1;
            int bestScore = Integer.MAX_VALUE;
            int searchFrom = 0;
            while (searchFrom <= content.length()) {
                int idx = content.indexOf(before, searchFrom);
                if (idx < 0) break;
                int candidate = Math.min(content.length(), idx + before.length());
                int score = Math.abs(candidate - fallbackLocalPosition);
                if (score < bestScore) {
                    bestScore = score;
                    bestIndex = candidate;
                }
                searchFrom = idx + Math.max(1, before.length());
            }
            if (bestIndex >= 0) return bestIndex;
        }

        return -1;
    }

    private String lastChars(String value, int count) {
        if (value == null || value.isEmpty() || count <= 0) return "";
        int start = Math.max(0, value.length() - count);
        return value.substring(start);
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
        if (largeTextEstimateActive) {
            return Math.max(1, largeTextPartitionStartLine + lines - 1);
        }
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
        if (toolbarVisible && bottomBar != null) {
            bottomBar.bringToFront();
            bottomBar.post(bottomBar::bringToFront);
        }
        if (readerPageStatus != null) readerPageStatus.setVisibility(View.VISIBLE);
        updateReaderFileTitleVisibility();
        updateBottomMenuBackground();
        // On e-ink readers, synchronous insets/navigation recalculation can make
        // the bottom menu feel like the app is waiting. Show the menu first, then
        // settle system-bar geometry on the next loop.
        handler.post(() -> {
            if (activityDestroyed) return;
            if (readerRoot != null) ViewCompat.requestApplyInsets(readerRoot);
            updateNavigationBarForBottomMenu();
        });
        handler.postDelayed(this::updateNavigationBarForBottomMenu, 60);
    }

    // --- Hardware page-turn keys ---

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (handleReaderPageTurnKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Fallback for devices that route hardware keys through onKeyDown() instead
        // of dispatchKeyEvent(). dispatchKeyEvent() normally consumes these first.
        if (handleReaderPageTurnKey(event)) return true;
        return super.onKeyDown(keyCode, event);
    }

    private boolean handleReaderPageTurnKey(KeyEvent event) {
        if (event == null || prefs == null || !prefs.getVolumeKeyScroll()) return false;

        int direction = pageTurnDirectionForKey(event.getKeyCode());
        if (direction == 0) return false;

        int action = event.getAction();
        if (action == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) {
                pageBy(direction);
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

        int charPos = getBookmarkSaveCharPosition();
        int lineNum = Math.max(1, countLinesUntilChar(charPos));
        String excerpt = getExcerpt(charPos);
        String anchorBefore = getAnchorTextBefore(charPos);
        String anchorAfter = getAnchorTextAfter(charPos);
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
                b.setAnchorTextBefore(anchorBefore);
                b.setAnchorTextAfter(anchorAfter);
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
        bookmark.setAnchorTextBefore(anchorBefore);
        bookmark.setAnchorTextAfter(anchorAfter);
        bookmark.setEndPosition(bookmarkEndPosition);
        bookmark.setPageNumber(getDisplayedCurrentPageNumber());
        bookmark.setTotalPages(getDisplayedTotalPageCount());
        // No label prompt here. Original behavior is excerpt/position based.
        // Optional memo editing remains available by long-pressing a bookmark.
        bookmarkManager.addBookmark(bookmark);
        Toast.makeText(this, getString(R.string.bookmark_saved), Toast.LENGTH_SHORT).show();

        if (afterSave != null) afterSave.run();
    }

    private void jumpToBookmark(Bookmark bookmark) {
        if (bookmark == null) return;

        // Do not resolve an off-partition bookmark against the currently loaded
        // partition.  The current partition may not contain the saved anchor text,
        // or it may contain only lookahead text owned by the next partition.  In
        // that case, reload the partition that owns the saved global character
        // position first, then resolve the anchor inside that partition.
        if (largeTextEstimateActive
                && !isAbsoluteCharPositionInCurrentLargeTextBody(bookmark.getCharPosition())) {
            jumpToAbsoluteCharPosition(
                    bookmark.getCharPosition(),
                    bookmark.getPageNumber(),
                    bookmark.getTotalPages(),
                    bookmark.getAnchorTextBefore(),
                    bookmark.getAnchorTextAfter(),
                    true);
            return;
        }

        int resolvedPosition = resolveAnchoredAbsolutePosition(
                fileContent,
                largeTextEstimateActive ? largeTextPreviewBaseCharOffset : 0,
                bookmark.getCharPosition(),
                bookmark.getAnchorTextBefore(),
                bookmark.getAnchorTextAfter());

        boolean cacheStillMatches = Math.abs(resolvedPosition - bookmark.getCharPosition()) <= 3;
        jumpToAbsoluteCharPosition(
                resolvedPosition,
                cacheStillMatches ? bookmark.getPageNumber() : 0,
                cacheStillMatches ? bookmark.getTotalPages() : 0,
                bookmark.getAnchorTextBefore(),
                bookmark.getAnchorTextAfter(),
                true);
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
        TextView hintButton = new TextView(this);
        hintButton.setText(getString(R.string.bookmark_hints_show));
        hintButton.setContentDescription(getString(R.string.bookmark_hints_show));
        hintButton.setTextColor(sub);
        hintButton.setTextSize(12f);
        hintButton.setTypeface(Typeface.DEFAULT_BOLD);
        hintButton.setGravity(Gravity.CENTER);
        hintButton.setPadding(0, dpToPx(6), 0, dpToPx(4));
        hintButton.setOnClickListener(v -> showBookmarkHintsPopup());
        box.addView(hintButton, new LinearLayout.LayoutParams(
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
        saveLp.setMargins(0, dpToPx(8), 0, dpToPx(2));

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
        box.addView(saveButton, saveLp);

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

        // Populate bookmark contents before show() so the bottom-positioned window
        // is measured once at its final height. Showing a temporary loading state
        // first and then replacing it with the real two-line status makes the
        // bookmark window visibly hard-drop on some devices.
        refresh.run();

        android.app.Dialog dialog = createPositionedReaderDialog(dialogPanel, bg,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, TXT_BOOKMARK_POPUP_Y_DP, 14, 460, false);
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
                    jumpToBookmark(b);
                    dialog.dismiss();
                    return;
                }

                File targetFile = new File(b.getFilePath());
                if (!targetFile.exists()) {
                    Toast.makeText(ReaderActivity.this,
                            getString(R.string.file_not_found_prefix) + b.getFilePath(),
                            Toast.LENGTH_SHORT).show();
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
                    intent.putExtra(EXTRA_JUMP_ANCHOR_BEFORE, b.getAnchorTextBefore());
                    intent.putExtra(EXTRA_JUMP_ANCHOR_AFTER, b.getAnchorTextAfter());
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


    private void showBookmarkHintsPopup() {
        final int bg = readerDialogBgColor();
        final int fg = readerDialogTextColor(bg);
        final int sub = readerDialogSubTextColor(bg);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Color.TRANSPARENT);
        panel.setPadding(0, 0, 0, 0);

        TextView title = makeReaderDialogTitle(getString(R.string.bookmark_hints_show), bg, fg);
        title.setGravity(Gravity.CENTER);
        title.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        panel.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView message = makeReaderDialogLabel(getString(R.string.bookmark_folder_hint), sub, 13f);
        message.setGravity(Gravity.START);
        message.setSingleLine(false);
        message.setLineSpacing(0f, 1.12f);
        message.setPadding(dpToPx(18), dpToPx(2), dpToPx(18), dpToPx(14));
        panel.addView(message, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        actionRow.setBackground(positionedActionPanelBackground(
                dialogActionPanelFillColor(bg),
                dialogActionPanelLineColor(bg)));
        actionRow.setPadding(dpToPx(18), 0, dpToPx(18), 0);

        TextView ok = makeReaderDialogActionText(getString(R.string.ok), fg,
                Gravity.CENTER_VERTICAL | Gravity.END);
        actionRow.addView(ok, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));
        panel.addView(actionRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(52)));

        android.app.Dialog dialog = createNarrowPositionedReaderDialog(
                panel,
                bg,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL,
                TXT_BOOKMARK_HINT_POPUP_Y_DP,
                0.74f,
                360,
                false);
        ok.setOnClickListener(v -> dialog.dismiss());
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
                TXT_TOOLBAR_POPUP_Y_DP,
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
            if (!t.isBuiltIn()) {
                row.setOnLongClickListener(v -> {
                    if (ref[0] != null) ref[0].dismiss();
                    showReaderCustomThemeActionsDialog(t);
                    return true;
                });
            }
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

        prepareReaderAlertDialogWindowNoJump(dialog, true);
        dialog.setOnShowListener(d -> {
            styleReaderDialogWindow(dialog, bg, fg, sub);
            prepareReaderAlertDialogWindowNoJump(dialog, false);
            if (dialog.getWindow() != null) {
                View decor = dialog.getWindow().getDecorView();
                decor.post(() -> decor.setAlpha(1f));
            }
        });
        dialog.show();
        prepareReaderAlertDialogWindowNoJump(dialog, false);
    }

    private void showReaderCustomThemeActionsDialog(@NonNull Theme theme) {
        final int bg = readerDialogBgColor();
        final int fg = readerDialogTextColor(bg);
        final int sub = readerDialogSubTextColor(bg);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setBackgroundColor(Color.TRANSPARENT);
        int pad = dpToPx(14);
        list.setPadding(pad, dpToPx(8), pad, dpToPx(8));

        TextView message = new TextView(this);
        message.setText(getString(R.string.custom_theme_options_message));
        message.setTextColor(sub);
        message.setTextSize(14f);
        message.setGravity(Gravity.CENTER);
        message.setPadding(dpToPx(8), 0, dpToPx(8), dpToPx(12));
        list.addView(message, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView edit = makeReaderCenteredActionButton(getString(R.string.edit_theme), fg);
        TextView delete = makeReaderCenteredActionButton(getString(R.string.delete_theme), fg);
        TextView cancel = makeReaderCenteredActionButton(getString(R.string.cancel), sub);
        list.addView(edit);
        list.addView(delete);
        list.addView(cancel);

        TextView title = makeReaderDialogTitle(theme.getName(), bg, fg);
        final AlertDialog[] ref = new AlertDialog[1];
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setCustomTitle(title)
                .setView(list)
                .create();
        ref[0] = dialog;

        edit.setOnClickListener(v -> {
            if (ref[0] != null) ref[0].dismiss();
            Intent editIntent = new Intent(this, ThemeEditorActivity.class);
            editIntent.putExtra(ThemeEditorActivity.EXTRA_THEME_ID, theme.getId());
            startActivity(editIntent);
        });
        delete.setOnClickListener(v -> {
            if (ref[0] != null) ref[0].dismiss();
            showReaderDeleteCustomThemeDialog(theme);
        });
        cancel.setOnClickListener(v -> {
            if (ref[0] != null) ref[0].dismiss();
        });

        prepareReaderCustomThemeDialogWindowNoJump(dialog, true);
        dialog.setOnShowListener(d -> {
            styleReaderDialogWindow(dialog, bg, fg, sub);
            prepareReaderCustomThemeDialogWindowNoJump(dialog, false);
            if (dialog.getWindow() != null) {
                View decor = dialog.getWindow().getDecorView();
                decor.post(() -> decor.setAlpha(1f));
            }
        });
        dialog.show();
        prepareReaderCustomThemeDialogWindowNoJump(dialog, false);
    }

    private void showReaderDeleteCustomThemeDialog(@NonNull Theme theme) {
        final int bg = readerDialogBgColor();
        final int fg = readerDialogTextColor(bg);
        final int sub = readerDialogSubTextColor(bg);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setBackgroundColor(Color.TRANSPARENT);
        int pad = dpToPx(14);
        list.setPadding(pad, dpToPx(8), pad, dpToPx(8));

        TextView message = new TextView(this);
        message.setText(getString(R.string.delete_theme_confirm, theme.getName()));
        message.setTextColor(sub);
        message.setTextSize(14f);
        message.setGravity(Gravity.CENTER);
        message.setPadding(dpToPx(8), 0, dpToPx(8), dpToPx(12));
        list.addView(message, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView delete = makeReaderCenteredActionButton(getString(R.string.delete), fg);
        TextView cancel = makeReaderCenteredActionButton(getString(R.string.cancel), sub);
        list.addView(delete);
        list.addView(cancel);

        TextView title = makeReaderDialogTitle(getString(R.string.delete_theme), bg, fg);
        final AlertDialog[] ref = new AlertDialog[1];
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setCustomTitle(title)
                .setView(list)
                .create();
        ref[0] = dialog;

        delete.setOnClickListener(v -> {
            if (ref[0] != null) ref[0].dismiss();
            themeManager.deleteCustomTheme(theme.getId());
            themeManager.reloadFromStorage();
            applyTheme();
            Toast.makeText(this, getString(R.string.theme_deleted), Toast.LENGTH_SHORT).show();
        });
        cancel.setOnClickListener(v -> {
            if (ref[0] != null) ref[0].dismiss();
        });

        prepareReaderCustomThemeDialogWindowNoJump(dialog, true);
        dialog.setOnShowListener(d -> {
            styleReaderDialogWindow(dialog, bg, fg, sub);
            prepareReaderCustomThemeDialogWindowNoJump(dialog, false);
            if (dialog.getWindow() != null) {
                View decor = dialog.getWindow().getDecorView();
                decor.post(() -> decor.setAlpha(1f));
            }
        });
        dialog.show();
        prepareReaderCustomThemeDialogWindowNoJump(dialog, false);
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
        // Match the thin outer boundary used by PDF/EPUB/Word dialog frames.
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

        ScrollView adaptiveScroll = wrapAdaptiveDialogContent(content, outerFrame);

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
            lp.y = dpToPx(TXT_TOOLBAR_POPUP_Y_DP);
            lp.dimAmount = 0.16f;
            window.setAttributes(lp);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int cappedWidth = Math.min(Math.round(screenWidth * 0.85f), dpToPx(maxWidthDp));
        applyAdaptiveDialogMaxHeight(dialog, adaptiveScroll, Math.max(dpToPx(220), cappedWidth));
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

        prepareReaderAlertDialogWindowNoJump(dialog, true);
        dialog.setOnShowListener(d -> {
            styleReaderDialogWindow(dialog, bg, fg, sub);
            prepareReaderAlertDialogWindowNoJump(dialog, false);
            if (dialog.getWindow() != null) {
                View decor = dialog.getWindow().getDecorView();
                decor.post(() -> decor.setAlpha(1f));
            }
        });
        dialog.show();
        prepareReaderAlertDialogWindowNoJump(dialog, false);
    }

    private void showTextSearch() {
        final int bg = readerDialogBgColor();
        final int fg = readerDialogTextColor(bg);
        final int sub = readerDialogSubTextColor(bg);

        FrameLayout titleBox = new FrameLayout(this);
        titleBox.setPadding(dpToPx(22), dpToPx(18), dpToPx(22), dpToPx(8));
        titleBox.setBackgroundColor(Color.TRANSPARENT);

        TextView title = new TextView(this);
        title.setText(getString(R.string.find_in_text));
        title.setTextColor(fg);
        title.setTextSize(20f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
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
                TXT_TOOLBAR_POPUP_Y_DP,
                0.85f,
                460,
                true);

        // Keep the find dialog at one stable lower position. Do not move it in response
        // to keyboard visibility; the previous delayed restore caused bounce.
        updatePositionedReaderDialogYOffset(dialog, TXT_TOOLBAR_POPUP_Y_DP);

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
            // current top line as the next search base. Keep a small visual context margin
            // only for search; bookmark and saved-position restore use exact top alignment.
            scrollToCharPositionWithContext(idx);
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
                TXT_TOOLBAR_POPUP_Y_DP,
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

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        cacheLoadedTextSnapshot();
        outState.putBoolean(STATE_RESTORE_FROM_MEMORY, true);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopAutoPageTurn(false);
        saveReadingState();
        cacheLoadedTextSnapshot();
    }

    @Override protected void onDestroy() {
        activityDestroyed = true;
        loadGeneration.incrementAndGet();
        largeTextExactPageIndexGeneration.incrementAndGet();
        largeTextPartitionSwitchGeneration.incrementAndGet();
        handler.removeCallbacksAndMessages(null);
        if (viewerBackToast != null) {
            viewerBackToast.cancel();
            viewerBackToast = null;
        }
        saveReadingState();
        cacheLoadedTextSnapshot();
        if (notificationHelper != null) notificationHelper.dismiss();
        releaseReaderMemory();
        executor.shutdownNow();
        largeTextPartitionExecutor.shutdownNow();
        if (isFinishing()) {
            clearLoadedTextSnapshot();
        }
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
        clearLargeTextPartitionSwitchPending();
        clearLargeTextQueuedPageDelta();
        resetLargeTextPageDirectionTracking();
        hugeTextPreviewOnly = false;
        pendingLargeTextRestorePosition = -1;
        pendingLargeTextCachedDisplayPage = 0;
        pendingLargeTextCachedTotalPages = 0;
        largeTextPreviewBaseCharOffset = 0;
        largeTextEstimatedBasePageOffset = 0;
        largeTextEstimatedTotalChars = 0;
        largeTextPartitionStartByte = 0L;
        largeTextPartitionEndByte = 0L;
        largeTextFileByteLength = 0L;
        largeTextEstimatedBytesPerChar = 1f;
        largeTextPartitionBodyCharCount = 0;
        largeTextPartitionStartLine = 1;
        largeTextPartitionEndLine = 1;
        largeTextTotalLogicalLines = 1;
        loadingWindowPartitionJumpGeneration = -1;
        clearLargeTextPartitionCache();
        resetLargeTextExactPageIndex();

        if (readerView != null) {
            readerView.setLargeTextPartitionMode(false);
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
        else if (id == R.id.action_font_reset) { resetFontSize(); return true; }
        else if (id == R.id.action_file_info) { showFileInfoDialog(); return true; }
        return super.onOptionsItemSelected(item);
    }

    private void changeFontSize(float delta) {
        float newSize = Math.max(8f, Math.min(48f, prefs.getFontSize() + delta));
        prefs.setFontSize(newSize);
        applyPreferences();
        updatePositionLabel();
    }

    private void resetFontSize() {
        prefs.setFontSize(PrefsManager.DEFAULT_FONT_SIZE);
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
