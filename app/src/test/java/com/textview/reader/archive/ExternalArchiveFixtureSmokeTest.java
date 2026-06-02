package com.textview.reader.archive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ExternalArchiveFixtureSmokeTest {
    private static final char[] PASSWORD = "password".toCharArray();

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void passwordZipFixture_listsAndExtractsWithPassword() throws Exception {
        File root = fixtureRoot();
        assertPasswordArchiveWorks(new File(root, "zip_sample_password.zip"));
    }

    @Test
    public void password7zFixture_listsAndExtractsWithPassword() throws Exception {
        File root = fixtureRoot();
        assertPasswordArchiveWorks(new File(root, "7z_sample_password.7z"));
    }

    @Test
    public void passwordZipxFixture_listsAndReportsUnsupportedExtraction() throws Exception {
        File root = fixtureRoot();
        File archive = new File(root, "zipx_sample_password.zipx");
        assumeTrue("Missing fixture: " + archive.getAbsolutePath(), archive.isFile());
        assertTrue("Not supported: " + archive.getName(), ArchiveSupport.isSupportedArchive(archive));
        assertTrue("Password was not detected for " + archive.getName(),
                ArchiveSupport.requiresPasswordForExtraction(archive));

        List<ArchiveSupport.EntryInfo> entries = ArchiveSupport.listEntries(archive, PASSWORD);
        ArchiveSupport.EntryInfo entry = firstFileEntry(entries);
        File out = tempFolder.newFile("zipx-entry.out");
        ArchiveSupport.ExtractionResult result = ArchiveSupport.extractSingleEntryDetailed(
                archive,
                entry.path,
                out,
                PASSWORD);

        assertFalse(result.success);
        assertEquals(ArchiveSupport.ExtractionFailure.UNSUPPORTED_FEATURE, result.failure);
        assertFalse(out.exists());
    }

    @Test
    public void rarTestFilesFixtures_matchCurrentSupportBoundary() throws Exception {
        File root = fixtureRoot();
        File bundle = new File(root, "rar-test-files-master.zip");
        assumeTrue(bundle.isFile());

        File rarRoot = extractRarFixtures(bundle);
        File build = new File(rarRoot, "rar-test-files-master/build");
        File rar5 = new File(build, "testfile.rar5.rar");
        File rar5Cbr = new File(build, "testfile.rar5.cbr");
        File rar3 = new File(build, "testfile.rar3.rar");
        File rar5Solid = new File(build, "testfile.rar5.solid.rar");

        assertTrue(ArchiveSupport.isSupportedArchive(rar5));
        assertTrue(ArchiveSupport.isSupportedArchive(rar5Cbr));
        assertTrue(ArchiveSupport.isSupportedArchive(rar3));
        assertTrue(ArchiveSupport.isSupportedArchive(rar5Solid));

        assertFalse(ArchiveSupport.listEntries(rar5, null).isEmpty());
        assertFalse(ArchiveSupport.listEntries(rar5Cbr, null).isEmpty());
        assertFalse(ArchiveSupport.listEntries(rar3, null).isEmpty());
        assertFalse(ArchiveSupport.listEntries(rar5Solid, null).isEmpty());

        File storedPng = tempFolder.newFile("rar-cbr-stored.png");
        assertTrue(ArchiveSupport.extractSingleEntry(rar5Cbr, "testfile.png", storedPng, null));
        assertTrue(storedPng.isFile());
        assertTrue(Files.size(storedPng.toPath()) > 0L);

        File compressedJpg = tempFolder.newFile("rar5-cbr-compressed.jpg");
        ArchiveSupport.ExtractionResult rar5Compressed = ArchiveSupport.extractSingleEntryDetailed(
                rar5Cbr,
                "testfile.jpg",
                compressedJpg,
                null);
        assertFalse(rar5Compressed.success);
        assertEquals(ArchiveSupport.ExtractionFailure.UNSUPPORTED_FEATURE, rar5Compressed.failure);
        assertFalse(compressedJpg.exists());

        File rar3Text = tempFolder.newFile("rar3-compressed.txt");
        assertTrue(ArchiveSupport.extractSingleEntry(rar3, "testfile.txt", rar3Text, null));
        assertTrue(rar3Text.isFile());
        assertTrue(Files.size(rar3Text.toPath()) > 0L);
    }

    private void assertPasswordArchiveWorks(File archive) throws Exception {
        assumeTrue("Missing fixture: " + archive.getAbsolutePath(), archive.isFile());
        assertTrue("Not supported: " + archive.getName(), ArchiveSupport.isSupportedArchive(archive));
        assertTrue("Password was not detected for " + archive.getName(),
                ArchiveSupport.requiresPasswordForExtraction(archive));

        List<ArchiveSupport.EntryInfo> entries = ArchiveSupport.listEntries(archive, PASSWORD);
        ArchiveSupport.EntryInfo entry = firstFileEntry(entries);
        File out = tempFolder.newFile(archive.getName().replaceAll("[^A-Za-z0-9_.-]", "_") + ".out");

        ArchiveSupport.ExtractionResult result = ArchiveSupport.extractSingleEntryDetailed(
                archive,
                entry.path,
                out,
                PASSWORD);

        assertTrue("Extraction failed for " + archive.getName() + ": " + result.failure + " " + result.detail,
                result.success);
        assertTrue(out.isFile());
        assertTrue(Files.size(out.toPath()) > 0L);
    }

    private ArchiveSupport.EntryInfo firstFileEntry(List<ArchiveSupport.EntryInfo> entries) {
        assertNotNull(entries);
        for (ArchiveSupport.EntryInfo entry : entries) {
            if (entry != null && !entry.directory) return entry;
        }
        throw new AssertionError("No file entry found");
    }

    private File fixtureRoot() {
        String path = System.getProperty("textview.externalArchiveFixtureDir");
        if (path == null || path.trim().length() == 0) {
            path = System.getenv("TEXTVIEW_EXTERNAL_ARCHIVE_FIXTURE_DIR");
        }
        assumeTrue("External archive fixture dir not provided", path != null && path.trim().length() > 0);
        File root = new File(path);
        assumeTrue("External archive fixture dir missing: " + root.getAbsolutePath(), root.isDirectory());
        return root;
    }

    private File extractRarFixtures(File bundle) throws Exception {
        File target = tempFolder.newFolder("rar-fixtures");
        ArrayList<String> wantedPrefixes = new ArrayList<>();
        wantedPrefixes.add("rar-test-files-master/build/testfile.rar5.rar");
        wantedPrefixes.add("rar-test-files-master/build/testfile.rar5.cbr");
        wantedPrefixes.add("rar-test-files-master/build/testfile.rar3.rar");
        wantedPrefixes.add("rar-test-files-master/build/testfile.rar5.solid.rar");
        try (ZipFile zip = new ZipFile(bundle)) {
            for (String name : wantedPrefixes) {
                ZipEntry entry = zip.getEntry(name);
                assertNotNull("Missing nested RAR fixture: " + name, entry);
                File out = new File(target, name);
                File parent = out.getParentFile();
                assertNotNull(parent);
                assertTrue(parent.exists() || parent.mkdirs());
                try (InputStream in = zip.getInputStream(entry);
                     FileOutputStream fileOut = new FileOutputStream(out)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) fileOut.write(buffer, 0, read);
                }
            }
        }
        return target;
    }
}
