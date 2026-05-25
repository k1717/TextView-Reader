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
 * File utilities including broad text encoding detection.
 *
 * Supported text families:
 * - Unicode: UTF-8, UTF-8 BOM, UTF-16LE/BE, UTF-16 BOM, BOM-less UTF-16LE/BE heuristic
 * - Android ICU-assisted detection where available for broad legacy text encodings
 * - Western/Central European/Turkish/Baltic single-byte encodings: Windows-1252/1250/1254/1257 and ISO-8859 variants
 * - Greek/Cyrillic/Hebrew/Arabic/Thai legacy encodings: Windows-1253/1251/1255/1256/874 and ISO/KOI8 variants
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

    private static final String[] TEXT_ENCODING_CANDIDATES = new String[]{
            "UTF-8",

            // Broad alphabetic single-byte encodings. These cover most Latin, Greek,
            // Cyrillic, Turkish, Baltic, Hebrew, Arabic, and Thai TXT files that are
            // not Unicode but should not be forced through CJK-only detection.
            "windows-1252", // Western European smart quotes / punctuation
            "ISO-8859-1",
            "ISO-8859-15",
            "windows-1250", // Central/Eastern European Latin
            "ISO-8859-2",
            "windows-1251", // Cyrillic
            "ISO-8859-5",
            "KOI8-R",
            "KOI8-U",
            "windows-1253", // Greek
            "ISO-8859-7",
            "windows-1254", // Turkish
            "ISO-8859-9",
            "windows-1257", // Baltic
            "ISO-8859-13",
            "windows-1255", // Hebrew
            "ISO-8859-8",
            "windows-1256", // Arabic
            "ISO-8859-6",
            "windows-874", // Thai
            "ISO-8859-11",
            "x-MacRoman",

            // CJK legacy encodings.
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
        final int alphabeticScriptCount;
        final int usefulPunctuationCount;
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
            int alphabeticScripts = 0;
            int latinExtended = 0;
            int greek = 0;
            int cyrillic = 0;
            int hebrew = 0;
            int arabic = 0;
            int thai = 0;
            int usefulPunctuation = 0;
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
                if (isBroadAlphabeticScript(ch)) alphabeticScripts++;
                if (isLatinExtended(ch)) latinExtended++;
                if (isGreek(ch)) greek++;
                if (isCyrillic(ch)) cyrillic++;
                if (isHebrew(ch)) hebrew++;
                if (isArabic(ch)) arabic++;
                if (isThai(ch)) thai++;
                if (isUsefulTextPunctuation(ch)) usefulPunctuation++;
            }

            this.replacementCount = replacements;
            this.badControlCount = badControls;
            this.nulCount = nuls;
            this.cjkCount = cjk;
            this.kanaCount = kana;
            this.hangulCount = hangul;
            this.alphabeticScriptCount = alphabeticScripts;
            this.usefulPunctuationCount = usefulPunctuation;
            this.asciiCount = ascii;

            // Lower is better. Replacement/control/NUL are strong evidence of wrong encoding.
            // Script-specific letters and common book punctuation are positive evidence.
            // This makes detection less CJK-only and helps Latin/Greek/Cyrillic/etc. legacy TXT.
            this.score =
                    replacements * 1000.0
                            + badControls * 350.0
                            + nuls * 1200.0
                            - cjk * 0.8
                            - kana * 1.5
                            - hangul * 1.2
                            - alphabeticScripts * 0.45
                            - usefulPunctuation * 0.35
                            - Math.min(ascii, 2000) * 0.02
                            - charsetScriptMatchBonus(charsetName, latinExtended, greek, cyrillic, hebrew, arabic, thai)
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

        // BOM-less UTF-16 text can look like valid UTF-8 when it is mostly ASCII
        // because NUL bytes are legal control characters in a byte stream. Detect
        // strong UTF-16 byte patterns before accepting strict UTF-8.
        String bomlessUtf16 = detectBomlessUtf16(data);
        if (bomlessUtf16 != null) return bomlessUtf16;

        // If it is strict-valid UTF-8, prefer UTF-8.
        if (isStrictValidUtf8(data)) return "UTF-8";

        // Android ICU covers many alphabetic legacy encodings better than a small
        // hand-written candidate score can. Use it when confidence is meaningful,
        // then fall back to the built-in broad candidate scorer below.
        String icuDetected = detectWithAndroidIcu(data);
        if (icuDetected != null) return icuDetected;

        DecodeResult best = null;
        for (String candidate : TEXT_ENCODING_CANDIDATES) {
            if (!Charset.isSupported(candidate)) continue;
            DecodeResult result = decodeCandidate(data, candidate);

            if (best == null || result.score < best.score) {
                best = result;
            }
        }

        return best != null ? best.charsetName : "UTF-8";
    }

    private static String detectWithAndroidIcu(byte[] data) {
        if (data == null || data.length == 0) return null;
        try {
            Class<?> detectorClass = Class.forName("android.icu.text.CharsetDetector");
            Object detector = detectorClass.getConstructor().newInstance();
            detectorClass.getMethod("setText", byte[].class).invoke(detector, (Object) data);
            Object match = detectorClass.getMethod("detect").invoke(detector);
            if (match == null) return null;

            Class<?> matchClass = match.getClass();
            Object nameObj = matchClass.getMethod("getName").invoke(match);
            Object confidenceObj = matchClass.getMethod("getConfidence").invoke(match);
            if (!(nameObj instanceof String) || !(confidenceObj instanceof Integer)) return null;

            String name = normalizeDetectedCharset((String) nameObj);
            int confidence = (Integer) confidenceObj;
            if (name == null || confidence < 40 || !Charset.isSupported(name)) return null;

            DecodeResult result = decodeCandidate(data, name);
            int length = Math.max(1, result.text.length());
            if (result.replacementCount > Math.max(2, length / 100)) return null;
            if (result.nulCount > Math.max(1, length / 200)) return null;
            if (result.badControlCount > Math.max(2, length / 80)) return null;
            return name;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String normalizeDetectedCharset(String name) {
        if (name == null) return null;
        String trimmed = name.trim();
        if (trimmed.isEmpty()) return null;
        String upper = trimmed.toUpperCase(Locale.ROOT).replace('_', '-');
        if (upper.equals("ISO-8859-8-I")) return "ISO-8859-8";
        if (upper.equals("MS949") || upper.equals("CP949")) return "windows-949";
        if (upper.equals("WINDOWS-31J") || upper.equals("CP932")) return "windows-31j";
        if (upper.equals("CP1250")) return "windows-1250";
        if (upper.equals("CP1251")) return "windows-1251";
        if (upper.equals("CP1252")) return "windows-1252";
        if (upper.equals("CP1253")) return "windows-1253";
        if (upper.equals("CP1254")) return "windows-1254";
        if (upper.equals("CP1255")) return "windows-1255";
        if (upper.equals("CP1256")) return "windows-1256";
        if (upper.equals("CP1257")) return "windows-1257";
        if (upper.equals("CP874")) return "windows-874";
        return trimmed;
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
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + chunkSize);
            end = clampToSurrogateSafeEnd(text, end);
            if (end <= start) {
                end = Math.min(text.length(), start + chunkSize);
            }
            chunks.add(new TextChunk(chunkIndex++, start, text.substring(start, end)));
            start = end;
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

        for (String c : TEXT_ENCODING_CANDIDATES) {
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


    private static String detectBomlessUtf16(byte[] data) {
        if (data == null || data.length < 8) return null;

        int limit = Math.min(data.length, SAMPLE_LIMIT);
        int pairs = limit / 2;
        if (pairs < 4) return null;

        int zeroEven = 0;
        int zeroOdd = 0;
        int leAsciiPairs = 0;
        int beAsciiPairs = 0;
        int leNewlinePairs = 0;
        int beNewlinePairs = 0;

        for (int i = 0; i + 1 < limit; i += 2) {
            int b0 = data[i] & 0xFF;
            int b1 = data[i + 1] & 0xFF;

            if (b0 == 0) zeroEven++;
            if (b1 == 0) zeroOdd++;

            if (b1 == 0 && isLikelySingleByteText(b0)) leAsciiPairs++;
            if (b0 == 0 && isLikelySingleByteText(b1)) beAsciiPairs++;

            if (b1 == 0 && (b0 == '\n' || b0 == '\r')) leNewlinePairs++;
            if (b0 == 0 && (b1 == '\n' || b1 == '\r')) beNewlinePairs++;
        }

        double zeroEvenRatio = zeroEven / (double) pairs;
        double zeroOddRatio = zeroOdd / (double) pairs;
        double leAsciiRatio = leAsciiPairs / (double) pairs;
        double beAsciiRatio = beAsciiPairs / (double) pairs;

        // Strong ASCII/newline evidence. This covers the common no-BOM UTF-16
        // files that previously looked like strict-valid UTF-8 with embedded NULs.
        if ((zeroOddRatio >= 0.30 && leAsciiRatio >= 0.25 && leAsciiPairs >= beAsciiPairs * 2)
                || (leNewlinePairs >= 2 && leNewlinePairs > beNewlinePairs)) {
            return "UTF-16LE";
        }
        if ((zeroEvenRatio >= 0.30 && beAsciiRatio >= 0.25 && beAsciiPairs >= leAsciiPairs * 2)
                || (beNewlinePairs >= 2 && beNewlinePairs > leNewlinePairs)) {
            return "UTF-16BE";
        }

        // CJK UTF-16 may not have much ASCII, but one byte lane often still shows
        // a meaningful amount of zeroes. Decode both endiannesses and choose the
        // more plausible text result only when there is enough UTF-16-like evidence.
        if (Math.max(zeroEvenRatio, zeroOddRatio) < 0.18) {
            return null;
        }

        DecodeResult le = decodeCandidate(data, "UTF-16LE");
        DecodeResult be = decodeCandidate(data, "UTF-16BE");
        boolean lePlausible = isPlausibleUtf16Text(le);
        boolean bePlausible = isPlausibleUtf16Text(be);

        if (lePlausible && !bePlausible) return "UTF-16LE";
        if (bePlausible && !lePlausible) return "UTF-16BE";
        if (!lePlausible && !bePlausible) return null;

        int leSignal = le.asciiCount + le.cjkCount + le.kanaCount + le.hangulCount + le.alphabeticScriptCount;
        int beSignal = be.asciiCount + be.cjkCount + be.kanaCount + be.hangulCount + be.alphabeticScriptCount;
        if (leSignal >= beSignal + Math.max(4, pairs / 20)) return "UTF-16LE";
        if (beSignal >= leSignal + Math.max(4, pairs / 20)) return "UTF-16BE";

        if (zeroOddRatio >= zeroEvenRatio * 1.35) return "UTF-16LE";
        if (zeroEvenRatio >= zeroOddRatio * 1.35) return "UTF-16BE";

        return le.score <= be.score ? "UTF-16LE" : "UTF-16BE";
    }

    private static boolean isPlausibleUtf16Text(DecodeResult result) {
        if (result == null || result.text == null || result.text.isEmpty()) return false;
        int length = Math.max(1, result.text.length());
        if (result.replacementCount > Math.max(2, length / 100)) return false;
        if (result.nulCount > Math.max(1, length / 200)) return false;
        if (result.badControlCount > Math.max(2, length / 80)) return false;
        int textSignal = result.asciiCount + result.cjkCount + result.kanaCount + result.hangulCount + result.alphabeticScriptCount;
        return textSignal >= Math.max(2, length / 8);
    }

    private static boolean isLikelySingleByteText(int b) {
        return b == '\t' || b == '\n' || b == '\r' || (b >= 0x20 && b <= 0x7E);
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

    private static boolean isBroadAlphabeticScript(char ch) {
        if (ch <= 0x7F) return false;
        if (isCjk(ch) || isKana(ch) || isHangul(ch)) return false;
        return Character.isLetterOrDigit(ch);
    }

    private static boolean isLatinExtended(char ch) {
        return (ch >= 0x00C0 && ch <= 0x024F)
                || (ch >= 0x1E00 && ch <= 0x1EFF);
    }

    private static boolean isGreek(char ch) {
        return (ch >= 0x0370 && ch <= 0x03FF)
                || (ch >= 0x1F00 && ch <= 0x1FFF);
    }

    private static boolean isCyrillic(char ch) {
        return (ch >= 0x0400 && ch <= 0x052F)
                || (ch >= 0x2DE0 && ch <= 0x2DFF)
                || (ch >= 0xA640 && ch <= 0xA69F);
    }

    private static boolean isHebrew(char ch) {
        return ch >= 0x0590 && ch <= 0x05FF;
    }

    private static boolean isArabic(char ch) {
        return (ch >= 0x0600 && ch <= 0x06FF)
                || (ch >= 0x0750 && ch <= 0x077F)
                || (ch >= 0x08A0 && ch <= 0x08FF)
                || (ch >= 0xFB50 && ch <= 0xFDFF)
                || (ch >= 0xFE70 && ch <= 0xFEFF);
    }

    private static boolean isThai(char ch) {
        return ch >= 0x0E00 && ch <= 0x0E7F;
    }

    private static boolean isUsefulTextPunctuation(char ch) {
        switch (ch) {
            case '‘': // ‘
            case '’': // ’
            case '‚': // ‚
            case '“': // “
            case '”': // ”
            case '„': // „
            case '‹': // ‹
            case '›': // ›
            case '–': // –
            case '—': // —
            case '…': // …
            case '•': // •
            case ' ': // non-breaking space
            case '«': // «
            case '»': // »
            case '¡': // ¡
            case '¿': // ¿
            case '£': // £
            case '€': // €
                return true;
            default:
                return false;
        }
    }

    private static double charsetScriptMatchBonus(String charsetName,
                                                    int latinExtended,
                                                    int greek,
                                                    int cyrillic,
                                                    int hebrew,
                                                    int arabic,
                                                    int thai) {
        if (charsetName == null) return 0.0;
        String n = charsetName.toUpperCase(Locale.ROOT);

        // Non-Latin legacy encodings are highly ambiguous at the byte level, so
        // only grant a strong script bonus when enough characters decoded into
        // the script. This avoids stealing short Western European accent text.
        if ((n.equals("WINDOWS-1251") || n.equals("ISO-8859-5") || n.startsWith("KOI8")) && cyrillic >= 6) {
            return cyrillic * 4.0;
        }
        if ((n.equals("WINDOWS-1253") || n.equals("ISO-8859-7")) && greek >= 6) {
            return greek * 4.0;
        }
        if ((n.equals("WINDOWS-1255") || n.equals("ISO-8859-8")) && hebrew >= 5) {
            return hebrew * 4.0;
        }
        if ((n.equals("WINDOWS-1256") || n.equals("ISO-8859-6")) && arabic >= 5) {
            return arabic * 4.0;
        }
        if ((n.equals("WINDOWS-874") || n.equals("ISO-8859-11")) && thai >= 5) {
            return thai * 4.0;
        }
        if ((n.equals("WINDOWS-1252") || n.equals("ISO-8859-1") || n.equals("ISO-8859-15")
                || n.equals("WINDOWS-1250") || n.equals("ISO-8859-2")
                || n.equals("WINDOWS-1254") || n.equals("ISO-8859-9")
                || n.equals("WINDOWS-1257") || n.equals("ISO-8859-13")
                || n.contains("MACROMAN")) && latinExtended > 0) {
            return latinExtended * 1.4;
        }
        return 0.0;
    }

    private static double charsetTieBreakerPenalty(String charsetName) {
        if (charsetName == null) return 50.0;
        String n = charsetName.toUpperCase(Locale.ROOT);

        // Prefer common encodings if scores are otherwise close. Windows code pages
        // are slightly preferred over ISO siblings because they preserve smart quotes
        // and other book punctuation in many old TXT files.
        if (n.equals("UTF-8")) return 0.0;
        if (n.equals("WINDOWS-1252")) return 3.0;
        if (n.equals("ISO-8859-1") || n.equals("ISO-8859-15")) return 7.0;
        if (n.equals("WINDOWS-1250") || n.equals("WINDOWS-1251") || n.equals("WINDOWS-1253")
                || n.equals("WINDOWS-1254") || n.equals("WINDOWS-1255") || n.equals("WINDOWS-1256")
                || n.equals("WINDOWS-1257") || n.equals("WINDOWS-874")) return 8.0;
        if (n.startsWith("ISO-8859-") || n.startsWith("KOI8")) return 11.0;
        if (n.contains("949") || n.equals("MS949") || n.equals("EUC-KR")) return 12.0;
        if (n.contains("SHIFT") || n.contains("31J")) return 14.0;
        if (n.contains("EUC-JP")) return 16.0;
        if (n.contains("GB")) return 18.0;
        if (n.contains("BIG5")) return 20.0;
        if (n.contains("MACROMAN")) return 22.0;

        return 25.0;
    }


    public static int clampToSurrogateSafeStart(String text, int index) {
        if (text == null || text.isEmpty()) return 0;
        int safe = Math.max(0, Math.min(text.length(), index));
        if (safe > 0 && safe < text.length()
                && Character.isLowSurrogate(text.charAt(safe))
                && Character.isHighSurrogate(text.charAt(safe - 1))) {
            return safe - 1;
        }
        return safe;
    }

    public static int clampToSurrogateSafeEnd(String text, int index) {
        if (text == null || text.isEmpty()) return 0;
        int safe = Math.max(0, Math.min(text.length(), index));
        if (safe > 0 && safe < text.length()
                && Character.isLowSurrogate(text.charAt(safe))
                && Character.isHighSurrogate(text.charAt(safe - 1))) {
            return safe + 1;
        }
        return safe;
    }

    public static String safeSubstring(String text, int start, int end) {
        if (text == null || text.isEmpty()) return "";
        int safeStart = clampToSurrogateSafeStart(text, start);
        int safeEnd = clampToSurrogateSafeEnd(text, end);
        safeStart = Math.max(0, Math.min(text.length(), safeStart));
        safeEnd = Math.max(0, Math.min(text.length(), safeEnd));
        if (safeEnd < safeStart) {
            int tmp = safeStart;
            safeStart = safeEnd;
            safeEnd = tmp;
        }
        return text.substring(safeStart, safeEnd);
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
