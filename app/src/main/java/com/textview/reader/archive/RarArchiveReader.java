package com.textview.reader.archive;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.spec.KeySpec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

final class RarArchiveReader {
    private static final byte[] RAR5_SIGNATURE = new byte[] {
            0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00
    };
    private static final byte[] RAR4_SIGNATURE = new byte[] {
            0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00
    };

    private static final int MAX_SFX_SCAN = 1024 * 1024;
    private static final int BUFFER_SIZE = 1024 * 64;

    private static final int HEADER_MAIN = 1;
    private static final int HEADER_FILE = 2;
    private static final int HEADER_SERVICE = 3;
    private static final int HEADER_ENCRYPTION = 4;
    private static final int HEADER_END = 5;

    private static final int RAR4_HEADER_MAIN = 0x73;
    private static final int RAR4_HEADER_FILE = 0x74;
    private static final int RAR4_HEADER_END = 0x7b;
    private static final int RAR4_MAIN_PASSWORD = 0x0080;
    private static final int RAR4_LONG_BLOCK = 0x8000;
    private static final int RAR4_FILE_SPLIT_BEFORE = 0x0001;
    private static final int RAR4_FILE_SPLIT_AFTER = 0x0002;
    private static final int RAR4_FILE_PASSWORD = 0x0004;
    private static final int RAR4_FILE_SOLID = 0x0010;
    private static final int RAR4_FILE_DIRECTORY_MASK = 0x00e0;
    private static final int RAR4_FILE_DIRECTORY = 0x00e0;
    private static final int RAR4_FILE_LARGE = 0x0100;
    private static final int RAR4_FILE_UNICODE_NAME = 0x0200;
    private static final int RAR4_FILE_SALT = 0x0400;
    private static final int RAR4_FILE_EXT_TIME = 0x1000;
    private static final int RAR4_METHOD_STORE = 0x30;

    private static final long HEADER_FLAG_EXTRA = 0x0001L;
    private static final long HEADER_FLAG_DATA = 0x0002L;
    private static final long HEADER_FLAG_DATA_CONTINUES_PREVIOUS = 0x0008L;
    private static final long HEADER_FLAG_DATA_CONTINUES_NEXT = 0x0010L;

    private static final long FILE_FLAG_DIRECTORY = 0x0001L;
    private static final long FILE_FLAG_UNIX_MTIME = 0x0002L;
    private static final long FILE_FLAG_CRC32 = 0x0004L;
    private static final long FILE_FLAG_UNKNOWN_UNPACKED_SIZE = 0x0008L;

    private static final int EXTRA_FILE_ENCRYPTION = 0x01;
    private static final int EXTRA_FILE_TIME = 0x03;
    private static final int RAR5_ENCRYPTION_VERSION_AES256 = 0;
    private static final int RAR5_ENCRYPTION_CHECK_VALUE = 0x0001;
    private static final int MAX_RAR5_KDF_COUNT = 24;
    private static final Pattern RAR_NEW_STYLE_PART = Pattern.compile("^(.*)\\.part(\\d+)\\.rar$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RAR_OLD_STYLE_PART = Pattern.compile("^(.*)\\.r(\\d{2,3})$", Pattern.CASE_INSENSITIVE);

    private RarArchiveReader() {}

    @NonNull
    static List<ArchiveSupport.EntryInfo> listEntries(@NonNull File archive) throws IOException {
        return listEntries(archive, null);
    }

    @NonNull
    static List<ArchiveSupport.EntryInfo> listEntries(@NonNull File archive,
                                                      @Nullable char[] password) throws IOException {
        List<RarEntry> entries = readEntries(archive, password);
        List<ArchiveSupport.EntryInfo> result = new ArrayList<>();
        for (RarEntry entry : entries) {
            if (entry.splitBefore) continue;
            result.add(new ArchiveSupport.EntryInfo(entry.path, entry.directory, entry.unpackedSize, entry.timeMillis));
        }
        return withSyntheticDirectories(result);
    }

    static boolean requiresPasswordForExtraction(@NonNull File archive) {
        try {
            for (RarEntry entry : readEntries(archive, null)) {
                if (!entry.directory && entry.encrypted()) return true;
            }
            return false;
        } catch (ArchiveSupport.PasswordRequiredException e) {
            return true;
        } catch (IOException | SecurityException ignored) {
            return false;
        }
    }

    static boolean extractArchiveIntoDirectory(@NonNull File archive, @NonNull File targetDir) throws IOException {
        return extractArchiveIntoDirectory(archive, targetDir, null);
    }

    static boolean extractArchiveIntoDirectory(@NonNull File archive,
                                               @NonNull File targetDir,
                                               @Nullable char[] password) throws IOException {
        List<RarEntry> entries = readEntries(archive, password);
        boolean sawEntry = false;
        for (RarEntry entry : entries) {
            if (entry.splitBefore) continue;
            File out = resolveOutput(targetDir, entry.path);
            if (out == null) return false;
            sawEntry = true;
            if (entry.directory || entry.path.endsWith("/")) {
                if (!out.exists() && !out.mkdirs()) return false;
                continue;
            }
            extractStoredEntry(entry, out, password, entries);
        }
        return sawEntry;
    }

    static boolean extractSingleEntry(@NonNull File archive,
                                      @NonNull String entryPath,
                                      @NonNull File outFile) throws IOException {
        return extractSingleEntry(archive, entryPath, outFile, null);
    }

    static boolean extractSingleEntry(@NonNull File archive,
                                      @NonNull String entryPath,
                                      @NonNull File outFile,
                                      @Nullable char[] password) throws IOException {
        String normalized = sanitizeEntryPath(entryPath);
        if (normalized == null || normalized.endsWith("/")) return false;
        List<RarEntry> entries = readEntries(archive, password);
        for (RarEntry entry : entries) {
            if (entry.directory || entry.splitBefore) continue;
            if (!normalized.equals(entry.path)) continue;
            extractStoredEntry(entry, outFile, password, entries);
            return true;
        }
        return false;
    }

    @NonNull
    private static List<RarEntry> readEntries(@NonNull File archive) throws IOException {
        return readEntries(archive, null);
    }

    @NonNull
    private static List<RarEntry> readEntries(@NonNull File archive,
                                              @Nullable char[] password) throws IOException {
        List<File> volumes = collectRarVolumes(archive);
        List<RarEntry> result = new ArrayList<>();
        for (File volume : volumes) {
            try (RandomAccessFile raf = new RandomAccessFile(volume, "r")) {
                result.addAll(readSingleVolumeEntries(volume, raf, password));
            } catch (IOException e) {
                if (volume.equals(archive) || result.isEmpty()) throw e;
                break;
            }
        }
        return result;
    }

    @NonNull
    private static List<RarEntry> readSingleVolumeEntries(@NonNull File archive,
                                                          @NonNull RandomAccessFile raf,
                                                          @Nullable char[] password) throws IOException {
            Signature signature = findSignature(raf);
            if (signature == null) throw new IOException("Not a RAR archive");
            List<RarEntry> entries;
            if (signature.version != 5) {
                raf.seek(signature.offset + RAR4_SIGNATURE.length);
                entries = readRar4Entries(raf, password);
            } else {
                raf.seek(signature.offset + RAR5_SIGNATURE.length);
                entries = readRar5Entries(raf, password);
            }
            for (RarEntry entry : entries) entry.sourceArchive = archive;
            return entries;
    }

    @NonNull
    private static List<RarEntry> readRar4Entries(@NonNull RandomAccessFile raf,
                                                  @Nullable char[] password) throws IOException {
        List<RarEntry> result = new ArrayList<>();
        while (raf.getFilePointer() < raf.length()) {
            long headerStart = raf.getFilePointer();
            if (raf.length() - headerStart < 7) break;
            readUInt16LE(raf); // Header CRC. RAR4 CRC coverage varies by block kind; treat it as advisory for now.
            int type = raf.readUnsignedByte();
            int flags = readUInt16LE(raf);
            int headerSize = readUInt16LE(raf);
            if (headerSize < 7 || headerStart + headerSize > raf.length()) {
                throw new IOException("Invalid RAR4 header size");
            }

            byte[] headerData = new byte[headerSize - 7];
            raf.readFully(headerData);
            long dataSize = 0L;
            if (type == RAR4_HEADER_FILE) {
                RarEntry entry = parseRar4FileBlock(headerStart + headerSize, flags, headerData);
                if (entry != null) {
                    result.add(entry);
                    dataSize = entry.packedSize;
                }
            } else if (type == RAR4_HEADER_MAIN && (flags & RAR4_MAIN_PASSWORD) != 0) {
                if (password != null && password.length > 0) {
                    throw new UnsupportedRarFeatureException("Encrypted RAR4 headers are not supported yet");
                }
                throw new ArchiveSupport.PasswordRequiredException();
            } else if ((flags & RAR4_LONG_BLOCK) != 0 && headerData.length >= 4) {
                dataSize = uint32FromBytes(headerData, 0);
            }

            long next = headerStart + headerSize + dataSize;
            if (next < headerStart || next > raf.length()) throw new IOException("Invalid RAR4 data size");
            raf.seek(next);
            if (type == RAR4_HEADER_END) break;
        }
        return result;
    }

    @Nullable
    private static RarEntry parseRar4FileBlock(long dataOffset, int flags, @NonNull byte[] headerData) throws IOException {
        ByteCursor cursor = new ByteCursor(headerData);
        long packSize = readUInt32LE(cursor);
        long unpackedSize = readUInt32LE(cursor);
        cursor.readUnsignedByte(); // Host OS.
        long dataCrc = readUInt32LE(cursor);
        long dosTime = readUInt32LE(cursor);
        cursor.readUnsignedByte(); // Minimum RAR version needed to extract.
        int method = cursor.readUnsignedByte();
        int nameSize = readUInt16LE(cursor);
        cursor.readUnsignedInt(); // File attributes.

        if ((flags & RAR4_FILE_LARGE) != 0) {
            long highPack = readUInt32LE(cursor);
            long highUnpacked = readUInt32LE(cursor);
            packSize |= highPack << 32;
            unpackedSize |= highUnpacked << 32;
        }
        if (nameSize < 0 || nameSize > cursor.remaining()) throw new IOException("Invalid RAR4 name size");
        byte[] rawName = cursor.readBytes(nameSize);
        String path = decodeRar4Name(rawName, (flags & RAR4_FILE_UNICODE_NAME) != 0);
        String sanitized = sanitizeEntryPath(path);
        if (sanitized == null) return null;

        boolean directory = (flags & RAR4_FILE_DIRECTORY_MASK) == RAR4_FILE_DIRECTORY || sanitized.endsWith("/");
        boolean encrypted = (flags & RAR4_FILE_PASSWORD) != 0;
        boolean splitBefore = (flags & RAR4_FILE_SPLIT_BEFORE) != 0;
        boolean splitAfter = (flags & RAR4_FILE_SPLIT_AFTER) != 0;
        boolean solid = (flags & RAR4_FILE_SOLID) != 0;
        int normalizedMethod = method == RAR4_METHOD_STORE ? 0 : method;
        long timeMillis = dosTimeToMillis(dosTime);
        return new RarEntry(sanitized, directory, unpackedSize, packSize, dataOffset, 4,
                normalizedMethod, solid, splitBefore, splitAfter, encrypted ? EncryptionInfo.rar4Unsupported() : null, dataCrc, timeMillis);
    }

    @NonNull
    private static List<RarEntry> readRar5Entries(@NonNull RandomAccessFile raf,
                                                  @Nullable char[] password) throws IOException {
        List<RarEntry> result = new ArrayList<>();
        while (raf.getFilePointer() < raf.length()) {
            HeaderBlock block = readRar5Header(raf);
            if (block == null) break;
            if (block.type == HEADER_ENCRYPTION) {
                if (password != null && password.length > 0) {
                    throw new UnsupportedRarFeatureException("Encrypted RAR5 headers are not supported yet");
                }
                throw new ArchiveSupport.PasswordRequiredException();
            }
            if (block.type == HEADER_FILE) {
                RarEntry entry = parseRar5FileBlock(block);
                if (entry != null) result.add(entry);
            }
            long next = block.dataOffset + block.dataSize;
            if (next < block.dataOffset || next > raf.length()) throw new IOException("Invalid RAR data size");
            raf.seek(next);
            if (block.type == HEADER_END) break;
        }
        return result;
    }

    @Nullable
    private static HeaderBlock readRar5Header(@NonNull RandomAccessFile raf) throws IOException {
        long headerStart = raf.getFilePointer();
        if (headerStart >= raf.length()) return null;
        if (raf.length() - headerStart < 5) return null;

        long headerCrc = readUInt32LE(raf);
        VInt headerSizeVint = readVInt(raf);
        if (headerSizeVint.value < 0 || headerSizeVint.value > 2L * 1024L * 1024L) {
            throw new IOException("Invalid RAR header size");
        }
        if (headerSizeVint.value > raf.length() - raf.getFilePointer()) {
            throw new IOException("RAR header exceeds file length");
        }

        byte[] headerData = new byte[(int) headerSizeVint.value];
        raf.readFully(headerData);
        verifyHeaderCrc(headerCrc, headerSizeVint.encoded, headerData);

        ByteCursor cursor = new ByteCursor(headerData);
        int type = checkedInt(cursor.readVInt());
        long flags = cursor.readVInt();
        long extraSize = (flags & HEADER_FLAG_EXTRA) != 0 ? cursor.readVInt() : 0L;
        long dataSize = (flags & HEADER_FLAG_DATA) != 0 ? cursor.readVInt() : 0L;
        int fieldsStart = cursor.position();
        int extraStart = safeExtraStart(headerData.length, extraSize);
        long dataOffset = raf.getFilePointer();

        return new HeaderBlock(type, flags, dataSize, dataOffset, headerData, fieldsStart, extraStart, extraSize);
    }

    @Nullable
    private static RarEntry parseRar5FileBlock(@NonNull HeaderBlock block) throws IOException {
        ByteCursor cursor = new ByteCursor(block.headerData, block.fieldsStart, block.extraStart);
        long fileFlags = cursor.readVInt();
        boolean directory = (fileFlags & FILE_FLAG_DIRECTORY) != 0;
        if ((fileFlags & FILE_FLAG_UNKNOWN_UNPACKED_SIZE) != 0) {
            throw new UnsupportedRarFeatureException("Unknown RAR unpacked size is not supported");
        }
        long unpackedSize = cursor.readVInt();
        cursor.readVInt(); // Attributes.
        long timeMillis = 0L;
        if ((fileFlags & FILE_FLAG_UNIX_MTIME) != 0) {
            timeMillis = readUInt32LE(cursor) * 1000L;
        }
        long dataCrc = -1L;
        if ((fileFlags & FILE_FLAG_CRC32) != 0) {
            dataCrc = readUInt32LE(cursor);
        }
        long compressionInfo = cursor.readVInt();
        cursor.readVInt(); // Host OS.
        long nameLength = cursor.readVInt();
        if (nameLength < 0 || nameLength > Integer.MAX_VALUE || nameLength > cursor.remaining()) {
            throw new IOException("Invalid RAR entry name length");
        }
        String path = new String(cursor.readBytes((int) nameLength), StandardCharsets.UTF_8);
        String sanitized = sanitizeEntryPath(path);
        if (sanitized == null) return null;

        ExtraInfo extra = parseFileExtra(block.headerData, block.extraStart, block.extraSize);
        if (extra.timeMillis > 0L) timeMillis = extra.timeMillis;

        int method = (int) ((compressionInfo & 0x0380L) >> 7);
        boolean solid = (compressionInfo & 0x0040L) != 0;
        boolean splitBefore = (block.flags & HEADER_FLAG_DATA_CONTINUES_PREVIOUS) != 0;
        boolean splitAfter = (block.flags & HEADER_FLAG_DATA_CONTINUES_NEXT) != 0;
        return new RarEntry(
                sanitized,
                directory,
                unpackedSize,
                block.dataSize,
                block.dataOffset,
                5,
                method,
                solid,
                splitBefore,
                splitAfter,
                extra.encryption,
                dataCrc,
                timeMillis);
    }

    @NonNull
    private static ExtraInfo parseFileExtra(@NonNull byte[] headerData,
                                            int extraStart,
                                            long extraSize) throws IOException {
        ExtraInfo info = new ExtraInfo();
        if (extraSize <= 0L) return info;
        long extraEndLong = (long) extraStart + extraSize;
        if (extraStart < 0 || extraEndLong > headerData.length) throw new IOException("Invalid RAR extra area");
        ByteCursor cursor = new ByteCursor(headerData, extraStart, (int) extraEndLong);
        while (cursor.hasRemaining()) {
            int recordStart = cursor.position();
            long recordSize = cursor.readVInt();
            if (recordSize < 0 || recordSize > cursor.remaining()) throw new IOException("Invalid RAR extra record");
            int recordEnd = checkedInt((long) cursor.position() + recordSize);
            if (recordEnd > cursor.limit()) throw new IOException("Invalid RAR extra record range");
            long recordType = cursor.readVInt();
            if (recordType == EXTRA_FILE_ENCRYPTION) {
                info.encryption = parseFileEncryptionRecord(cursor, recordEnd);
            } else if (recordType == EXTRA_FILE_TIME) {
                info.timeMillis = parseFileTimeRecord(cursor, recordEnd);
            }
            cursor.setPosition(recordEnd);
            if (cursor.position() <= recordStart) throw new IOException("Invalid RAR extra cursor");
        }
        return info;
    }

    @NonNull
    private static EncryptionInfo parseFileEncryptionRecord(@NonNull ByteCursor cursor,
                                                            int recordEnd) throws IOException {
        long version = cursor.readVInt();
        long flags = cursor.readVInt();
        int kdfCount = cursor.readUnsignedByte();
        byte[] salt = cursor.readBytes(16);
        byte[] iv = cursor.readBytes(16);
        byte[] check = ((flags & RAR5_ENCRYPTION_CHECK_VALUE) != 0 && cursor.position() + 12 <= recordEnd)
                ? cursor.readBytes(12)
                : new byte[0];
        return new EncryptionInfo(version, flags, kdfCount, salt, iv, check);
    }

    private static long parseFileTimeRecord(@NonNull ByteCursor cursor, int recordEnd) throws IOException {
        if (cursor.position() >= recordEnd) return 0L;
        long flags = cursor.readVInt();
        boolean unix = (flags & 0x0001L) != 0;
        boolean hasMtime = (flags & 0x0002L) != 0;
        boolean nanos = (flags & 0x0010L) != 0;
        if (!hasMtime) return 0L;
        if (unix) {
            long value = nanos ? readInt64LE(cursor) : readUInt32LE(cursor);
            return nanos ? value / 1_000_000L : value * 1000L;
        }
        long fileTime = readInt64LE(cursor);
        return windowsFileTimeToMillis(fileTime);
    }

    private static void extractStoredEntry(@NonNull RarEntry entry,
                                           @NonNull File outFile,
                                           @Nullable char[] password,
                                           @NonNull List<RarEntry> allEntries) throws IOException {
        if (entry.directory) return;
        if (entry.splitBefore) throw new UnsupportedRarFeatureException("RAR split continuation cannot be extracted directly");
        if (entry.solid) throw new UnsupportedRarFeatureException("Solid RAR entries are not supported");
        if (entry.method != 0) {
            if (entry.rarVersion >= 5) {
                throw new UnsupportedRarFeatureException("Compressed RAR5 entries are not supported yet");
            }
            if (entry.splitAfter) {
                throw new UnsupportedRarFeatureException("Compressed split RAR entries are not supported yet");
            }
            extractWithJunrarFallback(entry, outFile, password);
            verifyExtractedCrc(entry, outFile);
            return;
        }
        if (!entry.encrypted() && entry.packedSize != entry.unpackedSize) {
            throw new UnsupportedRarFeatureException("Stored RAR size mismatch");
        }

        File parent = outFile.getParentFile();
        if (parent == null) throw new IOException("Output file has no parent");
        if (!parent.exists() && !parent.mkdirs()) throw new IOException("Cannot create output directory");

        if (entry.splitAfter) {
            extractSplitStoredEntry(entry, outFile, password, allEntries);
            return;
        } else if (entry.encrypted()) {
            try (RandomAccessFile raf = openEntrySource(entry)) {
                raf.seek(entry.dataOffset);
                extractEncryptedStoredEntry(raf, entry, outFile, password);
            }
        } else {
            try (RandomAccessFile raf = openEntrySource(entry)) {
                raf.seek(entry.dataOffset);
                extractPlainStoredEntry(raf, entry, outFile);
            }
        }
        verifyExtractedCrc(entry, outFile);
    }

    private static void extractWithJunrarFallback(@NonNull RarEntry entry,
                                                  @NonNull File outFile,
                                                  @Nullable char[] password) throws IOException {
        if (entry.sourceArchive == null) throw new IOException("RAR entry source volume is missing");
        boolean extracted = RarJunrarFallback.extractSingleEntry(
                entry.sourceArchive,
                entry.path,
                outFile,
                password);
        if (!extracted) {
            throw new UnsupportedRarFeatureException("RAR fallback could not extract entry");
        }
    }

    private static void extractSplitStoredEntry(@NonNull RarEntry first,
                                                @NonNull File outFile,
                                                @Nullable char[] password,
                                                @NonNull List<RarEntry> allEntries) throws IOException {
        List<RarEntry> chain = buildSplitChain(first, allEntries);
        if (chain.isEmpty() || chain.get(chain.size() - 1).splitAfter) {
            throw new UnsupportedRarFeatureException("Incomplete RAR split payload");
        }
        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(outFile))) {
            for (RarEntry part : chain) {
                if (part.directory || part.solid || part.method != 0) {
                    throw new UnsupportedRarFeatureException("Unsupported RAR split payload");
                }
                if (part.encrypted()) {
                    throw new UnsupportedRarFeatureException("Encrypted split RAR payload is not supported yet");
                }
                try (RandomAccessFile raf = openEntrySource(part)) {
                    raf.seek(part.dataOffset);
                    copyStoredPayloadToStream(raf, part.packedSize, out);
                }
            }
            out.flush();
        }
        verifyExtractedCrc(chain.get(chain.size() - 1), outFile);
    }

    @NonNull
    private static List<RarEntry> buildSplitChain(@NonNull RarEntry first,
                                                  @NonNull List<RarEntry> allEntries) throws IOException {
        List<RarEntry> chain = new ArrayList<>();
        chain.add(first);
        RarEntry current = first;
        while (current.splitAfter) {
            RarEntry next = null;
            int currentIndex = allEntries.indexOf(current);
            for (int i = Math.max(0, currentIndex + 1); i < allEntries.size(); i++) {
                RarEntry candidate = allEntries.get(i);
                if (candidate.directory) continue;
                if (candidate.splitBefore && candidate.path.equals(first.path)) {
                    next = candidate;
                    break;
                }
            }
            if (next == null) throw new UnsupportedRarFeatureException("Missing RAR split continuation");
            chain.add(next);
            current = next;
        }
        return chain;
    }

    @NonNull
    private static RandomAccessFile openEntrySource(@NonNull RarEntry entry) throws IOException {
        if (entry.sourceArchive == null) throw new IOException("RAR entry source volume is missing");
        return new RandomAccessFile(entry.sourceArchive, "r");
    }

    private static void extractPlainStoredEntry(@NonNull RandomAccessFile raf,
                                                @NonNull RarEntry entry,
                                                @NonNull File outFile) throws IOException {
        long remaining = entry.packedSize;
        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(outFile))) {
            copyStoredPayloadToStream(raf, remaining, out);
            out.flush();
        }
    }

    private static void copyStoredPayloadToStream(@NonNull RandomAccessFile raf,
                                                  long size,
                                                  @NonNull BufferedOutputStream out) throws IOException {
        long remaining = size;
        byte[] buffer = new byte[BUFFER_SIZE];
        while (remaining > 0L) {
            int request = (int) Math.min(buffer.length, remaining);
            int read = raf.read(buffer, 0, request);
            if (read < 0) throw new EOFException("Unexpected EOF in RAR entry");
            out.write(buffer, 0, read);
            remaining -= read;
        }
    }

    private static void extractEncryptedStoredEntry(@NonNull RandomAccessFile raf,
                                                    @NonNull RarEntry entry,
                                                    @NonNull File outFile,
                                                    @Nullable char[] password) throws IOException {
        if (password == null || password.length == 0) throw new ArchiveSupport.PasswordRequiredException();
        EncryptionInfo encryption = entry.encryption;
        if (encryption == null || !encryption.isRar5Aes256()) {
            throw new UnsupportedRarFeatureException("Encrypted RAR file data is not supported yet");
        }
        if (entry.packedSize > Integer.MAX_VALUE) {
            throw new UnsupportedRarFeatureException("Encrypted RAR entry is too large for this decoder pass");
        }
        if ((entry.packedSize % 16L) != 0L) {
            throw new UnsupportedRarFeatureException("Encrypted RAR data is not AES block aligned");
        }
        byte[] encrypted = new byte[(int) entry.packedSize];
        raf.readFully(encrypted);
        byte[] key = deriveRar5FileKey(password, encryption);
        byte[] decrypted;
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new IvParameterSpec(encryption.iv));
            decrypted = cipher.doFinal(encrypted);
        } catch (GeneralSecurityException e) {
            throw new IOException("RAR AES decrypt failed", e);
        }
        if (entry.unpackedSize < 0 || entry.unpackedSize > decrypted.length) {
            throw new IOException("Invalid decrypted RAR stored size");
        }
        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(outFile))) {
            out.write(decrypted, 0, (int) entry.unpackedSize);
            out.flush();
        }
    }

    @NonNull
    private static byte[] deriveRar5FileKey(@NonNull char[] password,
                                            @NonNull EncryptionInfo encryption) throws IOException {
        if (encryption.kdfCount < 0 || encryption.kdfCount > MAX_RAR5_KDF_COUNT) {
            throw new UnsupportedRarFeatureException("RAR KDF count is too high");
        }
        int iterations = 1 << encryption.kdfCount;
        KeySpec spec = new PBEKeySpec(password, encryption.salt, iterations, 256);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IOException("RAR key derivation failed", e);
        }
    }

    private static void verifyExtractedCrc(@NonNull RarEntry entry, @NonNull File outFile) throws IOException {
        if (entry.dataCrc < 0) return;
        CRC32 crc32 = new CRC32();
        byte[] buffer = new byte[BUFFER_SIZE];
        try (FileInputStream in = new FileInputStream(outFile)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                crc32.update(buffer, 0, read);
            }
        }
        if ((crc32.getValue() & 0xffffffffL) != entry.dataCrc) {
            try { outFile.delete(); } catch (SecurityException ignored) {}
            throw new IOException("RAR entry CRC mismatch");
        }
    }

    @Nullable
    private static Signature findSignature(@NonNull RandomAccessFile raf) throws IOException {
        raf.seek(0L);
        int scanLimit = (int) Math.min(Math.max(raf.length(), 0L), MAX_SFX_SCAN + RAR5_SIGNATURE.length);
        byte[] data = new byte[scanLimit];
        raf.readFully(data);
        int rar5 = indexOf(data, RAR5_SIGNATURE);
        int rar4 = indexOf(data, RAR4_SIGNATURE);
        if (rar5 < 0 && rar4 < 0) return null;
        if (rar5 >= 0 && (rar4 < 0 || rar5 <= rar4)) return new Signature(rar5, 5);
        return new Signature(rar4, 4);
    }

    private static int indexOf(@NonNull byte[] haystack, @NonNull byte[] needle) {
        if (needle.length == 0 || haystack.length < needle.length) return -1;
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    private static void verifyHeaderCrc(long expected,
                                        @NonNull byte[] encodedHeaderSize,
                                        @NonNull byte[] headerData) throws IOException {
        CRC32 crc32 = new CRC32();
        crc32.update(encodedHeaderSize, 0, encodedHeaderSize.length);
        crc32.update(headerData, 0, headerData.length);
        long actual = crc32.getValue() & 0xffffffffL;
        if (actual != (expected & 0xffffffffL)) throw new IOException("RAR header CRC mismatch");
    }

    private static long readUInt32LE(@NonNull RandomAccessFile raf) throws IOException {
        long b0 = raf.readUnsignedByte();
        long b1 = raf.readUnsignedByte();
        long b2 = raf.readUnsignedByte();
        long b3 = raf.readUnsignedByte();
        return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
    }

    private static int readUInt16LE(@NonNull RandomAccessFile raf) throws IOException {
        int b0 = raf.readUnsignedByte();
        int b1 = raf.readUnsignedByte();
        return b0 | (b1 << 8);
    }

    private static long readUInt32LE(@NonNull ByteCursor cursor) throws IOException {
        long b0 = cursor.readUnsignedByte();
        long b1 = cursor.readUnsignedByte();
        long b2 = cursor.readUnsignedByte();
        long b3 = cursor.readUnsignedByte();
        return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
    }

    private static int readUInt16LE(@NonNull ByteCursor cursor) throws IOException {
        int b0 = cursor.readUnsignedByte();
        int b1 = cursor.readUnsignedByte();
        return b0 | (b1 << 8);
    }

    private static long readInt64LE(@NonNull ByteCursor cursor) throws IOException {
        long value = 0L;
        for (int i = 0; i < 8; i++) {
            value |= ((long) cursor.readUnsignedByte()) << (8 * i);
        }
        return value;
    }

    @NonNull
    private static VInt readVInt(@NonNull RandomAccessFile raf) throws IOException {
        long value = 0L;
        int shift = 0;
        byte[] encoded = new byte[10];
        int count = 0;
        while (count < encoded.length) {
            int b = raf.readUnsignedByte();
            encoded[count++] = (byte) b;
            value |= (long) (b & 0x7f) << shift;
            if ((b & 0x80) == 0) {
                byte[] used = new byte[count];
                System.arraycopy(encoded, 0, used, 0, count);
                return new VInt(value, used);
            }
            shift += 7;
        }
        throw new IOException("RAR vint is too long");
    }

    private static int safeExtraStart(int headerLength, long extraSize) throws IOException {
        long start = (long) headerLength - extraSize;
        if (extraSize < 0 || start < 0 || start > headerLength) throw new IOException("Invalid RAR extra size");
        return (int) start;
    }

    private static int checkedInt(long value) throws IOException {
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) throw new IOException("Integer overflow");
        return (int) value;
    }

    @NonNull
    private static List<File> collectRarVolumes(@NonNull File archive) {
        File parent = archive.getParentFile();
        if (parent == null) {
            List<File> single = new ArrayList<>();
            single.add(archive);
            return single;
        }
        String name = archive.getName();
        String lower = name.toLowerCase(Locale.ROOT);
        Matcher newStyle = RAR_NEW_STYLE_PART.matcher(lower);
        if (newStyle.matches()) {
            String prefix = name.substring(0, lower.lastIndexOf(".part"));
            List<File> volumes = collectNewStyleRarVolumes(parent, prefix);
            return volumes.isEmpty() ? singletonArchive(archive) : volumes;
        }
        Matcher oldStyle = RAR_OLD_STYLE_PART.matcher(lower);
        if (oldStyle.matches()) {
            String prefix = name.substring(0, name.length() - 4);
            List<File> volumes = collectOldStyleRarVolumes(parent, prefix);
            return volumes.isEmpty() ? singletonArchive(archive) : volumes;
        }
        if (lower.endsWith(".rar")) {
            String prefix = name.substring(0, name.length() - 4);
            List<File> newStyleVolumes = collectNewStyleRarVolumes(parent, prefix);
            if (newStyleVolumes.size() > 1) return newStyleVolumes;
            List<File> oldStyleVolumes = collectOldStyleRarVolumes(parent, prefix);
            if (oldStyleVolumes.size() > 1) return oldStyleVolumes;
        }
        return singletonArchive(archive);
    }

    @NonNull
    private static List<File> singletonArchive(@NonNull File archive) {
        List<File> result = new ArrayList<>();
        result.add(archive);
        return result;
    }

    @NonNull
    private static List<File> collectNewStyleRarVolumes(@NonNull File parent, @NonNull String prefix) {
        List<File> result = new ArrayList<>();
        for (int i = 1; i <= 9999; i++) {
            File part = new File(parent, prefix + ".part" + i + ".rar");
            if (!part.exists() || !part.isFile()) break;
            result.add(part);
        }
        return result;
    }

    @NonNull
    private static List<File> collectOldStyleRarVolumes(@NonNull File parent, @NonNull String prefix) {
        File first = new File(parent, prefix + ".rar");
        if (!first.exists() || !first.isFile()) return new ArrayList<>();
        List<File> result = new ArrayList<>();
        result.add(first);
        for (int i = 0; i <= 999; i++) {
            File part = new File(parent, String.format(Locale.ROOT, "%s.r%02d", prefix, i));
            if (!part.exists() || !part.isFile()) break;
            result.add(part);
        }
        return result;
    }

    private static long uint32FromBytes(@NonNull byte[] data, int offset) {
        return ((long) data[offset] & 0xff)
                | (((long) data[offset + 1] & 0xff) << 8)
                | (((long) data[offset + 2] & 0xff) << 16)
                | (((long) data[offset + 3] & 0xff) << 24);
    }

    @Nullable
    private static File resolveOutput(@NonNull File targetDir, @NonNull String entryPath) throws IOException {
        String path = sanitizeEntryPath(entryPath);
        if (path == null) return null;
        File out = new File(targetDir, path);
        return isSameOrDescendant(targetDir, out) ? out : null;
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

    private static long windowsFileTimeToMillis(long fileTime) {
        return (fileTime / 10_000L) - 11_644_473_600_000L;
    }

    private static long dosTimeToMillis(long dosTime) {
        int second = (int) ((dosTime & 0x1f) * 2);
        int minute = (int) ((dosTime >> 5) & 0x3f);
        int hour = (int) ((dosTime >> 11) & 0x1f);
        int day = (int) ((dosTime >> 16) & 0x1f);
        int month = (int) ((dosTime >> 21) & 0x0f);
        int year = (int) (((dosTime >> 25) & 0x7f) + 1980);
        if (month < 1 || month > 12 || day < 1 || day > 31) return 0L;
        java.util.Calendar calendar = java.util.Calendar.getInstance(java.util.TimeZone.getDefault(), java.util.Locale.ROOT);
        calendar.clear();
        calendar.set(year, month - 1, day, hour, minute, second);
        return calendar.getTimeInMillis();
    }

    @NonNull
    private static String decodeRar4Name(@NonNull byte[] rawName, boolean unicodeName) {
        int plainLength = rawName.length;
        if (unicodeName) {
            for (int i = 0; i < rawName.length; i++) {
                if (rawName[i] == 0) {
                    plainLength = i;
                    break;
                }
            }
        }
        byte[] plain = new byte[plainLength];
        System.arraycopy(rawName, 0, plain, 0, plainLength);
        if (unicodeName && plainLength + 1 < rawName.length) {
            String decoded = decodeRar4UnicodeName(plain, rawName, plainLength + 1);
            if (decoded != null && decoded.length() > 0) return decoded;
        }
        String utf8 = tryDecode(plain, StandardCharsets.UTF_8);
        if (utf8 != null) return utf8;
        return new String(plain, Charset.forName("IBM437"));
    }

    @Nullable
    private static String decodeRar4UnicodeName(@NonNull byte[] plainName,
                                                @NonNull byte[] rawName,
                                                int encodedOffset) {
        if (encodedOffset >= rawName.length) return null;
        int encodedPos = encodedOffset;
        int highByte = rawName[encodedPos++] & 0xff;
        int flags = 0;
        int flagBits = 0;
        int plainPos = 0;
        StringBuilder decoded = new StringBuilder(rawName.length);
        try {
            while (encodedPos < rawName.length) {
                if (flagBits == 0) {
                    flags = rawName[encodedPos++] & 0xff;
                    flagBits = 8;
                    if (encodedPos > rawName.length) break;
                }
                int mode = (flags >>> 6) & 0x03;
                flags = (flags << 2) & 0xff;
                flagBits -= 2;
                switch (mode) {
                    case 0:
                        if (encodedPos >= rawName.length) return null;
                        decoded.append((char) (rawName[encodedPos++] & 0xff));
                        plainPos++;
                        break;
                    case 1:
                        if (encodedPos >= rawName.length) return null;
                        decoded.append((char) ((highByte << 8) | (rawName[encodedPos++] & 0xff)));
                        plainPos++;
                        break;
                    case 2:
                        if (encodedPos + 1 >= rawName.length) return null;
                        int low = rawName[encodedPos++] & 0xff;
                        int high = rawName[encodedPos++] & 0xff;
                        decoded.append((char) (low | (high << 8)));
                        plainPos++;
                        break;
                    case 3:
                        if (encodedPos >= rawName.length) return null;
                        int length = rawName[encodedPos++] & 0xff;
                        if ((length & 0x80) != 0) {
                            if (encodedPos >= rawName.length) return null;
                            int correction = rawName[encodedPos++] & 0xff;
                            int count = (length & 0x7f) + 2;
                            for (int i = 0; i < count; i++) {
                                if (plainPos >= plainName.length) return null;
                                int value = ((plainName[plainPos++] & 0xff) + correction) & 0xff;
                                decoded.append((char) ((highByte << 8) | value));
                            }
                        } else {
                            int count = length + 2;
                            for (int i = 0; i < count; i++) {
                                if (plainPos >= plainName.length) return null;
                                decoded.append((char) (plainName[plainPos++] & 0xff));
                            }
                        }
                        break;
                    default:
                        return null;
                }
            }
        } catch (RuntimeException ignored) {
            return null;
        }
        return decoded.length() > 0 ? decoded.toString() : null;
    }

    @Nullable
    private static String tryDecode(@NonNull byte[] bytes, @NonNull Charset charset) {
        try {
            return charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException ignored) {
            return null;
        }
    }

    private static final class Signature {
        final long offset;
        final int version;

        Signature(long offset, int version) {
            this.offset = offset;
            this.version = version;
        }
    }

    private static final class VInt {
        final long value;
        final byte[] encoded;

        VInt(long value, @NonNull byte[] encoded) {
            this.value = value;
            this.encoded = encoded;
        }
    }

    private static final class HeaderBlock {
        final int type;
        final long flags;
        final long dataSize;
        final long dataOffset;
        final byte[] headerData;
        final int fieldsStart;
        final int extraStart;
        final long extraSize;

        HeaderBlock(int type,
                    long flags,
                    long dataSize,
                    long dataOffset,
                    @NonNull byte[] headerData,
                    int fieldsStart,
                    int extraStart,
                    long extraSize) {
            this.type = type;
            this.flags = flags;
            this.dataSize = dataSize;
            this.dataOffset = dataOffset;
            this.headerData = headerData;
            this.fieldsStart = fieldsStart;
            this.extraStart = extraStart;
            this.extraSize = extraSize;
        }
    }

    private static final class RarEntry {
        final String path;
        final boolean directory;
        final long unpackedSize;
        final long packedSize;
        final long dataOffset;
        final int rarVersion;
        final int method;
        final boolean solid;
        final boolean splitBefore;
        final boolean splitAfter;
        @Nullable final EncryptionInfo encryption;
        final long dataCrc;
        final long timeMillis;
        @Nullable File sourceArchive;

        RarEntry(@NonNull String path,
                 boolean directory,
                 long unpackedSize,
                 long packedSize,
                 long dataOffset,
                 int rarVersion,
                 int method,
                 boolean solid,
                 boolean splitBefore,
                 boolean splitAfter,
                 @Nullable EncryptionInfo encryption,
                 long dataCrc,
                 long timeMillis) {
            this.path = directory && !path.endsWith("/") ? path + "/" : path;
            this.directory = directory;
            this.unpackedSize = unpackedSize;
            this.packedSize = packedSize;
            this.dataOffset = dataOffset;
            this.rarVersion = rarVersion;
            this.method = method;
            this.solid = solid;
            this.splitBefore = splitBefore;
            this.splitAfter = splitAfter;
            this.encryption = encryption;
            this.dataCrc = dataCrc;
            this.timeMillis = timeMillis;
        }

        boolean encrypted() {
            return encryption != null;
        }
    }

    private static final class ExtraInfo {
        @Nullable EncryptionInfo encryption;
        long timeMillis;
    }

    private static final class EncryptionInfo {
        final long version;
        final long flags;
        final int kdfCount;
        final byte[] salt;
        final byte[] iv;
        final byte[] check;
        final boolean rar4Unsupported;

        EncryptionInfo(long version,
                       long flags,
                       int kdfCount,
                       @NonNull byte[] salt,
                       @NonNull byte[] iv,
                       @NonNull byte[] check) {
            this.version = version;
            this.flags = flags;
            this.kdfCount = kdfCount;
            this.salt = salt;
            this.iv = iv;
            this.check = check;
            this.rar4Unsupported = false;
        }

        private EncryptionInfo() {
            this.version = -1L;
            this.flags = 0L;
            this.kdfCount = 0;
            this.salt = new byte[0];
            this.iv = new byte[0];
            this.check = new byte[0];
            this.rar4Unsupported = true;
        }

        static EncryptionInfo rar4Unsupported() {
            return new EncryptionInfo();
        }

        boolean isRar5Aes256() {
            return !rar4Unsupported
                    && version == RAR5_ENCRYPTION_VERSION_AES256
                    && salt.length == 16
                    && iv.length == 16;
        }
    }

    static final class UnsupportedRarFeatureException extends ArchiveSupport.UnsupportedArchiveFeatureException {
        UnsupportedRarFeatureException(@NonNull String message) {
            super(message);
        }
    }

    private static final class ByteCursor {
        private final byte[] data;
        private final int limit;
        private int position;

        ByteCursor(@NonNull byte[] data) {
            this(data, 0, data.length);
        }

        ByteCursor(@NonNull byte[] data, int start, int limit) {
            this.data = data;
            this.position = start;
            this.limit = limit;
        }

        int position() {
            return position;
        }

        int limit() {
            return limit;
        }

        void setPosition(int position) throws IOException {
            if (position < 0 || position > limit) throw new IOException("Invalid RAR cursor position");
            this.position = position;
        }

        boolean hasRemaining() {
            return position < limit;
        }

        int remaining() {
            return limit - position;
        }

        int readUnsignedByte() throws IOException {
            if (position >= limit) throw new EOFException("Unexpected end of RAR header");
            return data[position++] & 0xff;
        }

        long readVInt() throws IOException {
            long value = 0L;
            int shift = 0;
            for (int i = 0; i < 10; i++) {
                int b = readUnsignedByte();
                value |= (long) (b & 0x7f) << shift;
                if ((b & 0x80) == 0) return value;
                shift += 7;
            }
            throw new IOException("RAR vint is too long");
        }

        byte[] readBytes(int length) throws IOException {
            if (length < 0 || length > remaining()) throw new EOFException("Unexpected end of RAR header bytes");
            byte[] result = new byte[length];
            System.arraycopy(data, position, result, 0, length);
            position += length;
            return result;
        }

        long readUnsignedInt() throws IOException {
            return readUInt32LE(this);
        }
    }
}
