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

final class RarJunrarFallback {
    private RarJunrarFallback() {}

    static boolean extractSingleEntry(@NonNull File archiveFile,
                                      @NonNull String entryPath,
                                      @NonNull File outFile,
                                      @Nullable char[] password) throws IOException {
        String normalized = sanitizeEntryPath(entryPath);
        if (normalized == null || normalized.endsWith("/")) return false;
        File parent = outFile.getParentFile();
        if (parent == null) throw new IOException("Output file has no parent");
        if (!parent.exists() && !parent.mkdirs()) throw new IOException("Cannot create output directory");

        boolean ok = false;
        try (Archive archive = openArchive(archiveFile, password)) {
            for (FileHeader header : archive.getFileHeaders()) {
                if (header == null || header.isDirectory()) continue;
                String path = sanitizeEntryPath(header.getFileName());
                if (!normalized.equals(path)) continue;
                try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(outFile))) {
                    archive.extractFile(header, out);
                    out.flush();
                }
                ok = true;
                return true;
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

    @NonNull
    private static IOException classifyRarException(@NonNull RarException e, @Nullable char[] password) {
        String message = e.getMessage();
        String lower = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT);
        if ((password == null || password.length == 0)
                && (lower.contains("password") || lower.contains("encrypted"))) {
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
}
