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
 * - Unicode: UTF-8, UTF-8 BOM, UTF-16LE/BE, UTF-16 BOM, UTF-32LE/BE BOM, BOM-less UTF-16LE/BE heuristic
 * - Android ICU-assisted detection and Mozilla/JUniversalChardet-assisted detection where available
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
    private static final byte[] BOM_UTF32_LE = {(byte) 0xFF, (byte) 0xFE, 0x00, 0x00};
    private static final byte[] BOM_UTF32_BE = {0x00, 0x00, (byte) 0xFE, (byte) 0xFF};

    // Fast path first: most TXT files can be classified from a modest sample.
    // If the primary sample is ambiguous, detection escalates to the extended
    // sample.  This keeps normal file opening responsive while preserving the
    // 256 KiB accuracy path for genuinely uncertain legacy encodings.
    private static final int PRIMARY_SAMPLE_LIMIT = 192 * 1024;
    private static final int EXTENDED_SAMPLE_LIMIT = 256 * 1024;
    private static final int SAMPLE_LIMIT = PRIMARY_SAMPLE_LIMIT;

    // Encoding scorer weights.  These names intentionally preserve the existing
    // numeric behavior while making future detector tuning reviewable in diffs.
    private static final double DETECTOR_ICU_EXACT_BONUS = 45.0;
    private static final double DETECTOR_MOZILLA_EXACT_BONUS = 70.0;
    private static final double DETECTOR_ICU_FAMILY_BONUS = 18.0;
    private static final double DETECTOR_MOZILLA_FAMILY_BONUS = 32.0;
    private static final double DETECTOR_AGREEING_FAMILY_BONUS = 45.0;
    private static final double SCORE_REPLACEMENT_PENALTY = 900.0;
    private static final double SCORE_BAD_CONTROL_PENALTY = 500.0;
    private static final double SCORE_NUL_PENALTY = 1500.0;
    private static final double KOREAN_HANGUL_BONUS = 6.7;
    private static final double KOREAN_CJK_BONUS = 1.45;
    private static final double KOREAN_SUSTAINED_SIGNAL_BONUS = 550.0;
    private static final double KOREAN_STRONG_SIGNAL_BONUS = 3200.0;
    private static final double KOREAN_WEAK_SIGNAL_PENALTY = 900.0;
    private static final double KOREAN_SUPERSET_BONUS = 35.0;
    private static final double KOREAN_EUC_KR_TIE_PENALTY = 10.0;
    private static final double CYRILLIC_CHAR_BONUS = 1.8;
    private static final double CYRILLIC_NATURALNESS_BONUS = 4.2;
    private static final double CYRILLIC_ODD_CHAR_PENALTY = 45.0;
    private static final double CYRILLIC_ODD_PATTERN_BASE_PENALTY = 1800.0;
    private static final double CYRILLIC_ODD_PATTERN_EXTRA_PENALTY = 18.0;
    private static final double CYRILLIC_SUSTAINED_SIGNAL_BONUS = 260.0;
    private static final double CYRILLIC_WEAK_SIGNAL_PENALTY = 850.0;
    private static final double CYRILLIC_BYTE_SHAPE_WEIGHT = 0.75;
    private static final double KOI8_BOX_DRAWING_PENALTY = 8.0;
    private static final double JAPANESE_KANA_BONUS = 3.5;
    private static final double JAPANESE_CJK_BONUS = 0.75;
    private static final double JAPANESE_SUSTAINED_SIGNAL_BONUS = 380.0;
    private static final double JAPANESE_WEAK_SIGNAL_PENALTY = 700.0;
    private static final double CHINESE_CJK_BONUS = 1.35;
    private static final double CHINESE_SUSTAINED_SIGNAL_BONUS = 360.0;
    private static final double CHINESE_WEAK_SIGNAL_PENALTY = 550.0;
    private static final double SINGLE_SCRIPT_CHAR_BONUS = 2.1;
    private static final double SINGLE_SCRIPT_SUSTAINED_SIGNAL_BONUS = 240.0;
    private static final double SINGLE_SCRIPT_WEAK_SIGNAL_PENALTY = 450.0;
    private static final double VIETNAMESE_SIGNAL_BONUS = 2.4;
    private static final double VIETNAMESE_SUSTAINED_SIGNAL_BONUS = 260.0;
    private static final double VIETNAMESE_WEAK_SIGNAL_PENALTY = 220.0;
    private static final double WESTERN_ASCII_LIKE_BONUS = 150.0;
    private static final double WESTERN_SCRIPT_MISMATCH_PENALTY = 400.0;

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
            "windows-1258", // Vietnamese
            "windows-1255", // Hebrew
            "ISO-8859-8",
            "windows-1256", // Arabic
            "ISO-8859-6",
            "windows-874", // Thai
            "ISO-8859-11",
            "IBM866", // DOS Cyrillic
            "x-MacRoman",
            "x-MacCyrillic",
            "x-MacGreek",
            "x-MacTurkish",
            "x-MacHebrew",
            "x-MacArabic",

            // CJK and stateful East Asian legacy encodings.
            "windows-949",
            "MS949",
            "EUC-KR",
            "ISO-2022-KR",
            "Shift_JIS",
            "windows-31j",
            "EUC-JP",
            "ISO-2022-JP",
            "GB18030",
            "GBK",
            "GB2312",
            "HZ-GB-2312",
            "Big5",
            "Big5-HKSCS",
            "EUC-TW",

            // Optional legacy families: only active when Android/Java supports them.
            "VISCII",
            "x-ISCII91"
    };

    public static class EncodingResult {
        public static final int HIGH_CONFIDENCE = 3;
        public static final int MEDIUM_CONFIDENCE = 2;
        public static final int LOW_CONFIDENCE = 1;

        public final String charsetName;
        public final int confidence;
        public final String source;
        public final String family;

        EncodingResult(String charsetName, int confidence, String source, String family) {
            this.charsetName = charsetName;
            this.confidence = confidence;
            this.source = source;
            this.family = family;
        }

        public boolean isHighConfidence() {
            return confidence >= HIGH_CONFIDENCE;
        }

        public String confidenceLabel() {
            if (confidence >= HIGH_CONFIDENCE) return "high";
            if (confidence >= MEDIUM_CONFIDENCE) return "medium";
            return "low";
        }

        public String displayLabel() {
            return charsetName + " (auto, " + confidenceLabel() + ")";
        }
    }

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
        final int latinExtendedCount;
        final int greekCount;
        final int cyrillicCount;
        final int hebrewCount;
        final int arabicCount;
        final int thaiCount;
        final int cyrillicNaturalnessScore;
        final int cyrillicOddCount;
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
            this.latinExtendedCount = latinExtended;
            this.greekCount = greek;
            this.cyrillicCount = cyrillic;
            this.hebrewCount = hebrew;
            this.arabicCount = arabic;
            this.thaiCount = thai;
            this.cyrillicNaturalnessScore = cyrillicNaturalnessScore(text);
            this.cyrillicOddCount = countOddCyrillicForRussianProse(text);

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
                            - cyrillicNaturalnessScore * 2.8
                            + cyrillicOddCount * 5.5
                            + charsetTieBreakerPenalty(charsetName);
        }
    }

    /**
     * Detect text file encoding.
     */
    public static String detectEncoding(File file) {
        return detectEncodingDetailed(file).charsetName;
    }

    public static EncodingResult detectEncodingDetailed(File file) {
        try {
            byte[] primary = readSample(file, PRIMARY_SAMPLE_LIMIT);
            EncodingResult result = detectEncodingResultFromBytes(primary);

            if (shouldExtendEncodingSample(result, primary.length, file.length())) {
                byte[] extended = readSample(file, EXTENDED_SAMPLE_LIMIT);
                EncodingResult extendedResult = detectEncodingResultFromBytes(extended);
                if (extendedResult.confidence >= result.confidence
                        || result.confidence < EncodingResult.HIGH_CONFIDENCE) {
                    return extendedResult;
                }
            }

            return result;
        } catch (Exception e) {
            Log.e(TAG, "Encoding detection failed", e);
            return new EncodingResult("UTF-8", EncodingResult.LOW_CONFIDENCE, "fallback", "unicode");
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

            bis.mark(PRIMARY_SAMPLE_LIMIT + 16);
            byte[] data = readSample(bis, PRIMARY_SAMPLE_LIMIT);
            bis.reset();

            return detectEncodingResultFromBytes(data).charsetName;
        } catch (Exception e) {
            Log.e(TAG, "Encoding detection from stream failed", e);
            return "UTF-8";
        }
    }

    private static EncodingResult detectEncodingResultFromBytes(byte[] data) {
        if (data == null || data.length == 0) {
            return new EncodingResult("UTF-8", EncodingResult.HIGH_CONFIDENCE, "empty", "unicode");
        }

        String bom = detectBom(data);
        if (bom != null) {
            return new EncodingResult(bom, EncodingResult.HIGH_CONFIDENCE, "bom", "unicode");
        }

        // Stateful East Asian encodings have visible escape sequences and are
        // cheap to identify before statistical legacy scoring.
        if (looksLikeIso2022Jp(data) && Charset.isSupported("ISO-2022-JP")) {
            return new EncodingResult("ISO-2022-JP", EncodingResult.HIGH_CONFIDENCE, "escape", "japanese");
        }
        if (looksLikeIso2022Kr(data) && Charset.isSupported("ISO-2022-KR")) {
            return new EncodingResult("ISO-2022-KR", EncodingResult.HIGH_CONFIDENCE, "escape", "korean");
        }
        if (looksLikeHzGb2312(data) && Charset.isSupported("HZ-GB-2312")) {
            return new EncodingResult("HZ-GB-2312", EncodingResult.HIGH_CONFIDENCE, "escape", "chinese");
        }

        // BOM-less UTF-16 text can look like valid UTF-8 when it is mostly ASCII
        // because NUL bytes are legal control characters in a byte stream. Detect
        // strong UTF-16 byte patterns before accepting strict UTF-8.
        String bomlessUtf16 = detectBomlessUtf16(data);
        if (bomlessUtf16 != null) {
            return new EncodingResult(bomlessUtf16, EncodingResult.HIGH_CONFIDENCE, "utf16-heuristic", "unicode");
        }

        // Strict valid UTF-8 is strong explicit evidence. Legacy encodings are only
        // scored after UTF-8 fails strict validation.
        if (isStrictValidUtf8(data)) {
            return new EncodingResult("UTF-8", EncodingResult.HIGH_CONFIDENCE, "strict-utf8", "unicode");
        }

        // Large fixed-size samples can end in the middle of a multibyte UTF-8
        // character. Without this boundary-aware rescue, valid UTF-8 files can
        // fall through to legacy scoring and be mislabeled as IBM866 or another
        // single-byte code page.
        if (isUtf8WithOnlyTrailingSampleBoundaryIssue(data)) {
            return new EncodingResult("UTF-8", EncodingResult.HIGH_CONFIDENCE, "utf8-sample-boundary", "unicode");
        }

        // Accuracy-first legacy detection:
        // Do not return immediately from a Korean/Cyrillic pre-pass.  CP949,
        // ISO-8859-5, Windows-1251, KOI8, and many Western encodings can all
        // produce "valid" Unicode from the same bytes.  Decode all candidate
        // families, score the decoded text, then choose the strongest family.
        String icuDetected = detectWithAndroidIcu(data);
        String mozillaDetected = detectWithMozillaUniversalChardet(data);
        return detectLegacyEncodingByAccuracy(data, icuDetected, mozillaDetected);
    }

    private static EncodingResult detectLegacyEncodingByAccuracy(byte[] data, String icuDetected, String mozillaDetected) {
        DecodeResult best = null;
        DecodeResult second = null;
        DecodeResult bestKorean = null;
        double bestScore = Double.MAX_VALUE;
        double secondScore = Double.MAX_VALUE;
        double bestKoreanScore = Double.MAX_VALUE;

        for (String candidate : TEXT_ENCODING_CANDIDATES) {
            if (!Charset.isSupported(candidate)) continue;
            DecodeResult result = decodeCandidate(data, candidate);
            double adjusted = accuracyAdjustedScore(result, data, icuDetected, mozillaDetected);

            if ("korean".equals(guessEncodingFamily(result.charsetName)) && adjusted < bestKoreanScore) {
                bestKorean = result;
                bestKoreanScore = adjusted;
            }

            if (best == null || adjusted < bestScore) {
                second = best;
                secondScore = bestScore;
                best = result;
                bestScore = adjusted;
            } else if (adjusted < secondScore) {
                second = result;
                secondScore = adjusted;
            }
        }

        if (best == null) {
            return new EncodingResult("UTF-8", EncodingResult.LOW_CONFIDENCE, "fallback", "unicode");
        }

        boolean koreanSignalOverride = false;
        if (bestKorean != null && hasOverwhelmingKoreanSignal(bestKorean)
                && !"korean".equals(guessEncodingFamily(best.charsetName))) {
            second = best;
            secondScore = bestScore;
            best = bestKorean;
            bestScore = bestKoreanScore;
            koreanSignalOverride = true;
        }

        int confidence = accuracyConfidence(best, bestScore, second, secondScore);
        String source = koreanSignalOverride
                ? "korean-signal+accuracy-score"
                : detectorMergeSource(best.charsetName, icuDetected, mozillaDetected);
        String chosenCharset = normalizeAutoDetectedKoreanSuperset(best.charsetName);
        return new EncodingResult(chosenCharset, confidence, source, guessEncodingFamily(chosenCharset));
    }

    private static String normalizeAutoDetectedKoreanSuperset(String charsetName) {
        if (charsetName == null) return "UTF-8";
        String upper = charsetName.toUpperCase(Locale.ROOT);
        if (upper.equals("WINDOWS-949") || upper.equals("MS949") || upper.equals("CP949") || upper.equals("EUC-KR")) {
            // CP949/windows-949 is a practical superset of EUC-KR for Korean TXT.
            // Auto-detection should prefer the superset so EUC-KR-compatible files
            // still open correctly while CP949-only extension characters are not
            // lost. Manual EUC-KR selection remains available when the user wants it.
            return "windows-949";
        }
        return charsetName;
    }

    private static double accuracyAdjustedScore(DecodeResult result, byte[] data, String icuDetected, String mozillaDetected) {
        double score = result.score;
        int length = Math.max(1, result.text.length());
        String family = guessEncodingFamily(result.charsetName);

        if (icuDetected != null && result.charsetName.equalsIgnoreCase(icuDetected)) {
            score -= DETECTOR_ICU_EXACT_BONUS;
        }
        if (mozillaDetected != null && result.charsetName.equalsIgnoreCase(mozillaDetected)) {
            score -= DETECTOR_MOZILLA_EXACT_BONUS;
        }

        String resultFamily = guessEncodingFamily(result.charsetName);
        if (icuDetected != null && resultFamily.equals(guessEncodingFamily(icuDetected))) {
            score -= DETECTOR_ICU_FAMILY_BONUS;
        }
        if (mozillaDetected != null && resultFamily.equals(guessEncodingFamily(mozillaDetected))) {
            score -= DETECTOR_MOZILLA_FAMILY_BONUS;
        }
        if (icuDetected != null && mozillaDetected != null
                && guessEncodingFamily(icuDetected).equals(guessEncodingFamily(mozillaDetected))
                && resultFamily.equals(guessEncodingFamily(icuDetected))) {
            score -= DETECTOR_AGREEING_FAMILY_BONUS;
        }

        // Heavy penalties for decoded text that is technically valid Unicode but
        // structurally implausible.
        score += Math.max(0, result.replacementCount - Math.max(2, length / 500)) * SCORE_REPLACEMENT_PENALTY;
        score += Math.max(0, result.badControlCount - Math.max(2, length / 350)) * SCORE_BAD_CONTROL_PENALTY;
        score += result.nulCount * SCORE_NUL_PENALTY;

        switch (family) {
            case "korean": {
                int signal = result.hangulCount + result.cjkCount;
                double density = signal / (double) length;
                // Hangul is a very strong signal because random legacy
                // mojibake rarely forms sustained valid Korean syllables.
                score -= result.hangulCount * KOREAN_HANGUL_BONUS;
                score -= result.cjkCount * KOREAN_CJK_BONUS;
                if (result.hangulCount >= 8 && density >= 0.04) score -= KOREAN_SUSTAINED_SIGNAL_BONUS;
                if (result.hangulCount >= 80 && density >= 0.18) score -= KOREAN_STRONG_SIGNAL_BONUS;
                if (result.hangulCount == 0 && result.cjkCount < 4) score += KOREAN_WEAK_SIGNAL_PENALTY;

                // CP949/MS949 is the practical superset for old Korean TXT. When
                // EUC-KR and CP949 are near-tied, prefer the superset.
                if (result.charsetName.equals("windows-949") || result.charsetName.equals("MS949")) score -= KOREAN_SUPERSET_BONUS;
                if (result.charsetName.equals("EUC-KR")) score += KOREAN_EUC_KR_TIE_PENALTY;
                break;
            }

            case "cyrillic": {
                double density = result.cyrillicCount / (double) length;
                score -= result.cyrillicCount * CYRILLIC_CHAR_BONUS;
                score -= result.cyrillicNaturalnessScore * CYRILLIC_NATURALNESS_BONUS;

                // Many non-Cyrillic legacy files, especially CP949 Korean text,
                // can decode into "valid" Cyrillic under ISO-8859-5/CP1251.
                // Those false positives tend to contain a high ratio of Serbian/
                // Ukrainian/Macedonian-only letters in otherwise Russian-looking
                // prose.  Penalize that pattern strongly so real Hangul/Hanja
                // evidence can beat Cyrillic mojibake automatically.
                score += result.cyrillicOddCount * CYRILLIC_ODD_CHAR_PENALTY;
                if (result.cyrillicCount > 0
                        && result.cyrillicOddCount > Math.max(8, result.cyrillicCount / 50)) {
                    score += CYRILLIC_ODD_PATTERN_BASE_PENALTY + result.cyrillicOddCount * CYRILLIC_ODD_PATTERN_EXTRA_PENALTY;
                }

                if (result.cyrillicCount >= 10 && density >= 0.04) score -= CYRILLIC_SUSTAINED_SIGNAL_BONUS;
                if (result.cyrillicCount < 8) score += CYRILLIC_WEAK_SIGNAL_PENALTY;

                if (result.charsetName.equals("ISO-8859-5")) {
                    score -= iso88595ByteShapeBonus(data) * CYRILLIC_BYTE_SHAPE_WEIGHT;
                } else if (result.charsetName.equals("windows-1251")) {
                    score -= windows1251ByteShapeBonus(data) * CYRILLIC_BYTE_SHAPE_WEIGHT;
                } else if (result.charsetName.startsWith("KOI8")) {
                    score += koi8BoxDrawingPenalty(result.text) * KOI8_BOX_DRAWING_PENALTY;
                }
                break;
            }

            case "japanese": {
                int signal = result.kanaCount + result.cjkCount;
                double density = signal / (double) length;
                score -= result.kanaCount * JAPANESE_KANA_BONUS;
                score -= result.cjkCount * JAPANESE_CJK_BONUS;
                if (result.kanaCount >= 6 && density >= 0.03) score -= JAPANESE_SUSTAINED_SIGNAL_BONUS;
                if (result.kanaCount == 0 && result.cjkCount < 8) score += JAPANESE_WEAK_SIGNAL_PENALTY;
                break;
            }

            case "chinese": {
                double density = result.cjkCount / (double) length;
                score -= result.cjkCount * CHINESE_CJK_BONUS;
                if (result.cjkCount >= 16 && density >= 0.06) score -= CHINESE_SUSTAINED_SIGNAL_BONUS;
                if (result.cjkCount < 8) score += CHINESE_WEAK_SIGNAL_PENALTY;
                break;
            }

            case "greek": {
                double density = result.greekCount / (double) length;
                score -= result.greekCount * SINGLE_SCRIPT_CHAR_BONUS;
                if (result.greekCount >= 8 && density >= 0.04) score -= SINGLE_SCRIPT_SUSTAINED_SIGNAL_BONUS;
                if (result.greekCount < 6) score += SINGLE_SCRIPT_WEAK_SIGNAL_PENALTY;
                break;
            }

            case "hebrew": {
                double density = result.hebrewCount / (double) length;
                score -= result.hebrewCount * SINGLE_SCRIPT_CHAR_BONUS;
                if (result.hebrewCount >= 8 && density >= 0.04) score -= SINGLE_SCRIPT_SUSTAINED_SIGNAL_BONUS;
                if (result.hebrewCount < 6) score += SINGLE_SCRIPT_WEAK_SIGNAL_PENALTY;
                break;
            }

            case "arabic": {
                double density = result.arabicCount / (double) length;
                score -= result.arabicCount * SINGLE_SCRIPT_CHAR_BONUS;
                if (result.arabicCount >= 8 && density >= 0.04) score -= SINGLE_SCRIPT_SUSTAINED_SIGNAL_BONUS;
                if (result.arabicCount < 6) score += SINGLE_SCRIPT_WEAK_SIGNAL_PENALTY;
                break;
            }

            case "thai": {
                double density = result.thaiCount / (double) length;
                score -= result.thaiCount * SINGLE_SCRIPT_CHAR_BONUS;
                if (result.thaiCount >= 8 && density >= 0.04) score -= SINGLE_SCRIPT_SUSTAINED_SIGNAL_BONUS;
                if (result.thaiCount < 6) score += SINGLE_SCRIPT_WEAK_SIGNAL_PENALTY;
                break;
            }

            case "vietnamese": {
                int signal = result.latinExtendedCount + countVietnameseToneMarks(result.text);
                double density = signal / (double) length;
                score -= signal * VIETNAMESE_SIGNAL_BONUS;
                if (signal >= 8 && density >= 0.025) score -= VIETNAMESE_SUSTAINED_SIGNAL_BONUS;
                if (signal < 4) score += VIETNAMESE_WEAK_SIGNAL_PENALTY;
                break;
            }

            case "western": {
                int nonAscii = Math.max(0, length - result.asciiCount);
                if (nonAscii < Math.max(4, length / 200) && result.badControlCount == 0) {
                    score -= WESTERN_ASCII_LIKE_BONUS;
                }
                if (result.cjkCount + result.hangulCount + result.kanaCount
                        + result.cyrillicCount + result.greekCount
                        + result.hebrewCount + result.arabicCount + result.thaiCount > length / 8) {
                    score += WESTERN_SCRIPT_MISMATCH_PENALTY;
                }
                break;
            }

            default:
                break;
        }

        return score;
    }

    private static boolean hasOverwhelmingKoreanSignal(DecodeResult result) {
        if (result == null || result.text == null) return false;
        int length = Math.max(1, result.text.length());
        int signal = result.hangulCount + result.cjkCount;

        if (result.replacementCount > Math.max(3, length / 250)) return false;
        if (result.nulCount > 0) return false;
        if (result.badControlCount > Math.max(4, length / 160)) return false;

        // This deliberately requires strong sustained Hangul evidence. It prevents
        // PC/browser detector hints from stealing Korean CP949/MS949 novels as Thai,
        // Arabic, or other single-byte legacy encodings, while avoiding false
        // positives for real Thai/Cyrillic/Western files.
        return result.hangulCount >= 48
                && signal >= 80
                && (signal / (double) length) >= 0.08;
    }

    private static int accuracyConfidence(DecodeResult best, double bestScore, DecodeResult second, double secondScore) {
        if (best == null) return EncodingResult.LOW_CONFIDENCE;
        int length = Math.max(1, best.text.length());
        double margin = (second == null || equivalentEncodingForConfidence(best.charsetName, second.charsetName))
                ? 9999.0
                : secondScore - bestScore;

        if (best.replacementCount > Math.max(3, length / 250)
                || best.nulCount > 0
                || best.badControlCount > Math.max(4, length / 160)) {
            return EncodingResult.LOW_CONFIDENCE;
        }

        String family = guessEncodingFamily(best.charsetName);
        boolean strongFamilySignal = false;
        switch (family) {
            case "korean":
                strongFamilySignal = best.hangulCount >= 8 && (best.hangulCount + best.cjkCount) >= 12;
                break;
            case "cyrillic":
                strongFamilySignal = best.cyrillicCount >= 10 && best.cyrillicOddCount <= Math.max(3, best.cyrillicCount / 12);
                break;
            case "japanese":
                strongFamilySignal = best.kanaCount >= 6 || best.cjkCount >= 20;
                break;
            case "chinese":
                strongFamilySignal = best.cjkCount >= 20;
                break;
            case "greek":
                strongFamilySignal = best.greekCount >= 8;
                break;
            case "hebrew":
                strongFamilySignal = best.hebrewCount >= 8;
                break;
            case "arabic":
                strongFamilySignal = best.arabicCount >= 8;
                break;
            case "thai":
                strongFamilySignal = best.thaiCount >= 8;
                break;
            case "vietnamese":
                strongFamilySignal = best.latinExtendedCount + countVietnameseToneMarks(best.text) >= 8;
                break;
            case "western":
                strongFamilySignal = best.asciiCount > length * 0.65;
                break;
            default:
                strongFamilySignal = best.alphabeticScriptCount >= 12 || best.asciiCount > length * 0.70;
                break;
        }

        if (strongFamilySignal && margin >= 80.0) return EncodingResult.HIGH_CONFIDENCE;
        if (strongFamilySignal || margin >= 40.0) return EncodingResult.MEDIUM_CONFIDENCE;
        return EncodingResult.LOW_CONFIDENCE;
    }

    private static String detectEncodingFromBytes(byte[] data) {
        return detectEncodingResultFromBytes(data).charsetName;
    }

    private static EncodingResult detectEncodingResultFromDataAdaptive(byte[] data) {
        if (data == null || data.length == 0) {
            return new EncodingResult("UTF-8", EncodingResult.HIGH_CONFIDENCE, "empty", "unicode");
        }

        byte[] primary = sampleBytes(data, PRIMARY_SAMPLE_LIMIT);
        EncodingResult result = detectEncodingResultFromBytes(primary);

        if (shouldExtendEncodingSample(result, primary.length, data.length)) {
            byte[] extended = sampleBytes(data, EXTENDED_SAMPLE_LIMIT);
            EncodingResult extendedResult = detectEncodingResultFromBytes(extended);
            if (extendedResult.confidence >= result.confidence
                    || result.confidence < EncodingResult.HIGH_CONFIDENCE) {
                return extendedResult;
            }
        }

        return result;
    }

    private static boolean shouldExtendEncodingSample(EncodingResult result, int sampleLength, long fullLength) {
        if (result == null) return true;
        if (fullLength <= sampleLength) return false;
        if (sampleLength >= EXTENDED_SAMPLE_LIMIT) return false;

        String source = result.source != null ? result.source : "";
        if ("bom".equals(source)
                || "strict-utf8".equals(source)
                || "utf8-sample-boundary".equals(source)
                || "utf16-heuristic".equals(source)
                || "escape".equals(source)) {
            return false;
        }

        // Legacy detector work is expensive. Only pay for the 512 KiB pass when
        // the first-pass decision is genuinely weak or detector-family consensus
        // did not produce a high-confidence result.
        return result.confidence < EncodingResult.HIGH_CONFIDENCE;
    }


    public static String[] getManualTextEncodingOptions() {
        return new String[]{
                "Auto",
                "UTF-8",
                "UTF-16LE",
                "UTF-16BE",
                "windows-949",
                "MS949",
                "EUC-KR",
                "windows-31j",
                "Shift_JIS",
                "EUC-JP",
                "ISO-2022-JP",
                "GB18030",
                "GBK",
                "Big5",
                "windows-1251",
                "ISO-8859-5",
                "KOI8-R",
                "KOI8-U",
                "windows-1252",
                "ISO-8859-1",
                "ISO-8859-15",
                "windows-1250",
                "ISO-8859-2",
                "windows-1253",
                "ISO-8859-7",
                "windows-1254",
                "ISO-8859-9",
                "windows-1257",
                "ISO-8859-13",
                "windows-1258",
                "windows-1255",
                "ISO-8859-8",
                "windows-1256",
                "ISO-8859-6",
                "windows-874",
                "ISO-8859-11",
                "IBM866",
                "x-MacRoman",
                "x-MacCyrillic",
                "x-MacGreek",
                "x-MacTurkish",
                "x-MacHebrew",
                "x-MacArabic",
                "GB2312",
                "HZ-GB-2312",
                "Big5-HKSCS",
                "EUC-TW",
                "ISO-2022-KR",
                "VISCII",
                "x-ISCII91"
        };
    }

    public static String normalizeManualEncodingName(String name) {
        String normalized = normalizeDetectedCharset(name);
        return normalized != null && Charset.isSupported(normalized) ? normalized : null;
    }

    private static boolean equivalentEncodingForConfidence(String a, String b) {
        if (a == null || b == null) return false;
        String na = a.toUpperCase(Locale.ROOT);
        String nb = b.toUpperCase(Locale.ROOT);
        if ((na.equals("WINDOWS-949") || na.equals("MS949"))
                && (nb.equals("WINDOWS-949") || nb.equals("MS949"))) return true;
        if ((na.equals("SHIFT_JIS") || na.equals("WINDOWS-31J"))
                && (nb.equals("SHIFT_JIS") || nb.equals("WINDOWS-31J"))) return true;
        return na.equals(nb);
    }

    private static String guessEncodingFamily(String charsetName) {
        if (charsetName == null) return "unknown";
        String n = charsetName.toUpperCase(Locale.ROOT);
        if (n.contains("949") || n.equals("MS949") || n.equals("EUC-KR") || n.equals("ISO-2022-KR")) return "korean";
        if (n.contains("SHIFT") || n.contains("31J") || n.equals("EUC-JP") || n.equals("ISO-2022-JP")) return "japanese";
        if (n.contains("GB") || n.contains("BIG5") || n.contains("HZ") || n.equals("EUC-TW")) return "chinese";
        if (n.equals("ISO-8859-5") || n.equals("WINDOWS-1251") || n.startsWith("KOI8")
                || n.equals("IBM866") || n.contains("MACCYRILLIC")) return "cyrillic";
        if (n.equals("UTF-8") || n.startsWith("UTF-16") || n.startsWith("UTF-32")) return "unicode";
        if (n.equals("WINDOWS-1253") || n.equals("ISO-8859-7") || n.contains("MACGREEK")) return "greek";
        if (n.equals("WINDOWS-1258") || n.equals("VISCII")) return "vietnamese";
        if (n.equals("WINDOWS-1255") || n.equals("ISO-8859-8") || n.contains("MACHEBREW")) return "hebrew";
        if (n.equals("WINDOWS-1256") || n.equals("ISO-8859-6") || n.contains("MACARABIC")) return "arabic";
        if (n.equals("WINDOWS-874") || n.equals("ISO-8859-11")) return "thai";
        return "western";
    }




    private static double iso88595ByteShapeBonus(byte[] data) {
        int b0bf = 0;
        int d0ef = 0;
        int c0cf = 0;
        int high = 0;

        for (byte raw : data) {
            int b = raw & 0xFF;
            if (b >= 0x80) high++;
            if (b >= 0xB0 && b <= 0xBF) b0bf++;
            if (b >= 0xD0 && b <= 0xEF) d0ef++;
            if (b >= 0xC0 && b <= 0xCF) c0cf++;
        }

        if (high == 0) return 0.0;
        double bonus = 0.0;
        bonus += Math.min(b0bf, 80) * 0.9;
        bonus += Math.min(d0ef, 160) * 0.28;
        bonus += Math.min(c0cf, 80) * 0.15;
        return bonus;
    }

    private static double windows1251ByteShapeBonus(byte[] data) {
        int c0ff = 0;
        int a8b8 = 0;
        int c1Controls = 0;

        for (byte raw : data) {
            int b = raw & 0xFF;
            if (b >= 0xC0 && b <= 0xFF) c0ff++;
            if (b == 0xA8 || b == 0xB8) a8b8++;
            if (b >= 0x80 && b <= 0x9F) c1Controls++;
        }

        double bonus = 0.0;
        bonus += Math.min(c0ff, 200) * 0.22;
        bonus += a8b8 * 2.0;
        bonus += Math.min(c1Controls, 40) * 0.35; // real cp1251 punctuation area, not ISO text controls
        return bonus;
    }

    private static int koi8BoxDrawingPenalty(String text) {
        if (text == null || text.isEmpty()) return 0;
        int penalty = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch >= 0x2500 && ch <= 0x257F) penalty++;
        }
        return penalty;
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
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static String detectorMergeSource(String bestCharset, String icuDetected, String mozillaDetected) {
        boolean icuExact = icuDetected != null && bestCharset != null && bestCharset.equalsIgnoreCase(icuDetected);
        boolean mozExact = mozillaDetected != null && bestCharset != null && bestCharset.equalsIgnoreCase(mozillaDetected);
        boolean icuFamily = icuDetected != null && bestCharset != null
                && guessEncodingFamily(bestCharset).equals(guessEncodingFamily(icuDetected));
        boolean mozFamily = mozillaDetected != null && bestCharset != null
                && guessEncodingFamily(bestCharset).equals(guessEncodingFamily(mozillaDetected));

        if (icuExact && mozExact) return "icu+mozilla+accuracy-score";
        if (mozExact && icuFamily) return "mozilla+icu-family+accuracy-score";
        if (icuExact && mozFamily) return "icu+mozilla-family+accuracy-score";
        if (mozExact) return "mozilla+accuracy-score";
        if (icuExact) return "icu+accuracy-score";
        if (icuFamily && mozFamily) return "icu-family+mozilla-family+accuracy-score";
        if (mozFamily) return "mozilla-family+accuracy-score";
        if (icuFamily) return "icu-family+accuracy-score";
        return "accuracy-score";
    }

    private static String detectWithMozillaUniversalChardet(byte[] data) {
        if (data == null || data.length == 0) return null;
        try {
            Class<?> detectorClass = Class.forName("org.mozilla.universalchardet.UniversalDetector");
            Object detector = null;

            for (java.lang.reflect.Constructor<?> ctor : detectorClass.getConstructors()) {
                Class<?>[] params = ctor.getParameterTypes();
                if (params.length == 0) {
                    detector = ctor.newInstance();
                    break;
                }
                if (params.length == 1) {
                    detector = ctor.newInstance(new Object[]{null});
                    break;
                }
            }
            if (detector == null) return null;

            detectorClass.getMethod("handleData", byte[].class, int.class, int.class)
                    .invoke(detector, data, 0, data.length);
            detectorClass.getMethod("dataEnd").invoke(detector);
            Object detected = detectorClass.getMethod("getDetectedCharset").invoke(detector);

            try {
                detectorClass.getMethod("reset").invoke(detector);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Some versions do not require explicit reset for a one-shot instance.
            }

            if (!(detected instanceof String)) return null;
            String name = normalizeDetectedCharset((String) detected);
            if (name == null || !Charset.isSupported(name)) return null;

            DecodeResult result = decodeCandidate(data, name);
            int length = Math.max(1, result.text.length());
            if (result.replacementCount > Math.max(2, length / 120)) return null;
            if (result.nulCount > Math.max(1, length / 240)) return null;
            if (result.badControlCount > Math.max(2, length / 90)) return null;
            return name;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static String normalizeDetectedCharset(String name) {
        if (name == null) return null;
        String trimmed = name.trim();
        if (trimmed.isEmpty()) return null;
        String upper = trimmed.toUpperCase(Locale.ROOT).replace('_', '-');
        if (upper.equals("ISO-8859-8-I")) return "ISO-8859-8";
        if (upper.equals("UTF32") || upper.equals("UTF-32")) return "UTF-32";
        if (upper.equals("UTF32LE") || upper.equals("UTF-32LE")) return "UTF-32LE";
        if (upper.equals("UTF32BE") || upper.equals("UTF-32BE")) return "UTF-32BE";
        if (upper.equals("MS949") || upper.equals("CP949") || upper.equals("X-WINDOWS-949")
                || upper.equals("KS-C-5601") || upper.equals("KS-C-5601-1987")) return "windows-949";
        if (upper.equals("ISO2022KR") || upper.equals("ISO-2022-KR")) return "ISO-2022-KR";
        if (upper.equals("WINDOWS-31J") || upper.equals("MS932") || upper.equals("CP932")) return "windows-31j";
        if (upper.equals("SHIFT-JIS") || upper.equals("SHIFT_JIS") || upper.equals("SJIS")) return "Shift_JIS";
        if (upper.equals("CP1250")) return "windows-1250";
        if (upper.equals("CP1251")) return "windows-1251";
        if (upper.equals("CP1252")) return "windows-1252";
        if (upper.equals("CP1253")) return "windows-1253";
        if (upper.equals("CP1254")) return "windows-1254";
        if (upper.equals("CP1255")) return "windows-1255";
        if (upper.equals("CP1256")) return "windows-1256";
        if (upper.equals("CP1257")) return "windows-1257";
        if (upper.equals("CP1258")) return "windows-1258";
        if (upper.equals("CP874")) return "windows-874";
        if (upper.equals("CP866") || upper.equals("IBM-866") || upper.equals("866")) return "IBM866";
        if (upper.equals("GB2312") || upper.equals("GB-2312") || upper.equals("EUC-CN")) return "GB2312";
        if (upper.equals("HZ") || upper.equals("HZGB2312") || upper.equals("HZ-GB2312")) return "HZ-GB-2312";
        if (upper.equals("BIG5HKSCS") || upper.equals("BIG5-HKSCS")) return "Big5-HKSCS";
        if (upper.equals("EUCHT") || upper.equals("EUC-TW")) return "EUC-TW";
        if (upper.equals("MACROMAN") || upper.equals("X-MAC-ROMAN")) return "x-MacRoman";
        if (upper.equals("MACCYRILLIC") || upper.equals("X-MAC-CYRILLIC")) return "x-MacCyrillic";
        if (upper.equals("MACGREEK") || upper.equals("X-MAC-GREEK")) return "x-MacGreek";
        if (upper.equals("MACTURKISH") || upper.equals("X-MAC-TURKISH")) return "x-MacTurkish";
        if (upper.equals("MACHEBREW") || upper.equals("X-MAC-HEBREW")) return "x-MacHebrew";
        if (upper.equals("MACARABIC") || upper.equals("X-MAC-ARABIC")) return "x-MacArabic";
        if (upper.equals("ISO8859-5") || upper.equals("ISO-8859-5") || upper.equals("ISO-IR-144")
                || upper.equals("CYRILLIC") || upper.equals("CSISOLATINCYRILLIC")) return "ISO-8859-5";
        return trimmed;
    }

    /**
     * Read entire text file with detected encoding and safe replacement.
     */
    public static String readTextFile(File file) throws IOException {
        byte[] data = readAllBytes(file);
        String encoding = detectEncodingResultFromDataAdaptive(data).charsetName;
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
            String encoding = detectEncodingResultFromDataAdaptive(data).charsetName;
            return decodeBestEffort(data, encoding);
        }
    }

    private static String decodeBestEffort(byte[] data, String encoding) {
        String bom = detectBom(data);
        int offset = bomOffset(data);
        byte[] body = offset > 0 ? Arrays.copyOfRange(data, offset, data.length) : data;

        // A BOM is explicit metadata from the file. Do not let broad legacy
        // candidate scoring override it, or UTF-8-BOM Korean/Unicode text can be
        // misread as a single-byte/CJK legacy encoding when mojibake happens to
        // receive a lower score.
        if (bom != null && Charset.isSupported(bom)) {
            return sanitizeDecodedText(decodeCandidate(body, bom).text);
        }

        // detectEncodingFromBytes() already performs BOM, UTF-16 heuristics,
        // strict UTF-8 validation, ICU detection, and legacy fallback scoring.
        // Once it selects an encoding, trust that selection here instead of
        // rescoring every candidate on the full file and accidentally changing
        // the result for later chunks or longer files.
        String selected = normalizeDetectedCharset(encoding);
        if (selected != null && Charset.isSupported(selected)) {
            return sanitizeDecodedText(decodeCandidate(body, selected).text);
        }

        DecodeResult best = null;
        for (String candidate : TEXT_ENCODING_CANDIDATES) {
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
        return readSample(file, SAMPLE_LIMIT);
    }

    private static byte[] readSample(File file, int limit) throws IOException {
        try (InputStream is = new FileInputStream(file)) {
            byte[] sample = readSample(is, limit);
            return file.length() > sample.length ? trimUtf8SampleBoundary(sample) : sample;
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

        byte[] sample = out.toByteArray();
        return total >= limit ? trimUtf8SampleBoundary(sample) : sample;
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
        return sampleBytes(data, SAMPLE_LIMIT);
    }

    private static byte[] sampleBytes(byte[] data, int limit) {
        if (data == null || data.length <= limit) return data;
        return trimUtf8SampleBoundary(Arrays.copyOf(data, limit));
    }

    private static byte[] trimUtf8SampleBoundary(byte[] sample) {
        if (sample == null || sample.length == 0) return sample;

        int safeEnd = utf8SafeBoundary(sample, sample.length);
        if (safeEnd <= 0 || safeEnd >= sample.length) return sample;
        return Arrays.copyOf(sample, safeEnd);
    }

    private static int utf8SafeBoundary(byte[] data, int limit) {
        if (data == null || data.length == 0) return 0;

        int end = Math.min(Math.max(0, limit), data.length);
        if (end <= 0 || end >= data.length) {
            // Still useful for fixed-size readSample(): end may equal sample.length
            // even when the original file continues beyond the sample.
            end = Math.min(Math.max(0, limit), data.length);
        }
        if (end <= 0) return 0;

        int last = data[end - 1] & 0xFF;
        if (last < 0x80) return end;

        int lead = end - 1;
        int continuationCount = 0;
        while (lead >= 0 && ((data[lead] & 0xC0) == 0x80) && continuationCount < 4) {
            lead--;
            continuationCount++;
        }

        if (lead < 0) return end;

        int leadByte = data[lead] & 0xFF;
        int expected;
        if ((leadByte & 0x80) == 0) {
            return end;
        } else if ((leadByte & 0xE0) == 0xC0) {
            expected = 2;
        } else if ((leadByte & 0xF0) == 0xE0) {
            expected = 3;
        } else if ((leadByte & 0xF8) == 0xF0) {
            expected = 4;
        } else {
            return end;
        }

        int available = end - lead;
        if (available < expected) {
            return lead;
        }
        return end;
    }

    private static String detectBom(byte[] data) {
        // Check UTF-32 before UTF-16 because UTF-32LE starts with FF FE.
        if (data.length >= 4
                && data[0] == BOM_UTF32_LE[0]
                && data[1] == BOM_UTF32_LE[1]
                && data[2] == BOM_UTF32_LE[2]
                && data[3] == BOM_UTF32_LE[3]
                && Charset.isSupported("UTF-32LE")) {
            return "UTF-32LE";
        }

        if (data.length >= 4
                && data[0] == BOM_UTF32_BE[0]
                && data[1] == BOM_UTF32_BE[1]
                && data[2] == BOM_UTF32_BE[2]
                && data[3] == BOM_UTF32_BE[3]
                && Charset.isSupported("UTF-32BE")) {
            return "UTF-32BE";
        }

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
        if (data.length >= 4
                && ((data[0] == BOM_UTF32_LE[0] && data[1] == BOM_UTF32_LE[1]
                        && data[2] == BOM_UTF32_LE[2] && data[3] == BOM_UTF32_LE[3])
                || (data[0] == BOM_UTF32_BE[0] && data[1] == BOM_UTF32_BE[1]
                        && data[2] == BOM_UTF32_BE[2] && data[3] == BOM_UTF32_BE[3]))) {
            return 4;
        }

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
        return validateUtf8(data, false).valid;
    }

    private static boolean isUtf8WithOnlyTrailingSampleBoundaryIssue(byte[] data) {
        Utf8Validation validation = validateUtf8(data, true);
        return validation.valid
                && validation.trailingBoundaryIssue
                && data != null
                && data.length >= SAMPLE_LIMIT
                && validation.validByteCount >= Math.max(0, data.length - 4);
    }

    private static class Utf8Validation {
        final boolean valid;
        final boolean trailingBoundaryIssue;
        final int multibyteCount;
        final int validByteCount;

        Utf8Validation(boolean valid, boolean trailingBoundaryIssue, int multibyteCount, int validByteCount) {
            this.valid = valid;
            this.trailingBoundaryIssue = trailingBoundaryIssue;
            this.multibyteCount = multibyteCount;
            this.validByteCount = validByteCount;
        }
    }

    private static Utf8Validation validateUtf8(byte[] data, boolean allowTrailingIncomplete) {
        if (data == null) return new Utf8Validation(false, false, 0, 0);

        int i = 0;
        int multibyte = 0;

        while (i < data.length) {
            int b = data[i] & 0xFF;
            int seqLen;

            if (b <= 0x7F) {
                i++;
                continue;
            } else if (b >= 0xC2 && b <= 0xDF) {
                seqLen = 2;
            } else if (b >= 0xE0 && b <= 0xEF) {
                seqLen = 3;
            } else if (b >= 0xF0 && b <= 0xF4) {
                seqLen = 4;
            } else {
                return new Utf8Validation(false, false, multibyte, i);
            }

            if (i + seqLen > data.length) {
                return new Utf8Validation(allowTrailingIncomplete, allowTrailingIncomplete, multibyte, i);
            }

            for (int j = 1; j < seqLen; j++) {
                if ((data[i + j] & 0xC0) != 0x80) {
                    return new Utf8Validation(false, false, multibyte, i);
                }
            }

            // Reject overlong sequences, UTF-16 surrogate halves, and code points
            // above U+10FFFF. This avoids treating arbitrary legacy bytes as UTF-8.
            if (seqLen == 3) {
                int b1 = data[i + 1] & 0xFF;
                if (b == 0xE0 && b1 < 0xA0) return new Utf8Validation(false, false, multibyte, i);
                if (b == 0xED && b1 > 0x9F) return new Utf8Validation(false, false, multibyte, i);
            } else if (seqLen == 4) {
                int b1 = data[i + 1] & 0xFF;
                if (b == 0xF0 && b1 < 0x90) return new Utf8Validation(false, false, multibyte, i);
                if (b == 0xF4 && b1 > 0x8F) return new Utf8Validation(false, false, multibyte, i);
            }

            multibyte++;
            i += seqLen;
        }

        // ASCII-only files are also valid UTF-8.
        return new Utf8Validation(true, false, multibyte, i);
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

    private static boolean looksLikeIso2022Kr(byte[] data) {
        for (int i = 0; i < data.length - 3; i++) {
            if ((data[i] & 0xFF) == 0x1B
                    && (data[i + 1] & 0xFF) == 0x24
                    && (data[i + 2] & 0xFF) == 0x29
                    && (data[i + 3] & 0xFF) == 0x43) {
                return true; // ESC $ ) C
            }
        }
        return false;
    }

    private static boolean looksLikeHzGb2312(byte[] data) {
        for (int i = 0; i < data.length - 1; i++) {
            int b0 = data[i] & 0xFF;
            int b1 = data[i + 1] & 0xFF;
            if (b0 == '~' && (b1 == '{' || b1 == '}' || b1 == '~' || b1 == '\n')) {
                return true;
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

    private static int cyrillicNaturalnessScore(String text) {
        if (text == null || text.isEmpty()) return 0;
        String lower = text.toLowerCase(Locale.ROOT);
        String[] common = new String[]{
                "ст", "но", "то", "на", "ен", "ов", "ни", "ра", "ко", "ро",
                "не", "по", "го", "ре", "ер", "пр", "ли", "ла", "ел", "ос",
                "ть", "те", "ал", "ка", "ес", "ом", "ет", "ит", "ия", "ых",
                "ого", "его", "что", "как", "для", "это", "его", "она", "они",
                "или", "при", "ост", "ени", "ого", "его", "ова", "ени"
        };

        int score = 0;
        for (String item : common) {
            score += countOccurrences(lower, item);
        }
        return score;
    }

    private static int countOccurrences(String text, String needle) {
        if (text == null || needle == null || needle.isEmpty()) return 0;
        int count = 0;
        int from = 0;
        while (from < text.length()) {
            int idx = text.indexOf(needle, from);
            if (idx < 0) break;
            count++;
            from = idx + needle.length();
        }
        return count;
    }

    private static int countOddCyrillicForRussianProse(String text) {
        if (text == null || text.isEmpty()) return 0;
        int odd = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            switch (ch) {
                // These letters are valid Unicode Cyrillic and valid for some
                // languages, but in Russian-prose TXT they often appear when
                // ISO-8859-5 bytes were incorrectly decoded as Windows-1251.
                case 'ѓ': case 'Ѓ':
                case 'є': case 'Є':
                case 'ѕ': case 'Ѕ':
                case 'і': case 'І':
                case 'ї': case 'Ї':
                case 'ј': case 'Ј':
                case 'љ': case 'Љ':
                case 'њ': case 'Њ':
                case 'ћ': case 'Ћ':
                case 'ќ': case 'Ќ':
                case 'ў': case 'Ў':
                case 'џ': case 'Џ':
                    odd++;
                    break;
                default:
                    break;
            }
        }
        return odd;
    }

    private static int countVietnameseToneMarks(String text) {
        if (text == null || text.isEmpty()) return 0;
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case 'Ă': case 'ă': case 'Â': case 'â': case 'Ê': case 'ê':
                case 'Ô': case 'ô': case 'Ơ': case 'ơ': case 'Ư': case 'ư':
                case 'Đ': case 'đ':
                case 'À': case 'Á': case 'Ả': case 'Ã': case 'Ạ':
                case 'à': case 'á': case 'ả': case 'ã': case 'ạ':
                case 'Ằ': case 'Ắ': case 'Ẳ': case 'Ẵ': case 'Ặ':
                case 'ằ': case 'ắ': case 'ẳ': case 'ẵ': case 'ặ':
                case 'Ầ': case 'Ấ': case 'Ẩ': case 'Ẫ': case 'Ậ':
                case 'ầ': case 'ấ': case 'ẩ': case 'ẫ': case 'ậ':
                case 'È': case 'É': case 'Ẻ': case 'Ẽ': case 'Ẹ':
                case 'è': case 'é': case 'ẻ': case 'ẽ': case 'ẹ':
                case 'Ề': case 'Ế': case 'Ể': case 'Ễ': case 'Ệ':
                case 'ề': case 'ế': case 'ể': case 'ễ': case 'ệ':
                case 'Ì': case 'Í': case 'Ỉ': case 'Ĩ': case 'Ị':
                case 'ì': case 'í': case 'ỉ': case 'ĩ': case 'ị':
                case 'Ò': case 'Ó': case 'Ỏ': case 'Õ': case 'Ọ':
                case 'ò': case 'ó': case 'ỏ': case 'õ': case 'ọ':
                case 'Ồ': case 'Ố': case 'Ổ': case 'Ỗ': case 'Ộ':
                case 'ồ': case 'ố': case 'ổ': case 'ỗ': case 'ộ':
                case 'Ờ': case 'Ớ': case 'Ở': case 'Ỡ': case 'Ợ':
                case 'ờ': case 'ớ': case 'ở': case 'ỡ': case 'ợ':
                case 'Ù': case 'Ú': case 'Ủ': case 'Ũ': case 'Ụ':
                case 'ù': case 'ú': case 'ủ': case 'ũ': case 'ụ':
                case 'Ừ': case 'Ứ': case 'Ử': case 'Ữ': case 'Ự':
                case 'ừ': case 'ứ': case 'ử': case 'ữ': case 'ự':
                case 'Ỳ': case 'Ý': case 'Ỷ': case 'Ỹ': case 'Ỵ':
                case 'ỳ': case 'ý': case 'ỷ': case 'ỹ': case 'ỵ':
                    count++;
                    break;
                default:
                    break;
            }
        }
        return count;
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
        if ((n.equals("WINDOWS-1258") || n.equals("VISCII")) && latinExtended > 0) {
            return latinExtended * 2.0;
        }
        if ((n.equals("WINDOWS-1252") || n.equals("ISO-8859-1") || n.equals("ISO-8859-15")
                || n.equals("WINDOWS-1250") || n.equals("ISO-8859-2")
                || n.equals("WINDOWS-1254") || n.equals("ISO-8859-9")
                || n.equals("WINDOWS-1257") || n.equals("ISO-8859-13")
                || n.contains("MAC")) && latinExtended > 0) {
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
                || n.equals("WINDOWS-1257") || n.equals("WINDOWS-1258") || n.equals("WINDOWS-874")
                || n.equals("IBM866")) return 8.0;
        if (n.startsWith("ISO-8859-") || n.startsWith("KOI8")) return 11.0;
        if (n.contains("949") || n.equals("MS949") || n.equals("EUC-KR")) return 12.0;
        if (n.contains("SHIFT") || n.contains("31J")) return 14.0;
        if (n.contains("EUC-JP")) return 16.0;
        if (n.contains("GB") || n.contains("HZ")) return 18.0;
        if (n.contains("BIG5") || n.contains("EUC-TW")) return 20.0;
        if (n.contains("MAC")) return 22.0;

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
