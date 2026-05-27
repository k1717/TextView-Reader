package com.textview.reader.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class FileUtilsTest {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void detect_emptyInput_defaultsToUtf8() throws Exception {
        File file = writeBytes("empty.txt", new byte[0]);

        FileUtils.EncodingResult result = FileUtils.detectEncodingDetailed(file);

        assertEquals("UTF-8", result.charsetName);
        assertTrue(result.isHighConfidence());
    }

    @Test
    public void detect_koreanUtf8_returnsUtf8() throws Exception {
        File file = writeText("korean_utf8.txt",
                repeat("한글 UTF-8 문장입니다. 가나다라 마바사 아자차카타파하.\n", 200),
                StandardCharsets.UTF_8);

        FileUtils.EncodingResult result = FileUtils.detectEncodingDetailed(file);

        assertEquals("UTF-8", result.charsetName);
    }

    @Test
    public void detect_utf8SampleCutInsideMultibyte_doesNotFallBackToIbm866() throws Exception {
        // PRIMARY_SAMPLE_LIMIT is 192 KiB. Prefixing one ASCII byte makes that
        // sample end after two bytes of a three-byte Hangul character.
        File file = writeText("utf8_cut_boundary.txt", "A" + repeat("가", 70000), StandardCharsets.UTF_8);

        FileUtils.EncodingResult result = FileUtils.detectEncodingDetailed(file);

        assertEquals("UTF-8", result.charsetName);
        assertNotEquals("IBM866", result.charsetName);
    }

    @Test
    public void detect_koreanLegacyEucKr_normalizesToWindows949() throws Exception {
        Assume.assumeTrue(Charset.isSupported("EUC-KR"));
        File file = writeText("korean_euckr.txt",
                repeat("오래된 한국어 텍스트 파일 인코딩 검사입니다. 가나다라 마바사.\n", 400),
                Charset.forName("EUC-KR"));

        FileUtils.EncodingResult result = FileUtils.detectEncodingDetailed(file);

        assertEquals("windows-949", result.charsetName);
    }

    @Test
    public void detect_utf16LeBom_returnsUtf16Le() throws Exception {
        byte[] body = repeat("UTF-16 LE 한글 테스트입니다.\n", 80).getBytes(StandardCharsets.UTF_16LE);
        byte[] bytes = new byte[body.length + 2];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xFE;
        System.arraycopy(body, 0, bytes, 2, body.length);
        File file = writeBytes("utf16le_bom.txt", bytes);

        FileUtils.EncodingResult result = FileUtils.detectEncodingDetailed(file);

        assertEquals("UTF-16LE", result.charsetName);
    }

    @Test
    public void detect_utf16BeBom_returnsUtf16Be() throws Exception {
        byte[] body = repeat("UTF-16 BE 한글 테스트입니다.\n", 80).getBytes(StandardCharsets.UTF_16BE);
        byte[] bytes = new byte[body.length + 2];
        bytes[0] = (byte) 0xFE;
        bytes[1] = (byte) 0xFF;
        System.arraycopy(body, 0, bytes, 2, body.length);
        File file = writeBytes("utf16be_bom.txt", bytes);

        FileUtils.EncodingResult result = FileUtils.detectEncodingDetailed(file);

        assertEquals("UTF-16BE", result.charsetName);
    }

    @Test
    public void detect_bomlessUtf16Le_returnsUtf16Le() throws Exception {
        File file = writeBytes("bomless_utf16le.txt",
                repeat("BOM 없는 UTF-16LE 한글 테스트입니다.\n", 120).getBytes(StandardCharsets.UTF_16LE));

        FileUtils.EncodingResult result = FileUtils.detectEncodingDetailed(file);

        assertEquals("UTF-16LE", result.charsetName);
    }

    private File writeText(String name, String text, Charset charset) throws Exception {
        return writeBytes(name, text.getBytes(charset));
    }

    private File writeBytes(String name, byte[] bytes) throws Exception {
        File file = tempFolder.newFile(name);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(bytes);
        }
        return file;
    }

    private static String repeat(String value, int count) {
        StringBuilder sb = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) {
            sb.append(value);
        }
        return sb.toString();
    }
}
