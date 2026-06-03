# Archive Format Validation Notes

This note records the current validation boundary for non-ZIP archive families in the 2.2.5 line.

## TAR family

The code maps the five TAR-family variants as follows:

- `.tar` / `.cbt` -> raw tar parsing
- `.tar.gz` / `.tgz` -> gzip stream wrapper, then tar parsing
- `.tar.bz2` / `.tbz2` -> bzip2 stream wrapper, then tar parsing
- `.tar.xz` / `.txz` -> XZ stream wrapper, then tar parsing
- `.tar.lzma` / `.tlz` -> LZMA stream wrapper, then tar parsing
- `.tar.Z` / `.taz` -> Unix compress stream wrapper, then tar parsing

Sample magic/header checks and host-side decompression simulations are useful for validating the mapping, but Android Java end-to-end fixtures should remain in release QA.

## Single compressor streams

The code maps `.gz`, `.bz2`, `.xz`, `.lzma`, and `.Z` to the corresponding Commons Compress single-stream decoders. `.gz`, `.bz2`, `.xz`, and `.lzma` sample payloads have been checked against the expected decoder mapping. `.Z` remains mapped in code but needs a real compress-format fixture when available.

## ALZ

`AlzipArchiveReader` is first-party parsing code. Synthetic ALZ Store/Deflate fixtures validate local-header parsing, multi-entry/subdirectory handling, output bytes, CRC mismatch rejection, and corrupt-output cleanup. This is useful coverage for the implemented reader, but broad compatibility still requires real ESTsoft-created ALZ fixtures, especially BZip2, encrypted, descriptor-heavy, split, and legacy variants.

## EGG

`EggArchiveReader` supports Store/Deflate/BZip2/AZO/LZMA paths in code and validates block CRCs. Encrypted, split, and solid EGG archives remain unsupported. Real ESTsoft-created EGG fixtures should remain part of release QA before claiming broad compatibility.
