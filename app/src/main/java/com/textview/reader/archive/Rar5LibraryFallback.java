package com.textview.reader.archive;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.textview.reader.util.FileOperationProgress;

/**
 * Optional RAR5 extraction bridge for RealBurst/unrar5j.
 *
 * <p>The app keeps this bridge reflection-based so the normal build remains usable when the
 * optional RAR5 decoder jar is not bundled. Drop unrar5j-v1.0.3.jar into app/libs, or run the
 * helper Gradle task documented in app/libs/UNRAR5J_README.md, to activate compressed RAR5
 * preview/extraction at runtime.</p>
 */
final class Rar5LibraryFallback {
    private static final String CLASS_UNRAR5J = "be.stef.rar5.Unrar5j";
    private static final String CLASS_RAR5_READER = "be.stef.rar5.Rar5Reader";

    private Rar5LibraryFallback() {}

    static boolean isAvailable() {
        try {
            Class.forName(CLASS_UNRAR5J);
            Class.forName(CLASS_RAR5_READER);
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }

    @NonNull
    static List<ArchiveSupport.EntryInfo> listEntries(@NonNull File archive,
                                                      @Nullable char[] password) throws IOException {
        ensureAvailable();
        try {
            Object reader = newReader(password);
            Method read = reader.getClass().getMethod("read", File.class);
            Object ok = read.invoke(reader, archive);
            if (!(ok instanceof Boolean) || !((Boolean) ok)) {
                if (password == null && isEncryptedArchive(archive)) {
                    throw new ArchiveSupport.PasswordRequiredException();
                }
                throw new ArchiveSupport.UnsupportedArchiveFeatureException(
                        "RAR5 library could not read archive headers");
            }
            Method getFileBlocks = reader.getClass().getMethod("getFileBlocks");
            Object blocks = getFileBlocks.invoke(reader);
            if (!(blocks instanceof Iterable)) {
                throw new ArchiveSupport.UnsupportedArchiveFeatureException(
                        "RAR5 library returned an unsupported entry list");
            }
            List<ArchiveSupport.EntryInfo> result = new ArrayList<>();
            for (Object block : (Iterable<?>) blocks) {
                String name = stringValue(call(block, "getFileName"));
                if (name == null || "[ENCRYPTED]".equals(name)) {
                    if (password == null) throw new ArchiveSupport.PasswordRequiredException();
                    continue;
                }
                Boolean directoryValue = booleanValue(call(block, "isDirectory"));
                boolean directory = directoryValue != null && directoryValue;
                String path = sanitizeEntryPath(name);
                if (path == null) continue;
                if (directory && !path.endsWith("/")) path += "/";
                long size = longValue(call(block, "getUnpackedSize"), -1L);
                long unixTime = longValue(call(block, "getUnixModificationTime"), 0L);
                long timeMillis = unixTime > 0L ? unixTime * 1000L : 0L;
                result.add(new ArchiveSupport.EntryInfo(path, directory, size, timeMillis));
            }
            return withSyntheticDirectories(result);
        } catch (InvocationTargetException e) {
            throw mapInvocationFailure(e, password);
        } catch (ReflectiveOperationException e) {
            throw new ArchiveSupport.UnsupportedArchiveFeatureException(
                    "RAR5 library API is not compatible with this build");
        }
    }

    static boolean requiresPasswordForExtraction(@NonNull File archive) {
        if (!isAvailable()) return false;
        try {
            if (isEncryptedArchive(archive)) return true;
            listEntries(archive, null);
            return false;
        } catch (ArchiveSupport.PasswordRequiredException e) {
            return true;
        } catch (IOException | SecurityException ignored) {
            return false;
        }
    }

    static boolean extractArchiveIntoDirectory(@NonNull File archive,
                                               @NonNull File targetDir,
                                               @Nullable char[] password) throws IOException {
        return extractArchiveIntoDirectory(archive, targetDir, password, null);
    }

    static boolean extractArchiveIntoDirectory(@NonNull File archive,
                                               @NonNull File targetDir,
                                               @Nullable char[] password,
                                               @Nullable FileOperationProgress progress) throws IOException {
        ensureAvailable();
        if (progress != null) {
            progress.setDetail(archive.getName());
            if (!progress.checkpoint()) return false;
        }
        if (!targetDir.exists() && !targetDir.mkdirs()) return false;
        Object result = invokeExtract(archive, targetDir, password, null);
        ExtractionStats stats = ExtractionStats.from(result);
        if (stats.badPassword) throw new IOException("Wrong RAR5 password");
        if (stats.noPassword && password == null) throw new ArchiveSupport.PasswordRequiredException();
        if (stats.errorCount > 0) {
            throw new IOException("RAR5 library extraction failed: " + stats.errorCount + " error(s)");
        }
        if (progress != null) {
            progress.addDoneBytes(Math.max(0L, sumExtractedFileBytes(targetDir)));
            if (!progress.checkpoint()) return false;
        }
        return stats.successCount > 0 || stats.totalFiles > 0;
    }

    static boolean extractSingleEntry(@NonNull File archive,
                                      @NonNull String entryPath,
                                      @NonNull File outFile,
                                      @Nullable char[] password) throws IOException {
        return extractSingleEntry(archive, entryPath, outFile, password, null);
    }

    static boolean extractSingleEntry(@NonNull File archive,
                                      @NonNull String entryPath,
                                      @NonNull File outFile,
                                      @Nullable char[] password,
                                      @Nullable FileOperationProgress progress) throws IOException {
        ensureAvailable();
        String normalized = sanitizeEntryPath(entryPath);
        if (normalized == null || normalized.endsWith("/")) return false;
        if (progress != null) {
            progress.setDetail(normalized);
            if (!progress.checkpoint()) return false;
        }
        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) return false;

        File base = parent != null ? parent : outFile.getAbsoluteFile().getParentFile();
        if (base == null) base = new File(System.getProperty("java.io.tmpdir"));
        File tempDir = new File(base, ".rar5_extract_" + System.nanoTime());
        if (!tempDir.mkdirs()) throw new IOException("Cannot create RAR5 temporary extraction directory");
        try {
            Object result = invokeExtract(archive, tempDir, password, normalized);
            ExtractionStats stats = ExtractionStats.from(result);
            if (stats.badPassword) throw new IOException("Wrong RAR5 password");
            if (stats.noPassword && password == null) throw new ArchiveSupport.PasswordRequiredException();
            if (stats.errorCount > 0) {
                throw new IOException("RAR5 library extraction failed: " + stats.errorCount + " error(s)");
            }
            File extracted = resolveOutput(tempDir, normalized);
            if (extracted == null || !extracted.isFile()) return false;
            moveFile(extracted, outFile);
            if (progress != null) {
                progress.addDoneBytes(Math.max(0L, outFile.length()));
                if (!progress.checkpoint()) return false;
            }
            return outFile.isFile();
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private static void ensureAvailable() throws ArchiveSupport.UnsupportedArchiveFeatureException {
        if (!isAvailable()) {
            throw new ArchiveSupport.UnsupportedArchiveFeatureException(
                    "RAR5 compressed extraction requires optional unrar5j-v1.0.3.jar in app/libs");
        }
    }

    @NonNull
    private static Object newReader(@Nullable char[] password)
            throws ReflectiveOperationException {
        Class<?> readerClass = Class.forName(CLASS_RAR5_READER);
        String pass = passwordString(password);
        if (pass != null) {
            try {
                Constructor<?> constructor = readerClass.getConstructor(String.class);
                return constructor.newInstance(pass);
            } catch (NoSuchMethodException ignored) {
                // Fall through to the no-arg constructor for older builds.
            }
        }
        return readerClass.getConstructor().newInstance();
    }

    private static boolean isEncryptedArchive(@NonNull File archive) throws IOException {
        try {
            Class<?> api = Class.forName(CLASS_UNRAR5J);
            Method isEncrypted = api.getMethod("isEncrypted", String.class);
            Object result = isEncrypted.invoke(null, archive.getAbsolutePath());
            return result instanceof Boolean && (Boolean) result;
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) throw (IOException) cause;
            return false;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    @Nullable
    private static Object invokeExtract(@NonNull File archive,
                                        @NonNull File targetDir,
                                        @Nullable char[] password,
                                        @Nullable String filter) throws IOException {
        try {
            Class<?> api = Class.forName(CLASS_UNRAR5J);
            suppressLibraryProgress(api);
            Method method;
            if (filter == null) {
                method = api.getMethod("extract", String.class, String.class, String.class);
                return method.invoke(null,
                        archive.getAbsolutePath(),
                        targetDir.getAbsolutePath(),
                        passwordString(password));
            }
            method = api.getMethod("extract", String.class, String.class, String.class, String.class);
            return method.invoke(null,
                    archive.getAbsolutePath(),
                    targetDir.getAbsolutePath(),
                    passwordString(password),
                    filter);
        } catch (InvocationTargetException e) {
            throw mapInvocationFailure(e, password);
        } catch (ReflectiveOperationException e) {
            throw new ArchiveSupport.UnsupportedArchiveFeatureException(
                    "RAR5 library API is not compatible with this build");
        }
    }

    private static void suppressLibraryProgress(@NonNull Class<?> api) {
        try {
            Field showProgress = api.getField("showProgress");
            showProgress.setBoolean(null, false);
        } catch (ReflectiveOperationException | SecurityException ignored) {
            // Progress output is cosmetic; extraction can continue.
        }
    }

    @NonNull
    private static IOException mapInvocationFailure(@NonNull InvocationTargetException e,
                                                    @Nullable char[] password) {
        Throwable cause = e.getCause();
        if (cause instanceof IOException) return (IOException) cause;
        String message = cause != null ? cause.getMessage() : e.getMessage();
        if (password == null && looksPasswordRelated(cause, message)) {
            return new ArchiveSupport.PasswordRequiredException();
        }
        return new IOException("RAR5 library operation failed" +
                (message == null ? "" : ": " + message), cause == null ? e : cause);
    }

    @Nullable
    private static Object call(@NonNull Object target, @NonNull String methodName)
            throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }

    @Nullable
    private static String stringValue(@Nullable Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @Nullable
    private static Boolean booleanValue(@Nullable Object value) {
        return value instanceof Boolean ? (Boolean) value : null;
    }

    private static long longValue(@Nullable Object value, long fallback) {
        if (value instanceof Number) return ((Number) value).longValue();
        return fallback;
    }

    @Nullable
    private static String passwordString(@Nullable char[] password) {
        return password == null || password.length == 0 ? null : new String(password);
    }

    @Nullable
    static String sanitizeEntryPath(@Nullable String rawPath) {
        if (rawPath == null) return null;
        String path = rawPath.trim().replace('\\', '/');
        while (path.startsWith("./")) path = path.substring(2);
        while (path.contains("//")) path = path.replace("//", "/");
        if (path.length() == 0) return null;
        if (path.startsWith("/") || path.matches("^[A-Za-z]:.*")) return null;

        String[] parts = path.split("/");
        StringBuilder clean = new StringBuilder();
        for (String part : parts) {
            if (part.length() == 0 || ".".equals(part)) continue;
            if ("..".equals(part)) return null;
            if (clean.length() > 0) clean.append('/');
            clean.append(part);
        }
        if (clean.length() == 0) return null;
        if (path.endsWith("/") && clean.charAt(clean.length() - 1) != '/') clean.append('/');
        return clean.toString();
    }

    @Nullable
    private static File resolveOutput(@NonNull File targetDir, @NonNull String entryPath) throws IOException {
        String clean = sanitizeEntryPath(entryPath);
        if (clean == null) return null;
        File out = new File(targetDir, clean).getCanonicalFile();
        File root = targetDir.getCanonicalFile();
        if (!isSameOrDescendant(root, out)) return null;
        return out;
    }

    private static boolean isSameOrDescendant(@NonNull File root, @NonNull File file) {
        File current = file;
        while (current != null) {
            if (current.equals(root)) return true;
            current = current.getParentFile();
        }
        return false;
    }

    @NonNull
    private static List<ArchiveSupport.EntryInfo> withSyntheticDirectories(
            @NonNull List<ArchiveSupport.EntryInfo> entries) {
        Map<String, ArchiveSupport.EntryInfo> map = new LinkedHashMap<>();
        for (ArchiveSupport.EntryInfo entry : entries) {
            String path = entry.path;
            int slash = path.indexOf('/');
            while (slash >= 0) {
                String dir = path.substring(0, slash + 1);
                if (!map.containsKey(dir)) {
                    map.put(dir, new ArchiveSupport.EntryInfo(dir, true, -1L, 0L));
                }
                slash = path.indexOf('/', slash + 1);
            }
            map.put(path, entry);
        }
        return new ArrayList<>(map.values());
    }

    private static boolean looksPasswordRelated(@Nullable Throwable cause, @Nullable String message) {
        StringBuilder builder = new StringBuilder();
        if (cause != null) builder.append(cause.getClass().getName()).append(' ');
        if (message != null) builder.append(message);
        String text = builder.toString().toLowerCase(Locale.US);
        return text.contains("password")
                || text.contains("decrypt")
                || text.contains("encrypted")
                || text.contains("crypt");
    }

    private static long sumExtractedFileBytes(@Nullable File file) {
        if (file == null || !file.exists()) return 0L;
        if (file.isFile()) return Math.max(0L, file.length());
        File[] children;
        try {
            children = file.listFiles();
        } catch (SecurityException ignored) {
            return 0L;
        }
        if (children == null) return 0L;
        long total = 0L;
        for (File child : children) {
            long value = sumExtractedFileBytes(child);
            if (Long.MAX_VALUE - total < value) return Long.MAX_VALUE;
            total += value;
        }
        return total;
    }

    private static void moveFile(@NonNull File source, @NonNull File target) throws IOException {
        if (target.exists() && !target.delete()) {
            throw new IOException("Cannot replace output file");
        }
        if (source.renameTo(target)) return;
        byte[] buffer = new byte[64 * 1024];
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(target)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
        if (!source.delete()) {
            try { source.deleteOnExit(); } catch (SecurityException ignored) {}
        }
    }

    private static void deleteRecursively(@Nullable File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursively(child);
        }
        try { file.delete(); } catch (SecurityException ignored) {}
    }

    private static final class ExtractionStats {
        final int successCount;
        final int errorCount;
        final int totalFiles;
        final boolean badPassword;
        final boolean noPassword;

        private ExtractionStats(int successCount,
                                int errorCount,
                                int totalFiles,
                                boolean badPassword,
                                boolean noPassword) {
            this.successCount = successCount;
            this.errorCount = errorCount;
            this.totalFiles = totalFiles;
            this.badPassword = badPassword;
            this.noPassword = noPassword;
        }

        @NonNull
        static ExtractionStats from(@Nullable Object result) {
            int success = readIntField(result, "successCount", 0);
            int errors = readIntField(result, "errorCount", 0);
            int total = readIntField(result, "totalFiles", 0);
            int passwordStatus = readIntField(result, "passwordStatut", 0);
            boolean badPassword = passwordStatus == 2;
            boolean noPassword = total == 0 && success == 0 && errors == 0;
            return new ExtractionStats(success, errors, total, badPassword, noPassword);
        }

        private static int readIntField(@Nullable Object target,
                                        @NonNull String fieldName,
                                        int fallback) {
            if (target == null) return fallback;
            try {
                Field field = target.getClass().getField(fieldName);
                Object value = field.get(target);
                if (value instanceof Number) return ((Number) value).intValue();
            } catch (ReflectiveOperationException | SecurityException ignored) {
                // Keep fallback for older/incompatible library builds.
            }
            return fallback;
        }
    }
}
