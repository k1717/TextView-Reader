package com.textview.reader;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.textview.reader.adapter.FileAdapter;
import com.textview.reader.util.FileClipboardController;
import com.textview.reader.util.FileOperationProgress;
import com.textview.reader.util.FileSystemOps;

import java.io.File;
import java.util.ArrayList;

final class MainSelectionModeController {
    private final MainActivity activity;

    MainSelectionModeController(@NonNull MainActivity activity) {
        this.activity = activity;
    }

    void enterFileSelectionMode(@NonNull File firstFile) {
        if (activity.fileSelectionMode) {
            activity.selectedFilePaths.add(firstFile.getAbsolutePath());
            updateFileSelectionUi();
            return;
        }
        activity.fileSelectionMode = true;
        activity.selectedFilePaths.clear();
        activity.selectedFilePaths.add(firstFile.getAbsolutePath());
        updateFileSelectionUi();
    }

    void toggleFileSelection(@NonNull File file) {
        String path = file.getAbsolutePath();
        if (activity.selectedFilePaths.contains(path)) {
            activity.selectedFilePaths.remove(path);
        } else {
            activity.selectedFilePaths.add(path);
        }
        if (activity.selectedFilePaths.isEmpty()) {
            exitFileSelectionMode(true);
        } else {
            updateFileSelectionUi();
        }
    }

    void exitFileSelectionMode(boolean restoreToolbar) {
        boolean wasSelectionMode = activity.fileSelectionMode || !activity.selectedFilePaths.isEmpty();
        if (!wasSelectionMode) return;
        activity.fileSelectionMode = false;
        activity.selectedFilePaths.clear();
        updateFileSelectionUi();
        if (restoreToolbar) restoreMainToolbarAfterSelection();
        else activity.installToolbarMenuButton(activity.mainToolbar);
    }

    private void updateFileSelectionUi() {
        if (activity.fileAdapter != null) activity.fileAdapter.setSelectionState(activity.fileSelectionMode, activity.selectedFilePaths);
        if (activity.recentAdapter != null) activity.recentAdapter.setSelectionState(activity.fileSelectionMode, activity.selectedFilePaths);
        if (activity.fileSelectionMode) applyFileSelectionToolbar();
        else activity.updateMainOverflowButtonVisibility();
    }

    void applyFileSelectionToolbar() {
        Toolbar toolbar = activity.mainToolbar;
        if (toolbar == null) return;
        if (activity.drawerToggle != null) activity.drawerToggle.setDrawerIndicatorEnabled(false);
        Drawable nav = ContextCompat.getDrawable(activity, R.drawable.ic_bottom_arrow_left);
        if (nav != null) {
            Drawable wrapped = DrawableCompat.wrap(nav.mutate());
            DrawableCompat.setTint(wrapped, Color.WHITE);
            toolbar.setNavigationIcon(wrapped);
        }
        toolbar.setNavigationContentDescription(R.string.clear_selection);
        toolbar.setNavigationOnClickListener(v -> exitFileSelectionMode(true));
        if (activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setTitle(activity.getString(R.string.file_selection_count, activity.selectedFilePaths.size()));
        } else {
            toolbar.setTitle(activity.getString(R.string.file_selection_count, activity.selectedFilePaths.size()));
        }
        activity.updateMainOverflowButtonVisibility();
    }

    private void restoreMainToolbarAfterSelection() {
        activity.installToolbarMenuButton(activity.mainToolbar);
        if (activity.getSupportActionBar() == null) return;
        if (activity.searchMode) {
            activity.getSupportActionBar().setTitle(R.string.file_search);
        } else if (activity.homeMode) {
            activity.getSupportActionBar().setTitle(R.string.app_name);
        } else if (activity.currentDirectory != null) {
            activity.getSupportActionBar().setTitle(activity.currentDirectory.getName().isEmpty() ? "/" : activity.currentDirectory.getName());
        } else {
            activity.getSupportActionBar().setTitle(R.string.app_name);
        }
    }

    @NonNull
    ArrayList<File> getSelectedFilesSnapshot() {
        ArrayList<File> result = new ArrayList<>();
        for (String path : new ArrayList<>(activity.selectedFilePaths)) {
            if (path == null || path.trim().isEmpty()) continue;
            result.add(new File(path));
        }
        return result;
    }

    @NonNull
    ArrayList<File> getSelectedShareableFilesSnapshot() {
        ArrayList<File> result = new ArrayList<>();
        for (File file : getSelectedFilesSnapshot()) {
            if (file != null && file.exists() && file.isFile() && file.canRead()) {
                result.add(file);
            }
        }
        return result;
    }

    @NonNull
    ArrayList<File> getSelectedArchiveFilesSnapshot() {
        ArrayList<File> selected = getSelectedFilesSnapshot();
        ArrayList<File> archives = new ArrayList<>();
        if (selected.isEmpty()) return archives;
        for (File file : selected) {
            if (file == null
                    || !file.exists()
                    || !file.isFile()
                    || !file.canRead()
                    || !activity.isSupportedArchive(file)) {
                archives.clear();
                return archives;
            }
            archives.add(file);
        }
        return archives;
    }

    @Nullable
    File getSingleSelectedFile() {
        ArrayList<File> selected = getSelectedFilesSnapshot();
        return selected.size() == 1 ? selected.get(0) : null;
    }

    void selectAllVisibleFiles() {
        FileAdapter visibleAdapter = activity.homeMode && !activity.searchMode ? activity.recentAdapter : activity.fileAdapter;
        if (visibleAdapter == null) return;
        for (File file : visibleAdapter.getFilesSnapshot()) {
            if (file != null) activity.selectedFilePaths.add(file.getAbsolutePath());
        }
        if (!activity.selectedFilePaths.isEmpty()) {
            activity.fileSelectionMode = true;
            updateFileSelectionUi();
        }
    }

    void startSelectedClipboardOperation(boolean copy) {
        ArrayList<File> selected = getSelectedFilesSnapshot();
        int started = 0;
        for (File file : selected) {
            if (file == null || !file.exists() || !(file.isFile() || file.isDirectory())) continue;
            FileClipboardController.StartResult result = activity.fileClipboardController.start(file, copy);
            if (result == FileClipboardController.StartResult.STARTED) started++;
        }
        if (started <= 0) {
            ShortToast.show(activity, R.string.file_operation_source_unavailable);
            exitFileSelectionMode(true);
            return;
        }
        activity.archiveExtractInProgress = false;
        exitFileSelectionMode(true);
        activity.updateMainOverflowButtonVisibility();
        Toast.makeText(activity,
                activity.getString(copy ? R.string.selected_files_copy_started : R.string.selected_files_move_started, started),
                Toast.LENGTH_LONG).show();
    }

    void startSelectedArchiveExtraction() {
        ArrayList<File> archives = getSelectedArchiveFilesSnapshot();
        if (archives.isEmpty()) {
            ShortToast.show(activity, R.string.archive_extract_failed);
            exitFileSelectionMode(true);
            return;
        }
        exitFileSelectionMode(true);
        activity.startArchiveExtractions(archives);
    }

    void showSelectedDeleteConfirm() {
        ArrayList<File> selected = getSelectedFilesSnapshot();
        if (selected.isEmpty()) {
            exitFileSelectionMode(true);
            return;
        }
        activity.showSimpleConfirmDialog(
                activity.getString(R.string.delete),
                activity.getString(R.string.delete_selected_files_confirm, selected.size()),
                activity.getString(R.string.delete),
                () -> deleteSelectedFiles(selected));
    }

    private int countSelectedFolders(@NonNull ArrayList<File> selected) {
        int count = 0;
        for (File file : selected) {
            if (file != null && file.isDirectory()) count++;
        }
        return count;
    }

    private int countExistingSelectedItems(@NonNull ArrayList<File> selected) {
        int count = 0;
        for (File file : selected) {
            if (file != null && file.exists()) count++;
        }
        return count;
    }

    private void deleteSelectedFiles(@NonNull ArrayList<File> selected) {
        if (selected.isEmpty()) return;
        ArrayList<String> deletedPaths = new ArrayList<>();
        FileOperationProgress progress = activity.showFileOperationProgress(
                activity.getString(R.string.file_deleting),
                activity.getString(R.string.file_selection_count, selected.size()));
        if (activity.currentDirectory != null) {
            progress.setFolder(activity.currentDirectory.getName().length() > 0
                    ? activity.currentDirectory.getName()
                    : activity.currentDirectory.getAbsolutePath());
        }
        activity.executeFolderBackgroundTask(() -> {
            long totalBytes = 0L;
            for (File file : selected) {
                if (file == null) continue;
                totalBytes += FileSystemOps.measureBytes(file);
                if (totalBytes < 0L) {
                    totalBytes = Long.MAX_VALUE;
                    break;
                }
            }
            progress.setTotalBytes(totalBytes);
            int deletedCount = 0;
            int itemIndex = 0;
            int itemTotal = countExistingSelectedItems(selected);
            int folderIndex = 0;
            int folderTotal = countSelectedFolders(selected);
            if (folderTotal <= 0) progress.clearFolderProgress();
            for (File file : selected) {
                if (file == null || !file.exists()) continue;
                String path = file.getAbsolutePath();
                boolean wasDirectory = file.isDirectory();
                progress.setItemProgress(++itemIndex, itemTotal);
                if (wasDirectory) progress.setFolderProgress(++folderIndex, folderTotal);
                else if (folderTotal > 0) progress.clearFolderProgress();
                if (FileSystemOps.delete(file, progress, false)) {
                    deletedCount++;
                    deletedPaths.add(path);
                    if (activity.bookmarkManager != null) activity.bookmarkManager.deleteReadingState(path);
                    activity.cleanupNavigationStateAfterDelete(path, wasDirectory);
                }
                if (progress.isCancelled()) break;
            }
            final int finalDeletedCount = deletedCount;
            activity.fileSearchHandler.post(() -> {
                activity.finishFileOperationProgress(progress);
                if (progress.isCancelled()) {
                    exitFileSelectionMode(true);
                    activity.refreshVisibleFileListAfterDelete();
                    ShortToast.show(activity, R.string.file_operation_cancelled);
                    return;
                }
                exitFileSelectionMode(true);
                if (activity.currentDirectory != null && activity.currentDirectory.exists() && activity.currentDirectory.isDirectory() && !activity.homeMode) {
                    activity.loadDirectory(activity.currentDirectory);
                } else {
                    activity.loadRecentFiles();
                }
                activity.rebuildDrawerStorageEntries();
                ShortToast.show(activity, finalDeletedCount > 0
                                ? activity.getString(R.string.selected_files_deleted, finalDeletedCount)
                                : activity.getString(R.string.selected_files_delete_failed));
            });
        });
    }
}
