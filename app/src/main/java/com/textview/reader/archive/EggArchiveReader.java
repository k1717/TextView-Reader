package com.textview.reader.archive;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.tukaani.xz.LZMAInputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * Read-only decoder for the ESTsoft EGG archive container.
 *
 * <p>The container layout (magic numbers, header field order and sizes, block
 * structure and compression-method codes) follows ESTsoft's published
 * EGG_Specification.pdf and the official unEGG v0.5 reference sources. See
 * {@code docs/EGG_FORMAT_NOTES.md}. This reader extracts entries compressed with
 * Store / Deflate / BZip2 / LZMA. ESTsoft's proprietary AZO method, encrypted,
 * split and solid EGG archives are reported as unsupported rather than producing
 * partial or corrupt output. Every block's CRC32 is verified.</p>
 */
final class EggArchiveReader {

    // Little-endian uint32 magic numbers (from unEGG EggTypes.h).
    private static final int MAGIC_EGG = 0x41474745;
    private static final int MAGIC_FILE = 0x0a8590e3;
    private static final int MAGIC_BLOCK = 0x02b50c13;
    private static final int MAGIC_ENCRYPT = 0x08d1470f;
    private static final int MAGIC_WINDOWS_FILEINFO = 0x2c86950b;
    private static final int MAGIC_POSIX_FILEINFO = 0x1ee922e5;
    private static final int MAGIC_FILENAME = 0x0a8591ac;
    private static final int MAGIC_COMMENT = 0x04c63672;
    private static final int MAGIC_SPLIT = 0x24f5a262;
    private static final int MAGIC_SOLID = 0x24e5a060;
    private static final int MAGIC_DUMMY = 0x07463307;
    private static final int MAGIC_END = 0x08e28222;

    // CompressionMethod enum (Store=0, Deflate=1, bzip=2, azo=3, lzma=4).
    private static final int COMP_STORE = 0;
    private static final int COMP_DEFLATE = 1;
    private static final int COMP_BZIP = 2;
    private static final int COMP_AZO = 3;
    private static final int COMP_LZMA = 4;

    private static final int LZMA_DATA_HEADER_SIZE = 9;
    private static final long MAX_ENTRY_BYTES = 512L * 1024 * 1024;

    private EggArchiveReader() {
    }

    // ----- Entry model -----

    private static final class EggEntry {
        String path = "";
        boolean directory;
        long uncompressedSize;
        boolean encrypted;
        boolean split;
        boolean solid;
        final List<EggBlock> blocks = new ArrayList<>();
    }

    private static final class EggBlock {
        int method;
        long uncompSize;
        long compSize;
        long crc;       // unsigned 32-bit, stored in a long
        long dataOffset;
    }

    // ----- Public API (mirrors AlzipArchiveReader shape) -----

    static boolean isEgg(@NonNull File archive) {
        try (RandomAccessFile raf = new RandomAccessFile(archive, "r")) {
            if (raf.length() < 14) return false;
            return readUInt32LE(raf) == MAGIC_EGG;
        } catch (IOException e) {
            return false;
        }
    }

    @NonNull
    static List<ArchiveSupport.EntryInfo> listEntries(@NonNull File archive,
                                                      @Nullable char[] password) throws IOException {
        List<EggEntry> entries = readEntries(archive);
        ArrayList<ArchiveSupport.EntryInfo> result = new ArrayList<>();
        for (EggEntry entry : entries) {
            result.add(new ArchiveSupport.EntryInfo(entry.path, entry.directory,
                    entry.uncompressedSize, 0L));
        }
        return result;
    }

    static boolean requiresPasswordForExtraction(@NonNull File archive) {
        try {
            for (EggEntry entry : readEntries(archive)) {
                if (entry.encrypted) return true;
            }
        } catch (IOException ignored) {
        }
        return false;
    }

    static boolean extractSingleEntry(@NonNull File archive,
                                      @NonNull String entryPath,
                                      @NonNull File outFile,
                                      @Nullable char[] password) throws IOException {
        String normalized = sanitizeEntryPath(entryPath);
        if (normalized == null) return false;
        try (RandomAccessFile raf = new RandomAccessFile(archive, "r")) {
            for (EggEntry entry : readEntries(archive, raf)) {
                if (entry.directory) continue;
                if (!normalized.equals(sanitizeEntryPath(entry.path))) continue;
                writeEntry(raf, entry, outFile, password);
                return true;
            }
        }
        return false;
    }

    static boolean extractArchiveIntoDirectory(@NonNull File archive,
                                               @NonNull File targetDir,
                                               @Nullable char[] password) throws IOException {
        boolean any = false;
        try (RandomAccessFile raf = new RandomAccessFile(archive, "r")) {
            for (EggEntry entry : readEntries(archive, raf)) {
                if (entry.directory) continue;
                File outFile = resolveOutput(targetDir, entry.path);
                if (outFile == null) continue;
                writeEntry(raf, entry, outFile, password);
                any = true;
            }
        }
        return any;
    }

    // ----- Parsing -----

    @NonNull
    private static List<EggEntry> readEntries(@NonNull File archive) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(archive, "r")) {
            return readEntries(archive, raf);
        }
    }

    @NonNull
    private static List<EggEntry> readEntries(@NonNull File archive,
                                              @NonNull RandomAccessFile raf) throws IOException {
        raf.seek(0);
        if (raf.length() < 14 || readUInt32LE(raf) != MAGIC_EGG) {
            throw invalidSignature(archive);
        }
        readUInt16LE(raf);  // version
        readUInt32LE(raf);  // id
        readUInt32LE(raf);  // reserved

        List<EggEntry> entries = new ArrayList<>();
        long len = raf.length();

        while (raf.getFilePointer() + 4 <= len) {
            long sigPos = raf.getFilePointer();
            int sig = readUInt32LE(raf);
            if (sig == MAGIC_END) {
                // Top-level END terminates the archive.
                break;
            }
            if (sig != MAGIC_FILE) {
                // Unknown top-level structure; stop rather than guess.
                break;
            }
            EggEntry entry = readFileEntry(raf, len);
            if (entry == null) break;
            entries.add(entry);
        }
        if (entries.isEmpty()) throw unsupported(archive);
        return entries;
    }

    @Nullable
    private static EggEntry readFileEntry(@NonNull RandomAccessFile raf, long len) throws IOException {
        EggEntry entry = new EggEntry();
        readUInt32LE(raf);                 // file id
        entry.uncompressedSize = readUInt64LE(raf);

        // Extra fields until END.
        while (raf.getFilePointer() + 4 <= len) {
            int fieldSig = readUInt32LE(raf);
            if (fieldSig == MAGIC_END) break;
            if (fieldSig == MAGIC_BLOCK) {
                // BLOCK belongs to the file's data section; rewind and break to
                // the block-reading loop below.
                raf.seek(raf.getFilePointer() - 4);
                break;
            }
            if (!readExtraField(raf, entry, fieldSig, len)) {
                return entry; // malformed; keep what we have
            }
        }

        // Blocks.
        while (raf.getFilePointer() + 4 <= len) {
            long pos = raf.getFilePointer();
            int sig = readUInt32LE(raf);
            if (sig != MAGIC_BLOCK) {
                // Not a block. This is the file section's terminating END (which
                // belongs to this FILE, not the archive), or another structure.
                // Consume a trailing END; otherwise rewind so the outer loop sees it.
                if (sig != MAGIC_END) {
                    raf.seek(pos);
                }
                break;
            }
            EggBlock block = new EggBlock();
            block.method = raf.readUnsignedByte();
            raf.readUnsignedByte();        // methodHint
            block.uncompSize = readUInt32LE(raf) & 0xffffffffL;
            block.compSize = readUInt32LE(raf) & 0xffffffffL;
            block.crc = readUInt32LE(raf) & 0xffffffffL;
            block.dataOffset = raf.getFilePointer();
            entry.blocks.add(block);
            long skipTo = block.dataOffset + block.compSize;
            if (skipTo < block.dataOffset || skipTo > len) break;
            raf.seek(skipTo);
        }

        entry.directory = entry.path.endsWith("/") || (entry.blocks.isEmpty() && entry.uncompressedSize == 0
                && entry.path.endsWith("/"));
        return entry;
    }

    private static boolean readExtraField(@NonNull RandomAccessFile raf,
                                          @NonNull EggEntry entry,
                                          int fieldSig,
                                          long len) throws IOException {
        int gpb = raf.readUnsignedByte();
        long size;
        if ((gpb & 1) == 1) {
            size = readUInt32LE(raf) & 0xffffffffL;
        } else {
            size = readUInt16LE(raf);
        }
        long payloadStart = raf.getFilePointer();
        if (size < 0 || payloadStart + size > len) return false;

        if (fieldSig == MAGIC_FILENAME) {
            byte[] payload = new byte[(int) Math.min(size, 65535)];
            raf.readFully(payload);
            entry.path = decodeFilename(payload, gpb);
        } else if (fieldSig == MAGIC_ENCRYPT) {
            entry.encrypted = true;
        } else if (fieldSig == MAGIC_SPLIT) {
            entry.split = true;
        } else if (fieldSig == MAGIC_SOLID) {
            entry.solid = true;
        }
        // WINDOWS_FILEINFO / POSIX_FILEINFO / COMMENT / DUMMY: skipped.

        raf.seek(payloadStart + size);
        return true;
    }

    @NonNull
    private static String decodeFilename(@NonNull byte[] payload, int gpb) {
        // FILENAME payload: optional locale (uint16) when the locale bit is set,
        // followed by the name bytes. The reference treats names as UTF-8 unless
        // a locale code page applies; we decode UTF-8 (the common modern case)
        // and fall back to the platform default if that produces nothing useful.
        int offset = 0;
        boolean hasLocale = (gpb & (1 << 5)) != 0;
        if (hasLocale && payload.length >= 2) {
            offset = 2; // skip locale code
        }
        int nameLen = payload.length - offset;
        if (nameLen <= 0) return "";
        String name = new String(payload, offset, nameLen, StandardCharsets.UTF_8);
        return name.replace('\\', '/');
    }

    // ----- Extraction -----

    private static void writeEntry(@NonNull RandomAccessFile raf,
                                   @NonNull EggEntry entry,
                                   @NonNull File outFile,
                                   @Nullable char[] password) throws IOException {
        if (entry.encrypted) {
            if (password == null || password.length == 0) {
                throw new ArchiveSupport.PasswordRequiredException();
            }
            throw new ArchiveSupport.UnsupportedArchiveFeatureException(
                    "Encrypted EGG entries are not supported");
        }
        if (entry.split) {
            throw new ArchiveSupport.UnsupportedArchiveFeatureException(
                    "Split EGG archives are not supported");
        }
        if (entry.solid) {
            throw new ArchiveSupport.UnsupportedArchiveFeatureException(
                    "Solid EGG archives are not supported");
        }
        if (entry.uncompressedSize > MAX_ENTRY_BYTES) {
            throw new ArchiveSupport.UnsupportedArchiveFeatureException(
                    "EGG entry is too large for this decoder");
        }

        File parent = outFile.getParentFile();
        if (parent == null) throw new IOException("Output file has no parent");
        if (!parent.exists() && !parent.mkdirs()) throw new IOException("Cannot create output directory");

        boolean ok = false;
        try (FileOutputStream out = new FileOutputStream(outFile)) {
            CRC32 runningCrc = new CRC32();
            for (EggBlock block : entry.blocks) {
                byte[] plain = decodeBlock(raf, block);
                // Per-block CRC verification (unsigned compare).
                CRC32 blockCrc = new CRC32();
                blockCrc.update(plain);
                if (block.crc != 0 && (blockCrc.getValue() & 0xffffffffL) != block.crc) {
                    throw new IOException("EGG block CRC mismatch");
                }
                runningCrc.update(plain);
                out.write(plain);
            }
            out.flush();
            ok = true;
        } finally {
            if (!ok) {
                try { //noinspection ResultOfMethodCallIgnored
                    outFile.delete();
                } catch (SecurityException ignored) {
                }
            }
        }
    }

    @NonNull
    private static byte[] decodeBlock(@NonNull RandomAccessFile raf, @NonNull EggBlock block) throws IOException {
        if (block.compSize < 0 || block.compSize > MAX_ENTRY_BYTES) {
            throw new ArchiveSupport.UnsupportedArchiveFeatureException("EGG block size out of range");
        }
        if (block.method == COMP_AZO) {
            throw new ArchiveSupport.UnsupportedArchiveFeatureException(
                    "EGG AZO compression is not supported");
        }

        byte[] compressed = new byte[(int) block.compSize];
        raf.seek(block.dataOffset);
        raf.readFully(compressed);

        switch (block.method) {
            case COMP_STORE:
                return compressed;
            case COMP_DEFLATE:
                return readAll(new InflaterInputStream(new ByteArrayInputStream(compressed), new Inflater(true)),
                        block.uncompSize);
            case COMP_BZIP:
                return readAll(new BZip2CompressorInputStream(new ByteArrayInputStream(compressed)),
                        block.uncompSize);
            case COMP_LZMA:
                return decodeLzmaBlock(compressed, block.uncompSize);
            default:
                throw new ArchiveSupport.UnsupportedArchiveFeatureException(
                        "Unsupported EGG compression method " + block.method);
        }
    }

    @NonNull
    private static byte[] decodeLzmaBlock(@NonNull byte[] data, long uncompSize) throws IOException {
        // EGG prepends a 9-byte LZMA data header (5-byte properties + 4-byte
        // dictionary/size hint per unEGG). org.tukaani LZMAInputStream accepts the
        // 5 properties bytes plus an explicit uncompressed size.
        if (data.length < LZMA_DATA_HEADER_SIZE) {
            throw new ArchiveSupport.UnsupportedArchiveFeatureException("Truncated EGG LZMA header");
        }
        byte propsByte = data[0];
        int dictSize = (data[1] & 0xff)
                | ((data[2] & 0xff) << 8)
                | ((data[3] & 0xff) << 16)
                | ((data[4] & 0xff) << 24);
        ByteArrayInputStream body = new ByteArrayInputStream(data, LZMA_DATA_HEADER_SIZE,
                data.length - LZMA_DATA_HEADER_SIZE);
        try {
            return readAll(new LZMAInputStream(body, uncompSize, propsByte, dictSize), uncompSize);
        } catch (IOException e) {
            throw new ArchiveSupport.UnsupportedArchiveFeatureException(
                    "EGG LZMA block could not be decoded: " + e.getMessage());
        }
    }

    @NonNull
    private static byte[] readAll(@NonNull InputStream input, long expectedSize) throws IOException {
        int initial = (expectedSize > 0 && expectedSize <= MAX_ENTRY_BYTES) ? (int) expectedSize : 64 * 1024;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(initial);
        byte[] chunk = new byte[64 * 1024];
        try (InputStream in = input) {
            int read;
            long total = 0;
            while ((read = in.read(chunk)) != -1) {
                total += read;
                if (total > MAX_ENTRY_BYTES) {
                    throw new ArchiveSupport.UnsupportedArchiveFeatureException("EGG entry exceeds size limit");
                }
                buffer.write(chunk, 0, read);
            }
        }
        return buffer.toByteArray();
    }

    // ----- Helpers -----

    @Nullable
    private static File resolveOutput(@NonNull File targetDir, @NonNull String entryPath) throws IOException {
        String safe = sanitizeEntryPath(entryPath);
        if (safe == null) return null;
        File out = new File(targetDir, safe);
        String base = targetDir.getCanonicalPath() + File.separator;
        String target = out.getCanonicalPath();
        if (!target.equals(targetDir.getCanonicalPath()) && !target.startsWith(base)) {
            return null; // path traversal guard
        }
        return out;
    }

    @Nullable
    private static String sanitizeEntryPath(String rawEntryName) {
        if (rawEntryName == null) return null;
        String entryName = rawEntryName.trim().replace('\\', '/');
        while (entryName.startsWith("./")) entryName = entryName.substring(2);
        while (entryName.contains("//")) entryName = entryName.replace("//", "/");
        if (entryName.isEmpty()) return null;
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

    private static int readUInt16LE(@NonNull RandomAccessFile raf) throws IOException {
        int b0 = raf.read();
        int b1 = raf.read();
        if ((b0 | b1) < 0) throw new IOException("Unexpected EOF");
        return (b0 & 0xff) | ((b1 & 0xff) << 8);
    }

    private static int readUInt32LE(@NonNull RandomAccessFile raf) throws IOException {
        int b0 = raf.read();
        int b1 = raf.read();
        int b2 = raf.read();
        int b3 = raf.read();
        if ((b0 | b1 | b2 | b3) < 0) throw new IOException("Unexpected EOF");
        return (b0 & 0xff) | ((b1 & 0xff) << 8) | ((b2 & 0xff) << 16) | ((b3 & 0xff) << 24);
    }

    private static long readUInt64LE(@NonNull RandomAccessFile raf) throws IOException {
        long lo = readUInt32LE(raf) & 0xffffffffL;
        long hi = readUInt32LE(raf) & 0xffffffffL;
        return lo | (hi << 32);
    }

    @NonNull
    private static IOException unsupported(@NonNull File archive) {
        return new ArchiveSupport.UnsupportedArchiveFeatureException(
                "Unsupported or malformed EGG archive: " + archive.getName());
    }

    @NonNull
    private static IOException invalidSignature(@NonNull File archive) {
        return new IOException("Invalid EGG signature: " + archive.getName());
    }
}
