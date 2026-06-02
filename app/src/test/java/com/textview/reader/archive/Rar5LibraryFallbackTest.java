package com.textview.reader.archive;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class Rar5LibraryFallbackTest {
    @Test
    public void sanitizeEntryPathRejectsTraversalAndAbsolutePaths() {
        assertNull(Rar5LibraryFallback.sanitizeEntryPath("../escape.txt"));
        assertNull(Rar5LibraryFallback.sanitizeEntryPath("safe/../../escape.txt"));
        assertNull(Rar5LibraryFallback.sanitizeEntryPath("/absolute/path.txt"));
        assertNull(Rar5LibraryFallback.sanitizeEntryPath("C:/absolute/path.txt"));
        assertNull(Rar5LibraryFallback.sanitizeEntryPath("C:\\absolute\\path.txt"));
    }

    @Test
    public void sanitizeEntryPathNormalizesSafeRelativePaths() {
        assertEquals("folder/file.txt", Rar5LibraryFallback.sanitizeEntryPath("./folder//file.txt"));
        assertEquals("folder/sub/file.txt", Rar5LibraryFallback.sanitizeEntryPath("folder\\sub\\file.txt"));
        assertEquals("folder/", Rar5LibraryFallback.sanitizeEntryPath("folder/"));
    }
}
