package com.textview.reader.util;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Low-level file-system operations shared by main-screen file actions.
 *
 * This class intentionally contains no Android UI code. Callers decide how to
 * show progress, conflicts, errors, and bookmark/recent-file side effects.
 */
public final class FileSystemOps {

    private static final int COPY_BUFFER_BYTES = 1024 * 64;

    private FileSystemOps() {
    }

    public static boolean move(@NonNull File source,
                               @NonNull File destination,
                               boolean overwrite) {
        if (destination.exists()) {
            if (!overwrite || sameCanonicalFile(source, destination)) return false;
            if (!delete(destination)) return false;
        }

        try {
            if (source.renameTo(destination)) return true;
        } catch (SecurityException ignored) {
        }

        boolean copied = copy(source, destination, false);
        if (!copied) return false;

        return delete(source);
    }

    public static boolean copy(@NonNull File source,
                               @NonNull File destination,
                               boolean overwrite) {
        if (!source.exists()) return false;
        if (destination.exists()) {
            if (!overwrite || sameCanonicalFile(source, destination)) return false;
            if (!delete(destination)) return false;
        }
        if (source.isDirectory()) {
            return copyDirectoryRecursively(source, destination);
        }
        if (source.isFile()) {
            return copyRegularFile(source, destination);
        }
        return false;
    }

    public static boolean delete(@NonNull File target) {
        if (!target.exists()) return true;
        if (target.isDirectory()) {
            File[] children;
            try {
                children = target.listFiles();
            } catch (SecurityException ignored) {
                return false;
            }
            if (children == null) return false;
            for (File child : children) {
                if (!delete(child)) return false;
            }
        }
        try {
            return target.delete();
        } catch (SecurityException ignored) {
            return false;
        }
    }

    public static boolean isSameOrDescendant(@NonNull File ancestor,
                                             @NonNull File candidate) {
        try {
            File ancestorCanonical = ancestor.getCanonicalFile();
            File current = candidate.getCanonicalFile();
            while (current != null) {
                if (ancestorCanonical.equals(current)) return true;
                current = current.getParentFile();
            }
            return false;
        } catch (IOException ignored) {
            String ancestorPath = ancestor.getAbsolutePath();
            String candidatePath = candidate.getAbsolutePath();
            return candidatePath.equals(ancestorPath)
                    || candidatePath.startsWith(ancestorPath + File.separator);
        }
    }

    public static boolean sameCanonicalFile(@NonNull File a, @NonNull File b) {
        try {
            return a.getCanonicalFile().equals(b.getCanonicalFile());
        } catch (IOException ignored) {
            return a.getAbsolutePath().equals(b.getAbsolutePath());
        }
    }

    private static boolean copyDirectoryRecursively(@NonNull File sourceDir,
                                                    @NonNull File destinationDir) {
        if (!sourceDir.exists() || !sourceDir.isDirectory()) return false;
        if (isSameOrDescendant(sourceDir, destinationDir)) return false;
        if (!destinationDir.exists()) {
            try {
                if (!destinationDir.mkdirs()) return false;
            } catch (SecurityException ignored) {
                return false;
            }
        }
        File[] children;
        try {
            children = sourceDir.listFiles();
        } catch (SecurityException ignored) {
            return false;
        }
        if (children == null) return false;
        for (File child : children) {
            File childDestination = new File(destinationDir, child.getName());
            boolean ok = child.isDirectory()
                    ? copyDirectoryRecursively(child, childDestination)
                    : copyRegularFile(child, childDestination);
            if (!ok) {
                delete(destinationDir);
                return false;
            }
        }
        return true;
    }

    private static boolean copyRegularFile(@NonNull File source,
                                           @NonNull File destination) {
        File parent = destination.getParentFile();
        if (parent == null || !parent.exists() || !parent.isDirectory()) return false;
        byte[] buffer = new byte[COPY_BUFFER_BYTES];
        boolean copied = false;
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(destination)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
            copied = destination.length() == source.length();
        } catch (IOException | SecurityException ignored) {
            copied = false;
        }

        if (!copied) {
            try {
                destination.delete();
            } catch (SecurityException ignored) {
            }
        }
        return copied;
    }
}
