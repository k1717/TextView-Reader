# 7z / CB7 Split Volume Notes

TextView Reader handles standard 7-Zip split volume names as one logical archive:

- `name.7z.001`, `name.7z.002`, `name.7z.003`, ...
- `name.cb7.001`, `name.cb7.002`, `name.cb7.003`, ...

Selecting any numbered part resolves back to the `.001` first volume. The resolver then collects the contiguous sibling parts and opens them through Apache Commons Compress `MultiReadOnlySeekableByteChannel`, so listing, full extraction, and single-entry preview extraction all use the same ordered stream without creating a temporary fully concatenated archive file.

Rejected cases:

- `.7z.002` or later selected when `.7z.001` is missing.
- Gapped chains where a later part exists after a missing part, such as `.001`, `.002`, `.004`.
- Unsupported 7z compression/encryption methods outside Commons Compress coverage.
- Split-volume creation.

This support is separate from RAR split handling and from generic `.001` reassembly used by other archive families.
