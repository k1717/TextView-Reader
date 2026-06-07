# TextView Reader 2.2.6 privacy/license cleanup audit

Audit target: public source package after the 2.2.6 privacy/license cleanup pass.

## Completed source/package changes

### Personal path and private-address cleanup

- Removed public release docs that contained pass-by-pass internal RAR handoff notes.
- Source/package grep check found no `<private Windows user path>` local path remnants.
- Source/package grep check found no previous personal Gmail handle or old anonaddy contact address.
- Developer contact remains `textview.ahnyb@addy.io`.

### Android backup and local data boundary

- `AndroidManifest.xml` sets `android:allowBackup="false"`.
- `PRIVACY.md` now documents that Android Auto Backup is not opted into by the app.
- Export/import already excludes PIN and lock-enabled state; that exclusion remains in `PrefsManager.isBackupExcludedKey()`.

### PIN storage

- New PIN values are stored as salted PBKDF2 verifier strings, not as plain PIN text.
- Legacy plain `lock_pin` values from older installs are migrated to the PBKDF2 verifier format after the first successful PIN verification.
- Unlock/change flows now call `PrefsManager.verifyLockPin(pin)` instead of comparing plain text.
- Clearing the lock removes the PIN verifier key.

### APK install permission

- Removed `android.permission.REQUEST_INSTALL_PACKAGES` from the default manifest.
- Disabled APK installer delegation in the default build.
- `FileUtils.isExternalOpenableFile()` now treats video files as external-openable, not APK files.
- README, privacy notes, patch notes, and changelog now say that the default build does not request APK-install permission or route APK files into Android's package installer.

### FileProvider / external app handoff

- `PRIVACY.md` documents that user-triggered open-with/share flows grant temporary read access through Android `FileProvider` and that the receiving app controls what happens after the user chooses it.

### Public documentation cleanup

- Pass-by-pass RAR internal notes are not present in the public docs set.
- Remaining docs are consolidated release-facing archive/FOSS/privacy/license notes.

### Third-party notices and SBOM artifacts

- `THIRD_PARTY_NOTICES.md` was tightened for libarchive-android/libarchive and zstd-jni native notice handling.
- Added `docs/LICENSE_REPORT_2_2_6.md`.
- Added `docs/SBOM_2_2_6.spdx.json`.

## Validation performed in this environment

The following checks passed:

```text
AndroidManifest.xml parsed as XML
SBOM_2_2_6.spdx.json parsed as JSON
No allowBackup true string found
No REQUEST_INSTALL_PACKAGES declaration in AndroidManifest.xml
No <private Windows user path> string found
No <old personal handle> string found
No old anonaddy contact string found
No direct direct plain-PIN SharedPreferences read/write literal literal plain-PIN storage calls remain
No LockActivity plain plain PIN equality comparison against a stored getter comparison remains
ZIP integrity checked after packaging
```

## Validation not completed here

- Gradle build/test was not completed in this environment because the Gradle wrapper attempted to download `https://services.gradle.org/distributions/gradle-9.4.1-bin.zip` and DNS/network access failed with `UnknownHostException: services.gradle.org`.
- APK string inspection was not completed because this source ZIP does not contain an APK/AAB and the APK could not be built here.
- The included SPDX file is a source-declared direct-dependency SBOM draft, not a Gradle-resolved transitive SBOM. A full resolved SBOM/license report should be regenerated from a network-enabled build machine before stricter repository submission.

## Remaining release-side checklist

Before publishing a binary APK/AAB release:

1. Build the release APK on a network-enabled/local Gradle environment.
2. Run unit/instrumentation tests if available.
3. Inspect the built APK/AAB for private paths, old email addresses, `allowBackup true`, `REQUEST_INSTALL_PACKAGES`, and unexpected secrets.
4. Regenerate a full resolved dependency license report/SBOM from the actual Gradle resolution output.
5. Keep `LICENSE`, `NOTICE`, `THIRD_PARTY_NOTICES.md`, `PRIVACY.md`, `docs/FOSS_STATUS.md`, `docs/LICENSE_REPORT_2_2_6.md`, and `docs/SBOM_2_2_6.spdx.json` with release materials.
