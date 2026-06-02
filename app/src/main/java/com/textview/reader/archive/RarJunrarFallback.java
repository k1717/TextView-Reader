package com.textview.reader.archive;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.junrar.Archive;
import com.github.junrar.exception.RarException;
import com.github.junrar.rarfile.FileHeader;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.textview.reader.util.FileOperationProgress;

final class RarJunrarFallback {
    private RarJunrarFallback() {}

    @NonNull
    static List<ArchiveSupport.EntryInfo> listEntries(@NonNull File archiveFile,
                                                      @Nullable char[] password) throws IOException {
        try (Archive archive = openArchive(archiveFile, password)) {
            ensurePasswordAvailable(archive, password);
            List<ArchiveSupport.EntryInfo> result = new ArrayList<>();
            List<FileHeader> headers = archive.getFileHeaders();
            if (headers != null) {
                for (FileHeader header : headers) {
                    if (header == null) continue;
                    String path = sanitizeEntryPath(header.getFileName());
                    if (path == null) continue;
                    boolean directory = header.isDirectory() || path.endsWith("/");
                    if (directory && !path.endsWith("/")) path += "/";
                    long size = directory ? -1L : Math.max(-1L, header.getFullUnpackSize());
                    result.add(new ArchiveSupport.EntryInfo(path, directory, size, 0L));
                }
            }
            return withSyntheticDirectories(result);
        } catch (RarException e) {
            throw classifyRarException(e, password);
        }
    }

    static boolean requiresPasswordForExtraction(@NonNull File archiveFile) {
        try (Archive archive = openArchive(archiveFile, null)) {
            if (archive.isEncrypted() || archive.isPasswordProtected()) return true;
            List<FileHeader> headers = archive.getFileHeaders();
            if (headers != null) {
                for (FileHeader header : headers) {
                    if (header != null && header.isEncrypted()) return true;
                }
            }
            return false;
        } catch (RarException e) {
            return looksPasswordRelated(e);
        } catch (IOException | SecurityException ignored) {
            return false;
        }
    }

    static boolean extractArchiveIntoDirectory(@NonNull File archiveFile,
                                               @NonNull File targetDir,
                                               @Nullable char[] password) throws IOException {
        return extractArchiveIntoDirectory(archiveFile, targetDir, password, null);
    }

    static boolean extractArchiveIntoDirectory(@NonNull File archiveFile,
                                               @NonNull File targetDir,
                                               @Nullable char[] password,
                                               @Nullable FileOperationProgress progress) throws IOException {
        boolean sawEntry = false;
        try (Archive archive = openArchive(archiveFile, password)) {
            ensurePasswordAvailable(archive, password);
            if (progress != null) progress.setTotalBytes(sumUnpackBytes(archive.getFileHeaders()));
            FileHeader header;
            while ((header = archive.nextFileHeader()) != null) {
                if (progress != null && !progress.checkpoint()) return false;
                String path = sanitizeEntryPath(header.getFileName());
                if (path == null) continue;
                boolean directory = header.isDirectory() || path.endsWith("/");
                if (directory && !path.endsWith("/")) path += "/";
                if (progress != null) progress.setDetail(path);
                File out = resolveOutput(targetDir, path);
                if (out == null) return false;
                sawEntry = true;
                if (directory) {
                    if (!out.exists() && !out.mkdirs()) return false;
                    continue;
                }
                File parent = out.getParentFile();
                if (parent == null) throw new IOException("Output file has no parent");
                if (!parent.exists() && !parent.mkdirs()) throw new IOException("Cannot create output directory");
                try (OutputStream output = new ProgressOutputStream(
                        new BufferedOutputStream(new FileOutputStream(out)), progress, true)) {
                    archive.extractFile(header, output);
                    output.flush();
                }
            }
            return sawEntry;
        } catch (RarException e) {
            throw classifyRarException(e, password);
        }
    }

    static boolean extractSingleEntry(@NonNull File archiveFile,
                                      @NonNull String entryPath,
                                      @NonNull File outFile,
                                      @Nullable char[] password) throws IOException {
        return extractSingleEntry(archiveFile, entryPath, outFile, password, null);
    }

    static boolean extractSingleEntry(@NonNull File archiveFile,
                                      @NonNull String entryPath,
                                      @NonNull File outFile,
                                      @Nullable char[] password,
                                      @Nullable FileOperationProgress progress) throws IOException {
        String normalized = sanitizeEntryPath(entryPath);
        if (normalized == null || normalized.endsWith("/")) return false;
        File parent = outFile.getParentFile();
        if (parent == null) throw new IOException("Output file has no parent");
        if (!parent.exists() && !parent.mkdirs()) throw new IOException("Cannot create output directory");

        boolean ok = false;
        try (Archive archive = openArchive(archiveFile, password)) {
            ensurePasswordAvailable(archive, password);
            if (progress != null) progress.setTotalBytes(sumUnpackBytes(archive.getFileHeaders()));
            FileHeader header;
            while ((header = archive.nextFileHeader()) != null) {
                if (progress != null && !progress.checkpoint()) return false;
                if (header.isDirectory()) continue;
                String path = sanitizeEntryPath(header.getFileName());
                if (path == null) continue;
                if (normalized.equals(path)) {
                    if (progress != null) progress.setDetail(path);
                    try (OutputStream out = new ProgressOutputStream(
                            new BufferedOutputStream(new FileOutputStream(outFile)), progress, true)) {
                        archive.extractFile(header, out);
                        out.flush();
                    }
                    ok = true;
                    return true;
                }
                archive.extractFile(header, new ProgressOutputStream(NullOutputStream.INSTANCE, progress, false));
            }
            return false;
        } catch (RarException e) {
            throw classifyRarException(e, password);
        } finally {
            if (!ok) {
                try { outFile.delete(); } catch (SecurityException ignored) {}
            }
        }
    }

    @NonNull
    private static Archive openArchive(@NonNull File archiveFile, @Nullable char[] password) throws RarException, IOException {
        if (password != null && password.length > 0) {
            return new Archive(archiveFile, new String(password));
        }
        return new Archive(archiveFile);
    }

    private static void ensurePasswordAvailable(@NonNull Archive archive,
                                                @Nullable char[] password) throws RarException, IOException {
        if (password != null && password.length > 0) return;
        if (archive.isEncrypted() || archive.isPasswordProtected()) {
            throw new ArchiveSupport.PasswordRequiredException();
        }
        List<FileHeader> headers = archive.getFileHeaders();
        if (headers != null) {
            for (FileHeader header : headers) {
                if (header != null && header.isEncrypted()) throw new ArchiveSupport.PasswordRequiredException();
            }
        }
    }

    @NonNull
    private static IOException classifyRarException(@NonNull RarException e, @Nullable char[] password) {
        String message = e.getMessage();
        String lower = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if ((password == null || password.length == 0) && looksPasswordRelated(lower)) {
            return new ArchiveSupport.PasswordRequiredException();
        }
        if (lower.contains("solid") || lower.contains("unsupported")
                || lower.contains("not implemented") || lower.contains("unknown")) {
            return new ArchiveSupport.UnsupportedArchiveFeatureException(message == null
                    ? "RAR feature is not supported by fallback decoder"
                    : message);
        }
        return new IOException("RAR fallback extraction failed", e);
    }

    private static boolean looksPasswordRelated(@NonNull RarException e) {
        String message = e.getMessage();
        return looksPasswordRelated(message == null ? "" : message.toLowerCase(Locale.ROOT));
    }

    private static boolean looksPasswordRelated(@NonNull String lowerMessage) {
        return lowerMessage.contains("password")
                || lowerMessage.contains("encrypted")
                || lowerMessage.contains("decrypt")
                || lowerMessage.contains("wrong password");
    }

    private static long sumUnpackBytes(@Nullable List<FileHeader> headers) {
        if (headers == null) return -1L;
        long total = 0L;
        boolean unknown = false;
        for (FileHeader header : headers) {
            if (header == null || header.isDirectory()) continue;
            long size = header.getFullUnpackSize();
            if (size < 0L) {
                unknown = true;
                continue;
            }
            if (Long.MAX_VALUE - total < size) return Long.MAX_VALUE;
            total += size;
        }
        return total > 0L ? total : (unknown ? -1L : 0L);
    }

    @Nullable
    private static File resolveOutput(@NonNull File targetDir, @NonNull String entryPath) throws IOException {
        String path = sanitizeEntryPath(entryPath);
        if (path == null) return null;
        File out = new File(targetDir, path);
        return isSameOrDescendant(targetDir, out) ? out : null;
    }

    private static boolean isSameOrDescendant(@NonNull File ancestor, @NonNull File candidate) throws IOException {
        File ancestorCanonical = ancestor.getCanonicalFile();
        File current = candidate.getCanonicalFile();
        while (current != null) {
            if (ancestorCanonical.equals(current)) return true;
            current = current.getParentFile();
        }
        return false;
    }

    @NonNull
    private static List<ArchiveSupport.EntryInfo> withSyntheticDirectories(@NonNull List<ArchiveSupport.EntryInfo> entries) {
        Map<String, ArchiveSupport.EntryInfo> map = new LinkedHashMap<>();
        for (ArchiveSupport.EntryInfo entry : entries) {
            String path = entry.path;
            int slash = path.indexOf('/');
            while (slash >= 0) {
                String dir = path.substring(0, slash + 1);
                if (!map.containsKey(dir)) map.put(dir, new ArchiveSupport.EntryInfo(dir, true, -1L, 0L));
                slash = path.indexOf('/', slash + 1);
            }
            map.put(path, entry);
        }
        return new ArrayList<>(map.values());
    }

    @Nullable
    private static String sanitizeEntryPath(String rawEntryName) {
        if (rawEntryName == null) return null;
        String entryName = rawEntryName.trim().replace('\\', '/');
        while (entryName.startsWith("./")) entryName = entryName.substring(2);
        while (entryName.contains("//")) entryName = entryName.replace("//", "/");
        if (entryName.length() == 0) return null;
        if (entryName.startsWith("/")
                || entryName.equals("..")
                || entryName.startsWith("../")
                || entryName.contains("/../")
                || entryName.endsWith("/..")
                || entryName.matches("^[A-Za-z]:.*")) {
            return null;
        }
        return entryName;
    }

    private static final class ProgressOutputStream extends OutputStream {
        private final OutputStream delegate;
        @Nullable private final FileOperationProgress progress;
        private final boolean countBytes;

        ProgressOutputStream(@NonNull OutputStream delegate,
                             @Nullable FileOperationProgress progress,
                             boolean countBytes) {
            this.delegate = delegate;
            this.progress = progress;
            this.countBytes = countBytes;
        }

        @Override
        public void write(int b) throws IOException {
            if (progress != null && !progress.checkpoint()) throw new IOException("RAR extraction cancelled");
            delegate.write(b);
            if (countBytes && progress != null) progress.addDoneBytes(1L);
        }

        @Override
        public void write(@NonNull byte[] b, int off, int len) throws IOException {
            if (len <= 0) return;
            if (progress != null && !progress.checkpoint()) throw new IOException("RAR extraction cancelled");
            delegate.write(b, off, len);
            if (countBytes && progress != null) progress.addDoneBytes(len);
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }

    private static final class NullOutputStream extends OutputStream {
        static final NullOutputStream INSTANCE = new NullOutputStream();

        private NullOutputStream() {}

        @Override
        public void write(int b) {}

        @Override
        public void write(@NonNull byte[] b, int off, int len) {}
    }
}
