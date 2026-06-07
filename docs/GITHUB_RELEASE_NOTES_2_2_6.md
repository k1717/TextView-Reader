# TextView Reader 2.2.6 GitHub release notes

TextView Reader 2.2.6 is the current FOSS-oriented archive-support line. The default build removes Junrar/UnRAR-license fallback code and uses first-party Java readers plus bundled FOSS-compatible libraries.

## Highlights

- ZIP/CBZ remains on Zip4j, with Commons Compress fallback for covered non-encrypted special methods.
- TAR/CBT, TAR.GZ, TAR.BZ2, TAR.XZ, TAR.LZMA, TAR.Z and single-stream GZ/BZ2/XZ/LZMA/Z use Apache Commons Compress and XZ for Java.
- 7z/CB7 uses Apache Commons Compress, with standard `.7z.001` / `.cb7.001` split chain handling where covered.
- RAR/CBR no longer uses Junrar. Stored RAR entries are handled by first-party Java where covered, and compressed RAR is attempted through bundled libarchive-android.
- RAR5 archive extraction can fall back from whole-archive extraction to per-entry extraction, matching the preview path for cases where individual entries can still be opened.
- ALZ and EGG use first-party readers for covered Store/Deflate/BZip2/AZO/LZMA-style paths.

## RAR support wording

Use conservative wording:

> RAR/CBR support is limited. Stored entries are handled by first-party Java where covered, and compressed entries are attempted through bundled libarchive-android. Split/multi-volume RAR and encrypted RAR are not guaranteed in this release package because they have not been re-tested for this GitHub-ready build.

Do not claim:

- complete RAR support;
- guaranteed split/multi-volume RAR support;
- guaranteed encrypted RAR support;
- first-party compressed RAR5 decoding;
- broad solid RAR support;
- support for every SFX executable wrapper.

## Known archive limits

- No RAR/7z/ALZ/EGG creation.
- ZIP creation is plain ZIP only.
- Encrypted ZIPX entries using uncommon methods such as XZ/BZip2/ZSTD remain unsupported in current routing.
- RAR PPMd, custom VM bytecode, broad solid archives, broad encrypted compressed variants, RAR5 encrypted headers, and generic executable/SFX compatibility remain limited or unsupported unless a specific file works through the bundled backend.
- EGG encrypted/split/solid archives are unsupported.

## Privacy / local-data note

- The default manifest has no `INTERNET` permission.
- Android app-data Auto Backup is disabled with `android:allowBackup="false"`.
- Settings uses a static copyable GitHub releases URL and a mailto-only developer contact button; TextView Reader does not send update checks, logs, files, bookmarks, history, or settings by itself.

## Files to include with release artifacts

- `LICENSE`
- `NOTICE`
- `THIRD_PARTY_NOTICES.md`
- `PRIVACY.md`
- `README.md`
- `docs/FOSS_STATUS.md`
- `docs/ARCHIVE_SUPPORT_MATRIX_2_2_6.md`
- `docs/RAR_STATUS_2_2_6.md`
