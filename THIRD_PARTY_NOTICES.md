# Third-party notices

TextView Reader source code is licensed under the MIT License in `LICENSE`.
This file records third-party components that are used by the Android build or by the app at runtime. Keep this file with source releases and with binary release materials such as APK/AAB release assets.

## Runtime / app dependencies

### AndroidX libraries

Used artifacts:

- `androidx.appcompat:appcompat:1.6.1`
- `androidx.recyclerview:recyclerview:1.3.2`
- `androidx.constraintlayout:constraintlayout:2.1.4`
- `androidx.activity:activity:1.9.3`
- `androidx.drawerlayout:drawerlayout:1.2.0`

License: Apache License 2.0.

### Material Components for Android

Artifact: `com.google.android.material:material:1.12.0`

License: Apache License 2.0.

### Material-style vector icons

The project contains local vector drawable icons for standard UI actions such as menu, search, settings, bookmark, delete, sort, storage, and download. These are treated as Material-style icon assets. If any icon path is replaced with a copied upstream Material Symbols / Material Icons path, preserve the Apache License 2.0 attribution for that source.

Material Symbols / Material Icons are distributed by Google under the Apache License 2.0.


### Apache Commons Compress

Artifact: `org.apache.commons:commons-compress:1.28.0`

Use in this project: archive extraction support for 7z, TAR-family formats, numeric split archives that are reassembled before extraction, and single-file compressor streams such as GZ/BZ2/XZ/LZMA/Z.

License: Apache License 2.0.

### XZ for Java

Artifact: `org.tukaani:xz:1.10`

Use in this project: XZ/LZMA2 decompression support used by 7z and `.tar.xz` / `.txz` extraction.

License: 0BSD.


### Zip4j

Artifact: `net.lingala.zip4j:zip4j:2.11.5`

Use in this project: ZIP/CBZ extraction, encrypted ZIP handling, and standard split ZIP archive handling.

License: Apache License 2.0.

### JUniversalChardet

Artifact: `com.github.albfernandez:juniversalchardet:2.5.0`

Use in this project: Mozilla universalchardet-based helper in the TXT encoding decision pipeline.

License path used by this project: MPL-1.1.

Upstream license information lists JUniversalChardet as tri-licensed under MPL-1.1, GPL, and LGPL alternatives. This project should rely on the MPL-1.1 option rather than a GPL option so the app source can remain under MIT. Do not modify and vendor JUniversalChardet source files into this repository without preserving the applicable upstream notices and rechecking MPL file-level obligations.

## Test dependencies

### JUnit

Artifact: `junit:junit:4.13.2`

License: Eclipse Public License 1.0.

JUnit also depends on Hamcrest at test runtime in standard Gradle/Maven resolution. Keep its BSD-style notice if distributing a bundled test-runtime package. Normal source releases that only declare the Maven dependency do not vendor Hamcrest source or binaries.

### AndroidX Test

Used artifacts:

- `androidx.test:runner:1.6.2`
- `androidx.test.ext:junit:1.2.1`

Use in this project: Android instrumentation tests for TXT reader paging continuity.

License: Apache License 2.0.

## Build tooling

### Gradle wrapper / Gradle Build Tool

Files: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`

License: Apache License 2.0.

### Android Gradle Plugin

Artifact: `com.android.application` plugin version `9.1.1`

License: Android Software Development Kit License / Google Maven distribution terms for the Android build tooling. The plugin is resolved by Gradle and is not vendored in this repository.

### Gradle Foojay Toolchains Resolver Convention Plugin

Artifact: `org.gradle.toolchains.foojay-resolver-convention:org.gradle.toolchains.foojay-resolver-convention.gradle.plugin:1.0.0`

Use in this project: Java toolchain resolver configuration in `settings.gradle`. It is a build-time Gradle settings plugin used to resolve Java runtimes through the Foojay Disco API. It is not vendored in this repository.

License: Apache License 2.0.

Upstream project pages identify this plugin as a Gradle-owned Foojay toolchain resolver and point to Gradle's Foojay / Disco toolchains sources. Keep this notice if the plugin remains in `settings.gradle`.

## Release-distribution note

The Gradle packaging block may exclude duplicate `META-INF/LICENSE*` and `META-INF/NOTICE*` files from packaged Android resources to avoid resource merge conflicts. That does not remove the need to provide third-party notices with public binary releases. Include this file, the root `LICENSE`, and any generated dependency-license report if one is later added to the build.
