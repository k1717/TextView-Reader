package com.textview.reader;

import androidx.annotation.NonNull;

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
        activity.showSimpleConfirmDialog(
                activity.getString(R.string.archive_create_title),
                activity.getString(R.string.archive_create_message,
                        ready.size(),
                        destination.getName()),
                activity.getString(R.string.archive_create),
                () -> createArchive(ready, destination, parent));
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

    private void createArchive(@NonNull ArrayList<File> sources,
                               @NonNull File destination,
                               @NonNull File parent) {
        FileOperationProgress progress = activity.showFileOperationProgress(
                activity.getString(R.string.archive_creating),
                destination.getName());
        progress.setFolder(parent.getName().length() > 0 ? parent.getName() : parent.getAbsolutePath());
        activity.executeFolderBackgroundTask(() -> {
            boolean done = ArchiveSupport.createZipArchive(sources, destination, progress);
            activity.fileSearchHandler.post(() -> {
                activity.finishFileOperationProgress(progress);
                if (activity.activityDestroyed) return;
                if (progress.isCancelled()) {
                    ShortToast.show(activity, R.string.file_operation_cancelled);
                    return;
                }
                if (!done) {
                    ShortToast.show(activity, R.string.archive_create_failed);
                    return;
                }
                if (activity.prefs != null) activity.prefs.addRecentFolder(parent.getAbsolutePath());
                activity.resetMainBrowseFiltersAndShow(parent, destination.getAbsolutePath());
                activity.rebuildDrawerStorageEntries();
                ShortToast.show(activity, R.string.archive_created);
            });
        });
    }

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
