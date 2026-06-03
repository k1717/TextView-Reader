# Dependency update notes

This package refreshes build/runtime/test modules without changing Android SDK metadata or app release metadata.

## Updated

| Component | Old | New | Reason |
|---|---:|---:|---|
| Android Gradle Plugin | 9.1.1 | 9.2.0 | Stable AGP line selected for this pass. |
| Gradle wrapper | 9.3.1 | 9.4.1 | AGP 9.2 documents Gradle 9.4.1 as its baseline. |
| AppCompat | 1.6.1 | 1.7.1 | Stable AndroidX refresh. |
| Material Components | 1.12.0 | 1.14.0 | Stable Material Components refresh. |
| RecyclerView | 1.3.2 | 1.4.0 | Stable AndroidX refresh. |
| ConstraintLayout | 2.1.4 | 2.2.1 | Stable AndroidX refresh. |
| Activity | 1.9.3 | 1.10.1 | SDK-35-compatible AndroidX refresh; avoids the Activity 1.13.x API-36 metadata requirement. |
| XZ for Java | 1.10 | 1.12 | Archive decompression dependency refresh. |
| Zip4j | 2.11.5 | 2.11.6 | ZIP handling dependency refresh. |
| zstd-jni | not bundled | 1.5.7-9 | Enables Commons Compress Zstandard streams and non-encrypted ZIP method-93 fallback in release builds. |
| AndroidX Test Runner | 1.6.2 | 1.7.0 | Instrumentation test dependency refresh. |
| AndroidX Test Ext JUnit | 1.2.1 | 1.3.0 | Instrumentation test dependency refresh. |

## Kept unchanged

| Component | Version | Reason |
|---|---:|---|
| Commons Compress | 1.28.0 | Already current in the checked dependency set. |
| Junrar | 7.6.0 | Already current in the checked dependency set; still RAR4-and-lower fallback only. |
| JUniversalChardet | 2.5.0 | Already current in the checked dependency set. |
| DrawerLayout | 1.2.0 | Already latest stable. |
| JUnit 4 | 4.13.2 | JUnit 4 line intentionally retained for existing unit tests. |

## Not changed in this pass

- `compileSdk 35`
- `targetSdk 35`
- `minSdk 24`
- `versionCode 2250`
- `versionName "2.2.5"`

Those values should be changed only in a separate SDK/Play-target migration pass.


## Build-fix correction after local validation

The initial module refresh used `androidx.activity:activity:1.13.0`, but local release validation showed that Activity 1.13.x pulls AndroidX artifacts requiring `compileSdk 36+`. Since the 2.2.5 release line intentionally keeps `compileSdk 35` and `targetSdk 35`, Activity is pinned to `1.10.1`. This keeps the module refresh within the Android 15 / API 35 build target while avoiding `checkReleaseAarMetadata` failure.

`gradle.properties` was also cleaned by removing deprecated AGP compatibility toggles scheduled for removal in AGP 10 and replacing the deprecated library-constraint flag with `android.dependency.useConstraints=false`.

## AGP library-constraint flag cleanup

Local release validation showed that `android.dependency.excludeLibraryComponentsFromConstraints=true` is deprecated under the current Android Gradle Plugin line. The project now uses `android.dependency.useConstraints=false`, which is the replacement suggested by the build warning, so the Gradle 10 removal warning is avoided.

## 2.2.5 ZIP Commons fallback note

The ZIP fallback introduced in 2.2.5 expands the runtime use of the existing `org.apache.commons:commons-compress:1.28.0` artifact already present for 7z/TAR/single-stream archive handling. `THIRD_PARTY_NOTICES.md` was updated so the Commons Compress use description also covers this ZIP fallback path.

## ZIP fallback codec boundary

The ZIP Commons fallback uses Apache Commons Compress for non-encrypted ZIP entries that Zip4j cannot decode. Non-encrypted XZ ZIP entries are covered through the bundled XZ for Java dependency. ZSTD ZIP entries require Commons Compress plus `com.github.luben:zstd-jni`, which is now bundled in 2.2.5.

ZSTD note: Commons Compress references `com.github.luben.zstd.*` classes for Zstandard support. TextView Reader now bundles `com.github.luben:zstd-jni:1.5.7-9`, so release builds do not need a Zstandard missing-class `dontwarn` rule and non-encrypted ZSTD ZIP entries can be attempted by the Commons fallback.
