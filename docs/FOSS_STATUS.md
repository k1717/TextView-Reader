# FOSS status for TextView Reader 2.2.6

This note summarizes the default source package and default release APK status for communities that require Free and Open Source Software (FOSS), such as r/FOSSdroid.

## Current assessment

The default TextView Reader 2.2.6 source package is intended to fit the usual FOSS definition:

- the first-party source is licensed under Apache License 2.0;
- the source code is included in the repository/source package;
- the app can be run, copied, distributed, studied, changed, and improved under the applicable licenses;
- the default build does not include a proprietary app EULA;
- the default build no longer includes Junrar or other UnRAR-license fallback code;
- the source package does not include private signing files, keystores, build outputs, or bundled proprietary binary dependencies;
- the app manifest disables Android app-data Auto Backup with `android:allowBackup="false"` for local-data privacy consistency.

This is not a legal opinion, but it is the current project-level compliance position for the default 2.2.6 source/APK line.

## Default runtime dependency status

The default runtime dependency graph uses FOSS-compatible licenses:

| Component | Use | License status |
|---|---|---|
| TextView Reader first-party source | app code | Apache License 2.0 |
| AndroidX / Material Components | Android UI/runtime support | Apache License 2.0 |
| Apache Commons Compress | primary TAR/7z/stream archive support and ZIP method fallback | Apache License 2.0 |
| Zip4j | primary ZIP/CBZ handling plus password/AES-specialist handling | Apache License 2.0 |
| libarchive-android | default RAR read/extract backend plus ZIP/TAR/7z compatibility fallback | Android library artifact under Apache License 2.0; bundled native libarchive is BSD-style |
| zstd-jni | Zstandard codec used by Commons Compress | JNI binding under BSD 2-Clause; native Zstandard under the permissive BSD licensing path |
| XZ for Java | XZ/LZMA support | 0BSD |
| JUniversalChardet | text encoding detection | MPL-1.1 option used by this project |
| xunazo-derived AZO decoder port | EGG AZO extraction | zlib license notice retained in source |

## RAR/CBR licensing boundary

RAR/CBR support is deliberately extraction-only.

TextView Reader 2.2.6 does **not** bundle Junrar or RARLAB UnRAR-license code in the default build. The old Junrar fallback was removed because UnRAR-style license restrictions can conflict with FOSS-focused distribution expectations.

The default RAR path is now:

1. bundled libarchive-android as the default RAR read/extract backend;
2. first-party Java metadata/stored-entry handling where covered;
3. first-party RAR5 stored-entry handling where covered;
4. dedicated Java ZIP/TAR/7z readers as their primary paths, with libarchive retained as fallback;
5. no optional unrar5j RAR5 bridge in the default source tree; unsupported cases fail cleanly.

`junrar/commons-vfs-rar` was reviewed and rejected because it depends on `com.github.junrar:junrar`, which would reintroduce the same Junrar/UnRAR-license concern.

## Optional local jars

The default source package contains no optional decoder jar under `app/libs`, and no optional RAR decoder bridge is wired into the default archive stack. In a clean source package the `app/libs` folder may be absent.

`app/build.gradle` still has a local `app/libs/*.jar` hook so a developer can test an optional RAR5 decoder locally. If any jar is added to `app/libs`, that custom build must be treated as a separate build and its license must be rechecked before calling the resulting APK FOSS.

The default GitHub source package and default release APK should be evaluated without optional local jars unless the release notes explicitly say otherwise.

## Binary release notes

When distributing APK/AAB files, keep these files available alongside the binary release materials:

- `LICENSE`
- `NOTICE`
- `THIRD_PARTY_NOTICES.md`
- `PRIVACY.md`
- `docs/LICENSE_REPORT_2_2_6.md`
- `docs/SBOM_2_2_6.spdx.json`
- this `docs/FOSS_STATUS.md` note

The Gradle packaging block excludes duplicate dependency `META-INF/LICENSE*` / `META-INF/NOTICE*` resources to avoid Android packaging conflicts. That does not remove the release obligation to provide the project-level license and third-party notices with the source and binary release materials.

## Current caveats

- RAR support is not complete. Common compressed RAR entries are attempted through libarchive-android by default, with first-party Java used for stored-entry fallback and metadata/safety handling. Split/multi-volume RAR and encrypted RAR are not guaranteed in the current GitHub-ready package because they have not been re-tested for this release. Broad compressed-solid/header-encrypted/SFX/unusual RAR variants are not guaranteed. ZIP/TAR/7z keep dedicated Java readers first, with libarchive retained for fallback and special cases.
- Optional local jars are outside the default FOSS assessment unless explicitly audited and documented. No optional RAR decoder jar is wired into the default archive stack.
- Android Gradle Plugin, Android SDK, and Gradle tooling are build-time tools resolved from their normal upstream channels; they are not vendored into the app source package.
- This package includes a direct-dependency license report (`docs/LICENSE_REPORT_2_2_6.md`) and a source-declared direct-dependency SPDX draft (`docs/SBOM_2_2_6.spdx.json`). They are not a Gradle-resolved transitive SBOM. For stricter distribution channels, regenerate a full resolved dependency report/SBOM from a network-enabled build environment before submission.
