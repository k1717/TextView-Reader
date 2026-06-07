# libarchive RAR backend notes

TextView Reader 2.2.6 removes the Junrar dependency and uses the Apache-2.0 `me.zhanghai.android.libarchive:library:1.1.6` AAR as the normal default backend for common RAR3/RAR4 compressed extraction. The first-party Java RAR reader remains in the tree for metadata parsing, stored method-0 extraction, covered stored split payloads, and RAR5 stored/encrypted-stored paths.

The goal is to keep the app FOSS-friendly while documenting the boundary honestly: common normal non-encrypted RAR3/RAR4 compressed archives should route through libarchive by default. First-party Java RAR work is now scoped to stored-entry fallback plus the remaining solid/encrypted gaps, not a generic compressed fallback.

## Default build behavior

- No Junrar dependency is present in `app/build.gradle`.
- The normal APK carries `me.zhanghai.android.libarchive:library:1.1.6` as the RAR3/RAR4 compressed extraction backend.
- No manual `-PtextviewEnableLibarchive=true` flag is needed.
- No local `libarchive.so` copy is needed.
- RAR listing, single-entry extraction, and whole-archive extraction prefer libarchive when the backend is available.
- If libarchive cannot open a plain store-only RAR4 archive, the first-party Java reader can still fall back to stored-entry handling.
- If libarchive cannot handle a RAR3/RAR4 solid or encrypted payload, the app now reports that as a scoped first-party decoder gap instead of falling through to the unfinished generic compressed decoder.
- RAR3/RAR4 solid, encrypted, SFX, compressed split, and unusual method/filter combinations are still not broad public compatibility claims.
- Split/multi-volume RAR and encrypted RAR were not re-tested for the current GitHub-ready package, so release notes should describe them as best-effort/unverified even where routing helpers exist.
- The optional unrar5j bridge has been removed from the default source tree; RAR5 support is now libarchive-first plus the covered first-party stored-data paths.

## RAR3/RAR4 backend path

The default compressed RAR3/RAR4 path is:

```text
ArchiveSupport
  -> RarArchiveReader
  -> RarLibarchiveFallback
  -> LibarchiveNativeBridge
  -> me.zhanghai.android.libarchive Archive/ArchiveEntry bindings
  -> bundled Android libarchive native libraries
```

The bridge registers libarchive's archive readers for RAR work instead of using the app's existing ZIP/TAR/7z routes. This keeps the backend scoped to RAR work instead of turning it into a second general archive router.

Default entry points:

- list entries
- detect likely password requirement
- extract a single entry
- extract a whole archive into a target directory

The Java bridge normalizes paths, rejects obvious traversal paths, skips unsupported special entries, and extracts regular files/directories only.

## Multi-volume handling

RAR volume discovery supports new-style multi-volume names such as `name.part1.rar`, `name.part01.rar`, and `name.part001.rar`. It also supports old-style suffixes such as `name.r00` and `name.r000`.

If a later volume is selected from the file browser, Java resolves the chain from the first available volume and passes the ordered volume path list to libarchive.

This is a routing feature, not a guarantee that every compressed multi-volume RAR can be extracted. Missing, gapped, encrypted, or method-changing chains must still fail cleanly. Current GitHub release wording must also note that split RAR was not re-tested for this package.

## SFX and callback-stream handling

The AAR-backed bridge opens RAR input through libarchive's Java callback API instead of directly calling `readOpenFileNames()`. The callback stream scans the first volume for an embedded RAR signature and starts the logical stream from that offset.

This matters for RAR self-extracting files, where an executable preamble can appear before the real RAR stream. Public app routing still does not treat every generic `.exe` as a normal archive, and SFX extraction must not be advertised as guaranteed. The DOS SFX fixture remains a known limited case.

## Progress and cancellation

Whole-archive extraction accepts the app's progress tracker. The bridge reports entry-level file/folder progress and byte counts while extracting known-size entries. Entry payload extraction streams through libarchive, so cancellation checkpoints occur between read blocks and entries depending on backend behavior.

## Scope warning

This backend is enough to make common normal non-encrypted compressed RAR3/RAR4 extraction the intended default path. It is not enough to claim complete RAR support.

Do not promise support for:

- every RAR3/RAR4 method variant
- encrypted RAR3/RAR4 archives
- broad RAR3/RAR4 solid archive compatibility
- every compressed multi-volume chain
- incomplete multi-volume chains where the first volume is missing
- every SFX executable wrapper
- compressed RAR5 data in the default FOSS build
- RAR5 split/multi-volume files

Passing a password into libarchive is only an extraction attempt. It is not enough to claim encrypted RAR support. Encrypted RAR was not re-tested for the current GitHub-ready package and remains a dedicated compatibility-risk area.

## commons-vfs-rar check

`junrar/commons-vfs-rar` was reviewed as a possible replacement. It is not suitable for this FOSS-focused backend transition because its Gradle metadata declares `api 'com.github.junrar:junrar:7.4.0'`. The provider project itself is MIT-licensed, but adopting it would reintroduce Junrar as a transitive dependency and would not resolve the UnRAR-license concern.

## External RAR fixture notes

See `docs/ARCHIVE_SUPPORT_MATRIX_2_2_6.md` and `docs/RAR_STATUS_2_2_6.md` for the current public boundary and support wording.
