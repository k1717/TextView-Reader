# Archive backend routing notes

This document summarizes the final public 2.2.6 archive routing policy. It is intentionally release-facing, not a pass-by-pass development log.

## Default routing policy

| Family | Primary route | Fallback / notes |
|---|---|---|
| ZIP / CBZ / ZIPX | Zip4j | Commons Compress for non-encrypted unsupported ZIP methods where bundled codecs can read them; libarchive only as a compatibility boundary where wired. |
| TAR family | Commons Compress | libarchive fallback may be attempted for covered cases. |
| Single compressor streams | Commons Compress | `.gz`, `.bz2`, `.xz`, `.lzma`, `.Z` as single-file decompression where covered. |
| 7z / CB7 | Java 7z path | Split/password cases are limited to covered backend behavior. |
| RAR / CBR | libarchive-android for common compressed attempts; first-party Java for metadata/stored paths | RAR support is limited and must not be advertised as complete. |
| ALZ / EGG | Limited first-party readers | See archive support matrix for supported method boundaries. |

## RAR routing policy

RAR is the main 2.2.6 licensing and compatibility boundary:

1. The default build does not include Junrar or RARLAB UnRAR-license fallback code.
2. First-party Java handles RAR metadata/safety checks and covered stored-entry/stored-split/stored-RAR5 paths.
3. Common compressed RAR3/RAR4 entries are attempted through bundled `libarchive-android`.
4. RAR5 compressed data is backend-dependent; there is no first-party RAR5 compressed decoder.
5. Split/multi-volume and encrypted RAR helpers are not public compatibility guarantees for this package.
6. Compressed-solid RAR, PPMd, VM-filtered payloads, broad SFX wrappers, compressed split chains, and unusual variants remain outside public support claims unless a specific file succeeds through the backend or a covered stored path.

## Failure behavior

Unsupported archive variants should fail cleanly with a scoped reason. Partial outputs should be cleaned up or restored when a guarded extraction path fails before commit.

Long backend failure details should be shown through the archive failure-detail dialog rather than only in a toast.
