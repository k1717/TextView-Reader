package com.textview.reader;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.HorizontalScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.textview.reader.util.FileTypeFilter;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Owns the main-screen search box and file-type filter strip so MainActivity can
 * stay focused on navigation, lifecycle, and file operations.
 */
final class MainSearchFilterController {
    private final MainActivity activity;
    private final Runnable fileTypeFilterSnapRunnable = this::snapFileTypeFilterToSlot;

    MainSearchFilterController(@NonNull MainActivity activity) {
        this.activity = activity;
    }

    void setupFileSearch() {
        activity.fileSearchBar = activity.findViewById(R.id.file_search_bar);
        activity.fileSearchInput = activity.findViewById(R.id.file_search_input);
        activity.fileSearchClearButton = activity.findViewById(R.id.file_search_clear_button);
        activity.fileSearchProgress = activity.findViewById(R.id.file_search_progress);
        activity.fileSortButton = activity.findViewById(R.id.file_sort_button);
        activity.fileTypeFilterScroll = activity.findViewById(R.id.file_type_filter_scroll);
        activity.filterAllChip = activity.findViewById(R.id.filter_all);
        activity.filterTxtChip = activity.findViewById(R.id.filter_txt);
        activity.filterGeneralChip = activity.findViewById(R.id.filter_general);
        activity.filterArchiveChip = activity.findViewById(R.id.filter_archive);
        activity.filterPdfChip = activity.findViewById(R.id.filter_pdf);
        activity.filterEpubChip = activity.findViewById(R.id.filter_epub);
        activity.filterWordChip = activity.findViewById(R.id.filter_word);
        activity.filterImageChip = activity.findViewById(R.id.filter_img);

        setupFileTypeChip(activity.filterAllChip, MainActivity.FILTER_ALL);
        setupFileTypeChip(activity.filterGeneralChip, MainActivity.FILTER_GENERAL);
        setupFileTypeChip(activity.filterTxtChip, MainActivity.FILTER_TXT);
        setupFileTypeChip(activity.filterArchiveChip, MainActivity.FILTER_ARCHIVE);
        setupFileTypeChip(activity.filterPdfChip, MainActivity.FILTER_PDF);
        setupFileTypeChip(activity.filterEpubChip, MainActivity.FILTER_EPUB);
        setupFileTypeChip(activity.filterWordChip, MainActivity.FILTER_WORD);
        setupFileTypeChip(activity.filterImageChip, MainActivity.FILTER_IMAGE);
        updateFileTypeChips();
        setupFileTypeFilterScrollBehavior();

        if (activity.fileSearchInput != null) {
            activity.fileSearchInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    scheduleLiveFileSearch();
                }
                @Override public void afterTextChanged(Editable s) {}
            });
            activity.fileSearchInput.setOnEditorActionListener((v, actionId, event) -> {
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

        if (activity.fileSearchClearButton != null) {
            activity.fileSearchClearButton.setOnClickListener(v -> {
                if (activity.fileSearchInput != null) activity.fileSearchInput.setText("");
                if (activity.searchMode) restorePreSearchLocation();
            });
            activity.fileSearchClearButton.setVisibility(View.GONE);
        }

        if (activity.fileSortButton != null) {
            activity.fileSortButton.setOnClickListener(v -> activity.showSortDialog());
        }
    }

    private void setupFileTypeFilterScrollBehavior() {
        if (activity.fileTypeFilterScroll == null) return;

        activity.fileTypeFilterScroll.addOnLayoutChangeListener((v, left, top, right, bottom,
                                                                oldLeft, oldTop, oldRight, oldBottom) ->
                applyFileTypeFilterSlotWidths());
        activity.fileTypeFilterScroll.post(this::applyFileTypeFilterSlotWidths);

        activity.fileTypeFilterScroll.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        if (activity.drawerLayout != null) {
                            activity.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.START);
                        }
                        activity.resetDrawerSwipeState();
                        v.removeCallbacks(fileTypeFilterSnapRunnable);
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                        break;
                    case MotionEvent.ACTION_MOVE:
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.getParent().requestDisallowInterceptTouchEvent(false);
                        v.postDelayed(fileTypeFilterSnapRunnable, 110L);
                        v.postDelayed(() -> {
                            if (activity.drawerLayout != null && !activity.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                                activity.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.START);
                            }
                        }, 160L);
                        break;
                    default:
                        break;
                }
                return false;
            }
        });
    }

    private void applyFileTypeFilterSlotWidths() {
        if (activity.fileTypeFilterScroll == null || activity.fileTypeFilterScroll.getWidth() <= 0) return;
        final int visibleSlots = 5;
        final int gap = activity.dpToPx(4);
        final int viewport = activity.fileTypeFilterScroll.getWidth();
        final int slotWidth = Math.max(activity.dpToPx(50),
                (viewport - (gap * (visibleSlots - 1))) / visibleSlots);
        activity.fileTypeFilterStepPx = slotWidth + gap;
        View[] chips = new View[] {
                activity.filterAllChip, activity.filterGeneralChip, activity.filterTxtChip, activity.filterArchiveChip,
                activity.filterPdfChip, activity.filterEpubChip, activity.filterWordChip, activity.filterImageChip
        };
        for (int i = 0; i < chips.length; i++) {
            View chip = chips[i];
            if (chip == null) continue;
            ViewGroup.LayoutParams rawLp = chip.getLayoutParams();
            if (rawLp == null) continue;
            rawLp.width = slotWidth;
            if (rawLp instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) rawLp;
                mlp.setMargins(i == 0 ? 0 : gap, mlp.topMargin, 0, mlp.bottomMargin);
            }
            chip.setLayoutParams(rawLp);
        }
        View row = activity.findViewById(R.id.file_type_filter_row);
        if (row != null) row.setPadding(0, 0, gap, 0);
        trimFileTypeFilterEdge();
    }

    private void snapFileTypeFilterToSlot() {
        if (!(activity.fileTypeFilterScroll instanceof HorizontalScrollView)) return;
        HorizontalScrollView scroll = (HorizontalScrollView) activity.fileTypeFilterScroll;
        if (scroll.getChildCount() == 0 || scroll.getWidth() <= 0) return;
        int slotWidth = Math.max(1, activity.fileTypeFilterStepPx > 0 ? activity.fileTypeFilterStepPx : scroll.getWidth() / 5);
        int maxScroll = Math.max(0, scroll.getChildAt(0).getWidth() - scroll.getWidth());
        if (maxScroll <= 1) {
            scroll.smoothScrollTo(0, 0);
            return;
        }
        int current = scroll.getScrollX();
        int target = Math.round(current / (float) slotWidth) * slotWidth;
        target = Math.max(0, Math.min(target, maxScroll));
        if (Math.abs(target - current) > 1) {
            scroll.smoothScrollTo(target, 0);
        }
    }

    private void trimFileTypeFilterEdge() {
        if (!(activity.fileTypeFilterScroll instanceof HorizontalScrollView)) return;
        HorizontalScrollView scroll = (HorizontalScrollView) activity.fileTypeFilterScroll;
        scroll.post(() -> {
            int maxScroll = 0;
            if (scroll.getChildCount() > 0) {
                maxScroll = Math.max(0, scroll.getChildAt(0).getWidth() - scroll.getWidth());
            }
            if (maxScroll <= 1) {
                scroll.scrollTo(0, 0);
            } else if (scroll.getScrollX() > maxScroll) {
                scroll.scrollTo(maxScroll, 0);
            }
        });
    }

    void updateFileSearchClearButtonVisibility() {
        if (activity.fileSearchClearButton == null) return;

        boolean hasTypedQuery = activity.fileSearchInput != null
                && activity.fileSearchInput.getText() != null
                && activity.fileSearchInput.getText().toString().trim().length() > 0;

        activity.fileSearchClearButton.setVisibility(hasTypedQuery ? View.VISIBLE : View.GONE);
    }

    private void setupFileTypeChip(TextView chip, int filter) {
        if (chip == null) return;
        chip.setOnClickListener(v -> {
            String query = activity.fileSearchInput != null
                    ? activity.fileSearchInput.getText().toString().trim()
                    : "";

            if (query.isEmpty() && (activity.homeMode || (activity.searchMode && activity.searchReturnToHome))) {
                activity.activeFileFilter = filter;
                clearSearchDebounce();
                activity.fileSearchGeneration.incrementAndGet();
                activity.searchMode = false;
                activity.homeMode = true;
                activity.searchReturnToHome = true;
                activity.searchReturnDirectory = null;
                if (activity.fileSearchProgress != null) activity.fileSearchProgress.setVisibility(View.GONE);
                updateFileSearchClearButtonVisibility();
                if (activity.recentSection != null) activity.recentSection.setVisibility(View.VISIBLE);
                if (activity.browserSection != null) activity.browserSection.setVisibility(View.GONE);
                activity.setPathBarVisible(false);
                if (activity.getSupportActionBar() != null) {
                    activity.getSupportActionBar().setTitle(R.string.app_name);
                }
                updateFileTypeChips();
                activity.loadRecentFiles();
                activity.updateMainOverflowButtonVisibility();
                activity.invalidateOptionsMenu();
                return;
            }

            if (filter == MainActivity.FILTER_ALL && query.isEmpty()) {
                activity.activeFileFilter = MainActivity.FILTER_ALL;
                updateFileTypeChips();
                restoreAllFilterLocation();
                return;
            }

            captureFilterReturnLocationIfNeeded();
            activity.activeFileFilter = filter;
            updateFileTypeChips();
            runLiveFileSearchNow();
        });
    }

    private void captureFilterReturnLocationIfNeeded() {
        if (activity.searchMode) return;
        boolean allStorage = activity.homeMode || activity.currentDirectory == null;
        activity.searchReturnToHome = allStorage;
        activity.searchReturnDirectory = allStorage ? null : activity.currentDirectory;
    }

    void restoreAllFilterLocation() {
        clearSearchDebounce();
        activity.fileSearchGeneration.incrementAndGet();
        if (activity.fileSearchProgress != null) activity.fileSearchProgress.setVisibility(View.GONE);
        updateFileSearchClearButtonVisibility();

        File visibleFolder = activity.currentDirectory;
        if (visibleFolder != null
                && visibleFolder.exists()
                && visibleFolder.isDirectory()
                && visibleFolder.canRead()
                && (!activity.searchReturnToHome || activity.searchMode || !activity.homeMode)) {
            activity.searchMode = false;
            activity.homeMode = false;
            activity.recentSection.setVisibility(View.GONE);
            activity.browserSection.setVisibility(View.VISIBLE);
            activity.setPathBarVisible(true);
            activity.loadDirectory(visibleFolder);
            activity.rebuildDrawerStorageEntries();
            activity.updateMainOverflowButtonVisibility();
            activity.invalidateOptionsMenu();
            return;
        }

        restorePreSearchLocation();
    }

    void updateFileTypeChips() {
        styleFileTypeChip(activity.filterAllChip, activity.activeFileFilter == MainActivity.FILTER_ALL);
        styleFileTypeChip(activity.filterGeneralChip, activity.activeFileFilter == MainActivity.FILTER_GENERAL);
        styleFileTypeChip(activity.filterTxtChip, activity.activeFileFilter == MainActivity.FILTER_TXT);
        styleFileTypeChip(activity.filterArchiveChip, activity.activeFileFilter == MainActivity.FILTER_ARCHIVE);
        styleFileTypeChip(activity.filterPdfChip, activity.activeFileFilter == MainActivity.FILTER_PDF);
        styleFileTypeChip(activity.filterEpubChip, activity.activeFileFilter == MainActivity.FILTER_EPUB);
        styleFileTypeChip(activity.filterWordChip, activity.activeFileFilter == MainActivity.FILTER_WORD);
        styleFileTypeChip(activity.filterImageChip, activity.activeFileFilter == MainActivity.FILTER_IMAGE);
    }

    void resetFileFilterForNavigation() {
        activity.activeFileFilter = MainActivity.FILTER_ALL;

        if (activity.fileSearchInput != null && activity.fileSearchInput.length() > 0) {
            activity.fileSearchInput.setText("");
        }

        clearSearchDebounce();
        activity.fileSearchGeneration.incrementAndGet();

        if (activity.fileSearchProgress != null) {
            activity.fileSearchProgress.setVisibility(View.GONE);
        }
        if (activity.fileSearchClearButton != null) {
            activity.fileSearchClearButton.setVisibility(View.GONE);
        }

        activity.searchReturnToHome = true;
        activity.searchReturnDirectory = null;
        updateFileTypeChips();
    }

    private void styleFileTypeChip(TextView chip, boolean selected) {
        if (chip == null) return;
        boolean dark = activity.prefs == null || activity.prefs.shouldUseDarkColors(activity);
        int bg = selected
                ? (activity.prefs != null ? activity.prefs.getMainFileTypeChipSelectedColor(activity) : (dark ? Color.rgb(72, 72, 72) : Color.rgb(32, 33, 36)))
                : (activity.prefs != null ? activity.prefs.getMainFileTypeChipColor(activity) : (dark ? Color.rgb(30, 30, 30) : Color.rgb(238, 238, 238)));
        int stroke = selected
                ? (activity.prefs != null ? activity.prefs.getMainControlColor(activity) : (dark ? Color.rgb(220, 220, 220) : Color.rgb(32, 33, 36)))
                : (activity.prefs != null ? activity.prefs.getMainOutlineColor(activity) : (dark ? Color.rgb(92, 92, 92) : Color.rgb(190, 190, 190)));
        int fg = selected
                ? UiColorUtils.readableChipTextColorForBackground(bg)
                : (activity.prefs != null ? activity.prefs.getMainTextColor(activity) : (dark ? Color.rgb(232, 234, 237) : Color.rgb(32, 33, 36)));
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(bg);
        shape.setCornerRadius(activity.dpToPx(15));
        shape.setStroke(Math.max(1, activity.dpToPx(1)), stroke);
        chip.setBackground(shape);
        chip.setTextColor(fg);
    }

    void clearSearchDebounce() {
        if (activity.pendingFileSearchRunnable != null) {
            activity.fileSearchHandler.removeCallbacks(activity.pendingFileSearchRunnable);
            activity.pendingFileSearchRunnable = null;
        }
    }

    private void scheduleLiveFileSearch() {
        if (activity.activityDestroyed) return;
        clearSearchDebounce();
        activity.pendingFileSearchRunnable = this::runLiveFileSearchNow;
        activity.fileSearchHandler.postDelayed(activity.pendingFileSearchRunnable, 180);
    }

    void runLiveFileSearchNow() {
        clearSearchDebounce();
        if (activity.activityDestroyed || activity.fileSearchInput == null) return;
        String query = activity.fileSearchInput.getText().toString().trim();

        if (query.isEmpty() && activity.homeMode) {
            activity.fileSearchGeneration.incrementAndGet();
            if (activity.fileSearchProgress != null) activity.fileSearchProgress.setVisibility(View.GONE);
            updateFileSearchClearButtonVisibility();
            if (activity.activeFileFilter == MainActivity.FILTER_ALL) {
                if (activity.searchMode) restorePreSearchLocation();
            } else {
                activity.searchMode = false;
                activity.loadRecentFiles();
            }
            return;
        }

        if (query.isEmpty() && activity.activeFileFilter == MainActivity.FILTER_ALL) {
            activity.fileSearchGeneration.incrementAndGet();
            if (activity.fileSearchProgress != null) activity.fileSearchProgress.setVisibility(View.GONE);
            updateFileSearchClearButtonVisibility();
            if (activity.searchMode) restorePreSearchLocation();
            return;
        }

        boolean allStorage = activity.homeMode || activity.currentDirectory == null;
        if (!activity.searchMode) {
            activity.searchReturnToHome = allStorage;
            activity.searchReturnDirectory = allStorage ? null : activity.currentDirectory;
        }
        startFileSearch(query, allStorage);
    }

    void restorePreSearchLocation() {
        activity.fileSearchGeneration.incrementAndGet();
        activity.searchMode = false;
        activity.activeFileFilter = MainActivity.FILTER_ALL;
        updateFileTypeChips();

        File target = activity.searchReturnDirectory;
        if (!activity.searchReturnToHome
                && target != null
                && target.exists()
                && target.isDirectory()
                && target.canRead()) {
            activity.showBrowseMode(target);
        } else {
            activity.showHomeMode();
        }
    }

    private void startFileSearch(@NonNull String query, boolean allStorage) {
        activity.cancelPendingFolderLoad();
        final int generation = activity.fileSearchGeneration.incrementAndGet();
        if (activity.fileSearchProgress != null) activity.fileSearchProgress.setVisibility(View.VISIBLE);
        if (activity.fileSearchClearButton != null) {
            activity.fileSearchClearButton.setEnabled(true);
        }
        updateFileSearchClearButtonVisibility();

        List<File> roots = buildSearchRoots(allStorage);
        final int filter = activity.activeFileFilter;
        activity.fileSearchExecutor.execute(() -> {
            if (activity.activityDestroyed || generation != activity.fileSearchGeneration.get()) return;
            List<File> results = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            String needle = query.toLowerCase(java.util.Locale.ROOT);
            boolean showHidden = activity.prefs == null || activity.prefs.getShowHiddenFiles();
            int[] visited = new int[]{0};

            for (File root : roots) {
                if (root == null || !root.exists() || !root.canRead()) continue;
                searchFilesRecursive(root, needle, filter, showHidden, seen, results, visited, generation, 0);
                if (generation != activity.fileSearchGeneration.get() || results.size() >= 300 || visited[0] >= 12000) break;
            }

            activity.runOnUiThread(() -> {
                if (activity.activityDestroyed || generation != activity.fileSearchGeneration.get()) return;
                if (activity.fileSearchProgress != null) activity.fileSearchProgress.setVisibility(View.GONE);
                showFileSearchResults(query, results);
            });
        });
    }

    private List<File> buildSearchRoots(boolean allStorage) {
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        if (!allStorage && !activity.homeMode && activity.currentDirectory != null && activity.currentDirectory.exists()) {
            paths.add(activity.currentDirectory.getAbsolutePath());
        } else {
            File internal = Environment.getExternalStorageDirectory();
            if (internal != null) paths.add(internal.getAbsolutePath());
            File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (downloads != null) paths.add(downloads.getAbsolutePath());
            for (File sd : activity.detectExternalSdCards()) paths.add(sd.getAbsolutePath());
            if (activity.prefs != null) paths.addAll(activity.prefs.getRecentFolders(10));
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
        if (generation != activity.fileSearchGeneration.get() || depth > 16 || results.size() >= 300 || visited[0] >= 12000) return;
        File[] children = dir.listFiles();
        if (children == null) return;

        for (File child : children) {
            if (generation != activity.fileSearchGeneration.get()) return;
            if (child == null) continue;
            visited[0]++;
            if (visited[0] >= 12000 || results.size() >= 300) return;
            String name = child.getName();
            if (!showHidden && name.startsWith(".")) continue;
            String path = child.getAbsolutePath();
            if (path.contains("/Android/data/") || path.contains("/Android/obb/")) continue;

            boolean nameMatch = needle.isEmpty() || name.toLowerCase(java.util.Locale.ROOT).contains(needle);
            boolean fileMatch = !child.isDirectory() && FileTypeFilter.matches(name, filter);
            boolean directoryMatch = child.isDirectory() && !needle.isEmpty()
                    && filter == MainActivity.FILTER_ALL
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
        return activity.getString(FileTypeFilter.labelResId(filter));
    }

    private void showFileSearchResults(@NonNull String query, @NonNull List<File> results) {
        activity.exitFileSelectionMode(false);
        activity.searchMode = true;
        activity.homeMode = false;
        activity.recentSection.setVisibility(View.GONE);
        activity.browserSection.setVisibility(View.VISIBLE);
        activity.setPathBarVisible(true);
        activity.pathText.setText(activity.getString(R.string.file_search_results_for,
                query.isEmpty() ? filterLabelFor(activity.activeFileFilter) : query,
                results.size()));
        if (activity.getSupportActionBar() != null) activity.getSupportActionBar().setTitle(R.string.file_search);
        updateFileSearchClearButtonVisibility();

        activity.fileAdapter.setFiles(results);
        activity.scrollListToTop(activity.fileRecyclerView);
        activity.emptyText.setVisibility(results.isEmpty() ? View.VISIBLE : View.GONE);
        if (results.isEmpty()) activity.emptyText.setText(activity.getString(R.string.no_file_search_results));
        updateFileSearchClearButtonVisibility();
        activity.updateMainOverflowButtonVisibility();
        activity.invalidateOptionsMenu();
    }
}
