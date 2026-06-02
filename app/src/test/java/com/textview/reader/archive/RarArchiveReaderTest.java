package com.textview.reader.archive;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.textview.reader.util.FileUtils;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.zip.CRC32;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class RarArchiveReaderTest {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void archiveSupport_detectsRarAndCbrNames() {
        assertEquals(ArchiveSupport.Type.RAR, ArchiveSupport.getSupportedArchiveType("sample.rar"));
        assertEquals(ArchiveSupport.Type.RAR, ArchiveSupport.getSupportedArchiveType("sample.cbr"));
        assertEquals(ArchiveSupport.Type.RAR, ArchiveSupport.getSupportedArchiveType("sample.part1.rar"));
        assertEquals(ArchiveSupport.Type.RAR, ArchiveSupport.getSupportedArchiveType("sample.r00"));
        assertTrue(FileUtils.isArchiveFile("sample.rar"));
        assertTrue(FileUtils.isArchiveFile("sample.cbr"));
        assertTrue(FileUtils.isArchiveFile("sample.part1.rar"));
        assertTrue(FileUtils.isArchiveFile("sample.r00"));
    }

    @Test
    public void listEntries_rar5StoredArchive_returnsFileMetadata() throws Exception {
        File archive = buildRar5Archive("comic/page001.txt", "hello rar".getBytes(StandardCharsets.UTF_8), 0);

        List<ArchiveSupport.EntryInfo> entries = ArchiveSupport.listEntries(archive, null);

        assertEquals(2, entries.size());
        assertEquals("comic/", entries.get(0).path);
        assertTrue(entries.get(0).directory);
        assertEquals("comic/page001.txt", entries.get(1).path);
        assertFalse(entries.get(1).directory);
        assertEquals(9L, entries.get(1).size);
    }

    @Test
    public void extractSingleEntry_rar5StoredArchive_writesPayload() throws Exception {
        byte[] payload = "stored image bytes".getBytes(StandardCharsets.UTF_8);
        File archive = buildRar5Archive("page001.jpg", payload, 0);
        File out = tempFolder.newFile("page001.jpg");

        assertTrue(ArchiveSupport.extractSingleEntry(archive, "page001.jpg", out, null));

        assertArrayEquals(payload, Files.readAllBytes(out.toPath()));
    }

    @Test
    public void extractSingleEntry_rar5StoredCrcMismatchDeletesPartialOutput() throws Exception {
        byte[] payload = "stored image bytes".getBytes(StandardCharsets.UTF_8);
        File archive = buildRar5Archive("page001.jpg", payload, 0, false, 0x12345678L);
        File out = tempFolder.newFile("crc-mismatch.jpg");

        assertFalse(ArchiveSupport.extractSingleEntry(archive, "page001.jpg", out, null));
        assertFalse(out.exists());
    }

    @Test
    public void extractSingleEntry_compressedRar5Entry_failsExplicitly() throws Exception {
        File archive = buildRar5Archive("page001.jpg", "payload".getBytes(StandardCharsets.UTF_8), 1);
        File out = tempFolder.newFile("page001.jpg");

        assertFalse(ArchiveSupport.extractSingleEntry(archive, "page001.jpg", out, null));
    }

    @Test
    public void listEntries_encryptedRar5FileData_stillListsVisibleNames() throws Exception {
        File archive = buildRar5Archive("private/page001.txt", "secret".getBytes(StandardCharsets.UTF_8), 0, true);

        List<ArchiveSupport.EntryInfo> entries = ArchiveSupport.listEntries(archive, null);

        assertEquals("private/", entries.get(0).path);
        assertEquals("private/page001.txt", entries.get(1).path);
    }

    @Test
    public void extractSingleEntry_encryptedRar5FileData_failsWithoutCorruptOutput() throws Exception {
        File archive = buildRar5Archive("page001.txt", "secret".getBytes(StandardCharsets.UTF_8), 0, true);
        File out = tempFolder.newFile("encrypted.txt");

        assertFalse(ArchiveSupport.extractSingleEntry(archive, "page001.txt", out, null));
    }

    @Test
    public void extractSingleEntry_encryptedRar5StoredData_attemptsPasswordDecrypt() throws Exception {
        byte[] payload = "secret".getBytes(StandardCharsets.UTF_8);
        File archive = buildRar5Archive("page001.txt", payload, 0, true);
        File out = tempFolder.newFile("decrypted.txt");

        assertTrue(ArchiveSupport.extractSingleEntry(archive, "page001.txt", out, "pw".toCharArray()));
        assertArrayEquals(payload, Files.readAllBytes(out.toPath()));
    }

    @Test
    public void extractSingleEntry_encryptedRar5StoredData_wrongPasswordDeletesPartialOutput() throws Exception {
        File archive = buildRar5Archive("page001.txt", "secret".getBytes(StandardCharsets.UTF_8), 0, true);
        File out = tempFolder.newFile("wrong-password.txt");

        assertFalse(ArchiveSupport.extractSingleEntry(archive, "page001.txt", out, "bad".toCharArray()));
        assertFalse(out.exists());
    }

    @Test(expected = ArchiveSupport.PasswordRequiredException.class)
    public void listEntries_rar5EncryptedHeaders_requestsPassword() throws Exception {
        ArchiveSupport.listEntries(buildRar5EncryptedHeaderArchive(), null);
    }

    @Test
    public void listEntries_rar5UnsafePath_skipsEntry() throws Exception {
        File archive = buildRar5Archive("../evil.txt", "evil".getBytes(StandardCharsets.UTF_8), 0);

        List<ArchiveSupport.EntryInfo> entries = ArchiveSupport.listEntries(archive, null);

        assertTrue(entries.isEmpty());
    }

    @Test
    public void extractArchive_rar5UnsafePathFailsWithoutWritingOutsideTarget() throws Exception {
        File archive = buildRar5Archive("../evil.txt", "evil".getBytes(StandardCharsets.UTF_8), 0);
        File target = new File(tempFolder.getRoot(), "rar-out");
        File outside = new File(tempFolder.getRoot(), "evil.txt");

        assertFalse(ArchiveSupport.extractArchive(archive, target, true, null));
        assertFalse(target.exists());
        assertFalse(outside.exists());
    }

    @Test
    public void listEntries_newStyleSplitRar_resolvesSelectedLaterPartToFirstVolume() throws Exception {
        File archive = buildRar5Archive("comic/page001.txt", "split payload".getBytes(StandardCharsets.UTF_8), 0);
        byte[] bytes = Files.readAllBytes(archive.toPath());
        File part1 = new File(archive.getParentFile(), "comic.part1.rar");
        File part2 = new File(archive.getParentFile(), "comic.part2.rar");
        Files.write(part1.toPath(), bytes);
        Files.write(part2.toPath(), new byte[] {0x01, 0x02});

        List<ArchiveSupport.EntryInfo> entries = ArchiveSupport.listEntries(part2, null);

        assertNotNull(entries);
        assertEquals("comic/page001.txt", entries.get(1).path);
    }

    @Test
    public void listEntries_oldStyleSplitRar_resolvesSelectedR00ToFirstVolume() throws Exception {
        File archive = buildRar5Archive("comic/page001.txt", "old split payload".getBytes(StandardCharsets.UTF_8), 0);
        byte[] bytes = Files.readAllBytes(archive.toPath());
        File first = new File(archive.getParentFile(), "comic.rar");
        File later = new File(archive.getParentFile(), "comic.r00");
        Files.write(first.toPath(), bytes);
        Files.write(later.toPath(), new byte[] {0x01, 0x02});

        List<ArchiveSupport.EntryInfo> entries = ArchiveSupport.listEntries(later, null);

        assertNotNull(entries);
        assertEquals("comic/page001.txt", entries.get(1).path);
    }

    @Test
    public void extractSingleEntry_newStyleSplitRarStoredPayload_writesCombinedData() throws Exception {
        byte[] payload = "split stored payload".getBytes(StandardCharsets.UTF_8);
        File part2 = buildRar5SplitStoredArchive("book", "comic/page001.txt", payload);
        File out = tempFolder.newFile("split-output.txt");

        assertTrue(ArchiveSupport.extractSingleEntry(part2, "comic/page001.txt", out, null));
        assertArrayEquals(payload, Files.readAllBytes(out.toPath()));
    }

    @Test
    public void listEntries_rar4StoredArchive_returnsFileMetadata() throws Exception {
        File archive = buildRar4Archive("comic/page001.txt", "hello rar4".getBytes(StandardCharsets.UTF_8), 0x30);

        List<ArchiveSupport.EntryInfo> entries = ArchiveSupport.listEntries(archive, null);

        assertEquals(2, entries.size());
        assertEquals("comic/", entries.get(0).path);
        assertTrue(entries.get(0).directory);
        assertEquals("comic/page001.txt", entries.get(1).path);
        assertFalse(entries.get(1).directory);
        assertEquals(10L, entries.get(1).size);
    }

    @Test
    public void extractSingleEntry_rar4StoredArchive_writesPayload() throws Exception {
        byte[] payload = "rar4 stored image bytes".getBytes(StandardCharsets.UTF_8);
        File archive = buildRar4Archive("page001.jpg", payload, 0x30);
        File out = tempFolder.newFile("page001-rar4.jpg");

        assertTrue(ArchiveSupport.extractSingleEntry(archive, "page001.jpg", out, null));

        assertArrayEquals(payload, Files.readAllBytes(out.toPath()));
    }

    @Test
    public void extractSingleEntry_compressedRar4SyntheticPayload_failsCleanlyThroughFallback() throws Exception {
        File archive = buildRar4Archive("page001.jpg", "payload".getBytes(StandardCharsets.UTF_8), 0x31);
        File out = tempFolder.newFile("compressed-rar4.jpg");

        assertFalse(ArchiveSupport.extractSingleEntry(archive, "page001.jpg", out, null));
    }

    @Test
    public void listEntries_rar4UnicodeName_decodesEncodedName() throws Exception {
        byte[] payload = "unicode-name".getBytes(StandardCharsets.UTF_8);
        String unicodeName = "한글/페이지.jpg";
        File archive = buildRar4ArchiveWithRawName(rar4UnicodeRawName("fallback/page.jpg", unicodeName), payload, 0x30, 0x0200);

        List<ArchiveSupport.EntryInfo> entries = ArchiveSupport.listEntries(archive, null);

        assertEquals("한글/", entries.get(0).path);
        assertEquals(unicodeName, entries.get(1).path);
    }

    @Test
    public void extractSingleEntry_rar4UnicodeName_usesDecodedPath() throws Exception {
        byte[] payload = "unicode extract".getBytes(StandardCharsets.UTF_8);
        String unicodeName = "한글/페이지.jpg";
        File archive = buildRar4ArchiveWithRawName(rar4UnicodeRawName("fallback/page.jpg", unicodeName), payload, 0x30, 0x0200);
        File out = tempFolder.newFile("unicode-rar4.jpg");

        assertTrue(ArchiveSupport.extractSingleEntry(archive, unicodeName, out, null));

        assertArrayEquals(payload, Files.readAllBytes(out.toPath()));
    }

    private File buildRar5Archive(String entryName, byte[] payload, int method) throws Exception {
        return buildRar5Archive(entryName, payload, method, false);
    }

    private File buildRar5Archive(String entryName, byte[] payload, int method, boolean encryptedFileData) throws Exception {
        return buildRar5Archive(entryName, payload, method, encryptedFileData, -1L);
    }

    private File buildRar5Archive(String entryName, byte[] payload, int method, boolean encryptedFileData, long crcOverride) throws Exception {
        File archive = tempFolder.newFile("sample.rar");
        try (FileOutputStream out = new FileOutputStream(archive)) {
            out.write(new byte[] {0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00});
            out.write(header(bytes(vint(1), vint(0), vint(0))));

            byte[] name = entryName.getBytes(StandardCharsets.UTF_8);
            CRC32 payloadCrc = new CRC32();
            payloadCrc.update(payload);
            long crc = crcOverride >= 0L ? crcOverride : payloadCrc.getValue();
            long compressionInfo = ((long) method) << 7;
            byte[] storedPayload = encryptedFileData ? encryptRar5StoredPayload(payload, "pw".toCharArray()) : payload;
            byte[] extra = encryptedFileData ? rar5EncryptionExtra() : new byte[0];
            out.write(header(bytes(
                    vint(2),
                    vint(encryptedFileData ? 0x0003 : 0x0002),
                    encryptedFileData ? vint(extra.length) : new byte[0],
                    vint(storedPayload.length),
                    vint(0x0004),
                    vint(payload.length),
                    vint(0),
                    uint32(crc),
                    vint(compressionInfo),
                    vint(1),
                    vint(name.length),
                    name,
                    extra)));
            out.write(storedPayload);
            out.write(header(bytes(vint(5), vint(0), vint(0))));
        }
        return archive;
    }

    private static byte[] rar5EncryptionExtra() throws Exception {
        byte[] record = bytes(
                vint(1),
                vint(0),
                vint(0),
                new byte[] {8},
                rar5TestSalt(),
                rar5TestIv());
        return bytes(vint(record.length), record);
    }

    private static byte[] encryptRar5StoredPayload(byte[] payload, char[] password) throws Exception {
        byte[] padded = new byte[((payload.length + 15) / 16) * 16];
        System.arraycopy(payload, 0, padded, 0, payload.length);
        byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(new PBEKeySpec(password, rar5TestSalt(), 1 << 8, 256))
                .getEncoded();
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(rar5TestIv()));
        return cipher.doFinal(padded);
    }

    private static byte[] rar5TestSalt() {
        return new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
    }

    private static byte[] rar5TestIv() {
        return new byte[] {16, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1};
    }

    private File buildRar5EncryptedHeaderArchive() throws Exception {
        File archive = tempFolder.newFile("encrypted-headers.rar");
        try (FileOutputStream out = new FileOutputStream(archive)) {
            out.write(new byte[] {0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00});
            out.write(header(bytes(vint(4), vint(0))));
        }
        return archive;
    }

    private File buildRar5SplitStoredArchive(String baseName, String entryName, byte[] payload) throws Exception {
        int cut = Math.max(1, payload.length / 2);
        byte[] firstPayload = java.util.Arrays.copyOfRange(payload, 0, cut);
        byte[] secondPayload = java.util.Arrays.copyOfRange(payload, cut, payload.length);
        File part1 = new File(tempFolder.getRoot(), baseName + ".part1.rar");
        File part2 = new File(tempFolder.getRoot(), baseName + ".part2.rar");
        writeRar5Volume(part1, entryName, firstPayload, 0x0010L, crc32(firstPayload), firstPayload.length);
        writeRar5Volume(part2, entryName, secondPayload, 0x0008L, crc32(payload), payload.length);
        return part2;
    }

    private static void writeRar5Volume(File file,
                                        String entryName,
                                        byte[] payload,
                                        long fileHeaderFlags,
                                        long crc,
                                        long unpackedSize) throws Exception {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(new byte[] {0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00});
            out.write(header(bytes(vint(1), vint(0), vint(0x0001))));
            byte[] name = entryName.getBytes(StandardCharsets.UTF_8);
            out.write(header(bytes(
                    vint(2),
                    vint(0x0002 | fileHeaderFlags),
                    vint(payload.length),
                    vint(0x0004),
                    vint(unpackedSize),
                    vint(0),
                    uint32(crc),
                    vint(0),
                    vint(1),
                    vint(name.length),
                    name)));
            out.write(payload);
            out.write(header(bytes(vint(5), vint(0), vint(0))));
        }
    }

    private static long crc32(byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(data);
        return crc.getValue();
    }

    private File buildRar4Archive(String entryName, byte[] payload, int method) throws Exception {
        return buildRar4ArchiveWithRawName(entryName.getBytes(StandardCharsets.UTF_8), payload, method, 0);
    }

    private File buildRar4ArchiveWithRawName(byte[] rawName, byte[] payload, int method, int extraFlags) throws Exception {
        File archive = tempFolder.newFile("sample-rar4.rar");
        try (FileOutputStream out = new FileOutputStream(archive)) {
            out.write(new byte[] {0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00});
            out.write(rar4Header(0x73, 0, new byte[] {0, 0, 0, 0, 0, 0}));

            CRC32 payloadCrc = new CRC32();
            payloadCrc.update(payload);
            byte[] body = bytes(
                    uint32(payload.length),
                    uint32(payload.length),
                    new byte[] {1},
                    uint32(payloadCrc.getValue()),
                    uint32(0),
                    new byte[] {29},
                    new byte[] {(byte) method},
                    uint16(rawName.length),
                    uint32(0),
                    rawName);
            out.write(rar4Header(0x74, 0x8000 | extraFlags, body));
            out.write(payload);
            out.write(rar4Header(0x7b, 0, new byte[0]));
        }
        return archive;
    }

    private static byte[] rar4UnicodeRawName(String fallbackName, String unicodeName) throws Exception {
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        encoded.write(fallbackName.getBytes(StandardCharsets.US_ASCII));
        encoded.write(0);
        encoded.write(0);

        int[] codeUnits = unicodeName.chars().toArray();
        int index = 0;
        while (index < codeUnits.length) {
            int group = Math.min(4, codeUnits.length - index);
            int flags = 0;
            for (int i = 0; i < group; i++) {
                flags |= 0x02 << (6 - (i * 2));
            }
            encoded.write(flags);
            for (int i = 0; i < group; i++) {
                int ch = codeUnits[index++];
                encoded.write(ch & 0xff);
                encoded.write((ch >>> 8) & 0xff);
            }
        }
        return encoded.toByteArray();
    }

    private static byte[] rar4Header(int type, int flags, byte[] body) throws Exception {
        int size = 7 + body.length;
        return bytes(
                uint16(0),
                new byte[] {(byte) type},
                uint16(flags),
                uint16(size),
                body);
    }

    private static byte[] header(byte[] headerData) throws Exception {
        byte[] headerSize = vint(headerData.length);
        CRC32 crc32 = new CRC32();
        crc32.update(headerSize);
        crc32.update(headerData);
        return bytes(uint32(crc32.getValue()), headerSize, headerData);
    }

    private static byte[] vint(long value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        long remaining = value;
        do {
            int b = (int) (remaining & 0x7f);
            remaining >>>= 7;
            if (remaining != 0) b |= 0x80;
            out.write(b);
        } while (remaining != 0);
        return out.toByteArray();
    }

    private static byte[] uint32(long value) {
        return new byte[] {
                (byte) (value & 0xff),
                (byte) ((value >>> 8) & 0xff),
                (byte) ((value >>> 16) & 0xff),
                (byte) ((value >>> 24) & 0xff)
        };
    }

    private static byte[] uint16(int value) {
        return new byte[] {
                (byte) (value & 0xff),
                (byte) ((value >>> 8) & 0xff)
        };
    }

    private static byte[] bytes(byte[]... parts) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) out.write(part);
        return out.toByteArray();
    }
}
