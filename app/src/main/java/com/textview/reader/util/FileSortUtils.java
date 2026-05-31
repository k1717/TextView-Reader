package com.textview.reader.util;

import androidx.annotation.NonNull;

import com.textview.reader.archive.ArchiveSupport;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Shared sorting code for main file lists and archive previews. */
public final class FileSortUtils {
    private FileSortUtils() {}

    public static void sortMainFiles(@NonNull List<File> target, int sortMode) {
        Collections.sort(target, (a, b) -> {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            switch (sortMode) {
                case PrefsManager.SORT_NAME_DESC:
                    return NaturalSort.compare(b.getName(), a.getName());
                case PrefsManager.SORT_DATE_NEW: {
                    int cmp = Long.compare(b.lastModified(), a.lastModified());
                    return cmp != 0 ? cmp : NaturalSort.compare(a.getName(), b.getName());
                }
                case PrefsManager.SORT_DATE_OLD: {
                    int cmp = Long.compare(a.lastModified(), b.lastModified());
                    return cmp != 0 ? cmp : NaturalSort.compare(a.getName(), b.getName());
                }
                case PrefsManager.SORT_SIZE_LARGE: {
                    int cmp = Long.compare(fileSortSize(b), fileSortSize(a));
                    return cmp != 0 ? cmp : NaturalSort.compare(a.getName(), b.getName());
                }
                case PrefsManager.SORT_SIZE_SMALL: {
                    int cmp = Long.compare(fileSortSize(a), fileSortSize(b));
                    return cmp != 0 ? cmp : NaturalSort.compare(a.getName(), b.getName());
                }
                case PrefsManager.SORT_TYPE: {
                    int cmp = fileExtension(a.getName()).compareTo(fileExtension(b.getName()));
                    return cmp != 0 ? cmp : NaturalSort.compare(a.getName(), b.getName());
                }
                case PrefsManager.SORT_NAME_ASC:
                default:
                    return NaturalSort.compare(a.getName(), b.getName());
            }
        });
    }

    public static void sortArchiveEntries(@NonNull List<ArchiveSupport.EntryInfo> target, int sortMode) {
        Collections.sort(target, (a, b) -> {
            if (a.directory != b.directory) return a.directory ? -1 : 1;
            switch (sortMode) {
                case PrefsManager.SORT_NAME_DESC:
                    return NaturalSort.compare(b.name(), a.name());
                case PrefsManager.SORT_DATE_NEW: {
                    int cmp = Long.compare(b.timeMillis, a.timeMillis);
                    return cmp != 0 ? cmp : NaturalSort.compare(a.name(), b.name());
                }
                case PrefsManager.SORT_DATE_OLD: {
                    int cmp = Long.compare(a.timeMillis, b.timeMillis);
                    return cmp != 0 ? cmp : NaturalSort.compare(a.name(), b.name());
                }
                case PrefsManager.SORT_SIZE_LARGE: {
                    int cmp = Long.compare(b.size, a.size);
                    return cmp != 0 ? cmp : NaturalSort.compare(a.name(), b.name());
                }
                case PrefsManager.SORT_SIZE_SMALL: {
                    int cmp = Long.compare(a.size, b.size);
                    return cmp != 0 ? cmp : NaturalSort.compare(a.name(), b.name());
                }
                case PrefsManager.SORT_TYPE: {
                    int cmp = fileExtension(a.name()).compareTo(fileExtension(b.name()));
                    return cmp != 0 ? cmp : NaturalSort.compare(a.name(), b.name());
                }
                case PrefsManager.SORT_NAME_ASC:
                default:
                    return NaturalSort.compare(a.name(), b.name());
            }
        });
    }

    public static void sortArchiveImagesForComic(@NonNull List<ArchiveSupport.EntryInfo> target) {
        Collections.sort(target, (a, b) -> NaturalSort.compare(a.path, b.path));
    }

    private static long fileSortSize(@NonNull File file) {
        return file.isDirectory() ? 0L : file.length();
    }

    @NonNull
    private static String fileExtension(@NonNull String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot >= name.length() - 1) return "";
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
