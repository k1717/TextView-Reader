# ZIP Commons fallback notes

TextView Reader keeps Zip4j as the primary production ZIP/CBZ path. Zip4j remains responsible for ordinary ZIP/CBZ listing and extraction, encrypted ZIP entries, and standard split ZIP archives.

2.2.5 adds a fallback only for non-encrypted ZIP entries that Zip4j rejects because of an unsupported compression method. In that case, `ArchiveSupport` retries extraction through Apache Commons Compress `org.apache.commons.compress.archivers.zip.ZipFile`. With Commons Compress 1.28.0 plus the bundled XZ for Java and zstd-jni dependencies, this covers methods Commons Compress can actually decode, notably Deflate64, BZip2, XZ, and ZSTD, and applies to both full-archive extraction and single-entry archive-preview extraction.

The fallback is deliberately not used for encrypted ZIP entries. AES/password handling stays on Zip4j. Entries that combine AES with a non-Zip4j compression method remain unsupported because no bundled path provides both decryption and that codec together.

Unsupported methods and native-codec load failures should fail cleanly. The code checks `canReadEntryData()` and still catches `LinkageError` defensively. ZSTD is normally available because zstd-jni is bundled, but an ABI-specific native load failure should be reported as unsupported rather than crashing. AES+XZ remains unsupported because encrypted entries stay on Zip4j and Commons Compress does not supply the AES path for that combination.

Licensing note: this fallback does not introduce a new third-party dependency. It broadens the runtime use of the already declared `org.apache.commons:commons-compress:1.28.0` dependency, which is Apache License 2.0 and is listed in `THIRD_PARTY_NOTICES.md`.

## Release minification note

Commons Compress references optional `com.github.luben.zstd.*` classes for Zstandard streams. The app now bundles `zstd-jni`, so release builds no longer need a Zstandard missing-class suppression. Runtime extraction still catches codec linkage failures and returns a clean unsupported result if an ABI-specific native load fails.
