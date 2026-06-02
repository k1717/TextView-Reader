package com.textview.reader;

import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.textview.reader.archive.ArchiveSupport;
import com.textview.reader.util.FileOperationProgress;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class MainArchiveCreateController {
    private final MainActivity activity;

    MainArchiveCreateController(@NonNull MainActivity activity) {
        this.activity = activity;
    }

    void startArchiveCreation(@NonNull File source) {
        ArrayList<File> sources = new ArrayList<>();
        sources.add(source);
        startArchiveCreation(sources);
    }

    void startArchiveCreation(@NonNull List<File> sources) {
        ArrayList<File> ready = collectReadySources(sources);
        if (ready.isEmpty()) {
            ShortToast.show(activity, R.string.archive_create_failed);
            return;
        }
        File parent = ready.get(0).getParentFile();
        if (parent == null || !parent.exists() || !parent.isDirectory() || !parent.canWrite()) {
            ShortToast.show(activity, R.string.file_move_destination_unavailable);
            return;
        }
        File destination = buildArchiveDestination(parent, ready);
        if (destination == null) {
            ShortToast.show(activity, R.string.archive_create_failed);
            return;
        }
        addPendingArchiveCreation(new MainPendingArchiveCreation(ready, destination, parent));
        activity.archiveCreateInProgress = false;
        activity.updateMainOverflowButtonVisibility();
        Toast.makeText(activity, R.string.archive_create_queued, Toast.LENGTH_LONG).show();

        if (parent.exists() && parent.isDirectory() && parent.canRead()) {
            activity.resetMainBrowseFiltersAndShow(parent, null);
        }
    }

    boolean setActivePendingArchiveCreation(@Nullable MainPendingArchiveCreation task) {
        if (task == null) return false;
        for (MainPendingArchiveCreation pending : activity.pendingArchiveCreations) {
            if (pending == task) {
                activity.pendingArchiveCreation = pending;
                return true;
            }
        }
        return false;
    }

    void cancelPendingArchiveCreation(@Nullable MainPendingArchiveCreation task) {
        removePendingArchiveCreation(task);
        activity.archiveCreateInProgress = false;
        activity.updateMainOverflowButtonVisibility();
        ShortToast.show(activity, R.string.file_operation_cancelled);
    }

    void confirmPendingArchiveCreation() {
        MainPendingArchiveCreation task = activity.pendingArchiveCreation;
        if (task == null) return;
        ArrayList<File> ready = collectReadySources(task.sources);
        if (ready.isEmpty() || !isWritableParent(task.parent)) {
            removePendingArchiveCreation(task);
            activity.updateMainOverflowButtonVisibility();
            ShortToast.show(activity, R.string.archive_create_failed);
            return;
        }
        File destination = freshDestinationFor(task.parent, ready, task.destination);
        if (destination == null) {
            ShortToast.show(activity, R.string.archive_create_failed);
            return;
        }
        activity.showSimpleConfirmDialog(
                activity.getString(R.string.archive_create_title),
                activity.getString(R.string.archive_create_message,
                        ready.size(),
                        destination.getName()),
                activity.getString(R.string.archive_create),
                () -> createArchive(task, ready, destination, task.parent));
    }

    void confirmAllPendingArchiveCreations() {
        if (activity.archiveCreateInProgress) return;
        ArrayList<MainPendingArchiveCreation> tasks = new ArrayList<>(activity.pendingArchiveCreations);
        if (tasks.isEmpty()) return;
        activity.showSimpleConfirmDialog(
                activity.getString(R.string.archive_create_all_title),
                activity.getString(R.string.archive_create_all_message, tasks.size()),
                activity.getString(R.string.archive_create_all),
                () -> continueAllArchiveCreations(tasks));
    }

    private void addPendingArchiveCreation(@NonNull MainPendingArchiveCreation task) {
        removePendingArchiveCreationWithSameDestination(task.destination);
        activity.pendingArchiveCreations.add(0, task);
        activity.pendingArchiveCreation = task;
    }

    private void removePendingArchiveCreation(@Nullable MainPendingArchiveCreation task) {
        if (task == null) return;
        for (int i = activity.pendingArchiveCreations.size() - 1; i >= 0; i--) {
            if (activity.pendingArchiveCreations.get(i) == task) activity.pendingArchiveCreations.remove(i);
        }
        if (activity.pendingArchiveCreation == task) {
            activity.pendingArchiveCreation = activity.pendingArchiveCreations.isEmpty()
                    ? null
                    : activity.pendingArchiveCreations.get(0);
        }
    }

    private void removePendingArchiveCreationWithSameDestination(@NonNull File destination) {
        String path = destination.getAbsolutePath();
        for (int i = activity.pendingArchiveCreations.size() - 1; i >= 0; i--) {
            MainPendingArchiveCreation pending = activity.pendingArchiveCreations.get(i);
            if (pending.destination.getAbsolutePath().equals(path)) activity.pendingArchiveCreations.remove(i);
        }
        if (activity.pendingArchiveCreation != null
                && activity.pendingArchiveCreation.destination.getAbsolutePath().equals(path)) {
            activity.pendingArchiveCreation = activity.pendingArchiveCreations.isEmpty()
                    ? null
                    : activity.pendingArchiveCreations.get(0);
        }
    }

    @NonNull
    private ArrayList<File> collectReadySources(@NonNull List<File> sources) {
        ArrayList<File> ready = new ArrayList<>();
        for (File source : sources) {
            if (source == null || !source.exists() || !source.canRead()) continue;
            if (!source.isFile() && !source.isDirectory()) continue;
            ready.add(source);
        }
        return ready;
    }

    private boolean isWritableParent(@Nullable File parent) {
        return parent != null && parent.exists() && parent.isDirectory() && parent.canWrite();
    }

    @Nullable
    private File freshDestinationFor(@NonNull File parent,
                                     @NonNull List<File> sources,
                                     @NonNull File preferred) {
        if (!preferred.exists()) return preferred;
        return buildArchiveDestination(parent, sources);
    }

    private void createArchive(@NonNull MainPendingArchiveCreation task,
                               @NonNull ArrayList<File> sources,
                               @NonNull File destination,
                               @NonNull File parent) {
        if (activity.archiveCreateInProgress) return;
        activity.archiveCreateInProgress = true;
        activity.updateMainOverflowButtonVisibility();
        FileOperationProgress progress = activity.showFileOperationProgress(
                activity.getString(R.string.archive_creating),
                destination.getName());
        progress.setFolder(parent.getName().length() > 0 ? parent.getName() : parent.getAbsolutePath());
        progress.setItemProgress(1, 1);
        activity.executeFolderBackgroundTask(() -> {
            boolean done = ArchiveSupport.createZipArchive(sources, destination, progress);
            activity.fileSearchHandler.post(() -> {
                activity.finishFileOperationProgress(progress);
                activity.archiveCreateInProgress = false;
                if (activity.activityDestroyed) return;
                if (progress.isCancelled()) {
                    ShortToast.show(activity, R.string.file_operation_cancelled);
                    activity.updateMainOverflowButtonVisibility();
                    return;
                }
                if (!done) {
                    ShortToast.show(activity, R.string.archive_create_failed);
                    activity.updateMainOverflowButtonVisibility();
                    return;
                }
                removePendingArchiveCreation(task);
                if (activity.prefs != null) activity.prefs.addRecentFolder(parent.getAbsolutePath());
                activity.resetMainBrowseFiltersAndShow(parent, destination.getAbsolutePath());
                activity.rebuildDrawerStorageEntries();
                activity.updateMainOverflowButtonVisibility();
                ShortToast.show(activity, R.string.archive_created);
            });
        });
    }

    private void continueAllArchiveCreations(@NonNull ArrayList<MainPendingArchiveCreation> tasks) {
        if (activity.archiveCreateInProgress) return;
        activity.archiveCreateInProgress = true;
        activity.updateMainOverflowButtonVisibility();
        FileOperationProgress progress = activity.showFileOperationProgress(
                activity.getString(R.string.archive_creating),
                activity.getString(R.string.archive_create_all));
        progress.setFolder(activity.getString(R.string.pending_actions_title));
        activity.executeFolderBackgroundTask(() -> {
            int doneCount = 0;
            File lastParent = null;
            File lastDestination = null;
            ArrayList<MainPendingArchiveCreation> succeededTasks = new ArrayList<>();
            int itemIndex = 0;
            final int itemTotal = tasks.size();
            for (MainPendingArchiveCreation task : tasks) {
                if (progress.isCancelled()) break;
                ArrayList<File> ready = collectReadySources(task.sources);
                if (ready.isEmpty() || !isWritableParent(task.parent)) continue;
                File destination = freshDestinationFor(task.parent, ready, task.destination);
                if (destination == null) continue;
                progress.setDetail(destination.getName());
                progress.setItemProgress(++itemIndex, itemTotal);
                boolean done = ArchiveSupport.createZipArchive(ready, destination, progress);
                if (done) {
                    doneCount++;
                    succeededTasks.add(task);
                    lastParent = task.parent;
                    lastDestination = destination;
                }
            }
            final int finalDoneCount = doneCount;
            final File finalLastParent = lastParent;
            final File finalLastDestination = lastDestination;
            final ArrayList<MainPendingArchiveCreation> finalSucceededTasks = succeededTasks;
            activity.fileSearchHandler.post(() -> {
                activity.finishFileOperationProgress(progress);
                activity.archiveCreateInProgress = false;
                if (activity.activityDestroyed) return;
                for (MainPendingArchiveCreation task : finalSucceededTasks) removePendingArchiveCreation(task);
                if (progress.isCancelled()) {
                    ShortToast.show(activity, R.string.file_operation_cancelled);
                } else if (finalDoneCount == tasks.size()) {
                    ShortToast.show(activity, activity.getString(R.string.archive_create_all_done, finalDoneCount, tasks.size()));
                } else if (finalDoneCount > 0) {
                    ShortToast.show(activity, activity.getString(R.string.archive_create_all_partial, finalDoneCount, tasks.size()));
                } else {
                    ShortToast.show(activity, R.string.archive_create_failed);
                }
                if (finalLastParent != null && finalLastDestination != null) {
                    if (activity.prefs != null) activity.prefs.addRecentFolder(finalLastParent.getAbsolutePath());
                    activity.resetMainBrowseFiltersAndShow(finalLastParent, finalLastDestination.getAbsolutePath());
                    activity.rebuildDrawerStorageEntries();
                } else {
                    activity.updateMainOverflowButtonVisibility();
                }
            });
        });
    }

    @Nullable
    private File buildArchiveDestination(@NonNull File parent, @NonNull List<File> sources) {
        String base = sources.size() == 1 ? stripExtension(sources.get(0).getName()) : activity.getString(R.string.archive_create_default_name);
        base = sanitizeBaseName(base);
        if (base.length() == 0) base = activity.getString(R.string.archive_create_default_name);
        for (int i = 0; i < 10000; i++) {
            String name = i == 0 ? base + ".zip" : base + " (" + i + ").zip";
            File candidate = new File(parent, name);
            if (!candidate.exists()) return candidate;
        }
        return null;
    }

    @NonNull
    private static String stripExtension(@NonNull String name) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0) return name;
        return name.substring(0, dot);
    }

    @NonNull
    private static String sanitizeBaseName(@NonNull String raw) {
        String value = raw.trim();
        value = value.replaceAll("[\\\\/:*?\"<>|]", "_");
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.equals(".") || lower.equals("..")) return "";
        return value;
    }
}
