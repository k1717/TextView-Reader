# Third-party notices

TextView Reader first-party source code is licensed under the Apache License 2.0 in `LICENSE`. The root `LICENSE` file includes the applied first-party copyright notice, and the root `NOTICE` file is part of the Apache-2.0 source distribution.
This file records third-party components that are used by the Android build or by the app at runtime. Keep this file with source releases and with binary release materials such as APK/AAB release assets.

## Runtime / app dependencies

### Android platform APIs

TXT text-to-speech in 2.2.2 uses Android platform TTS, foreground-service notification, and media-button APIs. The app does not bundle a third-party TTS engine or voice model; user-installed Android TTS voices remain managed by the device TTS engine and system settings.

### RAR / CBR support boundary

The RAR/CBR reader parses RAR5 and RAR4/RAR3-style archive metadata in this source tree, decodes RAR4 Unicode filename records, extracts entries stored without compression, assembles stored split payloads, and includes a limited first-party RAR5 encrypted stored-data decrypt attempt. Junrar is additionally bundled as a RAR extraction-only fallback for older RAR4/RAR3 compressed, solid, split/multi-volume, and encrypted cases. RAR5 compressed/solid/encrypted extraction can be enabled by adding the optional RealBurst/unrar5j jar to `app/libs`; without that jar, compressed RAR5 remains unsupported in the bundled decoder.

### Junrar

Artifact: `com.github.junrar:junrar:7.6.0`

Use in this project: RAR extraction-only fallback for older RAR4/RAR3 compressed, solid, split/multi-volume, and encrypted entries. This code path is not used for RAR creation, RAR compression, or building a WinRAR-compatible archiver.

License: UnRAR License. The UnRAR license permits use and distribution for processing RAR archives, but prohibits using the source to develop a RAR-compatible compression/archiving product.

### RealBurst/unrar5j optional RAR5 fallback

Project: https://github.com/RealBurst/unrar5j

Use in this project: optional local-jar, extraction-only RAR5 fallback loaded reflectively by `Rar5LibraryFallback` when `app/libs/unrar5j-v1.0.3.jar` is present. It is not required for normal builds and is not used for RAR creation/compression. RAR5 split/multi-volume support is not treated as guaranteed.

License: Apache License 2.0.

### First-party ALZ / EGG support boundary

ALZ/EGG archive names are recognized by the app's archive type system. This package does not bundle ALZip, the UnEGG binary module, or any other third-party ALZ/EGG decoder; the readers are first-party code.

The ALZ path parses local headers and extracts Store, Deflate, and BZip2 entries (BZip2 via Apache Commons Compress), including ZipCrypto for the covered encrypted cases, with CRC verification.

The EGG container reader (`EggArchiveReader`) is first-party Java code. It implements publicly documented EGG container concepts and interoperability behavior for read/extraction use only. No ESTsoft source files, unEGG source files, or UnEGG binary module are copied or bundled in this repository. The decoder handles Store/Deflate/BZip2/AZO/LZMA, verifies each extracted block's CRC32, and sanitizes entry paths against directory traversal. Encrypted, split, and solid EGG archives are reported as unsupported. The decoder is not used to create EGG archives.

### First-party lightweight ZIP / CBZ path

Plain non-encrypted ZIP/CBZ archives can be listed and extracted by the experimental source code path in this project using Android/JDK ZIP primitives. Production ZIP/CBZ routing remains on Zip4j until the first-party path consistently outperforms it. Encrypted ZIP and standard split ZIP remain routed through Zip4j for compatibility.

### AZO decoder notice

This release includes `AzoDecoder.java`, an extraction-only modified Java port of the `kippler/xunazo` AZO decoder for EGG method-3 payloads. The upstream xunazo project is distributed under the zlib license. The Java port is marked as modified and is used only for extraction; it is not used to create EGG/AZO archives.

Required xunazo notice is retained in the source file and summarized here:

- Copyright (C) 2018 kippler@gmail.com
- Software is provided as-is, without express or implied warranty.
- The origin of the software must not be misrepresented.
- Altered source versions must be plainly marked as altered.
- The notice must not be removed or altered from source distributions.

### AndroidX libraries

Used artifacts:

- `androidx.appcompat:appcompat:1.7.1`
- `androidx.recyclerview:recyclerview:1.4.0`
- `androidx.constraintlayout:constraintlayout:2.2.1`
- `androidx.activity:activity:1.10.1`
- `androidx.drawerlayout:drawerlayout:1.2.0`

License: Apache License 2.0.

### Material Components for Android

Artifact: `com.google.android.material:material:1.14.0`

License: Apache License 2.0.

### Material-style vector icons

The project contains local vector drawable icons for standard UI actions such as menu, search, settings, bookmark, delete, sort, storage, and download. These are treated as Material-style icon assets. If any icon path is replaced with a copied upstream Material Symbols / Material Icons path, preserve the Apache License 2.0 attribution for that source.

Material Symbols / Material Icons are distributed by Google under the Apache License 2.0.


### Apache Commons Compress

Artifact: `org.apache.commons:commons-compress:1.28.0`

Use in this project: archive extraction support for 7z, standard 7z/CB7 split-volume chains through a concatenated seekable channel, TAR-family formats, numeric split archives that are reassembled before extraction where appropriate, and single-file compressor streams such as GZ/BZ2/XZ/LZMA/Z.

License: Apache License 2.0.

### XZ for Java

Artifact: `org.tukaani:xz:1.12`

Use in this project: XZ/LZMA2 decompression support used by 7z and `.tar.xz` / `.txz` extraction.

License: 0BSD.


### Zip4j

Artifact: `net.lingala.zip4j:zip4j:2.11.6`

Use in this project: ZIP/CBZ extraction, encrypted ZIP handling, and standard split ZIP archive handling.

License: Apache License 2.0.

### JUniversalChardet

Artifact: `com.github.albfernandez:juniversalchardet:2.5.0`

Use in this project: Mozilla universalchardet-based helper in the TXT encoding decision pipeline.

License path used by this project: MPL-1.1.

Upstream license information lists JUniversalChardet as tri-licensed under MPL-1.1, GPL, and LGPL alternatives. This project should rely on the MPL-1.1 option rather than a GPL option so the app source can remain under Apache-2.0. Do not modify and vendor JUniversalChardet source files into this repository without preserving the applicable upstream notices and rechecking MPL file-level obligations.

## Test dependencies

### JUnit

Artifact: `junit:junit:4.13.2`

License: Eclipse Public License 1.0.

JUnit also depends on Hamcrest at test runtime in standard Gradle/Maven resolution. Keep its BSD-style notice if distributing a bundled test-runtime package. Normal source releases that only declare the Maven dependency do not vendor Hamcrest source or binaries.

### AndroidX Test

Used artifacts:

- `androidx.test:runner:1.7.0`
- `androidx.test.ext:junit:1.3.0`

Use in this project: Android instrumentation tests for TXT reader paging continuity.

License: Apache License 2.0.

## Build tooling

### Gradle wrapper / Gradle Build Tool

Files: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`

License: Apache License 2.0.

### Android Gradle Plugin

Artifact: `com.android.application` plugin version `9.2.0`

License: Android Software Development Kit License / Google Maven distribution terms for the Android build tooling. The plugin is resolved by Gradle and is not vendored in this repository.

### Gradle Foojay Toolchains Resolver Convention Plugin

Artifact: `org.gradle.toolchains.foojay-resolver-convention` plugin version `1.0.0`

Use in this project: Gradle Java toolchain resolver configuration for local builds.

License: Apache License 2.0. The plugin is resolved by Gradle at build time and is not vendored in this repository.

## Release-distribution note

The Gradle packaging block may exclude duplicate `META-INF/LICENSE*` and `META-INF/NOTICE*` files from packaged Android resources to avoid resource merge conflicts. That does not remove the need to provide third-party notices with public binary releases. Include this file, the root `LICENSE`, and any generated dependency-license report if one is later added to the build.

