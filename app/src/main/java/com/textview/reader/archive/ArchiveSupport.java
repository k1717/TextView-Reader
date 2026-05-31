package com.textview.reader.archive;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.FileHeader;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.lzma.LZMACompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.compressors.z.ZCompressorInputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ArchiveSupport {
    private ArchiveSupport() {}

    public enum Type {
        ZIP,
        TAR,
        TAR_GZ,
        TAR_BZ2,
        TAR_XZ,
        TAR_LZMA,
        TAR_Z,
        SEVEN_Z,
        SINGLE_GZ,
        SINGLE_BZ2,
        SINGLE_XZ,
        SINGLE_LZMA,
        SINGLE_Z
    }

    public static final class PasswordRequiredException extends IOException {
        public PasswordRequiredException() { super("Archive password required"); }
    }

    public static final class EntryInfo {
        public final String path;
        public final boolean directory;
        public final long size;
        public final long timeMillis;

        public EntryInfo(@NonNull String path, boolean directory, long size, long timeMillis) {
            this.path = normalizeDisplayPath(path, directory);
            this.directory = directory;
            this.size = size;
            this.timeMillis = timeMillis;
        }

        public String name() {
            String p = path;
            if (p.endsWith("/")) p = p.substring(0, p.length() - 1);
            int slash = p.lastIndexOf('/');
            return slash >= 0 ? p.substring(slash + 1) : p;
        }
    }

    @Nullable
    public static Type getSupportedArchiveType(@NonNull File file) {
        if (!file.isFile()) return null;
        return getSupportedArchiveType(file.getName());
    }

    @Nullable
    public static Type getSupportedArchiveType(@NonNull String fileName) {
        String name = fileName.toLowerCase(Locale.ROOT);
        if (isFirstNumericSplitName(name)) {
            Type splitBaseType = getSupportedArchiveType(name.substring(0, name.length() - 4));
            if (splitBaseType != null) return splitBaseType;
        }
        if (name.endsWith(".zip") || name.endsWith(".cbz")) return Type.ZIP;
        if (name.endsWith(".7z")) return Type.SEVEN_Z;
        if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) return Type.TAR_GZ;
        if (name.endsWith(".tar.bz2") || name.endsWith(".tbz2") || name.endsWith(".tbz")) return Type.TAR_BZ2;
        if (name.endsWith(".tar.xz") || name.endsWith(".txz")) return Type.TAR_XZ;
        if (name.endsWith(".tar.lzma") || name.endsWith(".tlz")) return Type.TAR_LZMA;
        if (name.endsWith(".tar.z") || name.endsWith(".taz")) return Type.TAR_Z;
        if (name.endsWith(".tar")) return Type.TAR;
        if (name.endsWith(".gz")) return Type.SINGLE_GZ;
        if (name.endsWith(".bz2")) return Type.SINGLE_BZ2;
        if (name.endsWith(".xz")) return Type.SINGLE_XZ;
        if (name.endsWith(".lzma")) return Type.SINGLE_LZMA;
        if (name.endsWith(".z")) return Type.SINGLE_Z;
        return null;
    }

    public static boolean isSupportedArchive(@NonNull File file) {
        return getSupportedArchiveType(file) != null;
    }

    public static boolean isSupportedArchiveFileName(@NonNull String fileName) {
        return getSupportedArchiveType(fileName) != null;
    }

    private static boolean isFirstNumericSplitName(@NonNull String lowerName) {
        return lowerName.endsWith(".001") && lowerName.length() > 4;
    }

    private static boolean isFirstNumericSplitArchive(@NonNull File file) {
        return isFirstNumericSplitName(file.getName().toLowerCase(Locale.ROOT));
    }

    public static String getArchiveOutputBaseName(@NonNull File archive, @NonNull String fallback) {
        String name = archive.getName();
        String lower = name.toLowerCase(Locale.ROOT);
        if (isFirstNumericSplitName(lower)) {
            name = name.substring(0, name.length() - 4);
            lower = lower.substring(0, lower.length() - 4);
        }
        String[] archiveExtensions = new String[] {
                ".tar.gz",
                ".tar.bz2",
                ".tar.xz",
                ".tar.lzma",
                ".tar.z",
                ".tgz",
                ".tbz2",
                ".tbz",
                ".txz",
                ".tlz",
                ".taz",
                ".lzma",
                ".bz2",
                ".gz",
                ".xz",
                ".z",
                ".zip",
                ".cbz",
                ".7z",
                ".tar"
        };
        for (String ext : archiveExtensions) {
            if (lower.endsWith(ext) && name.length() > ext.length()) {
                return name.substring(0, name.length() - ext.length());
            }
        }
        return name.length() > 0 ? name : fallback;
    }

    public static boolean canUsePassword(@NonNull File archive) {
        Type type = getSupportedArchiveType(archive);
        return type == Type.ZIP || type == Type.SEVEN_Z;
    }

    public static boolean isZipEncrypted(@NonNull File archive) {
        if (getSupportedArchiveType(archive) != Type.ZIP) return false;
        try (PreparedArchive prepared = prepareArchiveForRead(archive)) {
            return new ZipFile(prepared.file).isEncrypted();
        } catch (IOException | SecurityException ignored) {
            return false;
        }
    }

    @NonNull
    public static List<EntryInfo> listEntries(@NonNull File archive, @Nullable char[] password) throws IOException {
        try (PreparedArchive prepared = prepareArchiveForRead(archive)) {
            switch (prepared.type) {
                case ZIP:
                    return listZipEntries(prepared.file, password);
                case SEVEN_Z:
                    return listSevenZEntries(prepared.file, password);
                case TAR:
                case TAR_GZ:
                case TAR_BZ2:
                case TAR_XZ:
                case TAR_LZMA:
                case TAR_Z:
                    return listTarEntries(prepared.file, prepared.type);
                case SINGLE_GZ:
                case SINGLE_BZ2:
                case SINGLE_XZ:
                case SINGLE_LZMA:
                case SINGLE_Z:
                    return listSingleCompressedEntry(archive);
                default:
                    throw new IOException("Unsupported archive");
            }
        }
    }

    @NonNull
    private static List<EntryInfo> listZipEntries(@NonNull File archive, @Nullable char[] password) throws IOException {
        try {
            ZipFile zip = new ZipFile(archive);
            if (zip.isEncrypted()) {
                if (password == null || password.length == 0) throw new PasswordRequiredException();
                zip.setPassword(password);
            }
            List<EntryInfo> result = new ArrayList<>();
            @SuppressWarnings("unchecked")
            List<FileHeader> headers = zip.getFileHeaders();
            for (FileHeader header : headers) {
                if (header == null) continue;
                String path = sanitizeEntryPathForList(header.getFileName());
                if (path == null) continue;
                result.add(new EntryInfo(path, header.isDirectory(), header.getUncompressedSize(), 0L));
            }
            return withSyntheticDirectories(result);
        } catch (PasswordRequiredException e) {
            throw e;
        } catch (ZipException | SecurityException e) {
            throw new IOException(e);
        }
    }

    @NonNull
    private static List<EntryInfo> listSevenZEntries(@NonNull File archive, @Nullable char[] password) throws IOException {
        List<EntryInfo> result = new ArrayList<>();
        try (SevenZFile sevenZ = openSevenZFile(archive, password)) {
            SevenZArchiveEntry entry;
            while ((entry = sevenZ.getNextEntry()) != null) {
                String path = sanitizeEntryPathForList(entry.getName());
                if (path == null) continue;
                result.add(new EntryInfo(path, entry.isDirectory(), entry.getSize(), 0L));
            }
            return withSyntheticDirectories(result);
        } catch (IOException e) {
            if (password == null || password.length == 0) throw new PasswordRequiredException();
            throw e;
        }
    }

    @NonNull
    private static List<EntryInfo> listTarEntries(@NonNull File archive, @NonNull Type type) throws IOException {
        List<EntryInfo> result = new ArrayList<>();
        try (InputStream fileIn = new BufferedInputStream(new FileInputStream(archive));
             InputStream payloadIn = wrapTarPayloadInputStream(fileIn, type);
             TarArchiveInputStream tar = new TarArchiveInputStream(payloadIn)) {
            ArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                if (!tar.canReadEntryData(entry)) throw new IOException("Cannot read TAR entry");
                if (entry instanceof TarArchiveEntry) {
                    TarArchiveEntry tarEntry = (TarArchiveEntry) entry;
                    if (tarEntry.isSymbolicLink() || tarEntry.isLink()) continue;
                }
                String path = sanitizeEntryPathForList(entry.getName());
                if (path == null) continue;
                result.add(new EntryInfo(path, entry.isDirectory(), entry.getSize(), 0L));
            }
            return withSyntheticDirectories(result);
        }
    }

    public static boolean extractArchive(@NonNull File archive,
                                         @NonNull File destinationDir,
                                         boolean overwrite,
                                         @Nullable char[] password) {
        if (!isSupportedArchive(archive) || !archive.exists() || !archive.isFile()) return false;
        File parent = destinationDir.getParentFile();
        if (parent == null || !parent.exists() || !parent.isDirectory() || !parent.canWrite()) return false;

        File workDir = destinationDir;
        File tempDir = null;
        if (destinationDir.exists()) {
            if (!overwrite) return false;
            tempDir = buildTempExtractDirectory(parent, destinationDir.getName());
            if (tempDir == null) return false;
            workDir = tempDir;
        }

        if (workDir.exists()) return false;
        try {
            if (!workDir.mkdirs()) return false;
        } catch (SecurityException ignored) {
            return false;
        }

        boolean ok = extractArchiveIntoDirectory(archive, workDir, password);
        if (!ok) {
            deleteFileSystemItem(workDir);
            return false;
        }

        if (tempDir != null) {
            if (!deleteFileSystemItem(destinationDir)) {
                deleteFileSystemItem(tempDir);
                return false;
            }
            boolean replaced;
            try {
                replaced = tempDir.renameTo(destinationDir);
            } catch (SecurityException ignored) {
                replaced = false;
            }
            if (!replaced) {
                replaced = copyDirectoryRecursively(tempDir, destinationDir);
                deleteFileSystemItem(tempDir);
            }
            if (!replaced) {
                deleteFileSystemItem(destinationDir);
                return false;
            }
        }
        return true;
    }

    public static boolean extractSingleEntry(@NonNull File archive,
                                             @NonNull String entryPath,
                                             @NonNull File outFile,
                                             @Nullable char[] password) {
        String normalized = sanitizeEntryPathForList(entryPath);
        if (normalized == null || normalized.endsWith("/")) return false;
        if (outFile.exists() && !deleteFileSystemItem(outFile)) return false;
        File parent = outFile.getParentFile();
        if (parent == null) return false;
        if (!parent.exists() && !parent.mkdirs()) return false;

        try (PreparedArchive prepared = prepareArchiveForRead(archive)) {
            switch (prepared.type) {
                case ZIP:
                    return extractSingleZipEntry(prepared.file, normalized, outFile, password);
                case SEVEN_Z:
                    return extractSingleSevenZEntry(prepared.file, normalized, outFile, password);
                case TAR:
                case TAR_GZ:
                case TAR_BZ2:
                case TAR_XZ:
                case TAR_LZMA:
                case TAR_Z:
                    return extractSingleTarEntry(prepared.file, normalized, outFile, prepared.type);
                case SINGLE_GZ:
                case SINGLE_BZ2:
                case SINGLE_XZ:
                case SINGLE_LZMA:
                case SINGLE_Z:
                    return extractSingleCompressedEntry(prepared.file, archive, normalized, outFile, prepared.type);
                default:
                    return false;
            }
        } catch (IOException | SecurityException ignored) {
            try { outFile.delete(); } catch (SecurityException ignored2) {}
            return false;
        }
    }

    private static boolean extractArchiveIntoDirectory(@NonNull File archive,
                                                       @NonNull File targetDir,
                                                       @Nullable char[] password) {
        try (PreparedArchive prepared = prepareArchiveForRead(archive)) {
            switch (prepared.type) {
                case ZIP:
                    return extractZipIntoDirectory(prepared.file, targetDir, password);
                case SEVEN_Z:
                    return extractSevenZIntoDirectory(prepared.file, targetDir, password);
                case TAR:
                case TAR_GZ:
                case TAR_BZ2:
                case TAR_XZ:
                case TAR_LZMA:
                case TAR_Z:
                    return extractTarIntoDirectory(prepared.file, targetDir, prepared.type);
                case SINGLE_GZ:
                case SINGLE_BZ2:
                case SINGLE_XZ:
                case SINGLE_LZMA:
                case SINGLE_Z:
                    return extractSingleCompressedIntoDirectory(prepared.file, archive, targetDir, prepared.type);
                default:
                    return false;
            }
        } catch (IOException | SecurityException ignored) {
            return false;
        }
    }


    private static final class PreparedArchive implements AutoCloseable {
        final File file;
        final Type type;
        @Nullable final File tempFile;

        PreparedArchive(@NonNull File file, @NonNull Type type, @Nullable File tempFile) {
            this.file = file;
            this.type = type;
            this.tempFile = tempFile;
        }

        @Override
        public void close() {
            if (tempFile != null) {
                try { tempFile.delete(); } catch (SecurityException ignored) {}
            }
        }
    }

    @NonNull
    private static PreparedArchive prepareArchiveForRead(@NonNull File archive) throws IOException {
        Type type = getSupportedArchiveType(archive);
        if (type == null) throw new IOException("Unsupported archive");
        if (!isFirstNumericSplitArchive(archive)) return new PreparedArchive(archive, type, null);

        List<File> parts = collectNumericSplitParts(archive);
        if (parts.isEmpty()) throw new IOException("No split archive parts");
        File temp = File.createTempFile("textview_split_archive_", ".tmp");
        boolean ok = false;
        try {
            try (OutputStream out = new BufferedOutputStream(new FileOutputStream(temp))) {
                byte[] buffer = new byte[1024 * 64];
                for (File part : parts) {
                    try (InputStream in = new BufferedInputStream(new FileInputStream(part))) {
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                        }
                    }
                }
                out.flush();
            }
            ok = true;
            return new PreparedArchive(temp, type, temp);
        } finally {
            if (!ok) {
                try { temp.delete(); } catch (SecurityException ignored) {}
            }
        }
    }

    @NonNull
    private static List<File> collectNumericSplitParts(@NonNull File firstPart) throws IOException {
        String name = firstPart.getName();
        String lower = name.toLowerCase(Locale.ROOT);
        if (!isFirstNumericSplitName(lower)) return Collections.emptyList();
        File parent = firstPart.getParentFile();
        if (parent == null) throw new IOException("Split archive has no parent directory");
        String stem = name.substring(0, name.length() - 4);
        List<File> result = new ArrayList<>();
        for (int index = 1; index <= 999; index++) {
            String suffix = String.format(Locale.ROOT, ".%03d", index);
            File part = new File(parent, stem + suffix);
            if (!part.exists() || !part.isFile()) {
                if (index == 1) throw new IOException("First split archive part is missing");
                break;
            }
            result.add(part);
        }
        return result;
    }


    @NonNull
    private static List<EntryInfo> listSingleCompressedEntry(@NonNull File archive) {
        String outputName = getSingleCompressedOutputName(archive);
        List<EntryInfo> result = new ArrayList<>();
        result.add(new EntryInfo(outputName, false, -1L, 0L));
        return result;
    }

    private static boolean extractSingleCompressedIntoDirectory(@NonNull File payloadArchive,
                                                                @NonNull File nameSourceArchive,
                                                                @NonNull File targetDir,
                                                                @NonNull Type type) throws IOException {
        File out = new File(targetDir, getSingleCompressedOutputName(nameSourceArchive));
        if (!isSameOrDescendant(targetDir, out)) return false;
        try (InputStream fileIn = new BufferedInputStream(new FileInputStream(payloadArchive));
             InputStream payloadIn = wrapSingleCompressedInputStream(fileIn, type)) {
            return writeArchiveEntryStream(payloadIn, out);
        }
    }

    private static boolean extractSingleCompressedEntry(@NonNull File payloadArchive,
                                                        @NonNull File nameSourceArchive,
                                                        @NonNull String entryPath,
                                                        @NonNull File outFile,
                                                        @NonNull Type type) throws IOException {
        String outputName = getSingleCompressedOutputName(nameSourceArchive);
        if (!entryPath.equals(outputName)) return false;
        try (InputStream fileIn = new BufferedInputStream(new FileInputStream(payloadArchive));
             InputStream payloadIn = wrapSingleCompressedInputStream(fileIn, type)) {
            return writeArchiveEntryStream(payloadIn, outFile);
        }
    }

    private static boolean extractZipIntoDirectory(@NonNull File archive,
                                                   @NonNull File targetDir,
                                                   @Nullable char[] password) throws IOException {
        try {
            ZipFile zip = new ZipFile(archive);
            if (zip.isEncrypted()) {
                if (password == null || password.length == 0) throw new PasswordRequiredException();
                zip.setPassword(password);
            }
            boolean sawEntry = false;
            @SuppressWarnings("unchecked")
            List<FileHeader> headers = zip.getFileHeaders();
            for (FileHeader header : headers) {
                if (header == null) continue;
                File out = resolveArchiveEntryOutput(targetDir, header.getFileName());
                if (out == null) return false;
                sawEntry = true;
                if (header.isDirectory() || header.getFileName().replace('\\', '/').endsWith("/")) {
                    if (!out.exists() && !out.mkdirs()) return false;
                    continue;
                }
                File outParent = out.getParentFile();
                if (outParent == null) return false;
                if (!outParent.exists() && !outParent.mkdirs()) return false;
                try (InputStream in = zip.getInputStream(header)) {
                    if (!writeArchiveEntryStream(in, out)) return false;
                }
            }
            return sawEntry;
        } catch (ZipException e) {
            throw new IOException(e);
        }
    }

    private static boolean extractSingleZipEntry(@NonNull File archive,
                                                 @NonNull String entryPath,
                                                 @NonNull File outFile,
                                                 @Nullable char[] password) throws IOException {
        try {
            ZipFile zip = new ZipFile(archive);
            if (zip.isEncrypted()) {
                if (password == null || password.length == 0) throw new PasswordRequiredException();
                zip.setPassword(password);
            }
            @SuppressWarnings("unchecked")
            List<FileHeader> headers = zip.getFileHeaders();
            for (FileHeader header : headers) {
                if (header == null || header.isDirectory()) continue;
                String path = sanitizeEntryPathForList(header.getFileName());
                if (!entryPath.equals(path)) continue;
                try (InputStream in = zip.getInputStream(header)) {
                    return writeArchiveEntryStream(in, outFile);
                }
            }
            return false;
        } catch (ZipException e) {
            throw new IOException(e);
        }
    }

    private static boolean extractTarIntoDirectory(@NonNull File archive,
                                                   @NonNull File targetDir,
                                                   @NonNull Type type) throws IOException {
        boolean sawEntry = false;
        try (InputStream fileIn = new BufferedInputStream(new FileInputStream(archive));
             InputStream payloadIn = wrapTarPayloadInputStream(fileIn, type);
             TarArchiveInputStream tar = new TarArchiveInputStream(payloadIn)) {
            ArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                if (!tar.canReadEntryData(entry)) return false;
                if (entry instanceof TarArchiveEntry) {
                    TarArchiveEntry tarEntry = (TarArchiveEntry) entry;
                    if (tarEntry.isSymbolicLink() || tarEntry.isLink()) return false;
                }
                File out = resolveArchiveEntryOutput(targetDir, entry.getName());
                if (out == null) return false;
                sawEntry = true;
                if (entry.isDirectory() || entry.getName().replace('\\', '/').endsWith("/")) {
                    if (!out.exists() && !out.mkdirs()) return false;
                    continue;
                }
                if (!writeArchiveEntryStream(tar, out)) return false;
            }
            return sawEntry;
        }
    }

    private static boolean extractSingleTarEntry(@NonNull File archive,
                                                 @NonNull String entryPath,
                                                 @NonNull File outFile,
                                                 @NonNull Type type) throws IOException {
        try (InputStream fileIn = new BufferedInputStream(new FileInputStream(archive));
             InputStream payloadIn = wrapTarPayloadInputStream(fileIn, type);
             TarArchiveInputStream tar = new TarArchiveInputStream(payloadIn)) {
            ArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                if (!tar.canReadEntryData(entry)) return false;
                if (entry instanceof TarArchiveEntry) {
                    TarArchiveEntry tarEntry = (TarArchiveEntry) entry;
                    if (tarEntry.isSymbolicLink() || tarEntry.isLink()) return false;
                }
                if (entry.isDirectory()) continue;
                String path = sanitizeEntryPathForList(entry.getName());
                if (!entryPath.equals(path)) continue;
                return writeArchiveEntryStream(tar, outFile);
            }
            return false;
        }
    }

    private static boolean extractSevenZIntoDirectory(@NonNull File archive,
                                                      @NonNull File targetDir,
                                                      @Nullable char[] password) throws IOException {
        byte[] buffer = new byte[1024 * 64];
        boolean sawEntry = false;
        try (SevenZFile sevenZ = openSevenZFile(archive, password)) {
            SevenZArchiveEntry entry;
            while ((entry = sevenZ.getNextEntry()) != null) {
                File out = resolveArchiveEntryOutput(targetDir, entry.getName());
                if (out == null) return false;
                sawEntry = true;
                if (entry.isDirectory() || entry.getName().replace('\\', '/').endsWith("/")) {
                    if (!out.exists() && !out.mkdirs()) return false;
                    continue;
                }
                File outParent = out.getParentFile();
                if (outParent == null) return false;
                if (!outParent.exists() && !outParent.mkdirs()) return false;
                try (OutputStream outStream = new BufferedOutputStream(new FileOutputStream(out))) {
                    if (entry.hasStream()) {
                        int read;
                        while ((read = sevenZ.read(buffer)) > 0) {
                            outStream.write(buffer, 0, read);
                        }
                    }
                    outStream.flush();
                }
            }
            return sawEntry;
        }
    }

    private static boolean extractSingleSevenZEntry(@NonNull File archive,
                                                    @NonNull String entryPath,
                                                    @NonNull File outFile,
                                                    @Nullable char[] password) throws IOException {
        byte[] buffer = new byte[1024 * 64];
        try (SevenZFile sevenZ = openSevenZFile(archive, password)) {
            SevenZArchiveEntry entry;
            while ((entry = sevenZ.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String path = sanitizeEntryPathForList(entry.getName());
                if (!entryPath.equals(path)) {
                    drainSevenZEntry(sevenZ, entry, buffer);
                    continue;
                }
                try (OutputStream outStream = new BufferedOutputStream(new FileOutputStream(outFile))) {
                    if (entry.hasStream()) {
                        int read;
                        while ((read = sevenZ.read(buffer)) > 0) {
                            outStream.write(buffer, 0, read);
                        }
                    }
                    outStream.flush();
                    return true;
                }
            }
            return false;
        }
    }

    private static void drainSevenZEntry(@NonNull SevenZFile sevenZ,
                                         @NonNull SevenZArchiveEntry entry,
                                         @NonNull byte[] buffer) throws IOException {
        if (!entry.hasStream()) return;
        while (sevenZ.read(buffer) > 0) {
            // Drain unread payload before moving to the next entry in solid archives.
        }
    }

    private static SevenZFile openSevenZFile(@NonNull File archive, @Nullable char[] password) throws IOException {
        if (password != null && password.length > 0) {
            return new SevenZFile(archive, password);
        }
        return new SevenZFile(archive);
    }

    private static InputStream wrapTarPayloadInputStream(@NonNull InputStream input, @NonNull Type type) throws IOException {
        switch (type) {
            case TAR_GZ:
                return new GzipCompressorInputStream(input);
            case TAR_BZ2:
                return new BZip2CompressorInputStream(input);
            case TAR_XZ:
                return new XZCompressorInputStream(input);
            case TAR_LZMA:
                return new LZMACompressorInputStream(input);
            case TAR_Z:
                return new ZCompressorInputStream(input);
            case TAR:
            default:
                return input;
        }
    }


    private static InputStream wrapSingleCompressedInputStream(@NonNull InputStream input, @NonNull Type type) throws IOException {
        switch (type) {
            case SINGLE_GZ:
                return new GzipCompressorInputStream(input);
            case SINGLE_BZ2:
                return new BZip2CompressorInputStream(input);
            case SINGLE_XZ:
                return new XZCompressorInputStream(input);
            case SINGLE_LZMA:
                return new LZMACompressorInputStream(input);
            case SINGLE_Z:
                return new ZCompressorInputStream(input);
            default:
                throw new IOException("Unsupported single-file compression format");
        }
    }

    @NonNull
    private static String getSingleCompressedOutputName(@NonNull File archive) {
        String name = archive.getName();
        String lower = name.toLowerCase(Locale.ROOT);
        if (isFirstNumericSplitName(lower)) {
            name = name.substring(0, name.length() - 4);
            lower = lower.substring(0, lower.length() - 4);
        }
        String[] extensions = new String[] {".lzma", ".bz2", ".gz", ".xz", ".z"};
        for (String ext : extensions) {
            if (lower.endsWith(ext) && name.length() > ext.length()) {
                return name.substring(0, name.length() - ext.length());
            }
        }
        return name.length() > 0 ? name + ".out" : "decompressed";
    }

    @Nullable
    private static File resolveArchiveEntryOutput(@NonNull File targetDir, String rawEntryName) {
        String entryName = sanitizeEntryPathForList(rawEntryName);
        if (entryName == null) return null;
        File out = new File(targetDir, entryName);
        return isSameOrDescendant(targetDir, out) ? out : null;
    }

    private static boolean writeArchiveEntryStream(@NonNull InputStream in, @NonNull File out) throws IOException {
        File outParent = out.getParentFile();
        if (outParent == null) return false;
        if (!outParent.exists() && !outParent.mkdirs()) return false;
        byte[] buffer = new byte[1024 * 64];
        try (OutputStream outStream = new BufferedOutputStream(new FileOutputStream(out))) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                outStream.write(buffer, 0, read);
            }
            outStream.flush();
            return true;
        }
    }

    @Nullable
    private static String sanitizeEntryPathForList(String rawEntryName) {
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

    private static String normalizeDisplayPath(@NonNull String path, boolean directory) {
        String p = path.replace('\\', '/');
        if (directory && !p.endsWith("/")) p += "/";
        return p;
    }

    @NonNull
    private static List<EntryInfo> withSyntheticDirectories(@NonNull List<EntryInfo> entries) {
        Map<String, EntryInfo> map = new LinkedHashMap<>();
        for (EntryInfo entry : entries) {
            String path = entry.path;
            int slash = path.indexOf('/');
            while (slash >= 0) {
                String dir = path.substring(0, slash + 1);
                if (!map.containsKey(dir)) map.put(dir, new EntryInfo(dir, true, -1L, 0L));
                slash = path.indexOf('/', slash + 1);
            }
            map.put(path, entry);
        }
        return new ArrayList<>(map.values());
    }

    @Nullable
    private static File buildTempExtractDirectory(@NonNull File parentDir, @NonNull String targetName) {
        String base = ".textview_extract_" + targetName + "_" + System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            File candidate = new File(parentDir, i == 0 ? base : base + "_" + i);
            if (!candidate.exists()) return candidate;
        }
        return null;
    }

    private static boolean copyDirectoryRecursively(@NonNull File sourceDir, @NonNull File destinationDir) {
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
                deleteFileSystemItem(destinationDir);
                return false;
            }
        }
        return true;
    }

    private static boolean copyRegularFile(@NonNull File source, @NonNull File destination) {
        File parent = destination.getParentFile();
        if (parent == null || !parent.exists() || !parent.isDirectory()) return false;
        byte[] buffer = new byte[1024 * 64];
        boolean copied;
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(destination)) {
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            out.flush();
            copied = destination.length() == source.length();
        } catch (IOException | SecurityException ignored) {
            copied = false;
        }
        if (!copied) {
            try { destination.delete(); } catch (SecurityException ignored) {}
        }
        return copied;
    }

    private static boolean deleteFileSystemItem(@NonNull File target) {
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
                if (!deleteFileSystemItem(child)) return false;
            }
        }
        try {
            return target.delete();
        } catch (SecurityException ignored) {
            return false;
        }
    }

    private static boolean isSameOrDescendant(@NonNull File ancestor, @NonNull File candidate) {
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
            return candidatePath.equals(ancestorPath) || candidatePath.startsWith(ancestorPath + File.separator);
        }
    }
}
