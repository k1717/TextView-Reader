package com.simpletext.reader;

import android.Manifest;
import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Environment;
import android.provider.Settings;
import android.text.Editable;
import android.text.Layout;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.simpletext.reader.adapter.DrawerEntryAdapter;
import com.simpletext.reader.adapter.FileAdapter;
import com.simpletext.reader.model.DrawerEntry;
import com.simpletext.reader.model.ReaderState;
import com.simpletext.reader.util.BookmarkManager;
import com.simpletext.reader.util.EdgeToEdgeUtil;
import com.simpletext.reader.util.FileUtils;
import com.simpletext.reader.util.PrefsManager;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Main entry. Two-mode UI:
 *
 * - HOME mode: Recent files only. The default state on launch.
 * - BROWSE mode: File browser at a particular folder, picked from
 *                the navigation drawer (Internal storage, External SD, Downloads, /storage).
 *
 * The drawer slides in from the left edge and contains the storage shortcuts at the
 * top and the toolbar's old action buttons (Open File, Bookmarks, Settings) at the bottom.
 */
public class MainActivity extends AppCompatActivity implements FileAdapter.OnFileClickListener {

    public static final String EXTRA_RETURN_TO_VIEWER = "return_to_viewer";
    public static final String EXTRA_START_DIRECTORY = "start_directory";
    private static final int PERMISSION_REQUEST_CODE = 100;

    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle drawerToggle;
    private RecyclerView fileRecyclerView;
    private FileAdapter fileAdapter;
    private TextView pathText;
    private TextView emptyText;
    private View recentSection;
    private View browserSection;
    private RecyclerView recentRecyclerView;
    private TextView recentEmptyText;
    private TextView recentClearAllButton;
    private RecyclerView drawerFixedList;
    private RecyclerView drawerStorageList;
    private RecyclerView drawerShortcutList;
    private View drawerRecentFoldersHeader;
    private TextView drawerRecentFoldersTitle;
    private TextView drawerRecentFoldersClearButton;
    private DrawerEntryAdapter drawerFixedEntryAdapter;
    private DrawerEntryAdapter drawerShortcutEntryAdapter;
    private DrawerEntryAdapter drawerEntryAdapter;
    private FileAdapter recentAdapter;
    private EditText fileSearchInput;
    private TextView fileSearchClearButton;
    private ProgressBar fileSearchProgress;
    private ImageButton fileSortButton;
    private TextView filterAllChip;
    private TextView filterGeneralChip;
    private TextView filterPdfChip;
    private TextView filterEpubChip;
    private TextView filterWordChip;
    private static final int FILTER_ALL = 0;
    private static final int FILTER_GENERAL = 1;
    private static final int FILTER_PDF = 2;
    private static final int FILTER_EPUB = 3;
    private static final int FILTER_WORD = 4;
    private int activeFileFilter = FILTER_ALL;
    private final ExecutorService fileSearchExecutor = Executors.newSingleThreadExecutor();
    private final Handler fileSearchHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingFileSearchRunnable;
    private volatile boolean activityDestroyed = false;
    private final AtomicInteger fileSearchGeneration = new AtomicInteger(0);
    private boolean searchMode = false;
    private boolean searchReturnToHome = true;
    private File searchReturnDirectory = null;

    private float drawerSwipeStartX;
    private float drawerSwipeStartY;
    private boolean drawerSwipeTracking = false;
    private boolean drawerSwipeOpened = false;
    private int drawerSwipeTouchSlop;

    private File currentDirectory;
    /** True when the home (recent) view is active; false when browsing a folder. */
    private boolean homeMode = true;
    private PrefsManager prefs;
    private BookmarkManager bookmarkManager;
    private boolean lockChecked = false;
    private boolean returnToViewerMode = false;
    private File initialBrowseDirectory;
    private long lastBackPressedTime = 0L;
    /** Toolbar reference cached so onResume can re-apply the theme cheaply. */
    private Toolbar mainToolbar;
    private DrawerEntry pendingDrawerNavigationEntry;
    private boolean drawerNavigationPending = false;
    /**
     * Cached SD card detection. The previous code re-scanned /storage and called
     * getExternalFilesDirs() once per recent-folder candidate (up to 50 times per
     * onResume), which was the dominant slowdown after the drawer redesign.
     */
    private List<File> cachedSdCards;

    private final ActivityResultLauncher<String[]> openFileLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    openFileFromUri(uri);
                }
            });

    private final ActivityResultLauncher<Intent> manageStorageLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                    onPermissionsGranted();
                }
            });

    private final ActivityResultLauncher<Intent> lockLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    lockChecked = true;
                    checkPermissionsAndInit();
                } else {
                    finishAffinity();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = PrefsManager.getInstance(this);
        returnToViewerMode = getIntent().getBooleanExtra(EXTRA_RETURN_TO_VIEWER, false);
        initialBrowseDirectory = resolveInitialBrowseDirectory(getIntent());
        prefs.applyLanguage(prefs.getLanguageMode());
        prefs.applyDarkMode(prefs.getDarkMode());
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        drawerSwipeTouchSlop = ViewConfiguration.get(this).getScaledTouchSlop();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { handleMainBackPressed(); }
        });

        EdgeToEdgeUtil.applyStandardInsets(this, findViewById(R.id.main_root),
                findViewById(R.id.main_appbar), findViewById(R.id.file_search_bar));

        Toolbar toolbar = findViewById(R.id.toolbar);
        mainToolbar = toolbar;
        setSupportActionBar(toolbar);

        drawerLayout = findViewById(R.id.drawer_layout);
        drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar,
                R.string.drawer_open, R.string.drawer_close);
        drawerLayout.addDrawerListener(drawerToggle);
        drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerClosed(@NonNull View drawerView) {
                consumePendingDrawerNavigation();
            }
        });
        drawerToggle.syncState();
        // Allow swipe from the left edge to open immediately, even before the hamburger
        // has ever opened the drawer.
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.START);
        installReliableDrawerEdgeDrag();
        installToolbarMenuButton(toolbar);

        fileRecyclerView = findViewById(R.id.file_list);
        pathText = findViewById(R.id.current_path);
        emptyText = findViewById(R.id.empty_text);
        recentSection = findViewById(R.id.recent_section);
        browserSection = findViewById(R.id.main_content_container);
        recentRecyclerView = findViewById(R.id.recent_list);
        recentEmptyText = findViewById(R.id.recent_empty_text);
        recentClearAllButton = findViewById(R.id.recent_clear_all);

        fileAdapter = new FileAdapter();
        fileAdapter.setListener(this);
        fileAdapter.setSortMode(prefs.getSortMode());
        fileRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        fileRecyclerView.setAdapter(fileAdapter);

        recentAdapter = new FileAdapter();
        recentAdapter.setListener(this);
        recentAdapter.setSortEnabled(false);
        recentRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        recentRecyclerView.setAdapter(recentAdapter);

        bookmarkManager = BookmarkManager.getInstance(this);
        setupRecentHeaderActions();

        setupDrawerStorageList();
        setupDrawerBottomActions();
        setupFileSearch();

        applyMainReadableTheme(toolbar);

        // Handle external intent
        Intent intent = getIntent();
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri uri = intent.getData();
            if (uri != null) { openFileFromUri(uri); return; }
        }

        // Check lock
        if (prefs.isLockEnabled() && !lockChecked) {
            Intent lockIntent = new Intent(this, LockActivity.class);
            lockIntent.putExtra(LockActivity.EXTRA_MODE, LockActivity.MODE_UNLOCK);
            lockLauncher.launch(lockIntent);
        } else {
            checkPermissionsAndInit();
        }

        // Default view = HOME, unless a viewer asked to reopen the file browser
        // at the current file's folder.
        showInitialMainMode();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (handleGlobalRightDragForDrawer(event)) {
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

    private boolean handleGlobalRightDragForDrawer(@NonNull MotionEvent event) {
        if (drawerLayout == null || drawerLayout.isDrawerOpen(GravityCompat.START)) {
            resetDrawerSwipeState();
            return false;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                drawerSwipeStartX = event.getX();
                drawerSwipeStartY = event.getY();
                drawerSwipeOpened = false;
                drawerSwipeTracking = !isTouchInsideView(fileSearchInput, event);
                return false;

            case MotionEvent.ACTION_MOVE:
                if (!drawerSwipeTracking) return false;
                float dx = event.getX() - drawerSwipeStartX;
                float dy = event.getY() - drawerSwipeStartY;
                float absDx = Math.abs(dx);
                float absDy = Math.abs(dy);

                if (absDy > drawerSwipeTouchSlop * 2f && absDy > absDx * 1.15f) {
                    drawerSwipeTracking = false;
                    return false;
                }

                int threshold = Math.max(dpToPx(54), drawerSwipeTouchSlop * 4);
                if (dx > threshold && absDx > absDy * 1.65f) {
                    drawerSwipeOpened = true;
                    drawerSwipeTracking = false;
                    drawerLayout.openDrawer(GravityCompat.START);
                    return true;
                }
                return false;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                boolean consume = drawerSwipeOpened;
                resetDrawerSwipeState();
                return consume;

            default:
                return false;
        }
    }

    private boolean isTouchInsideView(View view, @NonNull MotionEvent event) {
        if (view == null || view.getVisibility() != View.VISIBLE) return false;
        int[] loc = new int[2];
        view.getLocationOnScreen(loc);
        float rawX = event.getRawX();
        float rawY = event.getRawY();
        return rawX >= loc[0] && rawX <= loc[0] + view.getWidth()
                && rawY >= loc[1] && rawY <= loc[1] + view.getHeight();
    }

    private void resetDrawerSwipeState() {
        drawerSwipeTracking = false;
        drawerSwipeOpened = false;
    }

    @Override
    public void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        if (drawerToggle != null) {
            drawerToggle.syncState();
            installToolbarMenuButton(findViewById(R.id.toolbar));
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (drawerToggle != null) {
            drawerToggle.onConfigurationChanged(newConfig);
            installToolbarMenuButton(findViewById(R.id.toolbar));
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        activityDestroyed = false;
        if (drawerLayout != null) {
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.START);
            updateDrawerGestureExclusion();
        }

        if (mainToolbar != null) applyMainReadableTheme(mainToolbar);

        loadRecentFiles();
        rebuildDrawerStorageEntries();
        if (!homeMode && !searchMode && currentDirectory != null) loadDirectory(currentDirectory);
    }

    @Override
    protected void onDestroy() {
        activityDestroyed = true;
        pendingDrawerNavigationEntry = null;
        drawerNavigationPending = false;
        clearSearchDebounce();
        fileSearchHandler.removeCallbacksAndMessages(null);
        fileSearchGeneration.incrementAndGet();

        if (fileAdapter != null) fileAdapter.release();
        if (recentAdapter != null) recentAdapter.release();
        if (drawerFixedEntryAdapter != null) {
            drawerFixedEntryAdapter.setListener(null);
            drawerFixedEntryAdapter.setLongClickListener(null);
        }
        if (drawerEntryAdapter != null) {
            drawerEntryAdapter.setListener(null);
            drawerEntryAdapter.setLongClickListener(null);
        }

        if (fileRecyclerView != null) fileRecyclerView.setAdapter(null);
        if (recentRecyclerView != null) recentRecyclerView.setAdapter(null);
        if (drawerFixedList != null) drawerFixedList.setAdapter(null);
        if (drawerStorageList != null) drawerStorageList.setAdapter(null);

        fileSearchExecutor.shutdownNow();
        super.onDestroy();
    }

    private void setupRecentHeaderActions() {
        if (recentClearAllButton != null) {
            recentClearAllButton.setOnClickListener(v -> showClearAllRecentFilesDialog());
        }
    }

    // -------------------------------------------------------------------------
    // Bottom file search
    // -------------------------------------------------------------------------

    private void setupFileSearch() {
        fileSearchInput = findViewById(R.id.file_search_input);
        fileSearchClearButton = findViewById(R.id.file_search_clear_button);
        fileSearchProgress = findViewById(R.id.file_search_progress);
        fileSortButton = findViewById(R.id.file_sort_button);
        filterAllChip = findViewById(R.id.filter_all);
        filterGeneralChip = findViewById(R.id.filter_general);
        filterPdfChip = findViewById(R.id.filter_pdf);
        filterEpubChip = findViewById(R.id.filter_epub);
        filterWordChip = findViewById(R.id.filter_word);

        setupFileTypeChip(filterAllChip, FILTER_ALL);
        setupFileTypeChip(filterGeneralChip, FILTER_GENERAL);
        setupFileTypeChip(filterPdfChip, FILTER_PDF);
        setupFileTypeChip(filterEpubChip, FILTER_EPUB);
        setupFileTypeChip(filterWordChip, FILTER_WORD);
        updateFileTypeChips();

        if (fileSearchInput != null) {
            fileSearchInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    scheduleLiveFileSearch();
                }
                @Override public void afterTextChanged(Editable s) {}
            });
            fileSearchInput.setOnEditorActionListener((v, actionId, event) -> {
                boolean enterKey = event != null
                        && event.getAction() == android.view.KeyEvent.ACTION_UP
                        && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER;
                if (actionId == EditorInfo.IME_ACTION_SEARCH || enterKey) {
                    runLiveFileSearchNow();
                    return true;
                }
                return false;
            });
        }

        if (fileSearchClearButton != null) {
            fileSearchClearButton.setOnClickListener(v -> {
                if (fileSearchInput != null) fileSearchInput.setText("");
                if (searchMode) restorePreSearchLocation();
            });
            fileSearchClearButton.setVisibility(View.GONE);
        }

        if (fileSortButton != null) {
            fileSortButton.setOnClickListener(v -> showSortDialog());
        }
    }

    private void scrollListToTop(RecyclerView recyclerView) {
        if (recyclerView == null) return;

        recyclerView.stopScroll();
        recyclerView.post(() -> {
            RecyclerView.LayoutManager lm = recyclerView.getLayoutManager();
            if (lm instanceof LinearLayoutManager) {
                ((LinearLayoutManager) lm).scrollToPositionWithOffset(0, 0);
            } else {
                recyclerView.scrollToPosition(0);
            }
        });
    }

    private void updateFileSearchClearButtonVisibility() {
        if (fileSearchClearButton == null) return;

        boolean hasTypedQuery = fileSearchInput != null
                && fileSearchInput.getText() != null
                && fileSearchInput.getText().toString().trim().length() > 0;

        // The X next to the sort button is only for clearing typed search text.
        // File-type filters already reset through the "전체" chip, so selecting
        // 일반/PDF/EPUB/Word with an empty search box must not show this X.
        fileSearchClearButton.setVisibility(hasTypedQuery ? View.VISIBLE : View.GONE);
    }

    private void setupFileTypeChip(TextView chip, int filter) {
        if (chip == null) return;
        chip.setOnClickListener(v -> {
            String query = fileSearchInput != null
                    ? fileSearchInput.getText().toString().trim()
                    : "";

            // On the Recent page, file-type chips should filter the recent-read list.
            // They must not start an all-storage scan from Internal storage.
            if (query.isEmpty() && (homeMode || (searchMode && searchReturnToHome))) {
                activeFileFilter = filter;
                clearSearchDebounce();
                fileSearchGeneration.incrementAndGet();
                searchMode = false;
                homeMode = true;
                searchReturnToHome = true;
                searchReturnDirectory = null;
                if (fileSearchProgress != null) fileSearchProgress.setVisibility(View.GONE);
                updateFileSearchClearButtonVisibility();
                if (recentSection != null) recentSection.setVisibility(View.VISIBLE);
                if (browserSection != null) browserSection.setVisibility(View.GONE);
                if (pathText != null) pathText.setVisibility(View.GONE);
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle(R.string.app_name);
                }
                updateFileTypeChips();
                loadRecentFiles();
                invalidateOptionsMenu();
                return;
            }

            // Returning from a type-filter-only result to "All" should restore the
            // folder/storage location the filter started from, not the Recent page.
            // Do this directly here instead of routing through the generic search path.
            if (filter == FILTER_ALL && query.isEmpty()) {
                activeFileFilter = FILTER_ALL;
                updateFileTypeChips();
                restoreAllFilterLocation();
                return;
            }

            captureFilterReturnLocationIfNeeded();
            activeFileFilter = filter;
            updateFileTypeChips();
            runLiveFileSearchNow();
        });
    }

    private void captureFilterReturnLocationIfNeeded() {
        if (searchMode) return;
        boolean allStorage = homeMode || currentDirectory == null;
        searchReturnToHome = allStorage;
        searchReturnDirectory = allStorage ? null : currentDirectory;
    }

    private void restoreAllFilterLocation() {
        clearSearchDebounce();
        fileSearchGeneration.incrementAndGet();
        if (fileSearchProgress != null) fileSearchProgress.setVisibility(View.GONE);
        updateFileSearchClearButtonVisibility();

        File visibleFolder = currentDirectory;
        if (visibleFolder != null
                && visibleFolder.exists()
                && visibleFolder.isDirectory()
                && visibleFolder.canRead()
                && (!searchReturnToHome || searchMode || !homeMode)) {
            searchMode = false;
            homeMode = false;
            recentSection.setVisibility(View.GONE);
            browserSection.setVisibility(View.VISIBLE);
            pathText.setVisibility(View.VISIBLE);
            loadDirectory(visibleFolder);
            rebuildDrawerStorageEntries();
            invalidateOptionsMenu();
            return;
        }

        restorePreSearchLocation();
    }

    private void updateFileTypeChips() {
        styleFileTypeChip(filterAllChip, activeFileFilter == FILTER_ALL);
        styleFileTypeChip(filterGeneralChip, activeFileFilter == FILTER_GENERAL);
        styleFileTypeChip(filterPdfChip, activeFileFilter == FILTER_PDF);
        styleFileTypeChip(filterEpubChip, activeFileFilter == FILTER_EPUB);
        styleFileTypeChip(filterWordChip, activeFileFilter == FILTER_WORD);
    }

    private void resetFileFilterForNavigation() {
        activeFileFilter = FILTER_ALL;

        if (fileSearchInput != null && fileSearchInput.length() > 0) {
            fileSearchInput.setText("");
        }

        clearSearchDebounce();
        fileSearchGeneration.incrementAndGet();

        if (fileSearchProgress != null) {
            fileSearchProgress.setVisibility(View.GONE);
        }
        if (fileSearchClearButton != null) {
            fileSearchClearButton.setVisibility(View.GONE);
        }

        searchReturnToHome = true;
        searchReturnDirectory = null;
        updateFileTypeChips();
    }

    private void styleFileTypeChip(TextView chip, boolean selected) {
        if (chip == null) return;
        boolean dark = prefs == null || prefs.shouldUseDarkColors(this);
        int bg = selected
                ? (dark ? Color.rgb(72, 72, 72) : Color.rgb(32, 33, 36))
                : (dark ? Color.rgb(30, 30, 30) : Color.rgb(238, 238, 238));
        int stroke = selected
                ? (dark ? Color.rgb(220, 220, 220) : Color.rgb(32, 33, 36))
                : (dark ? Color.rgb(92, 92, 92) : Color.rgb(190, 190, 190));
        int fg = selected
                ? Color.WHITE
                : (dark ? Color.rgb(232, 234, 237) : Color.rgb(32, 33, 36));
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(bg);
        shape.setCornerRadius(dpToPx(15));
        shape.setStroke(Math.max(1, dpToPx(1)), stroke);
        chip.setBackground(shape);
        chip.setTextColor(fg);
    }

    private void scheduleLiveFileSearch() {
        if (activityDestroyed) return;
        clearSearchDebounce();
        pendingFileSearchRunnable = this::runLiveFileSearchNow;
        fileSearchHandler.postDelayed(pendingFileSearchRunnable, 180);
    }

    private void clearSearchDebounce() {
        if (pendingFileSearchRunnable != null) {
            fileSearchHandler.removeCallbacks(pendingFileSearchRunnable);
            pendingFileSearchRunnable = null;
        }
    }

    private void runLiveFileSearchNow() {
        clearSearchDebounce();
        if (activityDestroyed || fileSearchInput == null) return;
        String query = fileSearchInput.getText().toString().trim();

        // With no text, never start an expensive full-storage scan from the Recent page.
        // If a type chip is active on Recent, filter the recent-read list locally.
        if (query.isEmpty() && homeMode) {
            fileSearchGeneration.incrementAndGet();
            if (fileSearchProgress != null) fileSearchProgress.setVisibility(View.GONE);
            updateFileSearchClearButtonVisibility();
            if (activeFileFilter == FILTER_ALL) {
                if (searchMode) restorePreSearchLocation();
            } else {
                searchMode = false;
                loadRecentFiles();
            }
            return;
        }

        // With no text and no active type filter, do not start an expensive full scan.
        if (query.isEmpty() && activeFileFilter == FILTER_ALL) {
            fileSearchGeneration.incrementAndGet();
            if (fileSearchProgress != null) fileSearchProgress.setVisibility(View.GONE);
            updateFileSearchClearButtonVisibility();
            if (searchMode) restorePreSearchLocation();
            return;
        }

        boolean allStorage = homeMode || currentDirectory == null;
        if (!searchMode) {
            searchReturnToHome = allStorage;
            searchReturnDirectory = allStorage ? null : currentDirectory;
        }
        startFileSearch(query, allStorage);
    }

    private void restorePreSearchLocation() {
        fileSearchGeneration.incrementAndGet();
        searchMode = false;

        // Leaving a file-type-filter result returns to the normal folder/recent view.
        // The content becomes the full "All" list, so the chip state must also return
        // to All instead of leaving General/PDF/EPUB/Word visually selected.
        activeFileFilter = FILTER_ALL;
        updateFileTypeChips();

        File target = searchReturnDirectory;
        if (!searchReturnToHome
                && target != null
                && target.exists()
                && target.isDirectory()
                && target.canRead()) {
            showBrowseMode(target);
        } else {
            showHomeMode();
        }
    }

    private void startFileSearch(@NonNull String query, boolean allStorage) {
        final int generation = fileSearchGeneration.incrementAndGet();
        if (fileSearchProgress != null) fileSearchProgress.setVisibility(View.VISIBLE);
        if (fileSearchClearButton != null) {
            fileSearchClearButton.setEnabled(true);
        }
        updateFileSearchClearButtonVisibility();

        List<File> roots = buildSearchRoots(allStorage);
        final int filter = activeFileFilter;
        fileSearchExecutor.execute(() -> {
            if (activityDestroyed || generation != fileSearchGeneration.get()) return;
            List<File> results = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            String needle = query.toLowerCase(java.util.Locale.ROOT);
            boolean showHidden = prefs == null || prefs.getShowHiddenFiles();
            int[] visited = new int[]{0};

            for (File root : roots) {
                if (root == null || !root.exists() || !root.canRead()) continue;
                searchFilesRecursive(root, needle, filter, showHidden, seen, results, visited, generation, 0);
                if (generation != fileSearchGeneration.get() || results.size() >= 300 || visited[0] >= 12000) break;
            }

            runOnUiThread(() -> {
                if (activityDestroyed || generation != fileSearchGeneration.get()) return;
                if (fileSearchProgress != null) fileSearchProgress.setVisibility(View.GONE);
                showFileSearchResults(query, results);
            });
        });
    }

    private List<File> buildSearchRoots(boolean allStorage) {
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        if (!allStorage && !homeMode && currentDirectory != null && currentDirectory.exists()) {
            paths.add(currentDirectory.getAbsolutePath());
        } else {
            File internal = Environment.getExternalStorageDirectory();
            if (internal != null) paths.add(internal.getAbsolutePath());
            File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (downloads != null) paths.add(downloads.getAbsolutePath());
            for (File sd : detectExternalSdCards()) paths.add(sd.getAbsolutePath());
            if (prefs != null) paths.addAll(prefs.getRecentFolders(10));
        }

        List<File> roots = new ArrayList<>();
        for (String path : paths) {
            if (path == null || path.trim().isEmpty()) continue;
            File f = new File(path);
            if (f.exists() && f.canRead()) roots.add(f);
        }
        return roots;
    }

    private void searchFilesRecursive(@NonNull File dir,
                                      @NonNull String needle,
                                      int filter,
                                      boolean showHidden,
                                      @NonNull Set<String> seen,
                                      @NonNull List<File> results,
                                      @NonNull int[] visited,
                                      int generation,
                                      int depth) {
        if (generation != fileSearchGeneration.get() || depth > 16 || results.size() >= 300 || visited[0] >= 12000) return;
        File[] children = dir.listFiles();
        if (children == null) return;

        for (File child : children) {
            if (generation != fileSearchGeneration.get()) return;
            if (child == null) continue;
            visited[0]++;
            if (visited[0] >= 12000 || results.size() >= 300) return;
            String name = child.getName();
            if (!showHidden && name.startsWith(".")) continue;
            String path = child.getAbsolutePath();
            if (path.contains("/Android/data/") || path.contains("/Android/obb/")) continue;

            boolean nameMatch = needle.isEmpty() || name.toLowerCase(java.util.Locale.ROOT).contains(needle);
            boolean fileMatch = !child.isDirectory() && matchesActiveFileFilter(name, filter);
            boolean directoryMatch = child.isDirectory() && !needle.isEmpty()
                    && filter == FILTER_ALL
                    && nameMatch;
            if ((fileMatch || directoryMatch) && nameMatch && seen.add(path)) {
                results.add(child);
                if (results.size() >= 300) return;
            }
            if (child.isDirectory() && child.canRead()) {
                searchFilesRecursive(child, needle, filter, showHidden, seen, results, visited, generation, depth + 1);
            }
        }
    }


    private String filterLabelFor(int filter) {
        switch (filter) {
            case FILTER_GENERAL: return getString(R.string.file_filter_general);
            case FILTER_PDF: return getString(R.string.file_filter_pdf);
            case FILTER_EPUB: return getString(R.string.file_filter_epub);
            case FILTER_WORD: return getString(R.string.file_filter_word);
            case FILTER_ALL:
            default: return getString(R.string.file_filter_all);
        }
    }


    private boolean matchesActiveFileFilter(@NonNull String name, int filter) {
        switch (filter) {
            case FILTER_GENERAL:
                return FileUtils.isTextFile(name);
            case FILTER_PDF:
                return FileUtils.isPdfFile(name);
            case FILTER_EPUB:
                return FileUtils.isEpubFile(name);
            case FILTER_WORD:
                return FileUtils.isWordFile(name);
            case FILTER_ALL:
            default:
                return FileUtils.isSupportedReadableFile(name);
        }
    }

    private void showFileSearchResults(@NonNull String query, @NonNull List<File> results) {
        searchMode = true;
        homeMode = false;
        recentSection.setVisibility(View.GONE);
        browserSection.setVisibility(View.VISIBLE);
        pathText.setVisibility(View.VISIBLE);
        pathText.setText(getString(R.string.file_search_results_for, query.isEmpty() ? filterLabelFor(activeFileFilter) : query, results.size()));
        if (getSupportActionBar() != null) getSupportActionBar().setTitle(R.string.file_search);
        updateFileSearchClearButtonVisibility();

        fileAdapter.setFiles(results);
        scrollListToTop(fileRecyclerView);
        emptyText.setVisibility(results.isEmpty() ? View.VISIBLE : View.GONE);
        if (results.isEmpty()) emptyText.setText(getString(R.string.no_file_search_results));
        updateFileSearchClearButtonVisibility();
        invalidateOptionsMenu();
    }

    // -------------------------------------------------------------------------
    // Drawer setup
    // -------------------------------------------------------------------------

    private void setupDrawerStorageList() {
        drawerFixedList = findViewById(R.id.drawer_fixed_list);
        drawerStorageList = findViewById(R.id.drawer_storage_list);
        drawerShortcutList = findViewById(R.id.drawer_shortcut_list);
        drawerRecentFoldersHeader = findViewById(R.id.drawer_recent_folders_header);
        drawerRecentFoldersTitle = findViewById(R.id.drawer_recent_folders_title);
        drawerRecentFoldersClearButton = findViewById(R.id.drawer_recent_folders_clear);

        drawerFixedEntryAdapter = new DrawerEntryAdapter();
        drawerShortcutEntryAdapter = new DrawerEntryAdapter();
        drawerEntryAdapter = new DrawerEntryAdapter();

        if (drawerFixedList != null) {
            drawerFixedList.setLayoutManager(new LinearLayoutManager(this));
            drawerFixedList.setAdapter(drawerFixedEntryAdapter);
            drawerFixedList.setNestedScrollingEnabled(false);
        }
        if (drawerStorageList != null) {
            drawerStorageList.setLayoutManager(new LinearLayoutManager(this));
            drawerStorageList.setAdapter(drawerEntryAdapter);
        }
        if (drawerShortcutList != null) {
            drawerShortcutList.setLayoutManager(new LinearLayoutManager(this));
            drawerShortcutList.setAdapter(drawerShortcutEntryAdapter);
            drawerShortcutList.setNestedScrollingEnabled(true);
        }

        DrawerEntryAdapter.OnEntryClickListener clickListener = this::queueDrawerNavigation;
        DrawerEntryAdapter.OnEntryLongClickListener longClickListener = this::handleDrawerEntryLongClick;
        drawerFixedEntryAdapter.setListener(clickListener);
        drawerShortcutEntryAdapter.setListener(clickListener);
        drawerEntryAdapter.setListener(clickListener);
        drawerFixedEntryAdapter.setLongClickListener(longClickListener);
        drawerShortcutEntryAdapter.setLongClickListener(longClickListener);
        drawerEntryAdapter.setLongClickListener(longClickListener);

        if (drawerRecentFoldersClearButton != null) {
            drawerRecentFoldersClearButton.setOnClickListener(v -> showClearAllRecentFoldersDialog());
        }

        rebuildDrawerStorageEntries();
    }

    private void rebuildDrawerStorageEntries() {
        List<DrawerEntry> fixedEntries = new ArrayList<>();

        // Built-in storage shortcuts belong to the bottom-adjacent shortcut zone.
        fixedEntries.add(new DrawerEntry(
                DrawerEntry.ACTION_RECENT,
                R.drawable.ic_recent,
                getString(R.string.recent),
                null,
                null));

        File internal = Environment.getExternalStorageDirectory();
        if (internal != null) {
            fixedEntries.add(new DrawerEntry(
                    DrawerEntry.ACTION_INTERNAL,
                    R.drawable.ic_storage_internal,
                    getString(R.string.internal_storage),
                    internal.getAbsolutePath(),
                    internal.getAbsolutePath()));
        }

        for (File sd : detectExternalSdCards()) {
            fixedEntries.add(new DrawerEntry(
                    DrawerEntry.ACTION_EXTERNAL_SD,
                    R.drawable.ic_storage_sdcard,
                    getString(R.string.external_storage),
                    sd.getAbsolutePath(),
                    sd.getAbsolutePath()));
        }

        File downloads = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS);
        if (downloads != null) {
            fixedEntries.add(new DrawerEntry(
                    DrawerEntry.ACTION_DOWNLOADS,
                    R.drawable.ic_download,
                    getString(R.string.downloads),
                    downloads.getAbsolutePath(),
                    downloads.getAbsolutePath()));
        }

        // Bottom-adjacent shortcut zone: built-in storage shortcuts plus user-added folder shortcuts.
        // This zone is independent from the recent-folder list and is pinned directly above
        // File Open / Bookmarks / Settings. It keeps five visible rows and scrolls internally
        // instead of growing when more shortcuts are added.
        List<DrawerEntry> shortcutEntries = new ArrayList<>(fixedEntries);
        addShortcutFolderEntries(shortcutEntries);
        addShortcutPlaceholderRows(shortcutEntries);

        List<DrawerEntry> recentFolderEntries = new ArrayList<>();
        addRecentFolderEntries(recentFolderEntries);

        if (drawerEntryAdapter != null) drawerEntryAdapter.setEntries(recentFolderEntries);
        if (drawerFixedEntryAdapter != null) drawerFixedEntryAdapter.setEntries(new ArrayList<>());
        if (drawerShortcutEntryAdapter != null) drawerShortcutEntryAdapter.setEntries(shortcutEntries);

        if (drawerStorageList != null) {
            applyRecentFolderListHeight(drawerStorageList, recentFolderEntries.size());
        }
        if (drawerFixedList != null) {
            applyFixedRowListHeight(drawerFixedList, 0, 0);
        }
        if (drawerShortcutList != null) {
            applyFixedRowListHeight(drawerShortcutList, shortcutEntries.size(), 5);
        }
        if (drawerRecentFoldersHeader != null) {
            // Keep the Recent folders header anchored even after clearing the list.
            // Only the clear action disappears so the title position/shape does not jump.
            drawerRecentFoldersHeader.setVisibility(View.VISIBLE);
        }
        if (drawerRecentFoldersTitle != null) {
            drawerRecentFoldersTitle.setText(R.string.recent_folders);
        }
        if (drawerRecentFoldersClearButton != null) {
            drawerRecentFoldersClearButton.setVisibility(recentFolderEntries.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        }
    }


    private void applyFixedRowListHeight(@NonNull RecyclerView list, int itemCount, int maxRows) {
        android.view.ViewGroup.LayoutParams lp = list.getLayoutParams();
        if (lp == null) return;

        int rows = Math.max(0, Math.min(itemCount, maxRows));
        lp.height = rows <= 0 ? 0 : dpToPx(rows * 48);
        list.setLayoutParams(lp);
        list.setVisibility(rows <= 0 ? View.GONE : View.VISIBLE);
        list.setNestedScrollingEnabled(itemCount > maxRows);
        list.setOverScrollMode(itemCount > maxRows
                ? View.OVER_SCROLL_IF_CONTENT_SCROLLS
                : View.OVER_SCROLL_NEVER);
        list.setVerticalScrollBarEnabled(itemCount > maxRows);
    }

    private void applyRecentFolderListHeight(@NonNull RecyclerView list, int itemCount) {
        android.view.ViewGroup.LayoutParams rawLp = list.getLayoutParams();
        if (rawLp == null) return;

        if (rawLp instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) rawLp;
            lp.height = 0;
            lp.weight = 1f;
            list.setLayoutParams(lp);
        } else {
            rawLp.height = 0;
            list.setLayoutParams(rawLp);
        }

        // Keep this flexible list visible even when it is empty. It acts as the
        // spacer above the bottom shortcut zone, so Recent/Internal/External/
        // Downloads and user folder shortcuts stay attached to the bottom buttons
        // instead of floating under the recent-folder section.
        list.setVisibility(View.VISIBLE);
        list.setNestedScrollingEnabled(true);
        list.setOverScrollMode(itemCount > 0
                ? View.OVER_SCROLL_IF_CONTENT_SCROLLS
                : View.OVER_SCROLL_NEVER);
        list.setVerticalScrollBarEnabled(itemCount > 0);
    }

    private void addShortcutPlaceholderRows(@NonNull List<DrawerEntry> entries) {
        // The bottom shortcut/storage zone is a fixed-height scrollable window.
        // Keep five visible rows so it stays attached above File Open / Bookmarks /
        // Settings, but never let added shortcuts expand the drawer vertically.
        final int visibleShortcutRows = 5;
        int missing = Math.max(0, visibleShortcutRows - entries.size());
        for (int i = 0; i < missing; i++) {
            entries.add(new DrawerEntry(
                    DrawerEntry.ACTION_FOLDER_SHORTCUT,
                    R.drawable.ic_folder,
                    getShortcutPlaceholderTitle(),
                    null,
                    null));
        }
    }

    private String getShortcutPlaceholderTitle() {
        String lang = java.util.Locale.getDefault().getLanguage();
        return "ko".equalsIgnoreCase(lang)
                ? "바로가기가 여기에 추가됩니다"
                : "Shortcut will be added here";
    }

    private void addShortcutFolderEntries(@NonNull List<DrawerEntry> entries) {
        if (prefs == null) return;

        for (String path : prefs.getFolderShortcuts(30)) {
            if (path == null || path.trim().isEmpty()) continue;
            File folder = new File(path.trim());
            if (!folder.exists() || !folder.isDirectory() || !folder.canRead()) continue;
            if (isBuiltInDrawerPath(folder.getAbsolutePath())) continue;

            String name = folder.getName();
            if (name.isEmpty()) name = folder.getAbsolutePath();
            entries.add(new DrawerEntry(
                    DrawerEntry.ACTION_FOLDER_SHORTCUT,
                    R.drawable.ic_folder,
                    name,
                    folder.getAbsolutePath(),
                    folder.getAbsolutePath()));
            if (entries.size() >= 30) break;
        }
    }

    private void addRecentFolderEntries(@NonNull List<DrawerEntry> entries) {
        if (bookmarkManager == null && prefs == null) return;

        LinkedHashSet<String> recentPaths = new LinkedHashSet<>();
        String lastDirectory = prefs != null ? prefs.getLastDirectory() : null;
        if (lastDirectory != null && !lastDirectory.trim().isEmpty()) {
            recentPaths.add(lastDirectory);
        }

        if (prefs != null) {
            recentPaths.addAll(prefs.getRecentFolders(16));
        }

        if (bookmarkManager != null) {
            for (ReaderState state : bookmarkManager.getRecentFiles(50)) {
                File file = new File(state.getFilePath());
                File parent = file.isDirectory() ? file : file.getParentFile();
                if (parent != null) recentPaths.add(parent.getAbsolutePath());
                if (recentPaths.size() >= 12) break;
            }
        }

        List<DrawerEntry> folderEntries = new ArrayList<>();
        for (String path : recentPaths) {
            if (path == null || path.trim().isEmpty()) continue;
            File folder = new File(path);
            if (!folder.exists() || !folder.isDirectory() || !folder.canRead()) continue;
            if (isBuiltInDrawerPath(folder.getAbsolutePath())) continue;
            if (prefs != null && prefs.isRecentFolderHidden(folder.getAbsolutePath())) continue;
            if (prefs != null && prefs.isFolderShortcut(folder.getAbsolutePath())) continue;
            String name = folder.getName();
            if (name.isEmpty()) name = folder.getAbsolutePath();
            folderEntries.add(new DrawerEntry(
                    DrawerEntry.ACTION_RECENT_FOLDER,
                    R.drawable.ic_folder,
                    name,
                    folder.getAbsolutePath(),
                    folder.getAbsolutePath()));
            if (folderEntries.size() >= 10) break;
        }

        entries.addAll(folderEntries);
    }

    private boolean isBuiltInDrawerPath(@NonNull String path) {
        File internal = Environment.getExternalStorageDirectory();
        if (internal != null && path.equals(internal.getAbsolutePath())) return true;

        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (downloads != null && path.equals(downloads.getAbsolutePath())) return true;

        if (path.equals("/storage")) return true;

        for (File sd : detectExternalSdCards()) {
            if (path.equals(sd.getAbsolutePath())) return true;
        }
        return false;
    }

    /**
     * Locate non-emulated external storage volumes (SD cards, USB OTG).
     * Cached after the first successful detection so the drawer rebuild
     * does not re-scan /storage and re-query getExternalFilesDirs() up to
     * 50 times per onResume.
     */
    private List<File> detectExternalSdCards() {
        if (cachedSdCards != null) return cachedSdCards;

        Set<String> seen = new LinkedHashSet<>();
        List<File> result = new ArrayList<>();

        // Method 1: scan /storage for siblings of "emulated"
        File storage = new File("/storage");
        File[] storageChildren = storage.listFiles();
        if (storageChildren != null) {
            for (File f : storageChildren) {
                String name = f.getName();
                if (name.equals("emulated") || name.equals("self")
                        || name.equals("enc_emulated") || name.startsWith(".")) continue;
                if (!f.isDirectory() || !f.canRead()) continue;
                String path = f.getAbsolutePath();
                if (seen.add(path)) result.add(f);
            }
        }

        // Method 2: derive from getExternalFilesDirs (skip the first = internal)
        File[] appDirs = ContextCompat.getExternalFilesDirs(this, null);
        if (appDirs.length > 1) {
            for (int i = 1; i < appDirs.length; i++) {
                File d = appDirs[i];
                if (d == null) continue;
                String p = d.getAbsolutePath();
                int idx = p.indexOf("/Android");
                if (idx > 0) {
                    File root = new File(p.substring(0, idx));
                    if (root.exists() && root.canRead() && seen.add(root.getAbsolutePath())) {
                        result.add(root);
                    }
                }
            }
        }

        cachedSdCards = result;
        return cachedSdCards;
    }

    private void setupDrawerBottomActions() {
        View openFile = findViewById(R.id.drawer_btn_open_file);
        View bookmarks = findViewById(R.id.drawer_btn_bookmarks);
        View settings = findViewById(R.id.drawer_btn_settings);

        if (openFile != null) {
            openFile.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                openFileLauncher.launch(getSupportedOpenMimeTypes());
            });
        }
        if (bookmarks != null) {
            bookmarks.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                startActivity(new Intent(this, BookmarkListActivity.class));
            });
        }
        if (settings != null) {
            settings.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                startActivity(new Intent(this, SettingsActivity.class));
            });
        }
    }


    private String[] getSupportedOpenMimeTypes() {
        return new String[]{
                "text/plain",
                "text/*",
                "application/pdf",
                "application/epub+zip",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.ms-word.document.macroEnabled.12",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.template",
                "application/vnd.ms-word.template.macroEnabled.12",
                "application/octet-stream"
        };
    }

    private void installReliableDrawerEdgeDrag() {
        if (drawerLayout == null) return;

        drawerLayout.post(() -> {
            widenDrawerEdgeDragArea(drawerLayout, dpToPx(32));
            updateDrawerGestureExclusion();
        });

        drawerLayout.addOnLayoutChangeListener((v, left, top, right, bottom,
                                                oldLeft, oldTop, oldRight, oldBottom) ->
                updateDrawerGestureExclusion());
    }

    private void installToolbarMenuButton(Toolbar toolbar) {
        if (toolbar == null) return;

        if (drawerToggle != null) {
            drawerToggle.setDrawerIndicatorEnabled(false);
        }

        Drawable menuIcon = ContextCompat.getDrawable(this, R.drawable.ic_menu);
        if (menuIcon != null) {
            Drawable wrapped = DrawableCompat.wrap(menuIcon.mutate());
            DrawableCompat.setTint(wrapped, Color.WHITE);
            toolbar.setNavigationIcon(wrapped);
        }
        toolbar.setNavigationContentDescription(R.string.drawer_open);
        toolbar.setNavigationOnClickListener(v -> {
            if (drawerLayout != null) drawerLayout.openDrawer(GravityCompat.START);
        });
    }

    private void widenDrawerEdgeDragArea(@NonNull DrawerLayout layout, int desiredPx) {
        try {
            Field leftDraggerField = DrawerLayout.class.getDeclaredField("mLeftDragger");
            leftDraggerField.setAccessible(true);
            Object leftDragger = leftDraggerField.get(layout);
            if (leftDragger == null) return;

            Field edgeSizeField = leftDragger.getClass().getDeclaredField("mEdgeSize");
            edgeSizeField.setAccessible(true);
            int current = edgeSizeField.getInt(leftDragger);
            edgeSizeField.setInt(leftDragger, Math.max(current, desiredPx));

        } catch (Throwable ignored) {
            // Best-effort compatibility path. If AndroidX internals change, the drawer
            // still works through the hamburger button and normal DrawerLayout handling.
        }
    }

    private void updateDrawerGestureExclusion() {
        if (drawerLayout == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        int height = drawerLayout.getHeight();
        if (height <= 0) return;
        int edge = dpToPx(18);
        drawerLayout.setSystemGestureExclusionRects(
                Collections.singletonList(new Rect(0, 0, edge, height)));
    }

    private int dpToPx(float dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private int txtReaderDialogWidthPx() {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        return Math.max(dpToPx(220), Math.min(Math.round(screenWidth * 0.85f), dpToPx(460)));
    }

    private android.app.Dialog createStableBottomDialog(@NonNull View content, int yPx, float dimAmount) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        frame.setBackgroundColor(Color.TRANSPARENT);
        frame.setClipChildren(true);
        frame.setClipToPadding(true);
        frame.addView(content, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT));
        dialog.setContentView(frame);

        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            android.view.WindowManager.LayoutParams lp = new android.view.WindowManager.LayoutParams();
            lp.copyFrom(window.getAttributes());
            lp.width = txtReaderDialogWidthPx();
            lp.height = android.view.WindowManager.LayoutParams.WRAP_CONTENT;
            lp.y = yPx;
            lp.dimAmount = dimAmount;
            window.setAttributes(lp);
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setWindowAnimations(0);
            window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        return dialog;
    }

    private void queueDrawerNavigation(@NonNull DrawerEntry entry) {
        pendingDrawerNavigationEntry = entry;
        drawerNavigationPending = true;

        // Drawer navigation can be expensive, especially Downloads, because
        // directory listing may touch many files. Close the drawer first so the
        // tap response is immediate; run navigation only after the close animation.
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            consumePendingDrawerNavigation();
        }
    }

    private void consumePendingDrawerNavigation() {
        if (!drawerNavigationPending) return;

        DrawerEntry entry = pendingDrawerNavigationEntry;
        pendingDrawerNavigationEntry = null;
        drawerNavigationPending = false;

        if (entry != null && !activityDestroyed) {
            handleDrawerEntryClick(entry);
        }
    }

    private void handleDrawerEntryClick(@NonNull DrawerEntry entry) {
        switch (entry.getActionType()) {
            case DrawerEntry.ACTION_RECENT:
                resetFileFilterForNavigation();
                showHomeMode();
                break;
            case DrawerEntry.ACTION_INTERNAL:
            case DrawerEntry.ACTION_EXTERNAL_SD:
            case DrawerEntry.ACTION_DOWNLOADS:
            case DrawerEntry.ACTION_STORAGE_ROOT:
            case DrawerEntry.ACTION_FOLDER_SHORTCUT:
            case DrawerEntry.ACTION_RECENT_FOLDER:
                if (entry.getPath() != null) {
                    File target = new File(entry.getPath());
                    if (target.exists() && target.canRead()) {
                        resetFileFilterForNavigation();
                        showBrowseMode(target);
                    } else {
                        Toast.makeText(this, target.getAbsolutePath(),
                                Toast.LENGTH_SHORT).show();
                    }
                }
                break;
        }
    }

    private boolean handleDrawerEntryLongClick(@NonNull DrawerEntry entry) {
        if (entry.getActionType() == DrawerEntry.ACTION_FOLDER_SHORTCUT && entry.getPath() != null) {
            showShortcutRemoveDialog(new File(entry.getPath()));
            return true;
        }
        if (entry.getActionType() == DrawerEntry.ACTION_RECENT_FOLDER && entry.getPath() != null) {
            showRecentFolderClearDialog(new File(entry.getPath()));
            return true;
        }
        return false;
    }

    private void addFolderShortcut(@NonNull File folder) {
        if (prefs == null || !folder.isDirectory()) return;
        prefs.addFolderShortcut(folder.getAbsolutePath());
        rebuildDrawerStorageEntries();
        Toast.makeText(this, getString(R.string.shortcut_added), Toast.LENGTH_SHORT).show();
    }

    private void removeFolderShortcut(@NonNull File folder) {
        if (prefs == null) return;
        prefs.removeFolderShortcut(folder.getAbsolutePath());
        rebuildDrawerStorageEntries();
        Toast.makeText(this, getString(R.string.shortcut_removed), Toast.LENGTH_SHORT).show();
    }

    private void showShortcutRemoveDialog(@NonNull File folder) {
        final boolean dark = prefs == null || prefs.shouldUseDarkColors(this);
        final int bg = dark ? Color.rgb(33, 33, 33) : Color.rgb(255, 255, 255);
        final int panel = dark ? Color.rgb(48, 48, 48) : Color.rgb(245, 245, 245);
        final int fg = dark ? Color.rgb(245, 245, 245) : Color.rgb(32, 33, 36);
        final int sub = dark ? Color.rgb(190, 190, 190) : Color.rgb(95, 99, 104);
        final int danger = dark ? Color.rgb(255, 170, 170) : Color.rgb(176, 0, 32);
        final int line = dark ? Color.rgb(92, 92, 92) : Color.rgb(210, 210, 210);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dpToPx(18), dpToPx(16), dpToPx(18), dpToPx(10));
        GradientDrawable bgShape = new GradientDrawable();
        bgShape.setColor(bg);
        bgShape.setCornerRadius(dpToPx(18));
        bgShape.setStroke(Math.max(1, dpToPx(1)), line);
        box.setBackground(bgShape);

        TextView title = new TextView(this);
        title.setText(getString(R.string.remove_shortcut));
        title.setTextColor(fg);
        title.setTextSize(21f);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setPadding(dpToPx(6), 0, dpToPx(6), dpToPx(6));
        box.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView hint = new TextView(this);
        hint.setText(folder.getAbsolutePath());
        hint.setTextColor(sub);
        hint.setTextSize(13f);
        hint.setSingleLine(true);
        hint.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        hint.setPadding(dpToPx(6), 0, dpToPx(6), dpToPx(12));
        box.addView(hint, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        final android.app.Dialog[] ref = new android.app.Dialog[1];
        addFileOpsRow(box, getString(R.string.remove_shortcut), danger, panel, () -> {
            if (ref[0] != null) ref[0].dismiss();
            removeFolderShortcut(folder);
        });

        TextView cancel = new TextView(this);
        cancel.setText(getString(R.string.cancel));
        cancel.setTextColor(sub);
        cancel.setTextSize(16f);
        cancel.setGravity(Gravity.CENTER);
        cancel.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        cancel.setPadding(dpToPx(12), 0, dpToPx(12), 0);
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(50));
        cancelLp.setMargins(0, dpToPx(4), 0, 0);
        box.addView(cancel, cancelLp);

        android.app.Dialog dialog = createStableBottomDialog(box, dpToPx(74), 0.22f);
        ref[0] = dialog;
        cancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showClearAllRecentFilesDialog() {
        if (bookmarkManager == null || bookmarkManager.getRecentFiles(Integer.MAX_VALUE).isEmpty()) {
            Toast.makeText(this, getString(R.string.no_recent_files_to_clear), Toast.LENGTH_SHORT).show();
            return;
        }
        showSimpleDangerDialog(
                getString(R.string.clear_recent_files),
                getString(R.string.clear_recent_files_confirm),
                getString(R.string.clear_recent_files),
                () -> {
                    if (bookmarkManager != null) bookmarkManager.clearReadingStates();
                    loadRecentFiles();
                    rebuildDrawerStorageEntries();
                    Toast.makeText(this, getString(R.string.recent_files_cleared), Toast.LENGTH_SHORT).show();
                });
    }

    private void showClearAllRecentFoldersDialog() {
        List<String> visibleRecentFolders = collectVisibleRecentFolderPaths();
        if (visibleRecentFolders.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_recent_folders_to_clear), Toast.LENGTH_SHORT).show();
            return;
        }
        showSimpleDangerDialog(
                getString(R.string.clear_recent_folders),
                getString(R.string.clear_recent_folders_confirm),
                getString(R.string.clear_recent_folders),
                () -> {
                    if (prefs != null) prefs.clearRecentFolders(visibleRecentFolders);
                    rebuildDrawerStorageEntries();
                    Toast.makeText(this, getString(R.string.recent_folders_cleared), Toast.LENGTH_SHORT).show();
                });
    }

    private void showRecentFolderClearDialog(@NonNull File folder) {
        showSimpleDangerDialog(
                getString(R.string.clear_recent_folder),
                getString(R.string.clear_recent_folder_confirm, folder.getAbsolutePath()),
                getString(R.string.clear_recent_folder),
                () -> {
                    if (prefs != null) prefs.removeRecentFolder(folder.getAbsolutePath());
                    rebuildDrawerStorageEntries();
                    Toast.makeText(this, getString(R.string.recent_folder_cleared), Toast.LENGTH_SHORT).show();
                });
    }

    private List<String> collectVisibleRecentFolderPaths() {
        List<String> result = new ArrayList<>();
        if (bookmarkManager == null && prefs == null) return result;

        LinkedHashSet<String> recentPaths = new LinkedHashSet<>();
        String lastDirectory = prefs != null ? prefs.getLastDirectory() : null;
        if (lastDirectory != null && !lastDirectory.trim().isEmpty()) recentPaths.add(lastDirectory.trim());

        if (prefs != null) recentPaths.addAll(prefs.getRecentFolders(64));

        if (bookmarkManager != null) {
            for (ReaderState state : bookmarkManager.getRecentFiles(200)) {
                File file = new File(state.getFilePath());
                File parent = file.isDirectory() ? file : file.getParentFile();
                if (parent != null) recentPaths.add(parent.getAbsolutePath());
            }
        }

        for (String path : recentPaths) {
            if (path == null || path.trim().isEmpty()) continue;
            File folder = new File(path.trim());
            if (!folder.exists() || !folder.isDirectory() || !folder.canRead()) continue;
            if (isBuiltInDrawerPath(folder.getAbsolutePath())) continue;
            if (prefs != null && prefs.isRecentFolderHidden(folder.getAbsolutePath())) continue;
            if (prefs != null && prefs.isFolderShortcut(folder.getAbsolutePath())) continue;
            result.add(folder.getAbsolutePath());
            if (result.size() >= 10) break;
        }
        return result;
    }

    private void showSimpleDangerDialog(@NonNull String titleText,
                                        @NonNull String messageText,
                                        @NonNull String actionText,
                                        @NonNull Runnable action) {
        final boolean dark = prefs == null || prefs.shouldUseDarkColors(this);
        final int bg = dark ? Color.rgb(33, 33, 33) : Color.rgb(255, 255, 255);
        final int panel = dark ? Color.rgb(48, 48, 48) : Color.rgb(245, 245, 245);
        final int fg = dark ? Color.rgb(245, 245, 245) : Color.rgb(32, 33, 36);
        final int sub = dark ? Color.rgb(190, 190, 190) : Color.rgb(95, 99, 104);
        final int danger = dark ? Color.rgb(255, 170, 170) : Color.rgb(176, 0, 32);
        final int line = dark ? Color.rgb(92, 92, 92) : Color.rgb(210, 210, 210);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dpToPx(18), dpToPx(16), dpToPx(18), dpToPx(10));
        GradientDrawable bgShape = new GradientDrawable();
        bgShape.setColor(bg);
        bgShape.setCornerRadius(dpToPx(18));
        bgShape.setStroke(Math.max(1, dpToPx(1)), line);
        box.setBackground(bgShape);

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(fg);
        title.setTextSize(21f);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setPadding(dpToPx(6), 0, dpToPx(6), dpToPx(8));
        box.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView message = new TextView(this);
        message.setText(messageText);
        message.setTextColor(sub);
        message.setTextSize(14f);
        message.setLineSpacing(dpToPx(2), 1.0f);
        message.setPadding(dpToPx(6), 0, dpToPx(6), dpToPx(14));
        box.addView(message, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        final android.app.Dialog[] ref = new android.app.Dialog[1];
        addFileOpsRow(box, actionText, danger, panel, () -> {
            if (ref[0] != null) ref[0].dismiss();
            action.run();
        });

        TextView cancel = new TextView(this);
        cancel.setText(getString(R.string.cancel));
        cancel.setTextColor(sub);
        cancel.setTextSize(16f);
        cancel.setGravity(Gravity.CENTER);
        cancel.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        cancel.setPadding(dpToPx(12), 0, dpToPx(12), 0);
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(50));
        cancelLp.setMargins(0, dpToPx(4), 0, 0);
        box.addView(cancel, cancelLp);

        android.app.Dialog dialog = createStableBottomDialog(box, dpToPx(74), 0.22f);
        ref[0] = dialog;
        cancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    // -------------------------------------------------------------------------
    // Home / Browse mode switching
    // -------------------------------------------------------------------------

    private void showHomeMode() {
        searchMode = false;
        searchReturnToHome = true;
        searchReturnDirectory = null;
        homeMode = true;
        recentSection.setVisibility(View.VISIBLE);
        browserSection.setVisibility(View.GONE);
        pathText.setVisibility(View.GONE);
        updateFileSearchClearButtonVisibility();
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.app_name);
        }
        loadRecentFiles();
        invalidateOptionsMenu();
    }

    private void showBrowseMode(@NonNull File dir) {
        searchMode = false;
        searchReturnToHome = false;
        searchReturnDirectory = dir;
        homeMode = false;
        recentSection.setVisibility(View.GONE);
        browserSection.setVisibility(View.VISIBLE);
        pathText.setVisibility(View.VISIBLE);
        updateFileSearchClearButtonVisibility();
        if (prefs != null) prefs.addRecentFolder(dir.getAbsolutePath());
        loadDirectory(dir);
        rebuildDrawerStorageEntries();
        invalidateOptionsMenu();
    }

    private void showInitialMainMode() {
        if (initialBrowseDirectory != null
                && initialBrowseDirectory.exists()
                && initialBrowseDirectory.isDirectory()
                && initialBrowseDirectory.canRead()) {
            File target = initialBrowseDirectory;
            initialBrowseDirectory = null;
            showBrowseMode(target);
        } else {
            initialBrowseDirectory = null;
            showHomeMode();
        }
    }

    private File resolveInitialBrowseDirectory(Intent intent) {
        if (intent == null) return null;
        String dirPath = intent.getStringExtra(EXTRA_START_DIRECTORY);
        if (dirPath == null || dirPath.trim().isEmpty()) return null;
        File dir = new File(dirPath.trim());
        if (dir.isFile()) dir = dir.getParentFile();
        return dir;
    }

    // -------------------------------------------------------------------------
    // Theming
    // -------------------------------------------------------------------------

    private boolean isDarkUi() {
        return prefs != null && prefs.shouldUseDarkColors(this);
    }

    private void applyMainReadableTheme(Toolbar toolbar) {
        boolean dark = isDarkUi();

        int bg = dark ? Color.rgb(0, 0, 0) : Color.rgb(255, 255, 255);
        int panel = dark ? Color.rgb(17, 17, 17) : Color.rgb(248, 249, 250);
        int fg = dark ? Color.rgb(232, 234, 237) : Color.rgb(32, 33, 36);
        int sub = dark ? Color.rgb(176, 176, 176) : Color.rgb(95, 99, 104);
        int bar = dark ? Color.rgb(0, 0, 0) : Color.rgb(32, 33, 36);

        View root = findViewById(R.id.main_root);
        View appbar = findViewById(R.id.main_appbar);
        View navDrawer = findViewById(R.id.nav_drawer);
        TextView recentTitle = findViewById(R.id.recent_section_title);
        View searchBar = findViewById(R.id.file_search_bar);

        if (root != null) root.setBackgroundColor(bg);
        if (browserSection != null) browserSection.setBackgroundColor(bg);
        if (recentSection != null) recentSection.setBackgroundColor(bg);
        if (navDrawer != null) {
            navDrawer.setBackgroundColor(bg);
            applyExplicitTextColors(navDrawer, fg, sub);
        }
        if (recentTitle != null) {
            recentTitle.setBackgroundColor(bg);
            recentTitle.setTextColor(fg);
        }
        if (recentClearAllButton != null) {
            recentClearAllButton.setTextColor(sub);
        }
        if (drawerRecentFoldersClearButton != null) {
            drawerRecentFoldersClearButton.setTextColor(sub);
        }
        if (searchBar != null) {
            searchBar.setBackgroundColor(bg);
            applyExplicitTextColors(searchBar, fg, sub);
        }
        if (fileSearchInput != null) {
            fileSearchInput.setTextColor(fg);
            fileSearchInput.setHintTextColor(sub);
            fileSearchInput.setBackgroundColor(panel);
            Drawable[] drawables = fileSearchInput.getCompoundDrawablesRelative();
            for (Drawable drawable : drawables) {
                if (drawable != null) DrawableCompat.setTint(drawable.mutate(), sub);
            }
        }
        if (fileSearchClearButton != null) fileSearchClearButton.setTextColor(fg);
        if (fileSortButton != null) {
            Drawable sortIcon = ContextCompat.getDrawable(this, R.drawable.ic_sort);
            if (sortIcon != null) {
                Drawable wrapped = DrawableCompat.wrap(sortIcon.mutate());
                DrawableCompat.setTint(wrapped, sub);
                fileSortButton.setImageDrawable(wrapped);
            }
        }
        if (fileRecyclerView != null) fileRecyclerView.setBackgroundColor(bg);
        if (recentRecyclerView != null) recentRecyclerView.setBackgroundColor(bg);
        if (recentSection != null) applyExplicitTextColors(recentSection, fg, sub);
        if (browserSection != null) applyExplicitTextColors(browserSection, fg, sub);
        if (pathText != null) {
            pathText.setBackgroundColor(panel);
            pathText.setTextColor(sub);
        }
        if (emptyText != null) emptyText.setTextColor(sub);
        if (recentEmptyText != null) recentEmptyText.setTextColor(sub);

        if (appbar != null) appbar.setBackgroundColor(bar);
        if (toolbar != null) {
            toolbar.setBackgroundColor(bar);
            toolbar.setTitleTextColor(Color.WHITE);
            tintMainToolbarIcons(toolbar, Color.WHITE);
            installToolbarMenuButton(toolbar);
        }

        getWindow().setStatusBarColor(bar);
        getWindow().setNavigationBarColor(bg);
        androidx.core.view.WindowInsetsControllerCompat controller =
                androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(!dark);
        updateFileTypeChips();
    }

    private void applyExplicitTextColors(@NonNull View view, int fg, int sub) {
        if (view instanceof TextView) {
            int id = view.getId();
            TextView textView = (TextView) view;
            if (id == R.id.empty_text || id == R.id.recent_empty_text || id == R.id.current_path) {
                textView.setTextColor(sub);
            } else {
                textView.setTextColor(fg);
            }
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyExplicitTextColors(group.getChildAt(i), fg, sub);
            }
        }
    }

    private void tintMainToolbarIcons(@NonNull Toolbar toolbar, int color) {
        Drawable overflow = ContextCompat.getDrawable(this, R.drawable.ic_more_vert);
        if (overflow != null) {
            Drawable wrapped = DrawableCompat.wrap(overflow.mutate());
            DrawableCompat.setTint(wrapped, color);
            toolbar.setOverflowIcon(wrapped);
        }
        Drawable nav = toolbar.getNavigationIcon();
        if (nav != null) {
            Drawable wrapped = DrawableCompat.wrap(nav.mutate());
            DrawableCompat.setTint(wrapped, color);
            toolbar.setNavigationIcon(wrapped);
        }
    }

    private void tintMenuIcons(@NonNull Menu menu, int color) {
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            Drawable icon = item.getIcon();
            if (icon != null) {
                Drawable wrapped = DrawableCompat.wrap(icon.mutate());
                DrawableCompat.setTint(wrapped, color);
                item.setIcon(wrapped);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Permissions
    // -------------------------------------------------------------------------

    private void checkPermissionsAndInit() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                onPermissionsGranted();
            } else {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    manageStorageLauncher.launch(intent);
                } catch (Exception e) {
                    manageStorageLauncher.launch(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                }
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        PERMISSION_REQUEST_CODE);
            } else {
                onPermissionsGranted();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            onPermissionsGranted();
        }
    }

    private void onPermissionsGranted() {
        rebuildDrawerStorageEntries();
        loadRecentFiles();
    }

    // -------------------------------------------------------------------------
    // File list / recent list
    // -------------------------------------------------------------------------

    private void loadDirectory(File dir) {
        currentDirectory = dir;
        prefs.setLastDirectory(dir.getAbsolutePath());
        prefs.addRecentFolder(dir.getAbsolutePath());
        pathText.setText(dir.getAbsolutePath());
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(dir.getName().isEmpty() ? "/" : dir.getName());
        }

        File[] fileArray = dir.listFiles();
        List<File> fileList = new ArrayList<>();
        boolean showHidden = prefs.getShowHiddenFiles();

        if (fileArray != null) {
            for (File f : fileArray) {
                if (!showHidden && f.getName().startsWith(".")) continue;
                if (f.isDirectory() || FileUtils.isSupportedReadableFile(f.getName())) fileList.add(f);
            }
        }

        if (fileAdapter != null && prefs != null) {
            fileAdapter.setSortMode(prefs.getSortMode());
        }
        fileAdapter.setFiles(fileList);
        scrollListToTop(fileRecyclerView);
        emptyText.setVisibility(fileList.isEmpty() ? View.VISIBLE : View.GONE);
        if (fileList.isEmpty()) emptyText.setText(getString(R.string.no_text_files_in_directory));
    }

    private boolean isRootStorage(File dir) {
        if (dir == null) return true;
        String path = dir.getAbsolutePath();
        return path.equals(Environment.getExternalStorageDirectory().getAbsolutePath())
                || path.equals("/storage") || path.equals("/");
    }

    private void loadRecentFiles() {
        List<ReaderState> recent = bookmarkManager.getRecentFiles(50);
        List<File> recentFiles = new ArrayList<>();
        for (ReaderState s : recent) {
            File f = new File(s.getFilePath());
            if (!f.exists()) continue;
            if (activeFileFilter != FILTER_ALL
                    && !matchesActiveFileFilter(f.getName(), activeFileFilter)) {
                continue;
            }
            recentFiles.add(f);
        }

        if (recentAdapter != null) {
            int recentSort = prefs != null ? prefs.getRecentSortMode() : PrefsManager.SORT_RECENT_READ;
            if (recentSort == PrefsManager.SORT_RECENT_READ) {
                // BookmarkManager already returns the list by last-read time, newest first.
                // Do not let FileAdapter re-sort this into name/number order.
                recentAdapter.setSortEnabled(false);
            } else {
                recentAdapter.setSortEnabled(true);
                recentAdapter.setSortMode(recentSort);
            }
            recentAdapter.setFiles(recentFiles);
            scrollListToTop(recentRecyclerView);
        }
        if (recentEmptyText != null) {
            recentEmptyText.setVisibility(recentFiles.isEmpty() ? View.VISIBLE : View.GONE);
        }
        if (recentClearAllButton != null) {
            boolean hasAnyRecent = bookmarkManager != null && !bookmarkManager.getRecentFiles(Integer.MAX_VALUE).isEmpty();
            recentClearAllButton.setVisibility(hasAnyRecent ? View.VISIBLE : View.INVISIBLE);
        }
    }

    @Override public void onFileClick(@NonNull File file) {
        if (file.isDirectory()) {
            // Stepping into a subfolder from home or search results promotes the view to browse mode.
            if (homeMode || searchMode) showBrowseMode(file);
            else loadDirectory(file);
        } else {
            openFile(file);
        }
    }

    @Override public void onFileLongClick(@NonNull File file) { showFileOpsDialog(file); }

    private void openFile(File file) {
        File parent = file.getParentFile();
        if (parent != null && prefs != null) {
            prefs.addRecentFolder(parent.getAbsolutePath());
            rebuildDrawerStorageEntries();
        }

        Intent intent;
        if (FileUtils.isPdfFile(file.getName())) {
            intent = new Intent(this, PdfReaderActivity.class);
            intent.putExtra(PdfReaderActivity.EXTRA_FILE_PATH, file.getAbsolutePath());
        } else if (FileUtils.isEpubFile(file.getName()) || FileUtils.isWordFile(file.getName())) {
            intent = new Intent(this, DocumentPageActivity.class);
            intent.putExtra(DocumentPageActivity.EXTRA_FILE_PATH, file.getAbsolutePath());
        } else {
            intent = new Intent(this, ReaderActivity.class);
            intent.putExtra(ReaderActivity.EXTRA_FILE_PATH, file.getAbsolutePath());
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);

        if (returnToViewerMode) finish();
    }

    private void openFileFromUri(Uri uri) {
        String displayName = FileUtils.getFileNameFromUri(this, uri);
        String mime = getContentResolver().getType(uri);
        boolean pdf = FileUtils.isPdfFile(displayName) || "application/pdf".equalsIgnoreCase(mime);
        boolean epub = FileUtils.isEpubFile(displayName) || "application/epub+zip".equalsIgnoreCase(mime);
        boolean word = FileUtils.isWordFile(displayName)
                || "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equalsIgnoreCase(mime)
                || "application/vnd.ms-word.document.macroEnabled.12".equalsIgnoreCase(mime)
                || "application/vnd.openxmlformats-officedocument.wordprocessingml.template".equalsIgnoreCase(mime)
                || "application/vnd.ms-word.template.macroEnabled.12".equalsIgnoreCase(mime);

        Intent intent;
        if (pdf) {
            intent = new Intent(this, PdfReaderActivity.class);
            intent.putExtra(PdfReaderActivity.EXTRA_FILE_URI, uri.toString());
        } else if (epub || word) {
            intent = new Intent(this, DocumentPageActivity.class);
            intent.putExtra(DocumentPageActivity.EXTRA_FILE_URI, uri.toString());
        } else {
            intent = new Intent(this, ReaderActivity.class);
            intent.putExtra(ReaderActivity.EXTRA_FILE_URI, uri.toString());
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);

        if (returnToViewerMode) finish();
    }

    // -------------------------------------------------------------------------
    // File operations (rename / delete / new folder / info / sort)
    // -------------------------------------------------------------------------

    private void showFileOpsDialog(File file) {
        final boolean dark = prefs == null || prefs.shouldUseDarkColors(this);
        final int bg = dark ? Color.rgb(33, 33, 33) : Color.rgb(255, 255, 255);
        final int panel = dark ? Color.rgb(48, 48, 48) : Color.rgb(245, 245, 245);
        final int fg = dark ? Color.rgb(245, 245, 245) : Color.rgb(32, 33, 36);
        final int sub = dark ? Color.rgb(190, 190, 190) : Color.rgb(95, 99, 104);
        final int danger = dark ? Color.rgb(255, 170, 170) : Color.rgb(176, 0, 32);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dpToPx(18), dpToPx(16), dpToPx(18), dpToPx(10));
        GradientDrawable bgShape = new GradientDrawable();
        bgShape.setColor(bg);
        bgShape.setCornerRadius(dpToPx(18));
        bgShape.setStroke(Math.max(1, dpToPx(1)), dark ? Color.rgb(92, 92, 92) : Color.rgb(210, 210, 210));
        box.setBackground(bgShape);

        TextView title = new TextView(this);
        title.setText(file.getName());
        title.setTextColor(fg);
        title.setTextSize(21f);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setPadding(dpToPx(6), 0, dpToPx(6), dpToPx(14));
        box.addView(title, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        final android.app.Dialog[] ref = new android.app.Dialog[1];
        if (!file.isDirectory()) {
            addFileOpsRow(box, getString(R.string.open), fg, panel, () -> { if (ref[0] != null) ref[0].dismiss(); openFile(file); });
            if (homeMode && bookmarkManager != null && bookmarkManager.getReadingState(file.getAbsolutePath()) != null) {
                addFileOpsRow(box, getString(R.string.clear_from_recently_viewed), fg, panel, () -> {
                    if (ref[0] != null) ref[0].dismiss();
                    bookmarkManager.deleteReadingState(file.getAbsolutePath());
                    loadRecentFiles();
                    rebuildDrawerStorageEntries();
                    Toast.makeText(this, getString(R.string.recent_file_cleared), Toast.LENGTH_SHORT).show();
                });
            }
        } else if (!isBuiltInDrawerPath(file.getAbsolutePath())) {
            boolean shortcut = prefs != null && prefs.isFolderShortcut(file.getAbsolutePath());
            addFileOpsRow(box,
                    getString(shortcut ? R.string.remove_shortcut : R.string.add_shortcut),
                    fg,
                    panel,
                    () -> {
                        if (ref[0] != null) ref[0].dismiss();
                        if (shortcut) removeFolderShortcut(file);
                        else addFolderShortcut(file);
                    });
        }
        addFileOpsRow(box, getString(R.string.rename), fg, panel, () -> { if (ref[0] != null) ref[0].dismiss(); showRenameDialog(file); });
        addFileOpsRow(box, getString(R.string.delete), danger, panel, () -> { if (ref[0] != null) ref[0].dismiss(); showDeleteConfirm(file); });
        if (!file.isDirectory()) {
            addFileOpsRow(box, getString(R.string.file_info), fg, panel, () -> { if (ref[0] != null) ref[0].dismiss(); showFileInfo(file); });
        }

        TextView cancel = new TextView(this);
        cancel.setText(getString(R.string.cancel));
        cancel.setTextColor(sub);
        cancel.setTextSize(16f);
        cancel.setGravity(Gravity.CENTER);
        cancel.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        cancel.setPadding(dpToPx(12), 0, dpToPx(12), 0);
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(50));
        cancelLp.setMargins(0, dpToPx(4), 0, 0);
        box.addView(cancel, cancelLp);

        android.app.Dialog dialog = createStableBottomDialog(box, dpToPx(74), 0.22f);
        ref[0] = dialog;
        cancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void addFileOpsRow(@NonNull LinearLayout box, @NonNull String label, int textColor, int panelColor, @NonNull Runnable action) {
        TextView row = new TextView(this);
        row.setText(label);
        row.setTextColor(textColor);
        row.setTextSize(17f);
        row.setGravity(Gravity.CENTER);
        row.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        row.setPadding(dpToPx(16), 0, dpToPx(16), 0);
        GradientDrawable rowBg = new GradientDrawable();
        rowBg.setColor(panelColor);
        rowBg.setCornerRadius(dpToPx(12));
        row.setBackground(rowBg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(50));
        lp.setMargins(0, 0, 0, dpToPx(8));
        box.addView(row, lp);
        row.setOnClickListener(v -> action.run());
    }

    private void showRenameDialog(File file) {
        final boolean dark = prefs == null || prefs.shouldUseDarkColors(this);
        final int bg = dark ? Color.rgb(33, 33, 33) : Color.rgb(255, 255, 255);
        final int panel = dark ? Color.rgb(48, 48, 48) : Color.rgb(245, 245, 245);
        final int fg = dark ? Color.rgb(245, 245, 245) : Color.rgb(32, 33, 36);
        final int sub = dark ? Color.rgb(190, 190, 190) : Color.rgb(95, 99, 104);
        final int line = dark ? Color.rgb(92, 92, 92) : Color.rgb(210, 210, 210);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dpToPx(18), dpToPx(16), dpToPx(18), dpToPx(10));
        GradientDrawable bgShape = new GradientDrawable();
        bgShape.setColor(bg);
        bgShape.setCornerRadius(dpToPx(18));
        bgShape.setStroke(Math.max(1, dpToPx(1)), line);
        box.setBackground(bgShape);

        TextView title = new TextView(this);
        title.setText(getString(R.string.rename));
        title.setTextColor(fg);
        title.setTextSize(21f);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setPadding(dpToPx(6), 0, dpToPx(6), dpToPx(6));
        box.addView(title, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView hint = new TextView(this);
        hint.setText(file.getName());
        hint.setTextColor(sub);
        hint.setTextSize(13f);
        hint.setSingleLine(true);
        hint.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        hint.setPadding(dpToPx(6), 0, dpToPx(6), dpToPx(12));
        box.addView(hint, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        EditText input = new EditText(this);
        input.setText(file.getName());
        input.selectAll();
        input.setSingleLine(true);
        input.setTextColor(fg);
        input.setHintTextColor(sub);
        input.setTextSize(16f);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        input.setPadding(dpToPx(14), 0, dpToPx(14), 0);
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(panel);
        inputBg.setCornerRadius(dpToPx(12));
        inputBg.setStroke(Math.max(1, dpToPx(1)), line);
        input.setBackground(inputBg);
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(52));
        inputLp.setMargins(0, 0, 0, dpToPx(12));
        box.addView(input, inputLp);

        final AlertDialog[] ref = new AlertDialog[1];
        addFileOpsRow(box, getString(R.string.rename), fg, panel, () -> {
            String newName = input.getText().toString().trim();
            if (newName.isEmpty()) return;
            File parent = file.getParentFile();
            if (parent == null) return;
            File newFile = new File(parent, newName);
            if (file.renameTo(newFile)) {
                if (ref[0] != null) ref[0].dismiss();
                loadDirectory(currentDirectory);
                Toast.makeText(this, getString(R.string.renamed), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, getString(R.string.rename_failed), Toast.LENGTH_SHORT).show();
            }
        });

        TextView cancel = new TextView(this);
        cancel.setText(getString(R.string.cancel));
        cancel.setTextColor(sub);
        cancel.setTextSize(16f);
        cancel.setGravity(Gravity.CENTER);
        cancel.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        box.addView(cancel, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(48)));

        AlertDialog dialog = new AlertDialog.Builder(this).create();
        ref[0] = dialog;
        dialog.setView(box);
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                android.view.WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
                lp.dimAmount = 0.22f;
                dialog.getWindow().setAttributes(lp);
                dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
            input.requestFocus();
        });
        cancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showDeleteConfirm(File file) {
        final boolean dark = prefs == null || prefs.shouldUseDarkColors(this);
        final int bg = dark ? Color.rgb(33, 33, 33) : Color.rgb(255, 255, 255);
        final int panel = dark ? Color.rgb(48, 48, 48) : Color.rgb(245, 245, 245);
        final int fg = dark ? Color.rgb(245, 245, 245) : Color.rgb(32, 33, 36);
        final int sub = dark ? Color.rgb(190, 190, 190) : Color.rgb(95, 99, 104);
        final int danger = dark ? Color.rgb(255, 170, 170) : Color.rgb(176, 0, 32);
        final int line = dark ? Color.rgb(92, 92, 92) : Color.rgb(210, 210, 210);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dpToPx(18), dpToPx(16), dpToPx(18), dpToPx(10));
        GradientDrawable bgShape = new GradientDrawable();
        bgShape.setColor(bg);
        bgShape.setCornerRadius(dpToPx(18));
        bgShape.setStroke(Math.max(1, dpToPx(1)), line);
        box.setBackground(bgShape);

        TextView title = new TextView(this);
        title.setText(getString(R.string.delete));
        title.setTextColor(danger);
        title.setTextSize(21f);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setPadding(dpToPx(6), 0, dpToPx(6), dpToPx(10));
        box.addView(title, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView message = new TextView(this);
        message.setText(getString(R.string.delete_file_confirm, file.getName()));
        message.setTextColor(fg);
        message.setTextSize(15f);
        message.setLineSpacing(dpToPx(2), 1.0f);
        message.setPadding(dpToPx(6), 0, dpToPx(6), dpToPx(14));
        box.addView(message, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        final AlertDialog[] ref = new AlertDialog[1];
        addFileOpsRow(box, getString(R.string.delete), danger, panel, () -> {
            if (deleteRecursive(file)) {
                if (ref[0] != null) ref[0].dismiss();
                loadDirectory(currentDirectory);
                Toast.makeText(this, getString(R.string.deleted), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, getString(R.string.delete_failed), Toast.LENGTH_SHORT).show();
            }
        });

        TextView cancel = new TextView(this);
        cancel.setText(getString(R.string.cancel));
        cancel.setTextColor(sub);
        cancel.setTextSize(16f);
        cancel.setGravity(Gravity.CENTER);
        cancel.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        box.addView(cancel, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(48)));

        AlertDialog dialog = new AlertDialog.Builder(this).create();
        ref[0] = dialog;
        dialog.setView(box);
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                android.view.WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
                lp.dimAmount = 0.22f;
                dialog.getWindow().setAttributes(lp);
                dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            }
        });
        cancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private boolean deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File c : children) deleteRecursive(c);
            }
        }
        return file.delete();
    }

    private void showFileInfo(File file) {
        final boolean dark = prefs == null || prefs.shouldUseDarkColors(this);
        final int bg = dark ? Color.rgb(33, 33, 33) : Color.rgb(255, 255, 255);
        final int panel = dark ? Color.rgb(48, 48, 48) : Color.rgb(245, 245, 245);
        final int fg = dark ? Color.rgb(245, 245, 245) : Color.rgb(32, 33, 36);
        final int sub = dark ? Color.rgb(205, 205, 205) : Color.rgb(78, 84, 92);
        final int line = dark ? Color.rgb(92, 92, 92) : Color.rgb(210, 210, 210);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dpToPx(18), dpToPx(16), dpToPx(18), dpToPx(10));
        GradientDrawable bgShape = new GradientDrawable();
        bgShape.setColor(bg);
        bgShape.setCornerRadius(dpToPx(18));
        bgShape.setStroke(Math.max(1, dpToPx(1)), line);
        box.setBackground(bgShape);

        TextView title = new TextView(this);
        title.setText(getString(R.string.file_info));
        title.setTextColor(fg);
        title.setTextSize(21f);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setPadding(dpToPx(6), 0, dpToPx(6), dpToPx(12));
        box.addView(title, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout infoList = new LinearLayout(this);
        infoList.setOrientation(LinearLayout.VERTICAL);
        infoList.setPadding(0, 0, 0, dpToPx(4));
        addFileInfoRow(infoList, getString(R.string.file_info_name), file.getName(), fg, sub, panel);
        addFileInfoRow(infoList, getString(R.string.file_info_path), file.getAbsolutePath(), fg, sub, panel);
        addFileInfoRow(infoList, getString(R.string.file_info_size), FileUtils.formatFileSize(file.length()), fg, sub, panel);
        addFileInfoRow(infoList, getString(R.string.file_info_modified),
                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                        .format(new java.util.Date(file.lastModified())), fg, sub, panel);
        addFileInfoRow(infoList, getString(R.string.file_info_readable), String.valueOf(file.canRead()), fg, sub, panel);
        addFileInfoRow(infoList, getString(R.string.file_info_writable), String.valueOf(file.canWrite()), fg, sub, panel);
        if (!file.isDirectory()) {
            addFileInfoRow(infoList, getString(R.string.file_info_type), FileUtils.getReadableFileType(file.getName()), fg, sub, panel);
            if (FileUtils.isTextFile(file.getName())) {
                addFileInfoRow(infoList, getString(R.string.file_info_encoding), FileUtils.detectEncoding(file), fg, sub, panel);
            }
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setBackgroundColor(Color.TRANSPARENT);
        scroll.addView(infoList);
        box.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.min(dpToPx(430), getResources().getDisplayMetrics().heightPixels - dpToPx(240))));

        TextView close = new TextView(this);
        close.setText(getString(R.string.ok));
        close.setTextColor(fg);
        close.setTextSize(16f);
        close.setGravity(Gravity.CENTER);
        close.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        close.setPadding(dpToPx(12), 0, dpToPx(12), 0);
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(50));
        closeLp.setMargins(0, dpToPx(4), 0, 0);
        box.addView(close, closeLp);

        AlertDialog dialog = new AlertDialog.Builder(this).create();
        dialog.setView(box);
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                android.view.WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
                lp.dimAmount = 0.22f;
                dialog.getWindow().setAttributes(lp);
                dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            }
        });
        close.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    @SuppressLint("WrongConstant")
    private void addFileInfoRow(@NonNull LinearLayout box, @NonNull String label, String value, int fg, int sub, int panelColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dpToPx(14), dpToPx(9), dpToPx(14), dpToPx(10));
        GradientDrawable rowBg = new GradientDrawable();
        rowBg.setColor(panelColor);
        rowBg.setCornerRadius(dpToPx(12));
        row.setBackground(rowBg);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(sub);
        labelView.setTextSize(12f);
        labelView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        row.addView(labelView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView valueView = new TextView(this);
        valueView.setText(value != null ? value : "");
        valueView.setTextColor(fg);
        valueView.setTextSize(15f);
        valueView.setPadding(0, dpToPx(3), 0, 0);
        valueView.setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE);
        row.addView(valueView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dpToPx(8));
        box.addView(row, lp);
    }

    private void showNewFolderDialog() {
        if (currentDirectory == null) return;
        EditText input = new EditText(this);
        input.setHint(getString(R.string.folder_name));

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.new_folder))
                .setView(input)
                .setPositiveButton(getString(R.string.create), (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) return;
                    File newDir = new File(currentDirectory, name);
                    if (newDir.mkdirs()) {
                        loadDirectory(currentDirectory);
                        Toast.makeText(this, getString(R.string.folder_created), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, getString(R.string.folder_create_failed), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void showSortDialog() {
        final boolean dark = prefs == null || prefs.shouldUseDarkColors(this);
        final int bg = dark ? Color.rgb(33, 33, 33) : Color.rgb(255, 255, 255);
        final int panel = dark ? Color.rgb(48, 48, 48) : Color.rgb(245, 245, 245);
        final int fg = dark ? Color.rgb(245, 245, 245) : Color.rgb(32, 33, 36);
        final int sub = dark ? Color.rgb(190, 190, 190) : Color.rgb(95, 99, 104);
        final int line = dark ? Color.rgb(92, 92, 92) : Color.rgb(210, 210, 210);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dpToPx(18), dpToPx(16), dpToPx(18), dpToPx(10));
        GradientDrawable bgShape = new GradientDrawable();
        bgShape.setColor(bg);
        bgShape.setCornerRadius(dpToPx(18));
        bgShape.setStroke(Math.max(1, dpToPx(1)), line);
        box.setBackground(bgShape);

        TextView title = new TextView(this);
        title.setText(getString(R.string.sort_by));
        title.setTextColor(fg);
        title.setTextSize(21f);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setPadding(dpToPx(6), 0, dpToPx(6), dpToPx(12));
        box.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);
        group.setPadding(0, 0, 0, dpToPx(2));
        box.addView(group, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        boolean sortingRecentHome = homeMode && !searchMode;
        final int recentReadId = View.generateViewId();

        if (sortingRecentHome) {
            group.addView(makeSortRadioButton(recentReadId, getString(R.string.sort_recent_read), fg, panel, line));
        }

        int[] ids = new int[7];
        for (int i = 0; i < ids.length; i++) ids[i] = View.generateViewId();

        CharSequence[] labels = new CharSequence[] {
                getString(R.string.sort_name_asc),
                getString(R.string.sort_name_desc),
                getString(R.string.sort_date_new),
                getString(R.string.sort_date_old),
                getString(R.string.sort_size_large),
                getString(R.string.sort_size_small),
                getString(R.string.sort_type)
        };

        for (int i = 0; i < labels.length; i++) {
            group.addView(makeSortRadioButton(ids[i], labels[i], fg, panel, line));
        }

        int current = sortingRecentHome
                ? (prefs != null ? prefs.getRecentSortMode() : PrefsManager.SORT_RECENT_READ)
                : (prefs != null ? prefs.getSortMode() : PrefsManager.SORT_NAME_ASC);
        if (sortingRecentHome && current == PrefsManager.SORT_RECENT_READ) {
            group.check(recentReadId);
        } else if (current >= 0 && current < ids.length) {
            group.check(ids[current]);
        } else {
            group.check(ids[PrefsManager.SORT_NAME_ASC]);
        }

        TextView cancel = new TextView(this);
        cancel.setText(getString(R.string.cancel));
        cancel.setTextColor(sub);
        cancel.setTextSize(16f);
        cancel.setGravity(Gravity.CENTER);
        cancel.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        box.addView(cancel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(48)));

        android.app.Dialog dialog = createStableBottomDialog(box, dpToPx(74), 0.22f);

        group.setOnCheckedChangeListener((g, checkedId) -> {
            if (sortingRecentHome && checkedId == recentReadId) {
                if (prefs != null) prefs.setRecentSortMode(PrefsManager.SORT_RECENT_READ);
                loadRecentFiles();
                scrollListToTop(recentRecyclerView);
                dialog.dismiss();
                return;
            }

            for (int i = 0; i < ids.length; i++) {
                if (ids[i] == checkedId) {
                    if (sortingRecentHome) {
                        if (prefs != null) prefs.setRecentSortMode(i);
                        loadRecentFiles();
                        scrollListToTop(recentRecyclerView);
                    } else {
                        if (prefs != null) prefs.setSortMode(i);
                        if (fileAdapter != null) fileAdapter.setSortMode(i);

                        if (!searchMode && currentDirectory != null) {
                            // Folder sort is scoped to the currently opened directory.
                            // Reload that directory instead of touching all-storage/search roots.
                            loadDirectory(currentDirectory);
                        } else {
                            // Search results stay as the current result set; only their order changes.
                            scrollListToTop(fileRecyclerView);
                        }
                    }
                    dialog.dismiss();
                    break;
                }
            }
        });
        cancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private RadioButton makeSortRadioButton(int id, CharSequence label, int fg, int panel, int line) {
        RadioButton radio = new RadioButton(this);
        radio.setId(id);
        radio.setText(label);
        radio.setTextColor(fg);
        radio.setTextSize(16f);
        radio.setGravity(Gravity.CENTER_VERTICAL);

        // Draw the radio circle as a normal compound drawable instead of using
        // the platform button slot. The platform slot hugs the far-left edge on
        // some devices; this keeps the bubble naturally inset inside the row.
        radio.setButtonDrawable(null);
        radio.setCompoundDrawablesWithIntrinsicBounds(makeSortRadioCircleDrawable(fg, line, panel), null, null, null);
        radio.setCompoundDrawablePadding(dpToPx(12));
        radio.setPadding(dpToPx(18), 0, dpToPx(14), 0);

        GradientDrawable rowBg = new GradientDrawable();
        rowBg.setColor(panel);
        rowBg.setCornerRadius(dpToPx(12));
        rowBg.setStroke(Math.max(1, dpToPx(1)), line);
        radio.setBackground(rowBg);

        RadioGroup.LayoutParams lp = new RadioGroup.LayoutParams(
                RadioGroup.LayoutParams.MATCH_PARENT,
                dpToPx(48));
        lp.setMargins(0, 0, 0, dpToPx(8));
        radio.setLayoutParams(lp);
        return radio;
    }

    private Drawable makeSortRadioCircleDrawable(int fg, int line, int panel) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] { android.R.attr.state_checked }, makeSortRadioCircle(true, fg, line, panel));
        states.addState(new int[] {}, makeSortRadioCircle(false, fg, line, panel));
        return states;
    }

    private Drawable makeSortRadioCircle(boolean checked, int fg, int line, int panel) {
        GradientDrawable outer = new GradientDrawable();
        outer.setShape(GradientDrawable.OVAL);
        outer.setColor(Color.TRANSPARENT);
        outer.setStroke(Math.max(1, dpToPx(2)), checked ? fg : line);
        outer.setSize(dpToPx(20), dpToPx(20));

        if (!checked) return outer;

        GradientDrawable inner = new GradientDrawable();
        inner.setShape(GradientDrawable.OVAL);
        inner.setColor(fg);
        inner.setSize(dpToPx(10), dpToPx(10));

        LayerDrawable layer = new LayerDrawable(new Drawable[] { outer, inner });
        int inset = dpToPx(5);
        layer.setLayerInset(1, inset, inset, inset, inset);
        return layer;
    }

    private void applyRoundedDialogWindow(@NonNull AlertDialog dialog) {
        if (dialog.getWindow() == null) return;
        dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        android.view.WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
        lp.dimAmount = 0.22f;
        dialog.getWindow().setAttributes(lp);
        dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
    }

    // -------------------------------------------------------------------------
    // Menu
    // -------------------------------------------------------------------------

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        MenuItem hidden = menu.findItem(R.id.action_show_hidden);
        if (hidden != null) hidden.setChecked(prefs.getShowHiddenFiles());
        tintMenuIcons(menu, Color.WHITE);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // Sort/new-folder/show-hidden are folder-only actions, hide them on home/search views.
        boolean inBrowse = !homeMode && !searchMode;
        MenuItem sort = menu.findItem(R.id.action_sort);
        MenuItem newFolder = menu.findItem(R.id.action_new_folder);
        MenuItem hidden = menu.findItem(R.id.action_show_hidden);
        if (sort != null) sort.setVisible(inBrowse);
        if (newFolder != null) newFolder.setVisible(inBrowse);
        if (hidden != null) hidden.setVisible(inBrowse);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (drawerToggle != null && drawerToggle.onOptionsItemSelected(item)) return true;

        int id = item.getItemId();
        if (id == R.id.action_sort) { showSortDialog(); return true; }
        else if (id == R.id.action_new_folder) { showNewFolderDialog(); return true; }
        else if (id == R.id.action_show_hidden) {
            boolean newVal = !item.isChecked();
            item.setChecked(newVal);
            prefs.setShowHiddenFiles(newVal);
            if (currentDirectory != null) loadDirectory(currentDirectory);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // -------------------------------------------------------------------------
    // Back handling
    // -------------------------------------------------------------------------

    private void handleMainBackPressed() {
        // 1. If the drawer is open, close it.
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
            return;
        }

        // 2. If only a file-type filter is active, Back should return the chips and
        // content to All instead of showing the exit toast from the home/recent page.
        if (activeFileFilter != FILTER_ALL) {
            activeFileFilter = FILTER_ALL;
            updateFileTypeChips();
            if (searchMode) {
                restorePreSearchLocation();
            } else if (homeMode) {
                loadRecentFiles();
            } else if (currentDirectory != null && currentDirectory.exists() && currentDirectory.isDirectory()) {
                loadDirectory(currentDirectory);
            } else {
                restoreAllFilterLocation();
            }
            return;
        }

        // 3. Search results return to home first.
        if (searchMode) {
            restorePreSearchLocation();
            return;
        }

        // 4. If browsing, navigate up. When at the storage root, drop back to home.
        if (!homeMode) {
            if (currentDirectory != null && !isRootStorage(currentDirectory)) {
                File parent = currentDirectory.getParentFile();
                if (parent != null && parent.canRead()) {
                    loadDirectory(parent);
                    return;
                }
            }
            // At root or nowhere to go up: return to home (Recent).
            showHomeMode();
            return;
        }

        // 5. From home: special "return to viewer" behavior, or double-tap-back to exit.
        if (returnToViewerMode) {
            finish();
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastBackPressedTime < 2000) {
            finish();
        } else {
            lastBackPressedTime = now;
            Toast.makeText(this, getString(R.string.press_back_again_exit), Toast.LENGTH_SHORT).show();
        }
    }

}
