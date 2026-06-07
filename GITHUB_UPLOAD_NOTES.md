# TextView Reader 2.2.6 GitHub Upload Notes

Use this package as the GitHub source upload for **TextView Reader 2.2.6**.

## Release metadata

- `versionCode 2260`
- `versionName "2.2.6"`
- Package ID: `com.textview.reader`
- Project license: Apache License 2.0
- First-party copyright: Copyright 2026 k1717 aka Delphinium
- Required root release files: `LICENSE`, `NOTICE`, `THIRD_PARTY_NOTICES.md`, `docs/LICENSE_REPORT_2_2_6.md`, `docs/SBOM_2_2_6.spdx.json`, `docs/PRIVACY_LICENSE_AUDIT_2_2_6.md`, `PRIVACY.md`, `CHANGELOG.md`, `PATCHNOTES.md`, `README.md`

## Suggested release title

`TextView Reader 2.2.6`

## Suggested short release description

TextView Reader 2.2.6 is the FOSS-focused RAR backend and public-release cleanup build. The default build removes Junrar/UnRAR-license fallback code, uses bundled libarchive-android for common compressed RAR attempts, keeps stored-RAR first-party handling, and disables Android app-data Auto Backup for local privacy consistency.

## Release highlights

- Removed Junrar/UnRAR-license fallback code from the default build.
- Added bundled `me.zhanghai.android.libarchive:library:1.1.6` as the default compressed RAR backend attempt path.
- Kept first-party RAR metadata listing, safe path handling, stored method-0/0x30 extraction, covered stored split handling, and covered RAR5 stored-entry handling.
- Kept RAR/CBR wording conservative: compressed RAR is backend-dependent, and split/encrypted/solid/SFX/RAR5-compressed cases are not guaranteed.
- Disabled Android app-data Auto Backup with `android:allowBackup="false"`.
- Updated the privacy notes for local-only app behavior, user-triggered external app handoff, manual settings export/import, and mailto-only developer contact.
- Changed developer contact to `textview.ahnyb@addy.io`.
- Consolidated 2.2.6 public documentation and removed noisy internal pass-by-pass RAR development notes from the release ZIP.

## Known archive boundaries

- RAR creation/compression is intentionally unsupported.
- RAR3/RAR4 normal non-encrypted compressed entries are attempted through bundled libarchive-android.
- First-party RAR extraction is limited to covered metadata/stored-entry/stored-split/stored-RAR5 paths and limited diagnostics/gap reducers.
- Split/multi-volume RAR and encrypted RAR are best-effort/unverified for this package.
- RAR3/RAR4 compressed solid, PPMd/VM-filtered first-party decoding, broad SFX handling, compressed split chains, and unusual variants must not be advertised as guaranteed support.
- RAR5 compressed/solid/encrypted-header extraction is backend-dependent, not first-party complete.
- Archive creation remains plain ZIP only.

## FOSS status checklist

- Default source license: Apache License 2.0.
- Default source package has no Junrar or UnRAR-license fallback code.
- `app/libs` should contain no decoder `.jar` files in the public source package. The folder may be absent.
- Bundled default runtime dependencies are documented in `THIRD_PARTY_NOTICES.md`, `docs/LICENSE_REPORT_2_2_6.md`, `docs/SBOM_2_2_6.spdx.json`.
- Keep `docs/FOSS_STATUS.md` with release materials.
- If any optional local jar is added for a custom build, do not describe that APK as the default FOSS build until the jar license and notices have been rechecked.

## Privacy checklist

- Default manifest has no `INTERNET` permission.
- Default manifest sets `android:allowBackup="false"`.
- Settings update line is a static copyable GitHub releases URL, not an app network request.
- Contact developer uses a `mailto:` intent to the user's mail app; TextView Reader does not send mail, logs, files, bookmarks, history, or settings itself.
- User-triggered file sharing/open-with flows use Android intents/FileProvider and hand the selected file to the app chosen by the user.
- Keep `PRIVACY.md` with source and binary release materials.

## Upload checklist

- Upload the full source package, not only the `app/` directory.
- Keep `gradle/wrapper/gradle-wrapper.jar` and `gradle/wrapper/gradle-wrapper.properties` in the repository.
- Include `LICENSE`, `NOTICE`, and `THIRD_PARTY_NOTICES.md`, `docs/LICENSE_REPORT_2_2_6.md`, `docs/SBOM_2_2_6.spdx.json`, and `docs/PRIVACY_LICENSE_AUDIT_2_2_6.md` with source and binary release materials.
- Include `PRIVACY.md` with source and binary release materials.
- Do not include keystores, `local.properties`, build outputs, APK/AAB files, personal fixture archives, or private test data.
- Validate locally with `./gradlew :app:assembleRelease` or `gradlew.bat assembleRelease` before tagging.
