# RAR5 library integration notes

## Current bundled boundary

TextView Reader 2.2.5 now has two RAR5 paths:

- The first-party reader still handles metadata listing, safe paths, stored method-0 extraction, stored split assembly for supported stored entries, and the limited RAR5 encrypted stored-data decrypt attempt.
- A new reflection-based `Rar5LibraryFallback` can delegate compressed RAR5 listing and extraction to RealBurst/unrar5j when `app/libs/unrar5j-v1.0.3.jar` is present at build/runtime.

The fallback remains optional. If the jar is absent, compressed RAR5 still fails cleanly with an unsupported-feature message instead of writing partial output.

## Optional decoder selection

`RealBurst/unrar5j` is the optional RAR5 decoder used by the reflection bridge in this source package.

Observed fit:

- Pure Java implementation, so no native binary/JNI packaging step is required.
- Apache-2.0 license, matching the project-level Apache-2.0 direction.
- Java 8+ and no external dependencies.
- Library API exposes `Unrar5j.extract(...)` for archive extraction and `Rar5Reader` / `Rar5FileBlock` for entry listing.
- Upstream documents RAR5 compression, AES/PBKDF2 encryption, encrypted headers/file names, filters, solid archives, and CRC verification.

Integration caveats:

- The jar is not a normal Maven Central dependency, so it is kept as an optional local jar under `app/libs`.
- The Gradle helper task `:app:fetchUnrar5jJar` downloads `unrar5j-v1.0.3.jar` when network access is available.
- Multi-volume RAR5 remains an upstream partial-support area, so split RAR5 should still be treated as not guaranteed until fixture tests prove the exact behavior.
- RAR creation/compression remains unsupported. This path is extraction-only.

## Implemented bridge behavior

`RarArchiveReader` now routes RAR5 compressed/solid/encrypted cases to `Rar5LibraryFallback` when first-party method-0 extraction is not enough. The bridge:

1. Loads `be.stef.rar5.Unrar5j` and `be.stef.rar5.Rar5Reader` reflectively.
2. Lists RAR5 entries through `Rar5Reader.getFileBlocks()` when available.
3. Extracts full archives through `Unrar5j.extract(archivePath, outputDir, password)`.
4. Extracts a single requested entry through `Unrar5j.extract(archivePath, outputDir, password, fileFilter)`.
5. Uses a temporary directory for single-entry extraction, then moves only the requested output file to the caller-provided destination.
6. Preserves path traversal checks and synthetic folder entries on the TextView Reader side.

## Fixture work still needed

Before widening public support claims beyond the current optional-fallback boundary, test at least:

- normal single-volume compressed RAR5
- password-protected RAR5 with visible headers
- encrypted-header RAR5
- solid RAR5 with single-entry preview from a later file
- split/multi-volume RAR5, especially where the selected file starts in a later volume
- malformed/path-traversal RAR5 names
