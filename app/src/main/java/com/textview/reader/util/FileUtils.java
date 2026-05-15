package com.textview.reader.util;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;

import com.textview.reader.model.TextChunk;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * File utilities including broad CJK encoding detection.
 *
 * Supported text families:
 * - Unicode: UTF-8, UTF-8 BOM, UTF-16LE/BE, UTF-16 BOM
 * - Korean legacy: MS949 / windows-949 / CP949 / EUC-KR
 * - Japanese legacy: Shift_JIS / windows-31j, EUC-JP, ISO-2022-JP
 * - Chinese legacy: GB18030, GBK, Big5
 *
 * Bad or unmappable bytes are decoded with replacement instead of crashing.
 */
public class FileUtils {
    private static final String TAG = "FileUtils";

    private static final byte[] BOM_UTF8 = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final byte[] BOM_UTF16_LE = {(byte) 0xFF, (byte) 0xFE};
    private static final byte[] BOM_UTF16_BE = {(byte) 0xFE, (byte) 0xFF};

    private static final int SAMPLE_LIMIT = 256 * 1024;

    private static final String[] CJK_CANDIDATES = new String[]{
            "UTF-8",
            "windows-949",
            "MS949",
            "EUC-KR",
            "Shift_JIS",
            "windows-31j",
            "EUC-JP",
            "ISO-2022-JP",
            "GB18030",
            "GBK",
            "Big5"
    };

    private static class DecodeResult {
        final String charsetName;
        final String text;
        final int replacementCount;
        final int badControlCount;
        final int nulCount;
        final int cjkCount;
        final int kanaCount;
        final int hangulCount;
        final int asciiCount;
        final double score;

        DecodeResult(String charsetName, String text) {
            this.charsetName = charsetName;
            this.text = text;

            int replacements = 0;
            int badControls = 0;
            int nuls = 0;
            int cjk = 0;
            int kana = 0;
            int hangul = 0;
            int ascii = 0;

            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);

                if (ch == '\uFFFD') replacements++;
                if (ch == '\u0000') nuls++;
                if (ch <= 0x7F) ascii++;

                if (isBadControl(ch)) badControls++;
                if (isCjk(ch)) cjk++;
                if (isKana(ch)) kana++;
                if (isHangul(ch)) hangul++;
            }

            this.replacementCount = replacements;
            this.badControlCount = badControls;
            this.nulCount = nuls;
            this.cjkCount = cjk;
            this.kanaCount = kana;
            this.hangulCount = hangul;
            this.asciiCount = ascii;

            // Lower is better. Replacement/control/NUL are strong evidence of wrong encoding.
            // CJK/Kana/Hangul presence is weak positive evidence for old novel text.
            this.score =
                    replacements * 1000.0
                            + badControls * 350.0
                            + nuls * 1200.0
                            - cjk * 0.8
                            - kana * 1.5
                            - hangul * 1.2
                            - Math.min(ascii, 2000) * 0.02
                            + charsetTieBreakerPenalty(charsetName);
        }
    }

    /**
     * Detect text file encoding.
     */
    public static String detectEncoding(File file) {
        try {
            byte[] data = readSample(file);
            return detectEncodingFromBytes(data);
        } catch (IOException e) {
            Log.e(TAG, "Encoding detection failed", e);
            return "UTF-8";
        }
    }

    /**
     * Detect encoding from an InputStream. The stream must support mark/reset if caller needs to re-read it.
     */
    public static String detectEncoding(InputStream inputStream) {
        try {
            BufferedInputStream bis = inputStream instanceof BufferedInputStream
                    ? (BufferedInputStream) inputStream
                    : new BufferedInputStream(inputStream);

            bis.mark(SAMPLE_LIMIT + 16);
            byte[] data = readSample(bis, SAMPLE_LIMIT);
            bis.reset();

            return detectEncodingFromBytes(data);
        } catch (IOException e) {
            Log.e(TAG, "Encoding detection from stream failed", e);
            return "UTF-8";
        }
    }

    private static String detectEncodingFromBytes(byte[] data) {
        if (data == null || data.length == 0) return "UTF-8";

        String bom = detectBom(data);
        if (bom != null) return bom;

        // ISO-2022-JP has visible escape sequences and is cheap to identify.
        if (looksLikeIso2022Jp(data)) return "ISO-2022-JP";

        // If it is strict-valid UTF-8 and contains meaningful multibyte data, prefer UTF-8.
        if (isStrictValidUtf8(data)) return "UTF-8";

        DecodeResult best = null;
        for (String candidate : CJK_CANDIDATES) {
            if (!Charset.isSupported(candidate)) continue;
            DecodeResult result = decodeCandidate(data, candidate);

            if (best == null || result.score < best.score) {
                best = result;
            }
        }

        return best != null ? best.charsetName : "UTF-8";
    }

    /**
     * Read entire text file with detected encoding and safe replacement.
     */
    public static String readTextFile(File file) throws IOException {
        byte[] data = readAllBytes(file);
        String encoding = detectEncodingFromBytes(sampleBytes(data));
        return decodeBestEffort(data, encoding);
    }

    /**
     * Read text file with specified encoding and safe replacement.
     */
    public static String readTextFile(File file, String encoding) throws IOException {
        byte[] data = readAllBytes(file);
        return decodeBestEffort(data, encoding);
    }

    /**
     * Read a text file into decoded chunks.
     * For correctness with variable-width encodings, decode once and split by Java chars.
     */
    public static List<TextChunk> readTextFileAsChunks(File file, int targetChunkChars) throws IOException {
        String text = readTextFile(file);
        int chunkSize = Math.max(2000, targetChunkChars);
        ArrayList<TextChunk> chunks = new ArrayList<>();

        if (text.isEmpty()) {
            chunks.add(new TextChunk(0, 0, ""));
            return chunks;
        }

        int chunkIndex = 0;
        for (int start = 0; start < text.length(); start += chunkSize) {
            int end = Math.min(text.length(), start + chunkSize);
            chunks.add(new TextChunk(chunkIndex++, start, text.substring(start, end)));
        }
        return chunks;
    }

    /**
     * Read text from a content URI.
     */
    public static String readTextFromUri(Context context, Uri uri) throws IOException {
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) throw new IOException("Cannot open URI: " + uri);
            byte[] data = readAllBytes(is);
            String encoding = detectEncodingFromBytes(sampleBytes(data));
            return decodeBestEffort(data, encoding);
        }
    }

    private static String decodeBestEffort(byte[] data, String encoding) {
        String bom = detectBom(data);
        int offset = bomOffset(data);

        ArrayList<String> candidates = new ArrayList<>();
        if (bom != null) candidates.add(bom);
        if (encoding != null && !encoding.trim().isEmpty()) candidates.add(encoding);

        for (String c : CJK_CANDIDATES) {
            if (!candidates.contains(c)) candidates.add(c);
        }

        DecodeResult best = null;
        byte[] body = offset > 0 ? Arrays.copyOfRange(data, offset, data.length) : data;

        for (String candidate : candidates) {
            if (!Charset.isSupported(candidate)) continue;
            DecodeResult result = decodeCandidate(body, candidate);
            if (best == null || result.score < best.score) {
                best = result;
            }
        }

        if (best == null) {
            best = decodeCandidate(body, "UTF-8");
        }

        return sanitizeDecodedText(best.text);
    }

    private static DecodeResult decodeCandidate(byte[] data, String charsetName) {
        try {
            Charset charset = Charset.forName(charsetName);
            CharsetDecoder decoder = charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE)
                    .replaceWith("\uFFFD");

            CharBuffer chars = decoder.decode(ByteBuffer.wrap(data));
            return new DecodeResult(charsetName, chars.toString());
        } catch (Exception e) {
            return new DecodeResult(charsetName, "");
        }
    }

    private static byte[] readSample(File file) throws IOException {
        try (InputStream is = new FileInputStream(file)) {
            return readSample(is, SAMPLE_LIMIT);
        }
    }

    private static byte[] readSample(InputStream is, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(limit, 8192));
        byte[] buffer = new byte[8192];
        int total = 0;
        int n;

        while (total < limit && (n = is.read(buffer, 0, Math.min(buffer.length, limit - total))) != -1) {
            out.write(buffer, 0, n);
            total += n;
        }

        return out.toByteArray();
    }

    private static byte[] readAllBytes(File file) throws IOException {
        try (InputStream is = new FileInputStream(file)) {
            return readAllBytes(is);
        }
    }

    private static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[32768];
        int n;

        while ((n = is.read(buffer)) != -1) {
            out.write(buffer, 0, n);
        }

        return out.toByteArray();
    }

    private static byte[] sampleBytes(byte[] data) {
        if (data.length <= SAMPLE_LIMIT) return data;
        return Arrays.copyOf(data, SAMPLE_LIMIT);
    }

    private static String detectBom(byte[] data) {
        if (data.length >= 3
                && data[0] == BOM_UTF8[0]
                && data[1] == BOM_UTF8[1]
                && data[2] == BOM_UTF8[2]) {
            return "UTF-8";
        }

        if (data.length >= 2
                && data[0] == BOM_UTF16_LE[0]
                && data[1] == BOM_UTF16_LE[1]) {
            return "UTF-16LE";
        }

        if (data.length >= 2
                && data[0] == BOM_UTF16_BE[0]
                && data[1] == BOM_UTF16_BE[1]) {
            return "UTF-16BE";
        }

        return null;
    }

    private static int bomOffset(byte[] data) {
        if (data.length >= 3
                && data[0] == BOM_UTF8[0]
                && data[1] == BOM_UTF8[1]
                && data[2] == BOM_UTF8[2]) {
            return 3;
        }

        if (data.length >= 2
                && ((data[0] == BOM_UTF16_LE[0] && data[1] == BOM_UTF16_LE[1])
                || (data[0] == BOM_UTF16_BE[0] && data[1] == BOM_UTF16_BE[1]))) {
            return 2;
        }

        return 0;
    }

    private static boolean isStrictValidUtf8(byte[] data) {
        int i = 0;
        boolean hasMultibyte = false;

        while (i < data.length) {
            int b = data[i] & 0xFF;
            int seqLen;

            if (b <= 0x7F) {
                seqLen = 1;
            } else if (b >= 0xC2 && b <= 0xDF) {
                seqLen = 2;
                hasMultibyte = true;
            } else if (b >= 0xE0 && b <= 0xEF) {
                seqLen = 3;
                hasMultibyte = true;
            } else if (b >= 0xF0 && b <= 0xF4) {
                seqLen = 4;
                hasMultibyte = true;
            } else {
                return false;
            }

            if (i + seqLen > data.length) return false;

            for (int j = 1; j < seqLen; j++) {
                if ((data[i + j] & 0xC0) != 0x80) return false;
            }

            i += seqLen;
        }

        // ASCII-only files are also UTF-8.
        return true;
    }

    private static boolean looksLikeIso2022Jp(byte[] data) {
        for (int i = 0; i < data.length - 2; i++) {
            if ((data[i] & 0xFF) == 0x1B) {
                int b1 = data[i + 1] & 0xFF;
                int b2 = data[i + 2] & 0xFF;

                if (b1 == 0x24 && (b2 == 0x40 || b2 == 0x42)) return true; // ESC $ @ / ESC $ B
                if (b1 == 0x28 && (b2 == 0x42 || b2 == 0x4A || b2 == 0x49)) return true; // ESC ( B/J/I
            }
        }
        return false;
    }

    private static boolean isBadControl(char ch) {
        if (ch == '\n' || ch == '\r' || ch == '\t') return false;
        return ch < 0x20 || (ch >= 0x7F && ch <= 0x9F);
    }

    private static boolean isCjk(char ch) {
        return (ch >= 0x3400 && ch <= 0x4DBF)    // CJK Ext A
                || (ch >= 0x4E00 && ch <= 0x9FFF) // CJK Unified
                || (ch >= 0xF900 && ch <= 0xFAFF); // Compatibility Ideographs
    }

    private static boolean isKana(char ch) {
        return (ch >= 0x3040 && ch <= 0x309F) // Hiragana
                || (ch >= 0x30A0 && ch <= 0x30FF) // Katakana
                || (ch >= 0x31F0 && ch <= 0x31FF); // Katakana Phonetic Extensions
    }

    private static boolean isHangul(char ch) {
        return (ch >= 0xAC00 && ch <= 0xD7AF)
                || (ch >= 0x1100 && ch <= 0x11FF)
                || (ch >= 0x3130 && ch <= 0x318F);
    }

    private static double charsetTieBreakerPenalty(String charsetName) {
        if (charsetName == null) return 50.0;
        String n = charsetName.toUpperCase();

        // Prefer common encodings if scores are otherwise close.
        if (n.equals("UTF-8")) return 0.0;
        if (n.contains("949") || n.equals("MS949") || n.equals("EUC-KR")) return 5.0;
        if (n.contains("SHIFT") || n.contains("31J")) return 7.0;
        if (n.contains("EUC-JP")) return 9.0;
        if (n.contains("GB")) return 12.0;
        if (n.contains("BIG5")) return 14.0;

        return 20.0;
    }

    private static String sanitizeDecodedText(String text) {
        if (text == null || text.isEmpty()) return "";

        StringBuilder out = new StringBuilder(text.length());
        boolean first = true;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);

            if (first && ch == '\uFEFF') {
                first = false;
                continue;
            }
            first = false;

            if (ch == '\u0000') {
                out.append('\uFFFD');
                continue;
            }

            if (Character.isHighSurrogate(ch)) {
                if (i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
                    out.append(ch);
                    out.append(text.charAt(i + 1));
                    i++;
                } else {
                    out.append('\uFFFD');
                }
                continue;
            }

            if (Character.isLowSurrogate(ch)) {
                out.append('\uFFFD');
                continue;
            }

            if (isBadControl(ch)) {
                out.append(' ');
                continue;
            }

            // U+2728 SPARKLES is emoji-default on many Samsung/Android builds.
            // Even with U+FE0E, some devices still route it through the color emoji font.
            // Use the closest monochrome text-star fallback instead.
            if (ch == '\u2728') {
                out.append('\u2727'); // ✧
                if (i + 1 < text.length()) {
                    char next = text.charAt(i + 1);
                    if (next == '\uFE0E' || next == '\uFE0F') {
                        i++;
                    }
                }
                continue;
            }

            if (needsTextPresentationSelector(ch)) {
                out.append(ch);
                if (i + 1 < text.length()) {
                    char next = text.charAt(i + 1);
                    if (next == '\uFE0E') {
                        out.append(next);
                        i++;
                    } else if (next == '\uFE0F') {
                        out.append('\uFE0E');
                        i++;
                    } else {
                        out.append('\uFE0E');
                    }
                } else {
                    out.append('\uFE0E');
                }
                continue;
            }

            out.append(ch);
        }

        return out.toString()
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }

    public static String enforceTextPresentationSelectors(String text) {
        return sanitizeDecodedText(text);
    }

    private static boolean needsTextPresentationSelector(char ch) {
        switch (ch) {
            case '\u2605': // ★
            case '\u2606': // ☆
            case '\u2665': // ♥
            case '\u2661': // ♡
            case '\u2660': // ♠
            case '\u2663': // ♣
            case '\u2666': // ♦
            case '\u263A': // ☺
            case '\u2639': // ☹
            case '\u2764': // ❤
            case '\u2714': // ✔
            case '\u2716': // ✖
                return true;
            default:
                return false;
        }
    }

    /**
     * Get filename from URI.
     */
    public static String getFileNameFromUri(Context context, Uri uri) {
        String result = null;

        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) result = cursor.getString(idx);
                }
            }
        }

        if (result == null) result = uri.getLastPathSegment();
        return result;
    }

    /**
     * Copy content URI to a local file and return the file.
     */
    public static File copyUriToLocal(Context context, Uri uri, String fileName) throws IOException {
        File cacheDir = new File(context.getCacheDir(), "opened_files");
        if (!cacheDir.exists()) cacheDir.mkdirs();

        File localFile = new File(cacheDir, fileName);
        try (InputStream is = context.getContentResolver().openInputStream(uri);
             FileOutputStream fos = new FileOutputStream(localFile)) {
            if (is == null) throw new IOException("Cannot open URI");

            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = is.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
        }

        return localFile;
    }


    /**
     * Read any app-supported readable file and normalize it into plain text.
     * This keeps the existing reader, paging, search, recent-file, and bookmark logic
     * shared across TXT, PDF, EPUB, and Word documents.
     */
    public static String readReadableFile(Context context, File file) throws IOException {
        String lower = lowerName(file != null ? file.getName() : null);

        if (lower.endsWith(".pdf")) {
            throw new IOException("PDF files use the original-page PDF viewer, not text extraction.");
        }
        if (lower.endsWith(".epub")) {
            return readEpubFile(file);
        }
        if (isWordFileName(lower)) {
            return readWordFile(file);
        }
        return readTextFile(file);
    }

    /**
     * Human-readable format label used in file info and list subtitles.
     */
    public static String getReadableFileType(String fileName) {
        String lower = lowerName(fileName);
        if (lower.endsWith(".pdf")) return "PDF";
        if (lower.endsWith(".epub")) return "EPUB";
        if (isWordFileName(lower)) return "Word";
        if (isTextFile(fileName)) return "Text";
        return "File";
    }

    public static boolean isSupportedReadableFile(String fileName) {
        return isTextFile(fileName)
                || isPdfFile(fileName)
                || isEpubFile(fileName)
                || isWordFile(fileName);
    }

    public static boolean isPdfFile(String fileName) {
        return lowerName(fileName).endsWith(".pdf");
    }

    public static boolean isEpubFile(String fileName) {
        return lowerName(fileName).endsWith(".epub");
    }

    public static boolean isWordFile(String fileName) {
        return isWordFileName(lowerName(fileName));
    }

    private static boolean isWordFileName(String lowerName) {
        return lowerName.endsWith(".docx")
                || lowerName.endsWith(".docm")
                || lowerName.endsWith(".dotx")
                || lowerName.endsWith(".dotm");
    }

    private static String readWordFile(File file) throws IOException {
        try (ZipFile zip = new ZipFile(file)) {
            ZipEntry documentXml = zip.getEntry("word/document.xml");
            if (documentXml == null) {
                throw new IOException("Unsupported Word file. Only OOXML Word files (.docx/.docm/.dotx/.dotm) are supported.");
            }

            Document doc;
            try (InputStream is = zip.getInputStream(documentXml)) {
                doc = secureDocumentBuilder().parse(is);
            } catch (Exception e) {
                throw new IOException("Cannot parse Word document text", e);
            }

            StringBuilder out = new StringBuilder();
            NodeList paragraphs = doc.getElementsByTagName("w:p");
            if (paragraphs.getLength() == 0) {
                paragraphs = doc.getElementsByTagNameNS("*", "p");
            }

            for (int i = 0; i < paragraphs.getLength(); i++) {
                StringBuilder paragraph = new StringBuilder();
                appendWordNodeText(paragraphs.item(i), paragraph);
                String line = paragraph.toString().trim();
                if (!line.isEmpty()) out.append(line);
                out.append('\n');
            }

            return sanitizeExtractedText(out.toString());
        }
    }

    private static void appendWordNodeText(Node node, StringBuilder out) {
        if (node == null) return;

        String name = node.getNodeName();
        String local = node.getLocalName();
        if ("w:t".equals(name) || "t".equals(local)) {
            String text = node.getTextContent();
            if (text != null) out.append(text);
            return;
        }
        if ("w:tab".equals(name) || "tab".equals(local)) {
            out.append('\t');
            return;
        }
        if ("w:br".equals(name) || "w:cr".equals(name)
                || "br".equals(local) || "cr".equals(local)) {
            out.append('\n');
            return;
        }

        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            appendWordNodeText(children.item(i), out);
        }
    }

    private static String readEpubFile(File file) throws IOException {
        try (ZipFile zip = new ZipFile(file)) {
            List<String> chapterPaths = findEpubSpinePaths(zip);
            if (chapterPaths.isEmpty()) {
                chapterPaths = findEpubHtmlEntries(zip);
            }

            if (chapterPaths.isEmpty()) {
                throw new IOException("No readable EPUB chapters found");
            }

            StringBuilder out = new StringBuilder();
            for (String path : chapterPaths) {
                ZipEntry entry = zip.getEntry(path);
                if (entry == null || entry.isDirectory()) continue;
                String html;
                try (InputStream is = zip.getInputStream(entry)) {
                    byte[] data = readAllBytes(is);
                    html = decodeBestEffort(data, detectEncodingFromBytes(sampleBytes(data)));
                }
                String text = htmlToPlainText(html);
                if (!text.trim().isEmpty()) {
                    out.append(text.trim()).append("\n\n");
                }
            }
            return sanitizeExtractedText(out.toString());
        }
    }

    private static List<String> findEpubSpinePaths(ZipFile zip) {
        ArrayList<String> result = new ArrayList<>();
        try {
            ZipEntry containerEntry = zip.getEntry("META-INF/container.xml");
            if (containerEntry == null) return result;

            Document containerDoc;
            try (InputStream is = zip.getInputStream(containerEntry)) {
                containerDoc = secureDocumentBuilder().parse(is);
            }

            NodeList rootFiles = containerDoc.getElementsByTagName("rootfile");
            if (rootFiles.getLength() == 0) {
                rootFiles = containerDoc.getElementsByTagNameNS("*", "rootfile");
            }
            if (rootFiles.getLength() == 0) return result;

            Node rootFile = rootFiles.item(0);
            NamedNodeMap rootAttrs = rootFile.getAttributes();
            Node fullPathAttr = rootAttrs != null ? rootAttrs.getNamedItem("full-path") : null;
            if (fullPathAttr == null) return result;

            String opfPath = fullPathAttr.getNodeValue();
            ZipEntry opfEntry = zip.getEntry(opfPath);
            if (opfEntry == null) return result;

            Document opfDoc;
            try (InputStream is = zip.getInputStream(opfEntry)) {
                opfDoc = secureDocumentBuilder().parse(is);
            }

            String basePath = "";
            int slash = opfPath.lastIndexOf('/');
            if (slash >= 0) basePath = opfPath.substring(0, slash + 1);

            java.util.Map<String, String> manifest = new java.util.LinkedHashMap<>();
            NodeList items = opfDoc.getElementsByTagName("item");
            if (items.getLength() == 0) {
                items = opfDoc.getElementsByTagNameNS("*", "item");
            }
            for (int i = 0; i < items.getLength(); i++) {
                Node item = items.item(i);
                NamedNodeMap itemAttrs = item.getAttributes();
                if (itemAttrs == null) continue;
                Node id = itemAttrs.getNamedItem("id");
                Node href = itemAttrs.getNamedItem("href");
                if (id != null && href != null) {
                    manifest.put(id.getNodeValue(), normalizeZipPath(basePath + decodeZipHref(href.getNodeValue())));
                }
            }

            NodeList itemRefs = opfDoc.getElementsByTagName("itemref");
            if (itemRefs.getLength() == 0) {
                itemRefs = opfDoc.getElementsByTagNameNS("*", "itemref");
            }
            for (int i = 0; i < itemRefs.getLength(); i++) {
                Node itemRef = itemRefs.item(i);
                NamedNodeMap itemRefAttrs = itemRef.getAttributes();
                if (itemRefAttrs == null) continue;
                Node idRef = itemRefAttrs.getNamedItem("idref");
                if (idRef == null) continue;
                String path = manifest.get(idRef.getNodeValue());
                if (path != null && isEpubHtmlPath(path) && zip.getEntry(path) != null) {
                    result.add(path);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "EPUB spine parse failed; falling back to entry order", e);
        }
        return result;
    }

    private static List<String> findEpubHtmlEntries(ZipFile zip) {
        ArrayList<String> result = new ArrayList<>();
        java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (!entry.isDirectory() && isEpubHtmlPath(entry.getName())) {
                result.add(entry.getName());
            }
        }
        java.util.Collections.sort(result);
        return result;
    }

    private static boolean isEpubHtmlPath(String path) {
        String lower = lowerName(path);
        return lower.endsWith(".xhtml") || lower.endsWith(".html") || lower.endsWith(".htm");
    }

    private static String htmlToPlainText(String html) {
        if (html == null || html.isEmpty()) return "";

        String cleaned = html
                .replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p\\s*>", "\n")
                .replaceAll("(?i)</div\\s*>", "\n")
                .replaceAll("(?i)</h[1-6]\\s*>", "\n")
                .replaceAll("(?i)</li\\s*>", "\n");

        Spanned spanned = Html.fromHtml(cleaned, Html.FROM_HTML_MODE_LEGACY);
        return spanned.toString();
    }

    private static DocumentBuilder secureDocumentBuilder() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setExpandEntityReferences(false);
        setXmlFeatureQuietly(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
        setXmlFeatureQuietly(factory, "http://xml.org/sax/features/external-general-entities", false);
        setXmlFeatureQuietly(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        setXmlFeatureQuietly(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        return factory.newDocumentBuilder();
    }

    private static void setXmlFeatureQuietly(DocumentBuilderFactory factory, String feature, boolean enabled) {
        try {
            factory.setFeature(feature, enabled);
        } catch (Exception ignored) {
            // Some Android XML parser implementations do not expose every hardening flag.
        }
    }


    private static String decodeZipHref(String href) {
        if (href == null) return "";
        try {
            return URLDecoder.decode(href, "UTF-8");
        } catch (Exception ignored) {
            return href;
        }
    }

    private static String normalizeZipPath(String path) {
        if (path == null) return "";
        String normalized = path.replace('\\', '/');
        while (normalized.contains("//")) normalized = normalized.replace("//", "/");
        ArrayList<String> parts = new ArrayList<>();
        for (String part : normalized.split("/")) {
            if (part.isEmpty() || ".".equals(part)) continue;
            if ("..".equals(part)) {
                if (!parts.isEmpty()) parts.remove(parts.size() - 1);
            } else {
                parts.add(part);
            }
        }
        return String.join("/", parts);
    }

    private static String sanitizeExtractedText(String text) {
        String normalized = sanitizeDecodedText(text != null ? text : "");
        normalized = normalized
                .replace('\u00A0', ' ')
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n[ \\t]+", "\n")
                .replaceAll("\\n{4,}", "\n\n\n")
                .trim();
        return normalized.isEmpty() ? " " : normalized;
    }

    private static String lowerName(String fileName) {
        return fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
    }

    /**
     * Format file size for display.
     */
    public static String formatFileSize(long size) {
        if (size <= 0) return "0 B";

        final String[] units = {"B", "KB", "MB", "GB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        digitGroups = Math.min(digitGroups, units.length - 1);

        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups))
                + " " + units[digitGroups];
    }

    /**
     * Check if file extension is a supported plain-text file.
     */
    public static boolean isTextFile(String fileName) {
        String lower = lowerName(fileName);
        return lower.endsWith(".txt") || lower.endsWith(".text")
                || lower.endsWith(".log") || lower.endsWith(".md")
                || lower.endsWith(".csv") || lower.endsWith(".ini")
                || lower.endsWith(".cfg") || lower.endsWith(".conf");
    }
}
